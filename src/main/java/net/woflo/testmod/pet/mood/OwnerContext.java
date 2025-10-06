package net.woflo.testmod.pet.mood;

/**
 * Provides live context about the pet's owner so the engine can persist
 * protective emotions while the owner is in danger.
 */
@FunctionalInterface
public interface OwnerContext {
    /**
     * @return a value between {@code 0.0f} (safe) and {@code 1.0f} (extreme danger)
     * describing how threatened the owner currently is.
     */
    float dangerLevel();

    default boolean isOwnerInDanger() {
        return normalizedDangerLevel() > 0f;
    }

    default float normalizedDangerLevel() {
        float raw = dangerLevel();
        if (Float.isNaN(raw) || Float.isInfinite(raw)) {
            return 0f;
        }
        if (raw <= 0f) {
            return 0f;
        }
        return Math.min(1f, raw);
    }

    static OwnerContext safe() {
        return () -> 0f;
    }
}
