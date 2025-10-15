package woflo.petsplus.ai.goals.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.List;
import java.util.UUID;

/**
 * Reworked HearthSettleGoal: Pets seek out warm places to rest when they feel cozy and safe.
 * A place is "warm" if it emits a high level of light (13+) and is not a magical light source.
 * A pet feels "cozy and safe" at night, when not raining, when near its owner, and when energy is low.
 */
public class HearthSettleGoal extends AdaptiveGoal {
    private static final int MIN_TICKS = 200; // 10s
    private static final int MAX_TICKS = 400; // 20s
    private static final int SCAN_RANGE = 10;

    private int ticks;
    private int duration;
    private Vec3d settlePos;

    public HearthSettleGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HEARTH_SETTLE), java.util.EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        settlePos = null;
        // Condition: Must be night and not raining.
        if (mob.getEntityWorld().isDay() || mob.getEntityWorld().isRaining()) {
            return false;
        }

        // Condition: Pet must be in a low-energy state.
        MomentumState ms = MomentumState.capture(mob);
        if (ms.momentum() >= 0.4f) return false; // Stricter momentum check

        PetComponent pc = PetComponent.get(mob);
        if (pc == null) return false;

        // Condition: Cooldowns must not be active.
        if (pc.isOnCooldown("world_micro") || pc.isOnCooldown("hearth_settle")) {
            return false;
        }

        Vec3d huddleTarget = findHuddleTarget(pc);
        if (huddleTarget != null) {
            settlePos = huddleTarget;
            return true;
        }

        // Condition: Owner must be nearby and safe.
        var owner = pc.getCachedOwnerEntity();
        if (owner == null || owner.distanceTo(mob) > 16) {
            return false;
        }
        OwnerCombatState ocs = OwnerCombatState.get(owner);
        if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
            return false;
        }

        // Action: Find a suitable warm spot.
        BlockPos warm = findWarmBlock(SCAN_RANGE);
        if (warm == null) return false;

        Vec3d candidate = findAdjacentSafeSettle(warm);
        if (candidate == null) return false;

        if (!hasLineOfSightTo(warm)) {
            return false;
        }

        settlePos = candidate;
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Stop if conditions are no longer met (e.g., owner leaves, threat appears)
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            var owner = pc.getCachedOwnerEntity();
            if (owner == null || owner.distanceTo(mob) > 20) return false;
            OwnerCombatState ocs = OwnerCombatState.get(owner);
            if (ocs != null && ocs.isInCombat()) return false;
        }
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
            int cd = secondsToTicks(120) + mob.getRandom().nextInt(secondsToTicks(121)); // 2-4 minute cooldown
            pc.setCooldown("hearth_settle", cd);
            int variety = secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16));
            pc.setCooldown("world_micro", variety);
        }
        if (mob.canMoveVoluntarily()) {
            setSitting(false);
        }
        mob.getNavigation().stop();
        settlePos = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;
        if (settlePos == null) return;

        // Once arrived, sit down and settle.
        if (mob.squaredDistanceTo(settlePos) <= 2.0) {
            if (mob.canMoveVoluntarily()) {
                setSitting(true);
            }
            mob.getNavigation().stop();

            // Gentle "breathing" animation
            float phase = (ticks % 40) / 40.0f;
            float pitch = (float) Math.sin(phase * MathHelper.TAU) * 4.0f;
            mob.setPitch(MathHelper.clamp(pitch, -8.0f, 8.0f));
        } else if (mob.getNavigation().isIdle()) {
            // Keep approaching if pathfinding stopped for some reason
            mob.getNavigation().startMovingTo(settlePos.x, settlePos.y, settlePos.z, 0.75);
        }
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Boosts contentment while resting by the fire
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.15f,
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f
        );
    }

    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.62f;

        if (!mob.getEntityWorld().isDay()) {
            engagement += 0.08f;
        }
        if (mob.getEntityWorld().isRaining() || mob.getEntityWorld().isThundering()) {
            engagement -= 0.12f;
        }

        // More engaging when stamina is low (seeking rest)
        float stamina = ctx.physicalStamina();
        engagement += MathHelper.clamp((0.5f - stamina) * 0.4f, -0.1f, 0.18f);

        // Comfortable moods reinforce desire to settle
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.3f)) {
            engagement += 0.1f;
        }

        if (settlePos != null) {
            double dist = Math.sqrt(mob.squaredDistanceTo(settlePos));
            engagement += MathHelper.clamp((SCAN_RANGE - dist) / (SCAN_RANGE * 2.0f), -0.08f, 0.12f);
        }

        return MathHelper.clamp(engagement, 0f, 1f);
    }

    // ==== Helpers ====

    private BlockPos findWarmBlock(int range) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = 0; y <= range; y++) {
            for (int x = 0; x <= range; x++) {
                for (int z = 0; z <= range; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    mutable.set(mob.getBlockPos(), x, y, z);
                    if (isHeatSource(mob.getEntityWorld().getBlockState(mutable))) return mutable.toImmutable();
                    mutable.set(mob.getBlockPos(), -x, y, z);
                    if (isHeatSource(mob.getEntityWorld().getBlockState(mutable))) return mutable.toImmutable();
                    mutable.set(mob.getBlockPos(), x, y, -z);
                    if (isHeatSource(mob.getEntityWorld().getBlockState(mutable))) return mutable.toImmutable();
                    mutable.set(mob.getBlockPos(), -x, y, -z);
                    if (isHeatSource(mob.getEntityWorld().getBlockState(mutable))) return mutable.toImmutable();
                }
            }
        }
        return null;
    }

    private boolean isHeatSource(BlockState state) {
        // Condition 1: Must be bright enough (13+)
        if (state.getLuminance() < 13) {
            return false;
        }

        // Condition 2: Filter out magical/non-thermal lights
        Block block = state.getBlock();
        if (block == Blocks.GLOWSTONE || block == Blocks.SEA_LANTERN || block == Blocks.SHROOMLIGHT || block == Blocks.BEACON) {
            return false;
        }

        return true;
    }



    private Vec3d findAdjacentSafeSettle(BlockPos warm) {
        var world = mob.getEntityWorld();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int i = 1; i <= 2; i++) { // Check 1 and 2 blocks away
                BlockPos groundPos = warm.offset(dir, i);
                BlockState groundState = world.getBlockState(groundPos);
                if (groundState.isSolidBlock(world, groundPos) && world.isAir(groundPos.up())) {
                    // Avoid dangerous ground
                    if (!groundState.isOf(Blocks.MAGMA_BLOCK) && !groundState.isOf(Blocks.CACTUS) && !groundState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                        return Vec3d.ofBottomCenter(groundPos).add(0, 1.0, 0);
                    }
                }
            }
        }
        return null;
    }

    private Vec3d findHuddleTarget(PetComponent pc) {
        UUID ownerId = pc.getOwnerUuid();
        if (ownerId == null) {
            return null;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        PetSwarmIndex swarmIndex = StateManager.forWorld(serverWorld).getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(ownerId);
        if (entries.isEmpty()) {
            return null;
        }
        for (PetSwarmIndex.SwarmEntry entry : entries) {
            MobEntity other = entry.pet();
            if (other == null || other == mob) {
                continue;
            }
            PetComponent otherComponent = entry.component();
            if (otherComponent == null) {
                otherComponent = PetComponent.get(other);
            }
            if (otherComponent == null) {
                continue;
            }
            if (GoalIds.HEARTH_SETTLE.equals(otherComponent.getAIState().getActiveMajorGoal())) {
                Vec3d adjacent = findAdjacentSafeSettle(other.getBlockPos().down());
                if (adjacent != null) {
                    return adjacent;
                }
            }
        }
        return null;
    }

    private boolean hasLineOfSightTo(BlockPos target) {
        Vec3d from = mob.getEyePos();
        Vec3d to = Vec3d.ofCenter(target);
        return mob.getEntityWorld().raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mob
        )).getType() == BlockHitResult.Type.MISS;
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }

    private void setSitting(boolean sitting) {
        if (mob instanceof PetsplusTameable tameable) {
            tameable.petsplus$setSitting(sitting);
        } else if (mob instanceof TameableEntity tameableEntity) {
            tameableEntity.setSitting(sitting);
        }
    }
}
