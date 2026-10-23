package exercise;

import java.time.DayOfWeek;
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

    private ShiftCoverage() {
    }

    /**
     * Checks coverage of every (weekday, shift, floor) slot for one week.
     * <p>
     * A person works at most one shift per day on any floor. Someone available for both shifts of a day is placed
     * on the day shift first, then on the night shift if the day is already covered. Staff are handed to floors in
     * ascending order, so a shortfall lands on the highest-numbered floors.
     *
     * @param floors       number of floors to staff; at least 1
     * @param availability staff availability rows; must not be null, may be empty; duplicates count once
     * @return the report, with uncovered slots ordered Monday to Sunday, DAY before NIGHT, floor ascending
     * @throws IllegalArgumentException if {@code floors} is below 1, the list is null, or a row is unusable
     */
    public static CoverageReport check(int floors, List<Availability> availability) {
        if (floors < 1) {
            throw new IllegalArgumentException("floors must be at least 1, got " + floors);
        }
        if (availability == null) {
            throw new IllegalArgumentException("availability must not be null");
        }

        Map<DayOfWeek, Map<String, EnumSet<Shift>>> shiftsByPersonByDay = new EnumMap<>(DayOfWeek.class);
        for (int i = 0; i < availability.size(); i++) {
            Availability row = availability.get(i);
            validate(row, i);
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
