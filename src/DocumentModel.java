import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DocumentModel {

    private final String fileName;
    private final String fileType;
    private final String absolutePath;

    private String title;
    private String fullText;

    private final List<String> pages;
    private final List<String> sections;
    private final List<String> tables;

    // ---------------------------------------------------------
    // Visual content
    // ---------------------------------------------------------

    private final List<DocumentImage> images;

    public DocumentModel(
            String fileName,
            String fileType,
            String absolutePath) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.absolutePath = absolutePath;

        this.title = "";
        this.fullText = "";

        this.pages = new ArrayList<>();
        this.sections = new ArrayList<>();
        this.tables = new ArrayList<>();

        this.images = new ArrayList<>();
    }

    // ---------------------------------------------------------
    // Basic metadata
    // ---------------------------------------------------------

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    // ---------------------------------------------------------
    // Title
    // ---------------------------------------------------------

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    // ---------------------------------------------------------
    // Full text
    // ---------------------------------------------------------

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText == null ? "" : fullText;
    }

    public void appendText(String text) {

        if (text == null || text.isBlank()) {
            return;
        }

        if (!fullText.isEmpty()) {
            fullText += "\n";
        }

        fullText += text;
    }

    // ---------------------------------------------------------
    // Pages
    // ---------------------------------------------------------

    public void addPage(String pageText) {

        if (pageText == null) {
            pageText = "";
        }

        pages.add(pageText);
    }

    public List<String> getPages() {
        return Collections.unmodifiableList(pages);
    }

    // ---------------------------------------------------------
    // Sections / slides
    // ---------------------------------------------------------

    public void addSection(String section) {

        if (section == null || section.isBlank()) {
            return;
        }

        sections.add(section);
    }

    public List<String> getSections() {
        return Collections.unmodifiableList(sections);
    }

    // ---------------------------------------------------------
    // Tables
    // ---------------------------------------------------------

    public void addTable(String table) {

        if (table == null || table.isBlank()) {
            return;
        }

        tables.add(table);
    }

    public List<String> getTables() {
        return Collections.unmodifiableList(tables);
    }

    // ---------------------------------------------------------
    // Visual content
    // ---------------------------------------------------------

    /**
     * Adds an extracted visual to this document.
     */
    public void addImage(DocumentImage image) {

        if (image == null) {
            return;
        }

        images.add(image);
    }

    /**
     * Returns all extracted visuals.
     *
     * The returned list cannot be modified directly.
     */
    public List<DocumentImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    // ---------------------------------------------------------
    // Statistics
    // ---------------------------------------------------------

    public int getPageCount() {
        return pages.size();
    }

    public int getSectionCount() {
        return sections.size();
    }

    public int getTableCount() {
        return tables.size();
    }

    public int getImageCount() {
        return images.size();
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
    // ---------------------------------------------------------
    // AI-friendly representation
    // ---------------------------------------------------------

    public String toAIContext() {

        StringBuilder context = new StringBuilder();

        context.append("DOCUMENT\n");
        context.append("========\n");

        context.append("File: ")
                .append(fileName)
                .append("\n");

        context.append("Type: ")
                .append(fileType)
                .append("\n");

        if (!title.isBlank()) {

            context.append("Title: ")
                    .append(title)
                    .append("\n");
        }

        context.append("\n");

        if (!fullText.isBlank()) {

            context.append("TEXT\n");
            context.append("----\n");
            context.append(fullText);
            context.append("\n\n");
        }

        if (!sections.isEmpty()) {

            context.append("SECTIONS\n");
            context.append("--------\n");

            for (int i = 0; i < sections.size(); i++) {

                context.append("\n[Section ")
                        .append(i + 1)
                        .append("]\n");

                context.append(
                        sections.get(i));

                context.append("\n");
            }

            context.append("\n");
        }

        if (!tables.isEmpty()) {

            context.append("TABLES\n");
            context.append("------\n");

            for (int i = 0; i < tables.size(); i++) {

                context.append("\n[Table ")
                        .append(i + 1)
                        .append("]\n");

                context.append(
                        tables.get(i));

                context.append("\n");
            }
        }

        // -----------------------------------------------------
        // Visual metadata
        //
        // We deliberately DO NOT put base64 image data into
        // toAIContext().
        //
        // Images will be sent separately through AIRequest
        // in the multimodal phase.
        // -----------------------------------------------------

        if (!images.isEmpty()) {

            context.append("\n");
            context.append("VISUAL CONTENT\n");
            context.append("--------------\n");

            for (int i = 0; i < images.size(); i++) {

                DocumentImage image = images.get(i);

                context.append("\n[Visual ")
                        .append(i + 1)
                        .append("]\n");

                context.append("Source: ")
                        .append(image.getSource())
                        .append("\n");

                if (image.getPageOrSlide() >= 0) {

                    context.append("Page/Slide: ")
                            .append(
                                    image.getPageOrSlide())
                            .append("\n");
                }

                context.append("Format: ")
                        .append(image.getImageFormat())
                        .append("\n");

                context.append("Size: ")
                        .append(
                                image.getSizeInBytes())
                        .append(" bytes\n");
            }
        }

        return context.toString();
    }

    // ---------------------------------------------------------
    // String representation
    // ---------------------------------------------------------

    @Override
    public String toString() {

        return "DocumentModel{" +
                "fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", pages=" + pages.size() +
                ", sections=" + sections.size() +
                ", tables=" + tables.size() +
                ", images=" + images.size() +
                '}';
    }
}