package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Aerial patrol for flying mobs - circles at various heights.
 */
public class AerialPatrolGoal extends AdaptiveGoal {
    private Vec3d patrolTarget;
    private int patrolTicks = 0;
    private double patrolRadius = 5.0;
    private double patrolHeight;
    
    public AerialPatrolGoal(MobEntity mob) {
        super(mob, GoalType.AERIAL_PATROL, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return !mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return true;
    }
    
    @Override
    protected void onStartGoal() {
        patrolTicks = 0;
        patrolHeight = mob.getY() + mob.getRandom().nextDouble() * 3 - 1.5;
        pickNewPatrolTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        patrolTicks++;
        
        if (patrolTarget == null || mob.getPos().distanceTo(patrolTarget) < 2.0 || patrolTicks % 60 == 0) {
            pickNewPatrolTarget();
        }
        
        if (patrolTarget != null) {
            mob.getNavigation().startMovingTo(patrolTarget.x, patrolTarget.y, patrolTarget.z, 1.0);
        }
    }
    
    private void pickNewPatrolTarget() {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        patrolTarget = new Vec3d(
            mob.getX() + Math.cos(angle) * patrolRadius,
            patrolHeight,
            mob.getZ() + Math.sin(angle) * patrolRadius
        );
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.6f;
    }
}
