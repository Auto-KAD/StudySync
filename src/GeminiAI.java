import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;

public class GeminiAI {

    /*
     * Stable Gemini model.
     *
     * You can change this later without touching the UI.
     */
    private static final String MODEL =
            "gemini-3.8-flash";

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL
                    + ":generateContent";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newHttpClient();

    // =========================================================
    // PUBLIC API
    // =========================================================

    public static String analyzeFile(
            File file,
            String instruction) throws Exception {

        if (file == null ||
                !file.exists() ||
                !file.isFile()) {

            throw new IOException(
                    "The selected file does not exist."
            );
        }

        if (!file.canRead()) {

            throw new IOException(
                    "The selected file is not readable locally.\n"
                            + "If this file is in OneDrive, select "
                            + "\"Always Keep on This Device\"."
            );
        }

         String apiKey =
                System.getenv("GEMINI_API_KEY");

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            throw new IllegalStateException(
                    "GEMINI_API_KEY is not configured.\n\n"
                            + "Set the environment variable before "
                            + "running StudySync."
            );
        }

        String mimeType =
                getMimeType(file);

        byte[] fileBytes =
                Files.readAllBytes(
                        file.toPath()
                );

        String base64 =
                Base64.getEncoder()
                        .encodeToString(fileBytes);

        String prompt =
                buildPrompt(
                        file,
                        instruction
                );

        String requestBody =
                buildRequestBody(
                        mimeType,
                        base64,
                        prompt
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        API_URL
                                                + "?key="
                                                + apiKey
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody
                                )
                        )
                        .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() < 200 ||
                response.statusCode() >= 300) {

            throw new IOException(
                    "Gemini API error "
                            + response.statusCode()
                            + ":\n\n"
                            + response.body()
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

        return """
                You are the AI assistant inside StudySync,
                a Student Resource Management System.

                Analyze the supplied academic resource carefully.

                Resource:
                %s

                User request:
                %s

                Instructions:
                - Base your answer primarily on the supplied resource.
                - Do not invent information that is not supported by it.
                - If something cannot be determined from the resource,
                  explicitly say so.
                - Use clear academic formatting.
                - Prefer headings and bullet points where useful.
                - Keep the response useful for a university student.
                """.formatted(
                file.getName(),
                instruction
        );
    }

    // =========================================================
    // REQUEST JSON
    // =========================================================

    private static String buildRequestBody(
            String mimeType,
            String base64,
            String prompt) {

        return """
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "inline_data": {
                            "mime_type": "%s",
                            "data": "%s"
                          }
                        },
                        {
                          "text": "%s"
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.2
                  }
                }
                """.formatted(
                escapeJson(mimeType),
                base64,
                escapeJson(prompt)
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

        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        if (name.endsWith(".doc")) {
            return "application/msword";
        }

        if (name.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }

        if (name.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
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

        return "application/octet-stream";
    }

    // =========================================================
    // RESPONSE PARSER
    // =========================================================

    private static String extractResponseText(
            String json) {

        String marker =
                "\"text\"";

        int textPosition =
                json.indexOf(marker);

        if (textPosition < 0) {

            return "Gemini returned no readable text.\n\n"
                    + json;
        }

        int colon =
                json.indexOf(
                        ':',
                        textPosition + marker.length()
                );

        if (colon < 0) {
            return json;
        }

        int firstQuote =
                json.indexOf(
                        '"',
                        colon + 1
                );

        if (firstQuote < 0) {
            return json;
        }

        StringBuilder result =
                new StringBuilder();

        boolean escaped = false;

        for (int i = firstQuote + 1;
             i < json.length();
             i++) {

            char c =
                    json.charAt(i);

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

                    default:
                        result.append(c);
                }

                escaped = false;
                continue;
            }

            if (c == '\\') {

                escaped = true;
                continue;
            }

            if (c == '"') {
                break;
            }

            result.append(c);
        }

        return result.toString().trim();
    }

    // =========================================================
    // JSON ESCAPE
    // =========================================================

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
}