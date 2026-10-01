import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StudySync Gemini AI
 *
 * Multimodal architecture:
 *
 * Local File
 *     ↓
 * Gemini Files API
 *     ↓
 * file_uri
 *     ↓
 * Gemini generateContent
 *     ↓
 * AI response
 *
 * This class does NOT use Tika.
 * It sends the original resource directly to Gemini.
 */
public class GeminiAI {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    private static final String FILE_UPLOAD_URL =
            "https://generativelanguage.googleapis.com/upload/v1beta/files";

    private static final String GENERATE_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    /*
     * Primary model first.
     *
     * If Gemini returns a temporary availability/rate-limit
     * error, the next model is attempted.
     */


    private static final String[] MODEL_LADDER = {
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
    };

    private static final int MAX_RETRIES = 2;

    private static final long RETRY_DELAY_MS = 1200;

    private static final long MODEL_COOLDOWN_MS = 60_000;

    private static final long FILE_READY_TIMEOUT_MS = 60_000;

    private static final long FILE_READY_POLL_MS = 800;


    // =========================================================
    // HTTP CLIENT
    // =========================================================

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(30)
                    )
                    .build();


    // =========================================================
    // MODEL COOLDOWNS
    // =========================================================

    private static final Map<String, Long> MODEL_COOLDOWNS =
            new ConcurrentHashMap<>();


    // =========================================================
    // UPLOADED FILE CACHE
    // =========================================================

    /*
     * Important performance optimization.
     *
     * If the same file is analyzed repeatedly during the
     * application's lifetime, don't upload it again.
     *
     * Cache key:
     *
     * absolute path + file size + last modified
     *
     * If the file changes, a new upload happens automatically.
     */
    private static final Map<String, UploadedFile> FILE_CACHE =
            new ConcurrentHashMap<>();


    // =========================================================
    // PUBLIC METHOD
    // =========================================================

    public static String analyzeFile(
            File file,
            String instruction) throws Exception {

        validateFile(file);

        String apiKey =
                getApiKey();

        String mimeType =
                getMimeType(file);

        String prompt =
                buildPrompt(
                        file,
                        instruction
                );


        // -----------------------------------------------------
        // UPLOAD / REUSE FILE
        // -----------------------------------------------------

        UploadedFile uploadedFile =
                getOrUploadFile(
                        file,
                        mimeType,
                        apiKey
                );


        // -----------------------------------------------------
        // MODEL FALLBACK
        // -----------------------------------------------------

        String lastError =
                "No Gemini model was available.";


        for (String model : MODEL_LADDER) {

            if (isCoolingDown(model)) {
                continue;
            }


            for (
                    int attempt = 1;
                    attempt <= MAX_RETRIES;
                    attempt++
            ) {

                try {

                    String response =
                            generateContent(
                                    model,
                                    uploadedFile,
                                    prompt,
                                    apiKey
                            );


                    if (
                            response != null &&
                                    !response.trim().isEmpty()
                    ) {

                        return response.trim();
                    }


                    lastError =
                            "Gemini returned an empty response.";

                    break;

                } catch (GeminiException e) {

                    lastError =
                            sanitizeError(
                                    e.getMessage()
                            );


                    int status =
                            e.getStatusCode();


                    // -----------------------------------------
                    // MODEL NOT FOUND
                    // -----------------------------------------

                    if (status == 404) {

                        MODEL_COOLDOWNS.put(
                                model,
                                System.currentTimeMillis()
                                        + 3_600_000
                        );

                        break;
                    }


                    // -----------------------------------------
                    // RATE LIMIT
                    // -----------------------------------------

                    if (status == 429) {

                        MODEL_COOLDOWNS.put(
                                model,
                                System.currentTimeMillis()
                                        + MODEL_COOLDOWN_MS
                        );

                        break;
                    }


                    // -----------------------------------------
                    // TEMPORARY SERVER FAILURE
                    // -----------------------------------------

                    if (
                            status == 408 ||
                                    status == 500 ||
                                    status == 502 ||
                                    status == 503 ||
                                    status == 504
                    ) {

                        if (
                                attempt < MAX_RETRIES
                        ) {

                            sleep(
                                    RETRY_DELAY_MS
                                            * attempt
                            );

                            continue;
                        }


                        MODEL_COOLDOWNS.put(
                                model,
                                System.currentTimeMillis()
                                        + MODEL_COOLDOWN_MS
                        );

                        break;
                    }


                    // -----------------------------------------
                    // OTHER ERROR
                    // -----------------------------------------

                    throw new IOException(
                            "StudySync AI request failed.\n\n"
                                    + lastError
                    );
                }
            }
        }


        throw new IOException(
                "StudySync AI could not analyze this resource.\n\n"
                        + "The file was successfully sent to "
                        + "the Gemini multimodal system, but "
                        + "all available models failed.\n\n"
                        + lastError
        );
    }


    // =========================================================
    // GET OR UPLOAD FILE
    // =========================================================

    private static UploadedFile getOrUploadFile(
            File file,
            String mimeType,
            String apiKey) throws Exception {

        String cacheKey =
                file.getAbsolutePath()
                        + "|"
                        + file.length()
                        + "|"
                        + file.lastModified();


        UploadedFile cached =
                FILE_CACHE.get(cacheKey);


        if (
                cached != null &&
                        cached.uri != null &&
                        !cached.uri.isBlank()
        ) {

            return cached;
        }


        UploadedFile uploaded =
                uploadFile(
                        file,
                        mimeType,
                        apiKey
                );


        FILE_CACHE.put(
                cacheKey,
                uploaded
        );


        return uploaded;
    }


    // =========================================================
    // GEMINI FILE UPLOAD
    // =========================================================

    private static UploadedFile uploadFile(
            File file,
            String mimeType,
            String apiKey) throws Exception {

        long fileSize =
                Files.size(
                        file.toPath()
                );


        /*
         * STEP 1
         *
         * Start a resumable upload.
         */

        String metadataJson =
                """
                {
                  "file": {
                    "display_name": "%s"
                  }
                }
                """.formatted(
                        escapeJson(
                                file.getName()
                        )
                );


        HttpRequest startRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        FILE_UPLOAD_URL
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(30)
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
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
                                String.valueOf(
                                        fileSize
                                )
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
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                metadataJson,
                                                StandardCharsets.UTF_8
                                        )
                        )
                        .build();


        HttpResponse<String> startResponse =
                HTTP_CLIENT.send(
                        startRequest,
                        HttpResponse.BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                );


        if (
                startResponse.statusCode() < 200 ||
                        startResponse.statusCode() >= 300
        ) {

            throw new GeminiException(
                    "Gemini Files API upload initialization failed.\n"
                            + extractApiError(
                            startResponse.body()
                    ),
                    startResponse.statusCode()
            );
        }


        /*
         * STEP 2
         *
         * Gemini gives us a resumable upload URL.
         */

        String uploadUrl =
                getHeaderIgnoreCase(
                        startResponse,
                        "X-Goog-Upload-URL"
                );


        if (
                uploadUrl == null ||
                        uploadUrl.isBlank()
        ) {

            throw new IOException(
                    "Gemini did not return an upload URL."
            );
        }


        /*
         * STEP 3
         *
         * Send the actual ORIGINAL FILE.
         *
         * No Tika.
         * No text extraction.
         * No Base64.
         */

        byte[] fileBytes =
                Files.readAllBytes(
                        file.toPath()
                );

        HttpRequest uploadRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        uploadUrl
                                )
                        )
                        .timeout(
                                Duration.ofMinutes(5)
                        )
                        .header(
                                "X-Goog-Upload-Offset",
                                "0"
                        )
                        .header(
                                "X-Goog-Upload-Command",
                                "upload, finalize"
                        )
                        .header(
                                "Content-Type",
                                mimeType
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofByteArray(
                                                fileBytes
                                        )
                        )
                        .build();


        HttpResponse<String> uploadResponse =
                HTTP_CLIENT.send(
                        uploadRequest,
                        HttpResponse.BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                );


        if (
                uploadResponse.statusCode() < 200 ||
                        uploadResponse.statusCode() >= 300
        ) {

            throw new GeminiException(
                    "Gemini Files API upload failed.\n"
                            + extractApiError(
                            uploadResponse.body()
                    ),
                    uploadResponse.statusCode()
            );
        }


        /*
         * STEP 4
         *
         * Extract:
         *
         * file.name
         * file.uri
         * file.mimeType
         * file.state
         */

        String response =
                uploadResponse.body();


        String uri =
                extractJsonString(
                        response,
                        "uri"
                );


        String returnedMimeType =
                extractJsonString(
                        response,
                        "mimeType"
                );


        if (
                returnedMimeType == null ||
                        returnedMimeType.isBlank()
        ) {

            returnedMimeType =
                    mimeType;
        }


        if (
                uri == null ||
                        uri.isBlank()
        ) {

            throw new IOException(
                    "Gemini uploaded the file but did not "
                            + "return a file URI."
            );
        }


        /*
         * Files API processing is normally quick, but for
         * some document types Gemini may need a short period
         * before the file is ready.
         */

        String fileName =
                extractJsonString(
                        response,
                        "name"
                );


        String state =
                extractJsonString(
                        response,
                        "state"
                );


        if (
                state != null &&
                        state.equalsIgnoreCase("PROCESSING")
        ) {

            waitForFileReady(
                    fileName,
                    apiKey
            );
        }


        return new UploadedFile(
                fileName,
                uri,
                returnedMimeType
        );
    }


    // =========================================================
    // WAIT FOR FILE READY
    // =========================================================

    private static void waitForFileReady(
            String fileName,
            String apiKey) throws Exception {

        if (
                fileName == null ||
                        fileName.isBlank()
        ) {

            return;
        }


        long start =
                System.currentTimeMillis();


        while (
                System.currentTimeMillis()
                        - start
                        < FILE_READY_TIMEOUT_MS
        ) {

            String url =
                    "https://generativelanguage.googleapis.com"
                            + "/v1beta/"
                            + fileName;


            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            url
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(15)
                            )
                            .header(
                                    "x-goog-api-key",
                                    apiKey
                            )
                            .GET()
                            .build();


            HttpResponse<String> response =
                    HTTP_CLIENT.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString(
                                            StandardCharsets.UTF_8
                                    )
                    );


            if (
                    response.statusCode() >= 200 &&
                            response.statusCode() < 300
            ) {

                String state =
                        extractJsonString(
                                response.body(),
                                "state"
                        );


                if (
                        state == null ||
                                state.equalsIgnoreCase("ACTIVE")
                ) {

                    return;
                }


                if (
                        state.equalsIgnoreCase(
                                "FAILED"
                        )
                ) {

                    throw new IOException(
                            "Gemini failed while processing "
                                    + "the uploaded file."
                    );
                }
            }


            sleep(
                    FILE_READY_POLL_MS
            );
        }


        throw new IOException(
                "Gemini took too long to process "
                        + "the uploaded resource."
        );
    }


    // =========================================================
    // GENERATE CONTENT
    // =========================================================

    private static String generateContent(
            String model,
            UploadedFile file,
            String prompt,
            String apiKey) throws Exception {

        String url =
                GENERATE_BASE_URL
                        + model
                        + ":generateContent";


        /*
         * This is the important part.
         *
         * Gemini receives:
         *
         * 1. Text instruction
         * 2. ORIGINAL uploaded file reference
         *
         * It is therefore multimodal.
         */

        String requestBody =
                """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        {
                          "text": "%s"
                        },
                        {
                          "file_data": {
                            "mime_type": "%s",
                            "file_uri": "%s"
                          }
                        }
                      ]
                    }
                  ],
"generationConfig": {
    "thinkingConfig": {
        "thinkingLevel": "low"
    },
    "maxOutputTokens": 4096
}
                }
                """.formatted(
                        escapeJson(prompt),
                        escapeJson(file.mimeType),
                        escapeJson(file.uri)
                );


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        url
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(90)
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                requestBody,
                                                StandardCharsets.UTF_8
                                        )
                        )
                        .build();


        HttpResponse<String> response =
                HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                );


        int status =
                response.statusCode();


        if (
                status < 200 ||
                        status >= 300
        ) {

            throw new GeminiException(
                    "Gemini API returned HTTP "
                            + status
                            + ".\n"
                            + extractApiError(
                            response.body()
                    ),
                    status
            );
        }


        return extractResponseText(
                response.body()
        );
    }


    // =========================================================
    // PROMPT
    // =========================================================

    private static String buildPrompt(
            File file,
            String instruction) {

        String userInstruction =
                instruction == null ||
                        instruction.trim().isEmpty()
                        ?
                        "Analyze this academic resource and explain its important content."
                        :
                        instruction.trim();


        return """
                You are StudySync AI, the multimodal academic
                assistant inside a Student Resource Management System.

                RESOURCE:
                %s

                USER REQUEST:
                %s

                Analyze the supplied resource directly.

                IMPORTANT:

                - Use the actual supplied resource as your primary source.
                - Do not invent information that is not present.
                - For PDFs, inspect the document content and structure.
                - For presentations, understand the slides, diagrams,
                  tables, text, and visual content.
                - For images, inspect the actual image.
                - For documents, use the actual document content.
                - Preserve technical terminology and formulas.
                - If the user asks about a particular page or slide,
                  focus on that part.
                - If information is unavailable in the resource,
                  clearly say so.
                - Explain concepts rather than merely copying them.
                - Use headings, bullets, numbered steps, and tables
                  when useful.
                - Answer at a university-student level.
                """.formatted(
                file.getName(),
                userInstruction
        );
    }


    // =========================================================
    // MIME TYPE
    // =========================================================

    private static String getMimeType(
            File file) {

        String name =
                file.getName()
                        .toLowerCase();


        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (name.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }

        if (name.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }

        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        if (name.endsWith(".doc")) {
            return "application/msword";
        }

        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        if (name.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }

        if (name.endsWith(".txt")) {
            return "text/plain";
        }

        if (name.endsWith(".csv")) {
            return "text/csv";
        }

        if (name.endsWith(".jpg") ||
                name.endsWith(".jpeg")) {

            return "image/jpeg";
        }

        if (name.endsWith(".png")) {
            return "image/png";
        }

        if (name.endsWith(".webp")) {
            return "image/webp";
        }

        if (name.endsWith(".gif")) {
            return "image/gif";
        }

        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }

        return "application/octet-stream";
    }


    // =========================================================
    // API KEY
    // =========================================================

    private static String getApiKey()
            throws IOException {

        /*
         * Your existing apiconfig.java already loads the
         * key from .env.
         *
         * Therefore GeminiAI does NOT contain the actual key.
         */

        String apiKey =
                apiconfig.GEMINI_API_KEY;


        if (
                apiKey == null ||
                        apiKey.trim().isEmpty()
        ) {

            throw new IOException(
                    "Gemini API key is not configured.\n\n"
                            + "Check your .env file."
            );
        }


        return apiKey.trim();
    }


    // =========================================================
    // VALIDATE FILE
    // =========================================================

    private static void validateFile(
            File file) throws IOException {

        if (
                file == null ||
                        !file.exists() ||
                        !file.isFile()
        ) {

            throw new IOException(
                    "The selected resource does not exist."
            );
        }


        if (!file.canRead()) {

            throw new IOException(
                    "The selected resource is not readable locally.\n\n"
                            + "If this file is in OneDrive, select "
                            + "\"Always Keep on This Device\"."
            );
        }


        if (file.length() == 0) {

            throw new IOException(
                    "The selected resource is empty."
            );
        }
    }


    // =========================================================
    // MODEL COOLDOWN
    // =========================================================

    private static boolean isCoolingDown(
            String model) {

        Long until =
                MODEL_COOLDOWNS.get(model);


        if (until == null) {
            return false;
        }


        if (
                System.currentTimeMillis()
                        < until
        ) {

            return true;
        }


        MODEL_COOLDOWNS.remove(model);

        return false;
    }


    // =========================================================
    // RESPONSE EXTRACTION
    // =========================================================

    private static String extractResponseText(
            String json) {

        StringBuilder output =
                new StringBuilder();


        int position = 0;


        while (true) {

            int index =
                    json.indexOf(
                            "\"text\"",
                            position
                    );


            if (index < 0) {
                break;
            }


            int colon =
                    json.indexOf(
                            ':',
                            index
                    );


            if (colon < 0) {
                break;
            }


            int quote =
                    findNextQuote(
                            json,
                            colon + 1
                    );


            if (quote < 0) {
                break;
            }


            int end =
                    findClosingQuote(
                            json,
                            quote + 1
                    );


            if (end < 0) {
                break;
            }


            String text =
                    decodeJsonString(
                            json.substring(
                                    quote + 1,
                                    end
                            )
                    );


            if (!text.isBlank()) {

                if (output.length() > 0) {
                    output.append("\n\n");
                }

                output.append(text);
            }


            position =
                    end + 1;
        }


        String result =
                output.toString().trim();


        if (!result.isEmpty()) {
            return result;
        }


        return "Gemini returned no readable text.";
    }


    // =========================================================
    // JSON STRING EXTRACTION
    // =========================================================

    private static String extractJsonString(
            String json,
            String key) {

        if (
                json == null ||
                        key == null
        ) {

            return null;
        }


        String marker =
                "\"" + key + "\"";


        int keyIndex =
                json.indexOf(marker);


        if (keyIndex < 0) {
            return null;
        }


        int colon =
                json.indexOf(
                        ':',
                        keyIndex + marker.length()
                );


        if (colon < 0) {
            return null;
        }


        int quote =
                findNextQuote(
                        json,
                        colon + 1
                );


        if (quote < 0) {
            return null;
        }


        int end =
                findClosingQuote(
                        json,
                        quote + 1
                );


        if (end < 0) {
            return null;
        }


        return decodeJsonString(
                json.substring(
                        quote + 1,
                        end
                )
        );
    }


    // =========================================================
    // JSON HELPERS
    // =========================================================

    private static int findNextQuote(
            String text,
            int start) {

        for (
                int i = start;
                i < text.length();
                i++
        ) {

            char c =
                    text.charAt(i);


            if (
                    Character.isWhitespace(c) ||
                            c == ':'
            ) {

                continue;
            }


            if (c == '"') {
                return i;
            }


            return -1;
        }


        return -1;
    }


    private static int findClosingQuote(
            String text,
            int start) {

        boolean escaped = false;


        for (
                int i = start;
                i < text.length();
                i++
        ) {

            char c =
                    text.charAt(i);


            if (escaped) {

                escaped = false;

                continue;
            }


            if (c == '\\') {

                escaped = true;

                continue;
            }


            if (c == '"') {

                return i;
            }
        }


        return -1;
    }


    private static String decodeJsonString(
            String value) {

        StringBuilder result =
                new StringBuilder();


        boolean escaped = false;


        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            char c =
                    value.charAt(i);


            if (!escaped) {

                if (c == '\\') {

                    escaped = true;

                } else {

                    result.append(c);
                }

                continue;
            }


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

                case 'b':
                    result.append('\b');
                    break;

                case 'f':
                    result.append('\f');
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

                default:
                    result.append(c);
                    break;
            }


            escaped = false;
        }


        return result.toString();
    }


    private static String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }


        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }


    // =========================================================
    // ERROR HANDLING
    // =========================================================

    private static String extractApiError(
            String body) {

        if (
                body == null ||
                        body.isBlank()
        ) {

            return "No additional error information.";
        }


        String message =
                extractJsonString(
                        body,
                        "message"
                );


        if (
                message != null &&
                        !message.isBlank()
        ) {

            return message;
        }


        if (body.length() > 1200) {

            return body.substring(
                    0,
                    1200
            ) + "...";
        }


        return body;
    }


    private static String sanitizeError(
            String error) {

        if (
                error == null ||
                        error.isBlank()
        ) {

            return "Unknown Gemini error.";
        }


        /*
         * Never expose the API key in StudySync's UI.
         */

        String cleaned =
                error.replaceAll(
                        "AIza[A-Za-z0-9_-]+",
                        "[REDACTED]"
                );


        cleaned =
                cleaned.replaceAll(
                        "AQ\\.[A-Za-z0-9_-]+",
                        "[REDACTED]"
                );


        if (cleaned.length() > 1500) {

            cleaned =
                    cleaned.substring(
                            0,
                            1500
                    ) + "...";
        }


        return cleaned;
    }


    // =========================================================
    // HEADER HELPER
    // =========================================================

    private static String getHeaderIgnoreCase(
            HttpResponse<?> response,
            String name) {

        return response.headers()
                .firstValue(name)
                .orElseGet(
                        () ->
                                response.headers()
                                        .map()
                                        .entrySet()
                                        .stream()
                                        .filter(
                                                entry ->
                                                        entry.getKey()
                                                                .equalsIgnoreCase(
                                                                        name
                                                                )
                                        )
                                        .flatMap(
                                                entry ->
                                                        entry.getValue()
                                                                .stream()
                                        )
                                        .findFirst()
                                        .orElse(null)
                );
    }


    // =========================================================
    // SLEEP
    // =========================================================

    private static void sleep(
            long milliseconds) {

        try {

            Thread.sleep(
                    milliseconds
            );

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();
        }
    }


    // =========================================================
    // UPLOADED FILE MODEL
    // =========================================================

    private static class UploadedFile {

        private final String name;

        private final String uri;

        private final String mimeType;


        private UploadedFile(
                String name,
                String uri,
                String mimeType) {

            this.name = name;
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }


    // =========================================================
    // GEMINI EXCEPTION
    // =========================================================

    private static class GeminiException
            extends Exception {

        private final int statusCode;


        private GeminiException(
                String message,
                int statusCode) {

            super(message);

            this.statusCode =
                    statusCode;
        }


        private int getStatusCode() {

            return statusCode;
        }
    }

}