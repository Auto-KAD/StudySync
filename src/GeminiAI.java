import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gemini REST client used by StudySync.
 *
 * Important:
 * - The original selected files are uploaded to Gemini.
 * - Tika is NOT used for AI analysis.
 * - This class intentionally avoids regex-based JSON parsing.
 *   Large Gemini responses / file metadata can otherwise make Java's
 *   regex engine consume excessive stack space and produce StackOverflowError.
 */
public class GeminiAI {

    private static final String[] MODEL_LADDER = {
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash"
    };

    /*
     * Gemini documents 503 UNAVAILABLE as a transient server-side
     * condition. Retry with exponential backoff and jitter before
     * moving to the next model.
     */
    private static final int GENERATION_RETRIES = 4;
    private static final long GENERATION_INITIAL_BACKOFF_MS = 1500L;

    private static final String FILE_UPLOAD_URL =
            "https://generativelanguage.googleapis.com/upload/v1beta/files";

    private static final String FILE_METADATA_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/";

    private static final String GENERATE_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

    private static final int FILE_READY_ATTEMPTS = 60;
    private static final long FILE_READY_DELAY_MS = 1000L;

    // =========================================================
    // SINGLE FILE
    // =========================================================

    public static String analyzeFile(
            File file,
            String instruction
    ) throws Exception {

        List<File> files = new ArrayList<>();
        files.add(file);

        return analyzeFiles(files, instruction);
    }

    // =========================================================
    // MULTI FILE
    // =========================================================

    public static String analyzeFiles(
            List<File> files,
            String instruction
    ) throws Exception {

        try {
            validateInput(files);

            String apiKey = apiconfig.GEMINI_API_KEY;

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException(
                        "GEMINI_API_KEY is not configured."
                );
            }

            String prompt = buildMultiFilePrompt(files, instruction);

            List<UploadedFile> uploaded = new ArrayList<>();

            for (File file : files) {
                uploaded.add(uploadFile(file, apiKey));
            }

            for (UploadedFile file : uploaded) {
                waitUntilReady(file.name, apiKey);
            }

            Exception lastError = null;

            for (String model : MODEL_LADDER) {
                try {
                    return generateContentWithRetry(
                            model,
                            uploaded,
                            prompt,
                            apiKey
                    );
                } catch (Exception ex) {
                    lastError = ex;

                    /*
                     * A 404/model-not-found means try the next model.
                     * Other non-transient errors should be shown directly.
                     */
                    if (!isRetryableModelError(ex)) {
                        throw ex;
                    }
                }
            }

            if (lastError != null) {
                throw lastError;
            }

            throw new IOException("Gemini analysis failed.");

        } catch (StackOverflowError error) {
            /*
             * Do not allow a JVM StackOverflowError to reach SwingWorker.
             * Convert it into a useful diagnostic.
             */
            throw new IOException(
                    "Gemini analysis caused a StackOverflowError.\n\n"
                            + "The Gemini client has been updated to remove "
                            + "recursive/regex-based JSON parsing. "
                            + "If this message still appears, restart "
                            + "StudySync after replacing GeminiAI.java.",
                    error
            );
        }
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private static void validateInput(
            List<File> files
    ) throws IOException {

        if (files == null || files.isEmpty()) {
            throw new IOException("No resources were selected.");
        }

        for (File file : files) {

            if (file == null || !file.exists() || !file.isFile()) {
                throw new IOException(
                        "A selected resource does not exist."
                );
            }

            if (!file.canRead()) {
                throw new IOException(
                        "Cannot read "
                                + file.getName()
                                + ".\n"
                                + "If it is in OneDrive, select "
                                + "\"Always Keep on This Device\"."
                );
            }
        }
    }

    // =========================================================
    // FILE UPLOAD
    // =========================================================

