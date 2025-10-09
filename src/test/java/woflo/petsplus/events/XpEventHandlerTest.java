package woflo.petsplus.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class XpEventHandlerTest {
    @Test
    void allocateXpSharesDoesNotExceedPlayerDeltaWithManyPets() {
        int xpDelta = 12;
        double[] weights = new double[128];
        Arrays.fill(weights, 1.0);

        List<Integer> shares = XpEventHandler.allocateXpShares(xpDelta, weights);
        assertEquals(weights.length, shares.size(), "Each pet should receive an allocation entry");

        int distributedTotal = shares.stream().mapToInt(Integer::intValue).sum();
        assertTrue(distributedTotal <= xpDelta,
            "Distributed XP should never exceed the player's XP delta");
    }

    @Test
    void allocateXpSharesDistributesRemaindersDeterministically() {
        double[] weights = new double[] {1.0, 1.0, 1.0, 1.0};

        List<Integer> shares = XpEventHandler.allocateXpShares(5.0, weights);

        assertEquals(List.of(2, 1, 1, 1), shares,
            "Remainder XP should be assigned deterministically based on remainders");
    }
}
