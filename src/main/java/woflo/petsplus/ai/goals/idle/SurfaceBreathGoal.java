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
    private static final int SURFACE_HOLD_TICKS = 30;
    private static final int MAX_ASCENT_TICKS = 120;

    private int totalTicks = 0;
    private int surfaceHoldTicks = 0;
    private boolean reachedSurface = false;
    private boolean finished = false;
    
    public SurfaceBreathGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SURFACE_BREATH), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && mob.isSubmergedInWater();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (!mob.isTouchingWater()) {
            return false;
        }

        if (finished) {
            return false;
        }

        return totalTicks < MAX_ASCENT_TICKS;
    }

    @Override
    protected void onStartGoal() {
        totalTicks = 0;
        surfaceHoldTicks = 0;
        reachedSurface = false;
        finished = false;
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        totalTicks = 0;
        surfaceHoldTicks = 0;
        reachedSurface = false;
        finished = false;
    }

    @Override
    protected void onTickGoal() {
        totalTicks++;

        boolean submerged = mob.isSubmergedInWater();
        boolean touchingWater = mob.isTouchingWater();

        if (!touchingWater) {
            requestStop();
            return;
        }

        if (totalTicks > MAX_ASCENT_TICKS) {
            finished = true;
            requestStop();
            return;
        }

        if (submerged) {
            if (reachedSurface) {
                reachedSurface = false;
            }
            surfaceHoldTicks = 0;
            mob.setVelocity(mob.getVelocity().add(0, 0.05, 0));
            return;
        }

        reachedSurface = true;
        surfaceHoldTicks++;

        // Float near the surface; dampen downward drift and add a light upward nudge if sinking
        mob.setVelocity(mob.getVelocity().multiply(0.9, 0.8, 0.9));
        if (touchingWater && mob.getVelocity().y < 0.0) {
            mob.setVelocity(mob.getVelocity().add(0, 0.02, 0));
        }

        // Look around
        if (surfaceHoldTicks % 10 == 0) {
            int yawStep = mob.getRandom().nextInt(3) - 1; // -1, 0, or 1
            mob.setYaw(mob.getYaw() + yawStep * 45f);
        }

        // Splash particles
        if (surfaceHoldTicks % 15 == 0 && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.SPLASH,
                mob.getX(), mob.getY(), mob.getZ(),
                5,
                0.5, 0.1, 0.5,
                0.1
            );
        }

        if (surfaceHoldTicks >= SURFACE_HOLD_TICKS) {
            finished = true;
            requestStop();
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

