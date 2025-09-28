package woflo.petsplus.abilities;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import woflo.petsplus.Petsplus;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.AsyncProcessingSettings;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventListener;
import woflo.petsplus.state.processing.OwnerEventType;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.jetbrains.annotations.Nullable;

/**
 * Bridges owner-scoped ability trigger events into per-pet ability execution.
 */
public final class OwnerAbilityEventBridge implements OwnerEventListener {
    public static final OwnerAbilityEventBridge INSTANCE = new OwnerAbilityEventBridge();

    @FunctionalInterface
    interface AbilityExecutor {
        void trigger(ServerWorld world,
                     @Nullable ServerPlayerEntity owner,
                     List<PetComponent> pets,
                     String triggerId,
                     Map<String, Object> eventData);
    }

    private static volatile AbilityExecutor abilityExecutor = AbilityManager::triggerAbilitiesForOwnerEvent;

    private OwnerAbilityEventBridge() {
    }

    static void setAbilityExecutorForTesting(@Nullable AbilityExecutor executor) {
        abilityExecutor = executor == null ? AbilityManager::triggerAbilitiesForOwnerEvent : executor;
    }

    @Override
    public void onOwnerEvent(OwnerEventFrame frame) {
        if (frame.eventType() != OwnerEventType.ABILITY_TRIGGER) {
            return;
        }
        AbilityTriggerPayload payload = frame.payload(AbilityTriggerPayload.class);
        if (payload == null) {
            return;
        }
        String triggerId = payload.eventType();
        if (triggerId.isEmpty()) {
            return;
        }
        StateManager manager = StateManager.forWorld(frame.world());
        OwnerBatchSnapshot snapshot = frame.snapshot();
        boolean asyncEnabled = AsyncProcessingSettings.asyncAbilitiesEnabled();
        boolean shadowEnabled = AsyncProcessingSettings.asyncAbilitiesShadowEnabled();

        if (asyncEnabled && snapshot != null && snapshot.ownerId() != null) {
            dispatchAsync(manager, frame, snapshot, payload, triggerId);
            return;
        }

        dispatchSynchronously(frame, payload, triggerId);

        if (shadowEnabled && snapshot != null && snapshot.ownerId() != null) {
            dispatchShadow(manager, snapshot, payload);
        }
    }

    private void dispatchAsync(StateManager manager,
                               OwnerEventFrame frame,
                               OwnerBatchSnapshot snapshot,
                               AbilityTriggerPayload payload,
                               String triggerId) {
        AsyncWorkCoordinator coordinator = manager.asyncWorkCoordinator();
        FallbackContext fallback = FallbackContext.capture(frame, payload, triggerId);
        CompletableFuture<AbilityManager.AbilityExecutionPlan> future = coordinator.submitOwnerBatch(
            snapshot,
            snap -> AbilityManager.prepareOwnerExecutionPlan(snap, payload),
            plan -> AbilityManager.applyOwnerExecutionPlan(plan, manager, payload, snapshot.ownerId())
        );
        future.exceptionally(error -> {
            Throwable cause = unwrap(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Async ability execution rejected for {}, falling back to synchronous", triggerId);
                dispatchSynchronously(fallback);
            } else {
                Petsplus.LOGGER.error("Async ability execution failed for {}", triggerId, cause);
            }
            return null;
        });
    }

    private void dispatchShadow(StateManager manager,
                                OwnerBatchSnapshot snapshot,
                                AbilityTriggerPayload payload) {
        AsyncWorkCoordinator coordinator = manager.asyncWorkCoordinator();
        coordinator.submitOwnerBatch(
            snapshot,
            snap -> AbilityManager.prepareOwnerExecutionPlan(snap, payload),
            plan -> {
                if (!plan.isEmpty()) {
                    Petsplus.LOGGER.trace("Shadow async ability plan prepared for owner {} with {} entries", snapshot.ownerId(), plan.executions().size());
                }
            }
        ).exceptionally(error -> {
            Throwable cause = unwrap(error);
            if (!(cause instanceof RejectedExecutionException)) {
                Petsplus.LOGGER.debug("Shadow async ability computation failed", cause);
            }
            return null;
        });
    }

    private void dispatchSynchronously(OwnerEventFrame frame,
                                       AbilityTriggerPayload payload,
                                       String triggerId) {
        dispatchSynchronously(FallbackContext.capture(frame, payload, triggerId));
    }

    private void dispatchSynchronously(FallbackContext context) {
        try {
            abilityExecutor.trigger(
                context.world(),
                context.owner(),
                context.pets(),
                context.triggerId(),
                context.eventData()
            );
        } catch (Exception ex) {
            Petsplus.LOGGER.error("Failed to trigger owner ability batch for {}", context.triggerId(), ex);
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }

    private record FallbackContext(ServerWorld world,
                                   @Nullable ServerPlayerEntity owner,
                                   List<PetComponent> pets,
                                   String triggerId,
                                   Map<String, Object> eventData) {
        private static FallbackContext capture(OwnerEventFrame frame,
                                               AbilityTriggerPayload payload,
                                               String triggerId) {
            List<PetComponent> framePets = frame.pets();
            List<PetComponent> copiedPets = framePets.isEmpty() ? List.of() : List.copyOf(framePets);
            Map<String, Object> data = payload.eventData().isEmpty() ? Map.of() : payload.eventData();
            return new FallbackContext(frame.world(), frame.owner(), copiedPets, triggerId, data);
        }
    }
}
