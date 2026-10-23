package exercise;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The vital signs we know how to evaluate. Device feeds spell them many ways; {@link #parse} normalises. */
public enum VitalType {
    HEART_RATE,
    SPO2,
    TEMPERATURE;

    /** Known labels after normalisation (upper case, letters and digits only). Add aliases here. */
    private static final Map<String, VitalType> ALIASES = Map.ofEntries(
            Map.entry("HR", HEART_RATE),
            Map.entry("HEARTRATE", HEART_RATE),
            Map.entry("PULSE", HEART_RATE),
            Map.entry("SPO2", SPO2),
            Map.entry("O2SAT", SPO2),
            Map.entry("OXYGENSATURATION", SPO2),
            Map.entry("TEMP", TEMPERATURE),
            Map.entry("TEMPERATURE", TEMPERATURE));

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");

    /**
     * Maps a device-supplied type label to a {@link VitalType}, ignoring case, spaces, hyphens and underscores.
     * Accepts e.g. "HR", "hr", "Heart-Rate", "heart_rate", "SPO2", "SpO2", "oxygen saturation", "TEMP", "temperature".
     *
     * @param label the raw label; may be null
     * @return the matching type, or empty if the label is null, blank or unknown
     */
    public static Optional<VitalType> parse(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String key = NOT_ALPHANUMERIC.matcher(label).replaceAll("").toUpperCase(Locale.ROOT);
        return Optional.ofNullable(ALIASES.get(key));
    }
}
