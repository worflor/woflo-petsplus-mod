package woflo.petsplus.ai.goals.environmental;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.ai.group.GroupCoordinator;
// GroupTuning: central constants
import woflo.petsplus.ai.group.GroupTuning;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * LeafChaseWindGoal - environmental playful darting toward wind-stirred leaves.
 * Subtle behavior: P0
 * Species tags: multi-tag gating
 *
 * Gating:
 * - Requires playfulSpecies
 *
 * Predicates:
 * - Windy/stormy weather boosts likelihood and extends search tolerance
 * - Scans for leaf blocks within ~6 blocks; fallback: random dart during storm
 *
 * Behavior:
 * - Short dart (4–8s) toward target position then stop
 *
 * Cooldown:
 * - 90–180s using PetComponent cooldown key "leaf_chase_wind"
 */
public class LeafChaseWindGoal extends AdaptiveGoal {
    private static final int MIN_DART_TICKS = 80;  // 4s
    private static final int MAX_DART_TICKS = 160; // 8s
    private static final int BASE_SCAN_RANGE = 6;

    private int dartTicks;
    private int dartDuration;
    private Vec3d targetPos;

    // Open invite follower state
    private boolean joiningAsFollower = false; // Species tags: open invite join
    private int followerStaggerTicks = 0;

