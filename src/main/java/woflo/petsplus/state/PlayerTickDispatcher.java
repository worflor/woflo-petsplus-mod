package woflo.petsplus.state;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;

/**
 * Central dispatcher that coordinates per-player tick listeners. Listeners are
 * invoked only when their scheduled tick is due, allowing subsystems to
 * self-schedule rather than polling every server tick.
 */
public final class PlayerTickDispatcher {

    private static final Set<PlayerTickListener> LISTENERS = new LinkedHashSet<>();
    private static final Map<UUID, Map<PlayerTickListener, Long>> OVERRIDES = new ConcurrentHashMap<>();

    private PlayerTickDispatcher() {}

    public static void register(PlayerTickListener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.add(listener);
    }

    public static void dispatch(ServerPlayerEntity player, long currentTick) {
        if (player == null) {
            return;
        }

        Map<PlayerTickListener, Long> overrides = OVERRIDES.get(player.getUuid());
        for (PlayerTickListener listener : LISTENERS) {
            long scheduledTick;
            try {
                scheduledTick = listener.nextRunTick(player);
            } catch (RuntimeException e) {
                Petsplus.LOGGER.error(
                    "Player tick listener {} failed to provide next run tick for {}",
                    listener.getClass().getName(),
                    safePlayerName(player),
                    e
                );
                continue;
            }

            Long overrideTick = null;
            if (overrides != null) {
                overrideTick = overrides.get(listener);
                if (overrideTick != null) {
                    scheduledTick = Math.min(scheduledTick, overrideTick);
                }
            }

            if (scheduledTick <= currentTick) {
                if (overrides != null && overrideTick != null && overrideTick <= currentTick) {
                    overrides.remove(listener, overrideTick);
                }
                try {
                    listener.run(player, currentTick);
                } catch (RuntimeException e) {
                    Petsplus.LOGGER.error(
                        "Player tick listener {} threw while running for {}",
                        listener.getClass().getName(),
                        safePlayerName(player),
                        e
                    );
                }
            }
        }

        if (overrides != null && overrides.isEmpty()) {
            OVERRIDES.remove(player.getUuid(), overrides);
        }
    }

    public static void requestImmediateRun(ServerPlayerEntity player, PlayerTickListener listener) {
        if (player == null || listener == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        OVERRIDES.computeIfAbsent(player.getUuid(), id -> new ConcurrentHashMap<>())
            .put(listener, (long) server.getTicks());
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        OVERRIDES.remove(player.getUuid());
        for (PlayerTickListener listener : LISTENERS) {
            listener.onPlayerRemoved(player);
        }
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }

    public static Set<PlayerTickListener> listeners() {
        return Set.copyOf(LISTENERS);
    }

    private static String safePlayerName(ServerPlayerEntity player) {
        if (player == null) {
            return "<unknown>";
        }
        return player.getGameProfile() != null
            ? player.getGameProfile().getName()
            : player.getName().getString();
    }
}

