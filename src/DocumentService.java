import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DocumentService {

    private final DocumentProcessorFactory processorFactory;

    public DocumentService() {

        processorFactory = new DocumentProcessorFactory();
    }

    /**
     * Processes a single document.
     */
    public DocumentModel process(File file)
            throws Exception {

        validateFile(file);

        DocumentProcessor processor = processorFactory.getProcessor(file);

        return processor.process(file);
    }

    /**
     * Processes multiple documents.
     *
     * Documents are processed independently so that one
     * unsupported or corrupted document does not silently
     * disappear from the processing pipeline.
     */
    public List<DocumentModel> processFiles(
            List<File> files) throws Exception {

        if (files == null || files.isEmpty()) {

            throw new IllegalArgumentException(
                    "No documents were supplied.");
        }

        List<DocumentModel> documents = new ArrayList<>();

        for (File file : files) {

            validateFile(file);

            DocumentModel model = process(file);

            documents.add(model);
        }

        return documents;
    }

    /**
     * Determines whether the supplied file can be processed.
     */
    public boolean supports(File file) {

        return processorFactory.supports(file);
    }

    private void validateFile(File file)
            throws Exception {

        if (file == null) {

            throw new IllegalArgumentException(
                    "Document file cannot be null.");
        }

        if (!file.exists()) {

            throw new IllegalArgumentException(
                    "Document does not exist:\n"
                            + file.getAbsolutePath());
        }

        if (!file.isFile()) {

            throw new IllegalArgumentException(
                    "Path is not a file:\n"
                            + file.getAbsolutePath());
        }

        if (!file.canRead()) {

            throw new IllegalArgumentException(
                    "Document cannot be read:\n"
                            + file.getAbsolutePath());
        }

        if (!processorFactory.supports(file)) {

            throw new IllegalArgumentException(
                    "Unsupported document type:\n"
                            + file.getName());
        }
    }
}