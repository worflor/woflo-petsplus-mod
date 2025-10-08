package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.criteria.PetStatThresholdCriterion;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.AuraTargetResolver;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.roles.support.SupportPotionUtils.SupportPotionState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;
import woflo.petsplus.api.entity.PetsplusTameable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manual trigger for sharing the Support pet's stored potion aura.
 */
public class SupportPotionPulseEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "support_potion_pulse");

    private final double radiusMultiplier;
    private final double durationMultiplier;
    private final boolean allowFallback;
    private final Boolean applyToPetOverride;
    private final double chargeCostMultiplier;
    private final boolean sendActionBar;
    private final boolean playConfiguredSound;
    private final boolean emitParticles;
    private final boolean swingOwner;

    public SupportPotionPulseEffect(JsonObject json) {
        this.radiusMultiplier = Math.max(0.25, RegistryJsonHelper.getDouble(json, "radius_multiplier", 1.0));
        this.durationMultiplier = Math.max(0.25, RegistryJsonHelper.getDouble(json, "duration_multiplier", 1.0));
        this.allowFallback = RegistryJsonHelper.getBoolean(json, "allow_fallback", true);
        if (json.has("apply_to_pet")) {
            this.applyToPetOverride = RegistryJsonHelper.getBoolean(json, "apply_to_pet", true);
        } else {
            this.applyToPetOverride = null;
        }
        this.chargeCostMultiplier = Math.max(0.0, RegistryJsonHelper.getDouble(json, "charge_cost_multiplier", 1.0));
        this.sendActionBar = RegistryJsonHelper.getBoolean(json, "send_action_bar", true);
        this.playConfiguredSound = RegistryJsonHelper.getBoolean(json, "play_sound", true);
        this.emitParticles = RegistryJsonHelper.getBoolean(json, "emit_particles", true);
        this.swingOwner = RegistryJsonHelper.getBoolean(json, "swing_owner", true);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        MobEntity pet = context.getPet();
        ServerWorld world = context.getEntityWorld();
        if (!(owner instanceof ServerPlayerEntity serverOwner) || pet == null || world == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }

        PetRoleType roleType = component.getRoleType(false);
        PetRoleType.SupportPotionBehavior behavior = roleType != null ? roleType.supportPotionBehavior() : null;
        if (behavior == null) {
            return false;
        }
        if (component.getLevel() < behavior.minLevel()) {
            UIFeedbackManager.sendSupportPotionLocked(serverOwner, pet, behavior.minLevel());
            return false;
        }
        if ((behavior.requireSitting() && !isPetSitting(pet))) {
            UIFeedbackManager.sendSupportPotionNeedsSitting(serverOwner, pet);
            return false;
        }
        if (!SupportPotionUtils.hasStoredPotion(component)) {
            UIFeedbackManager.sendSupportPotionEmpty(serverOwner, pet);
            return false;
        }

        SupportPotionState state = SupportPotionUtils.getStoredState(component);
        if (!state.isValid()) {
            SupportPotionUtils.clearStoredPotion(component);
            UIFeedbackManager.sendSupportPotionEmpty(serverOwner, pet);
            return false;
        }

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double radius = SupportPotionUtils.getScaledAuraRadius(
            component,
            behavior,
            config.getSupportPotionRadius(roleType, behavior)
        );
        radius *= radiusMultiplier;
        double initialRadius = radius;
        int baseDuration = SupportPotionUtils.getScaledAuraDuration(
            component,
            behavior,
            state,
            config.getSupportPotionDuration(roleType, behavior)
        );
        int duration = (int) Math.max(20, Math.round(baseDuration * durationMultiplier));

        StateManager manager = StateManager.forWorld(world);
        AuraTargetResolver resolver = manager.getAuraTargetResolver();
        Set<LivingEntity> recipients = new LinkedHashSet<>(resolver.resolveTargets(
            world,
            pet,
            component,
            serverOwner,
            radius,
            PetRoleType.AuraTarget.OWNER_AND_ALLIES
        ));
        boolean applyToPet = applyToPetOverride != null ? applyToPetOverride : config.isSupportPotionAppliedToPet(roleType, behavior);
        if (applyToPet) {
            recipients.add(pet);
        }
        recipients.removeIf(Entity::isRemoved);
        if (recipients.isEmpty()) {
            return false;
        }

        double baseConsumption = SupportPotionUtils.getConsumptionPerPulse(component) * chargeCostMultiplier;
        SupportPotionUtils.PulseProfile profile = SupportPotionUtils.computePulseProfile(
            component,
            state,
            behavior,
            radius,
            duration,
            baseConsumption,
            world,
            serverOwner,
            pet,
            recipients
        );

        double tunedRadius = profile.radius();
        duration = profile.durationTicks();

        if (Math.abs(tunedRadius - initialRadius) > 0.01) {
            radius = tunedRadius;
            recipients = new LinkedHashSet<>(resolver.resolveTargets(
                world,
                pet,
                component,
                serverOwner,
                radius,
                PetRoleType.AuraTarget.OWNER_AND_ALLIES
            ));
            if (applyToPet) {
                recipients.add(pet);
            }
            recipients.removeIf(Entity::isRemoved);
            if (recipients.isEmpty()) {
                return false;
            }
        } else {
            radius = tunedRadius;
        }

        List<StatusEffectInstance> storedEffects = SupportPotionUtils.deserializeEffects(state.serializedEffects(), duration);
        if (storedEffects.isEmpty() && allowFallback && behavior.fallbackEffect() != null) {
            RegistryEntry<StatusEffect> fallback = Registries.STATUS_EFFECT.getEntry(behavior.fallbackEffect()).orElse(null);
            if (fallback != null) {
                storedEffects = List.of(new StatusEffectInstance(fallback, duration, 0, false, true, true));
            }
        }
        if (storedEffects.isEmpty()) {
            SupportPotionUtils.clearStoredPotion(component);
            UIFeedbackManager.sendSupportPotionEmpty(serverOwner, pet);
            return false;
        }

        boolean applied = false;
        boolean healedAlly = false;
        for (StatusEffectInstance effect : storedEffects) {
            for (LivingEntity recipient : recipients) {
                recipient.addStatusEffect(new StatusEffectInstance(effect));
                applied = true;
                if (recipient instanceof ServerPlayerEntity ally && ally != serverOwner) {
                    healedAlly = true;
                    // Track unique allies healed for advancement (pet-centric modular history)
                    long currentDay = world.getTimeOfDay() / 24000L;
                    woflo.petsplus.history.HistoryManager.recordAllyHealed(pet, serverOwner, ally.getUuid(), currentDay);
                    
                    // Calculate unique allies healed today from pet's history
                    java.util.Set<java.util.UUID> uniqueAllies = component.getUniqueAlliesHealedOnDay(serverOwner.getUuid(), currentDay);
                    woflo.petsplus.advancement.AdvancementCriteriaRegistry.PET_STAT_THRESHOLD.trigger(
                        serverOwner,
                        PetStatThresholdCriterion.STAT_ALLIES_HEALED,
                        (float) uniqueAllies.size()
                    );
                }
            }
        }

        if (!applied) {
            return false;
        }

        double consumption = profile.chargeCost();
        if (consumption > 0.0) {
            SupportPotionUtils.consumeCharges(component, state, consumption);
        }

        // Removed rhythm/clutch/comfort spam - too frequent, not meaningful

        if (swingOwner) {
            serverOwner.swingHand(Hand.MAIN_HAND, true);
        }
        // Removed pulse spam - too frequent
        if (playConfiguredSound) {
            playSound(world, pet, behavior.sound());
        }
        if (emitParticles) {
            FeedbackManager.emitRoleAbility(PetRoleType.SUPPORT.id(), "Potion_Pulse", pet, world);
            emitConfiguredParticles(world, pet, recipients, radius, behavior.particleEvent());
        }
        // Removed assist spam - too frequent

        if (SupportPotionUtils.hasStoredPotion(component)) {
            SupportPotionUtils.recordPulseTelemetry(component, state, world, profile);
        } else {
            SupportPotionUtils.clearPulseTelemetry(component);
        }

        return true;
    }

    private void playSound(ServerWorld world, MobEntity pet, @Nullable PetRoleType.SoundCue sound) {
        if (sound == null || !sound.isPresent()) {
            return;
        }
        RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(sound.soundId()).orElse(null);
        if (entry == null) {
            return;
        }
        world.playSound(null, pet.getBlockPos(), entry.value(), SoundCategory.NEUTRAL, sound.volume(), sound.pitch());
    }

    private void emitConfiguredParticles(ServerWorld world, MobEntity pet, Set<LivingEntity> recipients,
                                         double radius, @Nullable String particleEvent) {
        if (particleEvent == null || particleEvent.isBlank()) {
            return;
        }
        switch (particleEvent) {
            case "support" -> emitSupportParticles(world, pet, radius, recipients);
            default -> emitGenericParticles(world, pet.getEntityPos());
        }
    }

    private void emitSupportParticles(ServerWorld world, MobEntity pet, double radius, Set<LivingEntity> recipients) {
        Vec3d center = pet.getEntityPos();
        int count = Math.max(4, recipients.size() * 3);
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            double distance = Math.min(radius, 2.5);
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART, x, center.y + 0.7, z, 1, 0.1, 0.2, 0.1, 0);
        }
    }

    private void emitGenericParticles(ServerWorld world, Vec3d pos) {
        world.spawnParticles(net.minecraft.particle.ParticleTypes.GLOW, pos.x, pos.y + 0.8, pos.z, 3, 0.2, 0.3, 0.2, 0.01);
    }

    private boolean isPetSitting(MobEntity pet) {
        return pet instanceof PetsplusTameable tameable && tameable.petsplus$isSitting();
    }
}



