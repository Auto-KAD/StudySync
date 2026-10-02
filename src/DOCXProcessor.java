import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.File;
import java.io.FileInputStream;

public class DOCXProcessor implements DocumentProcessor {

    @Override
    public boolean supports(File file) {

        if (file == null || !file.isFile()) {
            return false;
        }

        return file.getName()
                .toLowerCase()
                .endsWith(".docx");
    }

    @Override
    public DocumentModel process(File file) throws Exception {

        if (!supports(file)) {
            throw new IllegalArgumentException(
                    "Unsupported file for DOCXProcessor: "
                            + file.getName());
        }

        DocumentModel model = new DocumentModel(
                file.getName(),
                "DOCX",
                file.getAbsolutePath());

        try (
                FileInputStream inputStream = new FileInputStream(file);

                XWPFDocument document = new XWPFDocument(inputStream)) {

            StringBuilder fullText = new StringBuilder();

            // -------------------------------------------------
            // Paragraphs
            // -------------------------------------------------

            for (XWPFParagraph paragraph : document.getParagraphs()) {

                String text = paragraph.getText();

                if (text == null ||
                        text.isBlank()) {
                    continue;
                }

                String cleaned = text.trim();

                model.addSection(cleaned);

                if (fullText.length() > 0) {
                    fullText.append("\n");
                }

                fullText.append(cleaned);
            }

            // -------------------------------------------------
            // Tables
            // -------------------------------------------------

            for (XWPFTable table : document.getTables()) {

                String tableText = extractTable(table);

                if (!tableText.isBlank()) {

                    model.addTable(tableText);

                    if (fullText.length() > 0) {
                        fullText.append("\n\n");
                    }

                    fullText
                            .append("[TABLE]\n")
                            .append(tableText);
                }
            }

            model.setFullText(
                    fullText.toString());

            // -------------------------------------------------
            // Basic title detection
            // -------------------------------------------------

            if (!model.getSections().isEmpty()) {

                String firstSection = model.getSections().get(0);

                if (!firstSection.isBlank()) {

                    model.setTitle(
                            firstSection.trim());
                }
            }
        }

        return model;
    }

    private String extractTable(XWPFTable table) {

        StringBuilder result = new StringBuilder();

        for (XWPFTableRow row : table.getRows()) {

            boolean firstCell = true;

            for (XWPFTableCell cell : row.getTableCells()) {

                if (!firstCell) {
                    result.append(" | ");
                }

                String text = cell.getText();

                if (text != null) {
                    result.append(
                            text.trim());
                }

                firstCell = false;
            }

            result.append("\n");
        }

        return result.toString().trim();
    }
}