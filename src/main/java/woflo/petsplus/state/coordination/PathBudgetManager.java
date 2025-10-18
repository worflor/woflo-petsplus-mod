package woflo.petsplus.state.coordination;

import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-world, per-owner path start token bucket.
 * Limits the number of fresh path computations started in a single tick,
 * distributing budget fairly across a large pack.
 */
public final class PathBudgetManager {
    private static final Map<ServerWorld, PathBudgetManager> INSTANCES = new WeakHashMap<>();

    public static synchronized PathBudgetManager get(ServerWorld world) {
        return INSTANCES.computeIfAbsent(world, w -> new PathBudgetManager());
    }

    private final Map<UUID, OwnerBucket> buckets = new ConcurrentHashMap<>();

    private PathBudgetManager() {}

    /**
     * Attempt to consume one path token for the given owner. Resets the bucket
     * when {@code tick} advances.
     *
     * @param ownerId owner UUID
     * @param tick current world tick
     * @param perTickLimit maximum new path starts allowed for this owner this tick
     * @return true if a token was consumed and caller may start a new path
     */
    public boolean tryConsume(UUID ownerId, long tick, int perTickLimit) {
        if (ownerId == null || tick == Long.MIN_VALUE || perTickLimit <= 0) {
            return false;
        }
        OwnerBucket bucket = buckets.computeIfAbsent(ownerId, id -> new OwnerBucket());
        if (bucket.tick != tick) {
            bucket.tick = tick;
            bucket.remaining = perTickLimit;
        }
        if (bucket.remaining <= 0) {
            return false;
        }
        bucket.remaining--;
        return true;
    }

    private static final class OwnerBucket {
        long tick = Long.MIN_VALUE;
        int remaining = 0;
    }
}

