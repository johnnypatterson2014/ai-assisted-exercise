# Exercise notes

Filled in during the first five minutes. Written before any code, read back to the interviewer.

## Problem in my words

- Input: a list of appointments, each with id, provider id, patient id, start and end time.
- A double booking is two appointments whose times overlap and that share the same provider OR the same patient.
- Output every such pair, saying which resource (provider or patient) is double booked.
- Invalid appointments (bad times, missing fields, end before start) are reported separately; the batch never fails.
- Batch over a list; input order must not matter.

## Assumptions to confirm (ask these)

1. BLOCKER: intervals are half-open, so one ending 09:30 and one starting 09:30 do NOT overlap? (Touching is fine.)
2. Same id twice: reject the second occurrence as a duplicate, or treat them as two appointments? (I assume reject.)
3. Two appointments share both provider and patient and overlap: one conflict per kind (two rows), or one row? (I assume one per kind so the report says why.)
4. Zero-length appointment (start == end): invalid, or valid and never conflicting? (I assume invalid: end must be after start.)
5. Output as pairs is enough? (Alternative: clusters per resource. I assume pairs, ordered by id.)

## Edge cases

- Null or empty: null list -> IllegalArgumentException; empty -> no conflicts, no rejects; null element -> rejected with its index.
- Invalid types or formats: blank id, provider or patient; start or end missing or not ISO-8601 with a zone; end before or equal to start (sample a4).
- Duplicates: same id twice -> second rejected; a pair is reported once, with ids in sorted order, never (a,b) and (b,a).
- Ordering and time: out-of-order input; offsets such as +02:00 normalised to UTC; same start time; one interval nested inside another; chain a-b and b-c overlap but a-c do not.
- Boundaries: single appointment; two identical intervals; touching intervals (end == start) not a conflict; one-second overlap is a conflict.
- Scale: 10M appointments -> group by resource, sort by start, sweep with the still-open set. O(n log n) plus output size; the output itself can be O(k^2) for one resource with k overlapping bookings, so cap or aggregate per resource at volume.

## Data model and public method

```java
record RawAppointment(String id, String providerId, String patientId, String start, String end) {}   // as received
record Appointment(String id, String providerId, String patientId, Instant start, Instant end) {}    // validated
enum ConflictKind { PROVIDER, PATIENT }
record Conflict(ConflictKind kind, String resourceId, String firstId, String secondId) {}            // firstId < secondId
record RejectedAppointment(int index, String id, String reason) {}
record Result(List<Conflict> conflicts, List<RejectedAppointment> rejected) {}

public static Result findDoubleBookings(List<RawAppointment> appointments)
```

Approach: validate each row (reject with reason, keep going); group valid appointments by provider and, separately,
by patient; sort each group by start then id; sweep keeping the appointments still open (end > current start) and
emit a conflict for each open one. Sort conflicts by kind, resource, first id, second id.
Time O(n log n) + O(conflicts); space O(n).

## Plan

1. Tests for the cases above (red).
2. Smallest clean implementation (green).
3. Harden: validation, error handling, adversarial inputs, Runner for ad hoc inputs.
4. Wrap up: README notes, production next steps.

## Decisions made on the way (and why)

- Interviewer confirmed all five assumptions: half-open intervals, duplicate ids rejected (second occurrence),
  one conflict per kind, zero-length appointments invalid, pairs ordered by id.
- Phase 2: rejects carry the appointment id as well as the index, since the id is what a scheduler would search for.
- Phase 3: ids are trimmed before use, so " dr-1" and "dr-1" are the same provider; blank after trim is a reject.
- Phase 3: the sweep keeps an "open" list and drops entries whose end is not after the current start. It is
  O(k * open) per group, which is the output size, so no worse than the pairs it has to report.
- Phase 4: ids stay case-sensitive (pinned by a test); normalising case is a data-quality rule for the feed, not
  for the detector. Raw text in reject reasons is capped at 40 chars. The sweep moved into its own method so the
  half-open rule is one line to point at. Runner ignores unknown JSON fields such as "room".
- Phase 4, left as is: exceptions for bad rows, lexicographic id order ("a10" before "a2"), no cap on the conflict
  count, open-list pruning with removeIf (same order as the pairs it reports).
