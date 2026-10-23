package exercise;

import java.time.DayOfWeek;

/** One row of staff availability: this person can work this shift on this weekday. */
public record Availability(String staff, DayOfWeek day, Shift shift) {
}
