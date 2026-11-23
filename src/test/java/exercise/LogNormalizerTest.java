package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import exercise.LogNormalizer.NormalizedLog;
import exercise.LogNormalizer.Rejection;
import exercise.LogNormalizer.Result;

class LogNormalizerTest {

    private static final String SERVICE_A =
            "{\"timestamp\": \"2025-08-05T12:00:00Z\", \"msg\": \"User login\", \"user\": \"alice\"}";
    private static final String SERVICE_B =
            "{\"time\": \"2025-08-05 12:00:01\", \"message\": \"User logged in\", \"username\": \"bob\"}";
    private static final String SERVICE_C = "{\"ts\": 1691236800, \"log\": \"LOGIN carol\"}";

    private static Result normalize(String... lines) {
        return LogNormalizer.normalize(Arrays.asList(lines));
    }

    /** A Service A line with the given timestamp and user. */
    private static String line(String timestamp, String user) {
        return "{\"timestamp\": \"%s\", \"msg\": \"event\", \"user\": \"%s\"}".formatted(timestamp, user);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void serviceAFieldsMapToTimeUserAndMessage() {
            assertThat(normalize(SERVICE_A).logs())
                    .containsExactly(new NormalizedLog("2025-08-05T12:00:00Z", "alice", "User login"));
        }

        @Test
        void serviceBTimeWithoutAZoneIsReadAsUtc() {
            assertThat(normalize(SERVICE_B).logs())
                    .containsExactly(new NormalizedLog("2025-08-05T12:00:01Z", "bob", "User logged in"));
        }

        @Test
        void serviceCEpochSecondsBecomeUtcAndTheUserComesFromTheText() {
            assertThat(normalize(SERVICE_C).logs())
                    .containsExactly(new NormalizedLog("2023-08-05T12:00:00Z", "carol", "LOGIN carol"));
        }

        @ParameterizedTest(name = "\"{0}\" gives user {1}")
        @CsvSource({"LOGIN carol,carol", "User John logged in,John"})
        void userIsExtractedFromTheTextWhenThereIsNoUserField(String text, String expectedUser) {
            String line = "{\"ts\": 1691236800, \"log\": \"%s\"}".formatted(text);

            assertThat(normalize(line).logs()).extracting(NormalizedLog::user).containsExactly(expectedUser);
        }

        @Test
        void theFiveSampleLinesGiveThreeLogsOrderedByTimeAndTwoRejections() {
            Result result = normalize(SERVICE_A, SERVICE_B, SERVICE_C, "User John logged in",
                    "{\"log\": \"LOGIN dan\"}");

            assertThat(result.logs()).extracting(NormalizedLog::user).containsExactly("carol", "alice", "bob");
            assertThat(result.rejected()).extracting(Rejection::index).containsExactly(3, 4);
        }

        @Test
        void jsonOutputIsAnArrayOfTimeUserAndMessage() throws Exception {
            JsonNode array = new ObjectMapper().readTree(LogNormalizer.normalizeToJson(List.of(SERVICE_A)));

            assertThat(array.isArray()).isTrue();
            assertThat(array.size()).isEqualTo(1);
            assertThat(array.get(0).get("time").asText()).isEqualTo("2025-08-05T12:00:00Z");
            assertThat(array.get(0).get("user").asText()).isEqualTo("alice");
            assertThat(array.get(0).get("message").asText()).isEqualTo("User login");
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        void nullListGivesAnEmptyResult() {
            Result result = LogNormalizer.normalize(null);

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void emptyListGivesAnEmptyResult() {
            Result result = LogNormalizer.normalize(List.of());

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void missingMessageIsNull() {
            Result result = normalize("{\"timestamp\": \"2025-08-05T12:00:00Z\", \"user\": \"alice\"}");

            assertThat(result.logs()).containsExactly(new NormalizedLog("2025-08-05T12:00:00Z", "alice", null));
        }

        @Test
        void unparsableTimeKeepsTheEntryWithABlankTime() {
            Result result = normalize(line("not a date", "alice"));

            assertThat(result.logs()).containsExactly(new NormalizedLog("", "alice", "event"));
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void blankTimesSortAfterEveryParsedTime() {
            Result result = normalize(line("not a date", "late"), line("2025-08-05T12:00:00Z", "alice"));

            assertThat(result.logs()).extracting(NormalizedLog::user).containsExactly("alice", "late");
        }

        @Test
        void equalTimesKeepInputOrder() {
            Result result = normalize(line("2025-08-05T12:00:00Z", "zoe"), line("2025-08-05T12:00:00Z", "adam"));

            assertThat(result.logs()).extracting(NormalizedLog::user).containsExactly("zoe", "adam");
        }

        @Test
        void timeWithAnOffsetIsConvertedToUtc() {
            Result result = normalize(line("2025-08-05T14:00:00+02:00", "alice"));

            assertThat(result.logs()).extracting(NormalizedLog::time).containsExactly("2025-08-05T12:00:00Z");
        }

        @Test
        void numericUserIsKeptAsText() {
            Result result = normalize("{\"timestamp\": \"2025-08-05T12:00:00Z\", \"msg\": \"event\", \"user\": 42}");

            assertThat(result.logs()).extracting(NormalizedLog::user).containsExactly("42");
        }

        @Test
        void identicalLinesAreBothKept() {
            assertThat(normalize(SERVICE_A, SERVICE_A).logs()).hasSize(2);
        }

        @Test
        void unknownFieldsAreIgnored() {
            Result result = normalize("{\"timestamp\": \"2025-08-05T12:00:00Z\", \"msg\": \"event\","
                    + " \"user\": \"alice\", \"level\": \"INFO\", \"host\": \"node-1\"}");

            assertThat(result.logs()).containsExactly(new NormalizedLog("2025-08-05T12:00:00Z", "alice", "event"));
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @Test
        void plainTextWithoutATimeIsRejectedWithoutEchoingTheLine() {
            Result result = normalize("User John logged in");

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::index).containsExactly(0);
            assertThat(result.rejected().get(0).reason()).contains("time").doesNotContain("John");
        }

        @Test
        void jsonWithoutATimeIsRejected() {
            Result result = normalize("{\"log\": \"LOGIN dan\"}");

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::reason).singleElement().asString().contains("time");
        }

        @Test
        void jsonWithoutAUserIsRejected() {
            Result result = normalize("{\"timestamp\": \"2025-08-05T12:00:00Z\", \"msg\": \"disk full\"}");

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::reason).singleElement().asString().contains("user");
        }

        @Test
        void blankUserIsRejected() {
            Result result = normalize(line("2025-08-05T12:00:00Z", "   "));

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::reason).singleElement().asString().contains("user");
        }

        @ParameterizedTest(name = "[{0}] is rejected, nothing throws")
        @ValueSource(strings = {"[1, 2, 3]", "42", "\"just text\"", "null", "{not json", "   ", ""})
        void linesThatAreNotALogObjectAreRejectedWithoutThrowing(String line) {
            Result result = normalize(line);

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::index).containsExactly(0);
        }

        @Test
        void nullLineIsRejectedAndTheRestOfTheBatchContinues() {
            Result result = LogNormalizer.normalize(Arrays.asList(null, SERVICE_A));

            assertThat(result.logs()).extracting(NormalizedLog::user).containsExactly("alice");
            assertThat(result.rejected()).extracting(Rejection::index).containsExactly(0);
        }
    }

