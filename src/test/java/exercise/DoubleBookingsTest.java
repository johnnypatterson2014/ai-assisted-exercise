package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import exercise.DoubleBookings.Conflict;
import exercise.DoubleBookings.ConflictKind;
import exercise.DoubleBookings.RawAppointment;
import exercise.DoubleBookings.RejectedAppointment;
import exercise.DoubleBookings.Result;

class DoubleBookingsTest {

    /** Builds an appointment on 2026-10-23 from "HH:mm" times, so the tests read as a schedule. */
    private static RawAppointment appt(String id, String provider, String patient, String start, String end) {
        return new RawAppointment(id, provider, patient, at(start), at(end));
    }

    private static String at(String hhmm) {
        return "2026-10-23T" + hhmm + ":00Z";
    }

    private static Result find(List<RawAppointment> appointments) {
        return DoubleBookings.findDoubleBookings(appointments);
    }

    @Nested
    class HappyPath {

        @Test
        void nonOverlappingAppointmentsGiveNoConflicts() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-1", "10:00", "10:30")));

            assertThat(result.conflicts()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void sameProviderOverlapIsAProviderConflict() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a1", "a2"));
        }

        @Test
        void samePatientOverlapIsAPatientConflict() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a3", "dr-2", "p-1", "09:00", "09:20")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PATIENT, "p-1", "a1", "a3"));
        }

        @Test
        void overlapWithDifferentProviderAndPatientIsNotAConflict() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-2", "p-2", "09:00", "09:30")));

