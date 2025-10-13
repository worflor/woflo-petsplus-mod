package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Land-specific idle quirk - pet practices pouncing on imaginary prey.
 */
public class PouncePracticeGoal extends AdaptiveGoal {
    private static final int BASE_CROUCH_DURATION = 20;

    private Vec3d pounceTarget;
    private int crouchTicks = 0;
    private boolean hasJumped = false;

    private int getCrouchDuration() {
        PetContext ctx = getContext();
        float playfulness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.PLAYFUL) : 0.0f;
        float adjusted = MathHelper.clamp(BASE_CROUCH_DURATION * (1.2f - playfulness), 12f, 30f);
        return (int) adjusted;
    }
    
    public PouncePracticeGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.POUNCE_PRACTICE), EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        var profile = woflo.petsplus.ai.traits.SpeciesTraits.getProfile(mob);
        if (!profile.felineLike() && !profile.canineLike()) {
            return false;
        }
        return mob.isOnGround() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return !hasJumped || !mob.isOnGround();
    }
    
    @Override
    protected void onStartGoal() {
        crouchTicks = 0;
        hasJumped = false;
        
        // Pick a spot to pounce toward
        double angle = mob.getYaw() + (mob.getRandom().nextDouble() - 0.5) * 45;
        double distance = 2.0 + mob.getRandom().nextDouble() * 2;
        pounceTarget = mob.getEntityPos().add(
            Math.cos(Math.toRadians(angle)) * distance,
            0,
            Math.sin(Math.toRadians(angle)) * distance
        );
    }
    
    @Override
    protected void onStopGoal() {
        mob.setSneaking(false);
    }
    
    @Override
    protected void onTickGoal() {
        if (!hasJumped) {
            crouchTicks++;
            
            // Crouch phase (wiggle butt)
            if (crouchTicks < getCrouchDuration()) {
                mob.setSneaking(true);
                mob.getLookControl().lookAt(pounceTarget);
                
                // Wiggle animation
                if (crouchTicks % 4 < 2) {
                    mob.setYaw(mob.getYaw() + 2);
                } else {
                    mob.setYaw(mob.getYaw() - 2);
                }
            } else {
                // POUNCE!
                mob.playSound(net.minecraft.sound.SoundEvents.ENTITY_CAT_AMBIENT, 0.5f, 1.5f);
                mob.setSneaking(false);
                Vec3d velocity = pounceTarget.subtract(mob.getEntityPos()).normalize().multiply(0.5).add(0, 0.4, 0);
                mob.setVelocity(velocity);
                mob.velocityModified = true;
                hasJumped = true;
            }
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        // Very engaging for young pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.3f;
        }
        
        // Engaging if playful
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.3f)) {
            engagement += 0.2f;
        }

        engagement *= IdleEnergyTuning.energeticStaminaMultiplier(ctx.physicalStamina());
        engagement *= IdleEnergyTuning.socialCenteredMultiplier(ctx.socialCharge());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}

