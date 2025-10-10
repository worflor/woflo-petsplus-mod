package woflo.petsplus.ai.goals;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

/**
 * Registry of all behavioral goals pets can consider.
 * Each type defines capability requirements, preventing goals from
 * being suggested to mobs that can't execute them.
 */
public enum GoalType {
    // === UNIVERSAL IDLE QUIRKS (works with any mob) ===
    STRETCH_AND_YAW(Category.IDLE_QUIRK, 28, 5, 80, 
        MobCapabilities.CapabilityRequirement.any(), new Vec2f(0.3f, 0.7f)),
    
    CIRCLE_SPOT(Category.IDLE_QUIRK, 28, 8, 120,
        MobCapabilities.CapabilityRequirement.any(), new Vec2f(0.3f, 0.7f)),
    
    TAIL_CHASE(Category.IDLE_QUIRK, 28, 15, 200,
        MobCapabilities.CapabilityRequirement.any(), new Vec2f(0.6f, 1.0f)),
    
    // === LAND-SPECIFIC IDLE QUIRKS ===
    SNIFF_GROUND(Category.IDLE_QUIRK, 28, 12, 100,
        caps -> caps.canWander() && caps.prefersLand(), new Vec2f(0.3f, 0.7f)),
    
    POUNCE_PRACTICE(Category.IDLE_QUIRK, 28, 10, 150,
        caps -> caps.canJump() && caps.prefersLand(), new Vec2f(0.6f, 1.0f)),
    
    PERK_EARS_SCAN(Category.IDLE_QUIRK, 28, 8, 90,
        caps -> caps.canMakeSound() && caps.prefersLand(), new Vec2f(0.3f, 0.7f)),
    
    SIT_SPHINX_POSE(Category.IDLE_QUIRK, 28, 15, 180,
        caps -> caps.canSit(), new Vec2f(0.0f, 0.4f)),
    
    // === FLYING-SPECIFIC IDLE QUIRKS ===
    PREEN_FEATHERS(Category.IDLE_QUIRK, 28, 10, 150,
        caps -> caps.canFly(), new Vec2f(0.0f, 0.4f)),
    
    WING_FLUTTER(Category.IDLE_QUIRK, 28, 12, 120,
        caps -> caps.canFly(), new Vec2f(0.3f, 0.7f)),
    
    PERCH_HOP(Category.IDLE_QUIRK, 27, 8, 100,
        caps -> caps.canFly(), new Vec2f(0.6f, 1.0f)),
    
    // === AQUATIC-SPECIFIC IDLE QUIRKS ===
    FLOAT_IDLE(Category.IDLE_QUIRK, 28, 8, 100,
        caps -> caps.canSwim() && caps.prefersWater(), new Vec2f(0.0f, 0.4f)),
    
    BUBBLE_PLAY(Category.IDLE_QUIRK, 28, 10, 120,
        caps -> caps.canSwim() && caps.prefersWater(), new Vec2f(0.6f, 1.0f)),
    
    SURFACE_BREATH(Category.IDLE_QUIRK, 27, 5, 80,
        caps -> caps.canSwim() && caps.prefersWater(), new Vec2f(0.0f, 1.0f)),
    
    // === WANDER VARIANTS ===
    CASUAL_WANDER(Category.WANDER, 20, 0, 0,
        caps -> caps.canWander(), new Vec2f(0.3f, 0.7f)),
    
    AERIAL_PATROL(Category.WANDER, 20, 0, 0,
        caps -> caps.canFly(), new Vec2f(0.3f, 0.7f)),
    
    WATER_CRUISE(Category.WANDER, 20, 0, 0,
        caps -> caps.canSwim() && caps.prefersWater(), new Vec2f(0.3f, 0.7f)),
    
    SCENT_TRAIL_FOLLOW(Category.WANDER, 20, 0, 0,
        caps -> caps.canWander() && caps.prefersLand(), new Vec2f(0.3f, 0.7f)),
    
    PURPOSEFUL_PATROL(Category.WANDER, 20, 0, 0,
        caps -> caps.canWander(), new Vec2f(0.6f, 1.0f)),
    
    OWNER_ORBIT(Category.WANDER, 20, 0, 0,
        caps -> caps.hasOwner(), new Vec2f(0.0f, 1.0f)),
    
    // === PLAY BEHAVIORS ===
    TOY_POUNCE(Category.PLAY, 18, 0, 0,
        caps -> caps.canJump(), new Vec2f(0.6f, 1.0f)),
    
    PARKOUR_CHALLENGE(Category.PLAY, 18, 0, 0,
        caps -> caps.canJump() && caps.canWander(), new Vec2f(0.6f, 1.0f)),
    
    FETCH_ITEM(Category.PLAY, 18, 0, 0,
        caps -> caps.canPickUpItems() && caps.hasOwner(), new Vec2f(0.6f, 1.0f)),
    
