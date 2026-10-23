package exercise;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-vital alerting thresholds. This is the one place to change them.
 * A reading is abnormal when strictly outside [abnormalLow, abnormalHigh] and critical when strictly outside
 * [criticalLow, criticalHigh]; the critical range always contains the abnormal range, so critical implies abnormal.
 *
 * @param byType one entry for every {@link VitalType}
 */
public record Thresholds(Map<VitalType, Threshold> byType) {

    /** Thresholds for one vital. Must satisfy criticalLow <= abnormalLow < abnormalHigh <= criticalHigh. */
    public record Threshold(double abnormalLow, double abnormalHigh, double criticalLow, double criticalHigh) {
    }

    /** Clinically plausible defaults for an adult: HR in bpm, SpO2 in %, temperature in degrees Celsius. */
    public static Thresholds defaults() {
        Map<VitalType, Threshold> map = new EnumMap<>(VitalType.class);
        map.put(VitalType.HEART_RATE, new Threshold(50, 120, 40, 140));
        map.put(VitalType.SPO2, new Threshold(92, 100, 85, 100));
        map.put(VitalType.TEMPERATURE, new Threshold(36.0, 38.5, 35.0, 40.0));
        return new Thresholds(map);
    }

    /** Returns a copy with the threshold for one vital replaced. */
    public Thresholds with(VitalType type, Threshold threshold) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The threshold for the given vital. */
    public Threshold forType(VitalType type) {
        return byType.get(type);
    }
}
