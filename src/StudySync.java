
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * StudySync application controller.
 *
 * Owns application state (selected folder, subject, resources and AI
 * selection), coordinates events, and runs the AI workflow:
 *
 * UI (presentation) ---> StudySync ---> DataFetch (files / folders)
 * |
 * +--> DocumentService
 * +--> DocumentContextBuilder
 * +--> AIService (LocalAI / Ollama / Qwen)
 *
 * StudySync depends on DataFetch and UI; neither of them depends on
 * StudySync.
 */
public class StudySync extends JFrame {

        // ---------- UI ----------
        private final UI ui;

        // ---------- DATA ----------
        private File selectedOneDriveFolder;
        private File selectedSubjectFolder;

        private List<File> currentFiles = new ArrayList<>();

        // Local document-processing and AI services.
        private final DocumentService documentService = new DocumentService();
        private final DocumentContextBuilder documentContextBuilder = new DocumentContextBuilder();
        private final AIService localAI = new LocalAI();

        // Maximum number of document visuals passed to Qwen per request.
        private static final int MAX_AI_IMAGES = 5;

        // Absolute paths of resources selected for AI.
        private final Set<String> selectedAiFiles = new LinkedHashSet<>();

        // Data layer: OneDrive folders, subjects, resource files.
        private final DataFetch dataFetch = new DataFetch();

        public StudySync() {

                // The theme must be applied before any component is created.
                UI.applyDarkTheme();

                setTitle("StudySync");
                setSize(1350, 820);
                setMinimumSize(new Dimension(1100, 700));
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setLocationRelativeTo(null);

                // =========================================================
                // BUILD MAIN WINDOW
                // =========================================================

                ui = new UI();
                ui.installInto(this);

                // =========================================================
                // SELECT ONEDRIVE FOLDER
                // =========================================================

                ui.onSelectFolder(this::selectOneDriveFolder);

                // =========================================================
                // SUBJECT SELECTION
                // =========================================================

                ui.onSubjectSelected(selected -> {

                        if (selected != null &&
                                        selectedOneDriveFolder != null) {

                                selectedSubjectFolder = new File(
                                                selectedOneDriveFolder,
                                                selected);

                                clearAiSelection();
                                loadCategories();
                                showAllResources();
                        }
                });

                // =========================================================
                // CATEGORY SELECTION
                // =========================================================

                ui.onCategorySelected(this::refreshResources);

                // =========================================================
                // SEARCH
                // =========================================================

                ui.onSearchChanged(this::refreshResources);

                // =========================================================
                // TYPE FILTER
                // =========================================================

                ui.onTypeFilterChanged(this::refreshResources);

                // =========================================================
                // STUDYSYNC AI PANEL
                // =========================================================

                ui.onAskAi(this::askSelectedFilesWithAi);
                ui.onAiQueryChanged(this::updateAiSelectionUi);
        }

        // =============================================================
        // SELECT ONEDRIVE FOLDER
        // =============================================================

        private void selectOneDriveFolder() {

                File folder = UI.chooseFolder(
                                this,
                                "Select your OneDrive folder",
                                dataFetch.getDefaultOneDriveLocation());

                if (folder != null) {

                        selectedOneDriveFolder = folder;

                        ui.showSelectedFolderPath(
                                        selectedOneDriveFolder
                                                        .getAbsolutePath());

                        clearAiSelection();
                        loadSubjects(selectedOneDriveFolder);
                }
        }

        // =============================================================
        // AI SELECTION STATE
        // =============================================================

        private void updateAiSelectionUi() {
                ui.updateAiSelection(selectedAiFiles.size());
        }

        private void clearAiSelection() {

                selectedAiFiles.clear();

                ui.clearAiQuery();

                updateAiSelectionUi();
        }

        // =============================================================
        // LOAD SUBJECTS
        // =============================================================

        private void loadSubjects(File oneDriveFolder) {

                ui.clearSubjects();
                ui.clearCategories();
                ui.clearResources();

                selectedSubjectFolder = null;
                currentFiles.clear();

                if (!dataFetch.isValidFolder(oneDriveFolder)) {
                        return;
                }

                List<String> subjects = dataFetch.getSubjects(oneDriveFolder);

                ui.displaySubjects(subjects);

                ui.refreshResourcePanel();
        }

