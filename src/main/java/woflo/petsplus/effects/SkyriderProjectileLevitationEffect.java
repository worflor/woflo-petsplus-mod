package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.skyrider.SkyriderWinds;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import woflo.petsplus.util.ChanceValidationUtil;

/**
 * Applies Skyrider projectile levitation control on qualifying critical hits.
 */
public class SkyriderProjectileLevitationEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "skyrider_projectile_levitation");

    private final double radiusSq;
    private final int minLevel;
    private final boolean requireCritical;
    private final double chance;
    private final int victimDurationTicks;
    private final int victimAmplifier;
    private final double splashRadiusSq;
    private final int splashDurationTicks;
    private final int splashAmplifier;
    private final int ownerSlowfallTicks;
    private final int ownerLevitationTicks;
    private final int ownerLevitationAmplifier;
    private final int petSlowfallTicks;
    private final long duplicateGateTicks;
    private final boolean sendMessage;

    public SkyriderProjectileLevitationEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "radius", 16.0D);
        this.radiusSq = radius <= 0 ? 0 : radius * radius;
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
        this.requireCritical = RegistryJsonHelper.getBoolean(json, "require_critical", true);
        this.chance = ChanceValidationUtil.validateChance(RegistryJsonHelper.getDouble(json, "chance", SkyriderWinds.getProjLevitateChance()));
        this.victimDurationTicks = Math.max(1, RegistryJsonHelper.getInt(json, "victim_duration_ticks", 40));
        this.victimAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "victim_amplifier", 0));
        double splashRadius = RegistryJsonHelper.getDouble(json, "splash_radius", 0.0D);
        this.splashRadiusSq = splashRadius <= 0 ? 0 : splashRadius * splashRadius;
        this.splashDurationTicks = Math.max(0, RegistryJsonHelper.getInt(json, "splash_duration_ticks", 40));
        this.splashAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "splash_amplifier", 0));
        this.ownerSlowfallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "owner_slowfall_ticks", 80));
        this.ownerLevitationTicks = Math.max(0, RegistryJsonHelper.getInt(json, "owner_levitation_ticks", 0));
        this.ownerLevitationAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "owner_levitation_amplifier", 0));
        this.petSlowfallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "pet_slowfall_ticks", 80));
        this.duplicateGateTicks = Math.max(1L, RegistryJsonHelper.getInt(json, "duplicate_gate_ticks", 5));
        this.sendMessage = RegistryJsonHelper.getBoolean(json, "send_message", true);
    }

    public SkyriderProjectileLevitationEffect() {
        this.radiusSq = 16.0D * 16.0D;
        this.minLevel = 1;
        this.requireCritical = true;
        this.chance = SkyriderWinds.getProjLevitateChance();
        this.victimDurationTicks = 40;
        this.victimAmplifier = 0;
        this.splashRadiusSq = 0;
        this.splashDurationTicks = 40;
        this.splashAmplifier = 0;
        this.ownerSlowfallTicks = 80;
        this.ownerLevitationTicks = 0;
        this.ownerLevitationAmplifier = 0;
        this.petSlowfallTicks = 80;
        this.duplicateGateTicks = 5L;
        this.sendMessage = true;
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

        if (world == null || pet == null || !(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.SKYRIDER) || component.getLevel() < minLevel) {
            return false;
        }

        if (radiusSq > 0.0D && serverOwner.squaredDistanceTo(pet) > radiusSq) {
            return false;
        }

        Boolean critFlag = context.getTriggerContext().getData("projectile_critical", Boolean.class);
        if (requireCritical && (critFlag == null || !critFlag)) {
            return false;
        }

        if (!ChanceValidationUtil.checkChance(chance, serverOwner.getRandom())) {
            return false;
        }

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        long currentTick = world.getTime();
        long lastTick = ownerState.getTempState(SkyriderWinds.PROJ_LEVITATION_LAST_TRIGGER_KEY);
        if (lastTick != 0 && currentTick - lastTick < duplicateGateTicks) {
            return false;
        }

        LivingEntity victim = context.getTriggerContext().getVictim() instanceof LivingEntity living ? living : null;
        if (victim == null || !victim.isAlive()) {
            return false;
        }

        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, victimDurationTicks, victimAmplifier, false, true, true));

        if (splashRadiusSq > 0.0D) {
            applySplashLevitation(world, victim, pet, owner);
        }

        if (ownerSlowfallTicks > 0) {
            serverOwner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, ownerSlowfallTicks, 0, false, true, true));
        }
        if (ownerLevitationTicks > 0) {
            serverOwner.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, ownerLevitationTicks, ownerLevitationAmplifier, false, true, true));
        }

        if (petSlowfallTicks > 0 && pet instanceof LivingEntity petLiving) {
            petLiving.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, petSlowfallTicks, 0, false, true, true));
        }

        // Removed levitation spam - will add particle/sound in ability JSON

        ownerState.setTempState(SkyriderWinds.PROJ_LEVITATION_LAST_TRIGGER_KEY, currentTick);
        return true;
    }

    private void applySplashLevitation(ServerWorld world, LivingEntity victim, Entity pet, Entity owner) {
        Vec3d center = victim.getEntityPos();
        double radius = Math.sqrt(splashRadiusSq);
        Box area = new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, area, entity ->
            entity.isAlive() && entity != victim && entity != owner && entity != pet && entity instanceof HostileEntity);
        if (nearby.isEmpty()) {
            return;
        }

        StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.LEVITATION, Math.max(1, splashDurationTicks), Math.max(0, splashAmplifier), false, true, true);
        for (LivingEntity target : nearby) {
            target.addStatusEffect(effect);
        }
    }
}



