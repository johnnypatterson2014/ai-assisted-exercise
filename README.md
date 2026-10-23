# Shift coverage for hospital care units

Given care units with their floors, grouped by hospital location and time zone, and each staff member's weekly
availability per time zone, decide whether every day and night shift on every floor can be staffed for one week,
and report every floor-shift that cannot. Day shift is 07:00-19:00 local and needs two people per floor; night
shift is 19:00-07:00 the next morning, needs one, and is keyed by the day it starts on. The plan, interviewer
answers, decisions and coverage numbers are in [NOTES.md](NOTES.md).

## How to run

```bash
mvn -q test
```

Single unit, roster in the problem statement's own shape:

```bash
mvn -q compile exec:java "-Dexec.args=src/test/resources/sample-input.txt"
```

```
floors = 5
monica: mon - fri: day shift
jeff: mon, tues: day shift; fri, sat: night shift
```

Nightly run, one section per time zone with its units and the staff available in that zone:

```bash
mvn -q compile exec:java "-Dexec.args=src/test/resources/sample-nightly.txt"
```

```
zone = America/Vancouver
unit van-general  gen-1 = 2
unit van-children ch-1  = 1
monica: mon - fri: day
zone = America/Toronto
unit tor-general  tor-1 = 1
```

Days are any prefix of three or more letters, ranges expand in weekday order and may wrap, "shift" is optional,
blank lines and `#` comments are skipped, and every parse error names its line. The words `zone`, `unit` and
`floors` are reserved at the start of a line. Pass `-` to read from stdin. Output is JSON: a `CoverageReport` for
the single-unit format, a list of `UnitReport` for the nightly format.

In STS: right-click a test class > Run As > JUnit Test; right-click `Runner` > Run As > Java Application with the
file path under Run Configurations > Arguments. Clear `target/classes` before a command-line build if STS has been
compiling into it, or Maven may reuse its class files.

## Design

- `ShiftCoverage.check(floors, availability)` checks one unit; `checkAll(units, availabilityByZone)` checks every
  unit of a nightly run. Both are static, pure, and return immutable records. File and console handling stay in
  `Runner`.
- `Shift` owns the staffing rule (`DAY` needs 2 per floor, `NIGHT` needs 1), so the number lives in one place.
- Staff give availability per time zone and may work any unit in any location of that zone, so each zone has one
  shared pool and the zone is the key of the input map rather than a column on each row. Coverage is a counting
  problem per zone, weekday and shift, not an assignment: each person is bucketed per day as day-only, night-only
  or flexible; the day shift takes day-only people then flexible ones, the night shift takes night-only people
  then what is left, so nobody works twice on one day.
- The assigned head count is handed to units in location id then unit id order, and to floors 1..n within a unit,
  so a shortfall lands on the last units of the last location and the highest floors. Every short floor becomes one
  `UncoveredShift` with required and available counts. Reports are ordered by zone id, location id, unit id, then
  Monday to Sunday, day before night, floor ascending, so output is deterministic. A single unit through `checkAll`
  gives exactly the report of `check`.
- Validation happens at the public boundary with `IllegalArgumentException` messages that name the unit or the
  zone and row. Every roster is validated before anything is bucketed, in zone id order, so the first error is the
  same on every run. A roster with a hole in it is not a safe basis for a staffing answer, so a bad row fails the run.
- Names are matched trimmed and case-insensitively; duplicate rows count once. Unit and location ids are
  case-sensitive identifiers. Floors are capped at 500 per unit and ten million per run. One INFO line per run
  carries counts and the duration, never a name or an id.

## Complexity

Time is O(r + u) for r availability rows and u units: rows are read twice (validate, bucket) and each unit costs 14
iterations plus its floors. Memory is one map entry per distinct person per day per zone, bounded by staff count
rather than row count, plus at most 14 lines per floor in the report. The smoke test in
`ShiftCoverageCheckAllTest` runs 4000 units of five floors across five zones against 420 000 rows in about 130 ms.

## Limitations

- Names are the identifier. Two different people with the same name merge; production needs a staff ID.
- A person listed in two zones counts in both. Zones with several locations make this the likeliest data error.
- Zone ids must match exactly: `America/Vancouver` and `Canada/Pacific` are two pools. Zones should come from one
  configuration table.
- The first bad row aborts the run. For large imports, collecting every bad row with its reason is friendlier.
- A shortfall always lands on the same units (alphabetical). A hospital will want a priority order per unit.
- One week is checked in isolation per zone: Sunday night ends at 07:00 Monday of the next week, correct only if
  weeks are checked back to back. DST change days make a local shift 11 or 13 hours; head count is unchanged.
- No rest rules, no per-floor skill mix, no partial shifts. The whole report is one list in memory: 4000 empty
  units is 280 000 lines, so the nightly job should write one file per location or stream.

## Before production

- CI stages: build, unit tests, static analysis with a SonarQube quality gate, dependency scan (OWASP or Snyk),
  then deploy behind a feature flag with rollback.
- Coverage threshold on the pipeline (lines and branches, around 80%) as a signal, not a goal; the open branches
  are listed in NOTES.md.
- Nightly job: one run per zone in parallel, scheduled after that zone's local day ends, writing a file per location
  and a summary; a correlation id per run in structured JSON logs; a counter of uncovered floor-shifts per location
  and a timing histogram so a short week pages someone before Monday morning.
- Sensitive data: staff names are personal data in a hospital context. Keep the staff ID in the row and the name
  only for display, never in logs or exception messages, encrypt rosters at rest, and restrict access by role.
- Configuration injected (staffing rule per shift, floor caps, unit priority order, zone table) rather than
  constants, so a unit that needs three nurses on a floor is a config change.

## Template notes

The project started as `exercise-template`: Java 21, JUnit 5, AssertJ, Jackson with java.time, SLF4J simple.
`Placeholder` and `PlaceholderTest` show the conventions and are left in place. Maven supplies the dependency
classpath; a plain `java -cp target/classes exercise.Runner` fails because Jackson and SLF4J are not on it. STS
imports the folder as an existing Maven project; `PROMPTS.md` holds the prompt playbook and `CHECKLIST.md` the
production-readiness checklist used for the closing walk-through.
