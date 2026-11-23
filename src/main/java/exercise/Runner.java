package exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Command-line entry point for trying the solution against ad hoc inputs during the exercise
 * ("run your solution against modified inputs"), without writing a new unit test each time.
 *
 * Usage (Maven supplies the dependency classpath; a plain {@code java -cp target/classes} would miss Jackson
 * and SLF4J):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-logs.txt"
 *   echo "one line" | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 * In STS: right-click Runner, Run As, Java Application, with the file path under Run Configurations, Arguments.
 *
 * Each input line is one raw log entry, a JSON object or plain text. The output is the normalized logs ordered
 * by time and the rejected lines (zero-based line position and reason), as JSON.
 */
public final class Runner {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Runner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Runner <input-file | ->");
            System.exit(2);
        }
        List<String> lines = "-".equals(args[0])
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()
                : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        System.out.println(run(lines));
    }

    /** Normalizes the raw log lines and renders the logs and the rejections as JSON. */
    static String run(List<String> lines) throws IOException {
        return MAPPER.writeValueAsString(LogNormalizer.normalize(lines));
    }
}
