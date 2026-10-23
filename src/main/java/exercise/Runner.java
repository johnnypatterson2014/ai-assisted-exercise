package exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Command-line entry point for trying the coverage check against ad hoc roster files.
 *
 * Single-unit format, in the shape of the problem statement:
 * <pre>
 *   floors = 5                      # optional, default 5; blank lines and # comments are ignored
 *   monica: mon - fri: day
 *   jeff: mon, tue: day; fri, sat: night shift
 * </pre>
 * Nightly format, one section per time zone; units and staff lines belong to the section they are in:
 * <pre>
 *   zone = America/Vancouver
 *   unit van-general gen-1 = 2      # unit &lt;location&gt; &lt;unit-id&gt; = &lt;floors&gt;
 *   monica: mon - fri: day
 *   zone = America/Toronto
 *   unit tor-general t-1 = 3
 * </pre>
 * Days are any unambiguous prefix (mon, tues, thur, saturday); a range such as {@code mon-fri} expands in weekday
 * order and may wrap (sat-mon). The words {@code zone}, {@code unit} and {@code floors} are reserved at the start
 * of a line. Output is the {@link CoverageReport} (single-unit) or the list of {@link UnitReport} (nightly) as JSON.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-input.txt"
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-nightly.txt"
 *   echo "ann: mon: day" | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    static final int DEFAULT_FLOORS = 5;

    private static final Pattern ZONE_LINE = Pattern.compile("(?i)^zone\\s*[=:]\\s*(.*)$");
    private static final Pattern UNIT_LINE = Pattern.compile("(?i)^unit\\s+(\\S+)\\s+(\\S+)\\s*[=:]\\s*(.*)$");
    private static final Pattern FLOORS_LINE = Pattern.compile("(?i)^floors\\s*[=:]\\s*(.*)$");

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

    /** Parses roster lines, runs the single-unit or the nightly check, and renders the report as JSON. */
    static String run(List<String> lines) throws IOException {
        Parser parser = new Parser();
        int lineNumber = 0;
        for (String raw : lines) {
            parser.line(raw == null ? "" : raw.strip(), ++lineNumber);
        }
        if (parser.rostersByZone.isEmpty()) {
            return MAPPER.writeValueAsString(ShiftCoverage.check(parser.floors, parser.singleUnitRows));
        }
        return MAPPER.writeValueAsString(ShiftCoverage.checkAll(parser.units, parser.rostersByZone));
    }

    /** Line-by-line parser state: which section we are in and what has been collected so far. */
    private static final class Parser {
        int floors = DEFAULT_FLOORS;
        final List<Availability> singleUnitRows = new ArrayList<>();
        final List<Unit> units = new ArrayList<>();
        final Map<ZoneId, List<Availability>> rostersByZone = new LinkedHashMap<>();
        ZoneId currentZone;

        void line(String line, int lineNumber) {
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            var zone = ZONE_LINE.matcher(line);
            var unit = UNIT_LINE.matcher(line);
            var floorsLine = FLOORS_LINE.matcher(line);
            if (zone.matches()) {
                startZone(zone.group(1).strip(), lineNumber);
            } else if (unit.matches()) {
                addUnit(unit.group(1), unit.group(2), unit.group(3).strip(), lineNumber);
            } else if (floorsLine.matches()) {
                if (currentZone != null) {
                    throw error(lineNumber, "'floors' is for the single-unit format; use unit lines inside a zone");
                }
                floors = parseInt(floorsLine.group(1), "floors = <number>", lineNumber);
            } else {
                parseStaffLine(line, lineNumber, currentZone == null ? singleUnitRows : rostersByZone.get(currentZone));
            }
        }

        private void startZone(String id, int lineNumber) {
            if (!singleUnitRows.isEmpty()) {
                throw error(lineNumber, "zone sections must come before any staff line");
            }
            try {
                currentZone = ZoneId.of(id);
            } catch (DateTimeException e) {
                throw error(lineNumber, "unknown zone id '" + id + "'");
            }
            rostersByZone.computeIfAbsent(currentZone, z -> new ArrayList<>());
        }

        private void addUnit(String location, String id, String floorsText, int lineNumber) {
            if (currentZone == null) {
                throw error(lineNumber, "unit line before the first zone line");
            }
            int unitFloors = parseInt(floorsText, "unit <location> <unit-id> = <floors>", lineNumber);
            units.add(new Unit(id, new Location(location, currentZone), unitFloors));
        }
    }

    private static int parseInt(String text, String expected, int lineNumber) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw error(lineNumber, "expected '" + expected + "', got '" + text.strip() + "'");
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
