package exercise;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks whether one week of day and night shifts on every floor can be covered by the available staff. */
public final class ShiftCoverage {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftCoverage.class);

    /** Upper bound on floors: keeps the report small and the arithmetic far from overflow. */
    public static final int MAX_FLOORS = 500;

    private ShiftCoverage() {
    }

    /**
     * Checks coverage of every (weekday, shift, floor) slot for one week.
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

        Map<DayOfWeek, Map<String, EnumSet<Shift>>> shiftsByPersonByDay = new EnumMap<>(DayOfWeek.class);
        int index = 0;
        for (Availability row : availability) {
            validate(row, index++);
            shiftsByPersonByDay.computeIfAbsent(row.day(), day -> new HashMap<>())
                    .computeIfAbsent(normalizeName(row.staff()), name -> EnumSet.noneOf(Shift.class))
                    .add(row.shift());
        }

        List<UncoveredShift> uncovered = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            int dayOnly = 0;
            int nightOnly = 0;
            int flexible = 0;
            for (EnumSet<Shift> shifts : shiftsByPersonByDay.getOrDefault(day, Map.of()).values()) {
                if (shifts.size() == 2) {
                    flexible++;
                } else if (shifts.contains(Shift.DAY)) {
                    dayOnly++;
                } else {
                    nightOnly++;
                }
            }
            int dayStaff = Math.min(floors * Shift.DAY.staffPerFloor(), dayOnly + flexible);
            int flexibleLeft = flexible - Math.max(0, dayStaff - dayOnly);
            int nightStaff = Math.min(floors * Shift.NIGHT.staffPerFloor(), nightOnly + flexibleLeft);
            addShortFloors(uncovered, day, Shift.DAY, floors, dayStaff);
            addShortFloors(uncovered, day, Shift.NIGHT, floors, nightStaff);
        }

        LOG.info("checked {} floors against {} availability rows: {} of {} floor-shifts uncovered",
                floors, availability.size(), uncovered.size(), floors * DayOfWeek.values().length * Shift.values().length);
        return new CoverageReport(uncovered.isEmpty(), List.copyOf(uncovered));
    }

    /**
     * Checks coverage of every unit in every location for one nightly run.
     * <p>
     * Staff give availability per time zone and may work any unit in any location of that zone, so each zone has
     * one shared pool. Within a zone, assigned staff are handed to units in (location id, unit id) order, then to
     * floors as in {@link #check}; a shortfall lands on the last units of the last location. Availability for a zone
     * with no units is ignored.
     *
     * @param units              the units to staff; must not be null, may be empty; ids unique, floors 1..{@link #MAX_FLOORS}
     * @param availabilityByZone staff availability rows per zone; must not be null; a zone absent here has no staff
     * @return one report per unit, ordered by zone id, location id, unit id
     * @throws IllegalArgumentException if a unit, a location or a row is unusable, or a location id maps to two zones
     */
    public static List<UnitReport> checkAll(List<Unit> units, Map<ZoneId, List<Availability>> availabilityByZone) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static void validate(Availability row, int index) {
        if (row == null) {
            throw new IllegalArgumentException("row " + index + " must not be null");
        }
        if (row.staff() == null || row.staff().isBlank()) {
            throw new IllegalArgumentException("row " + index + ": staff name must not be blank");
        }
        if (row.day() == null) {
            throw new IllegalArgumentException("row " + index + ": day must not be null");
        }
        if (row.shift() == null) {
            throw new IllegalArgumentException("row " + index + ": shift must not be null");
        }
    }

    private static String normalizeName(String staff) {
        return staff.trim().toLowerCase(Locale.ROOT);
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
}
