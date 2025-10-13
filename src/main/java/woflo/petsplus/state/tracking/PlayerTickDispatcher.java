package woflo.petsplus.state.tracking;

import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.tracking.PlayerTickListener;

/**
 * Dispatcher shell for the future owner-centric ticking pipeline.
 *
 * Design intent:
 * - Enforce per-owner O(1) fairness across active owners
 * - Apply LOD gating based on documented policy cadences and distances
 * - Keep this layer free of direct Minecraft type references; wiring occurs later
 *
 * This is purely scaffolding for Phase A (Chunk 1). It intentionally has
 * no runtime behavior and is not referenced from existing loops/mixins yet.
 *
 * @since Phase A - Chunk 1
 */
public final class PlayerTickDispatcher {

    private PlayerTickDispatcher() {
        // Prevent instantiation
    }

    /**
     * Entry point for dispatching the owner-centric tick loop.
     * No-op placeholder; implementation arrives in later chunks.
     */
    public static void dispatchOwnerLoop() {
        // no-op placeholder
    }

    // ---------------------------------------------------------------------
    // Phase A compile-pass static API stubs (no behavior)
    // ---------------------------------------------------------------------
    public static void register(PlayerTickListener listener) { }

    public static void dispatch(ServerPlayerEntity player, long currentTick) { }

    public static void requestImmediateRun(ServerPlayerEntity player, PlayerTickListener listener) { }

    public static void clearPlayer(ServerPlayerEntity player) { }

    public static void clearAll() { }
}
