package woflo.petsplus.stats;

/**
 * Unifies the six primary stat multipliers that drive the pet progression pipeline.
 * <p>
 * All implementations must return multiplicative modifiers (1.0f = no change), which
 * stack in the order: Nature → Imprint → Role → Level → Temporary Effects.
 */
public interface StatModifierProvider {

    /**
     * Governs health scaling, stamina, and passive resilience.
     */
    default float getVitalityMultiplier() {
        return 1.0f;
    }

    /**
     * Influences ground speed, swim speed, and rotational responsiveness.
     */
    default float getSwiftnessMultiplier() {
        return 1.0f;
    }

    /**
     * Controls direct damage output, heavy action tempo, and intimidation.
     */
    default float getMightMultiplier() {
        return 1.0f;
    }

    /**
     * Represents defensive poise, armor scaling, and stagger resistance.
     */
    default float getGuardMultiplier() {
        return 1.0f;
    }

    /**
     * Covers perception, obedience checks, and precision behavior targeting.
     */
    default float getFocusMultiplier() {
        return 1.0f;
    }

    /**
     * Handles burst movement, dodge potential, and advanced maneuvering.
     */
    default float getAgilityMultiplier() {
        return 1.0f;
    }

    /**
     * Convenience helper to short-circuit providers that have no effect.
     */
    default boolean isEmpty() {
        return getVitalityMultiplier() == 1.0f
            && getSwiftnessMultiplier() == 1.0f
            && getMightMultiplier() == 1.0f
            && getGuardMultiplier() == 1.0f
            && getFocusMultiplier() == 1.0f
            && getAgilityMultiplier() == 1.0f;
    }
}
