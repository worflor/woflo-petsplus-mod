package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Perch on shoulder - small flying pets land on player's shoulder.
 * Creates adorable, intimate bonding behavior.
 */
public class PerchOnShoulderGoal extends AdaptiveGoal {
    private int perchTicks = 0;
    private static final int MAX_PERCH_TICKS = 200; // 10 seconds
    private boolean onShoulder = false;
    private Vec3d shoulderOffset;
    
    public PerchOnShoulderGoal(MobEntity mob) {
        super(mob, GoalType.PERCH_ON_SHOULDER, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        // Only small flying pets
        if (owner == null || mob.getWidth() > 0.6f) return false;
        
        return !mob.isOnGround() && 
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 8.0;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return perchTicks < MAX_PERCH_TICKS && 
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 3.0;
    }
    
    @Override
    protected void onStartGoal() {
        perchTicks = 0;
        onShoulder = false;
        
        // Choose random shoulder
        double side = mob.getRandom().nextBoolean() ? 0.4 : -0.4;
        shoulderOffset = new Vec3d(side, 1.4, 0.0);
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        onShoulder = false;
    }
    
    @Override
    protected void onTickGoal() {
        perchTicks++;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) return;
        
        if (!onShoulder) {
            // Approach shoulder position
            Vec3d targetPos = owner.getEntityPos().add(shoulderOffset);
            
            mob.getNavigation().startMovingTo(
                targetPos.x, targetPos.y, targetPos.z,
                1.0
            );
            
            // Land on shoulder when close
            if (mob.squaredDistanceTo(owner) < 2.0) {
                onShoulder = true;
            }
        } else {
            // Stay on shoulder
            Vec3d shoulderPos = owner.getEntityPos().add(shoulderOffset);
            mob.setPosition(shoulderPos);
            mob.setVelocity(Vec3d.ZERO);
            
            // Face same direction as owner
            mob.setYaw(owner.getYaw());
            
            // Occasionally look around
            if (perchTicks % 30 == 0) {
                mob.setPitch(mob.getRandom().nextFloat() * 20 - 10);
            }
            
            // Gentle bob/sway
            double bob = Math.sin(perchTicks * 0.1) * 0.02;
            mob.setPosition(shoulderPos.x, shoulderPos.y + bob, shoulderPos.z);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.triple(
            woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.18f,
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.62f, 0.96f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.4f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.86f, 1.05f);
        engagement *= staminaScale;

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.3f)) {
            engagement = Math.max(engagement, 1.0f);
        }

        if (onShoulder) {
            engagement = Math.max(engagement, 1.0f);
        }

        engagement += ctx.bondStrength() * 0.05f;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

