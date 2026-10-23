package exercise;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder solution that shows the conventions used in this project.
 * Replace it with the real exercise (or let the /coding-exercise skill create new classes beside it).
 *
 * Conventions:
 * - Validate inputs at the public boundary and fail fast with a clear message (IllegalArgumentException).
 * - Return a small immutable result type (a record) instead of printing.
 * - Log a one-line summary at INFO and details at DEBUG; never log sensitive data (patient identifiers, contact details).
 * - Keep the public method pure and easy to unit test; keep file and console handling in Runner.
 */
public final class Placeholder {

    private static final Logger LOG = LoggerFactory.getLogger(Placeholder.class);

    private Placeholder() {
    }

    /** Summary of a block of text: line counts and the longest line. */
    public record Summary(int totalLines, int blankLines, int longestLineLength) {
    }

    /**
     * Summarizes the given lines.
     *
     * @param lines the input lines; must not be null, may be empty; null elements count as blank lines
     * @return the summary
     * @throws IllegalArgumentException if {@code lines} is null
     */
    public static Summary summarize(List<String> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("lines must not be null");
        }
        int blank = 0;
        int longest = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                blank++;
                continue;
            }
            longest = Math.max(longest, line.length());
        }
        Summary summary = new Summary(lines.size(), blank, longest);
        LOG.info("summarized {} lines ({} blank, longest {})", summary.totalLines(), summary.blankLines(),
                summary.longestLineLength());
        return summary;
    }

    /** Small helper kept here to show how to treat optional strings consistently. */
    static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().replaceAll("\\s+", " ");
    }
}
