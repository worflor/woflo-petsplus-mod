package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Cancels small bursts of magical damage for Enchantment-Bound pets.
 */
public class MagicDamageShieldEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "magic_damage_shield");

    private final double maxDamage;
    private final int minimumLevel;

    public MagicDamageShieldEffect(JsonObject json) {
        this.maxDamage = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "max_damage", 1.0D));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
    }

    public MagicDamageShieldEffect() {
        this.maxDamage = 1.0D;
        this.minimumLevel = 1;
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
        DamageInterceptionResult interception = context.getDamageResult();
        if (interception == null || interception.isCancelled()) {
            return false;
        }
        MobEntity pet = context.getPet();
        if (pet == null) {
            return false;
        }
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ENCHANTMENT_BOUND) || component.getLevel() < minimumLevel) {
            return false;
        }
        if (!(context.getWorld() instanceof ServerWorld)) {
            return false;
        }

        DamageSource damageSource = context.getIncomingDamageSource();
        if (damageSource == null || !isMagicalDamage(damageSource)) {
            return false;
        }
        double amount = context.getIncomingDamageAmount();
        if (amount <= 0.0D || amount > maxDamage) {
            return false;
        }

        interception.cancel();
        return true;
    }

    private static boolean isMagicalDamage(DamageSource source) {
        return source.isOf(DamageTypes.MAGIC)
            || source.isOf(DamageTypes.INDIRECT_MAGIC)
            || source.isOf(DamageTypes.WITHER)
            || source.isOf(DamageTypes.DRAGON_BREATH);
    }
}
