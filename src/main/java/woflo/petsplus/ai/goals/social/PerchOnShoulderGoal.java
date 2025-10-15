package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Perch on shoulder - small flying pets land on player's shoulder.
 * Creates adorable, intimate bonding behavior.
 */
public class PerchOnShoulderGoal extends AdaptiveGoal {
    private int perchTicks = 0;
    private static final int MAX_PERCH_TICKS = 200; // 10 seconds
    private boolean onShoulder = false;
    private Vec3d baseShoulderOffset;
    private int approachDelayTicks = 0;
    
    public PerchOnShoulderGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PERCH_ON_SHOULDER), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();

        // Only small flying pets
        if (owner == null || mob.getWidth() > 0.6f) return false;

        // Avoid perching if the owner is exposed to rain (keeps cute vibe under cover)
        if ((mob.getEntityWorld().isRaining() || mob.getEntityWorld().isThundering())
            && mob.getEntityWorld().isSkyVisible(owner.getBlockPos())) {
            return false;
        }

        if (!mob.isOnGround() && ctx.ownerNearby() && ctx.distanceToOwner() < 8.0) {
            if (ctx.mobInteractionProfile() != null && ctx.mobInteractionProfile().maintainBuffer()) {
                double nearest = ctx.nearbyMobAgeProfile() != null ? ctx.nearbyMobAgeProfile().nearestBabyDistance() : Double.POSITIVE_INFINITY;
                if (Double.isFinite(nearest) && nearest < 2.5d) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
        approachDelayTicks = computeApproachDelay(getContext());

        // Choose random shoulder
        double side = mob.getRandom().nextBoolean() ? 0.4 : -0.4;
        baseShoulderOffset = new Vec3d(side, 1.4, 0.0);
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        onShoulder = false;
        baseShoulderOffset = null;
    }
    
    @Override
    protected void onTickGoal() {
        perchTicks++;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) return;

        var profile = ctx.mobInteractionProfile() != null
            ? ctx.mobInteractionProfile()
            : woflo.petsplus.ai.PetMobInteractionProfile.defaultProfile();

        Vec3d rotatedOffset = computeShoulderOffset(owner);

        if (!onShoulder) {
            if (perchTicks < approachDelayTicks) {
                mob.getNavigation().stop();
                return;
            }
            // Approach shoulder position
            Vec3d targetPos = owner.getEntityPos().add(rotatedOffset);

            double speed = profile.maintainBuffer() ? 0.7 : 1.0;
            if (profile.softenAnimations()) {
                speed = Math.min(speed, 0.85);
            }

            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, speed);

            // Land on shoulder when close
            if (mob.squaredDistanceTo(owner) < 2.0) {
                onShoulder = true;
            }
        } else {
            // Stay on shoulder
            mob.getNavigation().stop();
            Vec3d shoulderPos = owner.getEntityPos().add(rotatedOffset);
            mob.setVelocity(Vec3d.ZERO);

            // Face same direction as owner
            mob.setYaw(owner.getYaw());

            // Occasionally look around
            if (perchTicks % 30 == 0) {
                mob.setPitch(mob.getRandom().nextFloat() * 20 - 10);
            }

            // Gentle bob/sway
            double bobScale = profile.softenAnimations() ? 0.015 : 0.02;
            double bob = Math.sin(perchTicks * 0.1) * bobScale;
            mob.setPosition(shoulderPos.x, shoulderPos.y + bob, shoulderPos.z);
        }
    }

    private int computeApproachDelay(PetContext ctx) {
        int base = 6;
        if (ctx == null || ctx.mobInteractionProfile() == null) {
            return base;
        }
        var profile = ctx.mobInteractionProfile();
        if (profile.maintainBuffer()) {
            base *= 2;
        }
        if (profile.shouldApproachBabies()) {
            base = Math.max(4, base - 2);
        }
        return base;
    }

    private Vec3d computeShoulderOffset(PlayerEntity owner) {
        if (baseShoulderOffset == null) {
            return Vec3d.ZERO;
        }

        float yawRadians = owner.getYaw() * ((float) Math.PI / 180F);
        double sin = MathHelper.sin(yawRadians);
        double cos = MathHelper.cos(yawRadians);

        double rotatedX = baseShoulderOffset.x * cos - baseShoulderOffset.z * sin;
        double rotatedZ = baseShoulderOffset.x * sin + baseShoulderOffset.z * cos;

        return new Vec3d(rotatedX, baseShoulderOffset.y, rotatedZ);
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

