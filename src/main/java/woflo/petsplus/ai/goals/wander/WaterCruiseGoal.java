package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Water cruising for aquatic mobs - smooth swimming patterns.
 */
public class WaterCruiseGoal extends AdaptiveGoal {
    private Vec3d cruiseTarget;
    private int cruiseTicks = 0;
    
    public WaterCruiseGoal(MobEntity mob) {
        super(mob, GoalType.WATER_CRUISE, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater();
    }
    
    @Override
    protected void onStartGoal() {
        cruiseTicks = 0;
        pickNewCruiseTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        cruiseTicks++;
        
        if (cruiseTarget == null || mob.getPos().distanceTo(cruiseTarget) < 2.0 || cruiseTicks % 80 == 0) {
            pickNewCruiseTarget();
        }
        
        if (cruiseTarget != null) {
            mob.getNavigation().startMovingTo(cruiseTarget.x, cruiseTarget.y, cruiseTarget.z, 0.9);
        }
    }
    
    private void pickNewCruiseTarget() {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 5 + mob.getRandom().nextDouble() * 5;
        double depthVariation = mob.getRandom().nextDouble() * 3 - 1.5;
        
        cruiseTarget = new Vec3d(
            mob.getX() + Math.cos(angle) * distance,
            mob.getY() + depthVariation,
            mob.getZ() + Math.sin(angle) * distance
        );
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.6f;
    }
}
