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
 * Land-specific idle quirk - pet perks ears and scans environment.
 */
public class PerkEarsScanGoal extends AdaptiveGoal {
    private Vec3d scanTarget;
    private int scanTicks = 0;
    private int currentScanIndex = 0;
    private static final int SCANS_PER_SESSION = 3;
    private static final int TICKS_PER_SCAN = 20;
    
    public PerkEarsScanGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PERK_EARS_SCAN), EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return currentScanIndex < SCANS_PER_SESSION;
    }
    
    @Override
    protected void onStartGoal() {
        scanTicks = 0;
        currentScanIndex = 0;
        pickNextScanTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.setPitch(0);
    }
    
    @Override
    protected void onTickGoal() {
        scanTicks++;
        
        if (scanTicks >= TICKS_PER_SCAN) {
            currentScanIndex++;
            scanTicks = 0;
            pickNextScanTarget();
        }
        
        if (scanTarget != null) {
            mob.getLookControl().lookAt(scanTarget);
            // Alert posture
            mob.setPitch(-5);
        }
    }
    
    private void pickNextScanTarget() {
        // Look in different directions
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 5.0 + mob.getRandom().nextDouble() * 5;
        scanTarget = mob.getEntityPos().add(
            Math.cos(angle) * distance,
            mob.getRandom().nextDouble() * 2,
            Math.sin(angle) * distance
        );
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.VIGILANT, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f; // Moderately alert

        engagement *= IdleEnergyTuning.balancedStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}

