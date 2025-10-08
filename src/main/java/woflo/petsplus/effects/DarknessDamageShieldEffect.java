package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Cancels small amounts of damage while the Eclipsed pet and/or owner are shrouded in darkness.
 */
public class DarknessDamageShieldEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "darkness_damage_shield");

    private final boolean ownerContext;
    private final double maxDamage;
    private final double radiusSq;
    private final boolean requireOwnerDarkness;
    private final boolean requirePetDarkness;
    private final int minimumLevel;

    public DarknessDamageShieldEffect(JsonObject json) {
        String mode = RegistryJsonHelper.getString(json, "mode", "pet");
        this.ownerContext = "owner".equalsIgnoreCase(mode);
        this.maxDamage = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "max_damage", ownerContext ? 3.0D : 2.0D));
        double radius = RegistryJsonHelper.getDouble(json, "radius", 16.0D);
        this.radiusSq = radius <= 0.0D ? 0.0D : radius * radius;
        this.requireOwnerDarkness = RegistryJsonHelper.getBoolean(json, "require_owner_darkness", ownerContext);
        this.requirePetDarkness = RegistryJsonHelper.getBoolean(json, "require_pet_darkness", true);
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
    }

    public DarknessDamageShieldEffect() {
        this.ownerContext = false;
        this.maxDamage = 2.0D;
        this.radiusSq = 0.0D;
        this.requireOwnerDarkness = false;
        this.requirePetDarkness = true;
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
        double amount = context.getIncomingDamageAmount();
        if (amount <= 0.0D || amount > maxDamage) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null) {
            return false;
        }
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ECLIPSED) || component.getLevel() < minimumLevel) {
            return false;
        }
        if (!(context.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }
        if (requirePetDarkness && !isInDarkness(pet)) {
            return false;
        }

        if (ownerContext) {
            PlayerEntity owner = context.getOwner();
            if (!(owner instanceof ServerPlayerEntity serverOwner)) {
                return false;
            }
            if (radiusSq > 0.0D && serverOwner.squaredDistanceTo(pet) > radiusSq) {
                return false;
            }
            if (requireOwnerDarkness && !isInDarkness(serverOwner)) {
                return false;
            }
        }

        interception.cancel();
        return true;
    }

    private static boolean isInDarkness(LivingEntity entity) {
        return entity.getEntityWorld().getLightLevel(entity.getBlockPos()) <= 7;
    }
}


