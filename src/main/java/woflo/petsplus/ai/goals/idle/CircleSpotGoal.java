package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

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
        super(mob, GoalType.CIRCLE_SPOT, EnumSet.of(Control.MOVE));
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
    protected float calculateEngagement() {
        return 0.4f; // Moderately engaging
    }
}
