import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * LocalAI
 *
 * Local AI interface for StudySync.
 *
 * Current backend:
 * Ollama -> Qwen3.5 4B
 *
 * This class is intentionally independent of StudySync and GeminiAI.
 *
 * Phase 1:
 * Java -> Ollama -> Qwen3.5 4B
 *
 * Later:
 * LocalAI -> DocumentProcessor -> PDFBox / Apache POI
 * -> visual processing -> Qwen Vision
 */
public class LocalAI {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";

    private static final String MODEL = "qwen3.5:4b";

    /*
     * Keep the model loaded after a request.
     *
     * This prevents StudySync from repeatedly loading the
     * 3.4 GB model into memory.
     */
    private static final String KEEP_ALIVE = "30m";

    /*
     * Connection timeout.
     */
    private static final int CONNECT_TIMEOUT = 10_000;

    /*
     * Read timeout.
     *
     * Qwen can take some time to generate an answer,
     * especially during the first request.
     *
     * 5 minutes gives it enough room for local inference.
     */
    private static final int READ_TIMEOUT = 300_000;

    // ============================================================
    // SYSTEM PROMPT
    // ============================================================

    private static final String SYSTEM_PROMPT = """
            You are StudySync AI, a local academic assistant.

            Your job is to help students understand their study
            resources clearly and accurately.

            Rules:
            - Answer directly and avoid unnecessary preamble.
            - Do not repeat the user's question.
            - Do not provide a long discussion of your reasoning.
            - Give the final answer clearly.
            - Use headings, bullet points, tables, and numbered
              lists when they improve readability.
            - For academic questions, explain concepts step by step
              when necessary.
            - Do not invent information that is not present in the
              supplied resource context.
            - If the supplied resources do not contain enough
              information, clearly say so.
            - When comparing concepts, make the differences explicit.
            - Preserve formulas, algorithms, terminology, and
              technical names accurately.
            """;

    // ============================================================
    // SIMPLE TEXT ANALYSIS
    // ============================================================

    /**
     * Sends a general instruction to Qwen.
     *
     * This method is useful for testing LocalAI before connecting
     * it to StudySync.
     */
    public static String ask(String instruction) throws Exception {

        if (instruction == null ||
                instruction.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "AI instruction cannot be empty.");
        }

