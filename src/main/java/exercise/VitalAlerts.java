package exercise;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exercise.Thresholds.Threshold;

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

    private static final Logger LOG = LoggerFactory.getLogger(VitalAlerts.class);

    /** Number of consecutive abnormal readings for one patient and vital that raises a WARNING. */
    static final int WARNING_STREAK = 3;

    private static final Comparator<Alert> ALERT_ORDER = Comparator.comparing(Alert::patientId)
            .thenComparing(Alert::type)
            .thenComparing(Alert::at)
            .thenComparing(Alert::severity);

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
        if (rawReadings == null) {
            throw new IllegalArgumentException("readings must not be null");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds must not be null");
        }

        // 1. Validate each row; keep bad rows as rejects with the reason, never abort the batch.
        List<RejectedReading> rejected = new ArrayList<>();
        Map<GroupKey, List<Reading>> groups = new HashMap<>();
        for (int i = 0; i < rawReadings.size(); i++) {
            try {
                Reading reading = parse(rawReadings.get(i));
                groups.computeIfAbsent(new GroupKey(reading.patientId(), reading.type()), k -> new ArrayList<>())
                        .add(reading);
            } catch (IllegalArgumentException e) {
                rejected.add(new RejectedReading(i, e.getMessage()));
            }
        }

        // 2. Per patient and vital: order by time (stable, so ties keep arrival order), then one pass for alerts.
        List<Alert> alerts = new ArrayList<>();
        for (Map.Entry<GroupKey, List<Reading>> entry : groups.entrySet()) {
            List<Reading> readings = entry.getValue();
            readings.sort(Comparator.comparing(Reading::timestamp));
            alerts.addAll(alertsFor(readings, thresholds.forType(entry.getKey().type())));
        }
        alerts.sort(ALERT_ORDER);

        long critical = alerts.stream().filter(a -> a.severity() == Severity.CRITICAL).count();
        LOG.info("evaluated {} readings: {} rejected, {} critical, {} warnings",
                rawReadings.size(), rejected.size(), critical, alerts.size() - critical);
        return new Result(List.copyOf(alerts), List.copyOf(rejected));
    }

    private record GroupKey(String patientId, VitalType type) {
    }

    /** Streak logic for one patient and vital; {@code readings} must already be sorted by time. */
    private static List<Alert> alertsFor(List<Reading> readings, Threshold threshold) {
        List<Alert> alerts = new ArrayList<>();
        int streak = 0;
        for (Reading reading : readings) {
            double value = reading.value();
            boolean critical = value < threshold.criticalLow() || value > threshold.criticalHigh();
            boolean abnormal = value < threshold.abnormalLow() || value > threshold.abnormalHigh();
            if (critical) {
                alerts.add(new Alert(Severity.CRITICAL, reading.patientId(), reading.type(), reading.timestamp(),
                        "%s %s outside critical range [%s, %s]".formatted(
                                reading.type(), value, threshold.criticalLow(), threshold.criticalHigh())));
            }
            streak = abnormal ? streak + 1 : 0;
            if (streak == WARNING_STREAK) {
                alerts.add(new Alert(Severity.WARNING, reading.patientId(), reading.type(), reading.timestamp(),
                        "%d consecutive abnormal %s readings".formatted(WARNING_STREAK, reading.type())));
            }
        }
        return alerts;
    }

    /** Longest piece of raw device text echoed in a reject reason, so garbage cannot bloat the result. */
    private static final int MAX_ECHO = 40;

    private static String echo(Object raw) {
        String text = String.valueOf(raw);
        return text.length() <= MAX_ECHO ? text : text.substring(0, MAX_ECHO) + "...";
    }

    /** Converts a raw row to a validated reading, or throws IllegalArgumentException with the reason. */
    private static Reading parse(RawReading raw) {
        if (raw == null) {
            throw new IllegalArgumentException("reading is null");
        }
        if (raw.patientId() == null || raw.patientId().isBlank()) {
            throw new IllegalArgumentException("patient id is missing");
        }
        VitalType type = VitalType.parse(raw.type())
                .orElseThrow(() -> new IllegalArgumentException("unknown vital type: " + echo(raw.type())));
        double value = parseValue(raw.value());
        if (raw.timestamp() == null || raw.timestamp().isBlank()) {
            throw new IllegalArgumentException("timestamp is missing");
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(raw.timestamp().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("timestamp is not ISO-8601 with a zone: " + echo(raw.timestamp()));
        }
        return new Reading(raw.patientId().trim(), type, value, timestamp);
    }

    private static double parseValue(Object value) {
        double parsed;
        if (value instanceof Number n) {
            parsed = n.doubleValue();
        } else if (value instanceof String s) {
            try {
                parsed = Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("value is not a number: " + echo(s));
            }
        } else if (value == null) {
            throw new IllegalArgumentException("value is missing");
        } else {
            throw new IllegalArgumentException("value has unsupported type " + value.getClass().getSimpleName());
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("value is not finite: " + parsed);
        }
        return parsed;
    }
}
