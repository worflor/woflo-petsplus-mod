package woflo.petsplus.state.emotions;

import net.minecraft.util.math.MathHelper;

/**
 * Snapshot of the behavioural energy stack. Captures raw activity samples,
 * normalized activity weights, and the derived domain batteries that sit
 * alongside the global behavioural momentum value.
 */
public record BehaviouralEnergyProfile(
    float momentum,
    float rawPhysicalActivity,
    float rawMentalActivity,
    float rawSocialActivity,
    float normalizedPhysicalActivity,
    float normalizedMentalActivity,
    float normalizedSocialActivity,
    float physicalStamina,
    float mentalFocus,
    float socialCharge
) {
    /**
     * Neutral profile used when the mood engine has not produced values yet.
     */
    public static BehaviouralEnergyProfile neutral() {
        return new BehaviouralEnergyProfile(
            0.5f,
            0f,
            0f,
            0f,
            0f,
            0f,
            0f,
            0.65f,
            0.6f,
            0.45f
        );
    }

    /**
     * Combined normalized activity taking the same diminishing return that the
     * momentum target uses.
     */
    public float blendedActivity() {
        float total = normalizedPhysicalActivity + normalizedMentalActivity + normalizedSocialActivity;
        return MathHelper.clamp(total / (1f + total * 0.3f), 0f, 1f);
    }
}
