package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.guardian.GuardianFortressBondManager;

/**
 * Reduces incoming damage for guardians linked by the fortress bond when the
 * pet is about to take damage.
 */
public final class GuardianFortressBondPetDrEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "guardian_fortress_bond_pet_dr");

    public GuardianFortressBondPetDrEffect() {
    }

    @SuppressWarnings("unused")
    public GuardianFortressBondPetDrEffect(JsonObject json) {
        // Reserved for future configuration.
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!context.hasDamageContext()) {
            return false;
        }

        if (!"pet_incoming_damage".equals(context.getTriggerContext().getEventType())) {
            return false;
        }

        DamageInterceptionResult result = context.getDamageResult();
        if (result == null || result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null) {
            return false;
        }

        ServerPlayerEntity owner = context.getOwner() instanceof ServerPlayerEntity serverOwner ? serverOwner : null;
        if (owner == null) {
            return false;
        }

        return GuardianFortressBondManager.applyPetDamageReduction(owner, pet, result);
    }
}
