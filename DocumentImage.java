/**
 * Represents visual content extracted from a document.
 *
 * A DocumentImage can represent:
 * - an embedded image
 * - a rendered PDF page
 * - a rendered presentation slide
 * - another visual element extracted from a document
 */
public class DocumentImage {

    private final String source;
    private final int pageOrSlide;
    private final byte[] imageData;
    private final String imageFormat;

    public DocumentImage(
            String source,
            int pageOrSlide,
            byte[] imageData,
            String imageFormat) {

        this.source = source;
        this.pageOrSlide = pageOrSlide;
        this.imageData = imageData;
        this.imageFormat = imageFormat;
    }

    public String getSource() {
        return source;
    }

    public int getPageOrSlide() {
        return pageOrSlide;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public String getImageFormat() {
        return imageFormat;
    }

    public int getSizeInBytes() {

        if (imageData == null) {
            return 0;
        }

        return imageData.length;
    }

    @Override
    public String toString() {

        return "DocumentImage{" +
                "source='" + source + '\'' +
                ", pageOrSlide=" + pageOrSlide +
                ", imageFormat='" + imageFormat + '\'' +
                ", size=" + getSizeInBytes() +
                " bytes" +
                '}';
    }
}