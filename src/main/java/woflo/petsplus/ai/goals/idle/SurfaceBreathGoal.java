package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Aquatic-specific idle quirk - mob surfaces to breathe/look around.
 */
public class SurfaceBreathGoal extends AdaptiveGoal {
    private int surfaceTicks = 0;
    private static final int SURFACE_DURATION = 30;
    private boolean reachedSurface = false;
    
    public SurfaceBreathGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SURFACE_BREATH), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && mob.isSubmergedInWater();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (surfaceTicks >= SURFACE_DURATION) {
            return false;
        }

        if (!mob.isTouchingWater() && !reachedSurface) {
            return false;
        }

        return true;
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

        if (!mob.isTouchingWater()) {
            reachedSurface = true;
        }

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
            if (surfaceTicks % 15 == 0 && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
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
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.RELIEF, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}

