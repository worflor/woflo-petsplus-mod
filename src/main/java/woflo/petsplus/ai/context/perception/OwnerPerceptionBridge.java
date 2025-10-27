package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.OwnerFocusSnapshot;
import woflo.petsplus.state.processing.OwnerMovementPayload;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventListener;
import woflo.petsplus.state.processing.OwnerEventType;

import java.util.List;

/**
 * Bridges owner-scoped events emitted by the owner processing pipeline onto the
 * perception bus used by adaptive AI systems. The bridge marks the relevant
 * context slices dirty so cached {@link woflo.petsplus.ai.context.PetContext}
 * snapshots refresh lazily on demand instead of every tick.
 */
public final class OwnerPerceptionBridge implements OwnerEventListener {

    @Override
    public void onOwnerEvent(OwnerEventFrame frame) {
        OwnerEventType type = frame.eventType();
        if (type == OwnerEventType.MOVEMENT) {
            emitOwnerStimulus(frame);
            emitOwnerActivityStimulus(frame);
        }
        if (type.requiresSwarmSnapshot()) {
            emitCrowdStimulus(frame);
        }
    }

    private void emitOwnerStimulus(OwnerEventFrame frame) {
        emit(frame, PerceptionStimulusType.OWNER_NEARBY, ContextSlice.OWNER, frame.owner());
    }

    private void emitOwnerActivityStimulus(OwnerEventFrame frame) {
        Object payload = frame.payload();
        OwnerFocusSnapshot focus = null;
        if (payload instanceof OwnerMovementPayload movementPayload) {
            focus = movementPayload.focusSnapshot();
        } else if (payload instanceof OwnerFocusSnapshot focusSnapshot) {
            focus = focusSnapshot;
        }
        if (focus == null) {
            focus = OwnerFocusSnapshot.idle();
        }
        emit(frame, PerceptionStimulusType.OWNER_ACTIVITY, ContextSlice.OWNER, focus);
    }

    private void emitCrowdStimulus(OwnerEventFrame frame) {
        emit(frame, PerceptionStimulusType.CROWD_SUMMARY, ContextSlice.CROWD, frame.swarmSnapshot());
    }

    private void emit(OwnerEventFrame frame,
                      Identifier stimulusType,
                      ContextSlice slice,
                      @Nullable Object payload) {
        List<PetComponent> pets = frame.pets();
        if (pets == null || pets.isEmpty()) {
            return;
        }

        long tick = frame.currentTick();
        ContextSliceMask mask = ContextSliceMask.of(slice);
        for (PetComponent component : pets) {
            if (component == null) {
                continue;
            }
            component.markContextDirty(slice);
            PerceptionBus bus = component.getPerceptionBus();
            if (bus != null) {
                bus.publish(new PerceptionStimulus(stimulusType, tick, mask, payload));
            }
        }
    }
}
