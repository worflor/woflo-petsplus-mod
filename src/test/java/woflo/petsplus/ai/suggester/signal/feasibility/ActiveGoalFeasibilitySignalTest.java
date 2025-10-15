package woflo.petsplus.ai.suggester.signal.feasibility;

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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveGoalFeasibilitySignalTest {

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

    private static final GoalDefinition OTHER_GOAL = new GoalDefinition(
        Identifier.of("petsplus", "other_goal"),
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
    void identityWhenNoActiveGoalPresent() {
        PetContext context = emptyContext(null, Long.MIN_VALUE, 200L);
        ActiveGoalFeasibilitySignal signal = new ActiveGoalFeasibilitySignal(40L);

        SignalResult result = signal.evaluate(TEST_GOAL, context);

        assertEquals(1.0f, result.appliedValue(), 0.0001f);
        assertEquals(1.0f, result.rawValue(), 0.0001f);
    }

    @Test
    void suppressesOtherGoalsDuringGracePeriod() {
        Identifier activeId = OTHER_GOAL.id();
        PetContext context = emptyContext(activeId, 160L, 180L);
        ActiveGoalFeasibilitySignal signal = new ActiveGoalFeasibilitySignal(40L);

        SignalResult result = signal.evaluate(TEST_GOAL, context);

        assertEquals(0.0f, result.appliedValue(), 0.0001f);
        assertEquals(0.0f, result.rawValue(), 0.0001f);
        assertEquals("active_goal", result.trace().get("reason"));
        assertEquals(activeId.toString(), result.trace().get("active_goal"));
    }

    @Test
    void releasesOtherGoalsAfterGracePeriodExpires() {
        Identifier activeId = OTHER_GOAL.id();
        PetContext context = emptyContext(activeId, 100L, 180L);
        ActiveGoalFeasibilitySignal signal = new ActiveGoalFeasibilitySignal(40L);

        SignalResult result = signal.evaluate(TEST_GOAL, context);

        assertEquals(1.0f, result.appliedValue(), 0.0001f);
        assertEquals(1.0f, result.rawValue(), 0.0001f);
    }

    @Test
    void leavesActiveGoalUntouched() {
        Identifier activeId = TEST_GOAL.id();
        PetContext context = emptyContext(activeId, 160L, 180L);
        ActiveGoalFeasibilitySignal signal = new ActiveGoalFeasibilitySignal(40L);

        SignalResult result = signal.evaluate(TEST_GOAL, context);

        assertEquals(1.0f, result.appliedValue(), 0.0001f);
        assertTrue(result.trace().isEmpty());
    }

    private static PetContext emptyContext(Identifier activeGoalId, long startTick, long worldTime) {
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
            new java.util.ArrayDeque<>(),
            Map.of(),
            Map.of(),
            activeGoalId,
            startTick,
            0.5f,
            energyProfile
        );
    }
}
