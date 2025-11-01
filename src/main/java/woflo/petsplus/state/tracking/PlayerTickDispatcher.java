package woflo.petsplus.state.tracking;

import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import woflo.petsplus.Petsplus;

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

    private static final CopyOnWriteArrayList<PlayerTickListener> LISTENERS = new CopyOnWriteArrayList<>();

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
    public static void register(PlayerTickListener listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void dispatch(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.isRemoved()) {
            return;
        }

        for (PlayerTickListener listener : LISTENERS) {
            if (listener == null) {
                continue;
            }

            try {
                if (listener.nextRunTick(player) <= currentTick) {
                    listener.run(player, currentTick);
                }
            } catch (Exception ex) {
                Petsplus.LOGGER.error(
                    "Player tick listener {} failed for player {}",
                    listener.getClass().getName(),
                    player.getName().getString(),
                    ex
                );
            }
        }
    }

    public static void requestImmediateRun(ServerPlayerEntity player, PlayerTickListener listener) {
        if (player == null || listener == null || player.isRemoved()) {
            return;
        }

        if (player.getEntityWorld() == null) {
            return;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return;
        }

        try {
            server.execute(() -> {
                if (player.isRemoved()) {
                    return;
                }

                long currentTick = server.getTicks();
                try {
                    if (listener.nextRunTick(player) <= currentTick) {
                        listener.run(player, currentTick);
                    } else {
                        dispatch(player, currentTick);
                    }
                } catch (Exception ex) {
                    Petsplus.LOGGER.error(
                        "Player tick listener {} failed for player {}",
                        listener.getClass().getName(),
                        player.getName().getString(),
                        ex
                    );
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            Petsplus.LOGGER.warn(
                "Server rejected execution for player tick listener {} for player {}",
                listener.getClass().getName(),
                player.getName().getString(),
                ex
            );
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        for (PlayerTickListener listener : LISTENERS) {
            if (listener != null) {
                listener.onPlayerRemoved(player);
            }
        }
    }

    public static void clearAll() {
        LISTENERS.clear();
    }
}
