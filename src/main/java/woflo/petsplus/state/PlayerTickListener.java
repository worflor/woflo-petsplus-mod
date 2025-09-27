package woflo.petsplus.state;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Represents a per-player ticking task that can self-schedule its next
 * execution. Listeners expose the next tick they wish to run for an individual
 * player and are invoked by {@link PlayerTickDispatcher} only when that tick is
 * reached.
 */
public interface PlayerTickListener {

    /**
     * Returns the next server tick when this listener would like to run for the
     * supplied player. Implementations should return {@link Long#MAX_VALUE}
     * when there is no pending work for the player so the dispatcher can skip
     * unnecessary invocations.
     */
    long nextRunTick(ServerPlayerEntity player);

    /**
     * Performs the listener's work for the supplied player. Implementations are
     * responsible for updating their scheduling state so that
     * {@link #nextRunTick(ServerPlayerEntity)} returns the appropriate next
     * execution tick.
     */
    void run(ServerPlayerEntity player, long currentTick);

    /**
     * Notifies the listener that the player has been removed (disconnect,
     * dimension change, etc.) so any cached state can be cleared.
     */
    default void onPlayerRemoved(ServerPlayerEntity player) {
    }
}

