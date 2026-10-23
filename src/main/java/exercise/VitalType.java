package exercise;

import java.util.Optional;

/** The vital signs we know how to evaluate. Device feeds spell them many ways; {@link #parse} normalises. */
public enum VitalType {
    HEART_RATE,
    SPO2,
    TEMPERATURE;

    /**
     * Maps a device-supplied type label to a {@link VitalType}, ignoring case, spaces, hyphens and underscores.
     * Accepts e.g. "HR", "hr", "Heart-Rate", "heart_rate", "SPO2", "SpO2", "oxygen saturation", "TEMP", "temperature".
     *
     * @param label the raw label; may be null
     * @return the matching type, or empty if the label is null, blank or unknown
     */
    public static Optional<VitalType> parse(String label) {
        throw new UnsupportedOperationException("not implemented");
    }
}
