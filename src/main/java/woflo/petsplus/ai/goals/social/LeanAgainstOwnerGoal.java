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
    private static final double LOOK_DOT_THRESHOLD_SQ = LOOK_DOT_THRESHOLD * LOOK_DOT_THRESHOLD;
    private static final int MAX_GAZE_GRACE = 20;
    private static final double GOLDEN_ANGLE = MathHelper.TAU * 0.38196601125d; // ~137.5 deg
    private static final double OCC_MARGIN = 0.18d;
    private static final double NEAR_OWNER_SLOW_RADIUS_SQ = 9.0d; // 3 blocks
    private static final double APPROACH_EPSILON_SQ = 0.35d;
    private static final double LANE_EPSILON = 1.0e-4d;
    private static final double PUSH_MIN_SQ = 1.0e-4d;

    private int leanTicks = 0;
    private int gazeGraceTicks = 0;
    private boolean leanLeft = true;
    private record LeanSlot(double offsetX, double offsetZ) {}
    
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

        double ownerX = owner.getX();
        double ownerY = owner.getY();
        double ownerZ = owner.getZ();
        LeanSlot slot = computeLeanSlot(owner, ownerX, ownerZ);
        double baseTargetX = ownerX + slot.offsetX();
        double baseTargetY = ownerY;
        double baseTargetZ = ownerZ + slot.offsetZ();

        double mobX = mob.getX();
        double mobZ = mob.getZ();
        double toTargetX = baseTargetX - mobX;
        double toTargetZ = baseTargetZ - mobZ;
        double distanceSq = (toTargetX * toTargetX) + (toTargetZ * toTargetZ);

        if (distanceSq > APPROACH_EPSILON_SQ) {
            double moveTargetX = baseTargetX;
            double moveTargetY = baseTargetY;
            double moveTargetZ = baseTargetZ;
            if (distanceSq > 1.0d) {
                // Mild lane bias to avoid shoulder-to-shoulder shoves when approaching.
                int h = mob.getUuid().hashCode();
                double laneSign = ((h >>> 1) & 1) == 0 ? -1.0 : 1.0;
                double width = Math.max(0.3d, mob.getWidth());
                double laneMag = Math.min(0.35d, 0.25d * width);
                if (distanceSq > LANE_EPSILON) {
                    double laneScale = laneMag * laneSign / Math.sqrt(distanceSq);
                    moveTargetX += -toTargetZ * laneScale;
                    moveTargetZ += toTargetX * laneScale;
                }
            }

            double speed = 0.75d;
            double ownerDistSq = mob.squaredDistanceTo(owner);
            if (ownerDistSq < NEAR_OWNER_SLOW_RADIUS_SQ) {
                speed *= 0.86d; // subtle slowdown near owner/cluster
            }

            // Repath jitter to avoid sync-stepping in groups
            if ((leanTicks & 3) == 0 || mob.getNavigation().isIdle()) {
                if (!orientTowards(moveTargetX, moveTargetY + 0.2, moveTargetZ, 32.0f, 26.0f, 18.0f)) {
                    mob.getNavigation().stop();
                    return;
                }
                mob.getNavigation().startMovingTo(moveTargetX, moveTargetY, moveTargetZ, speed);
            }
        } else {
            mob.getNavigation().stop();
            mob.setYaw(owner.getYaw());
            mob.headYaw = owner.headYaw;
            mob.getLookControl().lookAt(owner, 20.0f, 20.0f);

            double fromOwnerX = mobX - ownerX;
            double fromOwnerZ = mobZ - ownerZ;
            double lengthSq = (fromOwnerX * fromOwnerX) + (fromOwnerZ * fromOwnerZ);
            if (lengthSq > PUSH_MIN_SQ) {
                double nudgeStrength = 0.008d + Math.min(leanTicks, 40) * 0.00015d;
                double invLength = MathHelper.fastInverseSqrt(lengthSq);
                double pushX = -fromOwnerX * invLength * nudgeStrength;
                double pushZ = -fromOwnerZ * invLength * nudgeStrength;
                mob.addVelocity(pushX, 0.0, pushZ);
                mob.velocityModified = true;
            }
        }
    }

    private boolean isOwnerLookingAtPet(PlayerEntity owner) {
        Vec3d ownerLook = owner.getRotationVec(1.0f);
        Vec3d ownerEye = owner.getCameraPosVec(1.0f);
        double petEyeX = mob.getX();
        double petEyeY = mob.getY() + mob.getStandingEyeHeight();
        double petEyeZ = mob.getZ();

        double toPetX = petEyeX - ownerEye.x;
        double toPetY = petEyeY - ownerEye.y;
        double toPetZ = petEyeZ - ownerEye.z;
        double lengthSq = (toPetX * toPetX) + (toPetY * toPetY) + (toPetZ * toPetZ);
        if (lengthSq < 1.0e-6d) {
            return false;
        }
        double dot = ownerLook.x * toPetX + ownerLook.y * toPetY + ownerLook.z * toPetZ;
        if (Double.isNaN(dot) || dot <= 0.0d) {
            return false;
        }
        double alignmentSq = dot * dot;
        return alignmentSq >= LOOK_DOT_THRESHOLD_SQ * lengthSq;
    }

    private LeanSlot computeLeanSlot(PlayerEntity owner, double ownerX, double ownerZ) {
        // Deterministic, size-aware slot around the owner with light occupancy fallback.
        int h = mob.getUuid().hashCode();
        double u = ((long) (h & 0x7fffffff)) / (double) 0x7fffffff; // [0,1)
        double angle = u * MathHelper.TAU;

        double width = Math.max(0.3d, mob.getWidth());
        double radius = 0.80d + 0.25d * width;

        LeanSlot bestSlot = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            double slotX = ownerX + ox;
            double slotZ = ownerZ + oz;
            if (!isSlotOccupied(owner, slotX, owner.getY(), slotZ, width)) {
                bestSlot = new LeanSlot(ox, oz);
                break;
            }
            angle += GOLDEN_ANGLE;
        }
        if (bestSlot == null) {
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            bestSlot = new LeanSlot(ox, oz);
        }
        return bestSlot;
    }

    private boolean isSlotOccupied(PlayerEntity owner, double slotX, double slotY, double slotZ, double thisWidth) {
        double occ = 0.5d * thisWidth + OCC_MARGIN;
        Box box = new Box(
            slotX - occ, slotY - 0.75d, slotZ - occ,
            slotX + occ, slotY + 0.75d, slotZ + occ
        );
        return !mob.getEntityWorld().getOtherEntities(mob, box, e -> {
            if (!(e instanceof MobEntity other)) {
                return false;
            }
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


