package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Aquatic-specific idle quirk - mob surfaces to breathe/look around.
 */
public class SurfaceBreathGoal extends AdaptiveGoal {
    private int surfaceTicks = 0;
    private static final int SURFACE_DURATION = 30;
    private boolean reachedSurface = false;
    
    public SurfaceBreathGoal(MobEntity mob) {
        super(mob, GoalType.SURFACE_BREATH, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && !mob.isSubmergedInWater();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return surfaceTicks < SURFACE_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        surfaceTicks = 0;
        reachedSurface = false;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        surfaceTicks++;
        
        if (!reachedSurface && mob.isSubmergedInWater()) {
            // Swim upward
            mob.setVelocity(mob.getVelocity().add(0, 0.05, 0));
        } else {
            reachedSurface = true;
            
            // Float at surface
            mob.setVelocity(mob.getVelocity().multiply(0.9, 0.9, 0.9));
            
            // Look around
            if (surfaceTicks % 10 == 0) {
                mob.setYaw(mob.getYaw() + 45 * mob.getRandom().nextInt(3) - 1);
            }
            
            // Splash particles
            if (surfaceTicks % 15 == 0 && mob.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(
                    ParticleTypes.SPLASH,
                    mob.getX(), mob.getY(), mob.getZ(),
                    5,
                    0.5, 0.1, 0.5,
                    0.1
                );
            }
        }
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.5f;
    }
}