        return sendToOllama(instruction);
    }

    // ============================================================
    // FILE API
    // ============================================================

    /**
     * Analyze a single file.
     *
     * NOTE:
     * Document parsing is intentionally NOT implemented yet.
     *
     * For Phase 1 this simply tells the model about the file.
     *
     * PDFBox / POI / visual processing will be added later.
     */
    public static String analyzeFile(
            File file,
            String instruction) throws Exception {

        validateFile(file);

        if (instruction == null ||
                instruction.trim().isEmpty()) {

            instruction = "Analyze the supplied resource and explain " +
                    "its important academic content.";
        }

        String prompt = "RESOURCE FILE:\n" +
                file.getName() +
                "\n\n" +
                "USER REQUEST:\n" +
                instruction +
                "\n\n" +
                "Important:\n" +
                "The actual document contents have not yet been " +
                "parsed in this Phase 1 test. Do not invent its " +
                "contents.";

        return sendToOllama(prompt);
    }

    /**
     * Analyze multiple files.
     *
     * Same Phase 1 limitation:
     * actual document extraction will be added later.
     */
    public static String analyzeFiles(
            List<File> files,
            String instruction) throws Exception {

        if (files == null ||
                files.isEmpty()) {

            throw new IllegalArgumentException(
                    "No files were supplied for AI analysis.");
        }

        if (instruction == null ||
                instruction.trim().isEmpty()) {

            instruction = "Analyze the supplied study resources " +
                    "and summarize their important content.";
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append(
                "SELECTED STUDY RESOURCES:\n\n");

        for (int i = 0; i < files.size(); i++) {

            File file = files.get(i);

            validateFile(file);

            prompt.append(i + 1)
                    .append(". ")
                    .append(file.getName())
                    .append("\n");
        }

        prompt.append(
                "\nUSER REQUEST:\n");

        prompt.append(instruction);

        prompt.append(
                """

                        \n\nImportant:
                        The actual contents of these documents have not
                        yet been parsed in this Phase 1 test.

                        Do not invent document contents.

                        The document-processing layer will later provide
                        extracted text, tables, images, graphs, diagrams,
                        and other relevant visual information.
                        """);

        return sendToOllama(
                prompt.toString());
    }

    // ============================================================
    // OLLAMA COMMUNICATION
    // ============================================================

    private static String sendToOllama(
            String userPrompt) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = URI.create(OLLAMA_URL)
                    .toURL();

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            connection.setConnectTimeout(
                    CONNECT_TIMEOUT);

            connection.setReadTimeout(
                    READ_TIMEOUT);

            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json");

            connection.setRequestProperty(
                    "Accept",
                    "application/json");

            // ----------------------------------------------------
            // JSON REQUEST
            // ----------------------------------------------------

            String json = buildRequestJson(userPrompt);

            try (OutputStream output = connection.getOutputStream()) {

                output.write(
                        json.getBytes(
                                StandardCharsets.UTF_8));
            }

            // ----------------------------------------------------
            // RESPONSE
            // ----------------------------------------------------

            int status = connection.getResponseCode();

            InputStream stream;

            if (status >= 200 &&
                    status < 300) {

                stream = connection.getInputStream();

            } else {

                stream = connection.getErrorStream();
            }

            String response = readStream(stream);

            if (status < 200 ||
                    status >= 300) {

                throw new IOException(
                        "Ollama returned HTTP " +
                                status +
                                ":\n" +
                                response);
            }

            // ----------------------------------------------------
            // EXTRACT RESPONSE TEXT
            // ----------------------------------------------------

            return extractResponse(response);

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ============================================================
    // BUILD OLLAMA JSON
    // ============================================================

    private static String buildRequestJson(
            String userPrompt) {

        return "{"
                + "\"model\":\"" + escapeJson(MODEL) + "\","
                + "\"messages\":["
                + "{"
                + "\"role\":\"system\","
                + "\"content\":\"" + escapeJson(SYSTEM_PROMPT) + "\""
                + "},"
                + "{"
                + "\"role\":\"user\","
                + "\"content\":\"" + escapeJson(userPrompt) + "\""
                + "}"
                + "],"
                + "\"stream\":false,"
                + "\"think\":false,"
                + "\"keep_alive\":\"30m\""
                + "}";
    }

    // ============================================================
    // JSON RESPONSE EXTRACTION
    // ============================================================

    /**
     * Extracts:
     *
     * {
     * "message": {
     * "role": "assistant",
     * "content": "..."
     * }
     * }
     *
     * without requiring a JSON library.
     *
     * We intentionally keep Phase 1 dependency-free.
     */
    private static String extractResponse(
            String json) throws IOException {

        if (json == null ||
                json.trim().isEmpty()) {

            throw new IOException(
                    "Ollama returned an empty response.");
        }

        String marker = "\"content\":\"";

        int start = json.indexOf(marker);

        if (start == -1) {

            throw new IOException(
                    "Could not find the AI response in Ollama output.\n\n"
                            + json);
        }

        start += marker.length();

        StringBuilder result = new StringBuilder();

        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {

            char c = json.charAt(i);

            if (escaped) {

                switch (c) {

                    case 'n':
                        result.append('\n');
                        break;

                    case 'r':
                        result.append('\r');
                        break;

                    case 't':
                        result.append('\t');
                        break;

                    case '"':
                        result.append('"');
                        break;

                    case '\\':
                        result.append('\\');
                        break;

                    case '/':
                        result.append('/');
                        break;

                    case 'b':
                        result.append('\b');
                        break;

                    case 'f':
                        result.append('\f');
                        break;

                    case 'u':

                        if (i + 4 < json.length()) {

                            String hex = json.substring(
                                    i + 1,
                                    i + 5);

                            try {

                                result.append(
                                        (char) Integer.parseInt(
                                                hex,
                                                16));

                                i += 4;

                            } catch (NumberFormatException e) {

                                result.append("\\u");
                                result.append(hex);

                                i += 4;
                            }

                        } else {

                            result.append("\\u");
                        }

                        break;

                    default:
                        result.append(c);
                }

                escaped = false;

            } else if (c == '\\') {

                escaped = true;

            } else if (c == '"') {

                break;

            } else {

                result.append(c);
            }
        }

        return result.toString().trim();
    }

    // ============================================================
    // FILE VALIDATION
    // ============================================================

    private static void validateFile(
            File file) {

        if (file == null) {

            throw new IllegalArgumentException(
                    "File cannot be null.");
        }

        if (!file.exists()) {

            throw new IllegalArgumentException(
                    "File does not exist:\n" +
                            file.getAbsolutePath());
        }

        if (!file.isFile()) {

            throw new IllegalArgumentException(
                    "Path is not a file:\n" +
                            file.getAbsolutePath());
        }
    }

    // ============================================================
    // STREAM READER
    // ============================================================

    private static String readStream(
            InputStream stream) throws IOException {

        if (stream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {

                result.append(line)
                        .append('\n');
            }
        }

        return result.toString().trim();
    }

    // ============================================================
    // JSON ESCAPING
    // ============================================================

    private static String escapeJson(
            String text) {

        if (text == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {

            switch (c) {

                case '"':
                    result.append("\\\"");
                    break;

                case '\\':
                    result.append("\\\\");
                    break;

                case '\b':
                    result.append("\\b");
                    break;

                case '\f':
                    result.append("\\f");
                    break;

                case '\n':
                    result.append("\\n");
                    break;

                case '\r':
                    result.append("\\r");
                    break;

                case '\t':
                    result.append("\\t");
                    break;

                default:

                    if (c < 32) {

                        result.append(
                                String.format(
                                        "\\u%04x",
                                        (int) c));

                    } else {

                        result.append(c);
                    }
            }
        }

        return result.toString();
    }

    // ============================================================
    // CONNECTION TEST
    // ============================================================

    /**
     * Simple health check.
     *
     * Returns true if Ollama is reachable.
     */
    public static boolean isOllamaRunning() {

        HttpURLConnection connection = null;

        try {

            URL url = URI.create(
                    "http://localhost:11434/api/tags").toURL();

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(
                    3000);

            connection.setReadTimeout(
                    3000);

            return connection.getResponseCode() == 200;

        } catch (Exception e) {

            return false;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ============================================================
    // MODEL TEST
    // ============================================================

    public static void main(
            String[] args) {

        System.out.println();
        System.out.println(
                "==========================================");
        System.out.println(
                "       StudySync LocalAI Test");
        System.out.println(
                "==========================================");
        System.out.println();

        System.out.println(
                "Model: " + MODEL);

        System.out.println(
                "Ollama: " +
                        (isOllamaRunning()
                                ? "ONLINE"
                                : "OFFLINE"));

        System.out.println();

        if (!isOllamaRunning()) {

            System.out.println(
                    "Ollama is not running.");

            System.out.println(
                    "Start it with:");

            System.out.println(
                    "ollama serve");

            return;
        }

        try {

            System.out.println(
                    "Sending test request to Qwen3.5 4B...");

            System.out.println();

            String response = ask(
                    "Say hello to me in one short sentence. "
                            + "Do not explain your reasoning.");

            System.out.println(
                    "Qwen response:");

            System.out.println(
                    "------------------------------------------");

            System.out.println(
                    response);

            System.out.println(
                    "------------------------------------------");

            System.out.println();

            System.out.println(
                    "LOCAL AI TEST PASSED.");

        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "LOCAL AI TEST FAILED.");

            System.out.println();

            e.printStackTrace();
        }
    }
}