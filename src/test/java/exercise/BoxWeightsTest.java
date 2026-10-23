package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoxWeightsTest {

    private static List<Integer> setA(Integer... weights) {
        return BoxWeights.minimalHeaviestSetA(List.of(weights));
    }

    @Nested
    class HappyPath {

        @Test
        void exampleFromTheStatement() {
            assertThat(setA(3, 7, 5, 6, 2)).containsExactly(6, 7);
        }

        @Test
        void sampleCase0() {
            assertThat(setA(5, 3, 2, 4, 1, 2)).containsExactly(4, 5);
        }

        @Test
        void sampleCase1() {
            assertThat(setA(4, 2, 5, 1, 6)).containsExactly(5, 6);
        }

        @Test
        void singleItemIsTheWholeSetA() {
            assertThat(setA(7)).containsExactly(7);
        }

        @Test
        void oneItemHeavierThanAllTheRestIsEnough() {
            assertThat(setA(1, 2, 3, 100)).containsExactly(100);
        }

        @Test
        void resultIsAscendingWhateverTheInputOrder() {
            List<Integer> input = new ArrayList<>(List.of(5, 3, 2, 4, 1, 2));
            Collections.shuffle(input, new Random(42));

            assertThat(BoxWeights.minimalHeaviestSetA(input)).containsExactly(4, 5);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyInputGivesAnEmptyResult() {
            assertThat(setA()).isEmpty();
        }

        @Test
        void twoEqualItemsNeedBothBecauseAMustBeStrictlyHeavier() {
            assertThat(setA(5, 5)).containsExactly(5, 5);
        }

        @Test
        void allEqualEvenCountTakesOneMoreThanHalf() {
            assertThat(setA(2, 2, 2, 2)).containsExactly(2, 2, 2);
        }

        @Test
        void allEqualOddCountTakesTheMajority() {
            assertThat(setA(1, 1, 1)).containsExactly(1, 1);
        }

        @Test
        void duplicateWeightsAreKeptInTheResult() {
            assertThat(setA(3, 3, 2)).containsExactly(3, 3);
        }

        @Test
        void exactTieIsNotEnoughSoOneMoreItemIsTaken() {
            // 2 + 2 = 4 equals 1 + 1 + 1 + 1, so a third item is needed
            assertThat(setA(1, 1, 1, 1, 2, 2)).containsExactly(1, 2, 2);
        }

        @Test
        void inputListIsNotMutated() {
            List<Integer> input = new ArrayList<>(List.of(5, 3, 2, 4, 1, 2));
            List<Integer> copy = List.copyOf(input);

            BoxWeights.minimalHeaviestSetA(input);

            assertThat(input).containsExactlyElementsOf(copy);
        }

        @Test
        void sumsAtTheConstraintLimitDoNotOverflowInt() {
            // 100,000 items of 100,000: total 10^10, A needs 50,001 items (5,000,100,000 > 4,999,900,000)
            List<Integer> input = Collections.nCopies(100_000, 100_000);

            List<Integer> result = BoxWeights.minimalHeaviestSetA(input);

            assertThat(result).hasSize(50_001).allMatch(w -> w == 100_000);
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void nullListIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> BoxWeights.minimalHeaviestSetA(null))
                    .withMessageContaining("null");
        }

        @Test
        void nullElementIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> BoxWeights.minimalHeaviestSetA(Arrays.asList(3, null, 5)))
                    .withMessageContaining("null");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void weightBelowOneIsRejected(int weight) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> setA(3, weight, 5))
                    .withMessageContaining("1");
        }
    }
}