        // =============================================================
        // LOAD CATEGORIES
        // =============================================================

        private void loadCategories() {
                ui.displayCategories(dataFetch.getCategories());
        }

        // =============================================================
        // SHOW ALL RESOURCES
        // =============================================================

        private void showAllResources() {

                if (selectedSubjectFolder == null) {
                        return;
                }

                currentFiles = dataFetch.getFilesRecursively(selectedSubjectFolder);

                ui.clearCategorySelection();
                refreshResources();
        }

        // =============================================================
        // REFRESH RESOURCES
        // =============================================================

        private void refreshResources() {

                ui.clearResources();

                if (selectedSubjectFolder == null) {
                        ui.refreshResourcePanel();
                        return;
                }

                List<File> matchingFiles = dataFetch.filterResources(
                                currentFiles,
                                ui.getSelectedCategory(),
                                ui.getSelectedType(),
                                ui.getSearchText());

                int displayed = 0;

                for (File file : matchingFiles) {
                        addResourceCard(file);
                        displayed++;
                }

                if (displayed == 0) {
                        ui.showNoResourcesMessage();
                }

                ui.refreshResourcePanel();
        }

        // =============================================================
        // RESOURCE CARD
        // =============================================================

        private void addResourceCard(File file) {

                String path = file.getAbsolutePath();

                ui.addResourceCard(
                                file,
                                dataFetch.getFileType(file),
                                dataFetch.getCategory(file),
                                selectedAiFiles.contains(path),
                                selected -> {
                                        if (selected) {
                                                selectedAiFiles.add(path);
                                        } else {
                                                selectedAiFiles.remove(path);
                                        }
                                        updateAiSelectionUi();
                                },
                                () -> openFile(file));
        }

        // =============================================================
        // ASK AI ABOUT SELECTED FILES
        // =============================================================

        private void askSelectedFilesWithAi() {

                String query = ui.getAiQuery().trim();

                if (query.isEmpty()) {

                        UI.showWarning(
                                        this,
                                        "Enter a question for StudySync AI.",
                                        "AI Query");

                        return;
                }

                if (selectedAiFiles.isEmpty()) {

                        UI.showWarning(
                                        this,
                                        "Select at least one resource first.",
                                        "No Resources Selected");

                        return;
                }

                List<File> files = dataFetch.getReadableFiles(selectedAiFiles);

                if (files.isEmpty()) {

                        UI.showError(
                                        this,
                                        "None of the selected resources are available locally.\n\n"
                                                        + "For OneDrive files, choose "
                                                        + "\"Always Keep on This Device\".",
                                        "AI Error");

                        return;
                }

                showMultiFileAiDialog(files, query);
        }

        /**
         * Sends selected StudySync resources through the local AI pipeline.
         *
         * Pipeline:
         *
         * Selected files
         * -> DocumentService
         * -> DocumentModel(s)
         * -> DocumentContextBuilder
         * -> AIRequest
         * -> LocalAI / Ollama / Qwen3.5 4B
         *
         * No cloud API or Gemini service is used here.
         */
        private String askLocalAI(
                        List<File> files,
                        String question) throws Exception {

                if (files == null || files.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "No resources were selected for AI analysis.");
                }

