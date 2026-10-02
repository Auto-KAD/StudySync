/**
 * Represents visual content extracted from a document.
 */
public class DocumentImage {

    private final String source;
    private final int pageOrSlide;
    private final byte[] imageData;
    private final String imageFormat;

    private final int width;
    private final int height;

    public DocumentImage(
            String source,
            int pageOrSlide,
            byte[] imageData,
            String imageFormat,
            int width,
            int height
    ) {
        this.source = source;
        this.pageOrSlide = pageOrSlide;
        this.imageData = imageData;
        this.imageFormat = imageFormat;
        this.width = width;
        this.height = height;
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSizeInBytes() {
        if (imageData == null) {
            return 0;
        }

        return imageData.length;
    }

    public double getSizeInKilobytes() {
        return getSizeInBytes() / 1024.0;
    }

    @Override
    public String toString() {
        return "DocumentImage{" +
                "source='" + source + '\'' +
                ", pageOrSlide=" + pageOrSlide +
                ", imageFormat='" + imageFormat + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", size=" + getSizeInBytes() +
                " bytes" +
                '}';
    }
}
