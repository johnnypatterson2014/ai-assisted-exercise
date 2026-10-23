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

import exercise.PatientConsolidation.RawRecord;
import exercise.PatientConsolidation.Result;

/**
 * Command-line entry point: reads a JSON array of demographic records (the format from the problem statement),
 * consolidates them and prints the patients and rejected rows as JSON. Unknown JSON fields such as "gender" are
 * ignored. The output contains names and contact details, so it is a development tool, not a log.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-patients.json"
 *   cat records.json | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 * In STS: right-click Runner, Run As, Java Application, with the file path under Run Configurations, Arguments.
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final TypeReference<List<RawRecord>> RECORDS = new TypeReference<>() {
    };

    private Runner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Runner <records.json | ->");
            System.exit(2);
        }
        List<String> lines = "-".equals(args[0])
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()
                : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        try {
            System.out.println(run(lines));
        } catch (JsonProcessingException e) {
            System.err.println("input is not a JSON array of records: " + e.getOriginalMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the lines as one JSON array of records, consolidates them and renders the {@link Result} as JSON.
     * Blank input means no records.
     */
    static String run(List<String> lines) throws IOException {
        String json = String.join("\n", lines).strip();
        List<RawRecord> records = json.isEmpty() ? List.of() : MAPPER.readValue(json, RECORDS);
        Result result = PatientConsolidation.consolidate(records);
        return MAPPER.writeValueAsString(result);
    }
}
