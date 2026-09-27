import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

    // ---------- DATA ----------
    private File selectedOneDriveFolder;
    private File selectedSubjectFolder;

    private List<File> currentFiles = new ArrayList<>();

    // Six clusters
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
        setSize(1150, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // =========================================================
        // TOP BAR
        // =========================================================

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(
                new EmptyBorder(10, 15, 10, 15)
        );

        JLabel title = new JLabel("StudySync");
        title.setFont(
                new Font("Arial", Font.BOLD, 26)
        );

        pathLabel = new JLabel(
                "No OneDrive folder selected"
        );

        pathLabel.setForeground(Color.GRAY);
        pathLabel.setFont(
                new Font("Arial", Font.PLAIN, 12)
        );

        JPanel titlePanel = new JPanel(
                new BorderLayout()
        );

        titlePanel.add(
                title,
                BorderLayout.NORTH
        );

        titlePanel.add(
                pathLabel,
                BorderLayout.SOUTH
        );

        JButton selectFolderButton =
                new JButton("Select OneDrive Folder");

        topPanel.add(
                titlePanel,
                BorderLayout.WEST
        );

        topPanel.add(
                selectFolderButton,
                BorderLayout.EAST
        );

        // =========================================================
        // SUBJECT LIST
        // =========================================================

        subjectModel =
                new DefaultListModel<>();

        subjectList =
                new JList<>(subjectModel);

        subjectList.setFont(
                new Font("Arial", Font.PLAIN, 16)
        );

        subjectList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        JScrollPane subjectScroll =
                new JScrollPane(subjectList);

        subjectScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Subjects"
                )
        );

        // =========================================================
        // CATEGORY LIST
        // =========================================================

        categoryModel =
                new DefaultListModel<>();

        categoryList =
                new JList<>(categoryModel);

        categoryList.setFont(
                new Font("Arial", Font.PLAIN, 14)
        );

        categoryList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        JScrollPane categoryScroll =
                new JScrollPane(categoryList);

        categoryScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Resource Clusters"
                )
        );

        // =========================================================
        // SEARCH + FILTER BAR
        // =========================================================

        JPanel filterPanel =
                new JPanel(new BorderLayout(10, 5));

        searchField =
                new JTextField();

        searchField.setToolTipText(
                "Search resources..."
        );

        typeFilter =
                new JComboBox<>(
                        new String[]{
                                "All Files",
                                "PDF",
                                "DOCX",
                                "PPTX",
                                "XLSX",
                                "TXT"
                        }
                );

        filterPanel.add(
                new JLabel("Search:"),
                BorderLayout.WEST
        );

        filterPanel.add(
                searchField,
                BorderLayout.CENTER
        );

        filterPanel.add(
                typeFilter,
                BorderLayout.EAST
        );

        // =========================================================
        // RESOURCE PANEL
        // =========================================================

        resourcePanel =
                new JPanel();

        resourcePanel.setLayout(
                new BoxLayout(
                        resourcePanel,
                        BoxLayout.Y_AXIS
                )
        );

        JScrollPane resourceScroll =
                new JScrollPane(resourcePanel);

        resourceScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Resources"
                )
        );

        // =========================================================
        // RIGHT SIDE
        // =========================================================

        JPanel rightPanel =
                new JPanel(new BorderLayout(5, 5));

        rightPanel.add(
                filterPanel,
                BorderLayout.NORTH
        );

        rightPanel.add(
                resourceScroll,
                BorderLayout.CENTER
        );

        // =========================================================
        // SUBJECT + CATEGORY
        // =========================================================

        JPanel leftPanel =
                new JPanel(new BorderLayout(5, 5));

        leftPanel.add(
                subjectScroll,
                BorderLayout.CENTER
        );

        leftPanel.add(
                categoryScroll,
                BorderLayout.SOUTH
        );

        leftPanel.setPreferredSize(
                new Dimension(320, 500)
        );

        // =========================================================
        // MAIN SPLIT
        // =========================================================

        JSplitPane mainSplit =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        leftPanel,
                        rightPanel
                );

        mainSplit.setDividerLocation(320);

        setLayout(
                new BorderLayout()
        );

        add(
                topPanel,
                BorderLayout.NORTH
        );

        add(
                mainSplit,
                BorderLayout.CENTER
        );

        // =========================================================
        // SELECT ONEDRIVE FOLDER
        // =========================================================

        selectFolderButton.addActionListener(e -> {

            JFileChooser chooser =
                    new JFileChooser();

            chooser.setDialogTitle(
                    "Select your OneDrive folder"
            );

            chooser.setFileSelectionMode(
                    JFileChooser.DIRECTORIES_ONLY
            );

            // macOS OneDrive location
            File cloudStorage =
                    new File(
                            System.getProperty("user.home"),
                            "Library/CloudStorage"
                    );

            if (cloudStorage.exists()) {
                chooser.setCurrentDirectory(
                        cloudStorage
                );
            }

            int result =
                    chooser.showOpenDialog(this);

            if (result ==
                    JFileChooser.APPROVE_OPTION) {

                selectedOneDriveFolder =
                        chooser.getSelectedFile();

                pathLabel.setText(
                        selectedOneDriveFolder
                                .getAbsolutePath()
                );

                loadSubjects(
                        selectedOneDriveFolder
                );
            }
        });

        // =========================================================
        // SUBJECT SELECTION
        // =========================================================

        subjectList.addListSelectionListener(e -> {

            if (!e.getValueIsAdjusting()) {

                String selected =
                        subjectList.getSelectedValue();

                if (selected != null &&
                        selectedOneDriveFolder != null) {

                    selectedSubjectFolder =
                            new File(
                                    selectedOneDriveFolder,
                                    selected
                            );

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

                String category =
                        categoryList.getSelectedValue();

                if (category != null) {

                    showCategoryResources(
                            category
                    );
                }
            }
        });

        // =========================================================
        // SEARCH
        // =========================================================

        searchField.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {

                            public void insertUpdate(
                                    javax.swing.event.DocumentEvent e) {
                                refreshResources();
                            }

                            public void removeUpdate(
                                    javax.swing.event.DocumentEvent e) {
                                refreshResources();
                            }

                            public void changedUpdate(
                                    javax.swing.event.DocumentEvent e) {
                                refreshResources();
                            }
                        }
                );

        // =========================================================
        // TYPE FILTER
        // =========================================================

        typeFilter.addActionListener(
                e -> refreshResources()
        );
    }

    // =============================================================
    // LOAD SUBJECTS
    // =============================================================

    private void loadSubjects(File oneDriveFolder) {

        subjectModel.clear();
        categoryModel.clear();
        resourcePanel.removeAll();

        File[] folders =
                oneDriveFolder.listFiles(
                        File::isDirectory
                );

        if (folders == null) {
            return;
        }

        Arrays.sort(
                folders,
                Comparator.comparing(
                        File::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        for (File folder : folders) {

            subjectModel.addElement(
                    folder.getName()
            );
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

            categoryModel.addElement(
                    category
            );
        }
    }

    // =============================================================
    // SHOW ALL RESOURCES
    // =============================================================

    private void showAllResources() {

        if (selectedSubjectFolder == null) {
            return;
        }

        currentFiles =
                getFilesRecursively(
                        selectedSubjectFolder
                );

        categoryList.clearSelection();

        refreshResources();
    }

    // =============================================================
    // SHOW CATEGORY
    // =============================================================

    private void showCategoryResources(
            String category) {

        if (selectedSubjectFolder == null) {
            return;
        }

        currentFiles =
                getFilesRecursively(
                        selectedSubjectFolder
                );

        refreshResources();
    }

    // =============================================================
    // REFRESH RESOURCES
    // =============================================================

    private void refreshResources() {

        if (selectedSubjectFolder == null) {
            return;
        }

        resourcePanel.removeAll();

        String search =
                searchField
                        .getText()
                        .trim()
                        .toLowerCase();

        String selectedCategory =
                categoryList.getSelectedValue();

        String selectedType =
                (String) typeFilter.getSelectedItem();

        int displayed = 0;

        for (File file : currentFiles) {

            // -----------------------------
            // CATEGORY FILTER
            // -----------------------------

            if (selectedCategory != null &&
                    !getCategory(file)
                            .equals(selectedCategory)) {

                continue;
            }

            // -----------------------------
            // SEARCH FILTER
            // -----------------------------

            if (!search.isEmpty() &&
                    !file.getName()
                            .toLowerCase()
                            .contains(search)) {

                continue;
            }

            // -----------------------------
            // TYPE FILTER
            // -----------------------------

            if (!matchesType(
                    file,
                    selectedType
            )) {

                continue;
            }

            addResourceCard(file);

            displayed++;
        }

        if (displayed == 0) {

            JLabel empty =
                    new JLabel(
                            "No resources found."
                    );

            empty.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            16
                    )
            );

            empty.setBorder(
                    new EmptyBorder(
                            20, 20, 20, 20
                    )
            );

            resourcePanel.add(empty);
        }

        resourcePanel.revalidate();
        resourcePanel.repaint();
    }

    // =============================================================
    // RESOURCE CARD
    // =============================================================

    private void addResourceCard(File file) {

        JPanel card =
                new JPanel(
                        new BorderLayout(15, 5)
                );

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(210, 210, 210)
                        ),
                        new EmptyBorder(
                                10, 12, 10, 12
                        )
                )
        );

        card.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        75
                )
        );

        // -----------------------------
        // FILE ICON
        // -----------------------------

        JLabel icon =
                new JLabel(
                        getFileIcon(file)
                );

        icon.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        25
                )
        );

        // -----------------------------
        // FILE INFORMATION
        // -----------------------------

        JPanel info =
                new JPanel();

        info.setLayout(
                new BoxLayout(
                        info,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel name =
                new JLabel(
                        file.getName()
                );

        name.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        14
                )
        );

        JLabel details =
                new JLabel(
                        getFileType(file)
                                + "   •   "
                                + formatSize(
                                file.length()
                        )
                                + "   •   "
                                + getCategory(file)
                );

        details.setForeground(
                Color.GRAY
        );

        info.add(name);
        info.add(details);

        // -----------------------------
        // OPEN BUTTON
        // -----------------------------

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                5,
                                0
                        )
                );

        JButton openButton =
                new JButton("OPEN");

        openButton.addActionListener(
                e -> openFile(file)
        );

        JButton aiButton =
                new JButton("AI");

        aiButton.setToolTipText(
                "Analyze this resource with Gemini"
        );

        aiButton.addActionListener(
                e -> showAIOptions(file)
        );

        buttonPanel.add(openButton);
        buttonPanel.add(aiButton);

        card.add(
                icon,
                BorderLayout.WEST
        );

        card.add(
                info,
                BorderLayout.CENTER
        );

        card.add(
                buttonPanel,
                BorderLayout.EAST
        );

        resourcePanel.add(card);

        resourcePanel.add(
                Box.createVerticalStrut(8)
        );
    }
    // =============================================================
