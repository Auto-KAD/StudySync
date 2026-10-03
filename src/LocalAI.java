import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * LocalAI
 *
 * Local AI interface for StudySync.
 *
 * Backend:
 * Ollama -> Qwen3.5 4B
 *
 * Supports:
 * - Text-only requests
 * - Multimodal requests with document images
 *
 * Visual safety:
 * - Limits the number of images sent to Qwen
 * - Ignores invalid image objects
 * - Keeps the image payload bounded
 *
 * This class is independent of StudySync UI and GeminiAI.
 */
public class LocalAI implements AIService {

        // =========================================================
        // CONFIGURATION
        // =========================================================

        private static final String OLLAMA_URL = "http://localhost:11434/api/chat";

        private static final String MODEL = "qwen3.5:4b";

        /**
         * Keep the model loaded between requests.
         */
        private static final String KEEP_ALIVE = "30m";

        /**
         * Maximum number of visual inputs that may be
         * sent to Qwen in a single request.
         *
         * This prevents accidentally sending dozens of
         * rendered document pages to the local model.
         */
        private static final int MAX_IMAGES_PER_REQUEST = 2;

        private static final int CONNECT_TIMEOUT = 10_000;

        private static final int READ_TIMEOUT = 300_000;

        // =========================================================
        // SYSTEM PROMPT
        // =========================================================

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
                          supplied resource context or visual material.
                        - If the supplied resources do not contain enough
                          information, clearly say so.
                        - When comparing concepts, make the differences explicit.
                        - Preserve formulas, algorithms, terminology, and
                          technical names accurately.
                        - When visual material is supplied, inspect it carefully
                          and use information visible in the images.
                        - Do not describe your hidden reasoning process.
                        """;

        // =========================================================
        // MAIN AI SERVICE
        // =========================================================

        @Override
        public AIResponse ask(
                        AIRequest request) throws Exception {

                if (request == null) {

                        throw new IllegalArgumentException(
                                        "AI request cannot be null.");
                }

                String question = request.getQuestion();

                String context = request.getContext();

                if (question == null ||
                                question.trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "AI question cannot be empty.");
                }

                if (context == null) {
                        context = "";
                }

                // =====================================================
                // BUILD PROMPT
                // =====================================================

                StringBuilder prompt = new StringBuilder();

                if (!context.isBlank()) {

                        prompt.append(context);

                        prompt.append("\n\n");
                }

                prompt.append(
                                "USER QUESTION\n");

                prompt.append(
                                "-------------\n");

                prompt.append(
                                question);

                prompt.append(
                                "\n\n");

                prompt.append(
                                "Answer the question using the supplied "
                                                + "academic resources.\n");

                prompt.append(
                                "Use all relevant resources.\n");

                if (request.hasImages()) {

                        prompt.append(
                                        "Visual material has also been supplied. "
                                                        + "Inspect the supplied images carefully "
                                                        + "and use relevant visual information.\n");

                        prompt.append(
                                        "Only the selected visual material is available "
                                                        + "for visual analysis.\n");
                }

                prompt.append(
                                "Do not invent information.\n");

                prompt.append(
                                "Be concise and student-friendly.");

                // =====================================================
                // SELECT VISUAL INPUTS
                // =====================================================

                List<DocumentImage> selectedImages = request.getImages(
                                MAX_IMAGES_PER_REQUEST);

                System.out.println(
                                "LocalAI: "
                                                + selectedImages.size()
                                                + " visual input(s) selected.");

                // =====================================================
                // SEND REQUEST
                // =====================================================

                String result = sendToOllama(
                                prompt.toString(),
                                selectedImages);

                return new AIResponse(result);
        }

        // =========================================================
        // TEXT + MULTIMODAL OLLAMA REQUEST
        // =========================================================

        /**
         * Sends a prompt with optional document images.
         *
         * Ollama expects multimodal images as Base64 strings
         * inside the user's message.
         */
        private static String sendToOllama(
                        String prompt,
                        List<DocumentImage> images) throws Exception {

                if (images == null) {
                        images = Collections.emptyList();
                }

                String json = buildRequestJson(
                                prompt,
                                images);

                return sendRawOllamaRequest(json);
        }

        // =========================================================
        // RAW OLLAMA HTTP REQUEST
        // =========================================================

        /**
         * Performs the actual HTTP request to Ollama.
         *
         * Both text-only and multimodal requests use this method.
         */
        private static String sendRawOllamaRequest(
                        String json) throws Exception {

                HttpURLConnection connection = null;

                try {

                        URL url = URI.create(
                                        OLLAMA_URL).toURL();

                        connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod(
                                        "POST");

                        connection.setConnectTimeout(
                                        CONNECT_TIMEOUT);

                        connection.setReadTimeout(
                                        READ_TIMEOUT);

                        connection.setDoOutput(
                                        true);

                        connection.setRequestProperty(
                                        "Content-Type",
                                        "application/json");

                        connection.setRequestProperty(
                                        "Accept",
                                        "application/json");

                        // =================================================
                        // SEND JSON
                        // =================================================

                        try (
                                        OutputStream output = connection.getOutputStream()) {

                                output.write(
                                                json.getBytes(
                                                                StandardCharsets.UTF_8));
                        }

                        // =================================================
                        // READ RESPONSE
                        // =================================================

                        int status = connection.getResponseCode();

                        InputStream stream;

                        if (status >= 200 &&
                                        status < 300) {

                                stream = connection.getInputStream();

                        } else {

                                stream = connection.getErrorStream();
                        }

                        String response = readStream(stream);

                        // =================================================
                        // ERROR HANDLING
                        // =================================================

                        if (status < 200 ||
                                        status >= 300) {

                                throw new IOException(
                                                "Ollama returned HTTP "
                                                                + status
                                                                + ":\n"
                                                                + response);
                        }

                        // =================================================
                        // EXTRACT AI RESPONSE
                        // =================================================

                        return extractResponse(
                                        response);

                } finally {

                        if (connection != null) {
                                connection.disconnect();
                        }
                }
        }

        // =========================================================
        // BUILD OLLAMA JSON
        // =========================================================

        /**
         * Builds an Ollama /api/chat request.
         *
         * Text-only:
         *
         * "messages": [...]
         *
         * Multimodal:
         *
         * "messages": [
         * {
         * "role": "user",
         * "content": "...",
         * "images": [
         * "BASE64_IMAGE_1"
         * ]
         * }
         * ]
         */
        private static String buildRequestJson(
                        String userPrompt,
                        List<DocumentImage> images) {

                StringBuilder json = new StringBuilder();

                json.append("{");

                // =====================================================
                // MODEL
                // =====================================================

                json.append(
                                "\"model\":\""
                                                + escapeJson(MODEL)
                                                + "\",");

                // =====================================================
                // MESSAGES
                // =====================================================

                json.append(
                                "\"messages\":[");

                // =====================================================
                // SYSTEM MESSAGE
                // =====================================================

                json.append("{");

                json.append(
                                "\"role\":\"system\",");

                json.append(
                                "\"content\":\""
                                                + escapeJson(
                                                                SYSTEM_PROMPT)
                                                + "\"");

                json.append("},");

                // =====================================================
                // USER MESSAGE
                // =====================================================

                json.append("{");

                json.append(
                                "\"role\":\"user\",");

                json.append(
                                "\"content\":\""
                                                + escapeJson(
                                                                userPrompt)
                                                + "\"");

                // =====================================================
                // IMAGES
                // =====================================================

                List<DocumentImage> validImages = getValidImages(
                                images,
                                MAX_IMAGES_PER_REQUEST);

                if (!validImages.isEmpty()) {

                        json.append(",");

                        json.append(
                                        "\"images\":[");

                        for (int i = 0; i < validImages.size(); i++) {

                                if (i > 0) {
                                        json.append(",");
                                }

                                DocumentImage image = validImages.get(i);

                                String base64 = Base64
                                                .getEncoder()
                                                .encodeToString(
                                                                image.getImageData());

                                json.append("\"");

                                json.append(
                                                base64);

                                json.append("\"");
                        }

                        json.append("]");
                }

                // =====================================================
                // CLOSE USER MESSAGE
                // =====================================================

                json.append("}");

                // =====================================================
                // CLOSE MESSAGES
                // =====================================================

                json.append("],");

                // =====================================================
                // GENERATION SETTINGS
                // =====================================================

                json.append("\"stream\":false,");
                json.append("\"think\":false,");

                json.append("\"options\":{");
                json.append("\"num_ctx\":12288,");
                json.append("\"num_predict\":2048");
                json.append("},");

                json.append(
                                "\"keep_alive\":\""
                                                + KEEP_ALIVE
                                                + "\"");

                // =====================================================
                // CLOSE ROOT JSON OBJECT
                // =====================================================

                json.append("}");

                return json.toString();
        }

        // =========================================================
        // VISUAL INPUT FILTERING
        // =========================================================

        /**
         * Returns only valid visual inputs and enforces
         * the maximum image limit.
         *
         * This is the final safety boundary before Base64
         * encoding and sending images to Ollama.
         */
        private static List<DocumentImage> getValidImages(
                        List<DocumentImage> images,
                        int maximum) {

                if (images == null ||
                                images.isEmpty() ||
                                maximum <= 0) {

                        return Collections.emptyList();
                }

                List<DocumentImage> validImages = new ArrayList<>();

                for (DocumentImage image : images) {

                        if (image == null) {
                                continue;
                        }

                        byte[] data = image.getImageData();

                        if (data == null ||
                                        data.length == 0) {

                                continue;
                        }

                        validImages.add(image);

                        if (validImages.size() >= maximum) {
                                break;
                        }
                }

                return validImages;
        }

        // =========================================================
        // SIMPLE TEXT ANALYSIS
        // =========================================================

        /**
         * Convenience method for testing LocalAI directly.
         *
         * This is not the main application API.
         */
        public static String ask(
                        String instruction) throws Exception {

                if (instruction == null ||
                                instruction.trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "AI instruction cannot be empty.");
                }

                return sendToOllama(
                                instruction,
                                Collections.emptyList());
        }

        // =========================================================
        // FILE API
        // =========================================================

        /**
         * Analyze a single file.
         *
         * Kept temporarily for compatibility with older
         * StudySync code.
         *
         * Preferred architecture:
         *
         * File
         * -> DocumentService
         * -> DocumentContextBuilder
         * -> AIRequest
         * -> LocalAI
         */
        public static String analyzeFile(
                        File file,
                        String instruction) throws Exception {

                validateFile(file);

                if (instruction == null ||
                                instruction.trim().isEmpty()) {

                        instruction = "Analyze the supplied resource and "
                                        + "explain its important academic content.";
                }

                String prompt = "RESOURCE FILE:\n"
                                + file.getName()
                                + "\n\n"
                                + "USER REQUEST:\n"
                                + instruction
                                + "\n\n"
                                + "Important:\n"
                                + "The document contents must be supplied "
                                + "through the document processing layer. "
                                + "Do not invent contents that were not supplied.";

                return sendToOllama(
                                prompt,
                                Collections.emptyList());
        }

        /**
         * Analyze multiple files.
         *
         * Kept temporarily for compatibility.
         *
         * Preferred architecture:
         *
         * Files
         * -> DocumentService
         * -> DocumentContextBuilder
         * -> AIRequest
         * -> LocalAI
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

                        instruction = "Analyze the supplied study resources "
                                        + "and summarize their important content.";
                }

                StringBuilder prompt = new StringBuilder();

                prompt.append(
                                "SELECTED STUDY RESOURCES:\n\n");

                for (int i = 0; i < files.size(); i++) {

                        File file = files.get(i);

                        validateFile(file);

                        prompt.append(
                                        i + 1);

                        prompt.append(
                                        ". ");

                        prompt.append(
                                        file.getName());

                        prompt.append(
                                        "\n");
                }

                prompt.append(
                                "\nUSER REQUEST:\n");

                prompt.append(
                                instruction);

                prompt.append(
                                "\n\nImportant:\n"
                                                + "The actual contents of these documents "
                                                + "must be supplied through the document "
                                                + "processing layer.\n"
                                                + "Do not invent document contents.");

                return sendToOllama(
                                prompt.toString(),
                                Collections.emptyList());
        }

        // =========================================================
        // RESPONSE EXTRACTION
        // =========================================================

        /**
         * Extracts message.content from the Ollama JSON response.
         *
         * This parser handles the escaped characters normally
         * returned inside the content field.
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
                                        "Could not find the AI response "
                                                        + "in Ollama output.\n\n"
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

                                                                result.append(
                                                                                "\\u");

                                                                result.append(
                                                                                hex);

                                                                i += 4;
                                                        }

                                                } else {

                                                        result.append(
                                                                        "\\u");
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

                return result
                                .toString()
                                .trim();
        }

        // =========================================================
        // FILE VALIDATION
        // =========================================================

        private static void validateFile(
                        File file) {

                if (file == null) {

                        throw new IllegalArgumentException(
                                        "File cannot be null.");
                }

                if (!file.exists()) {

                        throw new IllegalArgumentException(
                                        "File does not exist:\n"
                                                        + file.getAbsolutePath());
                }

                if (!file.isFile()) {

                        throw new IllegalArgumentException(
                                        "Path is not a file:\n"
                                                        + file.getAbsolutePath());
                }

                if (!file.canRead()) {

                        throw new IllegalArgumentException(
                                        "File cannot be read:\n"
                                                        + file.getAbsolutePath());
                }
        }

        // =========================================================
        // STREAM READER
        // =========================================================

        private static String readStream(
                        InputStream stream) throws IOException {

                if (stream == null) {
                        return "";
                }

                StringBuilder result = new StringBuilder();

                try (
                                BufferedReader reader = new BufferedReader(
                                                new InputStreamReader(
                                                                stream,
                                                                StandardCharsets.UTF_8))) {

                        String line;

                        while ((line = reader.readLine()) != null) {

                                result.append(line)
                                                .append('\n');
                        }
                }

                return result
                                .toString()
                                .trim();
        }

        // =========================================================
        // JSON ESCAPING
        // =========================================================

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

        // =========================================================
        // OLLAMA CONNECTION TEST
        // =========================================================

        public static boolean isOllamaRunning() {

                HttpURLConnection connection = null;

                try {

                        URL url = URI.create(
                                        "http://localhost:11434/api/tags").toURL();

                        connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod(
                                        "GET");

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

        // =========================================================
        // MODEL TEST
        // =========================================================

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
                                "Model: "
                                                + MODEL);

                System.out.println(
                                "Ollama: "
                                                + (isOllamaRunning()
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