    public LeafChaseWindGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.LEAF_CHASE_WIND), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (cached profile, O(1))
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.playfulSpecies()) {
            return false;
        }

        // Variety micro spacing
        PetComponent pc0 = PetComponent.get(mob);
        if (pc0 != null && pc0.isOnCooldown("environment_micro")) {
            return false; // Variety micro spacing
        }

        // Momentum gate + owner urgency
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        if (pc0 != null && pc0.getCachedOwnerEntity() != null) {
            var ocs = OwnerCombatState.get(pc0.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false; // Momentum gate + owner urgency
            }
        }

        // Try to consume nearby open invite first (follower path)
        // Species tags: open invite join
        GroupCoordinator.OpenInvite invite = GroupCoordinator
            .findNearbyOpenInvite(GoalIds.LEAF_CHASE_WIND, mob, GroupTuning.GROUP_RADIUS_DEFAULT)
            .orElse(null);
        if (invite != null) {
            // Simple acceptance heuristic with optional owner "tag-in" boost (does not alter cooldowns)
            float joinChance = 0.35f;
            if (invite.allowOwnerSneakBoost) {
                var pc = woflo.petsplus.state.PetComponent.get(mob);
                var owner = pc != null ? pc.getCachedOwnerEntity() : null;
                // Owner dimension guard for sneak boost
                if (owner != null
                    && owner.getEntityWorld().getRegistryKey() == mob.getEntityWorld().getRegistryKey()
                    && owner.isSneaking()
                    && owner.squaredDistanceTo(mob) <= GroupTuning.OWNER_SNEAK_BOOST_RADIUS_SQ) {
                    joinChance = Math.min(0.9f, joinChance * 1.2f);
                }
            }
            if (mob.getRandom().nextFloat() < joinChance && GroupCoordinator.tryJoinOpenInvite(mob, invite)) {
                // Align toward initiator with a small offset
                if (mob.getEntityWorld() instanceof ServerWorld sw) {
                    var ent = sw.getEntity(invite.initiatorUuid);
                    if (ent instanceof net.minecraft.entity.mob.MobEntity initMob) {
                        Vec3d base = new Vec3d(initMob.getX(), initMob.getY(), initMob.getZ());
                        double ox = (mob.getRandom().nextDouble() - 0.5) * 1.2;
                        double oz = (mob.getRandom().nextDouble() - 0.5) * 1.2;
                        targetPos = base.add(ox, 0, oz);
                    }
                }
                // Stagger follower start using GroupTuning range
                joiningAsFollower = true;
                followerStaggerTicks = GroupTuning.FOLLOWER_STAGGER_TICKS_MIN
                    + mob.getRandom().nextInt(GroupTuning.FOLLOWER_STAGGER_TICKS_MAX - GroupTuning.FOLLOWER_STAGGER_TICKS_MIN + 1);
                // If no initiator found (edge), still allow start with no movement target; will settle quickly
                return true;
            }
        }

        int scanRange = BASE_SCAN_RANGE;
        var world = mob.getEntityWorld();
        boolean stormy = world.isRaining() || world.isThundering();
        if (stormy) {
            scanRange += 2; // Stormy weather boost
        }

        BlockPos found = findNearbyLeaves(scanRange);
        if (found != null) {
            targetPos = Vec3d.ofCenter(found);
            return true;
        }

        // Fallback: occasional random dart during storm
        if (stormy && mob.getRandom().nextFloat() < 0.20f) {
            Vec3d random = pickNearbyGroundTarget(scanRange);
            if (random != null) {
                targetPos = random;
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Momentum gate + owner urgency
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }
        return dartTicks < dartDuration;
    }

    @Override
    protected void onStartGoal() {
        dartTicks = 0;
        dartDuration = MIN_DART_TICKS + mob.getRandom().nextInt((MAX_DART_TICKS - MIN_DART_TICKS) + 1);

        // As initiator, publish an open invite with tiny join window
        // Species tags: open invite join
        if (!joiningAsFollower) {
            GroupCoordinator.publishOpenInvite(
                this.mob,
                GoalIds.LEAF_CHASE_WIND,
                GroupTuning.GROUP_RADIUS_DEFAULT,
                GroupTuning.MAX_FOLLOWERS_DEFAULT,
                GroupTuning.INVITE_EXPIRY_TICKS_DEFAULT,
                true
            );
        }

        // Begin a quick dart toward the target (followers may stagger before moving)
        if (targetPos != null && followerStaggerTicks == 0) {
            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.15);
        }
    }

    @Override
    protected void onStopGoal() {
        // Apply PetComponent cooldown as per spec
        // Subtle behavior: P0
        var pc = woflo.petsplus.state.PetComponent.get(mob);
        if (pc != null) {
            int cooldown = 90 * 20 + mob.getRandom().nextInt((180 - 90 + 1) * 20); // 90–180s
            pc.setCooldown("leaf_chase_wind", cooldown);
            // Variety micro spacing
            pc.setCooldown("environment_micro", 20 * 20 + mob.getRandom().nextInt(16 * 20)); // Variety micro spacing
        }

        // Stop movement
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetPos = null;
        joiningAsFollower = false;
        followerStaggerTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        dartTicks++;

        // Followers: respect stagger before starting movement
        if (joiningAsFollower && targetPos != null && dartTicks == followerStaggerTicks) {
            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.1);
        }

        // Keep moving toward the target but do not overshoot; if close, slow down slightly
        if (targetPos != null) {
            double distSq = mob.squaredDistanceTo(targetPos);
            double speed = distSq > 4.0 ? 1.2 : 0.9;
            if (mob.getNavigation().isIdle()) {
                // If still within follower stagger window, delay re-issuing orders
                if (!(joiningAsFollower && dartTicks < Math.max(1, followerStaggerTicks))) {
                    mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, speed);
                }
            }
        }

        // Subtle head/eye adjustments for wind focus
        if (dartTicks % 8 == 0) {
            mob.setYaw(mob.bodyYaw + mob.getRandom().nextFloat() * 6f - 3f);
        }
    }

    @Override
    protected float calculateEngagement() {
        float engagement = 0.65f; // playful dart
        var world = mob.getEntityWorld();
        if (world.isRaining() || world.isThundering()) {
            engagement += 0.1f; // Windy boost
        }
        // Slight modulation by proximity to target
        if (targetPos != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetPos));
            engagement += MathHelper.clamp((float) ((BASE_SCAN_RANGE - d) / (BASE_SCAN_RANGE * 2.0)), -0.05f, 0.1f);
        }
        return MathHelper.clamp(engagement, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // A touch of playful joy
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.10f
        );
    }

    private BlockPos findNearbyLeaves(int range) {
        BlockPos origin = mob.getBlockPos();
        var world = mob.getEntityWorld();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (MathHelper.sqrt((float)(dx * dx + dz * dz)) > range) continue;
                // Scan up to 2 blocks vertically to catch low foliage
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isIn(BlockTags.LEAVES)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private Vec3d pickNearbyGroundTarget(int range) {
        BlockPos origin = mob.getBlockPos();
        var rnd = mob.getRandom();
        for (int tries = 0; tries < 10; tries++) {
            int dx = rnd.nextBetween(-range, range);
            int dz = rnd.nextBetween(-range, range);
            if (MathHelper.sqrt((float)(dx * dx + dz * dz)) > range) continue;
            BlockPos pos = origin.add(dx, 0, dz);
            // Require solid ground just below and air at head height
            var world = mob.getEntityWorld();
            if (world.getBlockState(pos).isSolidBlock(world, pos) && world.isAir(pos.up())) {
                return Vec3d.ofCenter(pos.up());
            }
        }
        return null;
    }
}