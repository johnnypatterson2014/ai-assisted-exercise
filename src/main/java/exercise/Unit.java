package exercise;

/** A care unit at a location with a number of floors to staff. Unit ids are unique across all locations. */
public record Unit(String id, Location location, int floors) {
}
