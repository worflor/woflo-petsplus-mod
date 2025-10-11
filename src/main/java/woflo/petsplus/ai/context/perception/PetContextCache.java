package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.PetContext;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Maintains a cached {@link PetContext} that is invalidated by perception
 * stimuli. The cache refreshes lazily and also enforces a maximum idle age so
 * time-dependent fields stay fresh without rebuilding every tick.
 */
public final class PetContextCache implements PerceptionListener {
    private static final long DEFAULT_MAX_IDLE_TICKS = 20L; // one second

    private final EnumSet<ContextSlice> dirtySlices = EnumSet.noneOf(ContextSlice.class);
    private long lastCaptureTick = Long.MIN_VALUE;
    private long maxIdleTicks = DEFAULT_MAX_IDLE_TICKS;
    @Nullable
    private PetContext cached;

    public void attachTo(PerceptionBus bus) {
        Objects.requireNonNull(bus, "bus").subscribeAll(this);
    }

    public void markDirty(ContextSlice slice) {
        if (slice == ContextSlice.ALL) {
            dirtySlices.clear();
            dirtySlices.add(ContextSlice.ALL);
            return;
        }
        dirtySlices.add(slice);
    }

    public void markDirty(EnumSet<ContextSlice> slices) {
        if (slices.contains(ContextSlice.ALL)) {
            dirtySlices.clear();
            dirtySlices.add(ContextSlice.ALL);
            return;
        }
        dirtySlices.addAll(slices);
    }

    public void markAllDirty() {
        dirtySlices.clear();
        dirtySlices.add(ContextSlice.ALL);
    }

    public void setMaxIdleTicks(long maxIdleTicks) {
        this.maxIdleTicks = Math.max(1L, maxIdleTicks);
    }

    public PetContext snapshot(MobEntity mob, Supplier<PetContext> capture) {
        World world = mob.getEntityWorld();
        long worldTime = world != null ? world.getTime() : 0L;
        if (shouldRefresh(worldTime)) {
            cached = capture.get();
            dirtySlices.clear();
            lastCaptureTick = worldTime;
        }
        return cached;
    }

    private boolean shouldRefresh(long worldTime) {
        if (cached == null) {
            return true;
        }
        if (!dirtySlices.isEmpty()) {
            return true;
        }
        if (lastCaptureTick == Long.MIN_VALUE) {
            return true;
        }
        return (worldTime - lastCaptureTick) >= maxIdleTicks;
    }

    @Override
    public void onStimulus(PerceptionStimulus stimulus) {
        markDirty(stimulus.slices());
    }
}
