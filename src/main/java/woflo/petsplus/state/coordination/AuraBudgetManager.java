package woflo.petsplus.state.coordination;

import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-owner, per-tick token bucket for aura target applications.
 * Prevents a single owner batch from applying effects to too many entities in one tick.
 */
public final class AuraBudgetManager {
    private static final Map<ServerWorld, AuraBudgetManager> INSTANCES = new WeakHashMap<>();

    public static synchronized AuraBudgetManager get(ServerWorld world) {
        return INSTANCES.computeIfAbsent(world, w -> new AuraBudgetManager());
    }

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    private AuraBudgetManager() {}

    /**
     * Attempt to consume a single aura target token.
     * Resets the bucket when the world tick advances.
     *
     * @param ownerId owner UUID
     * @param tick current world tick
     * @param perTickLimit tokens allowed per tick for this owner
     * @return true if a token was consumed
     */
    public boolean tryConsume(UUID ownerId, long tick, int perTickLimit) {
        if (ownerId == null || perTickLimit <= 0) return false;
        Bucket b = buckets.computeIfAbsent(ownerId, id -> new Bucket());
        if (b.tick != tick) {
            b.tick = tick;
            b.remaining = perTickLimit;
        }
        if (b.remaining <= 0) return false;
        b.remaining--;
        return true;
    }

    private static final class Bucket {
        long tick = Long.MIN_VALUE;
        int remaining = 0;
    }
}

