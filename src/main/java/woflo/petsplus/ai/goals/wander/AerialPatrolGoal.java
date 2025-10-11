package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.EnumSet;

/**
 * Aerial patrol for flying mobs - circles at various heights.
 */
public class AerialPatrolGoal extends AdaptiveGoal {
    private Vec3d patrolTarget;
    private int patrolTicks = 0;
    private double patrolRadius = 5.0;
    private double patrolHeight;
    
    public AerialPatrolGoal(MobEntity mob) {
        super(mob, GoalRegistry.AERIAL_PATROL, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return !mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return true;
    }
    
    @Override
    protected void onStartGoal() {
        patrolTicks = 0;
        patrolHeight = mob.getY() + mob.getRandom().nextDouble() * 3 - 1.5;
        pickNewPatrolTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        patrolTicks++;
        
        if (patrolTarget == null || mob.getEntityPos().distanceTo(patrolTarget) < 2.0 || patrolTicks % 60 == 0) {
            pickNewPatrolTarget();
        }
        
        if (patrolTarget != null) {
            mob.getNavigation().startMovingTo(patrolTarget.x, patrolTarget.y, patrolTarget.z, 1.0);
        }
    }
    
    private void pickNewPatrolTarget() {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        patrolTarget = new Vec3d(
            mob.getX() + Math.cos(angle) * patrolRadius,
            patrolHeight,
            mob.getZ() + Math.sin(angle) * patrolRadius
        );
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.12f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.65f, 1.12f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.14f);
        engagement *= momentumScale;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

