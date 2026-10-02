import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AIRequest {

    private final String question;
    private final String context;
    private final List<DocumentImage> images;

    public AIRequest(
            String question,
            String context) {

        this(
                question,
                context,
                Collections.emptyList());
    }

    public AIRequest(
            String question,
            String context,
            List<DocumentImage> images) {

        this.question = question;
        this.context = context;

        if (images == null) {

            this.images = Collections.emptyList();

        } else {

            this.images = Collections.unmodifiableList(
                    new ArrayList<>(images));
        }
    }

    public String getQuestion() {
        return question;
    }

    public String getContext() {
        return context;
    }

    public List<DocumentImage> getImages() {
        return images;
    }

    public int getImageCount() {
        return images.size();
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    /**
     * Returns at most maxImages visual elements.
     *
     * This prevents accidentally sending dozens of rendered
     * pages to the local multimodal model.
     */
    public List<DocumentImage> getImages(
            int maxImages) {

        if (maxImages <= 0 ||
                images.isEmpty()) {

            return Collections.emptyList();
        }

        int limit = Math.min(
                maxImages,
                images.size());

        return Collections.unmodifiableList(
                new ArrayList<>(
                        images.subList(
                                0,
                                limit)));
    }
}