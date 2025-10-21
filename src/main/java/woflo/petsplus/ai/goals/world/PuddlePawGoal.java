package woflo.petsplus.ai.goals.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
import woflo.petsplus.ai.group.GroupCoordinator;
// GroupTuning: central constants
import woflo.petsplus.ai.group.GroupTuning;

import java.util.EnumSet;

/**
 * Subtle behavior: P1
 * PuddlePawGoal - pets seek a shallow water edge during light rain or when a puddle is nearby and do a brief splash/footstep wiggle.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): aquatic || playfulSpecies
 *
 * Preconditions:
 * - Light rain OR waterlogged/shallow water (≤ 1 block deep) within 6 blocks
 * - No immediate combat threat; owner not in urgent combat
 *
 * Momentum gating:
 * - High (≥0.65) blocks start
 * - Mid (0.35–0.65) halves the internal start chance
 *
 * Cooldowns:
 * - Main: 60–120s via PetComponent key "puddle_paw"
 * - Variety: 20–35s micro cooldown via "world_micro"
 */
public class PuddlePawGoal extends AdaptiveGoal {
    private static final int MIN_DURATION_TICKS = 80;   // 4s
    private static final int MAX_DURATION_TICKS = 140;  // 7s
    private static final int SCAN_RANGE = 6;

    private int ticks;
    private int duration;
    private Vec3d targetPos;

    // Open invite follower state
    private boolean joiningAsFollower = false; // Species tags: open invite join
    private int followerStaggerTicks = 0;
    private Vec3d lastIssuedNavigationTarget;

