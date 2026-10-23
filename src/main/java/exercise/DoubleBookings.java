package exercise;

import java.time.Instant;
import java.util.List;

/**
 * Finds double bookings in a batch of appointments: two appointments whose times overlap and that share the
 * same provider or the same patient. Intervals are half-open, so an appointment ending at 09:30 and one
 * starting at 09:30 do not overlap. Invalid appointments are reported separately and never abort the batch.
 */
public final class DoubleBookings {

    private DoubleBookings() {
    }

    /** An appointment as received, before validation. All fields are raw strings. */
    public record RawAppointment(String id, String providerId, String patientId, String start, String end) {
    }

    /** A validated appointment; {@code end} is strictly after {@code start}. */
    public record Appointment(String id, String providerId, String patientId, Instant start, Instant end) {
    }

    /** Which shared resource is double booked. */
    public enum ConflictKind {
        PROVIDER,
        PATIENT
    }

    /** Two overlapping appointments sharing one resource; {@code firstId} sorts before {@code secondId}. */
    public record Conflict(ConflictKind kind, String resourceId, String firstId, String secondId) {
    }

    /** An appointment that could not be evaluated; {@code index} is its position in the input, {@code id} may be null. */
    public record RejectedAppointment(int index, String id, String reason) {
    }

    /** Conflicts sorted by kind, resource, first id, second id; rejects sorted by input index. */
    public record Result(List<Conflict> conflicts, List<RejectedAppointment> rejected) {
    }

    /**
     * Finds every pair of overlapping appointments that share a provider or a patient.
     *
     * @param appointments the appointments in any order; must not be null, may be empty
     * @return every conflict once, plus the appointments that were rejected and why, both in deterministic order
     * @throws IllegalArgumentException if {@code appointments} is null
     */
    public static Result findDoubleBookings(List<RawAppointment> appointments) {
        throw new UnsupportedOperationException("not implemented");
    }
}
