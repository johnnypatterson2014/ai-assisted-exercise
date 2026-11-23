package exercise;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Normalizes log lines from several microservices into one shape: time, user, message.
 * Our own logging carries positions and reasons only, never a user, a message or a raw line.
 */
public final class LogNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(LogNormalizer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Field names per service, in order of precedence. A new service is one more alias.
    private static final List<String> TIME_FIELDS = List.of("timestamp", "time", "ts");
    private static final List<String> USER_FIELDS = List.of("user", "username");
    private static final List<String> MESSAGE_FIELDS = List.of("msg", "message", "log");

    // Where the user sits inside the text when there is no user field.
    private static final List<Pattern> USER_IN_TEXT = List.of(
            Pattern.compile("LOGIN\\s+(\\S+)"),
            Pattern.compile("User\\s+(\\S+)\\s+logged in"));

    private static final DateTimeFormatter ZONELESS = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private static final Comparator<Entry> BY_TIME_BLANK_LAST =
            Comparator.comparing(Entry::instant, Comparator.nullsLast(Comparator.naturalOrder()));

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

    /** What one line offered before validation; a field is null when it is absent, null or blank. */
    private record Fields(JsonNode time, String user, String message) {
    }

    /** A normalized log with its parsed instant (null when unparsable), kept for sorting. */
    private record Entry(Instant instant, NormalizedLog log) {
    }

    /**
     * Normalizes raw log lines into time, user and message, ordered by time ascending.
     *
     * <p>Time and user are mandatory: a line without either is rejected with a reason and the batch continues.
     * A time that is present but cannot be parsed keeps the entry with a blank time, sorted after all others.
     * Entries with equal times keep their input order. The input list is not modified and nothing is thrown
     * for bad input.
     *
     * @param logEntries raw lines, JSON objects or plain text; null is treated as an empty list
     * @return the normalized logs and the rejected lines, both immutable
     */
    public static Result normalize(List<String> logEntries) {
        List<String> lines = logEntries == null ? List.of() : logEntries;
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        int unparsableTimes = 0;

        int index = -1;
        for (String line : lines) {
            index++;
            Fields fields = readFields(line);
            boolean hasTime = fields.time() != null;
            boolean hasUser = fields.user() != null;
            if (!hasTime || !hasUser) {
                String reason = hasTime ? "missing user" : hasUser ? "missing time" : "missing time and user";
                rejected.add(new Rejection(index, reason));
                LOG.warn("rejected line at index {}: {}", index, reason);
                continue;
            }
            Instant instant = parseTime(fields.time());
            if (instant == null) {
                unparsableTimes++;
                LOG.warn("line at index {} has an unparsable time, kept with a blank time", index);
            }
            String time = instant == null ? "" : instant.toString();
            entries.add(new Entry(instant, new NormalizedLog(time, fields.user(), fields.message())));
        }

        entries.sort(BY_TIME_BLANK_LAST);
        LOG.info("normalized {} of {} lines ({} rejected, {} with an unparsable time)", entries.size(),
                lines.size(), rejected.size(), unparsableTimes);
        return new Result(entries.stream().map(Entry::log).toList(), List.copyOf(rejected));
    }

    /**
     * The signature the exercise started from: the normalized logs rendered as a JSON array.
     *
     * @param logEntries raw lines; null is treated as an empty list
     * @return a JSON array of objects with {@code time}, {@code user} and {@code message}
     */
    public static String normalizeToJson(List<String> logEntries) {
        try {
            return MAPPER.writeValueAsString(normalize(logEntries).logs());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not render the normalized logs as JSON", e);
        }
    }

    private static Fields readFields(String line) {
        if (line == null || line.isBlank()) {
            return new Fields(null, null, null);
        }
        JsonNode object = parseObject(line);
        if (object == null) {
            String text = line.strip();
            return new Fields(null, userFromText(text), text);
        }
        String message = text(first(object, MESSAGE_FIELDS));
        String user = text(first(object, USER_FIELDS));
        if (user == null) {
            user = userFromText(message);
        }
        return new Fields(first(object, TIME_FIELDS), user, message);
    }

    /** Returns the line as a JSON object, or null when it is plain text, an array or a scalar. */
    private static JsonNode parseObject(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    /** Returns the first alias that has a value, or null; absent, null and blank all count as no value. */
    private static JsonNode first(JsonNode object, List<String> names) {
        for (String name : names) {
            JsonNode value = object.get(name);
            if (value != null && value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode value) {
        return value == null ? null : value.asText().strip();
    }

    private static String userFromText(String text) {
        if (text == null) {
            return null;
        }
        for (Pattern pattern : USER_IN_TEXT) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /** Returns the instant, or null when the value cannot be read as a time. */
    private static Instant parseTime(JsonNode value) {
        try {
            if (value.isIntegralNumber() && value.canConvertToLong()) {
                return Instant.ofEpochSecond(value.longValue());
            }
            if (value.isTextual()) {
                return parseTimeText(value.asText().strip());
            }
        } catch (DateTimeException unparsable) {
            // falls through: the caller keeps the entry with a blank time
        }
        return null;
    }

    private static Instant parseTimeText(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException notIso) {
            return LocalDateTime.parse(text, ZONELESS).toInstant(ZoneOffset.UTC);
        }
    }
}
