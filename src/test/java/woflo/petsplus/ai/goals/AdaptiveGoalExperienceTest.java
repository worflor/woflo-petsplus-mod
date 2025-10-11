package woflo.petsplus.ai.goals;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.ai.feedback.ExperienceLog;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveGoalExperienceTest {

    @AfterEach
    void cleanup() {
        AdaptiveGoal.clearFallbackHistory();
    }

    @Test
    void stopRecordsExperienceInComponentLog() {
        MobEntity mob = Mockito.mock(MobEntity.class);
        var world = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getTime()).thenReturn(200L);

        PetComponent component = new PetComponent(mob);
        PetComponent.set(mob, component);

        GoalDefinition definition = new GoalDefinition(
            Identifier.of("test", "experience"),
            GoalDefinition.Category.PLAY,
            10,
            0,
            0,
            profile -> true,
            new net.minecraft.util.math.Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mobx -> null
        );

        TestGoal goal = new TestGoal(mob, definition);
        goal.start();
        goal.stop();

        ExperienceLog log = component.getExperienceLog();
        assertEquals(1, log.entries().size());
        assertEquals(Identifier.of("test", "experience"), log.entries().get(0).goalId());
        PetComponent.remove(mob);
    }

    private static class TestGoal extends AdaptiveGoal {
        TestGoal(MobEntity mob, GoalDefinition def) {
            super(mob, def, java.util.EnumSet.noneOf(Goal.Control.class));
        }

        @Override
        protected boolean canStartGoal() { return true; }

        @Override
        protected boolean shouldContinueGoal() { return false; }

        @Override
        protected void onStartGoal() {}

        @Override
        protected void onStopGoal() {}

        @Override
        protected void onTickGoal() {}

        @Override
        protected float calculateEngagement() { return 0.8f; }

        @Override
        protected PetContext getContext() {
            return new PetContext(
                mob,
                null,
                null,
                0,
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                1,
                0f,
                0L,
                null,
                false,
                Float.MAX_VALUE,
                List.<Entity>of(),
                PetContextCrowdSummary.empty(),
                BlockPos.ORIGIN,
                0L,
                true,
                StimulusSnapshot.empty(),
                SocialSnapshot.empty(),
                false,
                new ArrayDeque<>(),
                Map.of(),
                Map.of(),
                0.5f,
                BehaviouralEnergyProfile.neutral()
            );
        }
    }
}

