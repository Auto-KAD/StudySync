import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import javax.imageio.ImageIO;

public class PDFProcessor implements DocumentProcessor {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    /**
     * Rendering DPI for visual pages.
     *
     * 120 DPI is a reasonable starting point for local
     * multimodal processing.
     */
    private static final float RENDER_DPI = 120f;

    /**
     * Prevent extremely large rendered images from being
     * generated accidentally.
     */
    private static final int MAX_IMAGE_WIDTH = 2400;
    private static final int MAX_IMAGE_HEIGHT = 2400;

    // =========================================================
    // FILE SUPPORT
    // =========================================================

    @Override
    public boolean supports(File file) {

        if (file == null || !file.isFile()) {
            return false;
        }

        return file.getName()
                .toLowerCase()
                .endsWith(".pdf");
    }

    // =========================================================
    // PROCESS DOCUMENT
    // =========================================================

    @Override
    public DocumentModel process(File file)
            throws Exception {

        if (!supports(file)) {

            throw new IllegalArgumentException(
                    "Unsupported file for PDFProcessor: "
                            + file.getName());
        }

        DocumentModel model = new DocumentModel(
                file.getName(),
                "PDF",
                file.getAbsolutePath());

        try (
                PDDocument document = Loader.loadPDF(file)) {

            // =================================================
            // TEXT EXTRACTION
            // =================================================

            PDFTextStripper stripper = new PDFTextStripper();

            StringBuilder completeText = new StringBuilder();

            int pageCount = document.getNumberOfPages();

            // =================================================
            // VISUAL RENDERER
            // =================================================

            PDFRenderer renderer = new PDFRenderer(document);

            // =================================================
            // PROCESS EACH PAGE
            // =================================================

            for (int page = 1; page <= pageCount; page++) {

                // -------------------------------------------------
                // TEXT
                // -------------------------------------------------

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper
                        .getText(document)
                        .trim();

                model.addPage(pageText);

                if (!pageText.isBlank()) {

                    if (completeText.length() > 0) {
                        completeText.append("\n\n");
                    }

                    completeText
                            .append("[Page ")
                            .append(page)
                            .append("]\n");

                    completeText.append(pageText);
                }

                // -------------------------------------------------
                // VISUAL CONTENT
                // -------------------------------------------------

                PDPage pdfPage = document.getPage(page - 1);

                boolean hasImage = pageContainsImage(pdfPage);

                if (hasImage) {

                    System.out.println(
                            "Visual content detected on PDF page "
                                    + page);

                    BufferedImage renderedImage = renderPageImage(
                            renderer,
                            page - 1);

                    if (renderedImage != null) {

                        byte[] imageData = imageToBytes(
                                renderedImage);

                        DocumentImage image = new DocumentImage(
                                "PDF page",
                                page,
                                imageData,
                                "png",
                                renderedImage.getWidth(),
                                renderedImage.getHeight());

                        model.addImage(image);

                        System.out.println(
                                "  Rendered page "
                                        + page
                                        + " -> "
                                        + renderedImage.getWidth()
                                        + "x"
                                        + renderedImage.getHeight()
                                        + " px, "
                                        + imageData.length
                                        + " bytes");
                    }
                }
            }

            // =================================================
            // COMPLETE TEXT
            // =================================================

            model.setFullText(
                    completeText.toString());

            // =================================================
            // DOCUMENT TITLE
            // =================================================

            String title = document
                    .getDocumentInformation()
                    .getTitle();

            if (title != null &&
                    !title.isBlank()) {

                model.setTitle(title);
            }
        }

        return model;
    }

    // =========================================================
    // IMAGE DETECTION
    // =========================================================

    /**
     * Determines whether a PDF page contains an embedded
     * raster image.
     *
     * This is intentionally separate from rendering.
     *
     * A page containing only text will therefore not be
     * rendered unnecessarily.
     */
    private boolean pageContainsImage(
            PDPage page) {

        try {

            if (page.getResources() == null) {
                return false;
            }

            for (var name : page.getResources()
                    .getXObjectNames()) {

                PDXObject object = page.getResources()
                        .getXObject(name);

                if (object instanceof PDImageXObject) {
                    return true;
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Warning: Could not inspect PDF page "
                            + "for embedded images.");
        }

        return false;
    }

    // =========================================================
    // PAGE RENDERING
    // =========================================================

    /**
     * Renders a PDF page into a BufferedImage.
     *
     * The image is resized if necessary before being
     * converted to PNG bytes.
     */
    private BufferedImage renderPageImage(
            PDFRenderer renderer,
            int pageIndex) throws Exception {

        BufferedImage image = renderer.renderImageWithDPI(
                pageIndex,
                RENDER_DPI,
                ImageType.RGB);

        return resizeIfNecessary(image);
    }

    // =========================================================
    // IMAGE TO PNG
    // =========================================================

    /**
     * Converts a BufferedImage into PNG bytes.
     */
    private byte[] imageToBytes(
            BufferedImage image) throws Exception {

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ImageIO.write(
                image,
                "png",
                output);

        return output.toByteArray();
    }

    // =========================================================
    // IMAGE SIZE CONTROL
    // =========================================================

    /**
     * Reduces very large rendered pages while preserving
     * the aspect ratio.
     */
    private BufferedImage resizeIfNecessary(
            BufferedImage original) {

        int width = original.getWidth();

        int height = original.getHeight();

        if (width <= MAX_IMAGE_WIDTH &&
                height <= MAX_IMAGE_HEIGHT) {

            return original;
        }

        double widthScale = (double) MAX_IMAGE_WIDTH / width;

        double heightScale = (double) MAX_IMAGE_HEIGHT / height;

        double scale = Math.min(
                widthScale,
                heightScale);

        int newWidth = Math.max(
                1,
                (int) (width * scale));

        int newHeight = Math.max(
                1,
                (int) (height * scale));

        BufferedImage resized = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D graphics = resized.createGraphics();

        graphics.drawImage(
                original,
                0,
                0,
                newWidth,
                newHeight,
                null);

        graphics.dispose();

        return resized;
    }
}