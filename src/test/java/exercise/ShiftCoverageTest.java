package exercise;

import static exercise.Shift.DAY;
import static exercise.Shift.NIGHT;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShiftCoverageTest {

    /** A roster with exactly enough people for every slot: 2 per floor on days, 1 per floor on nights. */
    static List<Availability> fullRoster(int floors) {
        List<Availability> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int i = 1; i <= floors * 2; i++) {
                rows.add(new Availability("day-" + i, day, DAY));
            }
            for (int i = 1; i <= floors; i++) {
                rows.add(new Availability("night-" + i, day, NIGHT));
            }
        }
        return rows;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void exactlyEnoughStaffCoversEveryShiftOnEveryFloor() {
            CoverageReport report = ShiftCoverage.check(5, fullRoster(5));

            assertThat(report.fullyCovered()).isTrue();
            assertThat(report.uncovered()).isEmpty();
        }

        @Test
        void oneMissingNightNurseIsReportedForTheHighestFloor() {
            List<Availability> rows = fullRoster(5);
            rows.remove(new Availability("night-3", FRIDAY, NIGHT));

            CoverageReport report = ShiftCoverage.check(5, rows);

            assertThat(report.fullyCovered()).isFalse();
            assertThat(report.uncovered()).containsExactly(new UncoveredShift(FRIDAY, NIGHT, 5, 1, 0));
        }

        @Test
        void aFloorWithOnlyOneDayNurseIsReportedAsPartlyCovered() {
            List<Availability> rows = fullRoster(2);
            rows.remove(new Availability("day-4", MONDAY, DAY));

            assertThat(ShiftCoverage.check(2, rows).uncovered())
                    .containsExactly(new UncoveredShift(MONDAY, DAY, 2, 2, 1));
        }

        @Test
        void exampleFromTheProblemStatementOnOneFloor() {
            List<Availability> rows = new ArrayList<>();
            for (DayOfWeek day : List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)) {
                rows.add(new Availability("monica", day, DAY));
            }
            rows.add(new Availability("jeff", MONDAY, DAY));
            rows.add(new Availability("jeff", TUESDAY, DAY));
            rows.add(new Availability("jeff", FRIDAY, NIGHT));
            rows.add(new Availability("jeff", SATURDAY, NIGHT));

            CoverageReport report = ShiftCoverage.check(1, rows);

            assertThat(report.fullyCovered()).isFalse();
            assertThat(report.uncovered()).containsExactly(
                    new UncoveredShift(MONDAY, NIGHT, 1, 1, 0),
                    new UncoveredShift(TUESDAY, NIGHT, 1, 1, 0),
                    new UncoveredShift(WEDNESDAY, DAY, 1, 2, 1),
                    new UncoveredShift(WEDNESDAY, NIGHT, 1, 1, 0),
                    new UncoveredShift(THURSDAY, DAY, 1, 2, 1),
                    new UncoveredShift(THURSDAY, NIGHT, 1, 1, 0),
                    new UncoveredShift(FRIDAY, DAY, 1, 2, 1),
                    new UncoveredShift(SATURDAY, DAY, 1, 2, 0),
                    new UncoveredShift(SUNDAY, DAY, 1, 2, 0),
                    new UncoveredShift(SUNDAY, NIGHT, 1, 1, 0));
        }

        @Test
        void someoneAvailableForBothShiftsFillsTheNightWhenTheDayIsAlreadyCovered() {
            List<Availability> rows = fullRoster(1);
            rows.remove(new Availability("night-1", MONDAY, NIGHT));
            rows.add(new Availability("flexible", MONDAY, DAY));
            rows.add(new Availability("flexible", MONDAY, NIGHT));

            assertThat(ShiftCoverage.check(1, rows).fullyCovered()).isTrue();
        }

        @ParameterizedTest(name = "{0} floors, {1} day and {2} night staff on Monday leave {3} Monday slots short")
        @CsvSource({"1,2,1,0", "1,1,1,1", "1,0,0,2", "3,6,2,1", "3,3,3,2", "5,10,5,0"})
        void shortfallOnMondayCountsFloorsNotPeople(int floors, int dayStaff, int nightStaff, int expectedShort) {
            List<Availability> rows = fullRoster(floors);
            rows.removeIf(row -> row.day() == MONDAY);
            for (int i = 1; i <= dayStaff; i++) {
                rows.add(new Availability("d" + i, MONDAY, DAY));
            }
            for (int i = 1; i <= nightStaff; i++) {
                rows.add(new Availability("n" + i, MONDAY, NIGHT));
            }

            assertThat(ShiftCoverage.check(floors, rows).uncovered()).hasSize(expectedShort);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        void noStaffAtAllReportsEverySlotInWeekdayThenShiftThenFloorOrder() {
            CoverageReport report = ShiftCoverage.check(2, List.of());

            assertThat(report.fullyCovered()).isFalse();
            assertThat(report.uncovered()).hasSize(7 * 2 * 2);
            assertThat(report.uncovered()).startsWith(
                    new UncoveredShift(MONDAY, DAY, 1, 2, 0),
                    new UncoveredShift(MONDAY, DAY, 2, 2, 0),
                    new UncoveredShift(MONDAY, NIGHT, 1, 1, 0),
                    new UncoveredShift(MONDAY, NIGHT, 2, 1, 0),
                    new UncoveredShift(TUESDAY, DAY, 1, 2, 0));
            assertThat(report.uncovered()).endsWith(new UncoveredShift(SUNDAY, NIGHT, 2, 1, 0));
        }

        @Test
        void duplicateRowsForTheSamePersonCountOnce() {
            List<Availability> rows = fullRoster(1);
            rows.remove(new Availability("day-2", MONDAY, DAY));
            rows.add(new Availability("day-1", MONDAY, DAY));

            assertThat(ShiftCoverage.check(1, rows).uncovered())
                    .containsExactly(new UncoveredShift(MONDAY, DAY, 1, 2, 1));
        }

        @Test
        void namesAreMatchedIgnoringCaseAndSurroundingSpaces() {
            List<Availability> rows = fullRoster(1);
            rows.remove(new Availability("day-2", MONDAY, DAY));
            rows.add(new Availability("  DAY-1 ", MONDAY, DAY));

            assertThat(ShiftCoverage.check(1, rows).uncovered())
                    .containsExactly(new UncoveredShift(MONDAY, DAY, 1, 2, 1));
        }

        @Test
        void onePersonCannotWorkBothTheDayAndTheNightOfTheSameDay() {
            List<Availability> rows = fullRoster(1);
            rows.remove(new Availability("night-1", MONDAY, NIGHT));
            rows.add(new Availability("day-1", MONDAY, NIGHT));

            assertThat(ShiftCoverage.check(1, rows).uncovered())
                    .containsExactly(new UncoveredShift(MONDAY, NIGHT, 1, 1, 0));
        }

        @Test
        void moreFlexibleStaffThanSlotsStillLeavesNothingShortAndNothingDoubleBooked() {
            List<Availability> rows = new ArrayList<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                for (int i = 1; i <= 10; i++) {
                    rows.add(new Availability("flex-" + i, day, DAY));
                    rows.add(new Availability("flex-" + i, day, NIGHT));
                }
            }

            assertThat(ShiftCoverage.check(2, rows).fullyCovered()).isTrue();
            // 4 floors need 8 day + 4 night = 12 people; the 10 cover the day first, then nights on floors 1 and 2
            assertThat(ShiftCoverage.check(4, rows).uncovered())
                    .hasSize(7 * 2)
                    .allMatch(slot -> slot.shift() == NIGHT && slot.floor() >= 3 && slot.available() == 0);
        }

        @Test
        void linkedListInputIsAcceptedAndValidatedByPosition() {
            List<Availability> rows = new LinkedList<>(fullRoster(1));
            rows.add(new Availability("late", null, DAY));

            assertThat(ShiftCoverage.check(1, new LinkedList<>(fullRoster(1))).fullyCovered()).isTrue();
            assertThatThrownBy(() -> ShiftCoverage.check(1, rows))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("row 21");
        }

        @Test
        void inputListIsNotModified() {
            List<Availability> rows = fullRoster(1);
            rows.add(new Availability("day-1", MONDAY, DAY));
            List<Availability> copy = new ArrayList<>(rows);

            ShiftCoverage.check(1, rows);

            assertThat(rows).isEqualTo(copy);
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @Test
        void nullAvailabilityIsRejected() {
            assertThatThrownBy(() -> ShiftCoverage.check(1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("availability must not be null");
        }

        @ParameterizedTest(name = "{0} floors is rejected")
        @ValueSource(ints = {0, -1, ShiftCoverage.MAX_FLOORS + 1, Integer.MAX_VALUE})
        void floorsOutsideOneToMaxAreRejected(int floors) {
            assertThatThrownBy(() -> ShiftCoverage.check(floors, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("floors must be between 1 and " + ShiftCoverage.MAX_FLOORS);
        }

        @Test
        void maximumFloorsWithNoStaffStillProducesAFullReport() {
            CoverageReport report = ShiftCoverage.check(ShiftCoverage.MAX_FLOORS, List.of());

            assertThat(report.uncovered()).hasSize(ShiftCoverage.MAX_FLOORS * 14)
                    .allMatch(slot -> slot.available() == 0 && slot.required() > 0);
        }

        @Test
        void rowWithoutShiftIsRejectedWithItsPosition() {
            List<Availability> rows = List.of(new Availability("a", MONDAY, DAY), new Availability("b", MONDAY, null));

            assertThatThrownBy(() -> ShiftCoverage.check(1, rows))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("row 1: shift");
        }

        @Test
        void nullRowIsRejectedWithItsPosition() {
            List<Availability> rows = Arrays.asList(new Availability("a", MONDAY, DAY), null);

            assertThatThrownBy(() -> ShiftCoverage.check(1, rows))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("row 1");
        }

        @Test
        void rowWithoutDayOrShiftIsRejectedWithItsPosition() {
            List<Availability> rows = List.of(new Availability("a", null, DAY), new Availability("b", MONDAY, null));

            assertThatThrownBy(() -> ShiftCoverage.check(1, rows))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("row 0");
        }

        @ParameterizedTest(name = "staff name [{0}] is rejected")
        @ValueSource(strings = {"", "   ", "\t\n"})
        void blankStaffNameIsRejected(String name) {
            assertThatThrownBy(() -> ShiftCoverage.check(1, List.of(new Availability(name, MONDAY, DAY))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("staff name");
        }
    }
}
