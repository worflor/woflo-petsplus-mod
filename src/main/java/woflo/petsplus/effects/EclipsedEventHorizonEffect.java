package woflo.petsplus.effects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.eclipsed.EclipsedVoid;
import woflo.petsplus.state.PetComponent;

/**
 * Ability effect for Event Horizon that applies crowd control and protective buffs when
 * the owner drops to low health.
 */
public class EclipsedEventHorizonEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eclipsed_event_horizon");

    private final double radius;
    private final int enemyEffectDuration;
    private final List<StatusEffectInstance> enemyEffects;
    private final List<StatusEffectInstance> ownerEffects;
    private final double healFlat;
    private final double healPercent;
    private final double projectileDr;
    private final int projectileDrDuration;
    private final int minLevel;
    private final boolean requirePerched;

    public EclipsedEventHorizonEffect(JsonObject json) {
        this.radius = Math.max(0.5D, RegistryJsonHelper.getDouble(json, "radius", EclipsedVoid.getEventHorizonRadius()));
        this.enemyEffectDuration = RegistryJsonHelper.getInt(json, "enemy_effect_duration_ticks", EclipsedVoid.getEventHorizonDurationTicks());
        this.enemyEffects = parseEffects(json, "enemy_effects", enemyEffectDuration);
        this.ownerEffects = parseEffects(json, "owner_effects", RegistryJsonHelper.getInt(json, "owner_effect_duration_ticks", 100));
        this.healFlat = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "heal_flat", 0.0D));
        this.healPercent = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "heal_percent", 0.0D));
        this.projectileDr = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "projectile_dr", EclipsedVoid.getEventHorizonProjectileDrPct()));
        this.projectileDrDuration = Math.max(0, RegistryJsonHelper.getInt(json, "projectile_dr_duration_ticks", EclipsedVoid.getEventHorizonDurationTicks()));
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 7));
        this.requirePerched = RegistryJsonHelper.getBoolean(json, "require_perched", false);
    }

    public EclipsedEventHorizonEffect() {
        this.radius = EclipsedVoid.getEventHorizonRadius();
        this.enemyEffectDuration = EclipsedVoid.getEventHorizonDurationTicks();
        this.enemyEffects = List.of();
        this.ownerEffects = List.of();
        this.healFlat = 0.0D;
        this.healPercent = 0.0D;
        this.projectileDr = EclipsedVoid.getEventHorizonProjectileDrPct();
        this.projectileDrDuration = EclipsedVoid.getEventHorizonDurationTicks();
        this.minLevel = 7;
        this.requirePerched = false;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();
        if (world == null || pet == null || owner == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ECLIPSED) || component.getLevel() < minLevel) {
            return false;
        }

        if (requirePerched && !EclipsedVoid.isPetPerched(pet, owner)) {
            return false;
        }

        Vec3d center = owner.getEntityPos();
        Box zone = Box.of(center, radius * 2, radius * 2, radius * 2);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, zone, entity -> entity.squaredDistanceTo(center) <= radius * radius && entity.isAlive());
        boolean affectedHostiles = false;
        if (!enemyEffects.isEmpty()) {
            for (HostileEntity hostile : hostiles) {
                for (StatusEffectInstance effect : enemyEffects) {
                    hostile.addStatusEffect(new StatusEffectInstance(effect));
                }
                affectedHostiles = true;
            }
        } else {
            affectedHostiles = !hostiles.isEmpty();
        }

        if (!ownerEffects.isEmpty()) {
            for (StatusEffectInstance effect : ownerEffects) {
                owner.addStatusEffect(new StatusEffectInstance(effect));
            }
        }

        if (healFlat > 0.0D || healPercent > 0.0D) {
            double healAmount = healFlat;
            healAmount += owner.getMaxHealth() * healPercent;
            if (healAmount > 0.0D) {
                owner.heal((float) healAmount);
            }
        }

        if (projectileDr > 0.0D && projectileDrDuration > 0) {
            new ProjectileDrForOwnerEffect(projectileDr, projectileDrDuration).execute(context);
        }

        // Removed event horizon spam - already has particle/sound in ability JSON

        spawnFeedback(world, center, hostiles);
        return affectedHostiles || !ownerEffects.isEmpty() || healFlat > 0.0D || healPercent > 0.0D || projectileDr > 0.0D;
    }

    private List<StatusEffectInstance> parseEffects(JsonObject json, String key, int defaultDuration) {
        JsonArray array = RegistryJsonHelper.getArray(json, key);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<StatusEffectInstance> instances = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                continue;
            }
            JsonObject effectJson = array.get(i).getAsJsonObject();
            if (!effectJson.has("duration")) {
                effectJson.addProperty("duration", defaultDuration);
            }
            StatusEffectInstance parsed = RegistryJsonHelper.parseStatusEffect(effectJson);
            if (parsed != null) {
                instances.add(parsed);
            }
        }
        return instances;
    }

    private void spawnFeedback(ServerWorld world, Vec3d center, List<? extends LivingEntity> hostiles) {
        world.spawnParticles(ParticleTypes.PORTAL, center.x, center.y + 0.5D, center.z, 32, radius * 0.4D, 0.4D, radius * 0.4D, 0.1D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 0.6F, 0.85F);
        for (LivingEntity hostile : hostiles) {
            world.spawnParticles(ParticleTypes.SMOKE, hostile.getX(), hostile.getEyeY(), hostile.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.02D);
        }
    }
}