            assertThat(result.conflicts()).isEmpty();
        }

        @Test
        void sharedProviderAndPatientGiveOneConflictPerKind() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-1", "09:10", "09:40")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a1", "a2"),
                    new Conflict(ConflictKind.PATIENT, "p-1", "a1", "a2"));
        }

        @Test
        void nestedAppointmentIsAConflict() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "10:00"),
                    appt("a2", "dr-1", "p-2", "09:20", "09:30")));

            assertThat(result.conflicts()).extracting(Conflict::firstId, Conflict::secondId)
                    .containsExactly(tuple("a1", "a2"));
        }

        @Test
        void sampleInputFromTheStatement() {
            List<RawAppointment> sample = List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45"),
                    appt("a3", "dr-2", "p-1", "09:00", "09:20"),
                    appt("a4", "dr-2", "p-3", "10:00", "09:00"));

            Result result = find(sample);

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a1", "a2"),
                    new Conflict(ConflictKind.PATIENT, "p-1", "a1", "a3"));
            assertThat(result.rejected()).extracting(RejectedAppointment::index, RejectedAppointment::id)
                    .containsExactly(tuple(3, "a4"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyInputGivesNoConflictsAndNoRejects() {
            Result result = find(List.of());

            assertThat(result.conflicts()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void singleAppointmentCannotConflict() {
            assertThat(find(List.of(appt("a1", "dr-1", "p-1", "09:00", "09:30"))).conflicts()).isEmpty();
        }

        @Test
        void touchingAppointmentsDoNotOverlap() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-1", "09:30", "10:00")));

            assertThat(result.conflicts()).isEmpty();
        }

        @Test
        void oneSecondOverlapIsAConflict() {
            Result result = find(List.of(
                    new RawAppointment("a1", "dr-1", "p-1", "2026-10-23T09:00:00Z", "2026-10-23T09:30:01Z"),
                    appt("a2", "dr-1", "p-2", "09:30", "10:00")));

            assertThat(result.conflicts()).hasSize(1);
        }

        @Test
        void identicalIntervalsConflict() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-2", "09:00", "09:30")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a1", "a2"));
        }

        @Test
        void chainOfOverlapsReportsOnlyPairsThatActuallyOverlap() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-2", "09:20", "09:50"),
                    appt("a3", "dr-1", "p-3", "09:40", "10:10")));

            assertThat(result.conflicts()).extracting(Conflict::firstId, Conflict::secondId)
                    .containsExactly(tuple("a1", "a2"), tuple("a2", "a3"));
        }

        @Test
        void threeWayOverlapReportsEveryPair() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "10:00"),
                    appt("a2", "dr-1", "p-2", "09:10", "10:00"),
                    appt("a3", "dr-1", "p-3", "09:20", "10:00")));

            assertThat(result.conflicts()).extracting(Conflict::firstId, Conflict::secondId)
                    .containsExactly(tuple("a1", "a2"), tuple("a1", "a3"), tuple("a2", "a3"));
        }

        @Test
        void pairIsReportedOnceWithIdsInSortedOrder() {
            Result result = find(List.of(
                    appt("b", "dr-1", "p-2", "09:15", "09:45"),
                    appt("a", "dr-1", "p-1", "09:00", "09:30")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a", "b"));
        }

        @Test
        void inputOrderDoesNotChangeTheResult() {
            List<RawAppointment> input = List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45"),
                    appt("a3", "dr-2", "p-1", "09:00", "09:20"));
            List<RawAppointment> reversed = new ArrayList<>(input);
            java.util.Collections.reverse(reversed);

            assertThat(find(reversed).conflicts()).isEqualTo(find(input).conflicts());
        }

        @Test
        void timestampsWithOffsetsAreComparedAsInstants() {
            Result result = find(List.of(
                    new RawAppointment("a1", "dr-1", "p-1", "2026-10-23T11:00:00+02:00", "2026-10-23T11:30:00+02:00"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45")));

            assertThat(result.conflicts()).hasSize(1);
        }

        @Test
        void conflictsAreSortedByKindThenResourceThenIds() {
            Result result = find(List.of(
                    appt("a4", "dr-2", "p-9", "09:00", "09:30"),
                    appt("a3", "dr-2", "p-8", "09:00", "09:30"),
                    appt("a2", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a1", "dr-3", "p-1", "09:00", "09:30")));

            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-2", "a3", "a4"),
                    new Conflict(ConflictKind.PATIENT, "p-1", "a1", "a2"));
        }

        @Test
        void inputListIsNotMutated() {
            List<RawAppointment> input = new ArrayList<>(List.of(
                    appt("a2", "dr-1", "p-2", "09:15", "09:45"),
                    appt("a1", "dr-1", "p-1", "09:00", "09:30")));
            List<RawAppointment> copy = List.copyOf(input);

            find(input);

            assertThat(input).containsExactlyElementsOf(copy);
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void nullListIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> DoubleBookings.findDoubleBookings(null))
                    .withMessageContaining("appointments");
        }

        @Test
        void nullElementIsRejectedWithItsIndexAndTheBatchContinues() {
            List<RawAppointment> input = Arrays.asList(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    null,
                    appt("a2", "dr-1", "p-2", "09:15", "09:45"));

            Result result = find(input);

            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isEqualTo(1);
                assertThat(rejected.id()).isNull();
                assertThat(rejected.reason()).contains("null");
            });
            assertThat(result.conflicts()).hasSize(1);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(nullValues = "NULL", value = {
                "blank id,          '  ', dr-1, p-1,  2026-10-23T09:00:00Z, 2026-10-23T09:30:00Z, id",
                "null id,           NULL, dr-1, p-1,  2026-10-23T09:00:00Z, 2026-10-23T09:30:00Z, id",
                "blank provider,    a1,   '  ', p-1,  2026-10-23T09:00:00Z, 2026-10-23T09:30:00Z, provider",
                "null patient,      a1,   dr-1, NULL, 2026-10-23T09:00:00Z, 2026-10-23T09:30:00Z, patient",
                "null start,        a1,   dr-1, p-1,  NULL,                 2026-10-23T09:30:00Z, start",
                "bad start,         a1,   dr-1, p-1,  tomorrow,             2026-10-23T09:30:00Z, start",
                "start without zone,a1,   dr-1, p-1,  2026-10-23T09:00:00,  2026-10-23T09:30:00Z, start",
                "bad end,           a1,   dr-1, p-1,  2026-10-23T09:00:00Z, never,                end",
                "end before start,  a1,   dr-1, p-1,  2026-10-23T10:00:00Z, 2026-10-23T09:00:00Z, end",
                "end equals start,  a1,   dr-1, p-1,  2026-10-23T09:00:00Z, 2026-10-23T09:00:00Z, end"
        })
        void invalidAppointmentIsRejectedWithAReason(String name, String id, String provider, String patient,
                String start, String end, String expectedInReason) {
            Result result = find(List.of(new RawAppointment(id, provider, patient, start, end)));

            assertThat(result.conflicts()).isEmpty();
            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isZero();
                assertThat(rejected.reason()).containsIgnoringCase(expectedInReason);
            });
        }

        @Test
        void duplicateIdRejectsTheSecondOccurrenceAndKeepsTheFirst() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("a1", "dr-9", "p-9", "12:00", "12:30"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45")));

            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isEqualTo(1);
                assertThat(rejected.reason()).containsIgnoringCase("duplicate");
            });
            assertThat(result.conflicts()).containsExactly(
                    new Conflict(ConflictKind.PROVIDER, "dr-1", "a1", "a2"));
        }

        @Test
        void invalidAppointmentDoesNotHideConflictsAmongTheOthers() {
            Result result = find(List.of(
                    appt("a1", "dr-1", "p-1", "09:00", "09:30"),
                    appt("bad", "dr-1", "p-1", "09:00", "08:00"),
                    appt("a2", "dr-1", "p-2", "09:15", "09:45")));

            assertThat(result.rejected()).extracting(RejectedAppointment::id).containsExactly("bad");
            assertThat(result.conflicts()).hasSize(1);
        }
    }
}
