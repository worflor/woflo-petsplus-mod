package woflo.petsplus.ai.suggester.signal.rules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;
import java.util.Set;

/**
 * Breeze threat proximity probe.
 * Server-only; short-circuits on first "minecraft:breeze" entity found near the pet.
 * Applies a modest desirability boost towards defensive/owner-hovering behaviors.
 * Includes a small debounce window to avoid oscillation.
 */
public final class BreezeThreatSignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/breeze_threat");
    private static final Set<Identifier> DEFENSIVE_GOALS = Set.of(
        GoalIds.OWNER_ORBIT,
        GoalIds.ORBIT_SWIM,
        GoalIds.LEAN_AGAINST_OWNER,
        GoalIds.PERCH_ON_SHOULDER,
        GoalIds.BEDTIME_COMPANION,
        GoalIds.CROUCH_APPROACH_RESPONSE,
        GoalIds.PURPOSEFUL_PATROL,
        GoalIds.SCENT_TRAIL_FOLLOW
    );

    // Micro debounce window in ticks to reduce per-tick scans
    private static final long COOLDOWN_TICKS = 40L;
    private static final int RADIUS = 16; // scan radius
    private static final double RADIUS_D = (double) RADIUS;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        if (!isDefensiveGoal(goal)) {
            return SignalResult.identity();
        }
        if (ctx == null || ctx.mob() == null) {
            return SignalResult.identity();
        }
        var pet = ctx.mob();
        World world = pet.getEntityWorld();
        if (world == null || world.isClient()) {
            return SignalResult.identity();
        }

        // Debounce using goal timing if available
        long since = ctx.ticksSince(goal);
        if (since >= 0 && since < COOLDOWN_TICKS) {
            return SignalResult.identity();
        }

        // Determine origin; prefer cached/current where possible
        BlockPos originPos = ctx.currentPos();
        if (originPos == null) {
            originPos = pet.getBlockPos();
            if (originPos == null) {
                return SignalResult.identity();
            }
        }

        // Build a tight AABB around the pet and ensure chunks are loaded
        double x = pet.getX();
        double y = pet.getY();
        double z = pet.getZ();
        Box box = new Box(
            x - RADIUS_D, y - RADIUS_D, z - RADIUS_D,
            x + RADIUS_D, y + RADIUS_D, z + RADIUS_D
        );

        if (!areBoundsChunksLoaded(world, box)) {
            // No-op if not safely loaded
            return SignalResult.identity();
        }

        // Resolve Breeze type via registry lookup to avoid hard dependency on class
        final Identifier breezeId = Identifier.of("minecraft", "breeze");
        if (!Registries.ENTITY_TYPE.containsId(breezeId)) {
            // Breeze might not exist in the current MC version/mod set
            return SignalResult.identity();
        }
        EntityType<?> breezeType = Registries.ENTITY_TYPE.get(breezeId);
        if (breezeType == null) {
            return SignalResult.identity();
        }

        // Iterate entities with a lightweight filter; short-circuit on first match
        for (Entity e : world.getOtherEntities(pet, box)) {
            if (e == null || e.isRemoved()) {
                continue;
            }
            if (e.getType() == breezeType) {
                float v = 1.30f; // modest boost within 1.25x–1.35x
                return new SignalResult(v, v, Map.of(
                    "matched", breezeId.toString(),
                    "pos", e.getBlockPos().toShortString()
                ));
            }
        }

        return SignalResult.identity();
    }

    private static boolean isDefensiveGoal(GoalDefinition goal) {
        if (goal == null) {
            return false;
        }
        Identifier goalId = goal.id();
        if (goalId != null && DEFENSIVE_GOALS.contains(goalId)) {
            return true;
        }
        return goal.category() == GoalDefinition.Category.SOCIAL
            && goalId != null
            && goalId.getPath().contains("owner");
    }

    private static boolean areBoundsChunksLoaded(World world, Box box) {
        BlockPos a = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos b = BlockPos.ofFloored(box.maxX, box.minY, box.minZ);
        BlockPos c = BlockPos.ofFloored(box.minX, box.minY, box.maxZ);
        BlockPos d = BlockPos.ofFloored(box.maxX, box.minY, box.maxZ);
        BlockPos e = BlockPos.ofFloored((box.minX + box.maxX) * 0.5, box.maxY, (box.minZ + box.maxZ) * 0.5);
        if (!world.isChunkLoaded(a)) return false;
        if (!world.isChunkLoaded(b)) return false;
        if (!world.isChunkLoaded(c)) return false;
        if (!world.isChunkLoaded(d)) return false;
        if (!world.isChunkLoaded(e)) return false;
        return true;
    }
}