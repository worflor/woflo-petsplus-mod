package woflo.petsplus.ai.context.perception;

import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Emits crowd stimuli whenever the swarm index observes movement updates.
 */
public final class SwarmPerceptionBridge implements PetSwarmIndex.SwarmListener {

    @Override
    public void onSwarmUpdated(@Nullable java.util.UUID ownerId, List<PetSwarmIndex.SwarmEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<PetComponent> unique = new HashSet<>();
        for (PetSwarmIndex.SwarmEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            PetComponent component = entry.component();
            if (component == null || !unique.add(component)) {
                continue;
            }
            component.markContextDirty(ContextSlice.CROWD);
            PerceptionBus bus = component.getPerceptionBus();
            if (bus == null) {
                continue;
            }
            long tick = component.getPetEntity().getEntityWorld().getTime();
            bus.publish(new PerceptionStimulus(PerceptionStimulusType.CROWD_SUMMARY, tick, ContextSliceMask.of(ContextSlice.CROWD), entries));
        }
    }
}
