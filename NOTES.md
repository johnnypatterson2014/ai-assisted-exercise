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
