package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Fired when an owner successfully performs a manual trigger action targeting their pet.
 */
public final class OwnerAbilitySignalEvent {
    /** Global event hook for owner ability trigger signals. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> (type, owner, pet) -> {
            for (Listener listener : listeners) {
                listener.onSignal(type, owner, pet);
            }
        }
    );

    private OwnerAbilitySignalEvent() {
    }

    /**
     * Dispatches the trigger event.
     */
    public static void fire(Type type, ServerPlayerEntity owner, MobEntity pet) {
        EVENT.invoker().onSignal(type, owner, pet);
    }

    /** Callback for listener registrations. */
    @FunctionalInterface
    public interface Listener {
        /**
         * Invoked when the owner completes the specified trigger targeting the supplied pet.
         */
        void onSignal(Type type, ServerPlayerEntity owner, MobEntity pet);
    }

    /** Supported manual trigger types. */
    public enum Type {
        DOUBLE_CROUCH,
        PROXIMITY_CHANNEL
    }
}
