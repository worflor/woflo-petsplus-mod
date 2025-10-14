package woflo.petsplus.ai.goals.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
// Species tags: multi-tag gating
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Subtle behavior: P1
 * HearthSettleGoal - sunbasker species find a warm lit block (furnace/campfire/fireplace) and settle/rest nearby.
 *
 * Preconditions:
 * - Lit furnace/campfire/fireplace within 8 blocks, line-of-sight to target
 * - Safe temperature (avoid harmful blocks), no rain
 *
 * Momentum gating:
 * - High (≥0.65) blocks; Mid (0.35–0.65) halves start chance
 *
 * Cooldown:
 * - 120–240s via "hearth_settle" + variety micro "world_micro" 20–35s
 */
public class HearthSettleGoal extends AdaptiveGoal {
    private static final int MIN_TICKS = 160; // 8s
    private static final int MAX_TICKS = 240; // 12s
    private static final int SCAN_RANGE = 8;

    private int ticks;
    private int duration;
    private Vec3d settlePos;

    public HearthSettleGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HEARTH_SETTLE), java.util.EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating - cached O(1)
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.sunBasker()) {
            return false;
        }

        // No rain for cozy hearth settle
        if (mob.getEntityWorld().isRaining()) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.isOnCooldown("world_micro")) {
            return false;
        }
        if (pc != null && pc.isOnCooldown("hearth_settle")) {
            return false;
        }

        // Owner urgency/threat abort
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            OwnerCombatState ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Momentum gating
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        // Find a nearby warm block with LOS and a safe adjacent settle spot
        BlockPos warm = findWarmBlock(SCAN_RANGE);
        if (warm == null) return false;

        Vec3d candidate = findAdjacentSafeSettle(warm);
        if (candidate == null) return false;

        // LOS check from eyes to warm block
        if (!hasLineOfSightTo(warm)) {
            return false;
        }

        settlePos = candidate;
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);
        if (settlePos != null) {
            mob.getNavigation().startMovingTo(settlePos.x, settlePos.y, settlePos.z, 0.75);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int cd = secondsToTicks(120) + mob.getRandom().nextInt(secondsToTicks(121)); // 120–240s
            pc.setCooldown("hearth_settle", cd);
            int variety = secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16)); // 20–35s
            pc.setCooldown("world_micro", variety);
        }
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        settlePos = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Once arrived, settle/rest: small breathing motion and soft gaze toward the warm block area
        if (settlePos != null && mob.squaredDistanceTo(settlePos) <= 1.0) {
            // Gentle "breathe"
            float phase = (ticks % 40) / 40.0f;
            float pitch = (float) Math.sin(phase * MathHelper.TAU) * 6.0f;
            mob.setPitch(MathHelper.clamp(pitch, -10.0f, 10.0f));

            // Micro yaw drift
            if (ticks % 20 == 0) {
                mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 6.0f);
            }
        } else if (settlePos != null && mob.getNavigation().isIdle()) {
            // Keep approaching until close
            mob.getNavigation().startMovingTo(settlePos.x, settlePos.y, settlePos.z, 0.75);
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.62f; // comforting warmth
        if (settlePos != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(settlePos));
            e += MathHelper.clamp((float) ((SCAN_RANGE - d) / (SCAN_RANGE * 2.0)), 0.0f, 0.1f);
        }
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Warmth/contentment
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f
        );
    }

    // ==== Helpers ====

    private BlockPos findWarmBlock(int range) {
        BlockPos origin = mob.getBlockPos();
        var world = mob.getEntityWorld();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (MathHelper.sqrt((float) (dx * dx + dz * dz)) > range) continue;

                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (isWarmState(state)) {
                        return pos.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean isWarmState(BlockState state) {
        Block block = state.getBlock();

        // Lit property (furnaces, smokers, blast furnaces, campfires)
        if (state.contains(Properties.LIT) && state.get(Properties.LIT)) {
            // Avoid standing directly on hot surfaces - settle adjacent
            return true;
        }

        // Fireplace: open fire blocks
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
            return true;
        }

        // Avoid counting MAGMA as a "warm settle" source (too hot)
        return false;
    }

    private Vec3d findAdjacentSafeSettle(BlockPos warm) {
        var world = mob.getEntityWorld();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos ground = warm.offset(dir);
            BlockPos head = ground.up();
            BlockState groundState = world.getBlockState(ground);
            BlockState headState = world.getBlockState(head);

            if (!groundState.isSolidBlock(world, ground)) continue;
            if (!headState.isAir()) continue;

            // Avoid dangerous ground
            if (groundState.isOf(Blocks.MAGMA_BLOCK) || groundState.isOf(Blocks.FIRE)) continue;

            return Vec3d.ofCenter(head);
        }
        // Fallback: try one block further
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos ground = warm.offset(dir, 2);
            BlockPos head = ground.up();
            BlockState groundState = world.getBlockState(ground);
            BlockState headState = world.getBlockState(head);
            if (groundState.isSolidBlock(world, ground) && headState.isAir()) {
                if (!groundState.isOf(Blocks.MAGMA_BLOCK) && !groundState.isOf(Blocks.FIRE)) {
                    return Vec3d.ofCenter(head);
                }
            }
        }
        return null;
    }

    private boolean hasLineOfSightTo(BlockPos target) {
        var world = mob.getEntityWorld();
        Vec3d from = mob.getEyePos();
        Vec3d to = Vec3d.ofCenter(target);
        BlockHitResult hit = world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mob
        ));
        // If we miss or we hit the target block, consider LOS valid
        if (hit == null) return true;
        if (hit.getType() == BlockHitResult.Type.MISS) return true;
        return hit.getBlockPos().equals(target);
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}