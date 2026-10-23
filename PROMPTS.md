# Prompt playbook for the live exercise

Every prompt is small, bounded and announced first. The sentence you say before sending it is in italics.
The `/coding-exercise` skill runs the same phases with a stop after each one; these prompts are the manual
version and the words to use when steering inside a phase.

## Phase 1: understand (no code)

*"Before touching code I want a shared understanding. I'll have Claude restate the problem and list the
questions I should ask you, then we agree the edge cases."*

> Here is the problem statement:
> <paste>
> Do not write code yet. 1) Restate the problem in your own words in three to five bullets. 2) List the
> assumptions I need to confirm, phrased as questions for the interviewer, most important first, at most five.
> 3) List edge cases grouped as: null or empty, invalid types or formats, duplicates, ordering and time,
> boundaries, scale. 4) Propose the data model as Java records and one public method signature, with the
> target time complexity. Keep it under 40 lines and write it into NOTES.md.

## Phase 2: tests first

*"I write tests before the implementation so the AI has a contract to hit and I have a way to verify it."*

> Create src/test/java/exercise/<Name>Test.java with JUnit 5 and AssertJ tests for the cases in NOTES.md,
> organized in @Nested classes HappyPath, EdgeCases and InvalidInput, one behaviour per test, names that read
> as sentences. Also create the records and a class <Name> whose public method throws
> UnsupportedOperationException so the tests compile and fail. Do not implement the logic.

Run the tests yourself. Red is the expected result; say so.

## Phase 3: implement

*"Now the implementation, with constraints, and I'll review the diff like a pull request before it runs."*

> Implement <Name>.<method> so the tests pass. Constraints: Java 21, no new dependencies, validate inputs at
> the boundary and throw IllegalArgumentException with clear messages, deterministic ordering of results,
> O(n log n) or better, Javadoc on the public method, never log patient identifiers or contact details.
> Explain any decision that was not in the plan.

Review out loud (see the audit list below), accept, run `mvn -q test` yourself.

## Phase 4: harden

*"It works for the cases we listed. Now I look for the ones we missed."*

> Review the implementation as a senior reviewer. List bugs, edge cases the tests do not cover, naming or
> complexity problems, and what would fail with ten million records. Do not change code yet.

> Add eight adversarial inputs as test cases: some that must throw, some that must be rejected gracefully,
> some at boundaries. Then make the implementation handle them.

> Update Runner.run to parse the input file as <format> into <type> and print the result as JSON, so I can run
> the solution against modified inputs from the terminal.

## Phase 5: wrap up

*"Last five minutes: what it would take to ship this."*

> Write a README section: how to run, design decisions, complexity, limitations, and what I would add before
> production: CI pipeline stages, SonarQube quality gate, coverage threshold, structured logging and metrics,
> handling of sensitive data.

## If they hand you an existing codebase instead

> Summarize this repository: structure, entry points, how to build and run the tests. Change nothing.

Run the tests before touching anything. Then:

> Write a failing test that reproduces this bug: <description>. Do not fix it yet.

Fix, run, then ask for the review prompt from phase 4.

## The audit list (say these while reading a diff)

1. Does it do what the statement says, including the edge cases in NOTES.md?
2. Null, empty and invalid inputs handled at the boundary, with messages a caller can act on?
3. Every import and API real? Will it compile? (Hallucinated methods are the classic failure.)
4. Mutability: inputs not modified, results copied or immutable, ordering deterministic.
5. Complexity: no nested loop over the whole input where a sort or a map would do.
6. Exceptions: right type, not swallowed, no empty catch blocks.
7. Logging: one summary line, nothing sensitive (names, identifiers, contact details).
8. Tests assert real values, not just "no exception"; one behaviour per test.
9. Names and structure fit the rest of the project.
10. Anything I would ask a human to change in review? Say it, then ask Claude for exactly that.

## Narration lines

- Opening: "My setup: Spring Tool Suite on the left is the editor and JUnit runner, Claude Code on the right
  is the agent working in this project folder, the terminal at the bottom runs Maven. Claude edits files, I
  review every diff before it is applied, I run the tests myself, and the chat history stays as it is."
- Before a prompt: "I'm asking for X, bounded by Y, because Z."
- Reading a diff: "Accepting this part; this part I'd push back on because..."
- When the AI is wrong: "That's wrong because... I'll tell it exactly what to change rather than re-rolling."
- When it is right: "This matches the plan; the tests will confirm it."
- Choosing to type yourself: "This is a two-line change, faster to type than to prompt."
