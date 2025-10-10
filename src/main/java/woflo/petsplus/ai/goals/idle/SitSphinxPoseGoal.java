package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Idle quirk - pet sits in a regal sphinx pose.
 * For mature, calm pets.
 */
public class SitSphinxPoseGoal extends AdaptiveGoal {
    private int sitTicks = 0;
    private static final int MIN_SIT_DURATION = 80;
    
    public SitSphinxPoseGoal(MobEntity mob) {
        super(mob, GoalType.SIT_SPHINX_POSE, EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!(mob instanceof TameableEntity tameable)) {
            return false;
        }
        return mob.isOnGround() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return sitTicks < MIN_SIT_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        sitTicks = 0;
        if (mob instanceof TameableEntity tameable) {
            tameable.setSitting(true);
        }
    }
    
    @Override
    protected void onStopGoal() {
        if (mob instanceof TameableEntity tameable) {
            tameable.setSitting(false);
        }
    }
    
    @Override
    protected void onTickGoal() {
        sitTicks++;
        // Regal, still posture
        mob.setPitch(0);
        mob.setYaw(mob.getYaw()); // Face same direction
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.15f,
            woflo.petsplus.state.PetComponent.Emotion.STOIC, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.7f;
        
        // Very engaging for mature pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.MATURE) {
            engagement += 0.2f;
        }
        
        // Engaging if calm
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.4f)) {
            engagement += 0.1f;
        }

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
