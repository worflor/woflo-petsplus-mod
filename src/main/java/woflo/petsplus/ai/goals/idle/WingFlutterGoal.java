package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

/**
 * Flying-specific idle quirk - bird flutters wings while stationary.
 */
public class WingFlutterGoal extends AdaptiveGoal {
    private int flutterTicks = 0;
    private static final int FLUTTER_DURATION = 40;
    
    public WingFlutterGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.WING_FLUTTER), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return flutterTicks < FLUTTER_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        flutterTicks = 0;
        // Soft flutter sound to sell the motion (low volume)
        try {
            mob.playSound(SoundEvents.ENTITY_PARROT_FLY, 0.25f, 1.2f + (mob.getRandom().nextFloat() * 0.2f - 0.1f));
        } catch (Throwable ignored) {
        }
    }
    
    @Override
    protected void onStopGoal() {
        // Reset
    }
    
    @Override
    protected void onTickGoal() {
        flutterTicks++;
        
        // Flutter animation - rapid up/down motion
        if (flutterTicks % 4 < 2) {
            mob.setVelocity(mob.getVelocity().add(0, 0.02, 0));
        }

        // Add slight rotation for visual interest
        mob.setYaw(mob.getYaw() + (float)Math.sin(flutterTicks * 0.3) * 2);

        // Gentle head pitch oscillation
        mob.setPitch((float)(Math.sin(flutterTicks * 0.2f) * 6.0f));

        // Occasional tiny air puff to indicate motion
        if (!mob.getEntityWorld().isClient() && flutterTicks % 8 == 0) {
            try {
                ((ServerWorld) mob.getEntityWorld()).spawnParticles(
                    ParticleTypes.CLOUD,
                    mob.getParticleX(0.6D),
                    mob.getRandomBodyY(),
                    mob.getParticleZ(0.6D),
                    1,
                    0.03, 0.02, 0.03,
                    0.01
                );
            } catch (Throwable ignored) {
            }
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.4f;

        engagement *= IdleEnergyTuning.energeticStaminaMultiplier(ctx.physicalStamina());
        engagement *= IdleEnergyTuning.socialCenteredMultiplier(ctx.socialCharge());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
