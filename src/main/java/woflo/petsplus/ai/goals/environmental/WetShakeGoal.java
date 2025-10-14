package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.EnumSet;

public class WetShakeGoal extends AdaptiveGoal {

    private final int shakeDuration; // Duration in ticks
    private final int particleCount;
    private int cooldown;

    public WetShakeGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.WET_SHAKE), EnumSet.of(Control.LOOK));
        // Scale duration and particle count by pet size
        float size = mob.getWidth() * mob.getHeight();
        this.shakeDuration = 10 + (int)(size * 5);
        this.particleCount = 5 + (int)(size * 10);
        this.cooldown = mob.getRandom().nextInt(120); // Initial random cooldown
    }

    @Override
    protected boolean canStartGoal() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!isEntitySurfaceWet(mob)) {
            return false;
        }
        if (mob.getType().isIn(PetsplusEntityTypeTags.AQUATIC_LIKE)) {
            return false;
        }
        // Don't shake if fully submerged
        if (mob.isSubmergedInWater()) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return getActiveTicks() < this.shakeDuration;
    }

    @Override
    protected void onStartGoal() {
        // For wolves, use the vanilla shake animation if possible
        if (mob instanceof WolfEntity wolf) {
            // The vanilla shake is tied to a lot of internal logic, so we just do particles
        }
    }

    @Override
    protected void onTickGoal() {
        if (mob.getEntityWorld() instanceof ServerWorld serverWorld && (getActiveTicks() % 2 == 0)) {
            float spread = mob.getWidth() * 0.8f;
            serverWorld.spawnParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + mob.getHeight() * 0.5f, mob.getZ(), particleCount / (shakeDuration / 2), mob.getWidth() * spread, mob.getHeight() * spread, mob.getWidth() * spread, 0.1);
        }
    }

    @Override
    protected void onStopGoal() {
        this.cooldown = 100 + mob.getRandom().nextInt(200); // 5-15 second cooldown
    }

    @Override
    protected float calculateEngagement() {
        return 0.1f; // Very low engagement, it's an ambient action
    }

    @Override
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        return woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.PHYSICAL;
    }

    @Override
    protected float getActivityIntensity() {
        return 0.05f;
    }

    private static boolean isEntitySurfaceWet(MobEntity entity) {
        return entity.isSubmergedInWater() || entity.isTouchingWater() || entity.isTouchingWaterOrRain();
    }
}
