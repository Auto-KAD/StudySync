import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.File;
import java.io.FileInputStream;

public class PPTXProcessor implements DocumentProcessor {

    @Override
    public boolean supports(File file) {

        if (file == null || !file.isFile()) {
            return false;
        }

        return file.getName()
                .toLowerCase()
                .endsWith(".pptx");
    }

    @Override
    public DocumentModel process(File file) throws Exception {

        if (!supports(file)) {
            throw new IllegalArgumentException(
                    "Unsupported file for PPTXProcessor: "
                            + file.getName());
        }

        DocumentModel model = new DocumentModel(
                file.getName(),
                "PPTX",
                file.getAbsolutePath());

        try (
                FileInputStream inputStream = new FileInputStream(file);

                XMLSlideShow presentation = new XMLSlideShow(inputStream)) {

            StringBuilder fullText = new StringBuilder();

            int slideNumber = 0;

            for (XSLFSlide slide : presentation.getSlides()) {

                slideNumber++;

                StringBuilder slideText = new StringBuilder();

                slideText
                        .append("[Slide ")
                        .append(slideNumber)
                        .append("]\n");

                for (XSLFShape shape : slide.getShapes()) {

                    // -----------------------------------------
                    // Text
                    // -----------------------------------------

                    if (shape instanceof XSLFTextShape textShape) {

                        String text = textShape.getText();

                        if (text != null &&
                                !text.isBlank()) {

                            slideText
                                    .append(text.trim())
                                    .append("\n");
                        }
                    }

                    // -----------------------------------------
                    // Tables
                    // -----------------------------------------

                    if (shape instanceof XSLFTable table) {

                        String tableText = extractTable(table);

                        if (!tableText.isBlank()) {

                            model.addTable(
                                    "Slide "
                                            + slideNumber
                                            + "\n"
                                            + tableText);

                            slideText
                                    .append("\n")
                                    .append(tableText)
                                    .append("\n");
                        }
                    }
                }

                String result = slideText
                        .toString()
                        .trim();

                model.addSection(result);

                if (fullText.length() > 0) {
                    fullText.append("\n\n");
                }

                fullText.append(result);
            }

            model.setFullText(
                    fullText.toString());

            if (!model.getSections().isEmpty()) {

                String firstSection = model.getSections().get(0);

                String[] lines = firstSection.split("\\R");

                if (lines.length > 1 &&
                        !lines[1].isBlank()) {

                    model.setTitle(
                            lines[1].trim());
                }
            }
        }

        return model;
    }

    private String extractTable(XSLFTable table) {

        StringBuilder result = new StringBuilder();

        for (XSLFTableRow row : table.getRows()) {

            boolean firstCell = true;

            for (XSLFTableCell cell : row.getCells()) {

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
