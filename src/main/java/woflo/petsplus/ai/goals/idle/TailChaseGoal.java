package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Universal idle quirk - pet spins in circles chasing their tail.
 * Works with ANY mob that can turn (no special requirements).
 * 
 * Engagement increases with successful spins and playful mood.
 */
public class TailChaseGoal extends AdaptiveGoal {
    private int spinCount = 0;
    private float targetYaw;
    private int spinDirection; // 1 or -1
    private float accumulatedRotation;
    private static final int MAX_SPINS = 8;
    
    public TailChaseGoal(MobEntity mob) {
        super(mob, GoalType.TAIL_CHASE, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Only start when truly idle (not moving, not in combat)
        return mob.getNavigation().isIdle() && mob.getVelocity().horizontalLength() < 0.1;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return spinCount < MAX_SPINS;
    }
    
    @Override
    protected void onStartGoal() {
        spinCount = 0;
        spinDirection = mob.getRandom().nextBoolean() ? 1 : -1;
        targetYaw = mob.getYaw();
        accumulatedRotation = 0f;
    }
    
    @Override
    protected void onStopGoal() {
        // Sometimes dizzy effect for young pets
        PetContext ctx = getContext();
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG && mob.getRandom().nextFloat() < 0.3f) {
            // Stumble a bit
            Vec3d stumble = new Vec3d(
                (mob.getRandom().nextDouble() - 0.5) * 0.3,
                0,
                (mob.getRandom().nextDouble() - 0.5) * 0.3
            );
            mob.setVelocity(stumble);
        }
    }
    
    @Override
    protected void onTickGoal() {
        // Spin around
        float yawDelta = spinDirection * 30f; // 30 degrees per tick = fast spin
        targetYaw += yawDelta;
        mob.setYaw(targetYaw);
        mob.headYaw = targetYaw;
        mob.bodyYaw = targetYaw;

        accumulatedRotation += Math.abs(yawDelta);

        // Count full rotations
        while (accumulatedRotation >= 360f) {
            spinCount++;
            accumulatedRotation -= 360f;

            targetYaw = (float) MathHelper.wrapDegrees(targetYaw);
            mob.setYaw(targetYaw);
            mob.headYaw = targetYaw;
            mob.bodyYaw = targetYaw;

            // Particle effect on spin completion
            if (mob.getWorld().isClient) {
                spawnSpinParticles();
            }
        }
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;
        
        // More engaging for young pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.3f;
        }
        
        // More engaging if in playful mood
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
            engagement += 0.2f;
        }
        
        // More engaging with more spins (building momentum)
        engagement += (spinCount * 0.05f);
        
        return Math.min(1.0f, engagement);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Tail chase is a playful solo idle behavior
        // Micro-rewards (0.05-0.12 range) appropriate for self-entertainment
        // Avoids overwhelming - these are intrinsic behaviors, not for external reward
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.10f,  // Light-hearted solo fun
            woflo.petsplus.state.PetComponent.Emotion.GLEE, 0.08f          // Mild movement joy
        );
    }
    
    private void spawnSpinParticles() {
        // TODO: Add particle effects for visual flair
    }
}
