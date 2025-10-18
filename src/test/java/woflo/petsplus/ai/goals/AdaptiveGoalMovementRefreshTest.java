package woflo.petsplus.ai.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.state.PetComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveGoalMovementRefreshTest {

    private static final Identifier TEST_ID = Identifier.of("petsplus", "movement_refresh_test");

    @AfterEach
    void tearDown() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void runningGoalReactsToMovementConfigReloads() throws Exception {
        int initialPriority = 6;

        GoalDefinition definition = new GoalDefinition(
            TEST_ID,
            GoalDefinition.Category.SPECIAL,
            initialPriority,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.NONE,
            false,
            false,
            ignored -> null
        );

        GoalRegistry.registerDataDriven(definition, GoalMovementConfig.actor(initialPriority));

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);
        TestAdaptiveGoal goal = createGoal(definition, director);

        invoke(goal, "registerMovementRole");
        assertTrue(director.isActorLeading(TEST_ID), "goal should claim leadership with initial config");

        int refreshedPriority = 3;
        GoalDefinition refreshedDefinition = new GoalDefinition(
            TEST_ID,
            GoalDefinition.Category.SPECIAL,
            refreshedPriority,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.NONE,
            false,
            false,
            ignored -> null
        );

        GoalRegistry.registerDataDriven(refreshedDefinition, null);

        assertTrue((boolean) invoke(goal, "maintainMovementLeadership"));
        assertEquals(refreshedPriority, leadingPriority(director), "actor priority should refresh after override");

        GoalDefinition influencerDefinition = new GoalDefinition(
            TEST_ID,
            GoalDefinition.Category.SOCIAL,
            refreshedPriority,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.NONE,
            false,
            false,
            ignored -> null
        );

        GoalRegistry.registerDataDriven(influencerDefinition, GoalMovementConfig.influencer(0.75f));

        assertTrue((boolean) invoke(goal, "maintainMovementLeadership"));
        assertFalse(director.isActorLeading(TEST_ID), "goal should release leadership when role becomes influencer");
        assertFalse(activeActorsContains(director, TEST_ID), "actor slot should be cleared when role drops");

        invoke(goal, "clearMovementRole");
    }

    @Test
    void runningGoalAdoptsRefreshedDefinitionMetadata() throws Exception {
        Identifier id = Identifier.of("petsplus", "definition_refresh_test");
        int initialPriority = 7;
        int initialCooldown = 20;

        GoalDefinition definition = new GoalDefinition(
            id,
            GoalDefinition.Category.SOCIAL,
            initialPriority,
            initialCooldown,
            initialCooldown,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.NONE,
            false,
            false,
            ignored -> null
        );

        GoalRegistry.registerDataDriven(definition, GoalMovementConfig.actor(initialPriority));

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);
        TestAdaptiveGoal goal = createGoal(definition, director);

        MobEntity mob = null;
        TrackingPetComponent component = instantiateComponent(null, director);

        setField(goal, AdaptiveGoal.class, "mob", null);
        setField(goal, AdaptiveGoal.class, "petComponent", component);

        try {
            goal.start();

            setField(goal, AdaptiveGoal.class, "activeTicks", 40);

            GoalDefinition refreshed = new GoalDefinition(
                id,
                GoalDefinition.Category.SOCIAL,
                3,
                60,
                60,
                MobCapabilities.CapabilityRequirement.any(),
                new Vec2f(0.0f, 1.0f),
                GoalDefinition.IdleStaminaBias.NONE,
                false,
                false,
                ignored -> null
            );

            GoalRegistry.registerDataDriven(refreshed, null);

            GoalDefinition resolved = (GoalDefinition) invoke(goal, "currentDefinition");
            assertEquals(refreshed.priority(), resolved.priority(), "priority should refresh while active");
            assertEquals(refreshed.minCooldownTicks(), resolved.minCooldownTicks(), "min cooldown should refresh while active");

            invoke(goal, "applyGoalCooldown");
            assertEquals(refreshed.minCooldownTicks(), component.lastCooldownTicks, "cooldown should apply refreshed duration");

            component.forceCooldown();
            assertFalse((boolean) invoke(goal, "isCooldownExpired"), "cooldown flag should block restart until cleared");
            component.clearCooldown();
            assertTrue((boolean) invoke(goal, "isCooldownExpired"), "clearing cooldown should allow restart checks to pass");
        } finally {
            if (mob != null) {
                PetComponent.remove(mob);
            }
        }
    }

    private static Object invoke(AdaptiveGoal goal, String methodName) throws Exception {
        Method method = AdaptiveGoal.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(goal);
    }

    @SuppressWarnings("unchecked")
    private static boolean activeActorsContains(PetComponent.MovementDirector director, Identifier id) throws Exception {
        Field field = PetComponent.MovementDirector.class.getDeclaredField("activeActors");
        field.setAccessible(true);
        return ((java.util.Map<Identifier, ?>) field.get(director)).containsKey(id);
    }

    private static int leadingPriority(PetComponent.MovementDirector director) throws Exception {
        Field field = PetComponent.MovementDirector.class.getDeclaredField("leadingActorPriority");
        field.setAccessible(true);
        return field.getInt(director);
    }

    private static final class TestAdaptiveGoal extends AdaptiveGoal {

        private TestAdaptiveGoal() {
            super((net.minecraft.entity.mob.MobEntity) null, GoalRegistry.require(GoalIds.FOLLOW_OWNER), EnumSet.noneOf(Control.class));
        }

        @Override
        protected boolean canStartGoal() {
            return true;
        }

        @Override
        protected boolean shouldContinueGoal() {
            return true;
        }

        @Override
        protected void onStartGoal() {
        }

        @Override
        protected void onStopGoal() {
        }

        @Override
        protected void onTickGoal() {
        }

        @Override
        protected float calculateEngagement() {
            return 1.0f;
        }
    }

    private static final sun.misc.Unsafe UNSAFE = fetchUnsafe();

    private static sun.misc.Unsafe fetchUnsafe() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to access Unsafe", error);
        }
    }

    private static TestAdaptiveGoal createGoal(GoalDefinition definition, PetComponent.MovementDirector director) throws Exception {
        TestAdaptiveGoal goal = (TestAdaptiveGoal) UNSAFE.allocateInstance(TestAdaptiveGoal.class);
        setField(goal, AdaptiveGoal.class, "goalDefinition", definition);
        setField(goal, AdaptiveGoal.class, "goalId", definition.id());
        setField(goal, AdaptiveGoal.class, "lastResolvedDefinition", definition);
        setField(goal, AdaptiveGoal.class, "movementDirector", director);
        setField(goal, Goal.class, "controls", EnumSet.noneOf(Goal.Control.class));
        return goal;
    }

    private static void setField(Object instance, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        long offset = UNSAFE.objectFieldOffset(field);
        Class<?> type = field.getType();
        if (type == int.class) {
            UNSAFE.putInt(instance, offset, ((Number) value).intValue());
        } else if (type == long.class) {
            UNSAFE.putLong(instance, offset, ((Number) value).longValue());
        } else if (type == float.class) {
            UNSAFE.putFloat(instance, offset, ((Number) value).floatValue());
        } else if (type == double.class) {
            UNSAFE.putDouble(instance, offset, ((Number) value).doubleValue());
        } else if (type == boolean.class) {
            UNSAFE.putBoolean(instance, offset, (Boolean) value);
        } else {
            UNSAFE.putObject(instance, offset, value);
        }
    }

    private static TrackingPetComponent instantiateComponent(MobEntity mob, PetComponent.MovementDirector director) throws Exception {
        TrackingPetComponent component = (TrackingPetComponent) UNSAFE.allocateInstance(TrackingPetComponent.class);
        setField(component, PetComponent.class, "pet", mob);
        setField(component, PetComponent.class, "movementDirector", director);
        if (mob != null) {
            PetComponent.set(mob, component);
        }
        return component;
    }

    private static final class TrackingPetComponent extends PetComponent {

        private int lastCooldownTicks;
        private boolean onCooldown;

        private TrackingPetComponent() {
            super((net.minecraft.entity.mob.MobEntity) null);
            throw new AssertionError("Stub");
        }

        @Override
        public void beginAdaptiveGoal(Identifier goalId, long worldTime) {
            // no-op for tests
        }

        @Override
        public void endAdaptiveGoal(Identifier goalId, long worldTime) {
            onCooldown = false;
        }

        @Override
        public void recordGoalStart(Identifier goalId) {
        }

        @Override
        public void recordGoalCompletion(Identifier goalId, long worldTime) {
        }

        @Override
        public void setCooldown(String key, int ticks) {
            lastCooldownTicks = ticks;
            onCooldown = ticks > 0;
        }

        @Override
        public boolean isOnCooldown(String key) {
            return onCooldown;
        }

        void forceCooldown() {
            onCooldown = true;
        }

        void clearCooldown() {
            onCooldown = false;
        }
    }
}

