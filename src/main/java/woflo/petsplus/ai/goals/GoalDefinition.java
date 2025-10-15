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
    /**
     * Whether this goal represents a showcase or otherwise major activity that should suppress
     * automatic follow behaviour while active.
     */
    boolean marksMajorActivity,
    GoalFactory factory
) {

    private static final float DEFAULT_MIN_BIAS = 0.05f;
    private static final float INCOMPATIBLE_SENTINEL = 0.08f;
    private static final float COMPATIBILITY_THRESHOLD = 0.12f;
    private static final float BATTERY_DEPLETION_EPSILON = 0.001f;

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
        return getEnergyBias(momentum) > COMPATIBILITY_THRESHOLD;
    }

    public boolean isEnergyCompatible(BehaviouralEnergyProfile profile) {
        if (profile == null) {
            return isEnergyCompatible(0.5f);
        }

        float momentumBias = getEnergyBias(profile.momentum());
        if (momentumBias <= INCOMPATIBLE_SENTINEL) {
            return false;
        }

        DomainEnergy domainEnergy = resolveDomainEnergy(profile, momentumBias);
        if (domainEnergy.limiting <= DEFAULT_MIN_BIAS + BATTERY_DEPLETION_EPSILON) {
            return false;
        }

        float combined = computeEnergyBiasRaw(profile, momentumBias, domainEnergy);
        return combined > COMPATIBILITY_THRESHOLD;
    }

    public float getEnergyBias(float momentum) {
        float min = energyRange.x;
        float max = energyRange.y;
        float center = (min + max) / 2f;
        float halfRange = (max - min) / 2f;

        if (momentum < min || momentum > max) {
            // Behaviour evaluation treats this sentinel as "momentum incompatible" and will skip
            // blending so the overall bias stays beneath the 0.12 viability threshold.
            return INCOMPATIBLE_SENTINEL;
        }

        float distanceFromCenter = Math.abs(momentum - center);
        float normalizedDistance = halfRange <= 0 ? 0 : distanceFromCenter / halfRange;
        return 1.0f - (normalizedDistance * 0.5f);
    }

    public float getEnergyBias(BehaviouralEnergyProfile profile) {
        if (profile == null) {
            return getEnergyBias(0.5f);
        }

        float baseBias = getEnergyBias(profile.momentum());

        if (baseBias <= INCOMPATIBLE_SENTINEL) {
            // Momentum fell outside the configured band; treat the goal as energy incompatible even
            // if stamina or social reserves look healthy to avoid "free" activations at low momentum.
            return INCOMPATIBLE_SENTINEL;
        }

        DomainEnergy domainEnergy = resolveDomainEnergy(profile, baseBias);
        if (domainEnergy.limiting <= DEFAULT_MIN_BIAS + BATTERY_DEPLETION_EPSILON) {
            return INCOMPATIBLE_SENTINEL;
        }

        float combined = computeEnergyBiasRaw(profile, baseBias, domainEnergy);

        if (combined <= COMPATIBILITY_THRESHOLD) {
            // Low stamina, focus, or social charge can now flag a goal as incompatible by keeping
            // the raw blend below the activation threshold before desirability clamps run.
            return INCOMPATIBLE_SENTINEL;
        }

        return MathHelper.clamp(combined, DEFAULT_MIN_BIAS, 1.0f);
    }

    private float computeEnergyBiasRaw(BehaviouralEnergyProfile profile, float baseBias, DomainEnergy domainEnergy) {
        float domainBias = domainEnergy.combined;

        if (category == Category.IDLE_QUIRK) {
            float combined = (baseBias * 0.55f) + (domainBias * 0.45f);

            if (socialIdleBias) {
                float socialBias = favourCenteredBattery(profile.socialCharge(), 0.45f, 0.28f);
                combined = (combined * 0.75f) + (socialBias * 0.25f);
            }

            return combined;
        }

        float baseWeight;
        float domainWeight;

        switch (category) {
            case PLAY, WANDER -> {
                baseWeight = 0.5f;
                domainWeight = 0.5f;
            }
            case SOCIAL -> {
                baseWeight = 0.4f;
                domainWeight = 0.6f;
            }
            case SPECIAL -> {
                baseWeight = 0.45f;
                domainWeight = 0.55f;
            }
            default -> {
                baseWeight = 0.6f;
                domainWeight = 0.4f;
            }
        }

        return (baseBias * baseWeight) + (domainBias * domainWeight);
    }

    private DomainEnergy resolveDomainEnergy(BehaviouralEnergyProfile profile, float baseBias) {
        return switch (category) {
            case IDLE_QUIRK -> resolveIdleDomainEnergy(profile);
            case PLAY, WANDER -> {
                float staminaBias = batteryBias(profile.physicalStamina(), 0.65f, 0.22f);
                yield new DomainEnergy(staminaBias, staminaBias);
            }
            case SOCIAL -> {
                float socialBias = batteryBias(profile.socialCharge(), 0.45f, 0.25f);
                yield new DomainEnergy(socialBias, socialBias);
            }
            case SPECIAL -> resolveSpecialDomainEnergy(profile);
            default -> new DomainEnergy(baseBias, baseBias);
        };
    }

    private DomainEnergy resolveIdleDomainEnergy(BehaviouralEnergyProfile profile) {
        float centre = (energyRange.x + energyRange.y) / 2f;
        float staminaBias = resolveIdleStaminaBias(profile, centre);
        float limiting = staminaBias;

        if (socialIdleBias) {
            float socialCharge = profile.socialCharge();
            float socialBias;
            if (socialCharge <= 0.15f + BATTERY_DEPLETION_EPSILON) {
                socialBias = DEFAULT_MIN_BIAS;
            } else {
                socialBias = favourCenteredBattery(socialCharge, 0.45f, 0.28f);
            }
            limiting = Math.min(limiting, socialBias);
        }

        return new DomainEnergy(staminaBias, limiting);
    }

    private DomainEnergy resolveSpecialDomainEnergy(BehaviouralEnergyProfile profile) {
        float focusBias = batteryBias(profile.mentalFocus(), 0.6f, 0.25f);
        float limiting = focusBias;

        if (idleStaminaBias == IdleStaminaBias.NONE) {
            return new DomainEnergy(focusBias, limiting);
        }

        float centre = (energyRange.x + energyRange.y) / 2f;
        float staminaBias = resolveIdleStaminaBias(profile, centre);
        limiting = Math.min(limiting, staminaBias);

        float combined = blendFocusAndStamina(focusBias, staminaBias);
        return new DomainEnergy(combined, limiting);
    }

    private float blendFocusAndStamina(float focusBias, float staminaBias) {
        return (focusBias * 0.6f) + (staminaBias * 0.4f);
    }

    private float resolveIdleStaminaBias(BehaviouralEnergyProfile profile, float centre) {
        float stamina = profile.physicalStamina();

        return switch (idleStaminaBias) {
            case LOW -> {
                float tolerance = 0.22f;
                if (isAbovePreferredWindow(stamina, centre, tolerance)) {
                    yield DEFAULT_MIN_BIAS;
                }
                yield favourLowBattery(stamina, centre, tolerance);
            }
            case HIGH -> {
                float tolerance = 0.25f;
                if (isBelowPreferredWindow(stamina, centre, tolerance)) {
                    yield DEFAULT_MIN_BIAS;
                }
                yield favourHighBattery(stamina, centre, tolerance);
            }
            case CENTERED -> {
                float tolerance = 0.2f;
                if (isOutsidePreferredWindow(stamina, centre, tolerance)) {
                    yield DEFAULT_MIN_BIAS;
                }
                yield favourCenteredBattery(stamina, centre, tolerance);
            }
            case NONE -> favourCenteredBattery(stamina, centre, 0.2f);
        };
    }

    private record DomainEnergy(float combined, float limiting) { }

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

    private static boolean isAbovePreferredWindow(float value, float midpoint, float tolerance) {
        float upper = Math.min(1f, midpoint + tolerance);
        return value >= upper - BATTERY_DEPLETION_EPSILON;
    }

    private static boolean isBelowPreferredWindow(float value, float midpoint, float tolerance) {
        float lower = Math.max(0f, midpoint - tolerance);
        return value <= lower + BATTERY_DEPLETION_EPSILON;
    }

    private static boolean isOutsidePreferredWindow(float value, float midpoint, float tolerance) {
        return isBelowPreferredWindow(value, midpoint, tolerance) || isAbovePreferredWindow(value, midpoint, tolerance);
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

