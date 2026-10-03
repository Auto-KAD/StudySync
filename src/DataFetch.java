import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * StudySync data layer.
 *
 * Owns resource acquisition and file-data operations:
 *
 * - locating the default OneDrive location
 * - identifying subject folders
 * - recursively scanning resource files
 * - resource cluster (category) classification
 * - file type matching and file type labels
 * - filtering resources by category / type / search text
 * - resolving locally readable files
 * - opening files with the operating system
 *
 * This class deliberately contains NO Swing code, NO AI logic,
 * NO Ollama calls and NO document parsing. It only returns data;
 * StudySync decides what to do with it and UI displays it.
 *
 * Dependency direction:
 *
 * StudySync ---> DataFetch
 */
public class DataFetch {

        // =============================================================
        // RESOURCE CLUSTERS
        // =============================================================

        private static final String[] CATEGORIES = {
                        "Lab",
                        "Lecture Slides / PPTs",
                        "Syllabus / Course Policy",
                        "Books",
                        "Previous Year Papers",
                        "Miscellaneous"
        };

        /**
         * Returns the resource cluster names, in display order.
         */
        public List<String> getCategories() {
                return new ArrayList<>(Arrays.asList(CATEGORIES));
        }

        // =============================================================
        // ONEDRIVE FOLDER
        // =============================================================

        /**
         * Returns the macOS CloudStorage folder (where OneDrive is
         * normally mounted) if it exists, otherwise null.
         */
        public File getDefaultOneDriveLocation() {

                File cloudStorage = new File(
                                System.getProperty("user.home"),
                                "Library/CloudStorage");

                if (cloudStorage.exists()) {
                        return cloudStorage;
                }

                return null;
        }

        public boolean isValidFolder(File folder) {
                return folder != null && folder.isDirectory();
        }

        // =============================================================
        // SUBJECTS
        // =============================================================

        /**
         * Returns the names of the subject folders directly inside the
         * OneDrive folder, sorted case-insensitively.
         */
        public List<String> getSubjects(File oneDriveFolder) {

                List<String> subjects = new ArrayList<>();

                if (!isValidFolder(oneDriveFolder)) {
                        return subjects;
                }

                File[] folders = oneDriveFolder.listFiles(
                                File::isDirectory);

                if (folders == null) {
                        return subjects;
                }

                Arrays.sort(
                                folders,
                                Comparator.comparing(
                                                File::getName,
                                                String.CASE_INSENSITIVE_ORDER));

                for (File folder : folders) {
                        subjects.add(folder.getName());
                }

                return subjects;
        }

        // =============================================================
        // RECURSIVE FILE SEARCH
        // =============================================================

        public List<File> getFilesRecursively(File folder) {

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
        // RESOURCE FILTERING
        // =============================================================

        /**
         * Returns the files that match the selected cluster, the
         * selected type filter and the search text.
         *
         * A null category means "all clusters".
         */
        public List<File> filterResources(
                        List<File> files,
                        String selectedCategory,
                        String selectedType,
                        String searchText) {

                List<File> matches = new ArrayList<>();

                if (files == null) {
                        return matches;
                }

                String search = searchText == null
                                ? ""
                                : searchText.trim().toLowerCase();

                for (File file : files) {

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

                        matches.add(file);
                }

                return matches;
        }

        // =============================================================
        // CLUSTER CLASSIFICATION
        // =============================================================

        public String getCategory(File file) {

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

        public boolean matchesType(
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
        // FILE TYPE LABEL
        // =============================================================

        public String getFileType(File file) {

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
        // LOCAL FILE AVAILABILITY
        // =============================================================

        /**
         * True if the file is present on this device (OneDrive files
         * that are cloud-only are not).
         */
        public boolean isAvailableLocally(File file) {
                return file != null && file.exists() && file.isFile();
        }

        /**
         * Converts selected absolute paths into files that exist
         * locally and can be read. Selection order is preserved.
         */
        public List<File> getReadableFiles(Collection<String> paths) {

                List<File> files = new ArrayList<>();

                if (paths == null) {
                        return files;
                }

                for (String path : paths) {

                        File file = new File(path);

                        if (file.exists() &&
                                        file.isFile() &&
                                        file.canRead()) {

                                files.add(file);
                        }
                }

                return files;
        }

        // =============================================================
        // OPEN FILE
        // =============================================================

        public boolean isDesktopOpenSupported() {
                return Desktop.isDesktopSupported();
        }

        /**
         * Opens the file with the operating system's default
         * application.
         */
        public void openFile(File file) throws IOException {
                Desktop.getDesktop().open(file);
        }
}