    public PuddlePawGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PUDDLE_PAW), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating - O(1) cached
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!(profile.aquatic() || profile.playfulSpecies())) {
            return false;
        }

        // Variety micro-cooldown to avoid spam across "world" category
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.isOnCooldown("world_micro")) {
            return false; // Variety micro spacing
        }
        if (pc != null && pc.isOnCooldown("puddle_paw")) {
            return false;
        }

        // Momentum gating (explicit as per spec)
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) {
            return false; // high band blocks
        }
        if (m >= 0.35f && mob.getRandom().nextBoolean()) {
            // mid band halves probability
            return false;
        }

        // Owner urgency abort
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var state = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (state != null && (state.isInCombat() || state.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Attempt to join an open invite first (follower path)
        // Species tags: open invite join
        GroupCoordinator.OpenInvite invite = GroupCoordinator
            .findNearbyOpenInvite(GoalIds.PUDDLE_PAW, mob, GroupTuning.GROUP_RADIUS_DEFAULT)
            .orElse(null);
        if (invite != null) {
            // Require a shallow water edge near follower as well
            BlockPos followerEdge = findShallowWaterEdge(SCAN_RANGE);
            if (followerEdge != null) {
                // Acceptance heuristic with optional owner “tag-in” boost
                float joinChance = 0.35f;
                if (invite.allowOwnerSneakBoost && pc != null) {
                    var owner = pc.getCachedOwnerEntity();
                    // Owner dimension guard for sneak boost
                    if (owner != null
                        && owner.getEntityWorld().getRegistryKey() == mob.getEntityWorld().getRegistryKey()
                        && owner.isSneaking()
                        && owner.squaredDistanceTo(mob) <= GroupTuning.OWNER_SNEAK_BOOST_RADIUS_SQ) {
                        joinChance = Math.min(0.9f, joinChance * 1.2f);
                    }
                }
                if (mob.getRandom().nextFloat() < joinChance && GroupCoordinator.tryJoinOpenInvite(mob, invite)) {
                    targetPos = Vec3d.ofCenter(followerEdge);
                    // Stagger follower start using GroupTuning range
                    joiningAsFollower = true;
                    followerStaggerTicks = GroupTuning.FOLLOWER_STAGGER_TICKS_MIN
                        + mob.getRandom().nextInt(GroupTuning.FOLLOWER_STAGGER_TICKS_MAX - GroupTuning.FOLLOWER_STAGGER_TICKS_MIN + 1);
                    lastIssuedNavigationTarget = null;
                    return true;
                }
            }
        }

        // World predicates: light rain OR shallow/waterlogged nearby
        boolean raining = mob.getEntityWorld().isRaining();
        BlockPos edge = findShallowWaterEdge(SCAN_RANGE);
        if (!raining && edge == null) {
            return false;
        }

        if (edge != null) {
            targetPos = Vec3d.ofCenter(edge);
        } else {
            // No puddle found but raining: pick a small local wander to wiggle feet
            targetPos = pickNearbyGround(SCAN_RANGE);
            if (targetPos == null) {
                return false;
            }
        }
        lastIssuedNavigationTarget = null;

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // abort on owner urgency
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var state = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (state != null && (state.isInCombat() || state.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_DURATION_TICKS + mob.getRandom().nextInt((MAX_DURATION_TICKS - MIN_DURATION_TICKS) + 1);

        // As initiator, publish an open invite with tiny join window
        // Species tags: open invite join
        if (!joiningAsFollower) {
            GroupCoordinator.publishOpenInvite(
                this.mob,
                GoalIds.PUDDLE_PAW,
                GroupTuning.GROUP_RADIUS_DEFAULT,
                GroupTuning.MAX_FOLLOWERS_DEFAULT,
                GroupTuning.INVITE_EXPIRY_TICKS_DEFAULT,
                true
            );
        }

        if (targetPos != null && followerStaggerTicks == 0) {
            issueNavigationCommand(0.95);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int mainCd = secondsToTicks(60) + mob.getRandom().nextInt(secondsToTicks(61)); // 60–120s
            pc.setCooldown("puddle_paw", mainCd);

            int varietyCd = secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16)); // 20–35s
            pc.setCooldown("world_micro", varietyCd);
        }

        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetPos = null;
        joiningAsFollower = false;
        followerStaggerTicks = 0;
        lastIssuedNavigationTarget = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Followers: respect stagger before starting movement
        if (joiningAsFollower && targetPos != null && ticks == Math.max(1, followerStaggerTicks)) {
            issueNavigationCommand(0.9);
        }

        // Subtle splash/footstep wiggle near the edge/spot
        if (ticks % 8 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 8.0f);
        }
        if (ticks % 12 == 0) {
            float bob = (float) Math.sin((ticks % 20) / 20.0f * MathHelper.TAU) * 10.0f;
            mob.setPitch(MathHelper.clamp(bob, -18.0f, 18.0f));
        }

        // Keep moving if not yet arrived
        if (targetPos != null) {
            double dSq = mob.squaredDistanceTo(targetPos);
            double speed = dSq > 2.25 ? 1.05 : 0.75;
            if (!(joiningAsFollower && ticks < Math.max(1, followerStaggerTicks))) {
                issueNavigationCommand(speed);
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        float engagement = 0.58f;
        if (mob.getEntityWorld().isRaining()) engagement += 0.08f;
        if (targetPos != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetPos));
            engagement += MathHelper.clamp((float) ((SCAN_RANGE - d) / (SCAN_RANGE * 2.0)), -0.03f, 0.08f);
        }
        return MathHelper.clamp(engagement, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Small playful pulse
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.08f
        );
    }

    // === Helpers ===

    private BlockPos findShallowWaterEdge(int range) {
        BlockPos origin = mob.getBlockPos();
        var world = mob.getEntityWorld();

        // Scan a small, shallow grid around the mob
        int rangeSq = range * range;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > rangeSq) continue;

                BlockPos waterPos = origin.add(dx, 0, dz);
                // Probe up to +-1Y to catch slight height differences
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = waterPos.add(0, dy, 0);
                    if (!isShallowWater(world.getBlockState(pos), pos)) continue;

                    // Look for an adjacent edge: solid ground with air above
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockPos edge = pos.offset(dir);
                        if (isEdgePosition(edge)) {
                            return edge.up(); // stand just above solid ground
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isShallowWater(BlockState state, BlockPos pos) {
        var world = mob.getEntityWorld();
        FluidState fluid = state.getFluidState();
        if (!fluid.isOf(Fluids.WATER)) {
            // also treat waterlogged blocks as water
            if (!state.contains(net.minecraft.state.property.Properties.WATERLOGGED)) {
                return false;
            }
            Boolean wl = state.get(net.minecraft.state.property.Properties.WATERLOGGED);
            if (wl == null || !wl) return false;
        }

        // "Shallow": solid ground immediately below or only one block of water depth
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        boolean solidBelow = belowState.isSolidBlock(world, below);

        if (solidBelow) return true;

        // If not solid below, ensure only one block deep
        BlockPos twoBelow = pos.down(2);
        FluidState belowFluid = world.getBlockState(below).getFluidState();
        if (!belowFluid.isOf(Fluids.WATER)) return false;
        // If two blocks below is also water, it's deeper than 1
        return !world.getBlockState(twoBelow).getFluidState().isOf(Fluids.WATER);
    }

    private boolean isEdgePosition(BlockPos pos) {
        var world = mob.getEntityWorld();
        BlockState ground = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());

        // Require solid footing and headroom
        if (!ground.isSolidBlock(world, pos)) return false;
        if (!head.isAir() && !head.getFluidState().isEmpty()) return false;

        // Avoid standing in direct damaging blocks
        return !ground.isOf(Blocks.FIRE) && !ground.isOf(Blocks.MAGMA_BLOCK);
    }

    private Vec3d pickNearbyGround(int range) {
        var world = mob.getEntityWorld();
        BlockPos origin = mob.getBlockPos();

        int rangeSq = range * range;
        for (int tries = 0; tries < 10; tries++) {
            int dx = mob.getRandom().nextBetween(-range, range);
            int dz = mob.getRandom().nextBetween(-range, range);
            int distSq = dx * dx + dz * dz;
            if (distSq > rangeSq) continue;

            BlockPos base = origin.add(dx, 0, dz);
            BlockPos ground = base.down();

            // Require solid footing below with a clear air column for the pet to stand in
            BlockState groundState = world.getBlockState(ground);
            if (!groundState.isSolidBlock(world, ground)) {
                continue;
            }

            BlockState spaceState = world.getBlockState(base);
            if (!spaceState.isAir()) {
                continue;
            }

            if (!world.isAir(base.up())) {
                continue;
            }

            return Vec3d.ofCenter(base);
        }
        return null;
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }

    private void issueNavigationCommand(double speed) {
        if (targetPos == null) {
            return;
        }
        if (!mob.getNavigation().isIdle() && lastIssuedNavigationTarget != null
            && targetPos.squaredDistanceTo(lastIssuedNavigationTarget) <= 0.04) {
            return;
        }
        mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, speed);
        lastIssuedNavigationTarget = targetPos;
    }
}
