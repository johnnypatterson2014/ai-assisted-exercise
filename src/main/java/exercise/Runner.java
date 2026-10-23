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

import exercise.DoubleBookings.RawAppointment;
import exercise.DoubleBookings.Result;

/**
 * Command-line entry point: reads a JSON array of appointments (the format from the problem statement), finds
 * the double bookings and prints conflicts and rejected rows as JSON. Unknown JSON fields such as "room" are
 * ignored.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-appointments.json"
 *   cat appointments.json | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 * In STS: right-click Runner, Run As, Java Application, with the file path under Run Configurations, Arguments.
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final TypeReference<List<RawAppointment>> APPOINTMENTS = new TypeReference<>() {
    };

    private Runner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Runner <appointments.json | ->");
            System.exit(2);
        }
        List<String> lines = "-".equals(args[0])
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()
                : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        try {
            System.out.println(run(lines));
        } catch (JsonProcessingException e) {
            System.err.println("input is not a JSON array of appointments: " + e.getOriginalMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the lines as one JSON array of appointments, finds the double bookings and renders the
     * {@link Result} as JSON. Blank input means no appointments.
     */
    static String run(List<String> lines) throws IOException {
        String json = String.join("\n", lines).strip();
        List<RawAppointment> appointments = json.isEmpty() ? List.of() : MAPPER.readValue(json, APPOINTMENTS);
        Result result = DoubleBookings.findDoubleBookings(appointments);
        return MAPPER.writeValueAsString(result);
    }
}
