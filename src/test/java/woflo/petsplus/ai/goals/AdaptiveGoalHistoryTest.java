package woflo.petsplus.ai.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveGoalHistoryTest {

    private static final GoalDefinition TEST_DEFINITION = new GoalDefinition(
        Identifier.of("petsplus", "test"),
        GoalDefinition.Category.PLAY,
        0,
        0,
        0,
        MobCapabilities.CapabilityRequirement.any(),
        new Vec2f(0.0f, 1.0f),
        GoalDefinition.IdleStaminaBias.CENTERED,
        false,
        mob -> null
    );

    @AfterEach
    void clearFallbacks() {
        AdaptiveGoal.clearFallbackHistory();
    }

    @Test
    void recordsHistoryOnComponent() {
        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);
        PetComponent component = Mockito.mock(PetComponent.class);
        PetMoodEngine moodEngine = Mockito.mock(PetMoodEngine.class);

        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getTime()).thenReturn(200L);
        Mockito.when(component.getMoodEngine()).thenReturn(moodEngine);

        try (MockedStatic<PetComponent> mocked = Mockito.mockStatic(PetComponent.class)) {
            mocked.when(() -> PetComponent.get(mob)).thenReturn(component);

            TestAdaptiveGoal goal = new TestAdaptiveGoal(mob);
            goal.start();
            goal.stop();

            Mockito.verify(component).recordGoalStart(TEST_DEFINITION.id());
            Mockito.verify(component).recordGoalCompletion(TEST_DEFINITION.id(), 200L);
        }
    }

    @Test
    void fallsBackToInternalHistoryWhenComponentMissing() {
        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);

        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getTime()).thenReturn(480L);

        try (MockedStatic<PetComponent> mocked = Mockito.mockStatic(PetComponent.class)) {
            mocked.when(() -> PetComponent.get(mob)).thenReturn(null);

            TestAdaptiveGoal goal = new TestAdaptiveGoal(mob);
            goal.start();
            goal.stop();
        }

        assertEquals(TEST_DEFINITION.id(), AdaptiveGoal.getFallbackRecentGoals(mob).peekFirst());
        Map<Identifier, Long> history = AdaptiveGoal.getFallbackLastExecuted(mob);
        assertTrue(history.containsKey(TEST_DEFINITION.id()));
        assertEquals(480L, history.get(TEST_DEFINITION.id()));
    }

    private static final class TestAdaptiveGoal extends AdaptiveGoal {
        private TestAdaptiveGoal(MobEntity mob) {
            super(mob, TEST_DEFINITION, EnumSet.noneOf(Goal.Control.class));
        }

        @Override
        protected boolean canStartGoal() {
            return true;
        }

        @Override
        protected boolean shouldContinueGoal() {
            return false;
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
            return 0.5f;
        }
    }
}
