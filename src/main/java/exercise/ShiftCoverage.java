package exercise;

import java.util.List;

/** Checks whether one week of day and night shifts on every floor can be covered by the available staff. */
public final class ShiftCoverage {

    private ShiftCoverage() {
    }

    /**
     * Checks coverage of every (weekday, shift, floor) slot for one week.
     *
     * @param floors       number of floors to staff; at least 1
     * @param availability staff availability rows; must not be null, may be empty
     * @return the report, with uncovered slots ordered Monday to Sunday, DAY before NIGHT, floor ascending
     * @throws IllegalArgumentException if {@code floors} is below 1, the list is null, or a row is unusable
     */
    public static CoverageReport check(int floors, List<Availability> availability) {
        throw new UnsupportedOperationException("not implemented");
    }
}
