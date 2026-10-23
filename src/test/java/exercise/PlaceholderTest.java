package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test layout used in the exercise: happy path, edge cases, invalid input, each in its own nested class so
 * the JUnit view in STS reads like a specification. Replace the cases when the real problem is known.
 */
class PlaceholderTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void summarizesMixedLines() {
            Placeholder.Summary summary = Placeholder.summarize(List.of("alpha", "", "gamma delta"));

            assertThat(summary.totalLines()).isEqualTo(3);
            assertThat(summary.blankLines()).isEqualTo(1);
            assertThat(summary.longestLineLength()).isEqualTo(11);
        }

        @ParameterizedTest(name = "\"{0}\" has length {1}")
        @CsvSource({"a,1", "abc,3", "'  padded  ',10"})
        void longestLineIsMeasuredAsGiven(String line, int expected) {
            assertThat(Placeholder.summarize(List.of(line)).longestLineLength()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        void emptyInputGivesZeroes() {
            assertThat(Placeholder.summarize(List.of())).isEqualTo(new Placeholder.Summary(0, 0, 0));
        }

        @Test
        void nullElementsCountAsBlank() {
            List<String> lines = Arrays.asList(null, "x", null);

            assertThat(Placeholder.summarize(lines)).isEqualTo(new Placeholder.Summary(3, 2, 1));
        }

        @Test
        void whitespaceOnlyLinesCountAsBlank() {
            assertThat(Placeholder.summarize(List.of("   ", "\t")).blankLines()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @Test
        void nullListIsRejectedWithAClearMessage() {
            assertThatThrownBy(() -> Placeholder.summarize(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }
}
