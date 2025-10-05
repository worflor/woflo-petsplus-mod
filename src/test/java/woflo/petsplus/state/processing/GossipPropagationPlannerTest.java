package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.processing.GossipPropagationPlanner.GossipPropagationPlan;
import woflo.petsplus.state.processing.OwnerBatchSnapshot.PetSummary;
import woflo.petsplus.state.processing.OwnerEventType;

final class GossipPropagationPlannerTest {

    @Test
    void planBatchesNeighborsWithinRadius() throws Exception {
        UUID storytellerId = UUID.randomUUID();
        UUID neighborId = UUID.randomUUID();

        RumorEntry rumor = RumorEntry.create(42L, 0.5f, 0.6f, 80L, null, null);
        PetSummary storyteller = new PetSummary(
            storytellerId,
            null,
            0,
            0L,
            false,
            0.0D,
            64.0D,
            0.0D,
            Map.of(),
            false,
            List.of(rumor),
            List.of()
        );

        PetSummary neighbor = new PetSummary(
            neighborId,
            null,
            0,
            0L,
            false,
            3.0D,
            64.0D,
            0.0D,
            Map.of(),
            false,
            List.of(),
            List.of()
        );

        OwnerBatchSnapshot snapshot = constructSnapshot(
            UUID.randomUUID(),
            List.of(storyteller, neighbor),
            100L,
            Map.of(PetWorkScheduler.TaskType.GOSSIP_DECAY,
                List.of(new OwnerBatchSnapshot.TaskSnapshot(PetWorkScheduler.TaskType.GOSSIP_DECAY, storytellerId, null, 100L))),
            Set.of(OwnerEventType.GOSSIP)
        );

        GossipPropagationPlan plan = GossipPropagationPlanner.plan(snapshot);
        assertFalse(plan.isEmpty(), "Plan should include storyteller entry");
        List<GossipPropagationPlanner.Share> shares = plan.sharesFor(storytellerId);
        assertEquals(1, shares.size(), "One neighbor should be scheduled");
        assertEquals(neighborId, shares.get(0).listenerId(), "Neighbor id should match");
    }

    private static OwnerBatchSnapshot constructSnapshot(UUID ownerId,
                                                        List<PetSummary> pets,
                                                        long snapshotTick,
                                                        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> buckets,
                                                        Set<OwnerEventType> dueEvents) throws Exception {
        Constructor<OwnerBatchSnapshot> constructor = OwnerBatchSnapshot.class.getDeclaredConstructor(
            UUID.class,
            long.class,
            UUID.class,
            Set.class,
            Map.class,
            List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            ownerId,
            snapshotTick,
            null,
            dueEvents,
            buckets,
            pets
        );
    }
}
