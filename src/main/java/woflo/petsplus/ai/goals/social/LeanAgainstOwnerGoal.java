package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Social behavior - pet leans against owner's leg for affection.
 */
public class LeanAgainstOwnerGoal extends AdaptiveGoal {
    private static final int LEAN_DURATION = 60;
    private static final String COOLDOWN_KEY = "lean_against_owner";
    private static final double LOOK_DOT_THRESHOLD = 0.35d;
    private static final int MAX_GAZE_GRACE = 20;

    private int leanTicks = 0;
    private int gazeGraceTicks = 0;
    private boolean leanLeft = true;
    
    public LeanAgainstOwnerGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.LEAN_AGAINST_OWNER), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) {
            return false;
        }

        if (ctx.distanceToOwner() >= 3.2f) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (ctx.nearbyMobAgeProfile() != null
            && ctx.nearbyMobAgeProfile().babyFriendlyCount() > 0
            && Double.isFinite(ctx.nearbyMobAgeProfile().nearestFriendlyBabyDistance())
            && ctx.nearbyMobAgeProfile().nearestFriendlyBabyDistance() <= 1.5d) {
            if (component != null) {
                component.setCooldown(COOLDOWN_KEY, 40);
            }
            return false;
        }

        if (component != null) {
            if (component.isOnCooldown(COOLDOWN_KEY)) {
                return false;
            }
            if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.45f)
                || component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.6f)) {
                return false;
            }
        }

        return isOwnerLookingAtPet(owner);
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return ctx.owner() != null && ctx.distanceToOwner() < 4.0f && leanTicks < LEAN_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        leanTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        leanTicks++;

        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) {
            return;
        }

        if (isOwnerLookingAtPet(owner)) {
            gazeGraceTicks = MAX_GAZE_GRACE;
        } else if (gazeGraceTicks > 0) {
            gazeGraceTicks--;
        }

        Vec3d leanTarget = owner.getEntityPos().add(computeLeanOffset(owner));
        double distanceSq = mob.squaredDistanceTo(leanTarget);
        if (distanceSq > 0.35d) {
            mob.getNavigation().startMovingTo(leanTarget.x, leanTarget.y, leanTarget.z, 0.75);
        } else {
            mob.getNavigation().stop();
            mob.setYaw(owner.getYaw());
            mob.headYaw = owner.headYaw;
            mob.getLookControl().lookAt(owner, 20.0f, 20.0f);

            double nudgeStrength = 0.008d + Math.min(leanTicks, 40) * 0.00015d;
            Vec3d fromOwner = mob.getEntityPos().subtract(owner.getEntityPos());
            double lengthSq = fromOwner.lengthSquared();
            if (lengthSq > 1.0e-4d) {
                Vec3d push = fromOwner.normalize().multiply(-nudgeStrength);
                mob.addVelocity(push.x, 0.0, push.z);
                mob.velocityModified = true;
            }
        }
    }
    
    private boolean isOwnerLookingAtPet(PlayerEntity owner) {
        Vec3d ownerEye = owner.getCameraPosVec(1.0f);
        Vec3d ownerLook = owner.getRotationVec(1.0f).normalize();
        Vec3d petEye = mob.getEntityPos().add(0.0, mob.getStandingEyeHeight(), 0.0);
        Vec3d toPet = petEye.subtract(ownerEye).normalize();
        double dot = ownerLook.dotProduct(toPet);
        if (Double.isNaN(dot)) {
            return false;
        }
        return dot >= LOOK_DOT_THRESHOLD;
    }

    private Vec3d computeLeanOffset(PlayerEntity owner) {
        Vec3d forward = owner.getRotationVec(1.0f).normalize();
        Vec3d lateral = new Vec3d(-forward.z, 0.0, forward.x);
        if (lateral.lengthSquared() < 1.0e-3d) {
            lateral = new Vec3d(1.0, 0.0, 0.0);
        } else {
            lateral = lateral.normalize();
        }
        double side = leanLeft ? -0.6d : 0.6d;
        double forwardStep = -0.35d;
        return forward.multiply(forwardStep).add(lateral.multiply(side));
    }
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.20f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.12f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.018f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.55f, 0.92f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.88f, 1.06f);
        engagement *= staminaScale;

        if (ctx.bondStrength() > 0.7f) {
            engagement = Math.max(engagement, 0.96f);
        }

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement = Math.max(engagement, 1.0f);
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}


