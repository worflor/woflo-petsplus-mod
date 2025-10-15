package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Land-specific idle quirk - pet sniffs the ground investigating scents.
 */
public class SniffGroundGoal extends AdaptiveGoal {
    private static final int BASE_SNIFF_DURATION = 50;

    private BlockPos sniffTarget;
    private int sniffTicks = 0;
    private int getSniffDuration() {
        PetContext ctx = getContext();
        float curiosity = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.CURIOUS) : 0.0f;
        float multiplier = MathHelper.clamp(1.0f + curiosity, 0.7f, 1.7f);
        return (int) (BASE_SNIFF_DURATION * multiplier);
    }
    
    public SniffGroundGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SNIFF_GROUND), EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isOnGround() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return sniffTicks < getSniffDuration();
    }
    
    @Override
    protected void onStartGoal() {
        sniffTicks = 0;
        // Pick a nearby spot to sniff
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 1.0 + mob.getRandom().nextDouble();
        sniffTarget = mob.getBlockPos().add(
            (int)(Math.cos(angle) * distance),
            0,
            (int)(Math.sin(angle) * distance)
        );
        mob.playSound(net.minecraft.sound.SoundEvents.ENTITY_FOX_SNIFF, 1.0f, 1.0f);
    }
    
    @Override
    protected void onStopGoal() {
        mob.setPitch(0);
    }
    
    @Override
    protected void onTickGoal() {
        sniffTicks++;

        // Look at ground
        Vec3d targetVec = Vec3d.ofBottomCenter(sniffTarget);
        mob.getLookControl().lookAt(targetVec.x, targetVec.y, targetVec.z);
        mob.setPitch(45); // Nose to ground

        // Sniff animation (head bob)
        if (sniffTicks % 10 < 5) {
            mob.setPitch(40);
        } else {
            mob.setPitch(50);
        }

        // Subtle foot shuffle toward the spot (no navigation; tiny velocity nudges)
        if (sniffTicks <= 20) {
            Vec3d to = targetVec.subtract(mob.getEntityPos());
            double len = to.length();
            if (len > 0.001 && len < 2.5) {
                Vec3d step = to.normalize().multiply(0.02);
                mob.addVelocity(step.x, 0.0, step.z);
                mob.velocityModified = true;
            }
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;
        
        // More engaging if curious
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.3f)) {
            engagement += 0.3f;
        }

        engagement *= IdleEnergyTuning.balancedStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
