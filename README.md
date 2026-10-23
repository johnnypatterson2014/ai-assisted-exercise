# Double bookings

Given a list of appointments (id, provider, patient, start, end), `DoubleBookings.findDoubleBookings` reports
every pair that overlaps in time and shares the same provider or the same patient. Invalid appointments are
reported separately with a reason; the batch never fails.

## How to run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/sample-appointments.json"
```

Input is a JSON array of `{"id","providerId","patientId","start","end"}`; extra fields are ignored. Output is
JSON with `conflicts` (kind PROVIDER or PATIENT, the resource id, and the two appointment ids in sorted order)
and `rejected` (input index, id and reason). Pass `-` to read from stdin. Malformed JSON prints one line and
exits with code 1.

## Design decisions

- **One public method**, `findDoubleBookings(List<RawAppointment>)`, pure and side-effect free: easy to test,
  easy to call from a batch job, an API or a scheduler hook later.
- **Reject, do not abort.** A bad row becomes a `RejectedAppointment` with its input index, its id and a reason;
  the rest of the batch is still checked. Only a null list fails fast with `IllegalArgumentException`.
- **Half-open intervals.** An appointment ending at 09:30 and one starting at 09:30 do not overlap. End must be
  strictly after start, so a zero-length appointment is invalid.
- **Sweep per resource.** Valid appointments are grouped by provider and, separately, by patient. Each group is
  sorted by start then id and swept with an "open" list: entries whose end is not after the current start are
  dropped, and the current appointment pairs with everything still open. That reports every overlapping pair,
  including nested and three-way overlaps, and nothing else.
- **One conflict per kind.** Two appointments sharing both provider and patient produce a PROVIDER row and a
  PATIENT row, so the report says why.
- **Deterministic output.** Each pair appears once with ids in sorted order; conflicts are sorted by kind,
  resource, first id, second id; rejects by input index. Input order never changes the result.
- **Duplicate ids.** The first occurrence is kept, the second is rejected as a duplicate.
- **Ids are trimmed but case-sensitive.** Normalising case is a data-quality rule for the feed, not the detector.
- **Timestamps** must be ISO-8601 with a zone or offset; offsets are normalised to UTC before comparing.
- **Nothing identifying is logged.** One INFO line with counts only. Reject reasons echo at most 40 characters
  of raw input and are returned to the caller, never logged.

## Complexity

Validation is O(n). Grouping is O(n) with a hash map per kind. Sorting within groups is O(k log k) per group,
so O(n log n) in the worst case. The sweep costs the number of pairs it reports, so total time is
O(n log n + conflicts). Memory is O(n) for the valid list and the two groupings, plus the output.

## Limitations

- Batch only, everything in memory: ten million appointments is roughly a gigabyte of heap, and the seen-id
  set alone holds every id.
- The conflict count is not capped. One resource with k fully overlapping appointments yields k(k-1)/2 pairs.
- Ids sort lexicographically, so "a10" comes before "a2".
- Bad rows are detected with exceptions, which is slow if most of a feed is garbage.
- No plausibility window on dates, and no per-resource rules such as a provider who legitimately double books.

## Before production

- **Streaming.** Sort by resource and start at the source (a database index does it for free) and stream one
  group at a time; only the open list stays in memory.
- **Aggregation at volume.** Cap pairs per resource or report clusters (one row per resource and time window)
  so a badly overbooked provider does not produce millions of rows.
- **CI stages.** Build, unit tests, static analysis and a SonarQube quality gate, dependency scan
  (OWASP Dependency-Check or Snyk), then deploy behind a feature flag with rollback.
- **Coverage threshold.** JaCoCo in the build with a gate around 80 percent on line and branch coverage for
  `exercise`, as a signal rather than a goal.
- **Structured logging and metrics.** JSON logs with a correlation id per batch; counters for appointments,
  rejects and conflicts by kind; a histogram for run time; an alert on the reject rate.
- **Sensitive data.** Patient ids are identifiers under HIPAA and PIPEDA: encrypt at rest and in transit,
  pseudonymise ids before they leave the trusted zone, keep them out of logs and error messages, restrict
  access by role, and retain only what the check needs.
- **Testing in layers.** Keep these unit tests; add contract tests at the API boundary, an integration test
  against the real appointment store, and a replay of recorded schedules as a regression suite.
- **Business rules.** Confirm with the scheduling team whether some providers may overlap (group sessions) and
  whether a cancelled status should exclude an appointment from the check.

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