    private static UploadedFile uploadFile(
            File file,
            String apiKey
    ) throws Exception {

        byte[] data = Files.readAllBytes(file.toPath());

        if (data.length == 0) {
            throw new IOException(
                    "The selected file is empty: "
                            + file.getName()
            );
        }

        String mimeType = getMimeType(file);

        // -----------------------------------------------------
        // Step 1: initialize resumable upload
        // -----------------------------------------------------

        String startUrl =
                FILE_UPLOAD_URL
                        + "?key="
                        + urlEncode(apiKey);

        String metadataJson =
                "{\"file\":{\"display_name\":\""
                        + jsonEscape(file.getName())
                        + "\"}}";

        HttpRequest startRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(startUrl))
                        .header(
                                "X-Goog-Upload-Protocol",
                                "resumable"
                        )
                        .header(
                                "X-Goog-Upload-Command",
                                "start"
                        )
                        .header(
                                "X-Goog-Upload-Header-Content-Length",
                                String.valueOf(data.length)
                        )
                        .header(
                                "X-Goog-Upload-Header-Content-Type",
                                mimeType
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        metadataJson,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        HttpResponse<String> startResponse =
                HTTP_CLIENT.send(
                        startRequest,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );

        requireSuccess(
                startResponse,
                "Gemini file upload initialization"
        );

        String uploadUrl =
                startResponse.headers()
                        .firstValue("X-Goog-Upload-URL")
                        .orElse(null);

        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new IOException(
                    "Gemini did not return an upload URL."
            );
        }

        // -----------------------------------------------------
        // Step 2: upload actual bytes
        // -----------------------------------------------------
        //
        // DO NOT manually set Content-Length here.
        // java.net.http calculates it from BodyPublishers.ofByteArray().
        // -----------------------------------------------------

