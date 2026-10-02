import java.io.File;

public interface DocumentProcessor {

    /**
     * Checks whether this processor can handle the given file.
     *
     * @param file document file
     * @return true if this processor supports the file type
     */
    boolean supports(File file);

    /**
     * Processes the document and converts it into
     * the common StudySync document representation.
     *
     * @param file document to process
     * @return parsed DocumentModel
     * @throws Exception if the document cannot be processed
     */
    DocumentModel process(File file) throws Exception;
}