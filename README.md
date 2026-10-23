# Vital-sign alerts

Devices send vital-sign readings (patient id, type, value, timestamp), out of order and sometimes garbled.
`VitalAlerts.evaluate` raises a CRITICAL alert for any single dangerous reading and a WARNING when a patient
has three abnormal readings in a row for the same vital. Thresholds live in `Thresholds` and are easy to change.

## How to run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/sample-readings.json"
```

Input is a JSON array of `{"patientId","type","value","timestamp"}`; extra fields are ignored. Output is JSON
with `alerts` (severity, patient, vital, time, reason) and `rejected` (input index and reason). Pass `-` to
read from stdin. Malformed JSON prints one line and exits with code 1.

## Design decisions

- **One public method**, `evaluate(List<RawReading>, Thresholds)`, pure and side-effect free: easy to test,
  easy to call from a batch job, an API or a stream consumer later.
- **Reject, do not abort.** A garbled row becomes a `RejectedReading` with its input index and a reason;
  the rest of the batch is still evaluated. A null list or null thresholds fail fast with
  `IllegalArgumentException`.
- **Order by time, not arrival.** Readings are grouped by patient and vital and sorted by timestamp with a
  stable sort, so ties keep arrival order and the result does not depend on input order.
- **Thresholds in one place.** `Thresholds.defaults()` holds the numbers; `with(type, threshold)` returns a
  modified copy; the record validates that the critical range contains the abnormal range. Abnormal and
  critical are strictly outside their ranges, so a value on the boundary is normal. SpO2 has no upper bound,
  so a sensor reporting 100.5 is noise, not an alert.
- **Streak semantics.** Critical readings count as abnormal. A normal reading resets the streak. A rejected
  reading is skipped, not a reset. A streak longer than three raises one warning, at the third reading.
- **Vital type aliases** ("HR", "Heart-Rate", "SpO2", "oxygen saturation", "TEMP") normalise through one
  table in `VitalType`; adding a spelling is one line.
- **Timestamps** must be ISO-8601 with a zone or offset; a timestamp with no zone is rejected rather than
  assumed UTC, because guessing wrong on a clinical alert is worse than a visible reject.
- **Nothing identifying is logged.** One INFO line with counts only. Reject reasons echo at most 40 characters
  of raw device text and are returned to the caller, never logged.

## Complexity

Validation and grouping are O(n) with a hash map keyed by patient and vital. Sorting within groups is
O(k log k) per group, so O(n log n) in the worst case of one huge group and close to O(n) for the realistic
shape of many small groups. The streak pass is O(n). Memory is O(n): the input, one `Reading` per valid row,
and the alerts.

## Limitations

- Batch only: everything is held in memory. Ten million readings is about a gigabyte of heap.
- The warning streak length is a constant on `VitalAlerts`, not part of `Thresholds`.
- Duplicate retransmissions count toward a streak; three identical rows raise a warning.
- Bad rows are detected with exceptions, which is slow if most of a feed is garbage.
- Thresholds are adult defaults with no per-patient or per-age override, and timestamps are not sanity-checked
  against a plausible window.

## Before production

- **Streaming.** Consume readings per patient from a queue, keep the last three readings per patient and
  vital as state, and emit alerts as they happen; or partition by patient and run the batch per partition.
- **Configuration.** Load `Thresholds` and the streak length from configuration or a database with an audit
  trail, since clinicians will change them.
- **CI stages.** Build, unit tests, static analysis and a SonarQube quality gate, dependency scan
  (OWASP Dependency-Check or Snyk), then deploy behind a feature flag with rollback.
- **Coverage threshold.** JaCoCo in the build with a gate around 80 percent on line and branch coverage for
  `exercise`, as a signal rather than a goal.
- **Structured logging and metrics.** JSON logs with a correlation id per batch; counters for readings,
  rejects, criticals and warnings; a histogram for evaluation time; an alert on the reject rate.
- **Sensitive data.** Patient ids are identifiers under HIPAA and PIPEDA: encrypt at rest and in transit,
  pseudonymise ids before they leave the trusted zone, keep them out of logs and error messages, restrict
  access by role, and retain only what the alerting needs.
- **Testing in layers.** Keep these unit tests; add contract tests at the API boundary, an integration test
  against the real queue, and a replay of recorded production feeds as a regression suite.
- **Clinical review.** The default thresholds need sign-off from a clinician before they alert on real patients.

---

# exercise-live (clean copy of exercise-template)

A ready-to-go Java 21 Maven project for a live, AI-assisted coding exercise. Everything compiles and the
tests are green before the interview starts, so the first minutes go to the problem, not to setup.

## What is in it

| File | Purpose |
|---|---|
| `pom.xml` | Java 21, JUnit 5, AssertJ, Jackson (JSON with java.time), SLF4J simple logging |
| `src/main/java/exercise/Placeholder.java` | Placeholder showing the conventions: validate at the boundary, return a record, log one summary line |
| `src/main/java/exercise/Runner.java` | Command-line runner for "modified inputs": reads a file or stdin, prints JSON |
| `src/test/java/exercise/PlaceholderTest.java` | Test layout: `@Nested` HappyPath, EdgeCases, InvalidInput, plus a parameterized example |
| `NOTES.md` | Filled live: restated problem, assumptions, edge cases, plan |
| `PROMPTS.md` | The prompt playbook and the narration lines for each phase |
| `CHECKLIST.md` | The production-readiness checklist to walk through at the end |

## Import into Spring Tool Suite

File > Import > Maven > Existing Maven Projects > root directory = this folder > Finish. STS reads the
`pom.xml` and downloads nothing new once Maven has run once from the command line.

Check once: Window > Preferences > General > Workspace > "Refresh using native hooks or polling" is ticked,
so files that Claude Code writes appear in STS without pressing F5.

## Run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/sample-input.txt"
```

Maven supplies the dependency classpath; a plain `java -cp target/classes exercise.Runner ...` fails with
`NoClassDefFoundError: com/fasterxml/jackson/databind/Module` because the Jackson and SLF4J jars are not on it.
From PowerShell quote the whole option: `mvn -q compile exec:java "-Dexec.args=src/test/resources/sample-input.txt"`.

In STS: right-click `PlaceholderTest` > Run As > JUnit Test, or right-click `Runner` > Run As > Java Application
(Run Configurations > Arguments to pass the input file); STS builds the classpath from the POM itself.

## Live layout

Spring Tool Suite on the left (editor and JUnit view), Claude Code on the right working in this folder,
a terminal at the bottom (the STS Terminal view, or Windows Terminal snapped under STS) for `mvn -q test` and
the Runner. Full screen is shared.
