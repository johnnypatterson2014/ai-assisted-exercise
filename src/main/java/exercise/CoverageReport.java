package exercise;

import java.util.List;

/** Result of a coverage check: {@code uncovered} is empty exactly when {@code fullyCovered} is true. */
public record CoverageReport(boolean fullyCovered, List<UncoveredShift> uncovered) {
}
