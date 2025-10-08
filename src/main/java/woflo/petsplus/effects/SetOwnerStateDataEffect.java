package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Sets temporary state data on the owner for timing windows and synergy tracking.
 */
public final class SetOwnerStateDataEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "set_owner_state_data");

    private final String key;
    private final int durationTicks;

    public SetOwnerStateDataEffect(JsonObject json) {
        this.key = RegistryJsonHelper.getString(json, "key", "");
        this.durationTicks = RegistryJsonHelper.getInt(json, "duration_ticks", 20);
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

        ServerWorld world = context.getEntityWorld();
        if (world == null) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(serverOwner);
        
        // Store expiry time in tempState
        if (durationTicks > 0) {
            long expiryTick = world.getTime() + durationTicks;
            combatState.setTempState(key, expiryTick);
        }

        return true;
    }
}


