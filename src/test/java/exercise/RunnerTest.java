package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class RunnerTest {

    @Test
    void runsSampleCase0InTheChallengeFormat() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/sample-case-0.txt"),
                StandardCharsets.UTF_8);

        assertThat(Runner.run(lines)).isEqualTo("4\n5");
    }

    @Test
    void toleratesWindowsLineEndingsBlankLinesAndPadding() {
        List<String> lines = List.of("5\r", " 4 ", "2\r", "", "5", "1", "6", "", "  ");

        assertThat(Runner.run(lines)).isEqualTo("5\n6");
    }

    @Test
    void countThatDoesNotMatchTheWeightsIsAnError() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Runner.run(List.of("3", "1", "2")))
                .withMessageContaining("3");
    }

    @Test
    void nonNumericLineIsAnError() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Runner.run(List.of("2", "1", "two")))
                .withMessageContaining("two");
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(Runner.run(List.of())).isEmpty();
    }
}