    @Nested
    @DisplayName("adversarial")
    class Adversarial {

        @Test
        void impossibleDateIsNotSilentlyCorrected() {
            Result result = normalize(
                    "{\"time\": \"2025-02-30 12:00:00\", \"message\": \"event\", \"username\": \"bob\"}");

            assertThat(result.logs()).containsExactly(new NormalizedLog("", "bob", "event"));
        }

        @Test
        void epochMillisecondsAreNotReadAsSeconds() {
            Result result = normalize("{\"ts\": 1691236800000, \"log\": \"LOGIN carol\"}");

            assertThat(result.logs()).extracting(NormalizedLog::time).containsExactly("2023-08-05T12:00:00Z");
        }

        @ParameterizedTest(name = "trailing [{0}] rejects the line")
        @ValueSource(strings = {"garbage", "{\"user\": \"mallory\"}"})
        void trailingContentAfterTheJsonObjectRejectsTheLine(String trailing) {
            Result result = normalize(SERVICE_A + " " + trailing);

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::index).containsExactly(0);
        }

        @Test
        void aValidTimeAliasIsUsedWhenAnEarlierOneIsUnparsable() {
            Result result = normalize(
                    "{\"timestamp\": \"not a date\", \"time\": \"2025-08-05 12:00:01\", \"user\": \"alice\"}");

            assertThat(result.logs()).extracting(NormalizedLog::time).containsExactly("2025-08-05T12:00:01Z");
        }

        @Test
        void timestampWinsWhenTwoTimeAliasesAreBothValid() {
            Result result = normalize(
                    "{\"time\": \"2025-08-05 12:00:01\", \"timestamp\": \"2024-01-01T00:00:00Z\", \"user\": \"alice\"}");

            assertThat(result.logs()).extracting(NormalizedLog::time).containsExactly("2024-01-01T00:00:00Z");
        }

        @ParameterizedTest(name = "ts {0} gives time [{1}]")
        @CsvSource({"0,1970-01-01T00:00:00Z", "99999999999999999999,''"})
        void epochBoundariesNeverThrow(String ts, String expectedTime) {
            Result result = normalize("{\"ts\": %s, \"log\": \"LOGIN carol\"}".formatted(ts));

            assertThat(result.logs()).extracting(NormalizedLog::time).containsExactly(expectedTime);
        }

        @Test
        void resultIsImmutableAndTheInputIsLeftAsGiven() {
            List<String> input = new ArrayList<>(List.of(SERVICE_B, SERVICE_A));

            Result result = LogNormalizer.normalize(input);

            assertThat(input).containsExactly(SERVICE_B, SERVICE_A);
            assertThatThrownBy(() -> result.logs().add(new NormalizedLog("", "eve", null)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.rejected().add(new Rejection(9, "forged")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void nearMissOfATextPatternIsRejectedWithoutLeakingTheUser() {
            Result result = normalize("LOGIN carol from 10.0.0.1");

            assertThat(result.logs()).isEmpty();
            assertThat(result.rejected()).extracting(Rejection::reason).singleElement().asString()
                    .doesNotContain("carol");
        }
    }
}
