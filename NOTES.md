# Exercise notes: shift coverage for a hospital care unit

## Problem in my words

- The unit has N floors. Every floor needs 2 staff on the day shift (07:00-19:00) and 1 on the night shift (19:00-07:00 next day).
- Each staff member tells us which weekdays they can work and which shift (day or night) on those days.
- A night shift is identified by the day it starts on ("fri night" = Fri 19:00 to Sat 07:00).
- Decide whether every (day, shift) slot on every floor can be filled, and list the ones that cannot, with the shortfall.
- Staff are interchangeable across floors, so this is a counting problem per (day, shift), not a floor assignment.

## Assumptions to confirm (ask these)

1. **Blocker:** how many floors? Fixed for the unit, or an input to the method? (I will take it as an input.)
2. Is the schedule a single week keyed by weekday (Mon-Sun), repeating, rather than calendar dates?
3. One person works at most one shift per day, on one floor? Any rest rule (e.g. no night Fri then day Sat)? Assume none.
4. If someone is available for both shifts on the same day, may I place them where the shortfall is? (Day first, since it needs two.)
5. Report per (day, shift) with required vs available counts, or expanded per floor ("floor 3, Fri night")?

## Edge cases

- Null or empty input: null list, no staff at all (every slot short), a staff member with no availability, blank name.
- Invalid types or formats: floors < 1, unknown weekday or shift text, null record or null field in a record.
- Duplicates: the same person listed twice for the same day and shift counts once; same name with different case.
- Ordering and time: night shift belongs to its start day; Sun night runs into Mon morning; report ordered Mon..Sun, DAY before NIGHT.
- Boundaries: 1 floor; exactly enough staff (no report); one short; one person available day and night on the same day.
- Scale: ten million availability rows is one pass with 14 counters and a per-slot set of names; nothing is sorted or held per floor.

## Data model and public method

```java
public enum Shift { DAY, NIGHT }                                          // DAY 07-19, NIGHT 19-07 next day
public record Availability(String staff, DayOfWeek day, Shift shift) {}
public record UncoveredShift(DayOfWeek day, Shift shift, int floor, int required, int available) {}
public record CoverageReport(boolean fullyCovered, List<UncoveredShift> uncovered) {}

public static CoverageReport check(int floors, List<Availability> availability)   // O(n) time, O(staff) space
```

Approach: bucket distinct staff per day into day-only, night-only and both; required = floors x 2 (day) and floors x 1
(night); fill day from day-only then flexible, night from night-only then remaining flexible; staff are handed to
floors in ascending order, so a shortfall lands on the highest floors; report one line per short floor.

## Interviewer's answers

- 5 floors (kept as a method input; the Runner defaults to 5). Schedule is one week, keyed by weekday.
- One person works at most one shift per day, on any floor. Report expanded per floor.
- Not asked: flexible staff go to the day shift first. Names are compared trimmed and case-insensitively.

## Decisions made on the way (and why)

- A bad row (null, blank name, missing day or shift) fails the whole check with its position, rather than being
  skipped: a roster with a hole in it is not a safe basis for a staffing answer, and the message says what to fix.
- Per-day bucketing is name -> set of shifts, so the same person listed for both shifts counts as one flexible person.
- Hardening: floors capped at 500 (overflow and report size), rows read through an iterator (LinkedList-safe).
- Runner reads the problem statement's own shape (`name: days: shift; days: shift`, `floors = N`) rather than JSON,
  so a modified input can be typed by hand during the exercise; every parse error names its line.
- Change 1: the zone is the grouping key of the input (`Map<ZoneId, List<Availability>>`), not a column on the row.
  `Availability`, `check` and all existing tests stay untouched, and a person's rows for one zone stay one list.
- Change 1: a zone that has staff but no units is ignored; a unit in a zone with no roster is fully uncovered.
- Change 1: every row in every zone is validated up front (a loop, no allocation), but only zones with units are
  bucketed, so a bad row anywhere fails the run while unused rosters cost no memory.
- Change 1: `check` and `checkAll` share `validate`, `bucketByDay`, `assign` and `addShortFloors`; a unit is one
  bucket of floors in `handOut`, so the single-unit report is byte-for-byte the multi-unit report for one unit.
- Change 1 hardening: floors summed over all units are capped at ten million (4000 x 500 is a fifth of it); zones
  are validated in id order so the first error is the same on every run; the log line carries the duration.
- Change 1 Runner: `zone = <id>` starts a section, `unit <location> <id> = <floors>` lines and staff lines belong
  to it; a file without zone lines keeps the single-unit format and output. `sample-nightly.txt` has 5 zones.
- Unit and location ids are case-sensitive (they are identifiers); staff names are not (they are people).

## Change 1: 4000 units across 5 locations in 5 time zones, one nightly run

### The change in my words

- The input is no longer one unit: 4000 units, each with its own floor count, in 7 hospital locations.
- There are 5 time zones: 3 locations share the first zone, the other 4 zones have one location each.
- Day shift is still 07:00-19:00 *local* and night 19:00-07:00 local, so the week is the zone's local week.
- Staff give their availability per time zone, and can work any unit in that zone, across its locations.
  So the pool is shared per zone and a shortfall must be placed on some location and unit in that zone.
- One nightly batch produces the report for every unit; the output is per unit, per floor, as before.

### Assumptions to confirm (ask these)

1. Confirmed: availability is per zone; the pool is per zone, across its locations. One shift per local day per zone.
   Not asked: if one person lists two zones, their rows may overlap in real time; I count them in each zone as given.
