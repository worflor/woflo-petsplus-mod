package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.PetMobInteractionProfile;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VarietyDesirabilitySignalTest {

    private static final GoalDefinition TEST_GOAL = new GoalDefinition(
        Identifier.of("petsplus", "test_goal"),
        GoalDefinition.Category.PLAY,
        10,
        0,
        0,
        MobCapabilities.CapabilityRequirement.any(),
        new net.minecraft.util.math.Vec2f(0.0f, 1.0f),
        GoalDefinition.IdleStaminaBias.NONE,
        false,
        false,
        mob -> null
    );

    @Test
    void staleWorldSnapshotTreatsGoalAsRecentlyCompleted() {
        Identifier goalId = TEST_GOAL.id();
        Map<Identifier, Long> lastExecuted = Map.of(goalId, 200L);
        Deque<Identifier> recentGoals = new ArrayDeque<>();
        PetContext context = contextWithHistory(180L, lastExecuted, recentGoals);
        VarietyDesirabilitySignal signal = new VarietyDesirabilitySignal();

        SignalResult result = signal.evaluate(TEST_GOAL, context);

        assertEquals(0.3f, result.appliedValue(), 0.0001f);
        assertEquals(0.3f, result.rawValue(), 0.0001f);
        assertEquals(0L, ((Number) result.trace().get("ticksSince")).longValue());
    }

    private static PetContext contextWithHistory(long worldTime,
                                                 Map<Identifier, Long> lastExecuted,
                                                 Deque<Identifier> recentGoals) {
        BehaviouralEnergyProfile energyProfile = new BehaviouralEnergyProfile(
            0.5f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.5f,
            0.5f,
            0.5f
        );

        return new PetContext(
            null,
            null,
            null,
            0,
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            0,
            0f,
            0L,
            null,
            false,
            Float.MAX_VALUE,
            List.of(),
            PetContextCrowdSummary.empty(),
            NearbyMobAgeProfile.empty(),
            PetMobInteractionProfile.defaultProfile(),
            net.minecraft.util.math.BlockPos.ORIGIN,
            worldTime,
            true,
            StimulusSnapshot.empty(),
            SocialSnapshot.empty(),
            false,
            recentGoals,
            lastExecuted,
            Map.of(),
            null,
            Long.MIN_VALUE,
            0.5f,
            energyProfile
        );
    }
}
