package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Clears state data from the owner (used to consume timing windows).
 */
public final class ClearOwnerStateDataEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "clear_owner_state_data");

    private final String key;

    public ClearOwnerStateDataEffect(JsonObject json) {
        this.key = RegistryJsonHelper.getString(json, "key", "");
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (key.isEmpty()) {
            return false;
        }

        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(serverOwner);
        combatState.clearTempState(key);

        return true;
    }
}
