package exercise;

import java.time.DayOfWeek;

/** A floor's shift that cannot be fully staffed: {@code available} of the {@code required} people are present. */
public record UncoveredShift(DayOfWeek day, Shift shift, int floor, int required, int available) {
}
