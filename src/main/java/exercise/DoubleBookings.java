package exercise;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds double bookings in a batch of appointments: two appointments whose times overlap and that share the
 * same provider or the same patient. Intervals are half-open, so an appointment ending at 09:30 and one
 * starting at 09:30 do not overlap. Invalid appointments are reported separately and never abort the batch.
 */
public final class DoubleBookings {

    private static final Logger LOG = LoggerFactory.getLogger(DoubleBookings.class);

    private static final Comparator<Conflict> CONFLICT_ORDER = Comparator.comparing(Conflict::kind)
            .thenComparing(Conflict::resourceId)
            .thenComparing(Conflict::firstId)
            .thenComparing(Conflict::secondId);

    private static final Comparator<Appointment> BY_START_THEN_ID = Comparator.comparing(Appointment::start)
            .thenComparing(Appointment::id);

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
        if (appointments == null) {
            throw new IllegalArgumentException("appointments must not be null");
        }

        // 1. Validate each row; keep bad rows as rejects with the reason, never abort the batch.
        List<Appointment> valid = new ArrayList<>();
        List<RejectedAppointment> rejected = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < appointments.size(); i++) {
            RawAppointment raw = appointments.get(i);
            try {
                Appointment appointment = parse(raw);
                if (!seenIds.add(appointment.id())) {
                    throw new IllegalArgumentException("duplicate id");
                }
                valid.add(appointment);
            } catch (IllegalArgumentException e) {
                rejected.add(new RejectedAppointment(i, raw == null ? null : raw.id(), e.getMessage()));
            }
        }

        // 2. Overlaps per provider, then per patient.
        List<Conflict> conflicts = new ArrayList<>();
        conflicts.addAll(conflictsFor(ConflictKind.PROVIDER, valid, Appointment::providerId));
        conflicts.addAll(conflictsFor(ConflictKind.PATIENT, valid, Appointment::patientId));
        conflicts.sort(CONFLICT_ORDER);

        LOG.info("checked {} appointments: {} rejected, {} conflicts", appointments.size(), rejected.size(),
                conflicts.size());
        return new Result(List.copyOf(conflicts), List.copyOf(rejected));
    }

    /**
     * Sweep per resource: sort by start, keep the appointments still open (end after the current start) and
     * pair the current one with each of them. Half-open intervals, so an appointment that ends exactly when the
     * current one starts has already been closed.
     */
    private static List<Conflict> conflictsFor(ConflictKind kind, List<Appointment> appointments,
            Function<Appointment, String> resourceId) {
        Map<String, List<Appointment>> byResource = new HashMap<>();
        for (Appointment appointment : appointments) {
            byResource.computeIfAbsent(resourceId.apply(appointment), k -> new ArrayList<>()).add(appointment);
        }

        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<Appointment>> entry : byResource.entrySet()) {
            List<Appointment> group = entry.getValue();
            group.sort(BY_START_THEN_ID);
            List<Appointment> open = new ArrayList<>();
            for (Appointment current : group) {
                open.removeIf(earlier -> !earlier.end().isAfter(current.start()));
                for (Appointment earlier : open) {
                    conflicts.add(conflict(kind, entry.getKey(), earlier, current));
                }
                open.add(current);
            }
        }
        return conflicts;
    }

    private static Conflict conflict(ConflictKind kind, String resourceId, Appointment a, Appointment b) {
        return a.id().compareTo(b.id()) <= 0
                ? new Conflict(kind, resourceId, a.id(), b.id())
                : new Conflict(kind, resourceId, b.id(), a.id());
    }

    /** Converts a raw row to a validated appointment, or throws IllegalArgumentException with the reason. */
    private static Appointment parse(RawAppointment raw) {
        if (raw == null) {
            throw new IllegalArgumentException("appointment is null");
        }
        String id = required("id", raw.id());
        String providerId = required("provider id", raw.providerId());
        String patientId = required("patient id", raw.patientId());
        Instant start = parseInstant("start", raw.start());
        Instant end = parseInstant("end", raw.end());
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
        return new Appointment(id, providerId, patientId, start, end);
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value.trim();
    }

    private static Instant parseInstant(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " is not ISO-8601 with a zone: " + value.trim());
        }
    }
}