        HttpRequest uploadRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(uploadUrl))
                        .header(
                                "X-Goog-Upload-Offset",
                                "0"
                        )
                        .header(
                                "X-Goog-Upload-Command",
                                "upload, finalize"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofByteArray(
                                        data
                                )
                        )
                        .build();

        HttpResponse<String> uploadResponse =
                HTTP_CLIENT.send(
                        uploadRequest,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );

        requireSuccess(
                uploadResponse,
                "Gemini file upload"
        );

        String responseBody = uploadResponse.body();

        /*
         * Gemini returns:
         *
         * {
         *   "file": {
         *      "name": "files/...",
         *      "displayName": "...",
         *      "mimeType": "...",
         *      "uri": "..."
         *   }
         * }
         *
         * We only need these three fields.
         */
        String fileName =
                findJsonStringValue(
                        responseBody,
                        "name"
                );

        String fileUri =
                findJsonStringValue(
                        responseBody,
                        "uri"
                );

        String returnedMime =
                findJsonStringValue(
                        responseBody,
                        "mimeType"
                );

        if (fileName == null || fileName.isBlank()) {
            throw new IOException(
                    "Gemini upload succeeded but no file name was returned.\n\n"
                            + safeBody(responseBody)
            );
        }

        if (fileUri == null || fileUri.isBlank()) {
            throw new IOException(
                    "Gemini upload succeeded but no file URI was returned.\n\n"
                            + safeBody(responseBody)
            );
        }

        if (returnedMime == null || returnedMime.isBlank()) {
            returnedMime = mimeType;
        }

        return new UploadedFile(
                fileName,
                fileUri,
                returnedMime
        );
    }

    // =========================================================
    // WAIT FOR FILE PROCESSING
    // =========================================================

    private static void waitUntilReady(
            String fileName,
            String apiKey
    ) throws Exception {

        String metadataUrl =
                FILE_METADATA_BASE_URL
                        + fileName
                        + "?key="
                        + urlEncode(apiKey);

        for (int attempt = 0;
             attempt < FILE_READY_ATTEMPTS;
             attempt++) {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(metadataUrl))
                            .GET()
                            .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            requireSuccess(
                    response,
                    "Checking Gemini file status"
            );

            String body = response.body();

            String state =
                    findJsonStringValue(
                            body,
                            "state"
                    );

            if (state == null || state.isBlank()) {
                /*
                 * Some responses may not expose a state field.
                 * In that case, allow generateContent to decide.
                 */
                return;
            }

            if ("ACTIVE".equalsIgnoreCase(state)) {
                return;
            }

            if ("FAILED".equalsIgnoreCase(state)) {
                String errorMessage =
                        findNestedErrorMessage(body);

                throw new IOException(
                        "Gemini could not process uploaded file:\n"
                                + fileName
                                + (errorMessage == null
                                ? ""
                                : "\n\n" + errorMessage)
                );
            }

            Thread.sleep(FILE_READY_DELAY_MS);
        }

        throw new IOException(
                "Gemini file processing timed out:\n"
                        + fileName
        );
    }

    // =========================================================
    // GENERATE CONTENT WITH RETRY
    // =========================================================

    private static String generateContentWithRetry(
            String model,
            List<UploadedFile> files,
            String prompt,
            String apiKey
    ) throws Exception {

        Exception lastError = null;

        for (int attempt = 0;
             attempt < GENERATION_RETRIES;
             attempt++) {

            try {
                return generateContent(
                        model,
                        files,
                        prompt,
                        apiKey
                );

            } catch (Exception ex) {

                lastError = ex;

                if (!isTransientGenerationError(ex) ||
                        attempt == GENERATION_RETRIES - 1) {

                    throw ex;
                }

                long baseDelay =
                        GENERATION_INITIAL_BACKOFF_MS
                                * (1L << attempt);

                /*
                 * Small jitter prevents repeated requests from lining
                 * up exactly at the same retry instant.
                 */
                long jitter =
                        (long) (Math.random() * 750L);

                long delay =
                        Math.min(
                                baseDelay + jitter,
                                30000L
                        );

                Thread.sleep(delay);
            }
        }

        throw lastError != null
                ? lastError
                : new IOException(
                "Gemini generation failed."
        );
    }

    // =========================================================
    // GENERATE CONTENT
    // =========================================================

    private static String generateContent(
            String model,
            List<UploadedFile> files,
            String prompt,
            String apiKey
    ) throws Exception {

        StringBuilder parts = new StringBuilder();

        parts.append("{\"text\":\"")
                .append(jsonEscape(prompt))
                .append("\"}");

        for (UploadedFile file : files) {

            parts.append(",")
                    .append("{\"file_data\":{")
                    .append("\"mime_type\":\"")
                    .append(jsonEscape(file.mimeType))
                    .append("\",")
                    .append("\"file_uri\":\"")
                    .append(jsonEscape(file.uri))
                    .append("\"")
                    .append("}}");
        }

        String requestBody =
                "{\"contents\":[{\"parts\":["
                        + parts
                        + "]}]}";

        String url =
                GENERATE_BASE_URL
                        + model
                        + ":generateContent?key="
                        + urlEncode(apiKey);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );

        requireSuccess(
                response,
                "Gemini content generation"
        );

        String text =
                extractResponseText(
                        response.body()
                );

        if (text == null || text.isBlank()) {
            throw new IOException(
                    "Gemini returned an empty response."
            );
        }

        return text.trim();
    }

    // =========================================================
    // PROMPT
    // =========================================================

    private static String buildMultiFilePrompt(
            List<File> files,
            String instruction
    ) {

        StringBuilder names = new StringBuilder();

        for (File file : files) {
            names.append("- ")
                    .append(file.getName())
                    .append('\n');
        }

        String request =
                instruction == null || instruction.isBlank()
                        ? "Help me understand these resources."
                        : instruction.trim();

        return """
                You are StudySync AI, the academic assistant inside
                a Student Resource Management System.

                The user selected the following original resources:

                %s

                User request:
                %s

                Analyze the supplied files together.

                Important instructions:
                - Treat the uploaded files as the primary source.
                - Use information from all relevant selected files.
                - Compare or connect the resources when useful.
                - Do not invent facts that are not supported by the files.
                - If the files do not contain enough information to answer
                  something, clearly say so.
                - The user is a university student, so make the response
                  practical and academically useful.
                - Use headings, numbered steps, tables, or bullet points
                  when they improve clarity.
                """.formatted(
                names,
                request
        );
    }

    // =========================================================
    // MIME TYPE
    // =========================================================

    private static String getMimeType(File file) {

        try {
            String detected =
                    Files.probeContentType(file.toPath());

            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (Exception ignored) {
        }

        String name =
                file.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".pdf"))
            return "application/pdf";

        if (name.endsWith(".docx"))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        if (name.endsWith(".doc"))
            return "application/msword";

        if (name.endsWith(".pptx"))
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";

        if (name.endsWith(".ppt"))
            return "application/vnd.ms-powerpoint";

        if (name.endsWith(".xlsx"))
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        if (name.endsWith(".xls"))
            return "application/vnd.ms-excel";

        if (name.endsWith(".txt"))
            return "text/plain";

        if (name.endsWith(".csv"))
            return "text/csv";

        if (name.endsWith(".png"))
            return "image/png";

        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "image/jpeg";

        if (name.endsWith(".webp"))
            return "image/webp";

        return "application/octet-stream";
    }

    // =========================================================
    // RESPONSE PARSING
    // =========================================================

    /**
     * Extract all text fields from Gemini's response without regex.
     *
     * Expected response shape:
     * candidates -> content -> parts -> text
     *
     * This deliberately uses a linear scanner rather than a regex matcher.
     */
    private static String extractResponseText(
            String json
    ) throws IOException {

        if (json == null || json.isBlank()) {
            throw new IOException(
                    "Gemini returned an empty response."
            );
        }

        StringBuilder result = new StringBuilder();
        int searchFrom = 0;

        while (searchFrom < json.length()) {

            int keyStart =
                    json.indexOf("\"text\"", searchFrom);

            if (keyStart < 0) {
                break;
            }

            int colon =
                    skipWhitespace(
                            json,
                            keyStart + 6
                    );

            if (colon >= json.length() ||
                    json.charAt(colon) != ':') {

                searchFrom = keyStart + 6;
                continue;
            }

            int valueStart =
                    skipWhitespace(
                            json,
                            colon + 1
                    );

            if (valueStart >= json.length() ||
                    json.charAt(valueStart) != '"') {

                searchFrom = keyStart + 6;
                continue;
            }

            ParsedString parsed =
                    readJsonString(
                            json,
                            valueStart
                    );

            if (parsed == null) {
                searchFrom = keyStart + 6;
                continue;
            }

            String value = parsed.value;

            if (!value.isBlank()) {
                if (result.length() > 0) {
                    result.append('\n');
                }

                result.append(value);
            }

            searchFrom = parsed.nextIndex;
        }

        if (result.length() == 0) {

            throw new IOException(
                    "Gemini returned no readable text.\n\n"
                            + simplifyApiError(json)
            );
        }

        return result.toString();
    }

    /**
     * Finds a JSON string value for a key without regex.
     *
     * Example:
     * "uri":"https://..."
     */
    private static String findJsonStringValue(
            String json,
            String key
    ) {

        if (json == null || key == null) {
            return null;
        }

        String quotedKey =
                "\"" + key + "\"";

        int searchFrom = 0;

        while (searchFrom < json.length()) {

            int keyStart =
                    json.indexOf(
                            quotedKey,
                            searchFrom
                    );

            if (keyStart < 0) {
                return null;
            }

            int colon =
                    skipWhitespace(
                            json,
                            keyStart + quotedKey.length()
                    );

            if (colon >= json.length() ||
                    json.charAt(colon) != ':') {

                searchFrom =
                        keyStart + quotedKey.length();
                continue;
            }

            int valueStart =
                    skipWhitespace(
                            json,
                            colon + 1
                    );

            if (valueStart >= json.length() ||
                    json.charAt(valueStart) != '"') {

                searchFrom =
                        keyStart + quotedKey.length();
                continue;
            }

            ParsedString parsed =
                    readJsonString(
                            json,
                            valueStart
                    );

            if (parsed != null) {
                return parsed.value;
            }

            searchFrom =
                    keyStart + quotedKey.length();
        }

        return null;
    }

    private static ParsedString readJsonString(
            String json,
            int openingQuote
    ) {

        if (openingQuote < 0 ||
                openingQuote >= json.length() ||
                json.charAt(openingQuote) != '"') {

            return null;
        }

        StringBuilder out = new StringBuilder();

        for (int i = openingQuote + 1;
             i < json.length();
             i++) {

            char c = json.charAt(i);

            if (c == '"') {
                return new ParsedString(
                        out.toString(),
                        i + 1
                );
            }

            if (c != '\\') {
                out.append(c);
                continue;
            }

            if (i + 1 >= json.length()) {
                return null;
            }

            char escaped =
                    json.charAt(++i);

            switch (escaped) {

                case '"':
                    out.append('"');
                    break;

                case '\\':
                    out.append('\\');
                    break;

                case '/':
                    out.append('/');
                    break;

                case 'b':
                    out.append('\b');
                    break;

                case 'f':
                    out.append('\f');
                    break;

                case 'n':
                    out.append('\n');
                    break;

                case 'r':
                    out.append('\r');
                    break;

                case 't':
                    out.append('\t');
                    break;

                case 'u':

                    if (i + 4 >= json.length()) {
                        return null;
                    }

                    String hex =
                            json.substring(
                                    i + 1,
                                    i + 5
                            );

                    try {
                        out.append(
                                (char) Integer.parseInt(
                                        hex,
                                        16
                                )
                        );
                    } catch (NumberFormatException ex) {
                        return null;
                    }

                    i += 4;
                    break;

                default:
                    /*
                     * Preserve unknown escaped characters rather than
                     * crashing the whole response parser.
                     */
                    out.append(escaped);
                    break;
            }
        }

        return null;
    }

    private static int skipWhitespace(
            String value,
            int index
    ) {

        int i = index;

        while (i < value.length()) {

            char c = value.charAt(i);

            if (!Character.isWhitespace(c)) {
                break;
            }

            i++;
        }

        return i;
    }

    // =========================================================
    // JSON GENERATION
    // =========================================================

    private static String jsonEscape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        StringBuilder out =
                new StringBuilder(
                        value.length() + 32
                );

        for (int i = 0;
             i < value.length();
             i++) {

            char c = value.charAt(i);

            switch (c) {

                case '\\':
                    out.append("\\\\");
                    break;

                case '"':
                    out.append("\\\"");
                    break;

                case '\b':
                    out.append("\\b");
                    break;

                case '\f':
                    out.append("\\f");
                    break;

                case '\n':
                    out.append("\\n");
                    break;

                case '\r':
                    out.append("\\r");
                    break;

                case '\t':
                    out.append("\\t");
                    break;

                default:

                    if (c < 32) {
                        out.append("\\u");

                        String hex =
                                Integer.toHexString(c);

                        for (int j = hex.length();
                             j < 4;
                             j++) {
                            out.append('0');
                        }

                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }

        return out.toString();
    }

    // =========================================================
    // HTTP / ERROR HELPERS
    // =========================================================

    private static void requireSuccess(
            HttpResponse<String> response,
            String operation
    ) throws IOException {

        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        String detail =
                simplifyApiError(
                        response.body()
                );

        if (status == 401) {
            detail +=
                    "\n\nAuthentication was rejected by Gemini. "
                            + "Check that the API key belongs to a project "
                            + "with the Gemini API enabled.";
        }

        throw new IOException(
                operation
                        + " failed (HTTP "
                        + status
                        + ").\n\n"
                        + detail
        );
    }

    private static boolean isTransientGenerationError(
            Exception ex
    ) {

        String message = ex.getMessage();

        if (message == null) {
            return false;
        }

        String lower =
                message.toLowerCase(Locale.ROOT);

        /*
         * Gemini recommends retrying transient 429 and 5xx responses.
         * 408 is also safe to retry for a direct REST client.
         */
        return lower.contains("http 408")
                || lower.contains("http 429")
                || lower.contains("http 500")
                || lower.contains("http 502")
                || lower.contains("http 503")
                || lower.contains("http 504")
                || lower.contains("service unavailable")
                || lower.contains("high demand")
                || lower.contains("temporarily unavailable");
    }

    private static boolean isRetryableModelError(
            Exception ex
    ) {

        String message = ex.getMessage();

        if (message == null) {
            return false;
        }

        String lower =
                message.toLowerCase(Locale.ROOT);

        return lower.contains("404")
                || lower.contains("not found")
                || lower.contains("unsupported model");
    }

    private static String simplifyApiError(
            String body
    ) {

        if (body == null || body.isBlank()) {
            return "No additional error details were returned.";
        }

        String message =
                findJsonStringValue(
                        body,
                        "message"
                );

        if (message != null && !message.isBlank()) {
            return message;
        }

        return safeBody(body);
    }

    private static String findNestedErrorMessage(
            String body
    ) {

        String message =
                findJsonStringValue(
                        body,
                        "message"
                );

        if (message == null || message.isBlank()) {
            return null;
        }

        return message;
    }

    private static String safeBody(
            String body
    ) {

        if (body == null || body.isBlank()) {
            return "No response body.";
        }

        final int limit = 3000;

        if (body.length() <= limit) {
            return body;
        }

        return body.substring(0, limit)
                + "\n...";
    }

    private static String urlEncode(
            String value
    ) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    // =========================================================
    // INTERNAL TYPES
    // =========================================================

    private static final class UploadedFile {

        private final String name;
        private final String uri;
        private final String mimeType;

        private UploadedFile(
                String name,
                String uri,
                String mimeType
        ) {
            this.name = name;
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }

    private static final class ParsedString {

        private final String value;
        private final int nextIndex;

        private ParsedString(
                String value,
                int nextIndex
        ) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
