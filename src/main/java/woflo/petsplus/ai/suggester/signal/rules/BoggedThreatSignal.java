package woflo.petsplus.ai.suggester.signal.rules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;
import java.util.Set;

/**
 * Bogged threat proximity probe.
 * Server-only; short-circuits on first "minecraft:bogged" entity found near the pet.
 * Applies a modest desirability boost towards defensive/owner-hovering behaviors.
 * Includes a small debounce window to avoid oscillation. Side-safe and allocation-light.
 */
public final class BoggedThreatSignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/bogged_threat");
    private static final Set<Identifier> DEFENSIVE_GOALS = Set.of(
        GoalIds.OWNER_ORBIT,
        GoalIds.ORBIT_SWIM,
        GoalIds.LEAN_AGAINST_OWNER,
        GoalIds.PERCH_ON_SHOULDER,
        GoalIds.CROUCH_APPROACH_RESPONSE,
        GoalIds.LEAD_FOLLOW_NUDGE,
        GoalIds.PURPOSEFUL_PATROL,
        GoalIds.SCENT_TRAIL_FOLLOW
    );

    // Micro debounce window in ticks to reduce per-tick scans (mirror Breeze)
    private static final long COOLDOWN_TICKS = 40L;
    private static final int RADIUS = 16;
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

        // Tight AABB and chunk-load guard
        double x = pet.getX();
        double y = pet.getY();
        double z = pet.getZ();
        Box box = new Box(
            x - RADIUS_D, y - RADIUS_D, z - RADIUS_D,
            x + RADIUS_D, y + RADIUS_D, z + RADIUS_D
        );

        if (!areBoundsChunksLoaded(world, box)) {
            return SignalResult.identity();
        }

        // Resolve Bogged via registry; avoid hard class ref
        final Identifier boggedId = Identifier.of("minecraft", "bogged");
        if (!Registries.ENTITY_TYPE.containsId(boggedId)) {
            return SignalResult.identity();
        }
        EntityType<?> boggedType = Registries.ENTITY_TYPE.get(boggedId);
        if (boggedType == null) {
            return SignalResult.identity();
        }

        // Iterate world entities within box; short-circuit on first match
        for (Entity e : world.getOtherEntities(pet, box)) {
            if (e == null || e.isRemoved()) {
                continue;
            }
            if (e.getType() == boggedType) {
                float v = 1.32f; // within 1.25–1.35; slightly defensive bias
                return new SignalResult(v, v, Map.of(
                    "matched", boggedId.toString(),
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