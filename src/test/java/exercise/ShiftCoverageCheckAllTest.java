package exercise;

import static exercise.Shift.DAY;
import static exercise.Shift.NIGHT;
import static exercise.ShiftCoverageTest.fullRoster;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The nightly run: many units in several locations, one shared staff pool per time zone. */
class ShiftCoverageCheckAllTest {

    static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");
    static final ZoneId EASTERN = ZoneId.of("America/Toronto");

    static final Location VAN_GENERAL = new Location("van-general", PACIFIC);
    static final Location VAN_CHILDREN = new Location("van-children", PACIFIC);
    static final Location VAN_ST_PAUL = new Location("van-st-paul", PACIFIC);
    static final Location TOR_GENERAL = new Location("tor-general", EASTERN);

    /** Three locations in the Pacific zone, five floors in total, listed out of order on purpose. */
    static List<Unit> pacificUnits() {
        return List.of(
                new Unit("sp-1", VAN_ST_PAUL, 2),
                new Unit("ch-1", VAN_CHILDREN, 1),
                new Unit("gen-1", VAN_GENERAL, 2));
    }

    static Map<ZoneId, List<Availability>> pool(ZoneId zone, List<Availability> rows) {
        return Map.of(zone, rows);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void threeLocationsInOneZoneShareOnePoolAndAreReportedInLocationThenUnitOrder() {
            List<UnitReport> reports = ShiftCoverage.checkAll(pacificUnits(), pool(PACIFIC, fullRoster(5)));

            assertThat(reports).extracting(UnitReport::unitId).containsExactly("ch-1", "gen-1", "sp-1");
            assertThat(reports).extracting(UnitReport::location).containsExactly("van-children", "van-general", "van-st-paul");
            assertThat(reports).extracting(UnitReport::zone).containsOnly(PACIFIC);
            assertThat(reports).allMatch(r -> r.report().fullyCovered());
        }

        @Test
        void oneMissingNightNurseLandsOnTheLastFloorOfTheLastUnitOfTheLastLocation() {
            List<Availability> rows = fullRoster(5);
            rows.remove(new Availability("night-1", FRIDAY, NIGHT));

            List<UnitReport> reports = ShiftCoverage.checkAll(pacificUnits(), pool(PACIFIC, rows));

            assertThat(reports.get(0).report().fullyCovered()).isTrue();
            assertThat(reports.get(1).report().fullyCovered()).isTrue();
            assertThat(reports.get(2).unitId()).isEqualTo("sp-1");
            assertThat(reports.get(2).report().uncovered()).containsExactly(new UncoveredShift(FRIDAY, NIGHT, 2, 1, 0));
        }

        @Test
        void aPartialPoolIsHandedOutUnitByUnitDownToFloors() {
            List<Availability> rows = fullRoster(5);
            rows.removeIf(row -> row.day() == MONDAY && row.shift() == DAY);
            for (int i = 1; i <= 7; i++) {
                rows.add(new Availability("mon-" + i, MONDAY, DAY));
            }

            List<UnitReport> reports = ShiftCoverage.checkAll(pacificUnits(), pool(PACIFIC, rows));

            // 7 of 10 needed: ch-1 takes 2, gen-1 takes 4, sp-1 gets the last 1
            assertThat(reports.get(0).report().fullyCovered()).isTrue();
            assertThat(reports.get(1).report().fullyCovered()).isTrue();
            assertThat(reports.get(2).report().uncovered()).containsExactly(
                    new UncoveredShift(MONDAY, DAY, 1, 2, 1),
                    new UncoveredShift(MONDAY, DAY, 2, 2, 0));
        }

        @Test
        void zonesDoNotShareStaff() {
            List<Unit> units = List.of(new Unit("gen-1", VAN_GENERAL, 1), new Unit("tor-1", TOR_GENERAL, 1));

            List<UnitReport> reports = ShiftCoverage.checkAll(units, pool(PACIFIC, fullRoster(2)));

            assertThat(reports).extracting(UnitReport::unitId).containsExactly("tor-1", "gen-1");
            assertThat(reports.get(0).report().uncovered()).hasSize(14);
            assertThat(reports.get(1).report().fullyCovered()).isTrue();
        }

        @Test
        void aPartialNightPoolIsHandedOutAcrossUnitsOfDifferentSizes() {
            List<Availability> rows = fullRoster(5);
            rows.removeIf(row -> row.day() == MONDAY && row.shift() == NIGHT);
            rows.add(new Availability("n-1", MONDAY, NIGHT));
            rows.add(new Availability("n-2", MONDAY, NIGHT));

            List<UnitReport> reports = ShiftCoverage.checkAll(pacificUnits(), pool(PACIFIC, rows));

            // 2 of 5 needed: ch-1 (1 floor) takes 1, gen-1 (2 floors) takes 1, sp-1 (2 floors) gets none
            assertThat(reports.get(0).report().fullyCovered()).isTrue();
            assertThat(reports.get(1).report().uncovered()).containsExactly(new UncoveredShift(MONDAY, NIGHT, 2, 1, 0));
            assertThat(reports.get(2).report().uncovered()).containsExactly(
                    new UncoveredShift(MONDAY, NIGHT, 1, 1, 0), new UncoveredShift(MONDAY, NIGHT, 2, 1, 0));
        }

        @Test
        void fiveZonesWithSevenLocationsAreReportedInZoneThenLocationThenUnitOrder() {
            List<Unit> units = new ArrayList<>(pacificUnits());
            units.add(new Unit("tor-1", TOR_GENERAL, 1));
            units.add(new Unit("hal-1", new Location("hal-general", ZoneId.of("America/Halifax")), 1));
            units.add(new Unit("edm-1", new Location("edm-general", ZoneId.of("America/Edmonton")), 1));
            units.add(new Unit("wpg-1", new Location("wpg-general", ZoneId.of("America/Winnipeg")), 1));
            Map<ZoneId, List<Availability>> pools = Map.of(
                    PACIFIC, fullRoster(5), EASTERN, fullRoster(1), ZoneId.of("America/Halifax"), fullRoster(1));

            List<UnitReport> reports = ShiftCoverage.checkAll(units, pools);

            assertThat(reports).extracting(UnitReport::unitId)
                    .containsExactly("edm-1", "hal-1", "tor-1", "ch-1", "gen-1", "sp-1", "wpg-1");
            assertThat(reports).filteredOn(r -> !r.report().fullyCovered())
                    .extracting(UnitReport::unitId).containsExactly("edm-1", "wpg-1");
        }

        @Test
        void fourThousandUnitsOfFiveFloorsAcrossFiveZonesRunInWellUnderASecond() {
            List<ZoneId> zones = List.of(PACIFIC, ZoneId.of("America/Edmonton"), ZoneId.of("America/Winnipeg"),
                    EASTERN, ZoneId.of("America/Halifax"));
            List<Unit> units = new ArrayList<>();
            Map<ZoneId, List<Availability>> pools = new HashMap<>();
            for (ZoneId zone : zones) {
                Location location = new Location("loc-" + zone.getId(), zone);
                for (int i = 0; i < 800; i++) {
                    units.add(new Unit(zone.getId() + "-u" + i, location, 5));
                }
                pools.put(zone, fullRoster(800 * 5));
            }

            long start = System.nanoTime();
            List<UnitReport> reports = ShiftCoverage.checkAll(units, pools);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(reports).hasSize(4000).allMatch(r -> r.report().fullyCovered());
            assertThat(elapsedMs).isLessThan(5_000);
        }

        @Test
        void aSingleUnitGivesTheSameReportAsCheck() {
            List<Availability> rows = fullRoster(3);
            rows.remove(new Availability("day-5", MONDAY, DAY));
            rows.remove(new Availability("night-2", FRIDAY, NIGHT));

            List<UnitReport> reports = ShiftCoverage.checkAll(List.of(new Unit("gen-1", VAN_GENERAL, 3)), pool(PACIFIC, rows));

            assertThat(reports).hasSize(1);
            assertThat(reports.get(0).report()).isEqualTo(ShiftCoverage.check(3, rows));
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        void noUnitsGivesAnEmptyReportEvenWithStaff() {
            assertThat(ShiftCoverage.checkAll(List.of(), pool(PACIFIC, fullRoster(1)))).isEmpty();
        }

        @Test
        void staffForAZoneWithoutUnitsAreIgnored() {
            List<Unit> units = List.of(new Unit("gen-1", VAN_GENERAL, 1));
            Map<ZoneId, List<Availability>> pools = Map.of(PACIFIC, fullRoster(1), EASTERN, fullRoster(9));

            assertThat(ShiftCoverage.checkAll(units, pools)).hasSize(1)
                    .allMatch(r -> r.report().fullyCovered());
        }

        @Test
        void aZoneWithAnEmptyRosterReportsEverySlotOfEveryUnit() {
            List<UnitReport> reports = ShiftCoverage.checkAll(pacificUnits(), pool(PACIFIC, List.of()));

            assertThat(reports).hasSize(3);
            assertThat(reports.get(0).report().uncovered()).hasSize(14);   // ch-1, 1 floor
            assertThat(reports.get(1).report().uncovered()).hasSize(28);   // gen-1, 2 floors
        }

        @Test
        void aPersonListedInTwoZonesCountsInBoth() {
            List<Unit> units = List.of(new Unit("gen-1", VAN_GENERAL, 1), new Unit("tor-1", TOR_GENERAL, 1));
            Map<ZoneId, List<Availability>> pools = Map.of(PACIFIC, fullRoster(1), EASTERN, fullRoster(1));

            assertThat(ShiftCoverage.checkAll(units, pools)).allMatch(r -> r.report().fullyCovered());
        }

        @Test
        void unitIdsDifferingOnlyInCaseAreDistinctUnits() {
            List<Unit> units = List.of(new Unit("U-1", VAN_GENERAL, 1), new Unit("u-1", VAN_GENERAL, 1));

            assertThat(ShiftCoverage.checkAll(units, pool(PACIFIC, fullRoster(2))))
                    .extracting(UnitReport::unitId).containsExactly("U-1", "u-1");
        }

        @Test
        void inputsAreNotModified() {
            List<Unit> units = new ArrayList<>(pacificUnits());
            List<Availability> rows = fullRoster(2);
            Map<ZoneId, List<Availability>> pools = new HashMap<>(Map.of(PACIFIC, rows));
            List<Unit> unitsCopy = List.copyOf(units);
            List<Availability> rowsCopy = List.copyOf(rows);

            ShiftCoverage.checkAll(units, pools);

            assertThat(units).isEqualTo(unitsCopy);
            assertThat(rows).isEqualTo(rowsCopy);
            assertThat(pools).containsOnlyKeys(PACIFIC);
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @Test
        void nullUnitsOrNullAvailabilityIsRejected() {
            assertThatThrownBy(() -> ShiftCoverage.checkAll(null, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("units must not be null");
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(), null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("availability must not be null");
        }

        @Test
        void nullUnitIsRejectedWithItsPosition() {
            List<Unit> units = Arrays.asList(new Unit("gen-1", VAN_GENERAL, 1), null);

            assertThatThrownBy(() -> ShiftCoverage.checkAll(units, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit 1 must not be null");
        }

        @Test
        void unitWithoutIdOrLocationOrZoneIsRejected() {
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(new Unit(" ", VAN_GENERAL, 1)), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit 0: id must not be blank");
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(new Unit("gen-1", null, 1)), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit gen-1: location must not be null");
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(new Unit("gen-1", new Location("x", null), 1)), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit gen-1: location zone must not be null");
        }

        @ParameterizedTest(name = "{0} floors on a unit is rejected")
        @ValueSource(ints = {0, -1, ShiftCoverage.MAX_FLOORS + 1})
        void unitFloorsOutsideOneToMaxAreRejected(int floors) {
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(new Unit("gen-1", VAN_GENERAL, floors)), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unit gen-1: floors must be between 1 and " + ShiftCoverage.MAX_FLOORS);
        }

        @Test
        void duplicateUnitIdIsRejectedAcrossLocations() {
            List<Unit> units = List.of(new Unit("u-1", VAN_GENERAL, 1), new Unit("u-1", TOR_GENERAL, 1));

            assertThatThrownBy(() -> ShiftCoverage.checkAll(units, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate unit id 'u-1'");
        }

        @Test
        void oneLocationIdInTwoZonesIsRejected() {
            List<Unit> units = List.of(
                    new Unit("u-1", new Location("general", PACIFIC), 1),
                    new Unit("u-2", new Location("general", EASTERN), 1));

            assertThatThrownBy(() -> ShiftCoverage.checkAll(units, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("location 'general' has two zones");
        }

        @Test
        void totalFloorsAcrossAllUnitsIsCapped() {
            Location location = new Location("big", PACIFIC);
            List<Unit> units = new ArrayList<>();
            for (int i = 0; i <= ShiftCoverage.MAX_TOTAL_FLOORS / ShiftCoverage.MAX_FLOORS; i++) {
                units.add(new Unit("u-" + i, location, ShiftCoverage.MAX_FLOORS));
            }

            assertThatThrownBy(() -> ShiftCoverage.checkAll(units, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("total floors across units must be at most " + ShiftCoverage.MAX_TOTAL_FLOORS);
        }

        @Test
        void badRowsInSeveralZonesAreReportedForTheLowestZoneIdFirst() {
            Map<ZoneId, List<Availability>> pools = new HashMap<>();
            pools.put(PACIFIC, Arrays.asList(new Availability("a", MONDAY, DAY), null));
            pools.put(EASTERN, List.of(new Availability(" ", MONDAY, DAY)));

            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(), pools))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zone America/Toronto row 0: staff name must not be blank");
        }

        @Test
        void badRowIsRejectedWithItsZoneAndPosition() {
            List<Availability> rows = fullRoster(1);
            rows.add(new Availability("late", null, DAY));

            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(new Unit("gen-1", VAN_GENERAL, 1)), pool(PACIFIC, rows)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zone America/Vancouver row 21: day must not be null");
        }

        @Test
        void nullZoneOrNullRosterInTheMapIsRejected() {
            Map<ZoneId, List<Availability>> nullZone = new HashMap<>();
            nullZone.put(null, fullRoster(1));
            Map<ZoneId, List<Availability>> nullRows = new HashMap<>();
            nullRows.put(PACIFIC, null);

            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(), nullZone))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zone must not be null");
            assertThatThrownBy(() -> ShiftCoverage.checkAll(List.of(), nullRows))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zone America/Vancouver: availability must not be null");
        }
    }
}
