package woflo.petsplus.ai.goals.idle;

import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

/**
 * Utility helpers for scaling idle quirk engagement using the behavioural
 * energy stack. Keeps stamina/social multipliers consistent across the
 * various idle goal implementations.
 */
final class IdleEnergyTuning {
    private static final float NEUTRAL_STAMINA = BehaviouralEnergyProfile.neutral().physicalStamina();
    private static final float NEUTRAL_SOCIAL = BehaviouralEnergyProfile.neutral().socialCharge();

    private IdleEnergyTuning() {
    }

    static float energeticStaminaMultiplier(float stamina) {
        float delta = stamina - NEUTRAL_STAMINA;
        float multiplier = 1.0f + delta * 0.85f;
        return MathHelper.clamp(multiplier, 0.55f, 1.25f);
    }

    static float restorativeStaminaMultiplier(float stamina) {
        float delta = stamina - NEUTRAL_STAMINA;
        float multiplier = 1.08f - delta * 0.9f;
        return MathHelper.clamp(multiplier, 0.65f, 1.25f);
    }

    static float balancedStaminaMultiplier(float stamina) {
        float delta = Math.abs(stamina - NEUTRAL_STAMINA);
        float multiplier = 1.05f - delta * 0.8f;
        return MathHelper.clamp(multiplier, 0.6f, 1.15f);
    }

    static float socialCenteredMultiplier(float socialCharge) {
        float delta = socialCharge - NEUTRAL_SOCIAL;
        float multiplier = 1.0f + delta * 0.75f;
        return MathHelper.clamp(multiplier, 0.6f, 1.2f);
    }
}
