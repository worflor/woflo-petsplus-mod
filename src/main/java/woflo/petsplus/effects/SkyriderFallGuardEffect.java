package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.skyrider.SkyriderWinds;
import woflo.petsplus.state.PetComponent;

/**
 * Cancels or reduces fall damage for Skyrider duos, optionally extending protection to mounts.
 */
public class SkyriderFallGuardEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "skyrider_fall_guard");

    private final boolean ownerMode;
    private final double radiusSq;
    private final double maxDamage;
    private final double reductionPct;
    private final double minFallDistance;
    private final int minLevel;
    private final boolean applyToMount;
    private final int mountSlowfallTicks;
    private final int petSlowfallTicks;

    public SkyriderFallGuardEffect(JsonObject json) {
        String mode = RegistryJsonHelper.getString(json, "mode", "owner");
        this.ownerMode = !"pet".equalsIgnoreCase(mode);
        double radius = RegistryJsonHelper.getDouble(json, "radius", 16.0D);
        this.radiusSq = radius <= 0 ? 0 : radius * radius;
        this.maxDamage = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "max_damage", ownerMode ? 4.0D : 2.0D));
        this.reductionPct = Math.max(0.0D, Math.min(1.0D, RegistryJsonHelper.getDouble(json, "reduction_pct", 0.0D)));
        this.minFallDistance = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "min_fall_distance", ownerMode ? SkyriderWinds.getWindlashMinFallBlocks() : 0.0D));
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
        this.applyToMount = ownerMode && RegistryJsonHelper.getBoolean(json, "apply_to_mount", true);
        this.mountSlowfallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "mount_slowfall_ticks", 100));
        this.petSlowfallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "pet_slowfall_ticks", 80));
    }

    public SkyriderFallGuardEffect() {
        this.ownerMode = true;
        this.radiusSq = 16.0D * 16.0D;
        this.maxDamage = 4.0D;
        this.reductionPct = 0.0D;
        this.minFallDistance = SkyriderWinds.getWindlashMinFallBlocks();
        this.minLevel = 1;
        this.applyToMount = true;
        this.mountSlowfallTicks = 100;
        this.petSlowfallTicks = 80;
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

        DamageInterceptionResult result = context.getDamageResult();
        DamageSource source = context.getIncomingDamageSource();
        if (result == null || result.isCancelled() || source == null || !source.isOf(DamageTypes.FALL)) {
            return false;
        }

        double damage = context.getIncomingDamageAmount();
        if (damage <= 0.0D) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.SKYRIDER) || component.getLevel() < minLevel) {
            return false;
        }

        if (ownerMode) {
            return guardOwner(context, pet, result, damage);
        }

        return guardPet(context, pet, result, damage);
    }

    private boolean guardOwner(EffectContext context, MobEntity pet, DamageInterceptionResult result, double damage) {
        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        if (radiusSq > 0.0D && serverOwner.squaredDistanceTo(pet) > radiusSq) {
            return false;
        }

        double fallDistance = Math.max(context.getTriggerContext().getFallDistance(), owner.fallDistance);
        if (fallDistance < minFallDistance) {
            return false;
        }

        boolean modified = false;
        if (damage <= maxDamage) {
            result.cancel();
            modified = true;
        } else if (reductionPct > 0.0D) {
            result.setRemainingDamageAmount(damage * Math.max(0.0D, 1.0D - reductionPct));
            modified = true;
        }

        if (!modified) {
            return false;
        }

        if (applyToMount) {
            applyMountProtection(serverOwner);
        }

        if (petSlowfallTicks > 0 && pet instanceof LivingEntity livingPet) {
            livingPet.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOW_FALLING, petSlowfallTicks, 0, false, true, true));
        }

        return true;
    }

    private boolean guardPet(EffectContext context, MobEntity pet, DamageInterceptionResult result, double damage) {
        double fallDistance = Math.max(context.getTriggerContext().getFallDistance(), pet.fallDistance);
        if (fallDistance < minFallDistance) {
            return false;
        }

        if (damage <= maxDamage) {
            result.cancel();
            return true;
        }

        if (reductionPct > 0.0D) {
            result.setRemainingDamageAmount(damage * Math.max(0.0D, 1.0D - reductionPct));
            return true;
        }

        return false;
    }

    private void applyMountProtection(ServerPlayerEntity owner) {
        Entity mount = owner.getVehicle();
        if (!(mount instanceof LivingEntity livingMount)) {
            return;
        }

        livingMount.fallDistance = 0.0F;
        if (mountSlowfallTicks > 0) {
            livingMount.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOW_FALLING, mountSlowfallTicks, 0, false, true, true));
        }
    }
}
