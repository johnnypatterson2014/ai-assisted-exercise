# Exercise notes

Filled in during the first five minutes. Written before any code, read back to the interviewer.

## Problem in my words

- Devices send vital-sign readings: patient id, vital type (HR, SpO2, temperature), a numeric value, a timestamp.
- Readings arrive out of order and some are garbled (bad type, non-numeric value, missing fields).
- Output 1: a CRITICAL alert for any single reading outside a dangerous range.
- Output 2: a WARNING when a patient has three abnormal readings in a row (by time) for the same vital.
- Thresholds are mine to define; they must live in one place and be easy to change (config object, not literals).

## Assumptions to confirm (ask these)

1. BLOCKER: "In a row" means sorted by timestamp per patient and vital, consecutive, regardless of gaps in time? (No time window.)
2. Garbled readings: skip and report them with a reason, rather than fail the whole batch? Does a skipped reading break a streak or is it ignored?
3. Is a critical reading also abnormal, so it counts toward the three-in-a-row warning? (I assume yes.)
4. Unknown vital types such as "BP": reject as invalid, or ignore silently? (I assume reject with a reason.)
5. Batch processing of a list is enough for now; streaming (one reading at a time) is a follow-up?

## Edge cases

- Null or empty input: null list -> IllegalArgumentException; empty list -> no alerts, no rejects. Null element -> rejected.
- Invalid types or formats: type aliases "HR", "hr", "Heart-Rate", "SPO2" normalise to one enum; "BP" unknown; value "n/a" or null; timestamp missing or not ISO-8601; patient id blank.
- Duplicates: identical reading twice -> counts twice (devices retry); same patient+vital+timestamp with different values kept, sorted stably by arrival.
- Ordering and time: sort per patient and vital by timestamp; input order must not change the result; ties keep arrival order.
- Boundaries: value exactly on a threshold (define inclusive/exclusive once: abnormal if outside [low, high]); exactly 3 abnormal then normal; 4 abnormal in a row -> one warning per run, not two; two abnormal + one critical.
- Scale: 10M readings -> group by (patient, vital) is O(n), sort within group O(k log k); memory holds all readings. Beyond that: stream per patient with a 3-deep window, or partition by patient.

## Data model and public method

```java
enum VitalType { HEART_RATE, SPO2, TEMPERATURE }           // parse(String) handles aliases, case, hyphens
record Reading(String patientId, VitalType type, double value, Instant timestamp) {}
record Threshold(double abnormalLow, double abnormalHigh, double criticalLow, double criticalHigh) {}
record Thresholds(Map<VitalType, Threshold> byType) { static Thresholds defaults(); }  // one place to change
record Alert(Severity severity, String patientId, VitalType type, Instant at, String reason) {}
record RejectedReading(int index, String reason) {}
record Result(List<Alert> alerts, List<RejectedReading> rejected) {}

public static Result evaluate(List<Map<String,Object>> rawReadings, Thresholds thresholds)
```

Approach: parse each raw row to a Reading or a RejectedReading; group valid readings by (patientId, type);
sort each group by timestamp; one pass per group emitting CRITICAL per dangerous reading and one WARNING
when the abnormal streak reaches 3 (reset on normal). Alerts returned sorted by patient, type, time.
Time O(n log n) worst case (sorting within groups), space O(n).

Default thresholds (abnormal outside / critical outside):
HR 50-120 / 40-140 bpm; SpO2 92-100 / 85-100 %; temperature 36.0-38.5 / 35.0-40.0 C.

## Plan

1. Tests for the cases above (red).
2. Smallest clean implementation (green).
3. Harden: validation, error handling, adversarial inputs, Runner for ad hoc inputs.
4. Wrap up: README notes, production next steps.

## Decisions made on the way (and why)

- Interviewer answers not available; proceeding on the assumptions above: "in a row" = consecutive by timestamp per
  patient and vital, no time window; garbled rows are rejected with a reason and do not break a streak; critical
  counts as abnormal; unknown vital types are rejected; batch over a list, streaming is a follow-up.
- Abnormal means strictly outside [abnormalLow, abnormalHigh]; a value on the boundary is normal. Same rule for critical.
- A streak longer than 3 raises one warning (at the third reading), not one per extra reading. Keeps alert volume sane.
- Input is a list of RawReading (strings, value as Object) so Jackson can map the sample JSON directly; validation
  happens in evaluate, rejects carry the input index so a caller can find the bad row.
- Phase 3: garbled rows are detected by a private parse that throws IllegalArgumentException, caught per row and
  turned into a RejectedReading. Readable, but exceptions cost at scale if most rows are bad; revisit in hardening.
- Phase 3: WARNING_STREAK is a package-private constant on VitalAlerts, not part of Thresholds, since the statement
  fixes it at three; moving it into Thresholds is a one-line change if the interviewer wants it configurable.
- Phase 4: SpO2 has no upper bound (POSITIVE_INFINITY) so sensor noise above 100 is not an alert. Timestamps
  without a zone stay rejected: guessing UTC for a clinical alert is worse than a visible reject. Raw device text
  echoed in reject reasons is capped at 40 chars. Runner ignores unknown JSON fields so extra device data is harmless.
- Phase 5: README written above the template notes; JP.md still names sample-input.txt, the new sample is
  src/test/resources/sample-readings.json.
