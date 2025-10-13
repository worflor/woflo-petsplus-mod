package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Universal idle quirk - pet spins in a small circle.
 * Works with any mob that can move.
 */
public class CircleSpotGoal extends AdaptiveGoal {
    private static final int BASE_CIRCLE_DURATION = 60; // 3 seconds baseline

    private double centerX;
    private double centerZ;
    private float currentAngle;
    private int circleTicks;
    private int targetDuration;

    private float getCircleRadius() {
        float baseRadius = Math.max(0.6f, mob.getWidth());
        return baseRadius * 1.5f;
    }

    private int computeCircleDuration() {
        PetContext ctx = getContext();
        float restlessness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.RESTLESS) : 0.0f;
        float durationMultiplier = MathHelper.clamp(1.0f + restlessness, 0.6f, 1.6f);
        return (int) (BASE_CIRCLE_DURATION * durationMultiplier);
    }
    
    public CircleSpotGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.CIRCLE_SPOT), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle() && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return circleTicks < targetDuration && mob.isOnGround();
    }

    @Override
    protected void onStartGoal() {
        centerX = mob.getX();
        centerZ = mob.getZ();
        currentAngle = 0;
        circleTicks = 0;
        targetDuration = Math.max(40, computeCircleDuration());
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        circleTicks = 0;
    }

    protected void onTickGoal() {
        if (!mob.isOnGround()) {
            requestStop();
            return;
        }

        currentAngle = MathHelper.wrapDegrees(currentAngle + 360.0f / targetDuration);
        circleTicks++;

        if (circleTicks >= targetDuration) {
            requestStop();
            return;
        }

        double targetX = centerX + Math.cos(Math.toRadians(currentAngle)) * getCircleRadius();
        double targetZ = centerZ + Math.sin(Math.toRadians(currentAngle)) * getCircleRadius();

        mob.getNavigation().startMovingTo(targetX, mob.getY(), targetZ, 0.8);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.4f; // Moderately engaging

        engagement *= IdleEnergyTuning.balancedStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
