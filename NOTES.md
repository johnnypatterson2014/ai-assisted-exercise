# Exercise notes

Filled in during the first five minutes. Written before any code, read back to the interviewer.

Source: G:\_apply.for.jobs\coding.challenge\challenge_001\instructions.md ("Optimizing Box Weights").

## Problem in my words

- Given item weights, split them into two boxes A and B: every item in exactly one box.
- Box A must be strictly heavier than box B.
- Among all such splits, A must have the fewest items; among those, the greatest total weight.
- Return A's weights in increasing order. The signature is fixed: `List<Integer> minimalHeaviestSetA(List<Integer> arr)`.
- Constraints: 1 <= n <= 100,000 items, each weight 1 to 100,000.

## Assumptions to confirm (ask these)

1. BLOCKER: the greedy is accepted as the answer: sort descending, take the heaviest items until A's sum exceeds
   the rest. For any size k the k heaviest items have the largest possible sum, so the first k that beats B is
   both minimal in size and maximal in weight. No search needed.
2. Equal weights are distinct items: [2, 2, 2, 2] gives A = [2, 2, 2] (6 > 2), duplicates kept in the output?
3. Empty input is outside the constraints (n >= 1). Return an empty list, or throw? (I assume an empty list:
   no valid A exists and the caller can tell.)
4. Weights outside the constraints (zero, negative, null element) are outside the harness; at our boundary,
   reject with IllegalArgumentException rather than compute something odd? (I assume reject.)
5. The returned list may be unmodifiable, and the input list is never modified?

## Edge cases

- Null or empty: null list -> IllegalArgumentException; empty list -> empty result; null element -> IllegalArgumentException.
- Invalid types or formats: zero or negative weight -> IllegalArgumentException (a zero never helps A, a negative breaks the greedy).
- Duplicates: [2, 2, 2, 2] -> [2, 2, 2]; [3, 3, 2] -> [3, 3]; all equal with odd count [1, 1, 1] -> [1, 1].
- Ordering and time: input order never matters ([5,3,2,4,1,2] and its reverse give [4, 5]); output ascending.
- Boundaries: one item -> that item; two equal items -> both (A must be strictly heavier); one item heavier
  than all the rest together -> just that item; sum of 100,000 weights of 100,000 is 10^10, so sums are long.
- Scale: n = 100,000 -> O(n log n) sort, trivial; at ten million still fine; the only trap is int overflow.

## Data model and public method

```java
public final class BoxWeights {
    /** Returns the minimal heaviest subset A, ascending; empty if arr is empty. */
    public static List<Integer> minimalHeaviestSetA(List<Integer> arr)
}
```

Approach: validate; copy and sort descending; total as long; walk from the heaviest adding to sumA until
sumA > total - sumA; return those items ascending. Time O(n log n), space O(n) for the sorted copy.

## Plan

1. Tests for the cases above (red).
2. Smallest clean implementation (green).
3. Harden: validation, error handling, adversarial inputs, Runner for the stdin format (n, then n lines).
4. Wrap up: README notes, production next steps.

## Decisions made on the way (and why)

- Plan agreed as written: greedy accepted, duplicates are distinct items, empty input gives an empty list,
  weights below 1 and null elements are rejected, input never modified.
- Phase 2: the class stays `public class BoxWeights` as given, with a private constructor added; the harness
  only calls the static method.
- Phase 3: the weights are copied into an int[] and sorted ascending, then A is taken from the top end down.
  That validates in the same pass as the copy, sorts primitives without boxing, and the slice from `start` to
  the end is already in increasing order, so no second sort or reversal.
- Phase 3: the result is unmodifiable. The challenge harness only reads it, and a caller cannot corrupt a
  returned answer by accident.
- Phase 4: the validation pass iterates the list instead of calling get(i), so a LinkedList input stays
  O(n log n); a 100,000-item LinkedList test with a two-second timeout pins it. `start` renamed `firstOfA`.
- Phase 4: a brute-force oracle over all subsets checks 300 random inputs of up to ten items against the greedy.
  That is the test that would catch a wrong greedy; the samples alone would not.
- Phase 4: Runner speaks the challenge's stdin format (count, then one weight per line) and prints one weight
  per line, so its output can be diffed against the sample output. Jackson is unused here and left in the pom.
- Phase 4, test failure: my review claimed box B is never empty for two or more items. False: [1, 1] needs
  both items in A. The property holds from three items up (the two heaviest of three positive weights beat
  the lightest), and the test now says so. The implementation was right; the reviewer was not.
- Phase 4, left as is: constraint limits (n and weight at most 100,000) are not enforced, since long sums make
  larger values safe; dual-pivot quicksort rather than a counting sort; validation stops at the first bad element.
- Phase 5: README written above the template notes; the Runner sample is src/test/resources/sample-case-0.txt.

## Coverage after phase 4 (JaCoCo 0.8.12, 146 tests, all green)

Run with no pom change:
`mvn -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`
Full report: `target/site/jacoco/index.html`. The template's `Placeholder` is left out; it is not part of the solution.

| Class | Lines | Branches |
|---|---|---|
| BoxWeights | 26 / 26 (100%) | 14 / 14 (100%) |
| Runner | 16 / 28 (57%) | 8 / 12 (67%) |
| **Total** | **42 / 54 (77.8%)** | **22 / 26 (84.6%)** |

Not covered, and why (talking points, not fixed):

- `Runner.main` lines 29-42: argument check, file versus stdin, the error exit. Only `run` is unit tested;
  main is exercised by hand from the terminal against sample-case-0.txt. A test that spawns the JVM, or a
  refactor that passes streams into a testable method, would close it.

Takeaway: the solution class is fully covered, including both validation messages and the empty-input path;
the only gap is the command-line shell, which a process-level test would cover before production.
