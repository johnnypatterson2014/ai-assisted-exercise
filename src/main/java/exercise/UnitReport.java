package exercise;

import java.time.ZoneId;

/** One unit's coverage for its zone's local week. */
public record UnitReport(ZoneId zone, String location, String unitId, CoverageReport report) {
}
