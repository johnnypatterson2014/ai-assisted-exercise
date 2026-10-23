package exercise;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks whether one week of day and night shifts on every floor can be covered by the available staff. */
public final class ShiftCoverage {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftCoverage.class);

    /** Upper bound on floors per unit: keeps the report small and the arithmetic far from overflow. */
    public static final int MAX_FLOORS = 500;

    /** Upper bound on floors summed over all units of one run; 4000 units of 500 floors is a fifth of it. */
    public static final long MAX_TOTAL_FLOORS = 10_000_000L;

    private static final Comparator<Unit> UNIT_ORDER =
            Comparator.comparing((Unit unit) -> unit.location().id()).thenComparing(Unit::id);

    private ShiftCoverage() {
    }

    /** Head count assigned to each shift of one day after the greedy fill. */
    private record Assigned(int day, int night) {
    }

    /**
     * Checks coverage of every (weekday, shift, floor) slot of one unit for one week.
     * <p>
     * A person works at most one shift per day on any floor. Someone available for both shifts of a day is placed
     * on the day shift first, then on the night shift if the day is already covered. Staff are handed to floors in
     * ascending order, so a shortfall lands on the highest-numbered floors.
     *
     * @param floors       number of floors to staff; between 1 and {@link #MAX_FLOORS}
     * @param availability staff availability rows; must not be null, may be empty; duplicates count once
     * @return the report, with uncovered slots ordered Monday to Sunday, DAY before NIGHT, floor ascending
     * @throws IllegalArgumentException if {@code floors} is out of range, the list is null, or a row is unusable
     */
    public static CoverageReport check(int floors, List<Availability> availability) {
        if (floors < 1 || floors > MAX_FLOORS) {
            throw new IllegalArgumentException("floors must be between 1 and " + MAX_FLOORS + ", got " + floors);
        }
        if (availability == null) {
            throw new IllegalArgumentException("availability must not be null");
        }
        validateRows(availability, "row ");
        Map<DayOfWeek, Map<String, EnumSet<Shift>>> people = bucketByDay(availability);

        List<UncoveredShift> uncovered = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            Assigned assigned = assign(people.getOrDefault(day, Map.of()), floors);
            addShortFloors(uncovered, day, Shift.DAY, floors, assigned.day());
            addShortFloors(uncovered, day, Shift.NIGHT, floors, assigned.night());
        }

        LOG.info("checked {} floors against {} availability rows: {} of {} floor-shifts uncovered",
                floors, availability.size(), uncovered.size(), floors * slotsPerFloor());
        return new CoverageReport(uncovered.isEmpty(), List.copyOf(uncovered));
    }

    /**
     * Checks coverage of every unit in every location for one nightly run.
     * <p>
     * Staff give availability per time zone and may work any unit in any location of that zone, so each zone has
     * one shared pool. Within a zone, assigned staff are handed to units in (location id, unit id) order, then to
     * floors as in {@link #check}; a shortfall lands on the last units of the last location. Availability for a zone
     * with no units is validated but otherwise ignored.
     *
     * @param units              the units to staff; must not be null, may be empty; ids unique, floors 1..{@link #MAX_FLOORS}
     * @param availabilityByZone staff availability rows per zone; must not be null; a zone absent here has no staff
     * @return one report per unit, ordered by zone id, location id, unit id
     * @throws IllegalArgumentException if a unit, a location or a row is unusable, a location id maps to two zones,
     *                                  or the floors summed over all units exceed {@link #MAX_TOTAL_FLOORS}
     */
    public static List<UnitReport> checkAll(List<Unit> units, Map<ZoneId, List<Availability>> availabilityByZone) {
        long start = System.nanoTime();
        if (units == null) {
            throw new IllegalArgumentException("units must not be null");
        }
        if (availabilityByZone == null) {
            throw new IllegalArgumentException("availability must not be null");
        }
        Map<ZoneId, List<Unit>> unitsByZone = validateAndGroupUnits(units);
        long totalFloors = units.stream().mapToLong(Unit::floors).sum();
        if (totalFloors > MAX_TOTAL_FLOORS) {
            throw new IllegalArgumentException(
                    "total floors across units must be at most " + MAX_TOTAL_FLOORS + ", got " + totalFloors);
        }
        List<ZoneId> zones = new ArrayList<>(availabilityByZone.keySet());
        zones.sort(Comparator.nullsFirst(Comparator.comparing(ZoneId::getId)));
        int rows = 0;
        for (ZoneId zone : zones) {
            if (zone == null) {
                throw new IllegalArgumentException("zone must not be null");
            }
            List<Availability> roster = availabilityByZone.get(zone);
            if (roster == null) {
                throw new IllegalArgumentException("zone " + zone + ": availability must not be null");
            }
            validateRows(roster, "zone " + zone + " row ");
            rows += roster.size();
        }

        List<UnitReport> reports = new ArrayList<>(units.size());
        int uncoveredTotal = 0;
        for (Map.Entry<ZoneId, List<Unit>> zone : unitsByZone.entrySet()) {
            List<Unit> zoneUnits = zone.getValue();
            Map<DayOfWeek, Map<String, EnumSet<Shift>>> people =
                    bucketByDay(availabilityByZone.getOrDefault(zone.getKey(), List.of()));
            int zoneFloors = zoneUnits.stream().mapToInt(Unit::floors).sum();

            Map<Unit, List<UncoveredShift>> uncoveredByUnit = new LinkedHashMap<>();
            zoneUnits.forEach(unit -> uncoveredByUnit.put(unit, new ArrayList<>()));
            for (DayOfWeek day : DayOfWeek.values()) {
                Assigned assigned = assign(people.getOrDefault(day, Map.of()), zoneFloors);
                handOut(uncoveredByUnit, day, Shift.DAY, assigned.day());
                handOut(uncoveredByUnit, day, Shift.NIGHT, assigned.night());
            }
            for (Map.Entry<Unit, List<UncoveredShift>> entry : uncoveredByUnit.entrySet()) {
                Unit unit = entry.getKey();
                List<UncoveredShift> uncovered = entry.getValue();
                uncoveredTotal += uncovered.size();
                reports.add(new UnitReport(zone.getKey(), unit.location().id(), unit.id(),
                        new CoverageReport(uncovered.isEmpty(), List.copyOf(uncovered))));
            }
        }

        LOG.info("checked {} units in {} zones against {} availability rows: {} floor-shifts uncovered in {} ms",
                units.size(), unitsByZone.size(), rows, uncoveredTotal, (System.nanoTime() - start) / 1_000_000);
        return List.copyOf(reports);
    }

    /** Validates every unit, then groups them by zone (zones in id order, units in location then id order). */
    private static Map<ZoneId, List<Unit>> validateAndGroupUnits(List<Unit> units) {
        Set<String> ids = new HashSet<>();
        Map<String, ZoneId> zoneByLocation = new HashMap<>();
        Map<ZoneId, List<Unit>> byZone = new TreeMap<>(Comparator.comparing(ZoneId::getId));
        int index = 0;
        for (Unit unit : units) {
            if (unit == null) {
                throw new IllegalArgumentException("unit " + index + " must not be null");
            }
            if (unit.id() == null || unit.id().isBlank()) {
                throw new IllegalArgumentException("unit " + index + ": id must not be blank");
            }
            String label = "unit " + unit.id() + ": ";
            if (unit.location() == null) {
                throw new IllegalArgumentException(label + "location must not be null");
            }
            if (unit.location().id() == null || unit.location().id().isBlank()) {
                throw new IllegalArgumentException(label + "location id must not be blank");
            }
            if (unit.location().zone() == null) {
                throw new IllegalArgumentException(label + "location zone must not be null");
            }
            if (unit.floors() < 1 || unit.floors() > MAX_FLOORS) {
                throw new IllegalArgumentException(
                        label + "floors must be between 1 and " + MAX_FLOORS + ", got " + unit.floors());
            }
            if (!ids.add(unit.id())) {
                throw new IllegalArgumentException("duplicate unit id '" + unit.id() + "'");
            }
            ZoneId previous = zoneByLocation.putIfAbsent(unit.location().id(), unit.location().zone());
            if (previous != null && !previous.equals(unit.location().zone())) {
                throw new IllegalArgumentException("location '" + unit.location().id() + "' has two zones: "
                        + previous + " and " + unit.location().zone());
            }
            byZone.computeIfAbsent(unit.location().zone(), zone -> new ArrayList<>()).add(unit);
            index++;
        }
        byZone.values().forEach(list -> list.sort(UNIT_ORDER));
        return byZone;
    }

    private static void validateRows(List<Availability> rows, String labelPrefix) {
        int index = 0;
        for (Availability row : rows) {
            validate(row, labelPrefix + index++);
        }
    }

    private static void validate(Availability row, String label) {
        if (row == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        if (row.staff() == null || row.staff().isBlank()) {
            throw new IllegalArgumentException(label + ": staff name must not be blank");
        }
        if (row.day() == null) {
            throw new IllegalArgumentException(label + ": day must not be null");
        }
        if (row.shift() == null) {
            throw new IllegalArgumentException(label + ": shift must not be null");
        }
    }

    /** One pass over validated rows: per day, each distinct person and the set of shifts they offered. */
    private static Map<DayOfWeek, Map<String, EnumSet<Shift>>> bucketByDay(List<Availability> rows) {
        Map<DayOfWeek, Map<String, EnumSet<Shift>>> byDay = new EnumMap<>(DayOfWeek.class);
        for (Availability row : rows) {
            byDay.computeIfAbsent(row.day(), day -> new HashMap<>())
                    .computeIfAbsent(normalizeName(row.staff()), name -> EnumSet.noneOf(Shift.class))
                    .add(row.shift());
        }
        return byDay;
    }

    private static String normalizeName(String staff) {
        return staff.trim().toLowerCase(Locale.ROOT);
    }

    /** Greedy fill for one day: day-only people then flexible ones on the day shift, the rest on the night. */
    private static Assigned assign(Map<String, EnumSet<Shift>> people, int floors) {
        int dayOnly = 0;
        int nightOnly = 0;
        int flexible = 0;
        for (EnumSet<Shift> shifts : people.values()) {
            if (shifts.size() == 2) {
                flexible++;
            } else if (shifts.contains(Shift.DAY)) {
                dayOnly++;
            } else {
                nightOnly++;
            }
        }
        int day = Math.min(floors * Shift.DAY.staffPerFloor(), dayOnly + flexible);
        int flexibleLeft = flexible - Math.max(0, day - dayOnly);
        int night = Math.min(floors * Shift.NIGHT.staffPerFloor(), nightOnly + flexibleLeft);
        return new Assigned(day, night);
    }

    /** Hands {@code staff} people to the units in order, each unit taking at most what its floors need. */
    private static void handOut(Map<Unit, List<UncoveredShift>> uncoveredByUnit, DayOfWeek day, Shift shift, int staff) {
        int remaining = staff;
        for (Map.Entry<Unit, List<UncoveredShift>> entry : uncoveredByUnit.entrySet()) {
            Unit unit = entry.getKey();
            int taken = Math.min(remaining, unit.floors() * shift.staffPerFloor());
            remaining -= taken;
            addShortFloors(entry.getValue(), day, shift, unit.floors(), taken);
        }
    }

    /** Hands {@code staff} people to floors 1..n in order and records every floor left short. */
    private static void addShortFloors(List<UncoveredShift> out, DayOfWeek day, Shift shift, int floors, int staff) {
        int required = shift.staffPerFloor();
        int remaining = staff;
        for (int floor = 1; floor <= floors; floor++) {
            int onFloor = Math.min(required, remaining);
            remaining -= onFloor;
            if (onFloor < required) {
                out.add(new UncoveredShift(day, shift, floor, required, onFloor));
            }
        }
    }

    private static int slotsPerFloor() {
        return DayOfWeek.values().length * Shift.values().length;
    }
}
