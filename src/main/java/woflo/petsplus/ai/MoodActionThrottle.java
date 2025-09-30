package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

/**
 * Shared helper that controls how frequently a mood goal may activate.
 */
public final class MoodActionThrottle {
    private final MoodGoalThrottleConfig config;
    private long lastStartTick = Long.MIN_VALUE / 4;
    private long lastStopTick = Long.MIN_VALUE / 4;
    private boolean active;
    private float chanceAccumulator;
    private long lastChanceTick = Long.MIN_VALUE / 4;

    public MoodActionThrottle(MoodGoalThrottleConfig config) {
        this.config = config;
    }

    public MoodGoalThrottleConfig config() {
        return config;
    }

    public boolean canStart(
        MobEntity mob,
        PetComponent component,
        PetComponent.Mood mood,
        long now,
        boolean bypassCooldown
    ) {
        if (config == null) {
            return true;
        }
        if (active) {
            long elapsed = now - lastStartTick;
            if (elapsed < config.minActiveTicks()) {
                return false;
            }
        }
        if (!bypassCooldown) {
            long cooldownElapsed = now - lastStopTick;
            if (cooldownElapsed < config.minCooldownTicks()) {
                return false;
            }
        }
        int rollBound = computeRollBound(component, mood, now);
        if (rollBound <= 1) {
            chanceAccumulator = 0.0f;
            lastChanceTick = now;
            return true;
        }
        if (now != lastChanceTick) {
            float increment = 1.0f / (float) rollBound;
            chanceAccumulator = MathHelper.clamp(chanceAccumulator + increment, 0.0f, 1.0f);
            lastChanceTick = now;
        }
        float roll = mob.getRandom().nextFloat();
        if (roll < chanceAccumulator) {
            chanceAccumulator = 0.0f;
            return true;
        }
        return false;
    }

    private int computeRollBound(PetComponent component, PetComponent.Mood mood, long now) {
        if (config == null) {
            return 1;
        }
        int roll = config.baseChance();
        if (component != null && mood != null && config.strengthChanceBonus() > 0) {
            float strength = MathHelper.clamp(component.getMoodStrength(mood), 0.0f, 1.0f);
            roll -= Math.round(strength * config.strengthChanceBonus());
        }
        if (config.pressureIntervalTicks() > 0 && config.pressureChanceBonus() > 0 && config.maxPressureBonus() > 0) {
            long elapsed = now - Math.max(lastStopTick, lastStartTick);
            if (elapsed > 0) {
                int steps = (int) Math.min(
                    config.maxPressureBonus() / Math.max(config.pressureChanceBonus(), 1),
                    elapsed / config.pressureIntervalTicks()
                );
                roll -= steps * config.pressureChanceBonus();
            }
        }
        return MathHelper.clamp(roll, config.minRollBound(), config.maxRollBound());
    }

    public void markStarted(long now, PetComponent component, PetComponent.Mood mood) {
        active = true;
        lastStartTick = now;
        chanceAccumulator = 0.0f;
        lastChanceTick = now;
        if (Petsplus.DEBUG_MODE && component != null) {
            Petsplus.LOGGER.debug(
                "[MoodAI] {} started {} (strength {:.2f}, cooldown {} ticks)",
                component.getPet().getName().getString(),
                mood != null ? mood.name().toLowerCase() : "unknown",
                mood != null ? component.getMoodStrength(mood) : 0.0f,
                config != null ? config.minCooldownTicks() : 0
            );
        }
    }

    public void markStopped(long now, PetComponent component, PetComponent.Mood mood) {
        active = false;
        lastStopTick = now;
        chanceAccumulator = 0.0f;
        lastChanceTick = now;
        if (Petsplus.DEBUG_MODE && component != null) {
            Petsplus.LOGGER.debug(
                "[MoodAI] {} stopped {} after {} ticks",
                component.getPet().getName().getString(),
                mood != null ? mood.name().toLowerCase() : "unknown",
                now - lastStartTick
            );
        }
    }

    public boolean hasSatisfiedMinActive(long now) {
        if (!active || config == null) {
            return true;
        }
        return now - lastStartTick >= config.minActiveTicks();
    }
}
