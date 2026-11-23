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

## The exercise: log normalizer

Logs from several microservices arrive in different shapes. `LogNormalizer.normalize` turns them into one
shape (`time`, `user`, `message`) ordered by time; `normalizeToJson` renders that as a JSON array. The plan,
the interviewer's answers and the coverage report are in `NOTES.md`.

### How to run

```bash
mvn -q test
mvn -q compile exec:java "-Dexec.args=src/test/resources/sample-logs.txt"
```

The input file has one raw log entry per line. The Runner prints the normalized logs and the rejected lines
(zero-based position and reason). Copy `sample-logs.txt` and edit it to try other inputs.

### Design decisions

- **Aliases, not per-service parsers.** Each output field has a list of source names in order of precedence
  (`timestamp|time|ts`, `user|username`, `msg|message|log`). A new service is usually one more alias.
- **Reject, do not abort.** Time and user are mandatory. A line without either becomes a `Rejection` with its
  position and a reason, and the batch continues. Nothing is thrown for bad input; a null list is an empty list.
- **Missing is not the same as unparsable.** A missing time rejects the line; a time that is present but cannot
  be read keeps the entry with a blank time, sorted last. This follows the interviewer's answers.
- **User from the text** when there is no user field: `LOGIN <user>` and `User <name> logged in`, whole-text
  matches only.
- **Times** are epoch seconds (milliseconds from 1e11, my assumption), ISO-8601 with an offset, or
  `yyyy-MM-dd HH:mm:ss` read as UTC. Dates are parsed strictly. Output is an ISO-8601 UTC instant.
- **Nothing personal in our own logs.** Log lines and reject reasons carry positions, counts and fixed reasons,
  never a user, a message or a raw line. The output is not masked, as agreed for now.

### Complexity

O(n log n) time for the sort, O(n) for everything else; O(n) space. One JSON parse per line.

### Limitations

- The whole batch is held in memory and `normalizeToJson` builds a single String, so it is not for ten million
  lines as it stands. Sorting needs the full batch, which also rules out streaming.
- One WARN per rejected or unparsable line; a bad batch floods the log.
- Reject reasons are free text, not an enum a caller can switch on.
- Field names and the two text patterns are case-sensitive and fixed in code, not configuration.
- An ISO time without an offset, a decimal epoch and a quoted epoch are unparsable. There is no plausibility
  window on times, so a far-future epoch is accepted.
- The `message` still contains the user name where the source put it there (`LOGIN carol`).

### Before production

- **CI stages:** build, unit tests, static analysis with a SonarQube quality gate, dependency scan (OWASP
  Dependency-Check or the platform's equivalent), then deploy behind a flag with a rollback path.
- **Coverage threshold:** JaCoCo in the build with a gate at 90% of lines and branches on new code. Today it
  is 91.3% of lines and 91.7% of branches; the known gaps are listed in `NOTES.md`, starting with a `RunnerTest`.
- **Structured logging and metrics:** JSON log lines with a correlation id per batch; counters for normalized,
  rejected by reason and unparsable times, and a timer per batch. Per-line detail moves to DEBUG.
- **Sensitive data:** mask or tokenize the user and scrub names out of messages before the output leaves the
  service, encrypt it in transit and at rest, restrict who can read it, and set a retention period.
- **Scale:** read and write as streams (Jackson `JsonParser` and `SequenceWriter`), sort externally or leave
  ordering to the downstream store, and parse lines in parallel since each line is independent.
- **Configuration:** aliases, text patterns and the millisecond threshold injected per service, with a
  contract test per service format so a format change fails in CI and not in production.
