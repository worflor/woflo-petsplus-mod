package woflo.petsplus.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.roles.support.SupportPotionUtils.SupportPotionState;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies passive aura and support behaviors driven by role metadata.
 */
public final class PetsplusEffectManager {

    private static final Map<String, AuraTimer> AURA_TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_EFFECT_NOTIFICATION = new ConcurrentHashMap<>();

    private PetsplusEffectManager() {}

    /**
     * Apply all configured aura behaviors for the supplied pet.
     */
    public static void applyRoleAuraEffects(ServerWorld world, MobEntity pet, PetComponent petComp, PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity serverOwner) || !owner.isAlive()) {
            return;
        }

        PetRoleType roleType = petComp.getRoleType(false);
        if (roleType == null) {
            return;
        }

        applyPassiveAuras(world, pet, petComp, serverOwner, roleType);

        PetRoleType.SupportPotionBehavior supportBehavior = roleType.supportPotionBehavior();
        if (supportBehavior != null) {
            applySupportPotionAura(world, pet, petComp, serverOwner, roleType, supportBehavior);
        }
    }

    private static void applyPassiveAuras(ServerWorld world, MobEntity pet, PetComponent petComp, ServerPlayerEntity owner, PetRoleType roleType) {
        List<PetRoleType.PassiveAura> auras = roleType.passiveAuras();
        if (auras.isEmpty()) {
            return;
        }

        int level = petComp.getLevel();
        String petKey = pet.getUuidAsString();
        PetsPlusConfig config = PetsPlusConfig.getInstance();

        for (PetRoleType.PassiveAura aura : auras) {
            if (!aura.hasEffects() || level < aura.minLevel()) {
                continue;
            }
            if (aura.requireSitting() && !isPetSitting(pet)) {
                continue;
            }

            int interval = config.getPassiveAuraInterval(roleType, aura);
            if (!shouldTriggerAura(petKey + "|passive|" + aura.id(), world.getTime(), interval)) {
                continue;
            }

            double radius = config.getPassiveAuraRadius(roleType, aura);
            Set<LivingEntity> affectedEntities = new LinkedHashSet<>();
            for (PetRoleType.AuraEffect effect : aura.effects()) {
                if (level < effect.minLevel()) {
                    continue;
                }
                affectedEntities.addAll(applyAuraEffect(world, pet, owner, radius, effect));
            }

            if (!affectedEntities.isEmpty()) {
                sendConfiguredMessage(owner, aura.message(), pet, "aura:" + aura.id());
                playSound(world, pet, aura.sound());
                emitParticles(world, pet, owner, affectedEntities, radius, aura.particleEvent());
            }
        }
    }

    private static void applySupportPotionAura(ServerWorld world, MobEntity pet, PetComponent petComp, ServerPlayerEntity owner,
                                               PetRoleType roleType, PetRoleType.SupportPotionBehavior behavior) {
        if (!SupportPotionUtils.hasStoredPotion(petComp)) {
            return;
        }
        if (petComp.getLevel() < behavior.minLevel()) {
            return;
        }
        if (behavior.requireSitting() && !isPetSitting(pet)) {
            return;
        }

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int interval = config.getSupportPotionInterval(roleType, behavior);
        String auraKey = pet.getUuidAsString() + "|support_potion";
        if (!shouldTriggerAura(auraKey, world.getTime(), interval)) {
            return;
        }

        SupportPotionState state = SupportPotionUtils.getStoredState(petComp);
        if (!state.isValid()) {
            return;
        }

        int pulseDuration = config.getSupportPotionDuration(roleType, behavior);
        List<StatusEffectInstance> storedEffects = SupportPotionUtils.deserializeEffects(state.serializedEffects(), pulseDuration);
        if (storedEffects.isEmpty() && behavior.fallbackEffect() != null) {
            RegistryEntry<StatusEffect> fallback = Registries.STATUS_EFFECT.getEntry(behavior.fallbackEffect()).orElse(null);
            if (fallback != null) {
                storedEffects = List.of(new StatusEffectInstance(fallback, pulseDuration, 0, false, true, true));
            }
        }
        if (storedEffects.isEmpty()) {
            SupportPotionUtils.clearStoredPotion(petComp);
            return;
        }

        double radius = config.getSupportPotionRadius(roleType, behavior);
        Set<LivingEntity> recipients = new LinkedHashSet<>(resolveTargets(world, pet, owner, radius, PetRoleType.AuraTarget.OWNER_AND_ALLIES));
        if (config.isSupportPotionAppliedToPet(roleType, behavior)) {
            recipients.add(pet);
        }

        if (recipients.isEmpty()) {
            return;
        }

        boolean appliedToAlly = false;
        for (StatusEffectInstance instance : storedEffects) {
            for (LivingEntity recipient : recipients) {
                recipient.addStatusEffect(new StatusEffectInstance(instance));
                if (recipient instanceof ServerPlayerEntity ally && ally != owner) {
                    appliedToAlly = true;
                    AdvancementManager.triggerSupportHealAllies(owner, ally);
                }
            }
        }

        if (appliedToAlly) {
            sendConfiguredMessage(owner, behavior.message(), pet, "support_potion");
        }
        playSound(world, pet, behavior.sound());
        emitParticles(world, pet, owner, recipients, radius, behavior.particleEvent());

        double consumption = SupportPotionUtils.getConsumptionPerPulse(petComp);
        SupportPotionUtils.consumeCharges(petComp, state, consumption);
    }

    private static List<LivingEntity> applyAuraEffect(ServerWorld world, MobEntity pet, ServerPlayerEntity owner,
                                                      double radius, PetRoleType.AuraEffect effect) {
        RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(effect.effectId()).orElse(null);
        if (entry == null) {
            Petsplus.LOGGER.warn("Unknown status effect '{}' configured for aura {}", effect.effectId(), pet.getUuid());
            return List.of();
        }

        List<LivingEntity> targets = resolveTargets(world, pet, owner, radius, effect.target());
        if (targets.isEmpty()) {
            return List.of();
        }

        for (LivingEntity target : targets) {
            target.addStatusEffect(new StatusEffectInstance(entry, effect.durationTicks(), effect.amplifier(), false, true, true));
        }
        return targets;
    }

    private static List<LivingEntity> resolveTargets(ServerWorld world, MobEntity pet, ServerPlayerEntity owner,
                                                     double radius, PetRoleType.AuraTarget target) {
        double squaredRadius = radius <= 0 ? 0 : radius * radius;
        Set<LivingEntity> resolved = new LinkedHashSet<>();
        switch (target) {
            case PET -> resolved.add(pet);
            case OWNER -> addOwnerIfInRange(resolved, owner, pet, squaredRadius);
            case OWNER_AND_PET -> {
                addOwnerIfInRange(resolved, owner, pet, squaredRadius);
                resolved.add(pet);
            }
            case OWNER_AND_ALLIES -> {
                addOwnerIfInRange(resolved, owner, pet, squaredRadius);
                resolved.addAll(findNearbyAllies(world, pet, owner, radius, squaredRadius));
            }
            case NEARBY_PLAYERS -> resolved.addAll(findNearbyPlayers(world, pet, owner, radius, squaredRadius));
            case NEARBY_ALLIES -> resolved.addAll(findNearbyAllies(world, pet, owner, radius, squaredRadius));
        }
        resolved.remove(pet); // ensure pet only included when explicitly requested
        if (target == PetRoleType.AuraTarget.PET || target == PetRoleType.AuraTarget.OWNER_AND_PET) {
            resolved.add(pet);
        }
        return new ArrayList<>(resolved);
    }

    private static void addOwnerIfInRange(Set<LivingEntity> resolved, ServerPlayerEntity owner, MobEntity pet, double squaredRadius) {
        if (owner == null) {
            return;
        }
        if (squaredRadius == 0 || owner.squaredDistanceTo(pet) <= squaredRadius) {
            resolved.add(owner);
        }
    }

    private static List<LivingEntity> findNearbyPlayers(ServerWorld world, MobEntity pet, ServerPlayerEntity owner,
                                                        double radius, double squaredRadius) {
        Box box = pet.getBoundingBox().expand(radius);
        List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class, box,
            player -> player.isAlive() && player.squaredDistanceTo(pet) <= squaredRadius);
        if (owner != null && (squaredRadius == 0 || owner.squaredDistanceTo(pet) <= squaredRadius) && !players.contains(owner)) {
            players.add(owner);
        }
        return new ArrayList<>(players);
    }

    private static List<LivingEntity> findNearbyAllies(ServerWorld world, MobEntity pet, ServerPlayerEntity owner,
                                                       double radius, double squaredRadius) {
        Box box = pet.getBoundingBox().expand(radius);
        List<LivingEntity> allies = new ArrayList<>();
        if (owner != null && (squaredRadius == 0 || owner.squaredDistanceTo(pet) <= squaredRadius)) {
            allies.add(owner);
        }
        allies.addAll(world.getEntitiesByClass(ServerPlayerEntity.class, box,
            player -> player != owner && player.isAlive() && player.squaredDistanceTo(pet) <= squaredRadius));
        allies.addAll(world.getEntitiesByClass(MobEntity.class, box,
            mob -> mob != pet && mob.isAlive() && PetComponent.get(mob) != null && mob.squaredDistanceTo(pet) <= squaredRadius));
        return allies;
    }

    private static boolean shouldTriggerAura(String key, long worldTime, int interval) {
        if (interval <= 0) {
            return true;
        }
        AuraTimer timer = AURA_TIMERS.computeIfAbsent(key, k -> new AuraTimer());
        if (worldTime - timer.lastWorldTime >= interval) {
            timer.lastWorldTime = worldTime;
            timer.lastRealTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private static void sendConfiguredMessage(ServerPlayerEntity owner, PetRoleType.Message message, MobEntity pet, String keySuffix) {
        if (message == null || !message.isPresent() || owner == null) {
            return;
        }
        Text text = resolveMessage(message, pet);
        if (text == null) {
            return;
        }
        notifyOwner(owner, pet.getUuidAsString() + ':' + keySuffix, text);
    }

    private static Text resolveMessage(PetRoleType.Message message, MobEntity pet) {
        if (message == null || !message.isPresent()) {
            return null;
        }
        String translationKey = message.translationKey();
        if (translationKey != null && !translationKey.isBlank()) {
            return Text.translatable(translationKey, pet.getDisplayName());
        }
        String fallback = message.fallback();
        if (fallback != null && !fallback.isBlank()) {
            String formatted = fallback;
            try {
                formatted = String.format(fallback, getPetName(pet));
            } catch (IllegalArgumentException ignored) {
            }
            return Text.literal(formatted);
        }
        return null;
    }

    private static void playSound(ServerWorld world, MobEntity pet, PetRoleType.SoundCue sound) {
        if (sound == null || !sound.isPresent()) {
            return;
        }
        RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(sound.soundId()).orElse(null);
        if (entry == null) {
            Petsplus.LOGGER.warn("Unknown sound '{}' configured for aura on pet {}", sound.soundId(), pet.getUuid());
            return;
        }
        world.playSound(null, pet.getBlockPos(), entry.value(), SoundCategory.NEUTRAL, sound.volume(), sound.pitch());
    }

    private static void emitParticles(ServerWorld world, MobEntity pet, ServerPlayerEntity owner,
                                      Set<LivingEntity> recipients, double radius, String particleEvent) {
        if (particleEvent == null || particleEvent.isBlank()) {
            return;
        }
        switch (particleEvent) {
            case "guardian" -> emitGuardianParticles(world, pet.getPos(), owner != null ? owner.getPos() : pet.getPos());
            case "support" -> emitSupportParticles(world, pet, radius, recipients);
            case "nap_time" -> emitNapTimeParticles(world, pet.getPos(), radius);
            default -> emitGenericParticles(world, pet.getPos());
        }
    }

    private static void emitGuardianParticles(ServerWorld world, Vec3d petPos, Vec3d ownerPos) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double x = petPos.x + Math.cos(angle) * 1.5;
            double z = petPos.z + Math.sin(angle) * 1.5;
            world.spawnParticles(ParticleTypes.ENCHANT, x, petPos.y + 1, z, 1, 0, 0, 0, 0.02);
        }
        world.spawnParticles(ParticleTypes.END_ROD, ownerPos.x, ownerPos.y + 1, ownerPos.z, 4, 0.2, 0.4, 0.2, 0.01);
    }

    private static void emitSupportParticles(ServerWorld world, MobEntity pet, double radius, Set<LivingEntity> recipients) {
        Vec3d center = pet.getPos();
        int count = Math.max(4, recipients.size() * 3);
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            double distance = Math.min(radius, 2.5);
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            world.spawnParticles(ParticleTypes.HEART, x, center.y + 0.7, z, 1, 0.1, 0.2, 0.1, 0);
        }
    }

    private static void emitNapTimeParticles(ServerWorld world, Vec3d petPos, double radius) {
        for (int i = 0; i < 6; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double distance = world.random.nextDouble() * radius;
            double x = petPos.x + Math.cos(angle) * distance;
            double z = petPos.z + Math.sin(angle) * distance;
            world.spawnParticles(ParticleTypes.SNEEZE, x, petPos.y + 0.5, z, 1, 0.0, 0.1, 0.0, 0.01);
        }
    }

    private static void emitGenericParticles(ServerWorld world, Vec3d petPos) {
        world.spawnParticles(ParticleTypes.GLOW, petPos.x, petPos.y + 0.8, petPos.z, 3, 0.2, 0.3, 0.2, 0.01);
    }

    private static boolean isPetSitting(MobEntity pet) {
        return pet instanceof net.minecraft.entity.passive.TameableEntity tameable && tameable.isSitting();
    }

    private static void notifyOwner(ServerPlayerEntity owner, String notificationKey, Text message) {
        long now = System.currentTimeMillis();
        Long lastSent = LAST_EFFECT_NOTIFICATION.get(notificationKey);
        if (lastSent == null || now - lastSent >= 10_000) {
            owner.sendMessage(message, true);
            LAST_EFFECT_NOTIFICATION.put(notificationKey, now);
        }
    }

    private static String getPetName(MobEntity pet) {
        return pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
    }

    /**
     * Clean up old tracking data to prevent memory leaks.
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        AURA_TIMERS.entrySet().removeIf(entry -> now - entry.getValue().lastRealTime > 300_000);
        LAST_EFFECT_NOTIFICATION.entrySet().removeIf(entry -> now - entry.getValue() > 300_000);
    }

    private static final class AuraTimer {
        long lastWorldTime;
        long lastRealTime;
    }
}
