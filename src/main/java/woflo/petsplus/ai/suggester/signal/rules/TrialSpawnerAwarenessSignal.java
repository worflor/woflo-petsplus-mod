package woflo.petsplus.ai.suggester.signal.rules;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;

/**
 * Minimal proximity probe around the pet for Trial Chamber feature blocks.
 * Short-circuits on first match within a tiny cube radius to avoid heavy scans.
 */
public final class TrialSpawnerAwarenessSignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/trial_proximity");
    private static final TagKey<Block> TRIAL_FEATURES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("petsplus", "natures/trial_chamber_features"));

    // micro debounce window in ticks to avoid per-tick oscillation when hovering near threshold
    private static final long COOLDOWN_TICKS = 40L;
    private static final int RADIUS = 10; // 8–12 suggested; pick 10 for balance

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        if (ctx == null || ctx.mob() == null) {
            return SignalResult.identity();
        }
        World world = ctx.mob().getEntityWorld();
        if (world == null || world.isClient()) {
            return SignalResult.identity();
        }
        // Cache world key once for stable comparisons if needed by callers
        final var worldKey = (world.getRegistryKey());
        if (worldKey == null) {
            return SignalResult.identity();
        }

        // light, per-goal debounce using goal lastExecuted timestamps if available
        long since = ctx.ticksSince(goal);
        if (since >= 0 && since < COOLDOWN_TICKS) {
            // preserve last multiplier implicitly by being neutral rather than toggling
            return SignalResult.identity();
        }

        BlockPos origin = ctx.currentPos();
        if (origin == null) {
            origin = ctx.mob().getBlockPos();
        }
        if (origin == null) {
            return SignalResult.identity();
        }

        // iterate a small cube and early exit on first tag match
        // keep allocations zero: reuse mutable pos
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int r = RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    // safe world check
                    if (!world.isChunkLoaded(mutable)) {
                        continue;
                    }
                    var state = world.getBlockState(mutable);
                    // state is non-null in vanilla; no extra allocation; proceed
                    if (state.isIn(TRIAL_FEATURES)) {
                        // modest positive exploration desirability; respect stabilizers elsewhere
                        float v = 1.25f; // 0.20–0.35 additive ~ 1.2–1.35 multiplicative; use 1.25
                        return new SignalResult(v, v, Map.of(
                            "matched", "petsplus:natures/trial_chamber_features",
                            "pos", mutable.toShortString()
                        ));
                    }
                }
            }
        }

        return SignalResult.identity();
    }
}