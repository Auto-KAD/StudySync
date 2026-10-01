import java.io.File;

public class ResourceCluster {

    public enum Category {
        LAB,
        LECTURE_SLIDES,
        SYLLABUS_POLICY,
        BOOKS,
        PREVIOUS_YEAR_PAPERS,
        MISCELLANEOUS
    }

    public static Category classify(File file) {

        String name = file.getName().toLowerCase();
        String extension = getExtension(name);

        // ---------- LAB ----------
        if (containsAny(name,
                "lab",
                "laboratory",
                "experiment",
                "exp",
                "practical",
                "assignment lab")) {

            return Category.LAB;
        }

        // ---------- PREVIOUS YEAR PAPERS ----------
        if (containsAny(name,
                "question paper",
                "questionpaper",
                "previous year",
                "previousyear",
                "pyq",
                "end term",
                "endterm",
                "mid term",
                "midterm",
                "mid-sem",
                "midsem",
                "exam paper",
                "university paper",
                "question bank")) {

            return Category.PREVIOUS_YEAR_PAPERS;
        }

        if (containsYear(name) &&
                containsAny(name,
                        "paper",
                        "exam",
                        "question",
                        "term",
                        "mid",
                        "end")) {

            return Category.PREVIOUS_YEAR_PAPERS;
        }

        // ---------- LECTURE SLIDES ----------
        if (extension.equals("ppt") ||
                extension.equals("pptx")) {

            return Category.LECTURE_SLIDES;
        }

        if (containsAny(name,
                "lecture",
                "slides",
                "slide",
                "presentation",
                "unit",
                "module",
                "chapter")) {

            return Category.LECTURE_SLIDES;
        }

        // ---------- SYLLABUS / COURSE POLICY ----------
        if (containsAny(name,
                "syllabus",
                "course policy",
                "course_policy",
                "coursepolicy",
                "course outline",
                "course_outline",
                "curriculum",
                "academic policy",
                "scheme",
                "course structure")) {

            return Category.SYLLABUS_POLICY;
        }

        // ---------- BOOKS ----------
        if (containsAny(name,
                "book",
                "textbook",
                "text book",
                "edition",
                "reference",
                "gonzales",
                "pressman",
                "silberschatz",
                "tanenbaum",
                "clrs")) {

            return Category.BOOKS;
        }

        return Category.MISCELLANEOUS;
    }

    private static String getExtension(
            String fileName
    ) {

        int dot =
                fileName.lastIndexOf('.');

        if (dot == -1) {
            return "";
        }

        return fileName.substring(
                dot + 1
        );
    }

    private static boolean containsAny(
            String text,
            String... keywords
    ) {

        for (String keyword : keywords) {

            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsYear(
            String text
    ) {

        for (int year = 2018;
             year <= 2035;
             year++) {

            if (text.contains(
                    String.valueOf(year)
            )) {
                return true;
            }
        }

        return false;
    }
}
