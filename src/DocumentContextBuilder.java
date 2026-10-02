import java.io.File;
import java.util.List;

public class DocumentContextBuilder {

    /*
     * Maximum amount of document text that will be placed
     * into a single AI request.
     *
     * This is deliberately kept below the model's context
     * window because the prompt, instructions and AI response
     * also need context space.
     */
    private static final int MAX_CONTEXT_CHARS = 8000;

    public String build(
            DocumentModel document,
            String userQuery) {

        if (document == null) {

            throw new IllegalArgumentException(
                    "Document cannot be null.");
        }

        return build(
                List.of(document),
                userQuery);
    }

    public String build(
            List<DocumentModel> documents,
            String userQuery) {

        if (documents == null ||
                documents.isEmpty()) {

            throw new IllegalArgumentException(
                    "No documents supplied.");
        }

        String query = userQuery == null
                ? ""
                : userQuery.trim();

        StringBuilder context = new StringBuilder();

        // =========================================================
        // SYSTEM INSTRUCTIONS
        // =========================================================

        context.append(
                "You are StudySync AI, "
                        + "a local academic assistant.\n\n");

        context.append(
                "IMPORTANT INSTRUCTIONS\n");

        context.append(
                "----------------------\n");

        context.append(
                "Answer primarily from the supplied "
                        + "academic resources.\n");

        context.append(
                "Use information from ALL supplied resources "
                        + "when relevant.\n");

        context.append(
                "Do not invent information that is not "
                        + "supported by the resources.\n");

        context.append(
                "If the resources do not contain enough "
                        + "information, say so clearly.\n");

        context.append(
                "Give practical, student-friendly answers.\n\n");

        // =========================================================
        // USER QUESTION
        // =========================================================

        if (!query.isBlank()) {

            context.append(
                    "USER QUESTION\n");

            context.append(
                    "-------------\n");

            context.append(query);

            context.append("\n\n");
        }

        // =========================================================
        // RESOURCE INFORMATION
        // =========================================================

        context.append(
                "SELECTED RESOURCES\n");

        context.append(
                "==================\n\n");

        /*
         * Divide the available context approximately equally
         * between all selected documents.
         *
         * This prevents the first large document from consuming
         * the entire context window.
         */
        int perDocumentBudget = MAX_CONTEXT_CHARS / documents.size();

        /*
         * Keep a small minimum so that a document is not completely
         * ignored when several files are selected.
         */
        perDocumentBudget = Math.max(
                perDocumentBudget,
                1000);

        int totalUsed = 0;

        for (int i = 0; i < documents.size(); i++) {

            DocumentModel document = documents.get(i);

            String documentContext = document.toAIContext();

            if (documentContext == null) {
                documentContext = "";
            }

            context.append(
                    "RESOURCE "
                            + (i + 1)
                            + "\n");

            context.append(
                    "---------\n");

            /*
             * Remaining global budget.
             */
            int remainingGlobal = MAX_CONTEXT_CHARS - totalUsed;

            if (remainingGlobal <= 0) {

                context.append(
                        "[No additional context available.]\n\n");

                continue;
            }

            /*
             * Give this document its fair share while also
             * respecting the remaining global budget.
             */
            int documentBudget = Math.min(
                    perDocumentBudget,
                    remainingGlobal);

            if (documentContext.length() <= documentBudget) {

                context.append(
                        documentContext);

                totalUsed += documentContext.length();

            } else {

                context.append(
                        documentContext.substring(
                                0,
                                documentBudget));

                context.append(
                        "\n\n"
                                + "[This resource was truncated "
                                + "to preserve context for other "
                                + "selected resources.]\n");

                totalUsed += documentBudget;
            }

            context.append("\n\n");
        }

        return context.toString();
    }

    private String getResourceRole(DocumentModel document) {

        String fileName = document.getFileName();

        File file = new File(fileName);

        ResourceCluster.Category category = ResourceCluster.classify(file);

        switch (category) {

            case LAB:
                return "Laboratory / Practical Material";

            case LECTURE_SLIDES:
                return "Lecture Notes / Lecture Slides";

            case SYLLABUS_POLICY:
                return "Course Policy / Syllabus";

            case BOOKS:
                return "Book / Reference Material";

            case PREVIOUS_YEAR_PAPERS:
                return "Previous Year Question Paper";

            case MISCELLANEOUS:
            default:
                return "Miscellaneous Academic Resource";
        }
    }
}