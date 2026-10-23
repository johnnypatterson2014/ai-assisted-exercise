package exercise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command-line entry point in the challenge's custom-testing format: the first line is {@code n}, then one
 * weight per line. Prints subset A one weight per line. Blank lines, surrounding spaces and Windows line
 * endings are tolerated.
 *
 * Usage (Maven supplies the dependency classpath):
 * <pre>
 *   mvn -q compile exec:java -Dexec.args="src/test/resources/sample-case-0.txt"
 *   printf "5\n4\n2\n5\n1\n6\n" | mvn -q compile exec:java -Dexec.args="-"
 * </pre>
 * In STS: right-click Runner, Run As, Java Application, with the file path under Run Configurations, Arguments.
 */
public final class Runner {

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
        try {
            System.out.println(run(lines));
        } catch (IllegalArgumentException e) {
            System.err.println("bad input: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the challenge format (count, then that many weights, one per line) and renders subset A one
     * weight per line. Empty input gives an empty string.
     *
     * @throws IllegalArgumentException if a line is not an integer or the count does not match the weights
     */
    static String run(List<String> lines) {
        List<String> tokens = lines.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (tokens.isEmpty()) {
            return "";
        }
        int count = parseInt(tokens.get(0), 1);
        if (tokens.size() - 1 != count) {
            throw new IllegalArgumentException(
                    "expected " + count + " weights after the count, got " + (tokens.size() - 1));
        }
        List<Integer> weights = new ArrayList<>(count);
        for (int i = 1; i < tokens.size(); i++) {
            weights.add(parseInt(tokens.get(i), i + 1));
        }
        return BoxWeights.minimalHeaviestSetA(weights).stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
    }

    private static int parseInt(String token, int line) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + line + " is not an integer: " + token);
        }
    }
}
