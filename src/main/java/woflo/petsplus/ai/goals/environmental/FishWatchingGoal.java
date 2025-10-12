package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.LivingEntity;
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
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ai.group.GroupCoordinator;
import woflo.petsplus.ai.group.GroupTuning;
import woflo.petsplus.state.OwnerCombatState;

import java.util.EnumSet;
import java.util.List;

/**
 * Subtle behavior: P1
 * FishWatchingGoal - watch nearby fish with subtle head/step adjustments.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): aquatic || curiousScavenger
 *
 * Preconditions:
 * - Visible fish entity (cod/salmon/tropical/glow) within 6–10 blocks
 * - Not in combat (base AdaptiveGoal already guards target/attacker)
 *
 * Behavior:
 * - Look-at for 5–10s with subtle head/step adjustments
 *
 * Cooldown:
 * - 90–180s via "fish_watching" (PetComponent)
 * - Variety micro "environment_micro" 20–35s
 *
 * Scanning:
 * - Single nearest-entity lookup in small radius; conservative filters
 */
public class FishWatchingGoal extends AdaptiveGoal {
    private static final int MIN_TICKS = 100; // 5s
    private static final int MAX_TICKS = 200; // 10s
    private static final int MIN_RANGE = 6;
    private static final int MAX_RANGE = 10;

    private int ticks;
    private int duration;
    private LivingEntity targetFish;

    // Open invite follower state
    private boolean joiningAsFollower = false; // Species tags: open invite join
    private int followerStaggerTicks = 0;

    public FishWatchingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FISH_WATCHING), EnumSet.of(Control.LOOK, Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating - O(1)
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!(profile.aquatic() || profile.curiousScavenger())) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.isOnCooldown("environment_micro")) {
            return false; // variety anti-spam
        }

        // Momentum gating and owner urgency before invite/join
        // Momentum gate + owner urgency
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Follower path: try to join a nearby open invite if we also see a fish (quiet co-watching)
        // Species tags: open invite join
        GroupCoordinator.OpenInvite invite = GroupCoordinator
            .findNearbyOpenInvite(GoalIds.FISH_WATCHING, mob, Math.max(8.0D, GroupTuning.GROUP_RADIUS_DEFAULT))
            .orElse(null);
        if (invite != null) {
            // Enforce local “fish visible within 10 blocks” for the follower
            LivingEntity nearbyFish = findNearestVisibleFish(MAX_RANGE);
            if (nearbyFish != null) {
                float joinChance = 0.35f; // no owner boost for fish watching (quiet co-watching)
                if (mob.getRandom().nextFloat() < joinChance && GroupCoordinator.tryJoinOpenInvite(mob, invite)) {
                    targetFish = nearbyFish;
                    joiningAsFollower = true;
                    // Use GroupTuning follower stagger range
                    followerStaggerTicks = GroupTuning.FOLLOWER_STAGGER_TICKS_MIN
                        + mob.getRandom().nextInt(GroupTuning.FOLLOWER_STAGGER_TICKS_MAX - GroupTuning.FOLLOWER_STAGGER_TICKS_MIN + 1);
                    return true;
                }
            }
        }

        // Scan for nearest fish within MAX_RANGE blocks (visible) - initiator path
        LivingEntity fish = findNearestVisibleFish(MAX_RANGE);
        if (fish == null) {
            return false;
        }

        double dSq = mob.squaredDistanceTo(fish);
        // Note: We intentionally allow slightly out-of-range or near-contact cases;
        // findNearestVisibleFish already bounds the search. Keeping fish even if
        // a bit nearer than MIN_RANGE or at MAX_RANGE edge is acceptable for subtle behavior.

        targetFish = fish;
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Abort on owner urgency
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        if (targetFish == null || !targetFish.isAlive() || targetFish.isRemoved()) {
            return false;
        }
        if (!mob.canSee(targetFish)) {
            return false;
        }
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);

        // As initiator, publish an open invite with tiny join window
        // Species tags: open invite join
        if (!joiningAsFollower) {
            GroupCoordinator.publishOpenInvite(
                this.mob,
                GoalIds.FISH_WATCHING,
                Math.max(8.0D, GroupTuning.GROUP_RADIUS_DEFAULT),
                GroupTuning.MAX_FOLLOWERS_DEFAULT,
                GroupTuning.INVITE_EXPIRY_TICKS_DEFAULT,
                false
            );
        }

        // initial focus
        if (targetFish != null && followerStaggerTicks == 0) {
            mob.getLookControl().lookAt(targetFish, 22.0f, 22.0f);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int mainCd = secondsToTicks(90) + mob.getRandom().nextInt(secondsToTicks(91)); // 90–180s
            pc.setCooldown("fish_watching", mainCd);
            pc.setCooldown("environment_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16))); // 20–35s
        }
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetFish = null;
        joiningAsFollower = false;
        followerStaggerTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        if (targetFish == null) return;

        // Followers: respect stagger before starting visible action
        if (joiningAsFollower && ticks < Math.max(1, followerStaggerTicks)) {
            return;
        }

        // Maintain look
        if (ticks % 2 == 0) {
            mob.getLookControl().lookAt(targetFish, 20.0f, 20.0f);
        }

        // Subtle step adjustments: tiny reposition every ~1.5s to get a better view
        if (ticks % 30 == 0) {
            Vec3d fishPos = new Vec3d(targetFish.getX(), targetFish.getY(), targetFish.getZ());
            Vec3d mobPos = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
            Vec3d toFish = fishPos.subtract(mobPos);
            Vec3d dir = new Vec3d(toFish.x, 0, toFish.z).normalize();
            if (dir.lengthSquared() > 1.0e-4) {
                Vec3d step = new Vec3d(mob.getX(), mob.getY(), mob.getZ()).add(dir.multiply(0.5));
                // Navigation/pathing should be server-side only
                if (mob.getEntityWorld() instanceof ServerWorld && mob.getNavigation().isIdle()) {
                    mob.getNavigation().startMovingTo(step.x, step.y, step.z, 0.6);
                }
            }
        }

        // Micro body yaw drift
        if (ticks % 10 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 6.0f);
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.57f;
        if (targetFish != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetFish));
            float range = MathHelper.clamp((float) d, MIN_RANGE, MAX_RANGE);
            e += MathHelper.clamp((MAX_RANGE - range) / (MAX_RANGE * 2.0f), 0.0f, 0.08f);
        }
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Quiet curiosity
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.07f
        );
    }

    // === Helpers ===

    private LivingEntity findNearestVisibleFish(int range) {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return null;
        }
        List<LivingEntity> entities = sw.getEntitiesByClass(
            LivingEntity.class,
            mob.getBoundingBox().expand(range),
            e -> {
                if (e == mob || !e.isAlive() || e.isRemoved()) return false;
                var type = e.getType();
                // Accept cod, salmon, tropical fish, glow squid
                return type == net.minecraft.entity.EntityType.COD
                    || type == net.minecraft.entity.EntityType.SALMON
                    || type == net.minecraft.entity.EntityType.TROPICAL_FISH
                    || type == net.minecraft.entity.EntityType.GLOW_SQUID;
            }
        );
        if (entities.isEmpty()) return null;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            if (!mob.canSee(e)) continue;
            double d = mob.squaredDistanceTo(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}