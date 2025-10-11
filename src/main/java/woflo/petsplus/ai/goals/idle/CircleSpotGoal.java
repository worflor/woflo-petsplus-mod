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
    private double centerX;
    private double centerZ;
    private float currentAngle;
    private static final float CIRCLE_RADIUS = 1.0f;
    private static final int CIRCLE_DURATION = 60; // 3 seconds
    
    public CircleSpotGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.CIRCLE_SPOT), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle() && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return true; // Let duration handle it
    }
    
    @Override
    protected void onStartGoal() {
        centerX = mob.getX();
        centerZ = mob.getZ();
        currentAngle = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        currentAngle += 6; // 6 degrees per tick = full circle in 60 ticks
        
        double targetX = centerX + Math.cos(Math.toRadians(currentAngle)) * CIRCLE_RADIUS;
        double targetZ = centerZ + Math.sin(Math.toRadians(currentAngle)) * CIRCLE_RADIUS;
        
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
