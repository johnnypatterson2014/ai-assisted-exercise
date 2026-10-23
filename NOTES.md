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