// AI OPTIONS
// =============================================================

    private void showAIOptions(File file) {

        String[] options = {
                "Summarize",
                "Explain the important concepts",
                "Create study notes",
                "Generate exam questions",
                "Ask a custom question"
        };

        String selected =
                (String) JOptionPane.showInputDialog(
                        this,
                        "What should StudySync AI do?",
                        "StudySync AI",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        options,
                        options[0]
                );

        if (selected == null) {
            return;
        }

        String instruction;

        switch (selected) {

            case "Summarize":

                instruction =
                        """
                        Summarize this resource.
    
                        Include:
                        - Main topic
                        - Important concepts
                        - Key definitions
                        - Important formulas or facts
                        - A short final revision summary
                        """;

                break;

            case "Explain the important concepts":

                instruction =
                        """
                        Explain the important concepts in this
                        resource in a way suitable for a university
                        student preparing for an examination.
    
                        Start from the fundamentals and gradually
                        explain the more difficult concepts.
                        """;

                break;

            case "Create study notes":

                instruction =
                        """
                        Convert this resource into structured
                        study notes.
    
                        Use:
                        - Headings
                        - Subheadings
                        - Bullet points
                        - Definitions
                        - Important formulas
                        - Examples where present
                        - Exam-important points
                        """;

                break;

            case "Generate exam questions":

                instruction =
                        """
                        Generate examination-oriented questions
                        from this resource.
    
                        Include:
                        - Short-answer questions
                        - Descriptive questions
                        - Conceptual questions
                        - Application-based questions
    
                        Provide answers after the questions.
                        """;

                break;

            case "Ask a custom question":

                String question =
                        JOptionPane.showInputDialog(
                                this,
                                "Ask something about this resource:",
                                "StudySync AI",
                                JOptionPane.QUESTION_MESSAGE
                        );

                if (question == null ||
                        question.trim().isEmpty()) {

                    return;
                }

                instruction = question;
                break;

            default:
                return;
        }

        runAIAnalysis(
                file,
                instruction
        );
    }
    // =============================================================
