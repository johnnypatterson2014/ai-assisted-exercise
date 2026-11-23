package exercise;

import java.util.List;

/**
 * Normalizes log lines from several microservices into one shape: time, user, message.
 * Phase 2 stub: the types and signatures are final, the logic is not written yet.
 */
public final class LogNormalizer {

    private LogNormalizer() {
    }

    /** One normalized entry. {@code time} is ISO-8601 UTC, or blank when the source value could not be parsed. */
    public record NormalizedLog(String time, String user, String message) {
    }

    /** A line that was not normalized: its position in the input and why. Never carries the raw line. */
    public record Rejection(int index, String reason) {
    }

    /** Normalized logs ordered by time, plus the lines that were rejected. */
    public record Result(List<NormalizedLog> logs, List<Rejection> rejected) {
    }

    public static Result normalize(List<String> logEntries) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The signature the exercise started from: the normalized logs rendered as a JSON array. */
    public static String normalizeToJson(List<String> logEntries) {
        throw new UnsupportedOperationException("not implemented");
    }
}
