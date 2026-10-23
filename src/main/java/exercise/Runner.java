package exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import exercise.VitalAlerts.RawReading;
import exercise.VitalAlerts.Result;

/**
 * Command-line entry point: evaluates a JSON array of readings (the format from the problem statement) with the
 * default thresholds and prints alerts and rejected rows as JSON. Unknown JSON fields such as "unit" are ignored.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-readings.json"
 *   cat readings.json | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 * In STS: right-click Runner, Run As, Java Application, with the file path under Run Configurations, Arguments.
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final TypeReference<List<RawReading>> READINGS = new TypeReference<>() {
    };

    private Runner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Runner <readings.json | ->");
            System.exit(2);
        }
        List<String> lines = "-".equals(args[0])
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()
                : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        try {
            System.out.println(run(lines));
        } catch (JsonProcessingException e) {
            System.err.println("input is not a JSON array of readings: " + e.getOriginalMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the lines as one JSON array of readings, evaluates them with {@link Thresholds#defaults()} and
     * renders the {@link Result} as JSON. Blank input means no readings.
     */
    static String run(List<String> lines) throws IOException {
        String json = String.join("\n", lines).strip();
        List<RawReading> readings = json.isEmpty() ? List.of() : MAPPER.readValue(json, READINGS);
        Result result = VitalAlerts.evaluate(readings, Thresholds.defaults());
        return MAPPER.writeValueAsString(result);
    }
}
