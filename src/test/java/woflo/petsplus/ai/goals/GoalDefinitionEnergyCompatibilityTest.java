package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalDefinitionEnergyCompatibilityTest {

    @Test
    void drainedPhysicalStaminaMarksPlayGoalIncompatible() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "play_test"),
            GoalDefinition.Category.PLAY,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.8f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        BehaviouralEnergyProfile profile = new BehaviouralEnergyProfile(
            0.5f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.0f,
            0.6f,
            0.45f
        );

        assertEquals(0.08f, definition.getEnergyBias(profile), 0.0001f, "Depleted stamina should trigger the incompatibility sentinel");
        assertFalse(definition.isEnergyCompatible(profile));
    }

    @Test
    void drainedMentalFocusBlocksSpecialGoalEvenWithHealthyStamina() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "special_focus"),
            GoalDefinition.Category.SPECIAL,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.2f, 0.9f),
            GoalDefinition.IdleStaminaBias.LOW,
            false,
            mob -> null
        );

        BehaviouralEnergyProfile profile = new BehaviouralEnergyProfile(
            0.5f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.6f,
            0.01f,
            0.6f
        );

        assertEquals(0.08f, definition.getEnergyBias(profile), 0.0001f, "Depleted focus should short-circuit the energy bias");
        assertFalse(definition.isEnergyCompatible(profile));
    }

    @Test
    void drainedSocialChargeBlocksSocialGoal() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "social_test"),
            GoalDefinition.Category.SOCIAL,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.25f, 0.85f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        BehaviouralEnergyProfile profile = new BehaviouralEnergyProfile(
            0.5f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.6f,
            0.6f,
            0.0f
        );

        assertEquals(0.08f, definition.getEnergyBias(profile), 0.0001f, "Depleted social charge should short-circuit the bias");
        assertFalse(definition.isEnergyCompatible(profile));
    }

    @Test
    void specialIdleBiasLowPrefersTiredPetsOverRestedOnes() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "special_idle_bias"),
            GoalDefinition.Category.SPECIAL,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.2f, 0.9f),
            GoalDefinition.IdleStaminaBias.LOW,
            false,
            mob -> null
        );

        BehaviouralEnergyProfile tiredProfile = new BehaviouralEnergyProfile(
            0.55f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.2f,
            0.7f,
            0.6f
        );
        BehaviouralEnergyProfile wiredProfile = new BehaviouralEnergyProfile(
            0.55f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.95f,
            0.7f,
            0.6f
        );

        float tiredBias = definition.getEnergyBias(tiredProfile);
        float wiredBias = definition.getEnergyBias(wiredProfile);

        assertTrue(tiredBias > wiredBias, "Low idle stamina bias should reward tired pets over wired ones");
    }

    @Test
    void highIdleBiasIdleGoalFailsWhenStaminaIsDrained() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "idle_high_bias"),
            GoalDefinition.Category.IDLE_QUIRK,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.2f, 0.7f),
            GoalDefinition.IdleStaminaBias.HIGH,
            false,
            mob -> null
        );

        BehaviouralEnergyProfile profile = new BehaviouralEnergyProfile(
            0.45f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.05f,
            0.5f,
            0.5f
        );

        assertEquals(0.08f, definition.getEnergyBias(profile), 0.0001f, "High-bias idle goal should go incompatible when stamina is empty");
        assertFalse(definition.isEnergyCompatible(profile));
    }

    @Test
    void socialIdleBiasGoalRespectsDrainedSocialCharge() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("petsplus", "idle_social_bias"),
            GoalDefinition.Category.IDLE_QUIRK,
            1,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.2f, 0.8f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            true,
            mob -> null
        );

        BehaviouralEnergyProfile profile = new BehaviouralEnergyProfile(
            0.5f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0.4f,
            0.4f,
            0.1f
        );

        assertEquals(0.08f, definition.getEnergyBias(profile), 0.0001f, "Drained social charge should flip the idle goal to incompatible");
        assertFalse(definition.isEnergyCompatible(profile));
    }
}
