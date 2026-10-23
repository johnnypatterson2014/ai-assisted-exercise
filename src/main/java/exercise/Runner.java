package exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Command-line entry point for trying the coverage check against ad hoc roster files.
 *
 * Input is one staff member per line, in the shape of the problem statement:
 * <pre>
 *   floors = 5                      # optional, default 5; blank lines and # comments are ignored
 *   monica: mon - fri: day
 *   jeff: mon, tue: day; fri, sat: night shift
 * </pre>
 * Days are any unambiguous prefix (mon, tues, thur, saturday); a range such as {@code mon-fri} expands in weekday
 * order and may wrap (sat-mon). Output is the {@link CoverageReport} as JSON.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-input.txt"
 *   echo "ann: mon: day" | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    static final int DEFAULT_FLOORS = 5;

    private Runner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Runner <roster-file | ->");
            System.exit(2);
        }
        List<String> lines = "-".equals(args[0])
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()
                : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        System.out.println(run(lines));
    }

    /** Parses roster lines, runs the coverage check and renders the report as JSON. */
    static String run(List<String> lines) throws IOException {
        int floors = DEFAULT_FLOORS;
        List<Availability> rows = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("floors")) {
                floors = parseFloors(line, lineNumber);
            } else {
                parseStaffLine(line, lineNumber, rows);
            }
        }
        return MAPPER.writeValueAsString(ShiftCoverage.check(floors, rows));
    }

    private static int parseFloors(String line, int lineNumber) {
        String[] parts = line.split("[=:]", 2);
        try {
            return Integer.parseInt(parts[1].strip());
        } catch (RuntimeException e) {
            throw error(lineNumber, "expected 'floors = <number>', got '" + line + "'");
        }
    }

    /** {@code name: days: shift; days: shift} */
    private static void parseStaffLine(String line, int lineNumber, List<Availability> out) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw error(lineNumber, "expected 'name: days: shift', got '" + line + "'");
        }
        String name = line.substring(0, colon).strip();
        for (String group : line.substring(colon + 1).split(";")) {
            int split = group.lastIndexOf(':');
            if (split < 0) {
                throw error(lineNumber, "expected 'days: shift' in '" + group.strip() + "'");
            }
            Shift shift = parseShift(group.substring(split + 1), lineNumber);
            for (String token : group.substring(0, split).split(",")) {
                for (DayOfWeek day : parseDays(token, lineNumber)) {
                    out.add(new Availability(name, day, shift));
                }
            }
        }
    }

    private static Shift parseShift(String text, int lineNumber) {
        String word = text.strip().toLowerCase(Locale.ROOT).replaceFirst("\\s*shift$", "");
        return switch (word) {
            case "day" -> Shift.DAY;
            case "night" -> Shift.NIGHT;
            default -> throw error(lineNumber, "unknown shift '" + text.strip() + "', expected day or night");
        };
    }

    private static List<DayOfWeek> parseDays(String token, int lineNumber) {
        String[] ends = token.strip().split("\\s*-\\s*", -1);
        if (ends.length > 2) {
            throw error(lineNumber, "unknown day '" + token.strip() + "'");
        }
        DayOfWeek first = parseDay(ends[0], lineNumber);
        DayOfWeek last = ends.length == 2 ? parseDay(ends[1], lineNumber) : first;
        List<DayOfWeek> days = new ArrayList<>();
        for (DayOfWeek day = first; ; day = day.plus(1)) {
            days.add(day);
            if (day == last) {
                return days;
            }
        }
    }

    private static DayOfWeek parseDay(String text, int lineNumber) {
        String word = text.strip().toLowerCase(Locale.ROOT);
        if (word.length() >= 3) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day.name().toLowerCase(Locale.ROOT).startsWith(word)) {
                    return day;
                }
            }
        }
        throw error(lineNumber, "unknown day '" + text.strip() + "', expected mon..sun");
    }

    private static IllegalArgumentException error(int lineNumber, String message) {
        return new IllegalArgumentException("line " + lineNumber + ": " + message);
    }
}
