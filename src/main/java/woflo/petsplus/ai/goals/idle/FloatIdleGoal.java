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
 * Aquatic-specific idle quirk - mob floats peacefully in water.
 */
public class FloatIdleGoal extends AdaptiveGoal {
    private static final int BASE_FLOAT_DURATION = 60;

    private int floatTicks = 0;

    private int getFloatDuration() {
        PetContext ctx = getContext();
        float calmness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.CALM) : 0.0f;
        float durationMultiplier = MathHelper.clamp(1.0f + calmness, 0.7f, 1.8f);
        return (int) (BASE_FLOAT_DURATION * durationMultiplier);
    }
    
    public FloatIdleGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FLOAT_IDLE), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        var profile = woflo.petsplus.ai.traits.SpeciesTraits.getProfile(mob);
        if (!profile.aquatic()) {
            return false;
        }
        return mob.isTouchingWater() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater() && floatTicks < getFloatDuration();
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

        // Particle effect
        if (!mob.getEntityWorld().isClient() && mob.getRandom().nextFloat() < 0.1f) {
            ((ServerWorld) mob.getEntityWorld()).spawnParticles(ParticleTypes.BUBBLE, mob.getParticleX(1.0D), mob.getRandomBodyY(), mob.getParticleZ(1.0D), 1, 0.5, 0.5, 0.5, 0.02);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.7f; // Peaceful and relaxing

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
