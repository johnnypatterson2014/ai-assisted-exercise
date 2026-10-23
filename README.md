# Patient record consolidation

Demographic records arrive from the EHR, the lab system and the CRM, and the same person appears in more than
one with different spelling and contact details. `PatientConsolidation.consolidate` returns one record per
patient and reports the records it could not use.

## How to run

```bash
mvn -q test
```

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/sample-patients.json"
```

Input is a JSON array of `{"sourceSystem","mrn","firstName","lastName","dateOfBirth","phone","email","updatedAt"}`;
extra fields are ignored. Output is JSON with `patients` (name, date of birth, phone, email, and the source
system and MRN pairs that fed it) and `rejected` (input index and reason). Pass `-` to read from stdin.
Malformed JSON prints one line and exits with code 1. The output holds names and contact details, so the
Runner is a development tool, not something to pipe into a log.

## Identity rule: when are two records the same person?

Two records are the same patient when their first name, last name and date of birth are equal after
normalisation: trim, collapse inner whitespace, fold case, strip diacritics (NFD, drop combining marks) and
fold the typographic apostrophe. So "Zoë O’Brien" and "ZOE O'BRIEN" match; "Zoe" and "Zoey" do not. There is
no fuzzy matching: a typo splits a patient rather than risking a merge of two people, and a split is visible
while a wrong merge is not. The date of birth must be `yyyy-MM-dd`; "05/01/1980" is rejected rather than
guessed, because a guessed day and month would feed the identity rule.

## Precedence rule: which details win?

- **Name and date of birth** come from the most trusted source: EHR, then LAB, then CRM (the `SourceSystem`
  declaration order), ties broken by the newest `updatedAt`. The EHR is the system of record for demographics.
- **Phone and email** are decided field by field: the newest record with a valid value wins, ties broken by
  trust. Contact details change often, so recency wins there. A newer record with no phone does not erase an
  older valid phone.
- **A record with no usable `updatedAt`** (missing or unparseable) is kept but ranks last.
- **Two records with the same source and MRN** are versions of one record: the newest decides which patient they
  belong to, the older is reported as superseded, and its valid contact details still take part in the merge.

## Design decisions

- **One public method**, `consolidate(List<RawRecord>)`, pure and side-effect free, with the two rules in its
  Javadoc and in two named comparators, `FOR_IDENTITY` and `FOR_CONTACT`, so the rules are one place to change.
- **Reject, do not abort.** A record that cannot be matched or traced (blank name, bad date of birth, unknown
  source, blank MRN) becomes a `RejectedRecord` with its input index and a reason. Only a null list fails fast.
- **Drop the field, keep the record.** An invalid phone (not ten NANP digits) or email (not local@domain.tld)
  becomes absent; the record still counts for identity and for its other field.
- **Deterministic output.** Patients sorted by last name, first name, date of birth; sources by system then MRN;
  rejects by index. Input order never changes the result.
- **Nothing identifying is logged.** One INFO line with counts only. Reject reasons echo at most 40 characters
  of raw input and are returned to the caller, never logged.

## Complexity

Validation and grouping are O(n) with hash maps keyed by source reference and by match key. Ranking inside a
patient's group is O(k log k) for a group of k records, so O(n log n) in the worst case and close to O(n) for
the realistic shape of two or three records per patient. Memory is O(n).

## Limitations

- No fuzzy matching: typos, nicknames, a middle initial in the first-name field, or "Mueller" for "Müller"
  split a patient.
- Two different people with the same name and date of birth merge silently; nothing in the output flags it.
- Phone and email checks are shape only; "0000000000" passes.
- No plausibility window on dates of birth, and a garbage `updatedAt` is silently treated as unknown.
- Batch only, everything in memory: ten million records is several gigabytes of heap.
- Bad rows are detected with exceptions, which is slow if most of a feed is garbage.

## Before production

- **Matching with confidence.** Keep exact matching as the auto-merge tier; add a probabilistic tier
  (Fellegi-Sunter or a trained model over name similarity, date of birth, phone, address) that produces
  candidate links for human review, with blocking by date of birth or last-name prefix so it scales.
- **Audit trail.** Persist every merge decision with the inputs that drove it, so a wrong merge can be undone.
- **Streaming.** Sort by match key at the source and stream one patient group at a time.
- **CI stages.** Build, unit tests, static analysis and a SonarQube quality gate, dependency scan
  (OWASP Dependency-Check or Snyk), then deploy behind a feature flag with rollback.
- **Coverage threshold.** JaCoCo in the build with a gate around 80 percent on line and branch coverage for
  `exercise`, as a signal rather than a goal; the current run is in NOTES.md.
- **Structured logging and metrics.** JSON logs with a correlation id per batch; counters for records, rejects
  and merges by source system; an alert on the reject rate and on the merge rate changing suddenly.
- **Sensitive data.** Names, dates of birth and contact details are protected health information under HIPAA
  and personal information under PIPEDA: encrypt at rest and in transit, keep them out of logs and error
  messages, restrict access by role, pseudonymise before analytics, and retain only what consolidation needs.
- **Testing in layers.** Keep these unit tests; add contract tests at the API boundary, an integration test
  against the real feeds, and a replay of recorded batches as a regression suite with known merge outcomes.
- **Data stewardship.** The trust order and the identity rule need sign-off from the data governance owner,
  and the rejected list needs a home where someone fixes the source records.

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
