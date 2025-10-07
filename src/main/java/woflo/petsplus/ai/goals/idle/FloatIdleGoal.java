package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Aquatic-specific idle quirk - mob floats peacefully in water.
 */
public class FloatIdleGoal extends AdaptiveGoal {
    private int floatTicks = 0;
    private static final int FLOAT_DURATION = 60;
    
    public FloatIdleGoal(MobEntity mob) {
        super(mob, GoalType.FLOAT_IDLE, EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater() && floatTicks < FLOAT_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        floatTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        // Nothing to clean up
    }
    
    @Override
    protected void onTickGoal() {
        floatTicks++;
        
        // Gentle bobbing motion
        double bob = Math.sin(floatTicks * 0.1) * 0.01;
        mob.setVelocity(mob.getVelocity().multiply(0.98, 0.95, 0.98).add(0, bob, 0));
        
        // Gentle rotation
        mob.setYaw(mob.getYaw() + (float)Math.sin(floatTicks * 0.05) * 0.5f);
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.7f; // Peaceful and relaxing
    }
}
