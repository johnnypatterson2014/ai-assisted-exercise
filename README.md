# Shift coverage for a hospital care unit

Given the unit's floors and each staff member's weekly availability, decide whether every day and night shift on
every floor can be staffed for one week, and report every floor-shift that cannot. Day shift is 07:00-19:00 and
needs two people per floor; night shift is 19:00-07:00 the next morning, needs one, and is keyed by the day it
starts on. The plan, interviewer answers, decisions and coverage numbers are in [NOTES.md](NOTES.md).

## How to run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java "-Dexec.args=src/test/resources/sample-input.txt"
```

The Runner reads a roster in the problem statement's own shape and prints the report as JSON:

```
floors = 5
monica: mon - fri: day shift
jeff: mon, tues: day shift; fri, sat: night shift
```

Days are any prefix of three or more letters, ranges expand in weekday order and may wrap, "shift" is optional,
blank lines and `#` comments are skipped, and every parse error names its line. Pass `-` to read from stdin.
In STS: right-click `ShiftCoverageTest` > Run As > JUnit Test; right-click `Runner` > Run As > Java Application with
the file path under Run Configurations > Arguments.

## Design

- `ShiftCoverage.check(floors, availability)` is one static, pure method returning an immutable `CoverageReport`.
  File and console handling stay in `Runner`.
- `Shift` owns the staffing rule (`DAY` needs 2 per floor, `NIGHT` needs 1), so the number lives in one place.
- Staff are interchangeable across floors, so coverage is a counting problem per weekday and shift, not an
  assignment. Each person is bucketed per day as day-only, night-only or flexible; the day shift takes day-only
  people then flexible ones, the night shift takes night-only people then what is left. Flexible people therefore
  never work twice on one day.
- The report is expanded per floor: staff are handed to floors 1..n in order, so a shortfall lands on the
  highest-numbered floors, and every short floor becomes one `UncoveredShift` with required and available counts.
  Order is Monday to Sunday, day before night, floor ascending, so output is deterministic.
- Validation happens at the public boundary with `IllegalArgumentException` messages that name the row and the
  field. A roster with a hole in it is not a safe basis for a staffing answer, so a bad row fails the whole check.
- Names are matched trimmed and case-insensitively; duplicate rows count once. One INFO log line carries counts
  only, never a name.

## Complexity

Time is O(n + f) for n availability rows and f floors; the per-floor loop is 14 iterations per floor. Memory is
one map entry per distinct person per day, bounded by staff count rather than row count, plus at most 14 x f
report lines. Floors are capped at 500 to keep the arithmetic and the report bounded.

## Limitations

- Names are the identifier. Two different people with the same name merge; production needs a staff ID.
- The first bad row aborts the check. For large imports, collecting every bad row with its reason is friendlier.
- One week is checked in isolation: Sunday night ends at 07:00 Monday of the next week, which is only correct if
  weeks are checked back to back.
- No rest rules (for example no night Friday then day Saturday), no per-floor skill mix, no partial shifts.
- The input is a `List` held in memory. Ten million rows for a few thousand staff is fine; ten million distinct
  people would want a streaming input and a byte mask per person instead of an `EnumSet`.

## Before production

- CI stages: build, unit tests, static analysis with a SonarQube quality gate, dependency scan (OWASP or Snyk),
  then deploy behind a feature flag with rollback.
- Coverage threshold on the pipeline (lines and branches, around 80%) as a signal, not a goal; the three open
  branches are listed in NOTES.md.
- Structured JSON logging with a correlation id per check; a counter of uncovered floor-shifts and a timing
  histogram per run so a bad week pages someone before Monday morning.
- Sensitive data: staff names are personal data in a hospital context. Keep the staff ID in the row and the name
  only for display, never in logs or exception messages, encrypt rosters at rest, and restrict access by role.
- Configuration injected (floors, staffing rule per shift, floor cap) rather than constants, so a unit that needs
  three nurses on a floor is a config change.

## Template notes

The project started as `exercise-template`: Java 21, JUnit 5, AssertJ, Jackson with java.time, SLF4J simple.
`Placeholder` and `PlaceholderTest` show the conventions and are left in place. Maven supplies the dependency
classpath; a plain `java -cp target/classes exercise.Runner` fails because Jackson and SLF4J are not on it. STS
imports the folder as an existing Maven project; `PROMPTS.md` holds the prompt playbook and `CHECKLIST.md` the
production-readiness checklist used for the closing walk-through.
