
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StudySync extends JFrame {

        // ---------- UI ----------
        private JLabel pathLabel;

        private JList<String> subjectList;
        private DefaultListModel<String> subjectModel;

        private JList<String> categoryList;
        private DefaultListModel<String> categoryModel;

        private JPanel resourcePanel;

        private JTextField searchField;
        private JComboBox<String> typeFilter;

        // StudySync AI
        private JPanel aiPanel;
        private JLabel aiSelectionLabel;
        private JTextField aiQueryField;
        private JButton aiAskButton;

        // ---------- DATA ----------
        private File selectedOneDriveFolder;
        private File selectedSubjectFolder;

        private List<File> currentFiles = new ArrayList<>();

        // Absolute paths of resources selected for AI.
        private final Set<String> selectedAiFiles = new LinkedHashSet<>();

        private static final String[] CATEGORIES = {
                        "Lab",
                        "Lecture Slides / PPTs",
                        "Syllabus / Course Policy",
                        "Books",
                        "Previous Year Papers",
                        "Miscellaneous"
        };

        public StudySync() {

                setTitle("StudySync");
                setSize(1250, 780);
                setMinimumSize(new Dimension(1050, 650));
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setLocationRelativeTo(null);

                // =========================================================
                // TOP BAR
                // =========================================================

                JPanel topPanel = new JPanel(new BorderLayout());
                topPanel.setBorder(
                                new EmptyBorder(10, 15, 10, 15));

                JLabel title = new JLabel("StudySync");
                title.setFont(
                                new Font("Arial", Font.BOLD, 26));

                pathLabel = new JLabel(
                                "No OneDrive folder selected");
                pathLabel.setForeground(Color.GRAY);
                pathLabel.setFont(
                                new Font("Arial", Font.PLAIN, 12));

                JPanel titlePanel = new JPanel(new BorderLayout());
                titlePanel.add(title, BorderLayout.NORTH);
                titlePanel.add(pathLabel, BorderLayout.SOUTH);

                JButton selectFolderButton = new JButton("Select OneDrive Folder");

                topPanel.add(titlePanel, BorderLayout.WEST);
                topPanel.add(selectFolderButton, BorderLayout.EAST);

                // =========================================================
                // SUBJECT LIST
                // =========================================================

                subjectModel = new DefaultListModel<>();

                subjectList = new JList<>(subjectModel);
                subjectList.setFont(
                                new Font("Arial", Font.PLAIN, 16));
                subjectList.setSelectionMode(
                                ListSelectionModel.SINGLE_SELECTION);

                JScrollPane subjectScroll = new JScrollPane(subjectList);

                subjectScroll.setBorder(
                                BorderFactory.createTitledBorder("Subjects"));

                // =========================================================
                // CATEGORY LIST
                // =========================================================

                categoryModel = new DefaultListModel<>();

                categoryList = new JList<>(categoryModel);
                categoryList.setFont(
                                new Font("Arial", Font.PLAIN, 14));
                categoryList.setSelectionMode(
                                ListSelectionModel.SINGLE_SELECTION);

                JScrollPane categoryScroll = new JScrollPane(categoryList);

                categoryScroll.setBorder(
                                BorderFactory.createTitledBorder(
                                                "Resource Clusters"));

                // =========================================================
                // STUDYSYNC AI PANEL
                // =========================================================

                aiPanel = createAiPanel();

                // =========================================================
                // LEFT SIDE
                // Subjects -> AI -> Resource Clusters
                // =========================================================

                JPanel leftPanel = new JPanel(new BorderLayout(5, 8));

                leftPanel.add(
                                subjectScroll,
                                BorderLayout.CENTER);

                JPanel bottomLeft = new JPanel(new BorderLayout(5, 8));

                bottomLeft.add(
                                aiPanel,
                                BorderLayout.NORTH);

                bottomLeft.add(
                                categoryScroll,
                                BorderLayout.CENTER);

                bottomLeft.setPreferredSize(
                                new Dimension(330, 420));

                leftPanel.add(
                                bottomLeft,
                                BorderLayout.SOUTH);

                leftPanel.setPreferredSize(
                                new Dimension(330, 650));

                // =========================================================
                // SEARCH + TYPE FILTER
                // =========================================================

                JPanel filterPanel = new JPanel(new BorderLayout(10, 5));

                searchField = new JTextField();
                searchField.setToolTipText(
                                "Search resources...");

                typeFilter = new JComboBox<>(
                                new String[] {
                                                "All Files",
                                                "PDF",
                                                "DOCX",
                                                "PPTX",
                                                "XLSX",
                                                "TXT",
                                                "Images"
                                });

                filterPanel.add(
                                new JLabel("Search:"),
                                BorderLayout.WEST);

                filterPanel.add(
                                searchField,
                                BorderLayout.CENTER);

                filterPanel.add(
                                typeFilter,
                                BorderLayout.EAST);

                // =========================================================
                // RESOURCE PANEL
                // =========================================================

                resourcePanel = new JPanel();

                resourcePanel.setLayout(
                                new BoxLayout(
                                                resourcePanel,
                                                BoxLayout.Y_AXIS));

                JScrollPane resourceScroll = new JScrollPane(resourcePanel);

                resourceScroll.setBorder(
                                BorderFactory.createTitledBorder(
                                                "Resources"));

                JPanel rightPanel = new JPanel(new BorderLayout(5, 5));

                rightPanel.add(
                                filterPanel,
                                BorderLayout.NORTH);

                rightPanel.add(
                                resourceScroll,
                                BorderLayout.CENTER);

                // =========================================================
                // MAIN SPLIT
                // =========================================================

                JSplitPane mainSplit = new JSplitPane(
                                JSplitPane.HORIZONTAL_SPLIT,
                                leftPanel,
                                rightPanel);

                mainSplit.setDividerLocation(330);
                mainSplit.setResizeWeight(0.0);

                setLayout(new BorderLayout());

                add(topPanel, BorderLayout.NORTH);
                add(mainSplit, BorderLayout.CENTER);

                // =========================================================
                // SELECT ONEDRIVE FOLDER
                // =========================================================

                selectFolderButton.addActionListener(e -> {

                        JFileChooser chooser = new JFileChooser();

                        chooser.setDialogTitle(
                                        "Select your OneDrive folder");

                        chooser.setFileSelectionMode(
                                        JFileChooser.DIRECTORIES_ONLY);

                        File cloudStorage = new File(
                                        System.getProperty("user.home"),
                                        "Library/CloudStorage");

                        if (cloudStorage.exists()) {
                                chooser.setCurrentDirectory(
                                                cloudStorage);
                        }

                        int result = chooser.showOpenDialog(this);

                        if (result == JFileChooser.APPROVE_OPTION) {

                                selectedOneDriveFolder = chooser.getSelectedFile();

                                pathLabel.setText(
                                                selectedOneDriveFolder
                                                                .getAbsolutePath());

                                clearAiSelection();
                                loadSubjects(
                                                selectedOneDriveFolder);
                        }
                });

                // =========================================================
                // SUBJECT SELECTION
                // =========================================================

                subjectList.addListSelectionListener(e -> {

                        if (!e.getValueIsAdjusting()) {

                                String selected = subjectList.getSelectedValue();

                                if (selected != null &&
                                                selectedOneDriveFolder != null) {

                                        selectedSubjectFolder = new File(
                                                        selectedOneDriveFolder,
                                                        selected);

                                        clearAiSelection();
                                        loadCategories();
                                        showAllResources();
                                }
                        }
                });

                // =========================================================
                // CATEGORY SELECTION
                // =========================================================

                categoryList.addListSelectionListener(e -> {

                        if (!e.getValueIsAdjusting()) {

                                if (categoryList.getSelectedValue() != null) {
                                        refreshResources();
                                }
                        }
                });

                // =========================================================
                // SEARCH
                // =========================================================

                searchField.getDocument()
                                .addDocumentListener(
                                                new DocumentListener() {

                                                        @Override
                                                        public void insertUpdate(
                                                                        DocumentEvent e) {
                                                                refreshResources();
                                                        }

                                                        @Override
                                                        public void removeUpdate(
                                                                        DocumentEvent e) {
                                                                refreshResources();
                                                        }

                                                        @Override
                                                        public void changedUpdate(
                                                                        DocumentEvent e) {
                                                                refreshResources();
                                                        }
                                                });

                // =========================================================
                // TYPE FILTER
                // =========================================================

                typeFilter.addActionListener(
                                e -> refreshResources());
        }

        // =============================================================
        // AI PANEL
        // =============================================================

        private JPanel createAiPanel() {

                JPanel panel = new JPanel(
                                new BorderLayout(5, 5));

                panel.setBorder(
                                BorderFactory.createCompoundBorder(
                                                BorderFactory.createTitledBorder(
                                                                "✨ StudySync AI"),
                                                new EmptyBorder(
                                                                5, 5, 5, 5)));

                aiSelectionLabel = new JLabel(
                                "0 resources selected");

                aiSelectionLabel.setFont(
                                new Font(
                                                "Arial",
                                                Font.BOLD,
                                                12));

                aiQueryField = new JTextField();

                aiQueryField.setToolTipText(
                                "Ask AI about the selected resources...");

                aiAskButton = new JButton("ASK AI");

                aiAskButton.setEnabled(false);

                aiAskButton.addActionListener(
                                e -> askSelectedFilesWithAi());

                JPanel top = new JPanel(
                                new BorderLayout());

                top.add(
                                aiSelectionLabel,
                                BorderLayout.WEST);

                JPanel input = new JPanel(
                                new BorderLayout(5, 0));

                input.add(
                                aiQueryField,
                                BorderLayout.CENTER);

                input.add(
                                aiAskButton,
                                BorderLayout.EAST);

                panel.add(top, BorderLayout.NORTH);
                panel.add(input, BorderLayout.CENTER);

                aiQueryField.getDocument()
                                .addDocumentListener(
                                                new DocumentListener() {

                                                        @Override
                                                        public void insertUpdate(
                                                                        DocumentEvent e) {
                                                                updateAiSelectionUi();
                                                        }

                                                        @Override
                                                        public void removeUpdate(
                                                                        DocumentEvent e) {
                                                                updateAiSelectionUi();
                                                        }

                                                        @Override
                                                        public void changedUpdate(
                                                                        DocumentEvent e) {
                                                                updateAiSelectionUi();
                                                        }
                                                });

                return panel;
        }

        private void updateAiSelectionUi() {

                int count = selectedAiFiles.size();

                aiSelectionLabel.setText(
                                count + (count == 1
                                                ? " resource selected"
                                                : " resources selected"));

                String query = aiQueryField.getText()
                                .trim();

                aiAskButton.setEnabled(
                                count > 0 &&
                                                !query.isEmpty());
        }

        private void clearAiSelection() {

                selectedAiFiles.clear();

                if (aiQueryField != null) {
                        aiQueryField.setText("");
                }

                updateAiSelectionUi();
        }

        // =============================================================
        // LOAD SUBJECTS
        // =============================================================

        private void loadSubjects(File oneDriveFolder) {

                subjectModel.clear();
                categoryModel.clear();
                resourcePanel.removeAll();

                selectedSubjectFolder = null;
                currentFiles.clear();

                if (oneDriveFolder == null ||
                                !oneDriveFolder.isDirectory()) {
                        return;
                }

                File[] folders = oneDriveFolder.listFiles(
                                File::isDirectory);

                if (folders == null) {
                        resourcePanel.revalidate();
                        resourcePanel.repaint();
                        return;
                }

                Arrays.sort(
                                folders,
                                Comparator.comparing(
                                                File::getName,
                                                String.CASE_INSENSITIVE_ORDER));

                for (File folder : folders) {
                        subjectModel.addElement(
                                        folder.getName());
                }

                resourcePanel.revalidate();
                resourcePanel.repaint();
        }

        // =============================================================
        // LOAD CATEGORIES
        // =============================================================

        private void loadCategories() {

                categoryModel.clear();

                for (String category : CATEGORIES) {
                        categoryModel.addElement(category);
                }
        }

        // =============================================================
        // SHOW ALL RESOURCES
        // =============================================================

        private void showAllResources() {

                if (selectedSubjectFolder == null) {
                        return;
                }

                currentFiles = getFilesRecursively(
                                selectedSubjectFolder);

                categoryList.clearSelection();
                refreshResources();
        }

        // =============================================================
        // REFRESH RESOURCES
        // =============================================================

        private void refreshResources() {

                resourcePanel.removeAll();

                if (selectedSubjectFolder == null) {
                        resourcePanel.revalidate();
                        resourcePanel.repaint();
                        return;
                }

                String search = searchField.getText()
                                .trim()
                                .toLowerCase();

                String selectedCategory = categoryList.getSelectedValue();

                String selectedType = (String) typeFilter.getSelectedItem();

                int displayed = 0;

                for (File file : currentFiles) {

                        if (file == null ||
                                        !file.exists() ||
                                        !file.isFile()) {
                                continue;
                        }

                        if (selectedCategory != null &&
                                        !getCategory(file)
                                                        .equals(selectedCategory)) {
                                continue;
                        }

                        if (!matchesType(
                                        file,
                                        selectedType)) {
                                continue;
                        }

                        if (!search.isEmpty() &&
                                        !file.getName()
                                                        .toLowerCase()
                                                        .contains(search)) {
                                continue;
                        }

                        addResourceCard(file);
                        displayed++;
                }

                if (displayed == 0) {

                        JLabel empty = new JLabel(
                                        "No resources match your filters.");

                        empty.setFont(
                                        new Font(
                                                        "Arial",
                                                        Font.PLAIN,
                                                        16));

                        empty.setBorder(
                                        new EmptyBorder(
                                                        20, 20, 20, 20));

                        resourcePanel.add(empty);
                }

                resourcePanel.revalidate();
                resourcePanel.repaint();
        }

        // =============================================================
        // RESOURCE CARD
        // =============================================================

        private void addResourceCard(File file) {

                JPanel card = new JPanel(
                                new BorderLayout(12, 5));

                card.setBorder(
                                BorderFactory.createCompoundBorder(
                                                BorderFactory.createLineBorder(
                                                                new Color(210, 210, 210)),
                                                new EmptyBorder(
                                                                10, 10, 10, 10)));

                card.setMaximumSize(
                                new Dimension(
                                                Integer.MAX_VALUE,
                                                85));

                // ---------- Selection checkbox ----------

                JCheckBox selectForAi = new JCheckBox();

                String path = file.getAbsolutePath();

                selectForAi.setSelected(
                                selectedAiFiles.contains(path));

                selectForAi.setToolTipText(
                                "Select this resource for StudySync AI");

                selectForAi.addActionListener(e -> {

                        if (selectForAi.isSelected()) {
                                selectedAiFiles.add(path);
                        } else {
                                selectedAiFiles.remove(path);
                        }

                        updateAiSelectionUi();
                });

                // ---------- Icon ----------

                JLabel icon = new JLabel(
                                getFileIcon(file));

                icon.setFont(
                                new Font(
                                                "Arial",
                                                Font.PLAIN,
                                                25));

                JPanel left = new JPanel(
                                new FlowLayout(
                                                FlowLayout.LEFT,
                                                0,
                                                0));

                left.add(selectForAi);
                left.add(icon);

                // ---------- File information ----------

                JPanel info = new JPanel();

                info.setOpaque(false);

                info.setLayout(
                                new BoxLayout(
                                                info,
                                                BoxLayout.Y_AXIS));

                JLabel name = new JLabel(
                                file.getName());

                name.setFont(
                                new Font(
                                                "Arial",
                                                Font.BOLD,
                                                14));

                JLabel details = new JLabel(
                                getFileType(file)
                                                + "   •   "
                                                + formatSize(
                                                                file.length())
                                                + "   •   "
                                                + getCategory(file));

                details.setForeground(Color.GRAY);

                info.add(name);
                info.add(Box.createVerticalStrut(4));
                info.add(details);

                // ---------- Buttons ----------

                JPanel buttons = new JPanel(
                                new FlowLayout(
                                                FlowLayout.RIGHT,
                                                5,
                                                0));

                JButton openButton = new JButton("OPEN");

                openButton.addActionListener(
                                e -> openFile(file));
                buttons.add(openButton);

                card.add(
                                left,
                                BorderLayout.WEST);

                card.add(
                                info,
                                BorderLayout.CENTER);

                card.add(
                                buttons,
                                BorderLayout.EAST);

                resourcePanel.add(card);
                resourcePanel.add(
                                Box.createVerticalStrut(8));
        }

        // =============================================================
        // ASK AI ABOUT SELECTED FILES
        // =============================================================

        private void askSelectedFilesWithAi() {

                String query = aiQueryField.getText()
                                .trim();

                if (query.isEmpty()) {

                        JOptionPane.showMessageDialog(
                                        this,
                                        "Enter a question for StudySync AI.",
                                        "AI Query",
                                        JOptionPane.WARNING_MESSAGE);

                        return;
                }

                if (selectedAiFiles.isEmpty()) {

                        JOptionPane.showMessageDialog(
                                        this,
                                        "Select at least one resource first.",
                                        "No Resources Selected",
                                        JOptionPane.WARNING_MESSAGE);

                        return;
                }

                List<File> files = new ArrayList<>();

                for (String path : selectedAiFiles) {

                        File file = new File(path);

                        if (file.exists() &&
                                        file.isFile() &&
                                        file.canRead()) {

                                files.add(file);
                        }
                }

                if (files.isEmpty()) {

                        JOptionPane.showMessageDialog(
                                        this,
                                        "None of the selected resources are available locally.\n\n"
                                                        + "For OneDrive files, choose "
                                                        + "\"Always Keep on This Device\".",
                                        "AI Error",
                                        JOptionPane.ERROR_MESSAGE);

                        return;
                }

                showMultiFileAiDialog(
                                files,
                                query);
        }

        private void showMultiFileAiDialog(
                        List<File> files,
                        String query) {

                JDialog dialog = new JDialog(
                                this,
                                "StudySync AI",
                                false);

                dialog.setSize(900, 680);
                dialog.setLocationRelativeTo(this);

                JLabel title = new JLabel(
                                "✨ StudySync AI");

                title.setFont(
                                new Font(
                                                "Arial",
                                                Font.BOLD,
                                                20));

                JLabel info = new JLabel(
                                "Analyzing "
                                                + files.size()
                                                + " selected resource"
                                                + (files.size() == 1 ? "" : "s")
                                                + "...");

                info.setForeground(Color.GRAY);

                JPanel header = new JPanel(
                                new BorderLayout(5, 5));

                header.setBorder(
                                new EmptyBorder(
                                                10, 15, 10, 15));

                header.add(
                                title,
                                BorderLayout.NORTH);

                header.add(
                                info,
                                BorderLayout.SOUTH);

                JEditorPane output = new JEditorPane();

                output.setContentType("text/html");
                output.setEditable(false);
                output.setBackground(Color.WHITE);
                output.putClientProperty(
                                JEditorPane.HONOR_DISPLAY_PROPERTIES,
                                Boolean.TRUE);

                StringBuilder selectedNames = new StringBuilder();

                for (File file : files) {
                        selectedNames
                                        .append("• ")
                                        .append(file.getName())
                                        .append("\n");
                }

                // Keep a lightweight conversation transcript so follow-up
                // questions have context from the previous turns.
                List<String> conversation = new ArrayList<>();

                conversation.add(
                                "USER: " + query);

                StringBuilder chatHtml = new StringBuilder();

                chatHtml.append(
                                "<html><head><style>")
                                .append("body{font-family:Arial,sans-serif;font-size:15px;")
                                .append("color:#202124;margin:14px;}")
                                .append("h1{font-size:22px;margin:4px 0 12px;}")
                                .append("h2{font-size:19px;margin:18px 0 8px;}")
                                .append("h3{font-size:17px;margin:16px 0 6px;}")
                                .append("p{margin:7px 0;line-height:1.45;}")
                                .append("li{margin:4px 0;line-height:1.4;}")
                                .append("table{border-collapse:collapse;margin:10px 0;}")
                                .append("th,td{border:1px solid #c9c9c9;padding:7px 9px;}")
                                .append("th{background:#f1f3f4;font-weight:bold;}")
                                .append(".user{background:#eef4ff;border-radius:8px;")
                                .append("padding:9px 11px;margin:12px 0;}")
                                .append(".ai{background:#f7f7f7;border-radius:8px;")
                                .append("padding:10px 12px;margin:12px 0;}")
                                .append(".label{font-weight:bold;margin-bottom:5px;}")
                                .append(".math{font-family:Menlo,monospace;background:#f4f4f4;")
                                .append("padding:3px 5px;border-radius:4px;}")
                                .append("pre{background:#f4f4f4;padding:10px;border-radius:6px;")
                                .append("white-space:pre-wrap;}")
                                .append("</style></head><body>");

                chatHtml.append(
                                "<div class='ai'><div class='label'>StudySync AI</div>")
                                .append("<p><b>Selected resources:</b></p>")
                                .append("<ul>");

                for (File file : files) {
                        chatHtml.append("<li>")
                                        .append(escapeHtml(file.getName()))
                                        .append("</li>");
                }

                chatHtml.append("</ul>")
                                .append("<p><b>Question:</b> ")
                                .append(inlineMarkdown(escapeHtml(query)))
                                .append("</p>")
                                .append("<p><i>Analyzing the original files...</i></p>")
                                .append("</div></body></html>");

                output.setText(chatHtml.toString());
                output.setCaretPosition(0);

                JScrollPane scrollPane = new JScrollPane(output);

                // ---------------------------------------------------------
                // CHAT BOX
                // ---------------------------------------------------------

                JTextField chatField = new JTextField();

                chatField.setToolTipText(
                                "Ask a follow-up question about the selected resources...");

                JButton sendButton = new JButton("SEND");

                JButton closeButton = new JButton("CLOSE");

                JLabel chatStatus = new JLabel(
                                "  Ask follow-up questions without reopening the resources.");

                chatStatus.setForeground(Color.GRAY);

                JPanel chatInput = new JPanel(
                                new BorderLayout(8, 5));

                chatInput.setBorder(
                                new EmptyBorder(8, 10, 8, 10));

                chatInput.add(
                                chatField,
                                BorderLayout.CENTER);

                chatInput.add(
                                sendButton,
                                BorderLayout.EAST);

                JPanel bottom = new JPanel(
                                new BorderLayout(5, 5));

                bottom.add(
                                chatStatus,
                                BorderLayout.NORTH);

                bottom.add(
                                chatInput,
                                BorderLayout.CENTER);

                JPanel closePanel = new JPanel(
                                new FlowLayout(FlowLayout.RIGHT, 10, 5));

                closePanel.add(closeButton);
                bottom.add(closePanel, BorderLayout.SOUTH);

                dialog.setLayout(
                                new BorderLayout());

                dialog.add(
                                header,
                                BorderLayout.NORTH);

                dialog.add(
                                scrollPane,
                                BorderLayout.CENTER);

                dialog.add(
                                bottom,
                                BorderLayout.SOUTH);

                closeButton.addActionListener(
                                e -> dialog.dispose());

                // ---------------------------------------------------------
                // CHAT REQUEST
                // ---------------------------------------------------------

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

                        appendChatMessage(
                                        output,
                                        chatHtml,
                                        "You",
                                        message,
                                        false);

                        String context = buildConversationInstruction(
                                        conversation);

                        info.setText(
                                        "StudySync AI is thinking...");

                        SwingWorker<String, Void> followUpWorker = new SwingWorker<>() {

                                @Override
                                protected String doInBackground()
                                                throws Exception {

                                        return GeminiAI.analyzeFiles(
                                                        files,
                                                        context);
                                }

                                @Override
                                protected void done() {

                                        try {

                                                String result = get();

                                                conversation.add(
                                                                "ASSISTANT: " + result);

                                                appendChatMessage(
                                                                output,
                                                                chatHtml,
                                                                "StudySync AI",
                                                                result,
                                                                true);

                                                info.setText(
                                                                "Conversation • "
                                                                                + files.size()
                                                                                + " selected resource"
                                                                                + (files.size() == 1 ? "" : "s"));

                                        } catch (Exception ex) {

                                                Throwable cause = ex.getCause() != null
                                                                ? ex.getCause()
                                                                : ex;

                                                appendChatMessage(
                                                                output,
                                                                chatHtml,
                                                                "StudySync AI",
                                                                "I couldn't process that follow-up.\n\n"
                                                                                + safeMessage(cause),
                                                                true);

                                                info.setText(
                                                                "Follow-up failed");

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

                sendButton.addActionListener(
                                e -> sendMessage.run());

                chatField.addActionListener(
                                e -> sendMessage.run());

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

                // First request is launched automatically.
                SwingWorker<String, Void> worker = new SwingWorker<>() {

                        @Override
                        protected String doInBackground()
                                        throws Exception {

                                return GeminiAI.analyzeFiles(
                                                files,
                                                query);
                        }

                        @Override
                        protected void done() {

                                try {

                                        String result = get();

                                        conversation.add(
                                                        "ASSISTANT: " + result);

                                        chatHtml.setLength(0);

                                        chatHtml.append(
                                                        "<html><head><style>")
                                                        .append("body{font-family:Arial,sans-serif;font-size:15px;")
                                                        .append("color:#202124;margin:14px;}")
                                                        .append("h1{font-size:22px;margin:4px 0 12px;}")
                                                        .append("h2{font-size:19px;margin:18px 0 8px;}")
                                                        .append("h3{font-size:17px;margin:16px 0 6px;}")
                                                        .append("p{margin:7px 0;line-height:1.45;}")
                                                        .append("li{margin:4px 0;line-height:1.4;}")
                                                        .append("table{border-collapse:collapse;margin:10px 0;}")
                                                        .append("th,td{border:1px solid #c9c9c9;padding:7px 9px;}")
                                                        .append("th{background:#f1f3f4;font-weight:bold;}")
                                                        .append(".user{background:#eef4ff;border-radius:8px;")
                                                        .append("padding:9px 11px;margin:12px 0;}")
                                                        .append(".ai{background:#f7f7f7;border-radius:8px;")
                                                        .append("padding:10px 12px;margin:12px 0;}")
                                                        .append(".label{font-weight:bold;margin-bottom:5px;}")
                                                        .append(".math{font-family:Menlo,monospace;background:#f4f4f4;")
                                                        .append("padding:3px 5px;border-radius:4px;}")
                                                        .append("pre{background:#f4f4f4;padding:10px;border-radius:6px;")
                                                        .append("white-space:pre-wrap;}")
                                                        .append("</style></head><body>");

                                        chatHtml.append(
                                                        "<div class='ai'><div class='label'>StudySync AI</div>")
                                                        .append(markdownToHtml(result))
                                                        .append("</div></body></html>");

                                        output.setText(chatHtml.toString());
                                        output.setCaretPosition(0);

                                        info.setText(
                                                        "Conversation • "
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

                                        output.setText(
                                                        "StudySync AI could not analyze "
                                                                        + "the selected resources.\n\n"
                                                                        + safeMessage(cause));

                                        output.setCaretPosition(0);

                                        info.setText(
                                                        "Analysis failed");

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

        private void appendChatMessage(
                        JEditorPane output,
                        StringBuilder transcript,
                        String sender,
                        String message,
                        boolean assistant) {

                /*
                 * Keep the conversation as HTML so Gemini's Markdown output
                 * appears as actual headings, lists, bold text, tables, etc.
                 */
                int bodyEnd = transcript.lastIndexOf("</body>");

                if (bodyEnd < 0) {
                        transcript.append(
                                        "<html><head></head><body>");
                        bodyEnd = transcript.length();
                }

                String cssClass = assistant ? "ai" : "user";

                String content = assistant
                                ? markdownToHtml(message)
                                : "<p>"
                                                + inlineMarkdown(
                                                                escapeHtml(message))
                                                + "</p>";

                transcript.insert(
                                bodyEnd,
                                "<div class='"
                                                + cssClass
                                                + "'>"
                                                + "<div class='label'>"
                                                + escapeHtml(sender)
                                                + "</div>"
                                                + content
                                                + "</div>");

                output.setText(transcript.toString());

                SwingUtilities.invokeLater(() -> {
                        output.setCaretPosition(
                                        output.getDocument().getLength());
                });
        }

        /**
         * Lightweight Markdown renderer for Swing.
         *
         * Supports the formatting Gemini commonly returns:
         * headings, bold, italic, bullets, numbered lists, tables,
         * horizontal rules, inline code, fenced code and simple formulas.
         */
        private static String markdownToHtml(String markdown) {

                if (markdown == null || markdown.isBlank()) {
                        return "<p></p>";
                }

                String[] lines = markdown.replace("\r\n", "\n")
                                .replace('\r', '\n')
                                .split("\n", -1);

                StringBuilder html = new StringBuilder();

                boolean inUl = false;
                boolean inOl = false;
                int orderedListNumber = 0;
                boolean inCode = false;
                boolean inTable = false;

                for (String rawLine : lines) {

                        String line = rawLine.trim();

                        if (line.startsWith("```")) {

                                if (!inCode) {

                                        closeList(html, inUl, inOl);
                                        inUl = false;
                                        inOl = false;

                                        html.append("<pre>");
                                        inCode = true;

                                } else {

                                        html.append("</pre>");
                                        inCode = false;
                                }

                                continue;
                        }

                        if (inCode) {

                                html.append(
                                                escapeHtml(rawLine))
                                                .append('\n');

                                continue;
                        }

                        if (line.isEmpty()) {

                                if (inTable) {
                                        html.append("</table>");
                                        inTable = false;
                                }

                                continue;
                        }

                        // Horizontal rule
                        if (line.matches("^([-*_])\\s*(\\1\\s*){2,}$")) {

                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;

                                html.append("<hr>");

                                continue;
                        }

                        // Markdown table separator
                        if (isTableSeparator(line)) {
                                continue;
                        }

                        // Markdown table row
                        if (line.startsWith("|") &&
                                        line.endsWith("|") &&
                                        line.indexOf('|', 1) > 0) {

                                if (!inTable) {

                                        closeList(html, inUl, inOl);
                                        inUl = false;
                                        inOl = false;

                                        html.append("<table>");
                                        inTable = true;
                                }

                                String[] cells = line.substring(
                                                1,
                                                line.length() - 1).split("\\|", -1);

                                html.append("<tr>");

                                for (String cell : cells) {
                                        html.append("<td>")
                                                        .append(
                                                                        inlineMarkdown(
                                                                                        escapeHtml(
                                                                                                        cell.trim())))
                                                        .append("</td>");
                                }

                                html.append("</tr>");

                                continue;
                        }

                        if (inTable) {
                                html.append("</table>");
                                inTable = false;
                        }

                        // Headings
                        if (line.startsWith("### ")) {

                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;

                                html.append("<h3>")
                                                .append(
                                                                inlineMarkdown(
                                                                                escapeHtml(
                                                                                                line.substring(4))))
                                                .append("</h3>");

                                continue;
                        }

                        if (line.startsWith("## ")) {

                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;

                                html.append("<h2>")
                                                .append(
                                                                inlineMarkdown(
                                                                                escapeHtml(
                                                                                                line.substring(3))))
                                                .append("</h2>");

                                continue;
                        }

                        if (line.startsWith("# ")) {

                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;

                                html.append("<h1>")
                                                .append(
                                                                inlineMarkdown(
                                                                                escapeHtml(
                                                                                                line.substring(2))))
                                                .append("</h1>");

                                continue;
                        }

                        // Bullet list
                        if (line.matches("^[-*+]\\s+.+")) {

                                if (inOl) {
                                        html.append("</ol>");
                                        inOl = false;
                                        orderedListNumber = 0;
                                }

                                if (!inUl) {
                                        html.append("<ul>");
                                        inUl = true;
                                }

                                String item = line.replaceFirst(
                                                "^[-*+]\\s+",
                                                "");

                                html.append("<li value=\"")
                                                .append(orderedListNumber)
                                                .append("\">")
                                                .append(
                                                                inlineMarkdown(
                                                                                escapeHtml(item)))
                                                .append("</li>");

                                continue;
                        }

                        // Numbered list
                        if (line.matches("^\\d+[.)]\\s+.+")) {

                                if (inUl) {
                                        html.append("</ul>");
                                        inUl = false;
                                }

                                if (!inOl) {
                                        html.append("<ol>");
                                        inOl = true;
                                        orderedListNumber = 0;
                                }

                                orderedListNumber++;

                                String item = line.replaceFirst(
                                                "^\\d+[.)]\\s+",
                                                "");

                                html.append("<li>")
                                                .append(
                                                                inlineMarkdown(
                                                                                escapeHtml(item)))
                                                .append("</li>");

                                continue;
                        }

                        closeList(html, inUl, inOl);
                        inUl = false;
                        inOl = false;

                        // LaTeX display equation
                        if (line.startsWith("$$") &&
                                        line.endsWith("$$") &&
                                        line.length() > 4) {

                                html.append("<pre>")
                                                .append(
                                                                escapeHtml(
                                                                                line.substring(
                                                                                                2,
                                                                                                line.length() - 2)
                                                                                                .trim()))
                                                .append("</pre>");

                                continue;
                        }

                        html.append("<p>")
                                        .append(
                                                        inlineMarkdown(
                                                                        escapeHtml(line)))
                                        .append("</p>");
                }

                if (inTable) {
                        html.append("</table>");
                }

                if (inCode) {
                        html.append("</pre>");
                }

                closeList(html, inUl, inOl);
                orderedListNumber = 0;

                return html.toString();
        }

        private static boolean isTableSeparator(
                        String line) {

                if (!line.startsWith("|") ||
                                !line.endsWith("|")) {
                        return false;
                }

                String middle = line.substring(
                                1,
                                line.length() - 1);

                String[] cells = middle.split("\\|");

                if (cells.length == 0) {
                        return false;
                }

                for (String cell : cells) {

                        if (!cell.trim().matches("^:?-{3,}:?$")) {
                                return false;
                        }
                }

                return true;
        }

        private static void closeList(
                        StringBuilder html,
                        boolean inUl,
                        boolean inOl) {

                if (inUl) {
                        html.append("</ul>");
                }

                if (inOl) {
                        html.append("</ol>");
                }
        }

        private static String inlineMarkdown(
                        String value) {

                if (value == null || value.isEmpty()) {
                        return "";
                }

                String result = value;

                // Inline code
                result = result.replaceAll(
                                "`([^`]+)`",
                                "<code>$1</code>");

                // Bold
                result = result.replaceAll(
                                "\\*\\*([^*]+)\\*\\*",
                                "<b>$1</b>");

                result = result.replaceAll(
                                "__([^_]+)__",
                                "<b>$1</b>");

                // Italic
                result = result.replaceAll(
                                "(?<!\\*)\\*([^*]+)\\*(?!\\*)",
                                "<i>$1</i>");

                result = result.replaceAll(
                                "(?<!_)_([^_]+)_(?!_)",
                                "<i>$1</i>");

                // Inline math, rendered in a readable monospace style.
                result = result.replaceAll(
                                "\\$([^$]+)\\$",
                                "<span class='math'>$1</span>");

                return result;
        }

        private static String escapeHtml(
                        String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                                .replace("\"", "&quot;")
                                .replace("'", "&#39;");
        }

        // =============================================================
        // CLUSTER CLASSIFICATION
        // =============================================================

        private String getCategory(File file) {

                ResourceCluster.Category category = ResourceCluster.classify(file);

                switch (category) {

                        case LAB:
                                return "Lab";

                        case LECTURE_SLIDES:
                                return "Lecture Slides / PPTs";

                        case SYLLABUS_POLICY:
                                return "Syllabus / Course Policy";

                        case BOOKS:
                                return "Books";

                        case PREVIOUS_YEAR_PAPERS:
                                return "Previous Year Papers";

                        case MISCELLANEOUS:
                        default:
                                return "Miscellaneous";
                }
        }

        // =============================================================
        // FILE TYPE
        // =============================================================

        private boolean matchesType(
                        File file,
                        String selectedType) {

                if (selectedType == null ||
                                selectedType.equals("All Files")) {
                        return true;
                }

                String name = file.getName()
                                .toLowerCase();

                switch (selectedType) {

                        case "PDF":
                                return name.endsWith(".pdf");

                        case "DOCX":
                                return name.endsWith(".docx");

                        case "PPTX":
                                return name.endsWith(".pptx");

                        case "XLSX":
                                return name.endsWith(".xlsx");

                        case "TXT":
                                return name.endsWith(".txt");

                        case "Images":
                                return name.endsWith(".jpg")
                                                || name.endsWith(".jpeg")
                                                || name.endsWith(".png")
                                                || name.endsWith(".webp");

                        default:
                                return true;
                }
        }

        // =============================================================
        // FILE TYPE DISPLAY
        // =============================================================

        private String getFileType(File file) {

                String name = file.getName()
                                .toLowerCase();

                if (name.endsWith(".pdf"))
                        return "PDF Document";

                if (name.endsWith(".docx") ||
                                name.endsWith(".doc"))
                        return "Word Document";

                if (name.endsWith(".pptx") ||
                                name.endsWith(".ppt"))
                        return "PowerPoint";

                if (name.endsWith(".xlsx") ||
                                name.endsWith(".xls"))
                        return "Excel Spreadsheet";

                if (name.endsWith(".txt"))
                        return "Text File";

                if (name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") ||
                                name.endsWith(".png") ||
                                name.endsWith(".webp"))
                        return "Image";

                return "File";
        }

        // =============================================================
        // FILE ICON
        // =============================================================

        private String getFileIcon(File file) {

                String name = file.getName()
                                .toLowerCase();

                if (name.endsWith(".pdf"))
                        return "📕";

                if (name.endsWith(".pptx") ||
                                name.endsWith(".ppt"))
                        return "📊";

                if (name.endsWith(".docx") ||
                                name.endsWith(".doc"))
                        return "📝";

                if (name.endsWith(".xlsx") ||
                                name.endsWith(".xls"))
                        return "📈";

                if (name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") ||
                                name.endsWith(".png") ||
                                name.endsWith(".webp"))
                        return "🖼️";

                return "📄";
        }

        // =============================================================
        // FILE SIZE
        // =============================================================

        private String formatSize(long bytes) {

                if (bytes < 1024)
                        return bytes + " B";

                if (bytes < 1024 * 1024)
                        return String.format(
                                        "%.2f KB",
                                        bytes / 1024.0);

                if (bytes < 1024 * 1024 * 1024)
                        return String.format(
                                        "%.2f MB",
                                        bytes / (1024.0 * 1024.0));

                return String.format(
                                "%.2f GB",
                                bytes / (1024.0 * 1024.0 * 1024.0));
        }

        // =============================================================
        // RECURSIVE FILE SEARCH
        // =============================================================

        private List<File> getFilesRecursively(
                        File folder) {

                List<File> files = new ArrayList<>();

                if (folder == null ||
                                !folder.isDirectory()) {
                        return files;
                }

                File[] contents = folder.listFiles();

                if (contents == null) {
                        return files;
                }

                for (File file : contents) {

                        if (file.isDirectory()) {

                                files.addAll(
                                                getFilesRecursively(file));

                        } else if (file.isFile()) {

                                files.add(file);
                        }
                }

                files.sort(
                                Comparator.comparing(
                                                File::getName,
                                                String.CASE_INSENSITIVE_ORDER));

                return files;
        }

        // =============================================================
        // OPEN FILE
        // =============================================================

        private void openFile(File file) {

                try {

                        if (file == null ||
                                        !file.exists() ||
                                        !file.isFile()) {

                                JOptionPane.showMessageDialog(
                                                this,
                                                "File is not available locally.\n"
                                                                + "Please mark it as "
                                                                + "\"Always Keep on This Device\" "
                                                                + "in OneDrive.",
                                                "File Not Available",
                                                JOptionPane.WARNING_MESSAGE);

                                return;
                        }

                        if (!Desktop.isDesktopSupported()) {

                                JOptionPane.showMessageDialog(
                                                this,
                                                "Desktop file opening is not supported "
                                                                + "on this system.",
                                                "Open Error",
                                                JOptionPane.ERROR_MESSAGE);

                                return;
                        }

                        Desktop.getDesktop().open(file);

                } catch (Exception ex) {

                        JOptionPane.showMessageDialog(
                                        this,
                                        "The file couldn't be opened.\n\n"
                                                        + safeMessage(ex),
                                        "Open Error",
                                        JOptionPane.ERROR_MESSAGE);
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

                if (message == null ||
                                message.trim().isEmpty()) {
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
