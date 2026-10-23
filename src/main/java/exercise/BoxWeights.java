package exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits item weights into two boxes A and B so that A is strictly heavier than B, A has as few items as
 * possible and, among those, the greatest total weight. The class and method names are fixed by the challenge.
 */
public class BoxWeights {

    private static final Logger LOG = LoggerFactory.getLogger(BoxWeights.class);

    private BoxWeights() {
    }

    /**
     * Returns subset A: the fewest items whose total weight strictly exceeds the total of the remaining items,
     * and among such subsets the heaviest, in increasing order.
     *
     * @param arr the item weights, each 1 or more; must not be null and must not contain null
     * @return A's weights in increasing order, unmodifiable; empty only when {@code arr} is empty
     * @throws IllegalArgumentException if {@code arr} is null, contains null, or contains a weight below 1
     */
    public static List<Integer> minimalHeaviestSetA(List<Integer> arr) {
        if (arr == null) {
            throw new IllegalArgumentException("arr must not be null");
        }
        // Copy into primitives: validates every element once, never touches the caller's list, and sorts
        // without boxing. The total is a long: 100,000 weights of 100,000 overflow an int.
        int[] weights = new int[arr.size()];
        long total = 0;
        int i = 0;
        for (Integer weight : arr) {   // iterate, not get(i): a LinkedList would make get(i) O(n) each
            if (weight == null) {
                throw new IllegalArgumentException("arr must not contain null (index " + i + ")");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("weights must be 1 or more, got " + weight + " at index " + i);
            }
            weights[i++] = weight;
            total += weight;
        }
        Arrays.sort(weights);

        // Walk down from the heaviest until A outweighs the rest. For any size k the k heaviest items have the
        // largest possible sum, so the first size that wins is both minimal and, for that size, the heaviest.
        long sumA = 0;
        int firstOfA = weights.length;
        while (firstOfA > 0 && sumA <= total - sumA) {
            firstOfA--;
            sumA += weights[firstOfA];
        }

        List<Integer> setA = new ArrayList<>(weights.length - firstOfA);
        for (int j = firstOfA; j < weights.length; j++) {
            setA.add(weights[j]);
        }
        LOG.info("box A takes {} of {} items, weight {} against {}", setA.size(), weights.length, sumA,
                total - sumA);
        return Collections.unmodifiableList(setA);
    }
}
