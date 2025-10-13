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
    private static final int BASE_STRETCH_DURATION = 30;

    private int stretchTicks = 0;
    private int getStretchDuration() {
        PetContext ctx = getContext();
        float stamina = ctx.physicalStamina();
        // Longer stretch for lower stamina
        float multiplier = MathHelper.clamp(1.5f - stamina, 0.8f, 1.8f);
        return (int) (BASE_STRETCH_DURATION * multiplier);
    }
    
    public StretchAndYawnGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.STRETCH_AND_YAW), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return stretchTicks < getStretchDuration();
    }
    
    @Override
    protected void onStartGoal() {
        stretchTicks = 0;
    }

    @Override
    protected void onStopGoal() {
        stretchTicks = 0;
        mob.setPitch(0.0f);
    }
    
    @Override
    protected void onTickGoal() {
        stretchTicks++;

        if (stretchTicks == 1) {
            // TODO: Add a yawning sound effect here
        }

        if (stretchTicks < 10) {
            // Start stretch
            mob.setPitch(MathHelper.lerp(stretchTicks / 10.0f, 0, -15));
        } else if (stretchTicks < 20) {
            // Hold stretch
            mob.setPitch(-15);
        } else if (stretchTicks < 30) {
            // End stretch
            mob.setPitch(MathHelper.lerp((stretchTicks - 20) / 10.0f, -15, 0));
        } else {
            // Yawn
            if (stretchTicks == 30) {
                mob.setPitch(10);
            } else if (stretchTicks == 40) {
                mob.setPitch(0);
            }
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
