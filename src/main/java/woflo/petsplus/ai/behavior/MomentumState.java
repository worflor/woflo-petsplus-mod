package woflo.petsplus.ai.behavior;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

/**
 * Immutable snapshot of a pet's behavioural momentum state.
 * Provides convenient categorization and goal selection bias.
 * 
 * Lightweight value object - no tick overhead, just data access.
 */
public record MomentumState(
    float momentum,          // 0.0-1.0 raw momentum value
    EnergyLevel level,       // Categorized energy level
    float restBias,          // 0.0-1.0 preference for rest/idle
    float activeBias,        // 0.0-1.0 preference for active/play
    float mentalBias,        // 0.0-1.0 preference for mental/work
    BehaviouralEnergyProfile energyProfile // Full behavioural energy stack snapshot
) {
    
    /**
     * Energy level categories for intuitive AI branching.
     */
    public enum EnergyLevel {
        EXHAUSTED,    // 0.0-0.2: Need rest urgently
        TIRED,        // 0.2-0.4: Prefer calm activities
        NEUTRAL,      // 0.4-0.6: Balanced, any activity fine
        ENERGETIC,    // 0.6-0.8: Prefer active play
        HYPERACTIVE   // 0.8-1.0: Must burn energy
    }
    
    /**
     * Capture current momentum state from a pet component.
     */
    public static MomentumState capture(PetComponent pc) {
        if (pc == null || pc.getMoodEngine() == null) {
            return neutral();
        }

        BehaviouralEnergyProfile profile = pc.getMoodEngine().getBehaviouralEnergyProfile();
        float momentum = profile.momentum();
        EnergyLevel level = categorize(momentum);

        // Calculate biases - smooth curves, not hard thresholds
        float restBias = calculateRestBias(momentum);
        float activeBias = calculateActiveBias(momentum);
        float mentalBias = calculateMentalBias(momentum);

        return new MomentumState(momentum, level, restBias, activeBias, mentalBias, profile);
    }
    
    /**
     * Capture from mob entity directly.
     */
    public static MomentumState capture(MobEntity mob) {
        PetComponent pc = PetComponent.get(mob);
        return capture(pc);
    }
    
    /**
     * Neutral default state.
     */
    public static MomentumState neutral() {
        return new MomentumState(0.5f, EnergyLevel.NEUTRAL, 0.5f, 0.5f, 0.5f, BehaviouralEnergyProfile.neutral());
    }
    
    /**
     * Categorize momentum into energy level.
     */
    private static EnergyLevel categorize(float momentum) {
        if (momentum < 0.2f) return EnergyLevel.EXHAUSTED;
        if (momentum < 0.4f) return EnergyLevel.TIRED;
        if (momentum < 0.6f) return EnergyLevel.NEUTRAL;
        if (momentum < 0.8f) return EnergyLevel.ENERGETIC;
        return EnergyLevel.HYPERACTIVE;
    }
    
    /**
     * Calculate rest/idle activity bias (high when tired).
     */
    private static float calculateRestBias(float momentum) {
        // Inverse sigmoid: high at low momentum, low at high momentum
        float inverted = 1.0f - momentum;
        return smoothstep(0.0f, 0.6f, inverted);
    }
    
    /**
     * Calculate active/play activity bias (high when energetic).
     */
    private static float calculateActiveBias(float momentum) {
        // Sigmoid: low at low momentum, high at high momentum
        return smoothstep(0.4f, 1.0f, momentum);
    }
    
    /**
     * Calculate mental/work activity bias (moderate when balanced).
     */
    private static float calculateMentalBias(float momentum) {
        // Bell curve: peaks at 0.5 (neutral state)
        float deviation = Math.abs(momentum - 0.5f);
        return 1.0f - (deviation * 2.0f);
    }
    
    /**
     * Smooth interpolation curve for natural bias transitions.
     */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Get bias multiplier for a specific goal category.
     * Multiply goal priority/weight by this value for momentum-aware selection.
     */
    public float getBiasFor(GoalCategory category) {
        return switch (category) {
            case REST, IDLE -> restBias;
            case PLAY, EXPLORE -> activeBias;
            case WORK, TRAINING -> mentalBias;
            case SOCIAL -> (activeBias + mentalBias) * 0.5f; // Balanced
            default -> 1.0f; // No bias
        };
    }
    
    /**
     * Goal category for momentum biasing.
     */
    public enum GoalCategory {
        REST,      // Sitting, sleeping, perching
        IDLE,      // Standing around, watching
        PLAY,      // Running, jumping, chasing
        EXPLORE,   // Wandering, investigating
        WORK,      // Fetching, guarding, hunting
        TRAINING,  // Learning, practicing
        SOCIAL,    // Interacting with owner/pets
        COMBAT,    // Fighting (no momentum bias)
        SURVIVAL   // Eating, drinking (no momentum bias)
    }
    
    /**
     * Check if pet should strongly prefer rest.
     */
    public boolean needsRest() {
        return level == EnergyLevel.EXHAUSTED || restBias > 0.7f;
    }
    
    /**
     * Check if pet should burn energy.
     */
    public boolean needsActivity() {
        return level == EnergyLevel.HYPERACTIVE || activeBias > 0.8f;
    }
    
    /**
     * Check if pet is in balanced state for any activity.
     */
    public boolean isBalanced() {
        return level == EnergyLevel.NEUTRAL;
    }
    
    /**
     * Get human-readable description for debugging.
     */
    public String describe() {
        return String.format("%s (%.2f) - Rest:%.2f Active:%.2f Mental:%.2f", 
            level, momentum, restBias, activeBias, mentalBias);
    }
}
