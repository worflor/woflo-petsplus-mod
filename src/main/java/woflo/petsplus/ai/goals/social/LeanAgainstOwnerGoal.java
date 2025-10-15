package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
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
    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private static final int LEAN_DURATION = 60;
    private static final String COOLDOWN_KEY = "lean_against_owner";
    private static final double LOOK_DOT_THRESHOLD = 0.35d;
    private static final int MAX_GAZE_GRACE = 20;
    private static final double GOLDEN_ANGLE = MathHelper.TAU * 0.38196601125d; // ~137.5°
    private static final double OCC_MARGIN = 0.18d;
    private static final double NEAR_OWNER_SLOW_RADIUS_SQ = 9.0d; // 3 blocks

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
            // If raining and pet is sheltered with recent wetness, avoid initiating lean unless already very close
            var world = mob.getEntityWorld();
            if (world != null && (world.isRaining() || world.isThundering()) && !world.isSkyVisible(mob.getBlockPos())) {
                Long lastWet = component.getStateData(LAST_WET_TICK_KEY, Long.class);
                if (lastWet != null && world.getTime() - lastWet <= 2400L) {
                    if (ctx.distanceToOwner() > 2.0f) {
                        return false;
                    }
                }
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

        Vec3d baseTarget = owner.getEntityPos().add(computeLeanOffset(owner));
        double distanceSq = mob.squaredDistanceTo(baseTarget);
        if (distanceSq > 0.35d) {
            Vec3d moveTarget = baseTarget;
            if (distanceSq > 1.0d) {
                // Mild lane bias to avoid shoulder-to-shoulder shoves when approaching.
                int h = mob.getUuid().hashCode();
                double laneSign = ((h >>> 1) & 1) == 0 ? -1.0 : 1.0;
                double width = Math.max(0.3d, mob.getWidth());
                double laneMag = Math.min(0.35d, 0.25d * width);
                Vec3d toTarget = baseTarget.subtract(mob.getEntityPos());
                double lenSq = toTarget.lengthSquared();
                if (lenSq > 1.0e-4d) {
                    Vec3d perp = new Vec3d(-toTarget.z, 0.0, toTarget.x).normalize().multiply(laneMag * laneSign);
                    moveTarget = moveTarget.add(perp);
                }
            }

            double speed = 0.75d;
            double ownerDistSq = mob.squaredDistanceTo(owner);
            if (ownerDistSq < NEAR_OWNER_SLOW_RADIUS_SQ) {
                speed *= 0.86d; // subtle slowdown near owner/cluster
            }

            // Repath jitter to avoid sync-stepping in groups
            if ((leanTicks & 3) == 0 || mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, speed);
            }
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
        // Deterministic, size-aware slot around the owner with light occupancy fallback.
        int h = mob.getUuid().hashCode();
        double u = ((long)(h & 0x7fffffff)) / (double)0x7fffffff; // [0,1)
        double angle = u * MathHelper.TAU;

        double width = Math.max(0.3d, mob.getWidth());
        double radius = 0.80d + 0.25d * width;

        Vec3d ownerPos = owner.getEntityPos();
        Vec3d bestOffset = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            Vec3d slotWorld = ownerPos.add(ox, 0.0, oz);

            if (!isSlotOccupied(owner, slotWorld, width)) {
                bestOffset = new Vec3d(ox, 0.0, oz);
                break;
            }
            angle += GOLDEN_ANGLE;
        }
        if (bestOffset == null) {
            bestOffset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        }
        return bestOffset;
    }

    private boolean isSlotOccupied(PlayerEntity owner, Vec3d slotWorld, double thisWidth) {
        double occ = 0.5d * thisWidth + OCC_MARGIN;
        Box box = new Box(
            slotWorld.x - occ, slotWorld.y - 0.75d, slotWorld.z - occ,
            slotWorld.x + occ, slotWorld.y + 0.75d, slotWorld.z + occ
        );
        return !mob.getEntityWorld().getOtherEntities(mob, box, e -> {
            if (!(e instanceof MobEntity other)) return false;
            PetComponent pc = PetComponent.get(other);
            return pc != null && pc.isOwnedBy(owner);
        }).isEmpty();
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


