package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
// Species tags: multi-tag gating
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Subtle behavior: P1
 * LeadFollowNudgeGoal - initiator slightly slows and adjusts heading; a nearby allied pet aligns as follower.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): socialAffiliative
 *
 * Preconditions:
 * - Another allied pet (same owner) within 6 blocks; low activity state
 *
 * Behavior:
 * - 6–10s alignment: initiator sets a gentle pace, follower aligns with 0.4–0.8s stagger
 *
 * Cooldown:
 * - 60–180s per pair via "lead_follow_pair:{uuidA}:{uuidB}"
 * - Variety micro-cooldown "social_micro" 20–35s
 *
 * Abort:
 * - On threat/owner urgency
 */
public class LeadFollowNudgeGoal extends AdaptiveGoal {
    private static final double SEARCH_RADIUS = 6.0;
    private static final int MIN_TICKS = 120; // 6s
    private static final int MAX_TICKS = 200; // 10s

    private int ticks;
    private int duration;
    private long followerStartDelayTicks;
    private MobEntity follower;
    private String pairCooldownKey;

    public LeadFollowNudgeGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.LEAD_FOLLOW_NUDGE), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.socialAffiliative()) {
            return false;
        }

        // Require component for cooldowns/owner
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            return false;
        }

        // Variety anti-spam
        if (pc.isOnCooldown("social_micro")) {
            return false;
        }

        // Momentum gating (high block, mid halve)
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        // Abort if owner urgent/threat
        if (pc.getCachedOwnerEntity() != null) {
            OwnerCombatState ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Low activity for initiator
        if (!mob.getNavigation().isIdle() || mob.getVelocity().horizontalLengthSquared() > 0.02) {
            return false;
        }

        // Find single follower candidate (same owner, low activity) within radius 6
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }

        UUID ownerId = pc.getOwnerUuid();
        if (ownerId == null) {
            return false;
        }

        List<MobEntity> nearby = sw.getEntitiesByClass(
            MobEntity.class,
            mob.getBoundingBox().expand(SEARCH_RADIUS),
            e -> {
                if (e == mob || !e.isAlive() || e.isRemoved()) return false;
                PetComponent epc = PetComponent.get(e);
                if (epc == null || !ownerId.equals(epc.getOwnerUuid())) return false;
                // low activity state for follower
                return e.getNavigation().isIdle() && e.getVelocity().horizontalLengthSquared() <= 0.02;
            }
        );
        if (nearby.isEmpty()) {
            return false;
        }

        follower = nearby.get(mob.getRandom().nextInt(Math.min(nearby.size(), 3))); // small pool to avoid heavy bias
        // Pair cooldown key (order-independent)
        pairCooldownKey = buildPairCooldownKey(mob, follower);
        // Respect per-pair cooldown on either side
        PetComponent fpc = PetComponent.get(follower);
        if ((pc != null && pc.isOnCooldown(pairCooldownKey)) || (fpc != null && fpc.isOnCooldown(pairCooldownKey))) {
            follower = null;
            return false;
        }

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (follower == null || !follower.isAlive() || follower.isRemoved()) {
            return false;
        }
        // End if either pet starts moving significantly (taken by higher-priority goals), base will also guard
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);
        followerStartDelayTicks = mob.getEntityWorld().getTime() + mob.getRandom().nextBetween(8, 16); // 0.4–0.8s

        // Initiator begins gentle nudge: small forward drift target
        Vec3d dir = forwardDir(mob);
        //Vec3d target = mob.getPos().add(dir.multiply(1.2)); // small lead
        Vec3d origin = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
        Vec3d target = origin.add(dir.multiply(1.2)); // small lead
        if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
            mob.getNavigation().startMovingTo(target.x, target.y, target.z, 0.65);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        PetComponent fpc = follower != null ? PetComponent.get(follower) : null;
        int pairCd = secondsToTicks(60) + mob.getRandom().nextInt(secondsToTicks(121)); // 60–180s
        if (pc != null) {
            pc.setCooldown(pairCooldownKey, pairCd);
            pc.setCooldown("social_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16))); // 20–35s
        }
        if (fpc != null) {
            fpc.setCooldown(pairCooldownKey, pairCd);
            fpc.setCooldown("social_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16)));
        }
        if (mob.getNavigation() != null) mob.getNavigation().stop();
        if (follower != null && follower.getNavigation() != null) follower.getNavigation().stop();
        follower = null;
        pairCooldownKey = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Initiator: keep a gentle pace, slight heading smoothing
        Vec3d dir = forwardDir(mob);
        if (ticks % 15 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 6.0f);
        }
        // Maintain small lead
        if (ticks % 10 == 0) {
//            Vec3d target = mob.getPos().add(dir.multiply(1.2));
            Vec3d origin2 = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
            Vec3d target = origin2.add(dir.multiply(1.2));
            if (mob.getNavigation().isIdle()) {
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                    mob.getNavigation().startMovingTo(target.x, target.y, target.z, 0.65);
                }
            }
        }

        // Follower stagger and alignment
        if (follower != null) {
            long now = mob.getEntityWorld().getTime();
            // Face the initiator
            follower.getLookControl().lookAt(mob, 20.0f, 20.0f);

            if (now >= followerStartDelayTicks) {
                // Align behind/adjacent to initiator at small offset to prevent collision
                Vec3d fOffset = dir.multiply(-0.8).add(rightDir(mob).multiply((mob.getRandom().nextBoolean() ? 0.6 : -0.6)));
//                Vec3d fTarget = mob.getPos().add(fOffset);
                Vec3d origin3 = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
                Vec3d fTarget = origin3.add(fOffset);
                if (follower.getNavigation().isIdle() || follower.squaredDistanceTo(fTarget) > 1.2) {
                    if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                        follower.getNavigation().startMovingTo(fTarget.x, fTarget.y, fTarget.z, 0.65);
                    }
                }
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.60f;
        if (follower != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(follower));
            e += MathHelper.clamp((float) ((SEARCH_RADIUS - d) / (SEARCH_RADIUS * 2.0)), 0.0f, 0.1f);
        }
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Mild social connection
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.08f
        );
    }

    // === Helpers ===

    private static String buildPairCooldownKey(MobEntity a, MobEntity b) {
        UUID ua = a.getUuid();
        UUID ub = b.getUuid();
        int cmp = ua.toString().compareTo(ub.toString());
        UUID first = cmp <= 0 ? ua : ub;
        UUID second = cmp <= 0 ? ub : ua;
        return "lead_follow_pair:" + first + ":" + second;
    }

    private static Vec3d forwardDir(MobEntity e) {
        float yaw = e.getYaw() * ((float)Math.PI / 180F);
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw)).normalize();
    }

    private static Vec3d rightDir(MobEntity e) {
        float yaw = (e.getYaw() + 90.0f) * ((float)Math.PI / 180F);
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw)).normalize();
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}