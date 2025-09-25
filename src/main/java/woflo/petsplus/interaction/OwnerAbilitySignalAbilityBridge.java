package woflo.petsplus.interaction;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.event.OwnerAbilitySignalEvent;

/**
 * Bridges manual owner ability signals into the shared ability trigger pipeline.
 */
public final class OwnerAbilitySignalAbilityBridge {
    private static final String DOUBLE_CROUCH_EVENT = "owner_signal_double_crouch";
    private static final String PROXIMITY_EVENT = "owner_signal_proximity_channel";

    private OwnerAbilitySignalAbilityBridge() {
    }

    /** Registers the bridge listener. */
    public static void register() {
        OwnerAbilitySignalEvent.EVENT.register(OwnerAbilitySignalAbilityBridge::handleSignal);
    }

    private static void handleSignal(OwnerAbilitySignalEvent.Type type, ServerPlayerEntity owner, MobEntity pet) {
        if (owner == null || pet == null) {
            return;
        }

        ServerWorld world = (ServerWorld) owner.getWorld();
        String eventType = mapEventType(type);
        if (eventType == null) {
            return;
        }

        TriggerContext context = new TriggerContext(world, pet, owner, eventType);
        AbilityManager.triggerAbilities(pet, context);
    }

    private static String mapEventType(OwnerAbilitySignalEvent.Type type) {
        return switch (type) {
            case DOUBLE_CROUCH -> DOUBLE_CROUCH_EVENT;
            case PROXIMITY_CHANNEL -> PROXIMITY_EVENT;
        };
    }
}
