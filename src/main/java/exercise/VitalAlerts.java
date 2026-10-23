package exercise;

import java.time.Instant;
import java.util.List;

/**
 * Evaluates a batch of vital-sign readings and raises alerts.
 * <ul>
 *   <li>CRITICAL for any single reading outside the critical range of its vital.</li>
 *   <li>WARNING when a patient has three consecutive abnormal readings (ordered by timestamp) for the same vital.</li>
 * </ul>
 * Readings may arrive in any order and some may be garbled; garbled rows are rejected with a reason and do not
 * abort the batch.
 */
public final class VitalAlerts {

    private VitalAlerts() {
    }

    /** A reading as received from a device, before validation. {@code value} is a number or a string. */
    public record RawReading(String patientId, String type, Object value, String timestamp) {
    }

    /** A validated reading. */
    public record Reading(String patientId, VitalType type, double value, Instant timestamp) {
    }

    public enum Severity {
        CRITICAL,
        WARNING
    }

    /** An alert for one patient and vital; {@code at} is the timestamp of the reading that triggered it. */
    public record Alert(Severity severity, String patientId, VitalType type, Instant at, String reason) {
    }

    /** A reading that could not be evaluated; {@code index} is its position in the input list. */
    public record RejectedReading(int index, String reason) {
    }

    /** Alerts sorted by patient, vital, time and severity; rejects sorted by input index. */
    public record Result(List<Alert> alerts, List<RejectedReading> rejected) {
    }

    /**
     * Evaluates the readings against the thresholds.
     *
     * @param rawReadings the readings in any order; must not be null, may be empty
     * @param thresholds the thresholds to apply; must not be null
     * @return alerts and rejected readings, both in deterministic order
     * @throws IllegalArgumentException if either argument is null
     */
    public static Result evaluate(List<RawReading> rawReadings, Thresholds thresholds) {
        throw new UnsupportedOperationException("not implemented");
    }
}
