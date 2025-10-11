package woflo.petsplus.ai.context.perception;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.Collection;

/**
 * Emits environment and world-time stimuli when coarse-grained sky state
 * changes occur. The bridge keeps track of weather and time-of-day buckets so
 * cached {@link woflo.petsplus.ai.context.PetContext} snapshots only refresh
 * when relevant world conditions change instead of every tick.
 */
public final class EnvironmentPerceptionBridge {
    private static final long TIME_SEGMENT_TICKS = 1000L; // ~50 seconds

    private boolean initialised;
    private boolean lastDaytime;
    private boolean lastRaining;
    private boolean lastThundering;
    private long lastTimeSegment = Long.MIN_VALUE;

    /**
     * Processes a world tick for the given world, publishing stimuli to each
     * tracked component when weather or the coarse time segment changes.
     */
    public void onWorldTick(ServerWorld world, Collection<PetComponent> components) {
        if (world == null || components == null || components.isEmpty()) {
            return;
        }

        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        boolean daytime = world.isDay();
        long timeOfDay = world.getTimeOfDay();
        long segment = timeOfDay / TIME_SEGMENT_TICKS;

        boolean environmentChanged = !initialised || lastRaining != raining || lastThundering != thundering;
        boolean worldChanged = !initialised || lastDaytime != daytime || lastTimeSegment != segment;

        if (!environmentChanged && !worldChanged) {
            // Nothing relevant changed – keep cached state intact.
            return;
        }

        EnvironmentSnapshot environmentSnapshot = environmentChanged
            ? new EnvironmentSnapshot(raining, thundering, daytime, world.getRegistryKey().getValue().toString())
            : null;

        WorldSnapshot worldSnapshot = worldChanged
            ? new WorldSnapshot(world.getTime(), timeOfDay, daytime, segment)
            : null;

        for (PetComponent component : components) {
            if (component == null) {
                continue;
            }
            if (environmentSnapshot != null) {
                component.notifyEnvironmentUpdated(environmentSnapshot);
            }
            if (worldSnapshot != null) {
                component.notifyWorldTimeUpdated(worldSnapshot);
            }
        }

        initialised = true;
        lastRaining = raining;
        lastThundering = thundering;
        lastDaytime = daytime;
        lastTimeSegment = segment;
    }

    /**
     * Lightweight snapshot describing sky conditions.
     */
    public record EnvironmentSnapshot(boolean raining,
                                      boolean thundering,
                                      boolean daytime,
                                      @Nullable String dimensionId) {
    }

    /**
     * Snapshot describing coarse world-time state.
     */
    public record WorldSnapshot(long worldTime,
                                long timeOfDay,
                                boolean daytime,
                                long segment) {
    }
}
