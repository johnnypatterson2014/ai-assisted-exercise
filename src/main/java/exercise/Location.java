package exercise;

import java.time.ZoneId;

/** A hospital location. Its zone decides what "07:00 local" and "the week" mean for every unit in it. */
public record Location(String id, ZoneId zone) {
}
