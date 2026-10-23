package exercise;

import java.util.Locale;
import java.util.Optional;

/** The systems that send demographic records. Declaration order is the trust order: EHR is the system of record. */
public enum SourceSystem {
    EHR,
    LAB,
    CRM;

    /**
     * Maps a raw label to a source system, ignoring case and surrounding whitespace.
     *
     * @param label the raw label; may be null
     * @return the system, or empty if the label is null, blank or unknown
     */
    public static Optional<SourceSystem> parse(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String key = label.trim().toUpperCase(Locale.ROOT);
        for (SourceSystem system : values()) {
            if (system.name().equals(key)) {
                return Optional.of(system);
            }
        }
        return Optional.empty();
    }
}
