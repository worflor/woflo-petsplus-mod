package woflo.petsplus.ai.goals;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.Objects;

/**
 * Immutable definition for a behavioral goal that can be registered in the adaptive AI registry.
 */
public record GoalDefinition(
    Identifier id,
    Category category,
    int priority,
    int minCooldownTicks,
    int maxCooldownTicks,
    MobCapabilities.CapabilityRequirement requirement,
    Vec2f energyRange,
    IdleStaminaBias idleStaminaBias,
    boolean socialIdleBias,
    GoalFactory factory
) {

    private static final float DEFAULT_MIN_BIAS = 0.05f;

    public GoalDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(factory, "factory");

        energyRange = energyRange != null ? energyRange : new Vec2f(0.0f, 1.0f);
        idleStaminaBias = idleStaminaBias != null ? idleStaminaBias : IdleStaminaBias.CENTERED;
    }

    public boolean isCompatible(MobCapabilities.CapabilityProfile profile) {
        return requirement.test(profile);
    }

    public boolean isEnergyCompatible(float momentum) {
        return getEnergyBias(momentum) > DEFAULT_MIN_BIAS;
    }

    public boolean isEnergyCompatible(BehaviouralEnergyProfile profile) {
        return getEnergyBias(profile) > 0.12f;
    }

    public float getEnergyBias(float momentum) {
        float min = energyRange.x;
        float max = energyRange.y;
        float center = (min + max) / 2f;
        float halfRange = (max - min) / 2f;

        if (momentum < min || momentum > max) {
            return 0.1f;
        }

        float distanceFromCenter = Math.abs(momentum - center);
        float normalizedDistance = halfRange <= 0 ? 0 : distanceFromCenter / halfRange;
        return 1.0f - (normalizedDistance * 0.5f);
    }

    public float getEnergyBias(BehaviouralEnergyProfile profile) {
        if (profile == null) {
            return getEnergyBias(0.5f);
        }

        if (category == Category.IDLE_QUIRK) {
            float baseBias = getEnergyBias(profile.momentum());
            float centre = (energyRange.x + energyRange.y) / 2f;
            float staminaBias = switch (idleStaminaBias) {
                case LOW -> favourLowBattery(profile.physicalStamina(), centre, 0.22f);
                case HIGH -> favourHighBattery(profile.physicalStamina(), centre, 0.25f);
                case CENTERED, NONE -> favourCenteredBattery(profile.physicalStamina(), centre, 0.2f);
            };

            float combined = MathHelper.clamp((baseBias * 0.55f) + (staminaBias * 0.45f), DEFAULT_MIN_BIAS, 1.0f);

            if (socialIdleBias) {
                float socialBias = favourCenteredBattery(profile.socialCharge(), 0.45f, 0.28f);
                combined = MathHelper.clamp((combined * 0.75f) + (socialBias * 0.25f), DEFAULT_MIN_BIAS, 1.0f);
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
        return MathHelper.clamp(combined, DEFAULT_MIN_BIAS, 1.0f);
    }

    public AdaptiveGoal createGoal(MobEntity mob) {
        return factory.create(mob);
    }

    private static float batteryBias(float value, float baseline, float slack) {
        float min = Math.max(0f, baseline - slack);
        if (value <= min) {
            return DEFAULT_MIN_BIAS;
        }
        if (value >= baseline) {
            float overshootRange = Math.max(0.0001f, 1f - baseline);
            float overshoot = MathHelper.clamp((value - baseline) / overshootRange, 0f, 1f);
            return MathHelper.clamp(0.8f + overshoot * 0.2f, DEFAULT_MIN_BIAS, 1.0f);
        }
        float baselineRange = Math.max(0.0001f, baseline - min);
        float normalized = MathHelper.clamp((value - min) / baselineRange, 0f, 1f);
        return MathHelper.clamp(0.2f + normalized * 0.6f, DEFAULT_MIN_BIAS, 1.0f);
    }

    private static float favourLowBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value <= lower) {
            return 0.95f;
        }
        if (value >= upper) {
            float overshoot = MathHelper.clamp((value - upper) / Math.max(1f - upper, 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - overshoot * 0.4f, DEFAULT_MIN_BIAS, 1.0f);
        }

        float span = Math.max(upper - lower, 0.0001f);
        float t = MathHelper.clamp((value - lower) / span, 0f, 1f);
        return MathHelper.clamp(0.95f - t * 0.35f, DEFAULT_MIN_BIAS, 1.0f);
    }

    private static float favourHighBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value >= upper) {
            return 0.95f;
        }
        if (value <= lower) {
            float deficit = MathHelper.clamp((lower - value) / Math.max(lower, 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - deficit * 0.4f, DEFAULT_MIN_BIAS, 1.0f);
        }

        float span = Math.max(upper - lower, 0.0001f);
        float t = MathHelper.clamp((value - lower) / span, 0f, 1f);
        return MathHelper.clamp(0.55f + t * 0.4f, DEFAULT_MIN_BIAS, 1.0f);
    }

    private static float favourCenteredBattery(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        float upper = Math.min(1f, midpoint + tolerance);

        if (value <= lower || value >= upper) {
            float distance = Math.min(Math.abs(value - lower), Math.abs(value - upper));
            float normalized = MathHelper.clamp(distance / (tolerance + 0.0001f), 0f, 1f);
            return MathHelper.clamp(0.55f - normalized * 0.35f, DEFAULT_MIN_BIAS, 1.0f);
        }

        float halfWindow = Math.max((upper - lower) / 2f, 0.0001f);
        float normalizedDistance = MathHelper.clamp(Math.abs(value - midpoint) / halfWindow, 0f, 1f);
        return MathHelper.clamp(0.95f - normalizedDistance * 0.35f, DEFAULT_MIN_BIAS, 1.0f);
    }

    public enum Category {
        IDLE_QUIRK,
        WANDER,
        PLAY,
        SOCIAL,
        SPECIAL
    }

    public enum IdleStaminaBias {
        NONE,
        CENTERED,
        LOW,
        HIGH
    }

    @FunctionalInterface
    public interface GoalFactory {
        AdaptiveGoal create(MobEntity mob);
    }
}