                if (question == null || question.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "AI question cannot be empty.");
                }

                // Parse the original documents once for the initial request.
                // Follow-up questions can reuse the parsed DocumentModel instances.
                List<DocumentModel> documents = documentService.processFiles(files);
                return askLocalAIWithDocuments(documents, question);
        }

        /**
         * Runs the AI pipeline using already-processed documents.
         * Follow-up questions use this method so PDFs, DOCX files and PPTX files
         * are not parsed again and PDF pages are not rendered again.
         */
        private String askLocalAIWithDocuments(
                        List<DocumentModel> documents,
                        String question) throws Exception {

                return askLocalAIWithDocuments(documents, question, false);
        }

        private String askLocalAIWithDocuments(
                        List<DocumentModel> documents,
                        String question,
                        boolean forceVisualContext) throws Exception {

                if (documents == null || documents.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "No processed resources are available for AI analysis.");
                }

                if (question == null || question.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "AI question cannot be empty.");
                }

                // Context is rebuilt because the user's question changes between turns.
                String context = documentContextBuilder.build(
                                documents,
                                question);

                // Reuse visual data already generated by the document processors.
                // For follow-ups, only send visuals when the question actually refers
                // to visual material. This avoids repeatedly sending several images
                // to Qwen when the user is asking a text-only follow-up.
                List<DocumentImage> images = (forceVisualContext || shouldUseVisualContext(question))
                                ? collectAIImages(documents)
                                : new ArrayList<>();

                System.out.println(
                                "StudySync LocalAI: using "
                                                + documents.size()
                                                + " cached document(s), "
                                                + images.size()
                                                + " visual input(s).");

                AIRequest request = new AIRequest(
                                question,
                                context,
                                images);

                AIResponse response = localAI.ask(request);

                if (response == null ||
                                response.getContent() == null ||
                                response.getContent().trim().isEmpty()) {

                        throw new IOException(
                                        "LocalAI returned an empty response.");
                }

                return response.getContent();
        }

        private boolean shouldUseVisualContext(String question) {

                if (question == null || question.trim().isEmpty()) {
                        return false;
                }

                String q = question.toLowerCase();

                String[] visualKeywords = {
                                "image",
                                "diagram",
                                "figure",
                                "fig.",
                                "chart",
                                "graph",
                                "plot",
                                "flowchart",
                                "illustration",
                                "visual",
                                "screenshot",
                                "picture",
                                "shown",
                                "shown in",
                                "looks like",
                                "what does this figure",
                                "what does this diagram",
                                "explain the figure",
                                "explain the diagram"
                };

                for (String keyword : visualKeywords) {
                        if (q.contains(keyword)) {
                                return true;
                        }
                }

                return false;
        }

        private List<DocumentImage> collectAIImages(
                        List<DocumentModel> documents) {

                List<DocumentImage> images = new ArrayList<>();

                if (documents == null || documents.isEmpty()) {
                        return images;
                }

                for (DocumentModel document : documents) {

                        if (document == null || !document.hasImages()) {
                                continue;
                        }

                        for (DocumentImage image : document.getImages()) {

                                if (image == null ||
                                                image.getImageData() == null ||
                                                image.getImageData().length == 0) {
                                        continue;
                                }

                                images.add(image);

                                if (images.size() >= MAX_AI_IMAGES) {
                                        return images;
                                }
                        }
                }

                return images;
        }

        private void showMultiFileAiDialog(
                        List<File> files,
                        String query) {

                JDialog dialog = UI.createAiDialog(this);

                // ---- Header ----
                JLabel info = UI.createAiStatusLabel(
                                "Preparing " + files.size()
                                                + " selected resource"
                                                + (files.size() == 1 ? "" : "s")
                                                + "...");

                JPanel header = UI.createAiDialogHeader(info);

                // ---- Output pane ----
                JEditorPane output = UI.createChatOutputPane();

                List<String> conversation = new ArrayList<>();
                conversation.add("USER: " + query);

                // Cache parsed documents for the lifetime of this AI dialog.
                // Follow-up questions reuse this data instead of reparsing files.
                final List<DocumentModel> cachedDocuments = new ArrayList<>();
                final boolean[] resourcesCached = { false };

                // Copy only the latest AI answer, not the entire conversation pane.
                final String[] latestAiResponse = { "" };

                StringBuilder chatHtml = new StringBuilder();
                chatHtml.append(UI.buildChatHtmlHead());
                chatHtml.append(UI.buildPreparingResourcesHtml(files, query));

                output.setText(chatHtml.toString());
                output.setCaretPosition(0);

                JScrollPane scrollPane = UI.createChatScrollPane(output);

                // ---- Chat input ----
                JTextField chatField = UI.createChatInputField();

                JButton sendButton = UI.createSendButton();

                JButton closeButton = UI.createCloseButton();

                JButton copyButton = UI.createCopyButton();

                copyButton.addActionListener(e -> {

                        try {
                                String textToCopy = latestAiResponse[0];

                                if (textToCopy == null ||
                                                textToCopy.trim().isEmpty()) {
                                        return;
                                }

                                UI.copyToClipboard(textToCopy);

                                UI.showTemporaryText(
                                                copyButton,
                                                "Copied!",
                                                "Copy",
                                                1200);

                        } catch (Exception ex) {

                                UI.showError(
                                                dialog,
                                                "Could not copy the AI response.\n\n"
                                                                + ex.getMessage(),
                                                "Copy Error");
                        }
                });

                JPanel bottom = UI.createChatBottomPanel(
                                chatField,
                                sendButton,
                                copyButton,
                                closeButton);

                UI.assembleAiDialog(dialog, header, scrollPane, bottom);

                closeButton.addActionListener(e -> dialog.dispose());

                // ---- Chat logic ----
                final boolean[] requestRunning = { true };

                Runnable sendMessage = () -> {

                        String message = chatField.getText().trim();

                        if (message.isEmpty() || requestRunning[0]) {
                                return;
                        }

                        chatField.setText("");
                        requestRunning[0] = true;
                        sendButton.setEnabled(false);
                        chatField.setEnabled(false);

                        conversation.add("USER: " + message);

                        UI.appendChatMessage(output, chatHtml,
                                        "You", message, false);

                        String context = buildConversationInstruction(conversation);

                        info.setText("StudySync AI is thinking...");

                        SwingWorker<String, Void> followUpWorker = new SwingWorker<>() {

                                @Override
                                protected String doInBackground() throws Exception {
                                        if (!resourcesCached[0] || cachedDocuments.isEmpty()) {
                                                throw new IllegalStateException(
                                                                "The selected resources are still being prepared. Please wait for the first answer.");
                                        }

                                        return askLocalAIWithDocuments(
                                                        cachedDocuments,
                                                        context,
                                                        shouldUseVisualContext(message));
                                }

                                @Override
                                protected void done() {
                                        try {
                                                String result = get();
                                                latestAiResponse[0] = result;
                                                conversation.add("ASSISTANT: " + result);
                                                UI.appendChatMessage(output, chatHtml,
                                                                "StudySync AI", result, true);

                                                info.setText("Conversation • "
                                                                + files.size()
                                                                + " selected resource"
                                                                + (files.size() == 1 ? "" : "s"));

                                        } catch (Exception ex) {
                                                Throwable cause = ex.getCause() != null
                                                                ? ex.getCause()
                                                                : ex;
                                                UI.appendChatMessage(output, chatHtml,
                                                                "StudySync AI",
                                                                "I couldn't process that follow-up.\n\n"
                                                                                + safeMessage(cause),
                                                                true);
                                                info.setText("Follow-up failed");

                                        } finally {
                                                requestRunning[0] = false;
                                                sendButton.setEnabled(
                                                                !chatField.getText().trim().isEmpty());
                                                chatField.setEnabled(true);
                                                chatField.requestFocusInWindow();
                                        }
                                }
                        };

                        followUpWorker.execute();
                };

                sendButton.addActionListener(e -> sendMessage.run());
                chatField.addActionListener(e -> sendMessage.run());

                chatField.getDocument()
                                .addDocumentListener(
                                                new DocumentListener() {
                                                        private void update() {
                                                                sendButton.setEnabled(
                                                                                !requestRunning[0]
                                                                                                && !chatField.getText()
                                                                                                                .trim()
                                                                                                                .isEmpty());
                                                        }

                                                        @Override
                                                        public void insertUpdate(DocumentEvent e) {
                                                                update();
                                                        }

                                                        @Override
                                                        public void removeUpdate(DocumentEvent e) {
                                                                update();
                                                        }

                                                        @Override
                                                        public void changedUpdate(DocumentEvent e) {
                                                                update();
                                                        }
                                                });

                // First request
                SwingWorker<String, Void> worker = new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                                List<DocumentModel> documents = documentService.processFiles(files);

                                if (documents == null || documents.isEmpty()) {
                                        throw new IllegalStateException(
                                                        "No readable document content was produced.");
                                }

                                // Cache parsed documents so follow-up questions do not
                                // reopen files or render PDF pages again.
                                cachedDocuments.clear();
                                cachedDocuments.addAll(documents);
                                resourcesCached[0] = true;

                                SwingUtilities.invokeLater(
                                                () -> info.setText("StudySync AI is thinking..."));

                                return askLocalAIWithDocuments(
                                                cachedDocuments,
                                                query,
                                                true);
                        }

                        @Override
                        protected void done() {
                                try {
                                        String result = get();
                                        latestAiResponse[0] = result;
                                        conversation.add("ASSISTANT: " + result);

                                        chatHtml.setLength(0);
                                        chatHtml.append(UI.buildChatHtmlHead());
                                        chatHtml.append(UI.buildAiAnswerHtml(result));

                                        output.setText(chatHtml.toString());
                                        output.setCaretPosition(0);

                                        info.setText("Conversation • "
                                                        + files.size()
                                                        + " selected resource"
                                                        + (files.size() == 1 ? "" : "s"));

                                        requestRunning[0] = false;
                                        sendButton.setEnabled(false);
                                        chatField.setEnabled(true);
                                        chatField.requestFocusInWindow();

                                } catch (Exception ex) {
                                        Throwable cause = ex.getCause() != null
                                                        ? ex.getCause()
                                                        : ex;

                                        chatHtml.setLength(0);
                                        chatHtml.append(UI.buildChatHtmlHead());
                                        chatHtml.append(UI.buildAiErrorHtml(safeMessage(cause)));

                                        output.setText(chatHtml.toString());
                                        output.setCaretPosition(0);

                                        info.setText("Analysis failed");

                                        requestRunning[0] = false;
                                        chatField.setEnabled(true);
                                        sendButton.setEnabled(false);
                                }
                        }
                };

                worker.execute();
                dialog.setVisible(true);
        }

        private String buildConversationInstruction(
                        List<String> conversation) {

                StringBuilder history = new StringBuilder();

                for (String turn : conversation) {
                        history.append(turn).append("\n\n");
                }

                return """
                                You are StudySync AI continuing an ongoing conversation
                                about the original academic resources uploaded by the user.

                                Use the uploaded files as the primary source.

                                Conversation so far:
                                %s

                                Answer the user's latest question directly.
                                Maintain continuity with the previous turns.
                                Do not ask the user to re-upload or describe the files.
                                If the files do not support an answer, say so clearly.
                                Keep the answer practical and useful for a university student.
                                """.formatted(history);
        }

        // =============================================================
        // OPEN FILE
        // =============================================================

        private void openFile(File file) {

                try {

                        if (!dataFetch.isAvailableLocally(file)) {

                                UI.showWarning(
                                                this,
                                                "File is not available locally.\n"
                                                                + "Please mark it as "
                                                                + "\"Always Keep on This Device\" "
                                                                + "in OneDrive.",
                                                "File Not Available");

                                return;
                        }

                        if (!dataFetch.isDesktopOpenSupported()) {

                                UI.showError(
                                                this,
                                                "Desktop file opening is not supported "
                                                                + "on this system.",
                                                "Open Error");

                                return;
                        }

                        dataFetch.openFile(file);

                } catch (Exception ex) {

                        UI.showError(
                                        this,
                                        "The file couldn't be opened.\n\n"
                                                        + safeMessage(ex),
                                        "Open Error");
                }
        }

        // =============================================================
        // SAFE ERROR MESSAGE
        // =============================================================

        private String safeMessage(Throwable throwable) {

                if (throwable == null) {
                        return "Unknown error.";
                }

                String message = throwable.getMessage();

                if (message == null || message.trim().isEmpty()) {
                        return throwable.toString();
                }

                return message;
        }

        // =============================================================
        // MAIN
        // =============================================================

        public static void main(String[] args) {

                SwingUtilities.invokeLater(() -> {
                        StudySync app = new StudySync();
                        app.setVisible(true);
                });
        }
}
