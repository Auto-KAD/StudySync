import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DocumentProcessorFactory {

    private final List<DocumentProcessor> processors;

    public DocumentProcessorFactory() {

        processors = Arrays.asList(
                new PDFProcessor(),
                new PPTXProcessor(),
                new DOCXProcessor());
    }

    /**
     * Finds the appropriate processor for the supplied file.
     *
     * @param file document file
     * @return matching processor
     * @throws IllegalArgumentException if the file type is unsupported
     */
    public DocumentProcessor getProcessor(File file) {

        if (file == null) {
            throw new IllegalArgumentException(
                    "Document file cannot be null.");
        }

        for (DocumentProcessor processor : processors) {

            if (processor.supports(file)) {
                return processor;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported document type: "
                        + file.getName());
    }

    /**
     * Checks whether StudySync can process the file.
     */
    public boolean supports(File file) {

        if (file == null) {
            return false;
        }

        for (DocumentProcessor processor : processors) {

            if (processor.supports(file)) {
                return true;
            }
        }

        return false;
    }
}