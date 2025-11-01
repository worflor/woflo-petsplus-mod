package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.gossip.RumorEntry;

class GossipPropagationPlannerTest {

    @Test
    void planMaintainsBoundedNearestOrderWithoutRedundantPairwork() throws Exception {
        UUID storytellerId = new UUID(0L, 1L);
        List<OwnerBatchSnapshot.PetSummary> pets = new ArrayList<>();
        pets.add(pet(storytellerId, 0.0D, 0.0D, 0.0D, false, shareables()));

        List<UUID> expectedNeighbors = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            UUID neighborId = new UUID(0L, i + 1L);
            pets.add(pet(neighborId, i, 0.0D, 0.0D, false, List.of()));
            if (i <= 4) {
                expectedNeighbors.add(neighborId);
            }
        }

        // Out-of-range, invalid, and opted-out candidates should be ignored.
        pets.add(pet(new UUID(0L, 100L), 20.0D, 0.0D, 0.0D, false, List.of()));
        pets.add(pet(new UUID(0L, 101L), Double.NaN, 0.0D, 0.0D, false, List.of()));
        pets.add(pet(new UUID(0L, 102L), 2.0D, 0.0D, 0.0D, true, List.of()));

        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> taskBuckets = new EnumMap<>(PetWorkScheduler.TaskType.class);
        taskBuckets.put(PetWorkScheduler.TaskType.GOSSIP_DECAY,
            List.of(new OwnerBatchSnapshot.TaskSnapshot(PetWorkScheduler.TaskType.GOSSIP_DECAY, storytellerId, null, 0L)));

        OwnerBatchSnapshot snapshot = snapshot(pets, taskBuckets);

        GossipPropagationPlanner.GossipPropagationPlan plan = GossipPropagationPlanner.plan(snapshot);
        List<GossipPropagationPlanner.Share> shares = plan.sharesFor(storytellerId);

        assertEquals(expectedNeighbors.size(), shares.size(), "Should target up to the four closest neighbors");
        for (int i = 0; i < expectedNeighbors.size(); i++) {
            assertEquals(expectedNeighbors.get(i), shares.get(i).listenerId(),
                "Neighbors should be ordered by increasing distance");
        }

        assertThrows(UnsupportedOperationException.class, () -> shares.add(new GossipPropagationPlanner.Share(UUID.randomUUID(), shareables().get(0))));
    }

    private static OwnerBatchSnapshot snapshot(List<OwnerBatchSnapshot.PetSummary> pets,
                                               Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> taskBuckets)
        throws Exception {
        Constructor<OwnerBatchSnapshot> ctor = OwnerBatchSnapshot.class.getDeclaredConstructor(
            UUID.class,
            long.class,
            UUID.class,
            Set.class,
            Map.class,
            List.class,
            OwnerFocusSnapshot.class
        );
        ctor.setAccessible(true);
        return ctor.newInstance(new UUID(0L, 0L), 0L, null, Set.of(), taskBuckets, List.copyOf(pets), OwnerFocusSnapshot.idle());
    }

    private static OwnerBatchSnapshot.PetSummary pet(UUID id,
                                                      double x,
                                                      double y,
                                                      double z,
                                                      boolean optedOut,
                                                      List<RumorEntry> freshRumors) {
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
            freshRumors,
            List.of()
        );
    }

    private static List<RumorEntry> shareables() {
        return List.of(
            RumorEntry.create(1L, 0.5f, 0.6f, 0L, null, null),
            RumorEntry.create(2L, 0.5f, 0.6f, 0L, null, null),
            RumorEntry.create(3L, 0.5f, 0.6f, 0L, null, null),
            RumorEntry.create(4L, 0.5f, 0.6f, 0L, null, null)
        );
    }
}