// AI ANALYSIS
// =============================================================

    private void runAIAnalysis(
            File file,
            String instruction) {

        JDialog dialog =
                new JDialog(
                        this,
                        "StudySync AI",
                        false
                );

        dialog.setSize(
                850,
                650
        );

        dialog.setLocationRelativeTo(this);

        JTextArea output =
                new JTextArea();

        output.setEditable(false);

        output.setLineWrap(true);

        output.setWrapStyleWord(true);

        output.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        15
                )
        );

        output.setText(
                "StudySync AI is analyzing:\n\n"
                        + file.getName()
                        + "\n\n"
                        + "Please wait..."
        );

        JScrollPane scrollPane =
                new JScrollPane(output);

        JPanel header =
                new JPanel(
                        new BorderLayout()
                );

        JLabel title =
                new JLabel(
                        "  ✨ StudySync AI"
                );

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        20
                )
        );

        JLabel resource =
                new JLabel(
                        "  " + file.getName()
                );

        resource.setForeground(
                Color.GRAY
        );

        header.add(
                title,
                BorderLayout.NORTH
        );

        header.add(
                resource,
                BorderLayout.SOUTH
        );

        dialog.setLayout(
                new BorderLayout()
        );

        dialog.add(
                header,
                BorderLayout.NORTH
        );

        dialog.add(
                scrollPane,
                BorderLayout.CENTER
        );

        dialog.setVisible(true);

        /*
         * Never call the network API on the Swing EDT.
         */
        new Thread(() -> {

            try {

                String result =
                        GeminiAI.analyzeFile(
                                file,
                                instruction
                        );

                SwingUtilities.invokeLater(() -> {

                    output.setText(
                            result
                    );

                    output.setCaretPosition(0);
                });

            } catch (Exception ex) {

                SwingUtilities.invokeLater(() -> {

                    output.setText(
                            "StudySync AI could not analyze "
                                    + "this resource.\n\n"
                                    + ex.getMessage()
                    );

                    output.setCaretPosition(0);
                });
            }

        }, "StudySync-AI").start();
    }

    // =============================================================
    // CLUSTER CLASSIFICATION
    // =============================================================

    private String getCategory(File file) {

        String name = file.getName().toLowerCase();

        // Remove extension
        name = name.replaceAll("\\.[^.]+$", "");

        // =====================================================
        // 1. SYLLABUS / COURSE POLICY
        // Highest priority because these are very specific
        // =====================================================
        if (name.contains("syllabus")
                || name.contains("course policy")
                || name.contains("course_policy")
                || name.contains("course outline")
                || name.contains("cp")
                || name.contains("curriculum")
                || name.contains("academic policy")) {

            return "Syllabus / Course Policy";
        }

        // =====================================================
        // 2. PREVIOUS YEAR PAPERS
        // =====================================================
        if (name.contains("previous year")
                || name.contains("previous_year")
                || name.contains("pyq")
                || name.contains("question paper")
                || name.contains("question_paper")
                || name.contains("end term")
                || name.contains("endterm")
                || name.contains("mid term")
                || name.contains("midterm")
                || name.matches(".*\\b20(2[0-9]|3[0-9])\\b.*")) {

            return "Previous Year Papers";
        }

        // =====================================================
        // 3. LAB
        // =====================================================
        if (name.contains("lab")
                || name.contains("experiment")
                || name.contains("practical")
                || name.contains("exp_")
                || name.contains("exp-")) {

            return "Lab";
        }

        // =====================================================
        // 4. LECTURE SLIDES / PPTs
        // =====================================================
        if (name.endsWith(".ppt")
                || name.endsWith(".pptx")
                || name.contains("lecture")
                || name.contains("lect")
                || name.contains("lec")
                || name.contains("slides")
                || name.contains("presentation")
                || name.contains("unit")) {

            return "Lecture Slides / PPTs";
        }

        // =====================================================
        // 5. BOOKS
        // =====================================================
        if (name.contains("book")
                || name.contains("textbook")
                || name.contains("text book")
                || name.contains("gonzalez")
                || name.contains("woods")
                || name.contains("reference")) {

            return "Books";
        }

        // =====================================================
        // 6. EVERYTHING ELSE
        // =====================================================
        return "Miscellaneous";
    }



    private boolean containsAny(
            String text,
            String... keywords) {

        for (String keyword : keywords) {

            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
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

        String name =
                file.getName()
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

            default:
                return true;
        }
    }

    // =============================================================
    // FILE TYPE DISPLAY
    // =============================================================

    private String getFileType(File file) {

        String name =
                file.getName()
                        .toLowerCase();

        if (name.endsWith(".pdf"))
            return "PDF Document";

        if (name.endsWith(".docx"))
            return "Word Document";

        if (name.endsWith(".pptx"))
            return "PowerPoint";

        if (name.endsWith(".xlsx"))
            return "Excel Spreadsheet";

        if (name.endsWith(".txt"))
            return "Text File";

        if (name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".png"))
            return "Image";

        return "File";
    }

    // =============================================================
    // FILE ICON
    // =============================================================

    private String getFileIcon(File file) {

        String name =
                file.getName()
                        .toLowerCase();

        if (name.endsWith(".pdf"))
            return "📕";

        if (name.endsWith(".pptx"))
            return "📊";

        if (name.endsWith(".docx"))
            return "📝";

        if (name.endsWith(".xlsx"))
            return "📈";

        if (name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".png"))
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
                    bytes / 1024.0
            );

        if (bytes < 1024 * 1024 * 1024)
            return String.format(
                    "%.2f MB",
                    bytes /
                            (1024.0 * 1024.0)
            );

        return String.format(
                "%.2f GB",
                bytes /
                        (1024.0 * 1024.0 * 1024.0)
        );
    }

    // =============================================================
    // RECURSIVE FILE SEARCH
    // =============================================================

    private List<File> getFilesRecursively(
            File folder) {

        List<File> files =
                new ArrayList<>();

        File[] contents =
                folder.listFiles();

        if (contents == null) {
            return files;
        }

        for (File file : contents) {

            if (file.isDirectory()) {

                files.addAll(
                        getFilesRecursively(file)
                );

            } else {

                files.add(file);
            }
        }

        files.sort(
                Comparator.comparing(
                        File::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return files;
    }

    // =============================================================
    // OPEN FILE
    // =============================================================

    private void openFile(File file) {

        try {

            if (!file.exists()) {

                JOptionPane.showMessageDialog(
                        this,
                        "File is not available locally.\n"
                                + "Please mark it as "
                                + "\"Always Keep on This Device\" "
                                + "in OneDrive.",
                        "File Not Available",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }

            Desktop.getDesktop()
                    .open(file);

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "The file couldn't be opened.\n\n"
                            + ex.getMessage(),
                    "Open Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =============================================================
    // MAIN
    // =============================================================

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            StudySync app =
                    new StudySync();

            app.setVisible(true);
        });
    }
}