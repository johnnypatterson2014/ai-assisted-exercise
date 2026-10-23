package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import exercise.Thresholds.Threshold;
import exercise.VitalAlerts.Alert;
import exercise.VitalAlerts.RawReading;
import exercise.VitalAlerts.Result;
import exercise.VitalAlerts.Severity;

class VitalAlertsTest {

    private static final Thresholds DEFAULTS = Thresholds.defaults();

    private static RawReading reading(String patientId, String type, Object value, String timestamp) {
        return new RawReading(patientId, type, value, timestamp);
    }

    /** Builds readings for one patient and vital, one per minute from 10:00, in the order given. */
    private static List<RawReading> series(String patientId, String type, double... values) {
        List<RawReading> list = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            list.add(reading(patientId, type, values[i], "2026-10-23T10:%02d:00Z".formatted(i)));
        }
        return list;
    }

    private static Result evaluate(List<RawReading> readings) {
        return VitalAlerts.evaluate(readings, DEFAULTS);
    }

    @Nested
    class HappyPath {

        @Test
        void normalReadingsProduceNoAlerts() {
            Result result = evaluate(series("p-1", "HR", 70, 72, 75, 80));

            assertThat(result.alerts()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void singleDangerousReadingRaisesCritical() {
            Result result = evaluate(List.of(reading("p-1", "HR", 150, "2026-10-23T10:00:00Z")));

            assertThat(result.alerts()).singleElement().satisfies(alert -> {
                assertThat(alert.severity()).isEqualTo(Severity.CRITICAL);
                assertThat(alert.patientId()).isEqualTo("p-1");
                assertThat(alert.type()).isEqualTo(VitalType.HEART_RATE);
                assertThat(alert.at()).isEqualTo(Instant.parse("2026-10-23T10:00:00Z"));
                assertThat(alert.reason()).contains("150");
            });
        }

        @Test
        void threeAbnormalReadingsInARowRaiseOneWarningAtTheThird() {
            Result result = evaluate(series("p-1", "HR", 125, 128, 130));

            assertThat(result.alerts()).singleElement().satisfies(alert -> {
                assertThat(alert.severity()).isEqualTo(Severity.WARNING);
                assertThat(alert.patientId()).isEqualTo("p-1");
                assertThat(alert.type()).isEqualTo(VitalType.HEART_RATE);
                assertThat(alert.at()).isEqualTo(Instant.parse("2026-10-23T10:02:00Z"));
            });
        }

        @Test
        void readingsAreOrderedByTimestampNotArrivalBeforeCheckingStreaks() {
            List<RawReading> outOfOrder = List.of(
                    reading("p-1", "HR", 125, "2026-10-23T10:10:00Z"),
                    reading("p-1", "HR", 70, "2026-10-23T10:03:00Z"),
                    reading("p-1", "HR", 130, "2026-10-23T10:00:00Z"),
                    reading("p-1", "HR", 128, "2026-10-23T10:05:00Z"));

            // by time: 130 (10:00), 70 (10:03), 128 (10:05), 125 (10:10) -> streak broken, no warning
            assertThat(evaluate(outOfOrder).alerts()).isEmpty();
        }

        @Test
        void criticalReadingAlsoCountsTowardTheWarningStreak() {
            Result result = evaluate(series("p-1", "HR", 125, 150, 128));

            assertThat(result.alerts()).extracting(Alert::severity)
                    .containsExactly(Severity.CRITICAL, Severity.WARNING);
        }

        @Test
        void customThresholdsAreApplied() {
            Thresholds strict = DEFAULTS.with(VitalType.HEART_RATE, new Threshold(60, 100, 50, 110));

            Result result = VitalAlerts.evaluate(List.of(reading("p-1", "HR", 115, "2026-10-23T10:00:00Z")), strict);

            assertThat(result.alerts()).extracting(Alert::severity).containsExactly(Severity.CRITICAL);
        }

        @ParameterizedTest
        @CsvSource({
                "HR, HEART_RATE",
                "hr, HEART_RATE",
                "Heart-Rate, HEART_RATE",
                "heart_rate, HEART_RATE",
                "SPO2, SPO2",
                "SpO2, SPO2",
                "oxygen saturation, SPO2",
                "TEMP, TEMPERATURE",
                "Temperature, TEMPERATURE"
        })
        void vitalTypeLabelsAreNormalised(String label, VitalType expected) {
            assertThat(VitalType.parse(label)).contains(expected);
        }

        @Test
        void sampleInputFromTheStatement() {
            List<RawReading> sample = List.of(
                    reading("p-1", "HR", 125, "2026-10-23T10:10:00Z"),
                    reading("p-1", "hr", 130, "2026-10-23T10:00:00Z"),
                    reading("p-1", "Heart-Rate", 128, "2026-10-23T10:05:00Z"),
                    reading("p-2", "SPO2", 82, "2026-10-23T10:00:00Z"),
                    reading("p-2", "SPO2", "n/a", "2026-10-23T10:01:00Z"),
                    reading("p-3", "BP", 120, "2026-10-23T10:00:00Z"));

            Result result = evaluate(sample);

            assertThat(result.alerts()).extracting(Alert::patientId, Alert::severity).containsExactly(
                    tuple("p-1", Severity.WARNING),
                    tuple("p-2", Severity.CRITICAL));
            assertThat(result.rejected()).extracting(VitalAlerts.RejectedReading::index).containsExactly(4, 5);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyInputGivesNoAlertsAndNoRejects() {
            Result result = evaluate(List.of());

            assertThat(result.alerts()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(doubles = {50, 120})
        void valueExactlyOnTheAbnormalBoundaryIsNormal(double value) {
            assertThat(evaluate(series("p-1", "HR", value, value, value)).alerts()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(doubles = {40, 140})
        void valueExactlyOnTheCriticalBoundaryIsAbnormalButNotCritical(double value) {
            Result result = evaluate(List.of(reading("p-1", "HR", value, "2026-10-23T10:00:00Z")));

            assertThat(result.alerts()).isEmpty();
            assertThat(evaluate(series("p-1", "HR", value, value, value)).alerts())
                    .extracting(Alert::severity).containsExactly(Severity.WARNING);
        }

        @Test
        void normalReadingBreaksTheStreak() {
            assertThat(evaluate(series("p-1", "HR", 125, 128, 70, 130, 135)).alerts()).isEmpty();
        }

        @Test
        void longerStreakRaisesOnlyOneWarning() {
            Result result = evaluate(series("p-1", "HR", 125, 128, 130, 132, 135));

            assertThat(result.alerts()).extracting(Alert::severity).containsExactly(Severity.WARNING);
            assertThat(result.alerts().get(0).at()).isEqualTo(Instant.parse("2026-10-23T10:02:00Z"));
        }

        @Test
        void streaksAreTrackedPerPatientAndPerVital() {
            List<RawReading> mixed = new ArrayList<>();
            mixed.addAll(series("p-1", "HR", 125, 128));
            mixed.add(reading("p-1", "SPO2", 90, "2026-10-23T10:02:00Z"));
            mixed.add(reading("p-2", "HR", 130, "2026-10-23T10:02:00Z"));

            assertThat(evaluate(mixed).alerts()).isEmpty();
        }

        @Test
        void rejectedReadingDoesNotBreakAStreak() {
            List<RawReading> withGarbage = List.of(
                    reading("p-1", "HR", 125, "2026-10-23T10:00:00Z"),
                    reading("p-1", "HR", "n/a", "2026-10-23T10:01:00Z"),
                    reading("p-1", "HR", 128, "2026-10-23T10:02:00Z"),
                    reading("p-1", "HR", 130, "2026-10-23T10:03:00Z"));

            Result result = evaluate(withGarbage);

            assertThat(result.alerts()).extracting(Alert::severity).containsExactly(Severity.WARNING);
            assertThat(result.rejected()).hasSize(1);
        }

        @Test
        void duplicateReadingsCountSeparately() {
            RawReading same = reading("p-1", "HR", 125, "2026-10-23T10:00:00Z");

            assertThat(evaluate(List.of(same, same, same)).alerts())
                    .extracting(Alert::severity).containsExactly(Severity.WARNING);
        }

        @Test
        void alertsAreSortedByPatientThenVitalThenTime() {
            List<RawReading> input = List.of(
                    reading("p-2", "HR", 150, "2026-10-23T10:00:00Z"),
                    reading("p-1", "SPO2", 80, "2026-10-23T10:00:00Z"),
                    reading("p-1", "HR", 150, "2026-10-23T10:05:00Z"),
                    reading("p-1", "HR", 150, "2026-10-23T10:00:00Z"));

            assertThat(evaluate(input).alerts()).extracting(Alert::patientId, Alert::type, Alert::at).containsExactly(
                    tuple("p-1", VitalType.HEART_RATE, Instant.parse("2026-10-23T10:00:00Z")),
                    tuple("p-1", VitalType.HEART_RATE, Instant.parse("2026-10-23T10:05:00Z")),
                    tuple("p-1", VitalType.SPO2, Instant.parse("2026-10-23T10:00:00Z")),
                    tuple("p-2", VitalType.HEART_RATE, Instant.parse("2026-10-23T10:00:00Z")));
        }

        @Test
        void inputListIsNotMutated() {
            List<RawReading> input = new ArrayList<>(List.of(
                    reading("p-1", "HR", 125, "2026-10-23T10:10:00Z"),
                    reading("p-1", "HR", 130, "2026-10-23T10:00:00Z")));
            List<RawReading> copy = List.copyOf(input);

            evaluate(input);

            assertThat(input).containsExactlyElementsOf(copy);
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void nullReadingListIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> VitalAlerts.evaluate(null, DEFAULTS))
                    .withMessageContaining("readings");
        }

        @Test
        void nullThresholdsAreRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> VitalAlerts.evaluate(List.of(), null))
                    .withMessageContaining("thresholds");
        }

        @Test
        void nullElementIsRejectedWithItsIndexAndTheBatchContinues() {
            List<RawReading> input = Arrays.asList(
                    reading("p-1", "HR", 150, "2026-10-23T10:00:00Z"),
                    null,
                    reading("p-2", "HR", 150, "2026-10-23T10:00:00Z"));

            Result result = evaluate(input);

            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isEqualTo(1);
                assertThat(rejected.reason()).contains("null");
            });
            assertThat(result.alerts()).hasSize(2);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(nullValues = "NULL", value = {
                "blank patient id,   '  ',  HR,   120,       2026-10-23T10:00:00Z, patient",
                "null patient id,    NULL,  HR,   120,       2026-10-23T10:00:00Z, patient",
                "unknown vital type, p-1,   BP,   120,       2026-10-23T10:00:00Z, type",
                "null vital type,    p-1,   NULL, 120,       2026-10-23T10:00:00Z, type",
                "non-numeric value,  p-1,   HR,   n/a,       2026-10-23T10:00:00Z, value",
                "null value,         p-1,   HR,   NULL,      2026-10-23T10:00:00Z, value",
                "bad timestamp,      p-1,   HR,   120,       yesterday,            timestamp",
                "null timestamp,     p-1,   HR,   120,       NULL,                 timestamp"
        })
        void garbledReadingIsRejectedWithAReason(String name, String patientId, String type, String value,
                String timestamp, String expectedInReason) {
            Result result = evaluate(List.of(reading(patientId, type, value, timestamp)));

            assertThat(result.alerts()).isEmpty();
            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isZero();
                assertThat(rejected.reason()).containsIgnoringCase(expectedInReason);
            });
        }

        @ParameterizedTest
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void nonFiniteValueIsRejected(double value) {
            Result result = evaluate(List.of(reading("p-1", "HR", value, "2026-10-23T10:00:00Z")));

            assertThat(result.alerts()).isEmpty();
            assertThat(result.rejected()).hasSize(1);
        }

        @Test
        void thresholdRangesMustNest() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Threshold(50, 120, 60, 140));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Threshold(120, 50, 40, 140));
        }

        @Test
        void thresholdsMustCoverEveryVital() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Thresholds(Map.of()));
        }
    }
}