2. When a zone's pool is short, which unit is reported short? I will fill locations, then units, in id order.
3. Is the floor count per unit an input (a list of units)? Can a unit have zero floors (closed) or appear twice?
4. The nightly run: the report is for the coming local week of each zone? One run after the last zone's midnight,
   or one run per zone? Where does the report go (file per location, a table)?

### Edge cases (delta only)

- Null or empty: no units, a zone with units but no staff, a zone with staff but no units (rows ignored or rejected?),
  a location with no units.
- Invalid: duplicate unit ids, the same location id with two different zones, zero or negative floors, an unknown
  zone id, a null zone on a row.
- Ordering and time: report ordered by zone id, location id, unit id, then as before; DST change days make a local
  day shift 11 or 13 hours (head count unchanged); Sunday night in one zone is already Monday in another, so the run
  must not cut a week at a single instant.
- Boundaries: one unit in a zone; 3 locations in one zone sharing one pool; 4000 x 14 = 56 000 floor-shift groups.
- Scale: rows are still one pass; demand per (zone, day, shift) is a sum over its units; memory is per person per day
  per zone, not per unit. The 5 zones are independent checks that can run in parallel in the nightly job.

### Data model and public method (delta)

```java
public record Location(String id, ZoneId zone) {}
public record Unit(String id, Location location, int floors) {}
public record UnitReport(ZoneId zone, String location, String unitId, CoverageReport report) {}
// Availability is unchanged; the zone is the key of the input map (see decisions)

public static List<UnitReport> checkAll(List<Unit> units, Map<ZoneId, List<Availability>> availabilityByZone)
```

Approach: group units by zone; per zone, demand per (day, shift) is the sum of floors x staffPerFloor over its units;
bucket people per (zone, day) as before; the same greedy fill; then hand the assigned head count to units in
(location id, unit id) order and to floors within each unit, reusing `addShortFloors`. The one-unit `check` stays
as a thin wrapper over the same code. The zone is carried on the report so "Friday night" names a local week.

## Coverage after phase 4 (JaCoCo 0.8.12, 49 tests, all green)

Command (no pom change; output under `target/`, which is ignored):

```
mvn -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
```

Report: `target/site/jacoco/index.html` (per-class HTML under `target/site/jacoco/exercise/`).

| Class | Lines covered | Branches covered |
|---|---|---|
| `ShiftCoverage` | 51 / 51 | 29 / 30 |
| `Runner` (`run` and its parse helpers) | 57 / 57 | 33 / 35 |
| `Shift` | 7 / 7 | n/a |
| Records: `Availability`, `UncoveredShift`, `CoverageReport` | 3 / 3 | n/a |
| **Total** | **118 / 118** | **62 / 65** |

`Placeholder` is the template's example and is left out; `Runner.main` is run by hand from the terminal (file and stdin
handling, `System.exit`) and is never counted.

Not covered, and why:

- `ShiftCoverage.java:85`: the `row.staff() == null` half of the blank-name guard. The tests use empty and whitespace
  names, never a null one. Closed by `check(1, List.of(new Availability(null, MONDAY, DAY)))` expecting "row 0: staff name".
- `Runner.java:64`: the `raw == null` guard for a null line in the list. `Files.readAllLines` never yields one. Closed by
  `Runner.run(Arrays.asList("floors = 1", null))` expecting it to be skipped like a blank line.
- `Runner.java:135`: the weekday loop running out without a match. The bad-day test uses "mo", which fails the length
  check before the loop. Closed by a line `monica: xyz: day` expecting "unknown day 'xyz'".

Takeaway: every line runs; the three missed branches are two null guards and one exhausted loop, each one test away and
left as talking points.

## Coverage after change 1, phase 4 (JaCoCo 0.8.12, 82 tests, all green)

Same command and report path as above. The 4000-unit smoke test ran in 127 ms (420 000 rows, 5 zones).

| Class | Lines covered | Branches covered |
|---|---|---|
| `ShiftCoverage` (with `Assigned`) | 145 / 146 | 74 / 78 |
| `Runner` (`run`, `Parser` and the parse helpers) | 87 / 87 | 47 / 49 |
| `Shift` | 7 / 7 | n/a |
| Records: `Availability`, `UncoveredShift`, `CoverageReport`, `Unit`, `Location`, `UnitReport` | 6 / 6 | n/a |
| **Total** | **245 / 246** | **121 / 127** |

`Placeholder` is the template's example and is left out; `Runner.main` is run by hand from the terminal and is never counted.

Not covered, and why:

- `ShiftCoverage.java:158`: the `unit.id() == null` half of the blank-id guard; the test uses a blank string. Closed by
  `checkAll(List.of(new Unit(null, VAN_GENERAL, 1)), Map.of())` expecting "unit 0: id must not be blank".
- `ShiftCoverage.java:165-166`: a null or blank location id is never given. Closed by a unit with
  `new Location(" ", PACIFIC)` expecting "unit gen-1: location id must not be blank".
- `ShiftCoverage.java:201`: the `row.staff() == null` guard, as before.
- `Runner.java:81`: the `raw == null` guard for a null line, as before.
- `Runner.java:196`: the weekday loop running out without a match, as before ("xyz" as a day).

Takeaway: the change added 127 covered lines and one new gap (a blank location id); the one missed line and six
missed branches are all null-or-blank guards and one exhausted loop, each one test away.
