package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class GossipPropagationPlannerTest {

    @Test
    void collectNeighborsMaintainsBoundedNearestOrderWithoutSubList() throws Exception {
        UUID storytellerId = new UUID(0L, 1L);
        OwnerBatchSnapshot.PetSummary storyteller = pet(storytellerId, 0.0D, 0.0D, 0.0D, false);

        Map<UUID, OwnerBatchSnapshot.PetSummary> summaries = new HashMap<>();
        summaries.put(storytellerId, storyteller);

        List<UUID> expectedNeighbors = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            UUID neighborId = new UUID(0L, i + 1L);
            OwnerBatchSnapshot.PetSummary neighbor = pet(neighborId, i, 0.0D, 0.0D, false);
            summaries.put(neighborId, neighbor);
            if (i <= 4) {
                expectedNeighbors.add(neighborId);
            }
        }

        // Out-of-range and invalid candidates should be ignored entirely.
        summaries.put(new UUID(0L, 100L), pet(new UUID(0L, 100L), 20.0D, 0.0D, 0.0D, false));
        summaries.put(new UUID(0L, 101L), pet(new UUID(0L, 101L), Double.NaN, 0.0D, 0.0D, false));
        summaries.put(new UUID(0L, 102L), pet(new UUID(0L, 102L), 2.0D, 0.0D, 0.0D, true));
        summaries.put(null, pet(null, 1.5D, 0.0D, 0.0D, false));

        Method method = GossipPropagationPlanner.class.getDeclaredMethod(
            "collectNeighbors",
            OwnerBatchSnapshot.PetSummary.class,
            Map.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<UUID> neighbors = (List<UUID>) method.invoke(null, storyteller, summaries);

        assertEquals(expectedNeighbors, neighbors, "Should return the four closest neighbors in order");
        assertThrows(UnsupportedOperationException.class, () -> neighbors.add(UUID.randomUUID()));
        String neighborListClass = neighbors.getClass().getName();
        assertTrue(neighborListClass.toLowerCase().contains("immutable"),
            "Neighbor list should be defensively copied");
        assertTrue(!neighborListClass.contains("SubList"),
            "Neighbor list should not expose a truncated ArrayList view");
    }

    private static OwnerBatchSnapshot.PetSummary pet(UUID id,
                                                      double x,
                                                      double y,
                                                      double z,
                                                      boolean optedOut) {
        return new OwnerBatchSnapshot.PetSummary(
            id,
            null,
            0,
            0L,
            false,
            x,
            y,
            z,
            Map.of(),
            optedOut,
            List.of(),
            List.of()
        );
    }
}