    DIVE_PLAY(Category.PLAY, 18, 0, 0,
        caps -> caps.canSwim(), new Vec2f(0.6f, 1.0f)),
    
    AERIAL_ACROBATICS(Category.PLAY, 18, 0, 0,
        caps -> caps.canFly(), new Vec2f(0.6f, 1.0f)),
    
    WATER_SPLASH(Category.PLAY, 18, 0, 0,
        caps -> caps.canSwim() && caps.canWander(), new Vec2f(0.6f, 1.0f)),
    
    // === SOCIAL BEHAVIORS ===
    LEAN_AGAINST_OWNER(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner() && caps.canWander(), new Vec2f(0.0f, 0.6f)),
    
    PARALLEL_PLAY(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner(), new Vec2f(0.3f, 0.8f)),
    
    SHOW_OFF_TRICK(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner(), new Vec2f(0.6f, 1.0f)),
    
    GIFT_BRINGING(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner() && caps.canPickUpItems(), new Vec2f(0.3f, 0.7f)),
    
    PERCH_ON_SHOULDER(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner() && caps.canFly() && caps.isSmallSize(), new Vec2f(0.0f, 0.7f)),
    
    ORBIT_SWIM(Category.SOCIAL, 15, 0, 0,
        caps -> caps.hasOwner() && caps.canSwim(), new Vec2f(0.3f, 0.8f)),
    
    EYE_CONTACT(Category.SOCIAL, 25, 0, 0,
        caps -> caps.hasOwner(), new Vec2f(0.0f, 1.0f)),
    
    CROUCH_APPROACH_RESPONSE(Category.SOCIAL, 12, 100, 300,
        caps -> caps.hasOwner(), new Vec2f(0.3f, 0.9f)),
    
    // === SPECIAL BEHAVIORS ===
    HIDE_AND_SEEK(Category.SPECIAL, 16, 0, 0,
        caps -> caps.hasOwner() && caps.canWander(), new Vec2f(0.6f, 1.0f)),
    
    INVESTIGATE_BLOCK(Category.SPECIAL, 16, 0, 0,
        caps -> caps.canWander(), new Vec2f(0.3f, 0.7f)),
    
    STARGAZING(Category.SPECIAL, 16, 0, 0,
        caps -> caps.canSit(), new Vec2f(0.0f, 0.5f));
    
    private final Category category;
    private final int priority;
    private final int minCooldownTicks;
    private final int maxCooldownTicks;
    private final MobCapabilities.CapabilityRequirement requirement;
    private final Vec2f energyRange;
    
    GoalType(Category category, int priority, int minCooldownTicks, int maxCooldownTicks,
             MobCapabilities.CapabilityRequirement requirement, Vec2f energyRange) {
        this.category = category;
        this.priority = priority;
        this.minCooldownTicks = minCooldownTicks;
        this.maxCooldownTicks = maxCooldownTicks;
        this.requirement = requirement;
        this.energyRange = energyRange;
    }
    
