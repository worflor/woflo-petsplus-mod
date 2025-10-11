package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Universal idle quirk - pet stretches and yawns.
 */
public class StretchAndYawnGoal extends AdaptiveGoal {
    private int stretchTicks = 0;
    private static final int STRETCH_DURATION = 30;
    
    public StretchAndYawnGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.STRETCH_AND_YAW), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return stretchTicks < STRETCH_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        stretchTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        stretchTicks = 0;
    }
    
    @Override
    protected void onTickGoal() {
        stretchTicks++;
        
        // Animation would go here (pitch/yaw adjustments, pose changes)
        if (stretchTicks == 10) {
            // Front stretch
            mob.setPitch(-15);
        } else if (stretchTicks == 20) {
            // Back to normal
            mob.setPitch(0);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.3f;

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
