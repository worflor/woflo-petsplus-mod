package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundEchoes;
import woflo.petsplus.state.PetComponent;

/**
 * Applies the Enchantment-Bound mining haste pulse when the owner breaks blocks.
 */
public class EnchantmentBoundMiningHasteEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "enchantment_mining_haste");

    private final int baseDurationTicks;
    private final int minimumLevel;

    public EnchantmentBoundMiningHasteEffect(JsonObject json) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier roleId = PetRoleType.ENCHANTMENT_BOUND.id();
        this.baseDurationTicks = Math.max(1, RegistryJsonHelper.getInt(json, "base_duration_ticks",
            config.getRoleInt(roleId, "miningHasteBaseTicks", 40)));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
    }

    public EnchantmentBoundMiningHasteEffect() {
        this.baseDurationTicks = 40;
        this.minimumLevel = 1;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }
        MobEntity pet = context.getPet();
        if (pet == null || !(context.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ENCHANTMENT_BOUND) || component.getLevel() < minimumLevel) {
            return false;
        }

        EnchantmentBoundEchoes.applyEnhancedHaste(owner, baseDurationTicks);
        return true;
    }
}


