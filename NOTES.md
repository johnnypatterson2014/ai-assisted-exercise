# Exercise notes: log normalizer

## Problem in my words

- Several microservices each log in their own shape: different field names, different time formats, some lines not JSON.
- Turn a list of raw log lines into one consistent JSON shape: `time`, `user`, `message`, ordered by time.
- `time` and `user` are mandatory; `message` may be null.
- A line that cannot give a time and a user is rejected and logged; the batch continues.
- Personal data must not end up in our own application logs (no masking of the output for now).

## Confirmed with the interviewer

1. No user field: extract the user from the text (`LOGIN <user>`, `User <name> logged in`).
2. A line missing time or user is rejected and logged; the rest of the batch continues.
3. A time without a zone (Service B) is UTC; output time is an ISO-8601 UTC instant.
4. The three sample formats are the full set for now.
5. Our own logs and reject reasons never echo user or message; the normalized output still carries `user`.
6. A null list is treated as an empty list; nothing throws.
7. Plain text and JSON arrays or scalars are tolerated: same rules, never an exception.
8. An unparsable date is logged and the entry is kept with `time` as a blank string.
9. A numeric user is accepted (as text); unknown and extra fields are ignored.
10. Output is ordered by time.

## My reading of three answers (confirm or correct)

- "Ignore duplicates": no dedupe, identical lines are both kept. Two aliases in one line: first in a fixed order wins.
- Missing time is a reject (answer 2), unparsable time is kept with `""` (answer 8). Blank times sort last, ties keep input order.
- "Log the rejected line": WARN with the input position and the reason only, never the raw text, because of answer 5.
  The JSON output is the array of normalized logs; rejects are on `Result` and in the log.

## Edge cases

- Null or empty input: null or empty list gives an empty result; a null or blank line is rejected.
- Invalid types or formats: plain text without a known pattern, JSON array or scalar, unparsable date, numeric user, blank user.
- Duplicates: identical lines both kept; `time` and `timestamp` in the same line.
- Ordering and time: ascending by time; offsets such as `+02:00` become UTC; Service C's `1691236800` is 2023, so it sorts first.
- Boundaries: one line; every line rejected; message absent versus empty string; `ts` of 0 or negative.
- Scale: one parse per line plus one sort; at ten million lines, stream in, sort externally, write out incrementally.

## Data model and public method

```java
record NormalizedLog(String time, String user, String message) {}  // time: ISO-8601 UTC, or "" when unparsable
record Rejection(int index, String reason) {}                      // input position and reason, never the raw line
record Result(List<NormalizedLog> logs, List<Rejection> rejected) {}

public static Result normalize(List<String> logEntries)  // class LogNormalizer; O(n log n) time, O(n) space
```

Approach: parse each line as a JSON tree (plain text becomes the message), read each field through its alias list,
fall back to the two text patterns for the user, parse time by type (number is epoch seconds, text is ISO-8601 or
`yyyy-MM-dd HH:mm:ss` as UTC), reject when time or user is missing, then sort on the parsed instant.
The given `String LogNormalizer(List<String>)` stays as a thin wrapper that renders the logs as JSON.

## Decisions made on the way (and why)

- `time` is a `String` in the record, not an `Instant`, because an unparsable date must come out as a blank string.
  Sorting uses the parsed instant, not the text (ISO text with fractional seconds does not sort chronologically).
- One rule for "missing": a field that is absent, JSON null or blank has no value, and the next alias is tried.
  So a blank time is a reject (missing), while `"not a date"` is kept with a blank time (unparsable).
- A `ts` that is a decimal or a quoted number is unparsable, not guessed at; only whole epoch seconds are read.
- `normalizeToJson` replaces the starter's `LogNormalizer(...)` method name, which read like a constructor.
