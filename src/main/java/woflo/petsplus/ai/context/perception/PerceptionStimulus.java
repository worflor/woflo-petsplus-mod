package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Event published onto the {@link PerceptionBus} describing a change in the
 * world, owner, or component state. Stimuli carry the logical context slices
 * they invalidate so caches can refresh lazily.
 */
public record PerceptionStimulus(
    Identifier type,
    long tick,
    EnumSet<ContextSlice> slices,
    @Nullable Object payload
) {

    public PerceptionStimulus {
        Objects.requireNonNull(type, "type");
        if (slices == null || slices.isEmpty()) {
            slices = EnumSet.of(ContextSlice.ALL);
        } else {
            slices = EnumSet.copyOf(slices);
        }
    }

    public static PerceptionStimulus of(Identifier type, long tick, ContextSlice slice, @Nullable Object payload) {
        return new PerceptionStimulus(type, tick, EnumSet.of(slice), payload);
    }
}
