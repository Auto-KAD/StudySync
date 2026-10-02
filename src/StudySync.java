
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StudySync extends JFrame {

        // ========== MODERN COLOR PALETTE ==========
        private static final Color BG_PRIMARY = new Color(15, 15, 20);
        private static final Color BG_SECONDARY = new Color(24, 24, 32);
        private static final Color BG_TERTIARY = new Color(32, 32, 44);
        private static final Color BG_CARD = new Color(28, 28, 38);
        private static final Color BG_CARD_HOVER = new Color(36, 36, 50);
        private static final Color BG_INPUT = new Color(20, 20, 28);
        private static final Color ACCENT_PRIMARY = new Color(99, 102, 241);     // Indigo
        private static final Color ACCENT_SECONDARY = new Color(139, 92, 246);   // Purple
        private static final Color ACCENT_GRADIENT_END = new Color(79, 70, 229); // Darker indigo
        private static final Color TEXT_PRIMARY = new Color(237, 237, 242);
        private static final Color TEXT_SECONDARY = new Color(156, 163, 175);
        private static final Color TEXT_MUTED = new Color(107, 114, 128);
        private static final Color BORDER_COLOR = new Color(55, 55, 72);
        private static final Color BORDER_FOCUSED = new Color(99, 102, 241);
        private static final Color SUCCESS_COLOR = new Color(52, 211, 153);
        private static final Color WARNING_COLOR = new Color(251, 191, 36);
        private static final Color DANGER_COLOR = new Color(248, 113, 113);
        private static final Color SELECTION_BG = new Color(99, 102, 241, 40);
        private static final Color SCROLLBAR_THUMB = new Color(60, 60, 80);
        private static final Color SCROLLBAR_TRACK = BG_SECONDARY;

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

                applyDarkTheme();

                setTitle("StudySync");
                setSize(1350, 820);
                setMinimumSize(new Dimension(1100, 700));
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setLocationRelativeTo(null);
                getContentPane().setBackground(BG_PRIMARY);

                // =========================================================
                // TOP BAR
                // =========================================================

                JPanel topPanel = new JPanel(new BorderLayout()) {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                GradientPaint gp = new GradientPaint(
                                                0, 0, BG_SECONDARY,
                                                getWidth(), 0, BG_TERTIARY);
                                g2.setPaint(gp);
                                g2.fillRect(0, 0, getWidth(), getHeight());
                                // Bottom accent line
                                GradientPaint accent = new GradientPaint(
                                                0, getHeight() - 2, ACCENT_PRIMARY,
                                                getWidth(), getHeight() - 2, ACCENT_SECONDARY);
                                g2.setPaint(accent);
                                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                                g2.dispose();
                        }
                };
                topPanel.setOpaque(false);
                topPanel.setBorder(new EmptyBorder(16, 22, 16, 22));

                JLabel title = new JLabel("StudySync");
                title.setFont(new Font("SansSerif", Font.BOLD, 28));
                title.setForeground(TEXT_PRIMARY);

                JLabel tagline = new JLabel("Intelligent Study Resource Manager");
                tagline.setFont(new Font("SansSerif", Font.PLAIN, 12));
                tagline.setForeground(ACCENT_PRIMARY);

                pathLabel = new JLabel("No OneDrive folder selected");
                pathLabel.setForeground(TEXT_MUTED);
                pathLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

                JPanel titlePanel = new JPanel();
                titlePanel.setOpaque(false);
                titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

                JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                titleRow.setOpaque(false);
                titleRow.add(title);
                titleRow.add(Box.createHorizontalStrut(10));
                titleRow.add(tagline);

                titlePanel.add(titleRow);
                titlePanel.add(Box.createVerticalStrut(3));
                titlePanel.add(pathLabel);

                JButton selectFolderButton = createModernButton(
                                "Select OneDrive Folder", false);

                JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                buttonWrapper.setOpaque(false);
                buttonWrapper.add(selectFolderButton);

                topPanel.add(titlePanel, BorderLayout.WEST);
                topPanel.add(buttonWrapper, BorderLayout.EAST);

                // =========================================================
                // SUBJECT LIST
                // =========================================================

                subjectModel = new DefaultListModel<>();

                subjectList = new JList<>(subjectModel);
                subjectList.setFont(new Font("SansSerif", Font.PLAIN, 15));
                subjectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                subjectList.setBackground(BG_SECONDARY);
                subjectList.setForeground(TEXT_PRIMARY);
                subjectList.setSelectionBackground(ACCENT_PRIMARY);
                subjectList.setSelectionForeground(Color.WHITE);
                subjectList.setFixedCellHeight(38);
                subjectList.setBorder(new EmptyBorder(6, 12, 6, 12));
                subjectList.setCellRenderer(new ModernListCellRenderer());

                JScrollPane subjectScroll = createModernScrollPane(subjectList);
                subjectScroll.setBorder(createSectionBorder("Subjects"));

                // =========================================================
                // CATEGORY LIST
                // =========================================================

                categoryModel = new DefaultListModel<>();

                categoryList = new JList<>(categoryModel);
                categoryList.setFont(new Font("SansSerif", Font.PLAIN, 14));
                categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                categoryList.setBackground(BG_SECONDARY);
                categoryList.setForeground(TEXT_PRIMARY);
                categoryList.setSelectionBackground(ACCENT_PRIMARY);
                categoryList.setSelectionForeground(Color.WHITE);
                categoryList.setFixedCellHeight(34);
                categoryList.setBorder(new EmptyBorder(6, 12, 6, 12));
                categoryList.setCellRenderer(new ModernListCellRenderer());

                JScrollPane categoryScroll = createModernScrollPane(categoryList);
                categoryScroll.setBorder(createSectionBorder("Resource Clusters"));

                // =========================================================
                // STUDYSYNC AI PANEL
                // =========================================================

                aiPanel = createAiPanel();

                // =========================================================
                // LEFT SIDE
                // =========================================================

                JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
                leftPanel.setBackground(BG_PRIMARY);
                leftPanel.setBorder(new EmptyBorder(8, 8, 8, 4));

                leftPanel.add(subjectScroll, BorderLayout.CENTER);

                JPanel bottomLeft = new JPanel(new BorderLayout(0, 10));
                bottomLeft.setBackground(BG_PRIMARY);

                bottomLeft.add(aiPanel, BorderLayout.NORTH);
                bottomLeft.add(categoryScroll, BorderLayout.CENTER);

                bottomLeft.setPreferredSize(new Dimension(340, 420));

                leftPanel.add(bottomLeft, BorderLayout.SOUTH);
                leftPanel.setPreferredSize(new Dimension(340, 650));

                // =========================================================
                // SEARCH + TYPE FILTER
                // =========================================================

                JPanel filterPanel = new JPanel(new BorderLayout(12, 0));
                filterPanel.setBackground(BG_PRIMARY);
                filterPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

                searchField = createModernTextField("Search resources...");

                typeFilter = createModernComboBox(
                                new String[] {
                                                "All Files", "PDF", "DOCX",
                                                "PPTX", "XLSX", "TXT", "Images"
                                });

                filterPanel.add(searchField, BorderLayout.CENTER);
                filterPanel.add(typeFilter, BorderLayout.EAST);

                // =========================================================
                // RESOURCE PANEL
                // =========================================================

                resourcePanel = new JPanel();
                resourcePanel.setBackground(BG_PRIMARY);
                resourcePanel.setLayout(
                                new BoxLayout(resourcePanel, BoxLayout.Y_AXIS));
                resourcePanel.setBorder(new EmptyBorder(6, 6, 6, 6));

                JScrollPane resourceScroll = createModernScrollPane(resourcePanel);
                resourceScroll.setBorder(createSectionBorder("Resources"));

                JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
                rightPanel.setBackground(BG_PRIMARY);
                rightPanel.setBorder(new EmptyBorder(8, 4, 8, 8));

                rightPanel.add(filterPanel, BorderLayout.NORTH);
                rightPanel.add(resourceScroll, BorderLayout.CENTER);

                // =========================================================
                // MAIN SPLIT
                // =========================================================

                JSplitPane mainSplit = new JSplitPane(
                                JSplitPane.HORIZONTAL_SPLIT,
                                leftPanel, rightPanel);

                mainSplit.setDividerLocation(340);
                mainSplit.setResizeWeight(0.0);
                mainSplit.setBackground(BG_PRIMARY);
                mainSplit.setBorder(null);
                mainSplit.setDividerSize(6);

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
                                chooser.setCurrentDirectory(cloudStorage);
                        }

                        int result = chooser.showOpenDialog(this);

                        if (result == JFileChooser.APPROVE_OPTION) {

                                selectedOneDriveFolder = chooser.getSelectedFile();

                                pathLabel.setText(
                                                selectedOneDriveFolder
                                                                .getAbsolutePath());
                                pathLabel.setForeground(TEXT_SECONDARY);

                                clearAiSelection();
                                loadSubjects(selectedOneDriveFolder);
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

                typeFilter.addActionListener(e -> refreshResources());
        }

        // =============================================================
        // DARK THEME
        // =============================================================

        private void applyDarkTheme() {
                try {
                        UIManager.setLookAndFeel(
                                        UIManager.getCrossPlatformLookAndFeelClassName());
                } catch (Exception ignored) {
                }

                UIManager.put("Panel.background", BG_PRIMARY);
                UIManager.put("OptionPane.background", BG_SECONDARY);
                UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
                UIManager.put("Button.background", BG_TERTIARY);
                UIManager.put("Button.foreground", TEXT_PRIMARY);
                UIManager.put("Label.foreground", TEXT_PRIMARY);

                UIManager.put("TextField.background", BG_INPUT);
                UIManager.put("TextField.foreground", TEXT_PRIMARY);
                UIManager.put("TextField.caretForeground", TEXT_PRIMARY);

                UIManager.put("ComboBox.background", BG_INPUT);
                UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
                UIManager.put("ComboBox.selectionBackground", ACCENT_PRIMARY);
                UIManager.put("ComboBox.selectionForeground", Color.WHITE);

                UIManager.put("List.background", BG_SECONDARY);
                UIManager.put("List.foreground", TEXT_PRIMARY);
                UIManager.put("List.selectionBackground", ACCENT_PRIMARY);
                UIManager.put("List.selectionForeground", Color.WHITE);

                UIManager.put("ScrollPane.background", BG_SECONDARY);
                UIManager.put("ScrollBar.background", SCROLLBAR_TRACK);
                UIManager.put("ScrollBar.thumb", SCROLLBAR_THUMB);
                UIManager.put("ScrollBar.track", SCROLLBAR_TRACK);
                UIManager.put("ScrollBar.thumbDarkShadow", SCROLLBAR_THUMB);
                UIManager.put("ScrollBar.thumbShadow", SCROLLBAR_THUMB);
                UIManager.put("ScrollBar.thumbHighlight", SCROLLBAR_THUMB);
                UIManager.put("ScrollBar.width", 10);

                UIManager.put("SplitPane.background", BG_PRIMARY);
                UIManager.put("SplitPaneDivider.draggingColor", ACCENT_PRIMARY);

                UIManager.put("TitledBorder.titleColor", TEXT_SECONDARY);

                UIManager.put("FileChooser.background", BG_SECONDARY);
        }

        // =============================================================
        // MODERN UI COMPONENTS
        // =============================================================

        private JButton createModernButton(String text, boolean primary) {
                JButton button = new JButton(text) {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);

                                int w = getWidth(), h = getHeight();
                                RoundRectangle2D.Float shape =
                                                new RoundRectangle2D.Float(0, 0, w, h, 12, 12);

                                if (primary) {
                                        GradientPaint gp = new GradientPaint(
                                                        0, 0, ACCENT_PRIMARY,
                                                        w, h, ACCENT_GRADIENT_END);
                                        g2.setPaint(gp);
                                } else {
                                        g2.setColor(getModel().isRollover()
                                                        ? BG_CARD_HOVER : BG_TERTIARY);
                                }

                                g2.fill(shape);

                                if (!primary) {
                                        g2.setColor(BORDER_COLOR);
                                        g2.draw(shape);
                                }

                                g2.setColor(primary ? Color.WHITE : TEXT_PRIMARY);
                                g2.setFont(getFont());
                                FontMetrics fm = g2.getFontMetrics();
                                int textX = (w - fm.stringWidth(getText())) / 2;
                                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                                g2.drawString(getText(), textX, textY);
                                g2.dispose();
                        }
                };

                button.setFont(new Font("SansSerif", Font.BOLD, 13));
                button.setForeground(primary ? Color.WHITE : TEXT_PRIMARY);
                button.setPreferredSize(new Dimension(
                                button.getFontMetrics(button.getFont())
                                                .stringWidth(text) + 40, 38));
                button.setBorderPainted(false);
                button.setContentAreaFilled(false);
                button.setFocusPainted(false);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                button.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                                button.repaint();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                                button.repaint();
                        }
                });

                return button;
        }

        private JTextField createModernTextField(String placeholder) {
                JTextField field = new JTextField() {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(getBackground());
                                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                                g2.dispose();
                                super.paintComponent(g);

                                if (getText().isEmpty() && !hasFocus()) {
                                        Graphics2D g2p = (Graphics2D) g.create();
                                        g2p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                                        g2p.setColor(TEXT_MUTED);
                                        g2p.setFont(getFont());
                                        Insets insets = getInsets();
                                        FontMetrics fm = g2p.getFontMetrics();
                                        int textX = insets.left + 2;
                                        int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                                        g2p.drawString(placeholder, textX, textY);
                                        g2p.dispose();
                                }
                        }
                };
                field.setFont(new Font("SansSerif", Font.PLAIN, 14));
                field.setBackground(BG_INPUT);
                field.setForeground(TEXT_PRIMARY);
                field.setCaretColor(ACCENT_PRIMARY);
                field.setBorder(BorderFactory.createCompoundBorder(
                                new RoundedBorder(10, BORDER_COLOR),
                                new EmptyBorder(8, 14, 8, 14)));
                field.setOpaque(false);
                field.setPreferredSize(new Dimension(200, 38));
                return field;
        }

        private JComboBox<String> createModernComboBox(String[] items) {
                JComboBox<String> combo = new JComboBox<>(items);
                combo.setFont(new Font("SansSerif", Font.PLAIN, 13));
                combo.setBackground(BG_INPUT);
                combo.setForeground(TEXT_PRIMARY);
                combo.setBorder(new RoundedBorder(10, BORDER_COLOR));
                combo.setPreferredSize(new Dimension(130, 38));
                combo.setRenderer(new DefaultListCellRenderer() {
                        @Override
                        public Component getListCellRendererComponent(
                                        JList<?> list, Object value, int index,
                                        boolean isSelected, boolean cellHasFocus) {
                                super.getListCellRendererComponent(
                                                list, value, index, isSelected, cellHasFocus);
                                setBackground(isSelected ? ACCENT_PRIMARY : BG_SECONDARY);
                                setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
                                setBorder(new EmptyBorder(6, 12, 6, 12));
                                return this;
                        }
                });
                return combo;
        }

        private JScrollPane createModernScrollPane(Component view) {
                JScrollPane sp = new JScrollPane(view);
                sp.setBackground(BG_SECONDARY);
                sp.getViewport().setBackground(BG_SECONDARY);
                sp.setBorder(null);
                sp.getVerticalScrollBar().setUnitIncrement(16);
                return sp;
        }

        private javax.swing.border.Border createSectionBorder(String title) {
                return BorderFactory.createCompoundBorder(
                                BorderFactory.createCompoundBorder(
                                                new RoundedBorder(12, BORDER_COLOR),
                                                BorderFactory.createTitledBorder(
                                                                BorderFactory.createEmptyBorder(),
                                                                title,
                                                                javax.swing.border.TitledBorder.LEFT,
                                                                javax.swing.border.TitledBorder.TOP,
                                                                new Font("SansSerif", Font.BOLD, 13),
                                                                TEXT_SECONDARY)),
                                new EmptyBorder(4, 4, 4, 4));
        }

        // =============================================================
        // CUSTOM BORDERS & RENDERERS
        // =============================================================

        private static class RoundedBorder extends AbstractBorder {
                private final int radius;
                private final Color color;

                RoundedBorder(int radius, Color color) {
                        this.radius = radius;
                        this.color = color;
                }

                @Override
                public void paintBorder(Component c, Graphics g, int x, int y,
                                int width, int height) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(color);
                        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
                        g2.dispose();
                }

                @Override
                public Insets getBorderInsets(Component c) {
                        return new Insets(4, 8, 4, 8);
                }
        }

        private class ModernListCellRenderer extends DefaultListCellRenderer {
                @Override
                public Component getListCellRendererComponent(
                                JList<?> list, Object value, int index,
                                boolean isSelected, boolean cellHasFocus) {
                        super.getListCellRendererComponent(
                                        list, value, index, isSelected, cellHasFocus);

                        setFont(new Font("SansSerif", Font.PLAIN, 14));
                        setBorder(new EmptyBorder(8, 16, 8, 16));

                        if (isSelected) {
                                setBackground(ACCENT_PRIMARY);
                                setForeground(Color.WHITE);
                        } else {
                                setBackground(BG_SECONDARY);
                                setForeground(TEXT_PRIMARY);
                        }

                        return this;
                }
        }

        // =============================================================
        // AI PANEL
        // =============================================================

        private JPanel createAiPanel() {

                JPanel panel = new JPanel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                GradientPaint gp = new GradientPaint(
                                                0, 0, new Color(99, 102, 241, 18),
                                                getWidth(), getHeight(), new Color(139, 92, 246, 10));
                                g2.setPaint(gp);
                                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                                g2.dispose();
                        }
                };

                panel.setOpaque(false);
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(BorderFactory.createCompoundBorder(
                                new RoundedBorder(12, new Color(99, 102, 241, 70)),
                                new EmptyBorder(12, 14, 12, 14)));

                // Row 1: Header (Title + Selection counter)
                JPanel header = new JPanel(new BorderLayout());
                header.setOpaque(false);
                header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

                JLabel aiTitle = new JLabel("StudySync AI");
                aiTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
                aiTitle.setForeground(ACCENT_PRIMARY);

                aiSelectionLabel = new JLabel("No files selected");
                aiSelectionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                aiSelectionLabel.setForeground(TEXT_MUTED);

                header.add(aiTitle, BorderLayout.WEST);
                header.add(aiSelectionLabel, BorderLayout.EAST);

                // Row 2: Full-width query input field
                aiQueryField = createModernTextField("Ask AI about selected resources...");
                aiQueryField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

                // Row 3: Full-width action button
                aiAskButton = createModernButton("Ask AI", true);
                aiAskButton.setEnabled(false);
                aiAskButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
                aiAskButton.setAlignmentX(Component.CENTER_ALIGNMENT);

                aiAskButton.addActionListener(e -> askSelectedFilesWithAi());
                aiQueryField.addActionListener(e -> {
                        if (aiAskButton.isEnabled()) {
                                askSelectedFilesWithAi();
                        }
                });

                aiQueryField.getDocument().addDocumentListener(new DocumentListener() {
                        @Override
                        public void insertUpdate(DocumentEvent e) {
                                updateAiSelectionUi();
                        }

                        @Override
                        public void removeUpdate(DocumentEvent e) {
                                updateAiSelectionUi();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e) {
                                updateAiSelectionUi();
                        }
                });

                panel.add(header);
                panel.add(Box.createVerticalStrut(10));
                panel.add(aiQueryField);
                panel.add(Box.createVerticalStrut(8));
                panel.add(aiAskButton);

                return panel;
        }

        private void updateAiSelectionUi() {

                int count = selectedAiFiles.size();

                if (count == 0) {
                        aiSelectionLabel.setText("No files selected");
                        aiSelectionLabel.setForeground(TEXT_MUTED);
                } else if (count == 1) {
                        aiSelectionLabel.setText("1 file selected");
                        aiSelectionLabel.setForeground(SUCCESS_COLOR);
                } else {
                        aiSelectionLabel.setText(count + " files selected");
                        aiSelectionLabel.setForeground(SUCCESS_COLOR);
                }

                aiAskButton.setEnabled(count > 0);
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
                        subjectModel.addElement(folder.getName());
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

                currentFiles = getFilesRecursively(selectedSubjectFolder);

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
                                .trim().toLowerCase();

                String selectedCategory = categoryList.getSelectedValue();
                String selectedType = (String) typeFilter.getSelectedItem();

                int displayed = 0;

                for (File file : currentFiles) {

                        if (file == null || !file.exists() || !file.isFile()) {
                                continue;
                        }

                        if (selectedCategory != null &&
                                        !getCategory(file).equals(selectedCategory)) {
                                continue;
                        }

                        if (!matchesType(file, selectedType)) {
                                continue;
                        }

                        if (!search.isEmpty() &&
                                        !file.getName().toLowerCase().contains(search)) {
                                continue;
                        }

                        addResourceCard(file);
                        displayed++;
                }

                if (displayed == 0) {

                        JPanel emptyState = new JPanel();
                        emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
                        emptyState.setBackground(BG_PRIMARY);
                        emptyState.setBorder(new EmptyBorder(60, 40, 60, 40));

                        JLabel emptyText = new JLabel("No resources match your filters");
                        emptyText.setFont(new Font("SansSerif", Font.BOLD, 15));
                        emptyText.setForeground(TEXT_SECONDARY);
                        emptyText.setAlignmentX(Component.CENTER_ALIGNMENT);

                        JLabel emptySub = new JLabel("Try selecting another subject or clearing your search");
                        emptySub.setFont(new Font("SansSerif", Font.PLAIN, 13));
                        emptySub.setForeground(TEXT_MUTED);
                        emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

                        emptyState.add(emptyText);
                        emptyState.add(Box.createVerticalStrut(6));
                        emptyState.add(emptySub);

                        resourcePanel.add(emptyState);
                }

                resourcePanel.revalidate();
                resourcePanel.repaint();
        }

        // =============================================================
        // RESOURCE CARD
        // =============================================================

        private void addResourceCard(File file) {

                JPanel card = new JPanel(new BorderLayout(14, 0)) {
                        private boolean hovered = false;

                        {
                                addMouseListener(new MouseAdapter() {
                                        @Override
                                        public void mouseEntered(MouseEvent e) {
                                                hovered = true;
                                                repaint();
                                        }

                                        @Override
                                        public void mouseExited(MouseEvent e) {
                                                hovered = false;
                                                repaint();
                                        }
                                });
                        }

                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(hovered ? BG_CARD_HOVER : BG_CARD);
                                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                                g2.setColor(hovered ? ACCENT_PRIMARY : BORDER_COLOR);
                                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                                g2.dispose();
                        }
                };

                card.setOpaque(false);
                card.setBorder(new EmptyBorder(14, 16, 14, 16));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

                // ---------- Selection checkbox ----------

                JCheckBox selectForAi = new JCheckBox();
                String path = file.getAbsolutePath();
                selectForAi.setSelected(selectedAiFiles.contains(path));
                selectForAi.setToolTipText("Select for StudySync AI");
                selectForAi.setOpaque(false);
                selectForAi.setForeground(ACCENT_PRIMARY);

                selectForAi.addActionListener(e -> {
                        if (selectForAi.isSelected()) {
                                selectedAiFiles.add(path);
                        } else {
                                selectedAiFiles.remove(path);
                        }
                        updateAiSelectionUi();
                });

                // ---------- File badge (modern, no emojis) ----------

                JComponent badge = createFileTypeBadge(file);

                JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                left.setOpaque(false);
                left.add(selectForAi);
                left.add(badge);

                // ---------- File information ----------

                JPanel info = new JPanel();
                info.setOpaque(false);
                info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

                JLabel name = new JLabel(file.getName());
                name.setFont(new Font("SansSerif", Font.BOLD, 14));
                name.setForeground(TEXT_PRIMARY);

                JLabel details = new JLabel(
                                getFileType(file) + "   •   "
                                                + formatSize(file.length())
                                                + "   •   " + getCategory(file));
                details.setForeground(TEXT_MUTED);
                details.setFont(new Font("SansSerif", Font.PLAIN, 12));

                info.add(name);
                info.add(Box.createVerticalStrut(4));
                info.add(details);

                // ---------- Open button ----------

                JButton openButton = createModernButton("OPEN", true);
                openButton.setPreferredSize(new Dimension(80, 32));
                openButton.addActionListener(e -> openFile(file));

                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                buttons.setOpaque(false);
                buttons.add(openButton);

                card.add(left, BorderLayout.WEST);
                card.add(info, BorderLayout.CENTER);
                card.add(buttons, BorderLayout.EAST);

                resourcePanel.add(card);
                resourcePanel.add(Box.createVerticalStrut(8));
        }

        // =============================================================
        // ASK AI ABOUT SELECTED FILES
        // =============================================================

        private void askSelectedFilesWithAi() {

                String query = aiQueryField.getText().trim();

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

                showMultiFileAiDialog(files, query);
        }

        private void showMultiFileAiDialog(
                        List<File> files,
                        String query) {

                JDialog dialog = new JDialog(this, "StudySync AI", false);
                dialog.setSize(960, 720);
                dialog.setLocationRelativeTo(this);
                dialog.getContentPane().setBackground(BG_PRIMARY);

                // ---- Header ----
                JPanel header = new JPanel(new BorderLayout(8, 4)) {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                GradientPaint gp = new GradientPaint(
                                                0, 0, BG_SECONDARY,
                                                getWidth(), 0, BG_TERTIARY);
                                g2.setPaint(gp);
                                g2.fillRect(0, 0, getWidth(), getHeight());
                                GradientPaint accent = new GradientPaint(
                                                0, getHeight() - 2, ACCENT_PRIMARY,
                                                getWidth(), getHeight() - 2, ACCENT_SECONDARY);
                                g2.setPaint(accent);
                                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                                g2.dispose();
                        }
                };
                header.setOpaque(false);
                header.setBorder(new EmptyBorder(14, 20, 14, 20));

                JLabel dialogTitle = new JLabel("StudySync AI");
                dialogTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
                dialogTitle.setForeground(TEXT_PRIMARY);

                JLabel info = new JLabel(
                                "Analyzing " + files.size()
                                                + " selected resource"
                                                + (files.size() == 1 ? "" : "s")
                                                + "...");
                info.setForeground(ACCENT_PRIMARY);
                info.setFont(new Font("SansSerif", Font.ITALIC, 13));

                header.add(dialogTitle, BorderLayout.NORTH);
                header.add(info, BorderLayout.SOUTH);

                // ---- Output pane ----
                JEditorPane output = new JEditorPane();
                output.setContentType("text/html");
                output.setEditable(false);
                output.setBackground(BG_PRIMARY);
                output.putClientProperty(
                                JEditorPane.HONOR_DISPLAY_PROPERTIES,
                                Boolean.TRUE);

                List<String> conversation = new ArrayList<>();
                conversation.add("USER: " + query);

                StringBuilder chatHtml = new StringBuilder();
                chatHtml.append(buildChatHtmlHead());

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
                                .append("<div class='thinking'>")
                                .append("Analyzing the original files...")
                                .append("</div>")
                                .append("</div></body></html>");

                output.setText(chatHtml.toString());
                output.setCaretPosition(0);

                JScrollPane scrollPane = createModernScrollPane(output);
                scrollPane.setBorder(new EmptyBorder(8, 12, 8, 12));

                // ---- Chat input ----
                JTextField chatField = createModernTextField(
                                "Ask a follow-up question...");

                JButton sendButton = createModernButton("Send", true);
                sendButton.setPreferredSize(new Dimension(90, 38));

                JButton closeButton = createModernButton("Close", false);
                closeButton.setPreferredSize(new Dimension(80, 34));

                JLabel chatStatus = new JLabel(
                                "Ask follow-up questions without reopening resources");
                chatStatus.setForeground(TEXT_MUTED);
                chatStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));

                JPanel chatInput = new JPanel(new BorderLayout(10, 0));
                chatInput.setBackground(BG_PRIMARY);
                chatInput.setBorder(new EmptyBorder(10, 16, 10, 16));
                chatInput.add(chatField, BorderLayout.CENTER);
                chatInput.add(sendButton, BorderLayout.EAST);

                JPanel bottom = new JPanel(new BorderLayout(0, 6));
                bottom.setBackground(BG_PRIMARY);
                bottom.add(chatStatus, BorderLayout.NORTH);
                bottom.add(chatInput, BorderLayout.CENTER);

                JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
                closePanel.setBackground(BG_PRIMARY);
                closePanel.add(closeButton);
                bottom.add(closePanel, BorderLayout.SOUTH);

                dialog.setLayout(new BorderLayout());
                dialog.add(header, BorderLayout.NORTH);
                dialog.add(scrollPane, BorderLayout.CENTER);
                dialog.add(bottom, BorderLayout.SOUTH);

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

                        appendChatMessage(output, chatHtml,
                                        "You", message, false);

                        String context = buildConversationInstruction(conversation);

                        info.setText("StudySync AI is thinking...");

                        SwingWorker<String, Void> followUpWorker = new SwingWorker<>() {

                                @Override
                                protected String doInBackground() throws Exception {
                                        return GeminiAI.analyzeFiles(files, context);
                                }

                                @Override
                                protected void done() {
                                        try {
                                                String result = get();
                                                conversation.add("ASSISTANT: " + result);
                                                appendChatMessage(output, chatHtml,
                                                                "StudySync AI", result, true);

                                                info.setText("Conversation • "
                                                                + files.size()
                                                                + " selected resource"
                                                                + (files.size() == 1 ? "" : "s"));

                                        } catch (Exception ex) {
                                                Throwable cause = ex.getCause() != null
                                                                ? ex.getCause() : ex;
                                                appendChatMessage(output, chatHtml,
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
                                return GeminiAI.analyzeFiles(files, query);
                        }

                        @Override
                        protected void done() {
                                try {
                                        String result = get();
                                        conversation.add("ASSISTANT: " + result);

                                        chatHtml.setLength(0);
                                        chatHtml.append(buildChatHtmlHead());
                                        chatHtml.append(
                                                        "<div class='ai'><div class='label'>StudySync AI</div>")
                                                        .append(markdownToHtml(result))
                                                        .append("</div></body></html>");

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
                                                        ? ex.getCause() : ex;

                                        chatHtml.setLength(0);
                                        chatHtml.append(buildChatHtmlHead());
                                        chatHtml.append("<div class='ai error'><div class='label'>Error</div>")
                                                        .append("<p>StudySync AI could not analyze the selected resources.</p>")
                                                        .append("<p>").append(escapeHtml(safeMessage(cause))).append("</p>")
                                                        .append("</div></body></html>");

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

        // =============================================================
        // CHAT HTML STYLING (DARK THEME)
        // =============================================================

        private String buildChatHtmlHead() {
                return "<html><head><style>"
                                + "body{"
                                + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;"
                                + "  font-size: 15px;"
                                + "  color: #ededed;"
                                + "  background: #0f0f14;"
                                + "  margin: 16px;"
                                + "  line-height: 1.6;"
                                + "}"
                                + "h1{"
                                + "  font-size: 22px;"
                                + "  margin: 6px 0 14px;"
                                + "  color: #e0e0f0;"
                                + "  border-bottom: 2px solid #6366f1;"
                                + "  padding-bottom: 8px;"
                                + "}"
                                + "h2{"
                                + "  font-size: 19px;"
                                + "  margin: 20px 0 10px;"
                                + "  color: #c7c7e0;"
                                + "  border-left: 3px solid #8b5cf6;"
                                + "  padding-left: 10px;"
                                + "}"
                                + "h3{"
                                + "  font-size: 17px;"
                                + "  margin: 18px 0 8px;"
                                + "  color: #b0b0d0;"
                                + "}"
                                + "p{"
                                + "  margin: 8px 0;"
                                + "  line-height: 1.65;"
                                + "}"
                                + "li{"
                                + "  margin: 5px 0;"
                                + "  line-height: 1.55;"
                                + "}"
                                + "ul, ol{"
                                + "  padding-left: 24px;"
                                + "  margin: 8px 0;"
                                + "}"
                                + "table{"
                                + "  border-collapse: collapse;"
                                + "  margin: 14px 0;"
                                + "  width: 100%;"
                                + "  border-radius: 8px;"
                                + "  overflow: hidden;"
                                + "}"
                                + "th,td{"
                                + "  border: 1px solid #37374a;"
                                + "  padding: 10px 14px;"
                                + "  text-align: left;"
                                + "}"
                                + "th{"
                                + "  background: #1e1e2c;"
                                + "  font-weight: 600;"
                                + "  color: #a5b4fc;"
                                + "}"
                                + "td{"
                                + "  background: #18181f;"
                                + "}"
                                + "tr:nth-child(even) td{"
                                + "  background: #1c1c26;"
                                + "}"
                                + ".user{"
                                + "  background: linear-gradient(135deg, #1e293b, #1e1b4b);"
                                + "  border: 1px solid #334155;"
                                + "  border-radius: 12px;"
                                + "  padding: 14px 18px;"
                                + "  margin: 14px 0;"
                                + "}"
                                + ".ai{"
                                + "  background: linear-gradient(135deg, #1a1a24, #1c1c28);"
                                + "  border: 1px solid #2d2d3e;"
                                + "  border-left: 3px solid #6366f1;"
                                + "  border-radius: 12px;"
                                + "  padding: 16px 20px;"
                                + "  margin: 14px 0;"
                                + "}"
                                + ".ai.error{"
                                + "  border-left-color: #f87171;"
                                + "}"
                                + ".label{"
                                + "  font-weight: 700;"
                                + "  margin-bottom: 8px;"
                                + "  color: #a5b4fc;"
                                + "  font-size: 14px;"
                                + "}"
                                + ".math{"
                                + "  font-family: 'SF Mono', Menlo, 'Cascadia Code', monospace;"
                                + "  background: #2a2a3a;"
                                + "  padding: 3px 7px;"
                                + "  border-radius: 5px;"
                                + "  color: #c4b5fd;"
                                + "  font-size: 14px;"
                                + "}"
                                + "code{"
                                + "  font-family: 'SF Mono', Menlo, 'Cascadia Code', monospace;"
                                + "  background: #2a2a3a;"
                                + "  padding: 2px 7px;"
                                + "  border-radius: 5px;"
                                + "  color: #34d399;"
                                + "  font-size: 13px;"
                                + "}"
                                + "pre{"
                                + "  background: #111118;"
                                + "  border: 1px solid #2d2d3e;"
                                + "  padding: 14px 18px;"
                                + "  border-radius: 10px;"
                                + "  white-space: pre-wrap;"
                                + "  font-family: 'SF Mono', Menlo, 'Cascadia Code', monospace;"
                                + "  font-size: 13px;"
                                + "  color: #d1d5db;"
                                + "  line-height: 1.5;"
                                + "  overflow-x: auto;"
                                + "}"
                                + "hr{"
                                + "  border: none;"
                                + "  border-top: 1px solid #37374a;"
                                + "  margin: 20px 0;"
                                + "}"
                                + "blockquote{"
                                + "  border-left: 3px solid #6366f1;"
                                + "  margin: 12px 0;"
                                + "  padding: 8px 16px;"
                                + "  background: #1a1a28;"
                                + "  border-radius: 0 8px 8px 0;"
                                + "  color: #9ca3af;"
                                + "}"
                                + ".thinking{"
                                + "  color: #6366f1;"
                                + "  font-style: italic;"
                                + "  padding: 8px 0;"
                                + "}"
                                + ".thinking-dot{"
                                + "  color: #8b5cf6;"
                                + "  font-size: 12px;"
                                + "}"
                                + "b, strong{"
                                + "  color: #e0e0f0;"
                                + "  font-weight: 600;"
                                + "}"
                                + "a{"
                                + "  color: #818cf8;"
                                + "  text-decoration: none;"
                                + "}"
                                + "</style></head><body>";
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

                int bodyEnd = transcript.lastIndexOf("</body>");

                if (bodyEnd < 0) {
                        transcript.append(buildChatHtmlHead());
                        bodyEnd = transcript.length();
                }

                String cssClass = assistant ? "ai" : "user";

                String content = assistant
                                ? markdownToHtml(message)
                                : "<p>"
                                                + inlineMarkdown(escapeHtml(message))
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
         * Robust Markdown-to-HTML renderer for Swing's JEditorPane.
         *
         * Converts the Markdown formatting commonly returned by LLMs
         * (Gemini, etc.) into clean, structured HTML:
         *   - # / ## / ### headings
         *   - **bold**, *italic*, `inline code`
         *   - Bullet and numbered lists (including nested)
         *   - Fenced code blocks (```)
         *   - Markdown tables
         *   - Horizontal rules
         *   - Blockquotes (>)
         *   - LaTeX display equations ($$...$$)
         *   - Inline math ($...$)
         *
         * Raw markdown characters (##, **, <>, etc.) are never shown
         * to the user — they are always converted to proper HTML.
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
                boolean inCode = false;
                boolean inTable = false;
                boolean inBlockquote = false;
                boolean tableHasHeader = false;

                for (int i = 0; i < lines.length; i++) {

                        String rawLine = lines[i];
                        String line = rawLine.trim();

                        // ---- Fenced code blocks ----
                        if (line.startsWith("```")) {

                                if (!inCode) {
                                        closeList(html, inUl, inOl);
                                        inUl = false;
                                        inOl = false;

                                        if (inBlockquote) {
                                                html.append("</blockquote>");
                                                inBlockquote = false;
                                        }

                                        String lang = line.substring(3).trim();
                                        html.append("<pre>");
                                        if (!lang.isEmpty()) {
                                                html.append("<code class='lang-")
                                                                .append(escapeHtml(lang))
                                                                .append("'>");
                                        }
                                        inCode = true;
                                } else {
                                        html.append("</pre>");
                                        inCode = false;
                                }

                                continue;
                        }

                        if (inCode) {
                                html.append(escapeHtml(rawLine)).append('\n');
                                continue;
                        }

                        // ---- Empty lines ----
                        if (line.isEmpty()) {

                                if (inTable) {
                                        html.append("</table>");
                                        inTable = false;
                                        tableHasHeader = false;
                                }

                                if (inBlockquote) {
                                        html.append("</blockquote>");
                                        inBlockquote = false;
                                }

                                continue;
                        }

                        // ---- Horizontal rule ----
                        if (line.matches("^([-*_])\\s*(\\1\\s*){2,}$")) {
                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;
                                html.append("<hr>");
                                continue;
                        }

                        // ---- Blockquotes ----
                        if (line.startsWith("> ") || line.equals(">")) {
                                if (!inBlockquote) {
                                        closeList(html, inUl, inOl);
                                        inUl = false;
                                        inOl = false;
                                        html.append("<blockquote>");
                                        inBlockquote = true;
                                }
                                String quoteContent = line.length() > 2
                                                ? line.substring(2) : "";
                                html.append("<p>")
                                                .append(inlineMarkdown(escapeHtml(quoteContent)))
                                                .append("</p>");
                                continue;
                        }

                        if (inBlockquote && !line.startsWith(">")) {
                                html.append("</blockquote>");
                                inBlockquote = false;
                        }

                        // ---- Table separator ----
                        if (isTableSeparator(line)) {
                                tableHasHeader = true;
                                continue;
                        }

                        // ---- Table row ----
                        if (line.startsWith("|") &&
                                        line.endsWith("|") &&
                                        line.indexOf('|', 1) > 0) {

                                if (!inTable) {
                                        closeList(html, inUl, inOl);
                                        inUl = false;
                                        inOl = false;
                                        html.append("<table>");
                                        inTable = true;

                                        // Check if next line is separator → this is a header row
                                        boolean nextIsSep = (i + 1 < lines.length)
                                                        && isTableSeparator(lines[i + 1].trim());

                                        String[] cells = line.substring(1, line.length() - 1)
                                                        .split("\\|", -1);
                                        html.append("<tr>");
                                        String tag = nextIsSep ? "th" : "td";
                                        for (String cell : cells) {
                                                html.append("<").append(tag).append(">")
                                                                .append(inlineMarkdown(
                                                                                escapeHtml(cell.trim())))
                                                                .append("</").append(tag).append(">");
                                        }
                                        html.append("</tr>");
                                        continue;
                                }

                                String[] cells = line.substring(1, line.length() - 1)
                                                .split("\\|", -1);
                                html.append("<tr>");
                                for (String cell : cells) {
                                        html.append("<td>")
                                                        .append(inlineMarkdown(
                                                                        escapeHtml(cell.trim())))
                                                        .append("</td>");
                                }
                                html.append("</tr>");
                                continue;
                        }

                        if (inTable) {
                                html.append("</table>");
                                inTable = false;
                                tableHasHeader = false;
                        }

                        // ---- Headings ----
                        if (line.startsWith("#### ")) {
                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;
                                html.append("<h3>")
                                                .append(inlineMarkdown(escapeHtml(line.substring(5))))
                                                .append("</h3>");
                                continue;
                        }

                        if (line.startsWith("### ")) {
                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;
                                html.append("<h3>")
                                                .append(inlineMarkdown(escapeHtml(line.substring(4))))
                                                .append("</h3>");
                                continue;
                        }

                        if (line.startsWith("## ")) {
                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;
                                html.append("<h2>")
                                                .append(inlineMarkdown(escapeHtml(line.substring(3))))
                                                .append("</h2>");
                                continue;
                        }

                        if (line.startsWith("# ")) {
                                closeList(html, inUl, inOl);
                                inUl = false;
                                inOl = false;
                                html.append("<h1>")
                                                .append(inlineMarkdown(escapeHtml(line.substring(2))))
                                                .append("</h1>");
                                continue;
                        }

                        // ---- Bullet list (with sub-bullet support) ----
                        if (line.matches("^[-*+]\\s+.+")) {

                                if (inOl) {
                                        html.append("</ol>");
                                        inOl = false;
                                }

                                if (!inUl) {
                                        html.append("<ul>");
                                        inUl = true;
                                }

                                String item = line.replaceFirst("^[-*+]\\s+", "");
                                html.append("<li>")
                                                .append(inlineMarkdown(escapeHtml(item)))
                                                .append("</li>");
                                continue;
                        }

                        // Sub-bullets (indented)
                        if (rawLine.matches("^\\s{2,}[-*+]\\s+.+")) {
                                String item = rawLine.trim().replaceFirst("^[-*+]\\s+", "");
                                html.append("<ul><li>")
                                                .append(inlineMarkdown(escapeHtml(item)))
                                                .append("</li></ul>");
                                continue;
                        }

                        // ---- Numbered list ----
                        if (line.matches("^\\d+[.)]\\s+.+")) {

                                if (inUl) {
                                        html.append("</ul>");
                                        inUl = false;
                                }

                                if (!inOl) {
                                        html.append("<ol>");
                                        inOl = true;
                                }

                                String item = line.replaceFirst("^\\d+[.)]\\s+", "");
                                html.append("<li>")
                                                .append(inlineMarkdown(escapeHtml(item)))
                                                .append("</li>");
                                continue;
                        }

                        closeList(html, inUl, inOl);
                        inUl = false;
                        inOl = false;

                        // ---- LaTeX display equation ----
                        if (line.startsWith("$$") &&
                                        line.endsWith("$$") &&
                                        line.length() > 4) {

                                html.append("<pre>")
                                                .append(escapeHtml(
                                                                line.substring(2, line.length() - 2).trim()))
                                                .append("</pre>");
                                continue;
                        }

                        // ---- Regular paragraph ----
                        html.append("<p>")
                                        .append(inlineMarkdown(escapeHtml(line)))
                                        .append("</p>");
                }

                if (inTable) {
                        html.append("</table>");
                }

                if (inCode) {
                        html.append("</pre>");
                }

                if (inBlockquote) {
                        html.append("</blockquote>");
                }

                closeList(html, inUl, inOl);

                return html.toString();
        }

        private static boolean isTableSeparator(String line) {

                if (!line.startsWith("|") || !line.endsWith("|")) {
                        return false;
                }

                String middle = line.substring(1, line.length() - 1);
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

        /**
         * Inline Markdown processing: bold, italic, inline code, math, links.
         */
        private static String inlineMarkdown(String value) {

                if (value == null || value.isEmpty()) {
                        return "";
                }

                String result = value;

                // Inline code (must come before bold/italic to avoid conflicts)
                result = result.replaceAll(
                                "`([^`]+)`",
                                "<code>$1</code>");

                // Bold (**text** or __text__)
                result = result.replaceAll(
                                "\\*\\*([^*]+)\\*\\*",
                                "<b>$1</b>");

                result = result.replaceAll(
                                "__([^_]+)__",
                                "<b>$1</b>");

                // Italic (*text* or _text_)
                result = result.replaceAll(
                                "(?<!\\*)\\*([^*]+)\\*(?!\\*)",
                                "<i>$1</i>");

                result = result.replaceAll(
                                "(?<!_)_([^_]+)_(?!_)",
                                "<i>$1</i>");

                // Strikethrough (~~text~~)
                result = result.replaceAll(
                                "~~([^~]+)~~",
                                "<del>$1</del>");

                // Inline math ($...$)
                result = result.replaceAll(
                                "\\$([^$]+)\\$",
                                "<span class='math'>$1</span>");

                // Links [text](url) — basic support
                result = result.replaceAll(
                                "\\[([^\\]]+)\\]\\(([^)]+)\\)",
                                "<a href='$2'>$1</a>");

                return result;
        }

        private static String escapeHtml(String value) {

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

                String name = file.getName().toLowerCase();

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

                String name = file.getName().toLowerCase();

                if (name.endsWith(".pdf"))
                        return "PDF Document";

                if (name.endsWith(".docx") || name.endsWith(".doc"))
                        return "Word Document";

                if (name.endsWith(".pptx") || name.endsWith(".ppt"))
                        return "PowerPoint";

                if (name.endsWith(".xlsx") || name.endsWith(".xls"))
                        return "Excel Spreadsheet";

                if (name.endsWith(".txt"))
                        return "Text File";

                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".webp"))
                        return "Image";

                return "File";
        }

        // =============================================================
        // FILE TYPE BADGE
        // =============================================================

        private JComponent createFileTypeBadge(File file) {

                String name = file.getName().toLowerCase();
                String ext;
                Color bg;
                Color fg;

                if (name.endsWith(".pdf")) {
                        ext = "PDF";
                        bg = new Color(239, 68, 68, 32);
                        fg = new Color(248, 113, 113);
                } else if (name.endsWith(".pptx") || name.endsWith(".ppt")) {
                        ext = "PPT";
                        bg = new Color(245, 158, 11, 32);
                        fg = new Color(251, 191, 36);
                } else if (name.endsWith(".docx") || name.endsWith(".doc")) {
                        ext = "DOC";
                        bg = new Color(59, 130, 246, 32);
                        fg = new Color(96, 165, 250);
                } else if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                        ext = "XLS";
                        bg = new Color(16, 185, 129, 32);
                        fg = new Color(52, 211, 153);
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif")) {
                        ext = "IMG";
                        bg = new Color(168, 85, 247, 32);
                        fg = new Color(192, 132, 252);
                } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                        ext = "TXT";
                        bg = new Color(14, 165, 233, 32);
                        fg = new Color(56, 189, 248);
                } else if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".tar") || name.endsWith(".gz")) {
                        ext = "ZIP";
                        bg = new Color(234, 179, 8, 32);
                        fg = new Color(250, 204, 21);
                } else {
                        ext = "FILE";
                        bg = new Color(100, 116, 139, 32);
                        fg = new Color(148, 163, 184);
                }

                final Color bgColor = bg;
                final Color fgColor = fg;
                final String text = ext;

                JPanel badge = new JPanel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(bgColor);
                                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                                g2.setColor(new Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), 80));
                                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                                g2.setColor(fgColor);
                                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                                FontMetrics fm = g2.getFontMetrics();
                                int tx = (getWidth() - fm.stringWidth(text)) / 2;
                                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                                g2.drawString(text, tx, ty);
                                g2.dispose();
                        }
                };

                badge.setOpaque(false);
                badge.setPreferredSize(new Dimension(46, 32));
                badge.setMinimumSize(new Dimension(46, 32));
                badge.setMaximumSize(new Dimension(46, 32));
                return badge;
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

        private List<File> getFilesRecursively(File folder) {

                List<File> files = new ArrayList<>();

                if (folder == null || !folder.isDirectory()) {
                        return files;
                }

                File[] contents = folder.listFiles();

                if (contents == null) {
                        return files;
                }

                for (File file : contents) {

                        if (file.isDirectory()) {
                                files.addAll(getFilesRecursively(file));
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

                        if (file == null || !file.exists() || !file.isFile()) {

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
