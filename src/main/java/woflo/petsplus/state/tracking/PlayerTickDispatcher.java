package woflo.petsplus.state.tracking;

import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.tracking.PlayerTickListener;

/**
 * Dispatcher shell for the future owner-centric ticking pipeline.
 *
 * Design intent:
 * - Enforce per-owner O(1) fairness across active owners
 * - Apply LOD gating based on documented policy cadences and distances
 * - Keep this layer free of direct Minecraft type references
 *
 * This is scaffolding and intentionally has no runtime behavior yet.
 */

public final class PlayerTickDispatcher {

    private PlayerTickDispatcher() {
        // Prevent instantiation
    }

    /**
     * Entry point for dispatching the owner-centric tick loop.
     * No-op placeholder; implementation pending.
     */
    public static void dispatchOwnerLoop() {
        // no-op placeholder
    }

    // ---------------------------------------------------------------------
    // Placeholder static API stubs (no behavior)
    // ---------------------------------------------------------------------
    public static void register(PlayerTickListener listener) { }

    public static void dispatch(ServerPlayerEntity player, long currentTick) { }

    public static void requestImmediateRun(ServerPlayerEntity player, PlayerTickListener listener) { }

    public static void clearPlayer(ServerPlayerEntity player) { }

    public static void clearAll() { }
}
