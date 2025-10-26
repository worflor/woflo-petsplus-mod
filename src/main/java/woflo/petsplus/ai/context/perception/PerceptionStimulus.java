package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Event published onto the {@link PerceptionBus} describing a change in the
 * world, owner, or component state. Stimuli carry the logical context slices
 * they invalidate so caches can refresh lazily.
 */
public record PerceptionStimulus(
    Identifier type,
    long tick,
    ContextSliceMask slices,
    @Nullable Object payload
) {

    public PerceptionStimulus {
        Objects.requireNonNull(type, "type");
        if (slices == null || slices.isEmpty()) {
            slices = ContextSliceMask.ALL;
        }
    }

    public static PerceptionStimulus of(Identifier type, long tick, ContextSlice slice, @Nullable Object payload) {
        return new PerceptionStimulus(type, tick, ContextSliceMask.of(slice), payload);
    }
}
