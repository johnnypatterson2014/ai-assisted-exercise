package exercise;

import static exercise.Shift.DAY;
import static exercise.Shift.NIGHT;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RunnerTest {

    static CoverageReport run(String... lines) throws IOException {
        return Runner.MAPPER.readValue(Runner.run(List.of(lines)), CoverageReport.class);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void problemStatementRosterOnOneFloorRendersTheUncoveredShifts() throws IOException {
            CoverageReport report = run(
                    "# roster from the problem statement",
                    "floors = 1",
                    "",
                    "monica: mon - fri: day shift",
                    "jeff: mon, tues: day shift; fri, sat: night shift");

            assertThat(report.fullyCovered()).isFalse();
            assertThat(report.uncovered()).hasSize(10)
                    .contains(new UncoveredShift(MONDAY, NIGHT, 1, 1, 0), new UncoveredShift(SATURDAY, DAY, 1, 2, 0))
                    .doesNotContain(new UncoveredShift(FRIDAY, NIGHT, 1, 1, 0));
        }

        @Test
        void floorsDefaultToFiveWhenNoHeaderIsGiven() throws IOException {
            assertThat(run("ann: mon: day").uncovered())
                    .hasSize(5 * 14)
                    .contains(new UncoveredShift(MONDAY, DAY, 1, 2, 1));
        }

        @Test
        void zoneSectionsGiveOneReportPerUnitInZoneThenLocationThenUnitOrder() throws IOException {
            List<UnitReport> reports = Runner.MAPPER.readValue(Runner.run(List.of(
                    "zone = America/Vancouver",
                    "unit van-general gen-1 = 1",
                    "unit van-children ch-1 = 1",
                    "d1: mon-sun: day", "d2: mon-sun: day", "d3: mon-sun: day", "n1: mon-sun: night",
                    "",
                    "zone: America/Toronto",
                    "unit tor-general t-1 = 1")), new TypeReference<List<UnitReport>>() { });

            assertThat(reports).extracting(UnitReport::unitId).containsExactly("t-1", "ch-1", "gen-1");
            assertThat(reports.get(0).zone()).isEqualTo(ZoneId.of("America/Toronto"));
            assertThat(reports.get(0).report().uncovered()).hasSize(14);
            assertThat(reports.get(1).report().fullyCovered()).isTrue();
            assertThat(reports.get(2).report().uncovered()).hasSize(14)
                    .contains(new UncoveredShift(MONDAY, DAY, 1, 2, 1), new UncoveredShift(MONDAY, NIGHT, 1, 1, 0));
        }

        @Test
        void dayRangesWrapAroundTheWeekAndPrefixesAreAccepted() throws IOException {
            CoverageReport report = run("floors: 1", "ann: SATURDAY-Monday: Night", "bob: Sat - mon: night");

            assertThat(report.uncovered()).filteredOn(slot -> slot.shift() == NIGHT)
                    .extracting(UncoveredShift::day)
                    .doesNotContain(SATURDAY, SUNDAY, MONDAY)
                    .hasSize(4);
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @ParameterizedTest(name = "line [{0}] fails with [{1}]")
        @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
                "monica mon fri day       | line 2: expected 'name: days: shift'",
                "monica: mon: evening     | line 2: unknown shift 'evening'",
                "monica: mo: day          | line 2: unknown day 'mo'",
                "monica: mon-tue-wed: day | line 2: unknown day 'mon-tue-wed'",
                "monica: mon day          | line 2: expected 'days: shift'",
                "floors = five            | line 2: expected 'floors = <number>'",
                "floors = 0               | floors must be between 1 and 500",
                ": mon: day               | row 0: staff name must not be blank"})
        void badLinesAreReportedWithTheirLineNumber(String line, String message) {
            assertThatThrownBy(() -> Runner.run(List.of("# header", line)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(message);
        }

        @ParameterizedTest(name = "after a zone line, [{0}] fails with [{1}]")
        @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
                "zone = Mars/Olympus          | line 2: unknown zone id 'Mars/Olympus'",
                "unit gen-1 = 2               | line 2: expected 'name: days: shift'",
                "unit van-general gen-1 = two | line 2: expected 'unit <location> <unit-id> = <floors>'",
                "unit van-general gen-1 = 0   | unit gen-1: floors must be between 1 and 500",
                "floors = 3                   | line 2: 'floors' is for the single-unit format"})
        void badNightlyLinesAreReportedWithTheirLineNumber(String line, String message) {
            assertThatThrownBy(() -> Runner.run(List.of("zone = America/Vancouver", line)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(message);
        }

        @Test
        void unitLinesAndStaffLinesMustBeInsideAZoneSectionWhenZonesAreUsed() {
            assertThatThrownBy(() -> Runner.run(List.of("unit van-general gen-1 = 2")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("line 1: unit line before the first zone line");
            assertThatThrownBy(() -> Runner.run(List.of("ann: mon: day", "zone = America/Vancouver")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("line 2: zone sections must come before any staff line");
        }
    }
}
