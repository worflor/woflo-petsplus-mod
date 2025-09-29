package woflo.petsplus.abilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import woflo.petsplus.api.Ability;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;

class AbilityManagerTest {
    private Map<Identifier, Object> roleEventCaches;
    private Identifier testRole;

    @BeforeEach
    void setUpCacheField() throws Exception {
        Field cachesField = AbilityManager.class.getDeclaredField("ROLE_EVENT_CACHES");
        cachesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Identifier, Object> caches = (Map<Identifier, Object>) cachesField.get(null);
        this.roleEventCaches = caches;
        this.testRole = Identifier.of("petsplus", "test_role");
    }

    @AfterEach
    void tearDownCacheEntry() {
        roleEventCaches.remove(testRole);
    }

    @Test
    void asyncAbilityPlansRecheckCooldownsAtApplyTime() {
        UUID ownerId = UUID.randomUUID();
        String trigger = "test_trigger";
        Identifier abilityId = Identifier.of("petsplus", "test_ability");
        String cooldownKey = abilityId.toString();

        Trigger triggerImpl = new Trigger() {
            private final Identifier id = Identifier.of("petsplus", trigger);

            @Override
            public Identifier getId() {
                return id;
            }

            @Override
            public boolean shouldActivate(woflo.petsplus.api.TriggerContext context) {
                return true;
            }
        };
        Ability ability = new Ability(abilityId, triggerImpl, List.of(), null);

        try {
            Class<?> cacheClass = Class.forName("woflo.petsplus.abilities.AbilityManager$RoleAbilityCache");
            var buildMethod = cacheClass.getDeclaredMethod("build", List.class, Map.class, List.class);
            buildMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            Object cache = buildMethod.invoke(null, List.of(ability), Map.of(trigger, List.of(ability)), List.of());
            roleEventCaches.put(testRole, cache);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build role ability cache", exception);
        }

        Map<String, Long> cooldownSnapshotMap = Map.of(cooldownKey, 100L);
        OwnerBatchSnapshot.PetSummary summary = new OwnerBatchSnapshot.PetSummary(
            UUID.randomUUID(),
            testRole,
            5,
            0L,
            false,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            cooldownSnapshotMap,
            false,
            List.of(),
            List.of()
        );

        OwnerBatchSnapshot snapshot = createSnapshot(ownerId, List.of(summary), 80L);

        AbilityTriggerPayload payload = AbilityTriggerPayload.of(trigger, Map.of());
        AbilityManager.AbilityExecutionPlan plan = AbilityManager.prepareOwnerExecutionPlan(snapshot, payload);
        assertFalse(plan.isEmpty(), "Ability plan should retain abilities that were cooling down at snapshot time");

        AbilityManager.AbilityExecutionPlan.PetExecution execution = plan.executions().get(0);
        Map<String, Long> cooldownSnapshot = execution.cooldowns();

        List<?> filteredAtSnapshot = invokeCooldownFilter(execution.abilities(), cooldownSnapshot, 80L);
        assertTrue(filteredAtSnapshot.isEmpty(), "Snapshot tick should still treat ability as cooling down");

        List<?> filteredAtApply = invokeCooldownFilter(execution.abilities(), cooldownSnapshot, 120L);
        assertFalse(filteredAtApply.isEmpty(), "Application tick should allow ability to execute");

        Object compiled = filteredAtApply.get(0);
        try {
            var abilityMethod = compiled.getClass().getDeclaredMethod("ability");
            abilityMethod.setAccessible(true);
            Object compiledAbility = abilityMethod.invoke(compiled);
            assertTrue(compiledAbility == ability, "Compiled ability should reference the prepared ability instance");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to inspect compiled ability", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeCooldownFilter(List<?> abilities, Map<String, Long> cooldowns, long tick) {
        try {
            var method = AbilityManager.class.getDeclaredMethod(
                "filterAbilitiesByCooldown",
                List.class,
                Map.class,
                long.class
            );
            method.setAccessible(true);
            return (List<?>) method.invoke(null, abilities, cooldowns, tick);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to invoke cooldown filter", exception);
        }
    }

    private static OwnerBatchSnapshot createSnapshot(UUID ownerId, List<OwnerBatchSnapshot.PetSummary> pets, long snapshotTick) {
        try {
            var constructor = OwnerBatchSnapshot.class.getDeclaredConstructor(
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
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to construct snapshot", exception);
        }
    }
}
