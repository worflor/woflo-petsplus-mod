package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.context.PetContext;

import java.util.EnumSet;

/**
 * Aquatic-specific idle quirk - mob plays with bubbles.
 */
public class BubblePlayGoal extends AdaptiveGoal {
    private static final int BASE_PLAY_DURATION = 50;

    private int playTicks = 0;

    private int getPlayDuration() {
        PetContext ctx = getContext();
        float playfulness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.PLAYFUL) : 0.0f;
        float durationMultiplier = MathHelper.clamp(1.0f + playfulness, 0.6f, 1.8f);
        return (int) (BASE_PLAY_DURATION * durationMultiplier);
    }
    
    public BubblePlayGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.BUBBLE_PLAY), EnumSet.noneOf(Control.class));
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
        return mob.isTouchingWater() && playTicks < getPlayDuration();
    }
    
    @Override
    protected void onStartGoal() {
        playTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        // Nothing to clean up
    }
    
    @Override
    protected void onTickGoal() {
        playTicks++;
        
        // Spin and create bubbles
        mob.setYaw(mob.getYaw() + mob.getRandom().nextFloat() * 20.0f - 10.0f);
        
        // Spawn bubble particles
        if (playTicks % 5 == 0 && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            double x = mob.getX() + mob.getRandom().nextGaussian() * 0.5;
            double y = mob.getY() + 0.5;
            double z = mob.getZ() + mob.getRandom().nextGaussian() * 0.5;
            serverWorld.spawnParticles(
                ParticleTypes.BUBBLE,
                x, y, z,
                1,
                0.0, 0.1, 0.0,
                0.1
            );
            // Chase the bubble
            mob.getNavigation().startMovingTo(x, y, z, 1.2);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        engagement *= IdleEnergyTuning.energeticStaminaMultiplier(ctx.physicalStamina());
        engagement *= IdleEnergyTuning.socialCenteredMultiplier(ctx.socialCharge());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}

