import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class apiconfig {

    public static final String GEMINI_API_KEY =
            loadApiKey();

    private static String loadApiKey() {

        // 1. Try system environment variable first.
        String envKey =
                System.getenv(
                        "GEMINI_API_KEY"
                );

        if (envKey != null &&
                !envKey.trim().isEmpty()) {

            return envKey.trim();
        }

        // 2. Otherwise load from .env in project root.
        File envFile =
                new File(".env");

        if (!envFile.exists()) {

            throw new IllegalStateException(
                    "GEMINI_API_KEY not found.\n\n"
                            + "Create a .env file in the StudySync project root."
            );
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(envFile)
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty() ||
                        line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith(
                        "GEMINI_API_KEY="
                )) {

                    String key =
                            line.substring(
                                    "GEMINI_API_KEY=".length()
                            ).trim();

                    if (
                            (key.startsWith("\"")
                                    && key.endsWith("\""))
                                    ||
                                    (key.startsWith("'")
                                            && key.endsWith("'"))
                    ) {

                        key =
                                key.substring(
                                        1,
                                        key.length() - 1
                                );
                    }

                    if (!key.isEmpty()) {
                        return key;
                    }
                }
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Could not read .env file.",
                    e
            );
        }

        throw new IllegalStateException(
                "GEMINI_API_KEY not found inside .env."
        );
    }
}
