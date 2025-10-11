package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.EnumSet;

/**
 * Owner orbit - maintains distance while circling owner.
 */
public class OwnerOrbitGoal extends AdaptiveGoal {
    private double orbitAngle;
    private double orbitRadius = 4.0;
    private Vec3d orbitTarget;
    
    public OwnerOrbitGoal(MobEntity mob) {
        super(mob, GoalRegistry.OWNER_ORBIT, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        return ctx.owner() != null && ctx.ownerNearby();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return ctx.owner() != null && ctx.ownerNearby();
    }
    
    @Override
    protected void onStartGoal() {
        orbitAngle = mob.getRandom().nextDouble() * Math.PI * 2;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) {
            return;
        }
        
        // Update orbit angle
        orbitAngle += 0.05; // Slow rotation
        
        // Calculate orbit position
        orbitTarget = owner.getEntityPos().add(
            Math.cos(orbitAngle) * orbitRadius,
            0,
            Math.sin(orbitAngle) * orbitRadius
        );
        
        // Move to orbit position
        mob.getNavigation().startMovingTo(orbitTarget.x, orbitTarget.y, orbitTarget.z, 0.9);
        
        // Periodically look at owner
        if (mob.age % 20 < 10) {
            mob.getLookControl().lookAt(owner, 30, 30);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.GUARDIAN_VIGIL, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.7f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.64f, 1.08f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.45f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.1f);
        engagement *= momentumScale;

        float socialBlend = MathHelper.clamp((socialCharge - 0.45f) / 0.25f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.72f, 1.12f);
        engagement *= socialScale;

        // Very engaging if bonded
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement += 0.2f;
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

