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
    private static final String SHIFT_INTERACT_EVENT = "owner_signal_shift_interact";
    private static final String CROUCH_APPROACH_EVENT = "owner_signal_crouch_approach";

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
        
        // Mark signal in PetComponent for goal detection
        if (type == OwnerAbilitySignalEvent.Type.CROUCH_APPROACH) {
            woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(pet);
            if (pc != null) {
                pc.setStateData("crouch_approach_signal_tick", world.getTime());
            }
        }

        TriggerContext context = new TriggerContext(world, pet, owner, eventType);
        AbilityManager.triggerAbilities(pet, context);
    }

    private static String mapEventType(OwnerAbilitySignalEvent.Type type) {
        return switch (type) {
            case DOUBLE_CROUCH -> DOUBLE_CROUCH_EVENT;
            case PROXIMITY_CHANNEL -> PROXIMITY_EVENT;
            case SHIFT_INTERACT -> SHIFT_INTERACT_EVENT;
            case CROUCH_APPROACH -> CROUCH_APPROACH_EVENT;
        };
    }
}
