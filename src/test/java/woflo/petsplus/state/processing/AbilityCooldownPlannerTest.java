package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import woflo.petsplus.state.processing.OwnerBatchSnapshot.PetSummary;

final class AbilityCooldownPlannerTest {

    @Test
    void planCollectsExpiredCooldowns() throws Exception {
        UUID petId = UUID.randomUUID();
        Map<String, Long> cooldowns = Map.of(
            "expired", 80L,
            "active", 240L
        );

        PetSummary summary = new PetSummary(
            petId,
            null,
            0,
            0L,
            false,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            cooldowns,
            false,
            List.of(),
            List.of()
        );

        OwnerBatchSnapshot snapshot = constructSnapshot(UUID.randomUUID(), List.of(summary), 120L);

        AbilityCooldownPlan plan = AbilityCooldownPlanner.plan(snapshot);
        assertNotNull(plan);
        assertTrue(!plan.isEmpty(), "Plan should include expired cooldowns");

        AbilityCooldownPlan.PetCooldown petPlan = plan.planFor(petId);
        assertNotNull(petPlan, "Plan should contain entry for pet");
        assertEquals(List.of("expired"), petPlan.expiredKeys(), "Only expired cooldown should be listed");
    }

    private static OwnerBatchSnapshot constructSnapshot(UUID ownerId,
                                                        List<PetSummary> pets,
                                                        long snapshotTick) throws Exception {
        Constructor<OwnerBatchSnapshot> constructor = OwnerBatchSnapshot.class.getDeclaredConstructor(
            UUID.class,
            long.class,
            UUID.class,
            java.util.Set.class,
            Map.class,
            List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            ownerId,
            snapshotTick,
            null,
            java.util.Set.of(),
            Map.of(),
            pets
        );
    }
}
