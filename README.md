# Optimizing box weights

Given item weights, `BoxWeights.minimalHeaviestSetA` splits them into two boxes so that box A is strictly
heavier than box B, has the fewest items possible and, among those, the greatest total weight. It returns A
in increasing order. The class and method names are fixed by the challenge
(`G:\_apply.for.jobs\coding.challenge\challenge_001\instructions.md`).

## How to run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/sample-case-0.txt"
```

Input is the challenge's custom-testing format: the first line is the count, then one weight per line.
Output is subset A, one weight per line, so it diffs against the sample output. Pass `-` to read from stdin.
Blank lines, padding and Windows line endings are tolerated; a non-integer line or a count that does not match
the weights prints one line and exits with code 1.

## Design decisions

- **Greedy, with the reason stated.** Sort descending and take the heaviest items until A's total strictly
  exceeds the rest. For any size k the k heaviest items have the largest possible sum, so the first size that
  wins is minimal, and for that size the set is the heaviest. No search over subsets.
- **Proved by an oracle, not just by samples.** A brute-force test enumerates every subset for 300 random
  inputs of up to ten items and compares. The samples alone would pass a wrong greedy.
- **Primitives and a long total.** The weights are copied into an `int[]` and sorted without boxing; the total
  is a `long` because 100,000 weights of 100,000 overflow an `int`. The slice from the stopping index to the end
  is already in increasing order, so there is no second sort.
- **Validate at the boundary.** A null list, a null element or a weight below one throws
  `IllegalArgumentException` with the index in the message. Empty input returns an empty list, the only case
  with an empty result.
- **Input never modified, output unmodifiable.** The caller's list is iterated once, never sorted in place,
  and the returned list cannot be changed by accident.
- **Nothing sensitive logged.** One INFO line with counts and sums.

## Complexity

One O(n) pass to validate and copy, one O(n log n) sort, one O(n) walk. Time O(n log n), space O(n) for the
primitive copy and the result. At the constraint limit of 100,000 items this is a few milliseconds.

## Limitations

- The constraint limits (n and each weight at most 100,000) are not enforced; larger values are handled
  correctly because the total is a `long`, so there was nothing to gain by rejecting them.
- Validation stops at the first bad element, so a caller fixing a bad feed sees one problem at a time.
- The JDK dual-pivot quicksort is used; a counting sort over the weight range would be O(n + range) and
  immune to adversarial orderings, at the cost of an extra array of that range.
- `Runner.main` has no automated test; only `run` does.

## Before production

- **Streaming input.** With weights bounded by 100,000, a histogram of counts per weight replaces the sorted
  array: one pass to count, one pass down the histogram to pick A. Memory becomes O(range), independent of n.
- **CI stages.** Build, unit tests, static analysis and a SonarQube quality gate, dependency scan
  (OWASP Dependency-Check or Snyk), then deploy behind a feature flag with rollback.
- **Coverage threshold.** JaCoCo in the build with a gate around 80 percent on line and branch coverage for
  `exercise`, as a signal rather than a goal; the current run is in NOTES.md.
- **Structured logging and metrics.** JSON logs with a correlation id per request; a histogram of input size
  and of evaluation time; an alert on rejected inputs.
- **Sensitive data.** Item weights are not personal data, but in a real fulfilment system the items carry
  order and customer identifiers: keep those out of this function's inputs and logs, and pass weights only.
- **Testing in layers.** Keep these unit tests and the brute-force oracle; add a process-level test for
  `Runner.main`, and a property-based test (jqwik) to replace the hand-rolled random loop.
- **API shape.** If this becomes a service, return A's weights and B's total alongside, so a caller does not
  recompute, and version the contract since the challenge fixes the signature.

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
