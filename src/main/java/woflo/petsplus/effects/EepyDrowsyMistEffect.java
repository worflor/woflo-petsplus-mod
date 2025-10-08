package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusEntityTypeTags;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * Shift-interact ability for Eepy Eepers that blankets an area in slumberous mist.
 */
public class EepyDrowsyMistEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eepy_drowsy_mist");
    private static final ConcurrentMap<ServerWorld, ConcurrentMap<UUID, MistInstance>> ACTIVE_MISTS = new ConcurrentHashMap<>();
    private static volatile boolean tickerRegistered;

    private final Config config;

    public EepyDrowsyMistEffect(JsonObject json) {
        this.config = Config.fromJson(json);
        registerTicker();
    }

    public EepyDrowsyMistEffect(Config config) {
        this.config = config;
        registerTicker();
    }

    private static void registerTicker() {
        if (!tickerRegistered) {
            synchronized (EepyDrowsyMistEffect.class) {
                if (!tickerRegistered) {
                    ServerTickEvents.END_WORLD_TICK.register(EepyDrowsyMistEffect::tickWorld);
                    ServerWorldEvents.UNLOAD.register((server, world) -> ACTIVE_MISTS.remove(world));
                    ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE_MISTS.clear());
                    tickerRegistered = true;
                }
            }
        }
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        if (pet == null || pet.isRemoved() || !pet.isAlive()) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }

        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }

        if (!component.isOwnedBy(owner)) {
            return false;
        }

        if (pet instanceof TameableEntity tameable && !tameable.isInSittingPose()) {
            return false;
        }

        if (component.isPerched()) {
            return false;
        }

        if (owner.squaredDistanceTo(pet) > config.activationRangeSq) {
            return false;
        }

        int level = Math.max(1, component.getLevel());
        long now = world.getTime();
        int duration = config.resolveDuration(level);
        long expiryTick = now + duration;
        Vec3d anchor = pet.getEntityPos();

        ConcurrentMap<UUID, MistInstance> worldStates = ACTIVE_MISTS.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        MistInstance instance = worldStates.compute(pet.getUuid(), (uuid, existing) -> {
            if (existing == null) {
                return new MistInstance(pet.getUuid(), component.getOwnerUuid(), anchor, expiryTick, config, now);
            }
            existing.refresh(anchor, expiryTick, now);
            return existing;
        });

        if (instance == null) {
            return false;
        }

        instance.forcePulse(world, pet, level, now);

        FeedbackManager.emitRoleAbility(PetRoleType.EEPY_EEPER.id(), "drowsy_mist", pet, world);
        world.playSound(null, anchor.x, anchor.y, anchor.z,
            SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.6f, 0.8f);
        // Removed drowsy mist spam - already has sound feedback above
        return true;
    }

    private static void tickWorld(ServerWorld world) {
        ConcurrentMap<UUID, MistInstance> states = ACTIVE_MISTS.get(world);
        if (states == null || states.isEmpty()) {
            return;
        }

        long now = world.getTime();
        Iterator<Map.Entry<UUID, MistInstance>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MistInstance> entry = iterator.next();
            MistInstance state = entry.getValue();
            if (state == null || !state.tick(world, now)) {
                iterator.remove();
            }
        }

        if (states.isEmpty()) {
            ACTIVE_MISTS.remove(world, states);
        }
    }

    private static class MistInstance {
        private final UUID petId;
        @Nullable
        private final UUID ownerId;
        private final Config config;
        private Vec3d anchor;
        private long expireTick;
        private long nextPulseTick;
        private long startTick;

        MistInstance(UUID petId, @Nullable UUID ownerId, Vec3d anchor, long expireTick, Config config, long now) {
            this.petId = petId;
            this.ownerId = ownerId;
            this.config = config;
            this.anchor = anchor;
            this.expireTick = expireTick;
            this.nextPulseTick = now;
            this.startTick = now;
        }

        void refresh(Vec3d newAnchor, long newExpireTick, long now) {
            this.anchor = newAnchor;
            this.expireTick = Math.max(this.expireTick, newExpireTick);
            this.nextPulseTick = Math.min(this.nextPulseTick, now);
            this.startTick = now;
        }

        void forcePulse(ServerWorld world, MobEntity pet, int level, long now) {
            pulse(world, pet, level, now);
            this.nextPulseTick = now + Math.max(1, config.pulseIntervalTicks);
        }

        boolean tick(ServerWorld world, long now) {
            if (now >= expireTick) {
                return false;
            }

            Entity entity = world.getEntity(petId);
            if (!(entity instanceof MobEntity pet) || pet.isRemoved() || !pet.isAlive()) {
                return false;
            }

            if (pet instanceof TameableEntity tameable && !tameable.isInSittingPose()) {
                return false;
            }

            PetComponent component = PetComponent.get(pet);
            if (component == null) {
                return false;
            }

            if (component.isPerched()) {
                return false;
            }

            if (ownerId != null && !ownerId.equals(component.getOwnerUuid())) {
                return false;
            }

            if (now < nextPulseTick) {
                return true;
            }

            int level = Math.max(1, component.getLevel());
            pulse(world, pet, level, now);
            nextPulseTick = now + Math.max(1, config.pulseIntervalTicks);
            return true;
        }

        private void pulse(ServerWorld world, MobEntity pet, int level, long now) {
            double radius = config.resolveRadius(level);
            double radiusSq = radius * radius;
            Vec3d center = this.anchor;
            double lifetime = Math.max(1.0, expireTick - startTick);
            double remainingStrength = MathHelper.clamp((expireTick - now) / lifetime, 0.0, 1.0);

            Box searchBox = Box.of(center, radius * 2.0, Math.max(1.5, radius), radius * 2.0);
            Predicate<HostileEntity> filter = hostile -> hostile.isAlive()
                && hostile.squaredDistanceTo(center.x, center.y, center.z) <= radiusSq
                && (config.targetTag == null || hostile.getType().isIn(config.targetTag))
                && (config.immuneTag == null || !hostile.getType().isIn(config.immuneTag))
                && !hostile.isTeammate(pet);

            StatusEffectInstance slowness = new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                config.effectDurationTicks,
                config.resolveSlownessAmplifier(level),
                false,
                true,
                true
            );

            StatusEffectInstance weakness = null;
            if (config.shouldApplyWeakness(level)) {
                weakness = new StatusEffectInstance(
                    StatusEffects.WEAKNESS,
                    config.effectDurationTicks,
                    Math.max(0, config.weaknessAmplifier),
                    false,
                    true,
                    true
                );
            }

            for (HostileEntity hostile : world.getEntitiesByClass(HostileEntity.class, searchBox, filter)) {
                hostile.addStatusEffect(slowness, pet);
                if (weakness != null) {
                    hostile.addStatusEffect(weakness, pet);
                }
            }

            spawnParticles(world, center, radius, level, remainingStrength);
        }

        private void spawnParticles(ServerWorld world, Vec3d center, double radius, int level, double strength) {
            double clampedRadius = MathHelper.clamp(radius, 1.5, config.radiusMax);
            double spread = clampedRadius * 0.45;
            double yBase = center.y + 0.1;
            double scaledStrength = MathHelper.clamp(strength, 0.25, 1.0);

            int cloudCount = MathHelper.ceil((6 + clampedRadius * 2.0) * scaledStrength);
            world.spawnParticles(
                ParticleTypes.CLOUD,
                center.x,
                yBase,
                center.z,
                cloudCount,
                spread,
                0.1,
                spread,
                0.01
            );

            int enchantCount = MathHelper.ceil((3 + clampedRadius) * scaledStrength);
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                center.x,
                yBase + 0.25,
                center.z,
                enchantCount,
                clampedRadius * 0.35,
                0.35,
                clampedRadius * 0.35,
                0.0
            );

            if (config.shouldApplyWeakness(level)) {
                int sporeCount = MathHelper.ceil((1 + clampedRadius * 0.35) * scaledStrength);
                world.spawnParticles(
                    ParticleTypes.FALLING_SPORE_BLOSSOM,
                    center.x,
                    yBase + 0.1,
                    center.z,
                    sporeCount,
                    clampedRadius * 0.25,
                    0.2,
                    clampedRadius * 0.25,
                    0.0
                );
            }

            if (world.getRandom().nextFloat() < 0.25f * scaledStrength) {
                world.playSound(null, center.x, yBase, center.z,
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_STEP, SoundCategory.PLAYERS, 0.25f,
                    0.9f + world.getRandom().nextFloat() * 0.1f);
            }
        }
    }

    private static class Config {
        private final double radiusBase;
        private final double radiusPerLevel;
        private final double radiusMax;
        private final int durationBaseTicks;
        private final int durationPerLevelTicks;
        private final int durationMaxTicks;
        private final int pulseIntervalTicks;
        private final int effectDurationTicks;
        private final int slownessBaseAmplifier;
        private final int slownessLevelThreshold;
        private final int slownessMaxAmplifier;
        private final int weaknessLevelThreshold;
        private final int weaknessAmplifier;
        private final double activationRangeSq;
        @Nullable
        private final TagKey<net.minecraft.entity.EntityType<?>> targetTag;
        @Nullable
        private final TagKey<net.minecraft.entity.EntityType<?>> immuneTag;

        private Config(double radiusBase, double radiusPerLevel, double radiusMax, int durationBaseTicks,
                       int durationPerLevelTicks, int durationMaxTicks, int pulseIntervalTicks, int effectDurationTicks,
                       int slownessBaseAmplifier, int slownessLevelThreshold, int slownessMaxAmplifier,
                       int weaknessLevelThreshold, int weaknessAmplifier, double activationRange,
                       @Nullable TagKey<net.minecraft.entity.EntityType<?>> targetTag,
                       @Nullable TagKey<net.minecraft.entity.EntityType<?>> immuneTag) {
            this.radiusBase = radiusBase;
            this.radiusPerLevel = radiusPerLevel;
            this.radiusMax = radiusMax;
            this.durationBaseTicks = durationBaseTicks;
            this.durationPerLevelTicks = durationPerLevelTicks;
            this.durationMaxTicks = durationMaxTicks;
            this.pulseIntervalTicks = Math.max(1, pulseIntervalTicks);
            this.effectDurationTicks = Math.max(1, effectDurationTicks);
            this.slownessBaseAmplifier = Math.max(0, slownessBaseAmplifier);
            this.slownessLevelThreshold = Math.max(1, slownessLevelThreshold);
            this.slownessMaxAmplifier = Math.max(this.slownessBaseAmplifier, slownessMaxAmplifier);
            this.weaknessLevelThreshold = Math.max(1, weaknessLevelThreshold);
            this.weaknessAmplifier = Math.max(0, weaknessAmplifier);
            this.activationRangeSq = activationRange * activationRange;
            this.targetTag = targetTag;
            this.immuneTag = immuneTag;
        }

        static Config fromJson(JsonObject json) {
            double radiusBase = RegistryJsonHelper.getDouble(json, "radius_base", 4.5);
            double radiusPerLevel = RegistryJsonHelper.getDouble(json, "radius_per_level", 0.1);
            double radiusMax = RegistryJsonHelper.getDouble(json, "radius_max", 8.0);
            int durationBase = RegistryJsonHelper.getInt(json, "duration_base_ticks", 100);
            int durationPerLevel = RegistryJsonHelper.getInt(json, "duration_per_level_ticks", 2);
            int durationMax = RegistryJsonHelper.getInt(json, "duration_max_ticks", 160);
            int pulseInterval = RegistryJsonHelper.getInt(json, "pulse_interval_ticks", 10);
            int effectDuration = RegistryJsonHelper.getInt(json, "effect_pulse_ticks", 60);
            int slownessBase = RegistryJsonHelper.getInt(json, "slowness_base_amplifier", 2);
            int slownessThreshold = RegistryJsonHelper.getInt(json, "slowness_level_threshold", 24);
            int slownessMax = RegistryJsonHelper.getInt(json, "slowness_max_amplifier", 3);
            int weaknessThreshold = RegistryJsonHelper.getInt(json, "weakness_level_threshold", 17);
            int weaknessAmp = RegistryJsonHelper.getInt(json, "weakness_amplifier", 1);
            double activationRange = RegistryJsonHelper.getDouble(json, "activation_range", 10.0);

            String targetTagId = RegistryJsonHelper.getString(json, "target_tag", null);
            TagKey<net.minecraft.entity.EntityType<?>> targetTag = parseTag(targetTagId);

            String immuneTagId = RegistryJsonHelper.getString(json, "immune_tag", null);
            TagKey<net.minecraft.entity.EntityType<?>> immuneTag = immuneTagId != null
                ? parseTag(immuneTagId)
                : PetsplusEntityTypeTags.CC_RESISTANT;

            return new Config(radiusBase, radiusPerLevel, radiusMax, durationBase, durationPerLevel, durationMax,
                pulseInterval, effectDuration, slownessBase, slownessThreshold, slownessMax, weaknessThreshold,
                weaknessAmp, activationRange, targetTag, immuneTag);
        }

        private static TagKey<net.minecraft.entity.EntityType<?>> parseTag(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            Identifier id = Identifier.of(raw);
            return TagKey.of(RegistryKeys.ENTITY_TYPE, id);
        }

        double resolveRadius(int level) {
            double scaled = radiusBase + radiusPerLevel * Math.max(level, 1);
            return MathHelper.clamp(scaled, 0.5, radiusMax);
        }

        int resolveDuration(int level) {
            int scaled = durationBaseTicks + durationPerLevelTicks * Math.max(level, 1);
            int minimum = Math.max(Math.max(durationBaseTicks, pulseIntervalTicks), effectDurationTicks);
            return MathHelper.clamp(scaled, minimum, durationMaxTicks);
        }

        int resolveSlownessAmplifier(int level) {
            return level >= slownessLevelThreshold ? slownessMaxAmplifier : slownessBaseAmplifier;
        }

        boolean shouldApplyWeakness(int level) {
            return level >= weaknessLevelThreshold && weaknessAmplifier >= 0;
        }
    }
}



