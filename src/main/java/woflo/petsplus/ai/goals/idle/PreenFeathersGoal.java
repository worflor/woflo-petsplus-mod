package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Flying-specific idle quirk - bird preens its feathers.
 */
public class PreenFeathersGoal extends AdaptiveGoal {
    private int preenTicks = 0;
    private int preenSpot = 0; // 0=wing, 1=chest, 2=tail
    private static final int PREEN_DURATION = 60;
    
    public PreenFeathersGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PREEN_FEATHERS), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle() && (mob.isOnGround() || mob.hasVehicle());
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return preenTicks < PREEN_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        preenTicks = 0;
        preenSpot = mob.getRandom().nextInt(3);
    }
    
    @Override
    protected void onStopGoal() {
        mob.setPitch(0);
        mob.setYaw(mob.bodyYaw);
    }
    
    @Override
    protected void onTickGoal() {
        preenTicks++;
        
        // Preen different spots
        switch (preenSpot) {
            case 0: // Wing
                if (preenTicks % 20 < 10) {
                    mob.setYaw(mob.bodyYaw - 30);
                } else {
                    mob.setYaw(mob.bodyYaw + 30);
                }
                mob.setPitch(20);
                break;
            case 1: // Chest
                mob.setPitch(45);
                break;
            case 2: // Tail
                mob.setYaw(mob.bodyYaw + 180);
                mob.setPitch(30);
                break;
        }
        
        // Switch spots occasionally
        if (preenTicks % 20 == 0) {
            preenSpot = mob.getRandom().nextInt(3);
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
        float engagement = 0.6f; // Satisfying grooming behavior

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
