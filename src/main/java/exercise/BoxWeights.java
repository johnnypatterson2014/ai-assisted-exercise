package exercise;

import java.util.List;

/**
 * Splits item weights into two boxes A and B so that A is strictly heavier than B, A has as few items as
 * possible and, among those, the greatest total weight. The class and method names are fixed by the challenge.
 */
public class BoxWeights {

    private BoxWeights() {
    }

    /**
     * Returns subset A: the fewest items whose total weight strictly exceeds the total of the remaining items,
     * and among such subsets the heaviest, in increasing order.
     *
     * @param arr the item weights, each 1 or more; must not be null and must not contain null
     * @return A's weights in increasing order; empty if {@code arr} is empty
     * @throws IllegalArgumentException if {@code arr} is null, contains null, or contains a weight below 1
     */
    public static List<Integer> minimalHeaviestSetA(List<Integer> arr) {
        throw new UnsupportedOperationException("not implemented");
    }
}
