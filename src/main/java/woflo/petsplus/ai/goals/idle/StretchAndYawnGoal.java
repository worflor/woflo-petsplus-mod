package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Universal idle quirk - pet stretches and yawns.
 */
public class StretchAndYawnGoal extends AdaptiveGoal {
    private static final int BASE_STRETCH_DURATION = 30;

    private int animationTicks = 0;
    private int stretchDuration;
    private int yawnDuration;
    private int totalDuration;

    public StretchAndYawnGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.STRETCH_AND_YAW), EnumSet.noneOf(Control.class));
    }

    private void calculateDurations() {
        PetContext ctx = getContext();
        float stamina = ctx.physicalStamina();
        // Longer stretch for lower stamina
        float multiplier = MathHelper.clamp(1.5f - stamina, 0.8f, 1.8f);
        stretchDuration = (int) (BASE_STRETCH_DURATION * multiplier);
        yawnDuration = 15; // fixed duration for the yawn
        totalDuration = stretchDuration + yawnDuration;
    }

    @Override
    protected boolean canStartGoal() {
        if (!mob.getNavigation().isIdle() || !mob.isOnGround()) {
            return false;
        }
        // Add a check for being in water, which is not ideal for this animation
        return !mob.isTouchingWaterOrRain();
    }

    @Override
    protected boolean shouldContinueGoal() {
        return animationTicks < totalDuration;
    }

    @Override
    protected void onStartGoal() {
        animationTicks = 0;
        calculateDurations();
        // Soft anticipatory breath
        try {
            mob.playSound(SoundEvents.ENTITY_CAT_PURR, 0.2f, 0.8f + (mob.getRandom().nextFloat() * 0.2f - 0.1f));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStopGoal() {
        animationTicks = 0;
        mob.setPitch(0.0f);
    }

    @Override
    protected void onTickGoal() {
        animationTicks++;

        if (animationTicks == stretchDuration + 1) {
            // Gentle yawn effect (sound + faint breath puff)
            try {
                mob.playSound(SoundEvents.ENTITY_FOX_AMBIENT, 0.2f, 0.7f);
            } catch (Throwable ignored) {
            }
            if (!mob.getEntityWorld().isClient()) {
                try {
                    ((ServerWorld) mob.getEntityWorld()).spawnParticles(
                        ParticleTypes.CLOUD,
                        mob.getParticleX(0.5D),
                        mob.getRandomBodyY(),
                        mob.getParticleZ(0.5D),
                        2,
                        0.1, 0.0, 0.1,
                        0.02
                    );
                } catch (Throwable ignored) {
                }
            }
        }

        // Stretch phase
        if (animationTicks <= stretchDuration) {
            float progress = (float) animationTicks / stretchDuration;
            if (progress < 0.33f) { // Wind up
                mob.setPitch(MathHelper.lerp(progress / 0.33f, 0, -15));
            } else if (progress < 0.66f) { // Hold
                mob.setPitch(-15);
            } else { // Wind down
                mob.setPitch(MathHelper.lerp((progress - 0.66f) / 0.34f, -15, 0));
            }
        }
        // Yawn phase
        else {
            int yawnTicks = animationTicks - stretchDuration;
            float progress = (float) yawnTicks / yawnDuration;
            if (progress < 0.5f) {
                mob.setPitch(MathHelper.lerp(progress / 0.5f, 0, 10));
            } else {
                mob.setPitch(MathHelper.lerp((progress - 0.5f) / 0.5f, 10, 0));
            }
            // Slight head wobble during yawn
            mob.setYaw(mob.getYaw() + (float)Math.sin(yawnTicks * 0.3f) * 0.8f);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.3f;

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
