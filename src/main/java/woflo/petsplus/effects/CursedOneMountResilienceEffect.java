package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.roles.cursedone.CursedOneMountBehaviors;
import woflo.petsplus.state.PetComponent;

/**
 * Applies the Cursed One mount resilience buff when the owner respawns while mounted.
 */
public class CursedOneMountResilienceEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "cursed_mount_resilience");

    private final double radiusSq;
    private final int durationTicks;
    private final int amplifier;
    private final boolean requireDeathRespawn;

    public CursedOneMountResilienceEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "search_radius", 16.0D);
        this.radiusSq = Math.max(0.0D, radius) * Math.max(0.0D, radius);
        this.durationTicks = Math.max(0, RegistryJsonHelper.getInt(json, "duration_ticks", 60));
        this.amplifier = Math.max(0, RegistryJsonHelper.getInt(json, "amplifier", 0));
        this.requireDeathRespawn = RegistryJsonHelper.getBoolean(json, "require_death_respawn", true);
    }

    public CursedOneMountResilienceEffect() {
        this.radiusSq = 16.0D * 16.0D;
        this.durationTicks = 60;
        this.amplifier = 0;
        this.requireDeathRespawn = true;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!"owner_respawn".equals(context.getTriggerContext().getEventType())) {
            return false;
        }
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }
        Boolean deathRespawn = context.getTriggerContext().getData("death_respawn", Boolean.class);
        if (requireDeathRespawn && (deathRespawn == null || !deathRespawn)) {
            return false;
        }
        Entity mountEntity = owner.getVehicle();
        if (!(mountEntity instanceof LivingEntity mount)) {
            return false;
        }
        MobEntity pet = context.getPet();
        if (pet == null || pet.isRemoved()) {
            return false;
        }
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            return false;
        }
        if (!component.hasRole(PetRoleType.CURSED_ONE)) {
            return false;
        }
        if (radiusSq > 0.0D && owner.squaredDistanceTo(pet) > radiusSq) {
            return false;
        }
        double radius = radiusSq <= 0.0D ? 0.0D : Math.sqrt(radiusSq);
        if (!CursedOneMountBehaviors.hasNearbyCursedOne(owner, radius)) {
            return false;
        }
        mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, durationTicks, amplifier));
        return true;
    }
}