    public Category getCategory() {
        return category;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public int getMinCooldownTicks() {
        return minCooldownTicks;
    }
    
    public int getMaxCooldownTicks() {
        return maxCooldownTicks;
    }
    
    /**
     * Check if this goal can be executed by a mob with the given capabilities.
     */
    public boolean isCompatible(MobCapabilities.CapabilityProfile profile) {
        return requirement.test(profile);
    }
    
    /**
     * Check if the current momentum is within this goal's energy range.
     * Uses soft gating: returns false only if far outside range.
     */
    public boolean isEnergyCompatible(float momentum) {
        // Soft gate: allow some flexibility
        return getEnergyBias(momentum) > 0.05f;
    }

    public boolean isEnergyCompatible(BehaviouralEnergyProfile profile) {
        return getEnergyBias(profile) > 0.12f;
    }

    /**
     * Get energy bias multiplier for this goal based on current momentum.
     * Returns 1.0 at optimal center, lower at range edges, 0.1 if outside range.
     * Maintains behavioral unpredictability while gating spam.
     */
    public float getEnergyBias(float momentum) {
        float min = energyRange.x;
        float max = energyRange.y;
        float center = (min + max) / 2f;
        float halfRange = (max - min) / 2f;

        // If outside range, return low bias (0.1) for unpredictability
        if (momentum < min || momentum > max) {
            return 0.1f;
        }

        // Inside range: calculate distance from center
        float distanceFromCenter = Math.abs(momentum - center);
        float normalizedDistance = distanceFromCenter / halfRange; // 0 at center, 1 at edges

        // Linear falloff from center (1.0) to edges (0.5)
        // At center: 1.0, at edges: 0.5
        return 1.0f - (normalizedDistance * 0.5f);
    }

    public float getEnergyBias(BehaviouralEnergyProfile profile) {
        if (profile == null) {
            return getEnergyBias(0.5f);
        }
        if (category == Category.IDLE_QUIRK) {
            float baseBias = getEnergyBias(profile.momentum());
            float staminaBias;
            float centre = (energyRange.x + energyRange.y) / 2f;

            switch (this) {
                case STRETCH_AND_YAW, SIT_SPHINX_POSE, PREEN_FEATHERS, FLOAT_IDLE, SURFACE_BREATH ->
                    staminaBias = favourLowBattery(profile.physicalStamina(), centre, 0.22f);
                case TAIL_CHASE, POUNCE_PRACTICE, PERCH_HOP, BUBBLE_PLAY, WING_FLUTTER ->
                    staminaBias = favourHighBattery(profile.physicalStamina(), centre, 0.25f);
                default ->
                    staminaBias = favourCenteredBattery(profile.physicalStamina(), centre, 0.2f);
            }

            float combined = MathHelper.clamp((baseBias * 0.55f) + (staminaBias * 0.45f), 0.05f, 1.0f);

            if (usesSocialIdleBias()) {
                float socialBias = favourCenteredBattery(profile.socialCharge(), 0.45f, 0.28f);
                combined = MathHelper.clamp((combined * 0.75f) + (socialBias * 0.25f), 0.05f, 1.0f);
            }

            return combined;
        }

        float baseBias = getEnergyBias(profile.momentum());
        float domainBias;
        float baseWeight;
        float domainWeight;

        switch (category) {
            case PLAY, WANDER -> {
                domainBias = batteryBias(profile.physicalStamina(), 0.65f, 0.22f);
                baseWeight = 0.5f;
                domainWeight = 0.5f;
            }
            case SOCIAL -> {
                domainBias = batteryBias(profile.socialCharge(), 0.45f, 0.25f);
                baseWeight = 0.4f;
                domainWeight = 0.6f;
            }
            case SPECIAL -> {
                domainBias = batteryBias(profile.mentalFocus(), 0.6f, 0.25f);
                baseWeight = 0.45f;
                domainWeight = 0.55f;
            }
            default -> {
                domainBias = baseBias;
                baseWeight = 0.6f;
                domainWeight = 0.4f;
            }
        }

        float combined = (baseBias * baseWeight) + (domainBias * domainWeight);
        return MathHelper.clamp(combined, 0.05f, 1.0f);
    }

    private boolean usesSocialIdleBias() {
        return this == TAIL_CHASE || this == BUBBLE_PLAY || this == PERCH_HOP;
    }

    private static float batteryBias(float value, float baseline, float slack) {
        float min = Math.max(0f, baseline - slack);
        if (value <= min) {
            return 0.05f;
        }
        if (value >= baseline) {
            float overshootRange = Math.max(0.0001f, 1f - baseline);
            float overshoot = MathHelper.clamp((value - baseline) / overshootRange, 0f, 1f);
            return MathHelper.clamp(0.8f + overshoot * 0.2f, 0.05f, 1.0f);
        }
        float baselineRange = Math.max(0.0001f, baseline - min);
        float normalized = MathHelper.clamp((value - min) / baselineRange, 0f, 1f);
        return MathHelper.clamp(0.2f + normalized * 0.6f, 0.05f, 1.0f);
    }

    private static float favourLowBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value <= lower) {
            return 0.95f;
        }
        if (value >= upper) {
            float overshoot = MathHelper.clamp((value - upper) / Math.max(1f - upper, 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - overshoot * 0.4f, 0.05f, 1.0f);
        }

        float span = Math.max(upper - lower, 0.0001f);
        float t = MathHelper.clamp((value - lower) / span, 0f, 1f);
        return MathHelper.clamp(0.95f - t * 0.35f, 0.05f, 1.0f);
    }

    private static float favourHighBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value >= upper) {
            return 0.95f;
        }
        if (value <= lower) {
            float deficit = MathHelper.clamp((lower - value) / Math.max(lower, 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - deficit * 0.4f, 0.05f, 1.0f);
        }

        float span = Math.max(upper - lower, 0.0001f);
        float t = MathHelper.clamp((value - lower) / span, 0f, 1f);
        return MathHelper.clamp(0.55f + t * 0.4f, 0.05f, 1.0f);
    }

    private static float favourCenteredBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value <= lower || value >= upper) {
            float distance = Math.min(Math.abs(value - lower), Math.abs(value - upper));
            float normalized = MathHelper.clamp(distance / (tolerance + 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - normalized * 0.35f, 0.05f, 1.0f);
        }

        float halfWindow = Math.max((upper - lower) / 2f, 0.0001f);
        float normalizedDistance = MathHelper.clamp(Math.abs(value - midpoint) / halfWindow, 0f, 1f);
        return MathHelper.clamp(0.95f - normalizedDistance * 0.35f, 0.05f, 1.0f);
    }
    
    /**
     * Goal categories for organizational purposes.
     */
    public enum Category {
        IDLE_QUIRK,    // Short micro-behaviors when idle
        WANDER,        // Movement patterns
        PLAY,          // Play activities
        SOCIAL,        // Owner interaction
        SPECIAL        // Context-specific behaviors
    }
}
