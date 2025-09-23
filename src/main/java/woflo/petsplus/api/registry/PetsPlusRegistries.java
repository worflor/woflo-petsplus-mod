package woflo.petsplus.api.registry;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.data.AbilityDataLoader;
import woflo.petsplus.data.PetRoleDataLoader;
import woflo.petsplus.effects.AreaEffectEffect;
import woflo.petsplus.effects.BuffEffect;
import woflo.petsplus.effects.HealOwnerFlatPctEffect;
import woflo.petsplus.effects.KnockupEffect;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.effects.MountedConeAuraEffect;
import woflo.petsplus.effects.OwnerNextAttackBonusEffect;
import woflo.petsplus.effects.PerchPotionSipReductionEffect;
import woflo.petsplus.effects.ProjectileDrForOwnerEffect;
import woflo.petsplus.effects.RetargetNearestHostileEffect;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.util.TriggerConditions;

import java.util.Objects;
import java.util.Optional;

/**
 * Central access point for all Pets+ custom registries. The registries are
 * simple Fabric-backed instances so downstream code can reference and register
 * content at common entry points.
 */
public final class PetsPlusRegistries {
    public static final RegistryKey<Registry<AbilityType>> ABILITY_TYPE_KEY =
        RegistryKey.ofRegistry(id("ability_type"));
    public static final RegistryKey<Registry<TriggerSerializer<?>>> TRIGGER_SERIALIZER_KEY =
        RegistryKey.ofRegistry(id("trigger_type"));
    public static final RegistryKey<Registry<EffectSerializer<?>>> EFFECT_SERIALIZER_KEY =
        RegistryKey.ofRegistry(id("effect_type"));
    public static final RegistryKey<Registry<PetRoleType>> PET_ROLE_TYPE_KEY =
        RegistryKey.ofRegistry(id("pet_role_type"));
    public static final RegistryKey<Registry<LevelRewardType>> LEVEL_REWARD_TYPE_KEY =
        RegistryKey.ofRegistry(id("level_reward_type"));

    public static final Registry<AbilityType> ABILITY_TYPES =
        FabricRegistryBuilder.createSimple(ABILITY_TYPE_KEY).buildAndRegister();
    public static final Registry<TriggerSerializer<?>> TRIGGER_SERIALIZERS =
        FabricRegistryBuilder.createSimple(TRIGGER_SERIALIZER_KEY).buildAndRegister();
    public static final Registry<EffectSerializer<?>> EFFECT_SERIALIZERS =
        FabricRegistryBuilder.createSimple(EFFECT_SERIALIZER_KEY).buildAndRegister();
    public static final Registry<PetRoleType> PET_ROLE_TYPES =
        FabricRegistryBuilder.createSimple(PET_ROLE_TYPE_KEY).buildAndRegister();
    public static final Registry<LevelRewardType> LEVEL_REWARD_TYPES =
        FabricRegistryBuilder.createSimple(LEVEL_REWARD_TYPE_KEY).buildAndRegister();

    private static boolean bootstrapped;

    private PetsPlusRegistries() {
    }

    /**
     * Ensures all built-in registry content is registered exactly once.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        Petsplus.LOGGER.debug("Bootstrapping Pets+ registries");
        registerPetRoles();
        registerTriggerSerializers();
        registerEffectSerializers();
        registerLevelRewardTypes();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new PetRoleDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new AbilityDataLoader());
        AbilityManager.reloadFromRegistry();
    }

    public static Registry<AbilityType> abilityTypeRegistry() {
        return ABILITY_TYPES;
    }

    public static Registry<TriggerSerializer<?>> triggerSerializerRegistry() {
        return TRIGGER_SERIALIZERS;
    }

    public static Registry<EffectSerializer<?>> effectSerializerRegistry() {
        return EFFECT_SERIALIZERS;
    }

    public static Registry<PetRoleType> petRoleTypeRegistry() {
        return PET_ROLE_TYPES;
    }

    public static Registry<LevelRewardType> levelRewardTypeRegistry() {
        return LEVEL_REWARD_TYPES;
    }

    public static AbilityType registerAbilityType(Identifier id, AbilityType type) {
        return register(ABILITY_TYPES, id, type);
    }

    public static AbilityType registerAbilityType(String path, AbilityType type) {
        return registerAbilityType(id(path), type);
    }

    public static <C> TriggerSerializer<C> registerTriggerSerializer(TriggerSerializer<C> serializer) {
        @SuppressWarnings("unchecked")
        TriggerSerializer<C> registered = (TriggerSerializer<C>) register(TRIGGER_SERIALIZERS, serializer.id(), serializer);
        return registered;
    }

    public static <C> TriggerSerializer<C> registerTriggerSerializer(TriggerSerializer.Builder<C> builder) {
        return registerTriggerSerializer(builder.build());
    }

    public static <C> EffectSerializer<C> registerEffectSerializer(EffectSerializer<C> serializer) {
        @SuppressWarnings("unchecked")
        EffectSerializer<C> registered = (EffectSerializer<C>) register(EFFECT_SERIALIZERS, serializer.id(), serializer);
        return registered;
    }

    public static <C> EffectSerializer<C> registerEffectSerializer(EffectSerializer.Builder<C> builder) {
        return registerEffectSerializer(builder.build());
    }

    public static PetRoleType registerPetRoleType(Identifier id, PetRoleType type) {
        return register(PET_ROLE_TYPES, id, type);
    }

    public static PetRoleType registerPetRoleType(String path, PetRoleType type) {
        return registerPetRoleType(id(path), type);
    }

    public static LevelRewardType registerLevelRewardType(Identifier id, LevelRewardType type) {
        return register(LEVEL_REWARD_TYPES, id, type);
    }

    public static LevelRewardType registerLevelRewardType(String path, LevelRewardType type) {
        return registerLevelRewardType(id(path), type);
    }

    private static <T> T register(Registry<T> registry, Identifier id, T value) {
        T existing = registry.get(id);
        if (existing != null) {
            Petsplus.LOGGER.warn("Attempted to re-register {} with id {}. Keeping existing entry.", value, id);
            return existing;
        }
        return Registry.register(registry, id, value);
    }

    private static void registerPetRoles() {
        registerPetRoleType(PetRoleType.GUARDIAN.id(), PetRoleType.GUARDIAN);
        registerPetRoleType(PetRoleType.STRIKER.id(), PetRoleType.STRIKER);
        registerPetRoleType(PetRoleType.SUPPORT.id(), PetRoleType.SUPPORT);
        registerPetRoleType(PetRoleType.SCOUT.id(), PetRoleType.SCOUT);
        registerPetRoleType(PetRoleType.SKYRIDER.id(), PetRoleType.SKYRIDER);
        registerPetRoleType(PetRoleType.ENCHANTMENT_BOUND.id(), PetRoleType.ENCHANTMENT_BOUND);
        registerPetRoleType(PetRoleType.CURSED_ONE.id(), PetRoleType.CURSED_ONE);
        registerPetRoleType(PetRoleType.EEPY_EEPER.id(), PetRoleType.EEPY_EEPER);
        registerPetRoleType(PetRoleType.ECLIPSED.id(), PetRoleType.ECLIPSED);
    }

    private static void registerTriggerSerializers() {
        registerTriggerSerializer(TriggerSerializer.builder(id("after_pet_redirect"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("after_pet_redirect", config.resolvedCooldown())))
            .description("Fires immediately after a pet redirects damage.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_dealt_damage"), OwnerDealtDamageConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_dealt_damage");
                private final double hpThreshold = config.targetHpPctBelow().orElse(-1.0);
                private final int cooldown = config.cooldown().resolvedCooldown();

                @Override
                public Identifier getId() {
                    return triggerId;
                }

                @Override
                public int getInternalCooldownTicks() {
                    return cooldown;
                }

                @Override
                public boolean shouldActivate(TriggerContext context) {
                    if (!Objects.equals(context.getEventType(), "owner_dealt_damage")) {
                        return false;
                    }
                    return hpThreshold < 0 || context.getVictimHpPercent() <= hpThreshold;
                }
            }))
            .description("Triggered when the owner damages a target, optionally filtered by remaining HP.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_begin_fall"), OwnerBeginFallConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_begin_fall");
                private final double minimumFall = config.minFall().orElse(0.0);
                private final int cooldown = config.cooldown().resolvedCooldown();

                @Override
                public Identifier getId() {
                    return triggerId;
                }

                @Override
                public int getInternalCooldownTicks() {
                    return cooldown;
                }

                @Override
                public boolean shouldActivate(TriggerContext context) {
                    if (!Objects.equals(context.getEventType(), "owner_begin_fall")) {
                        return false;
                    }
                    return context.getFallDistance() >= minimumFall;
                }
            }))
            .description("Owner starts falling beyond a configured height.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("interval_while_active"), IntervalWhileActiveConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("interval_while_active");
                private final int cadence = Math.max(1, config.ticks().orElse(20));
                private final boolean requirePerched = config.requirePerched().orElse(false);
                private final boolean requireMounted = config.requireMountedOwner().orElse(false);
                private final boolean requireCombat = config.requireInCombat().orElse(false);

                @Override
                public Identifier getId() {
                    return triggerId;
                }

                @Override
                public int getInternalCooldownTicks() {
                    return cadence;
                }

                @Override
                public boolean shouldActivate(TriggerContext context) {
                    if (!Objects.equals(context.getEventType(), "interval_tick")) {
                        return false;
                    }
                    if (requirePerched && !TriggerConditions.isPerched(context.getOwner())) {
                        return false;
                    }
                    if (requireMounted && !TriggerConditions.isMounted(context.getOwner())) {
                        return false;
                    }
                    return !requireCombat || TriggerConditions.isInCombat(context.getOwner());
                }
            }))
            .description("Runs on a fixed cadence while a pet remains active.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("aggro_acquired"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("aggro_acquired", config.resolvedCooldown())))
            .description("Pet gains aggro on a hostile target.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("on_combat_end"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("on_combat_end", config.resolvedCooldown())))
            .description("Triggered when the pet exits combat.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_low_health"), OwnerLowHealthConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_low_health");
                private final int cooldown = config.cooldown().resolvedCooldown();
                private final double threshold = config.ownerHpPctBelow().orElse(1.0);

                @Override
                public Identifier getId() {
                    return triggerId;
                }

                @Override
                public int getInternalCooldownTicks() {
                    return cooldown;
                }

                @Override
                public boolean shouldActivate(TriggerContext context) {
                    String event = context.getEventType();
                    if (!Objects.equals(event, "owner_low_health") && !Objects.equals(event, "on_owner_low_health")) {
                        return false;
                    }
                    Double ownerHpPct = context.getData("owner_hp_pct", Double.class);
                    return ownerHpPct == null || ownerHpPct <= threshold;
                }
            }))
            .description("Owner reaches a low health threshold.")
            .build());
    }

    private static void registerEffectSerializers() {
        registerEffectSerializer(EffectSerializer.builder(id("owner_next_attack_bonus"), OwnerNextAttackBonusConfig.CODEC,
            (abilityId, config, context) -> {
                DataResult<Effect> nestedResult = config.onHitEffect()
                    .map(json -> context.deserialize(json, "on_hit_effect"))
                    .orElse(DataResult.success(null));
                return nestedResult.map(nested ->
                    new OwnerNextAttackBonusEffect(config.bonusDamagePct().orElse(0.0),
                        config.vsTag().orElse(null), nested, config.expireTicks().orElse(100)));
            })
            .description("Applies a temporary rider to the owner's next attack.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("tag_target"), TagTargetConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new TagTargetEffect(
                config.targetKey(), config.key(), config.durationTicks())))
            .description("Applies a named tag to the current target.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("retarget_nearest_hostile"), RetargetNearestHostileConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new RetargetNearestHostileEffect(
                config.radius().orElse(8.0), config.storeAs().orElse("target"))))
            .description("Retargets nearby hostiles and stores the result in the trigger context.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("buff"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                StatusEffectInstance instance = RegistryJsonHelper.parseStatusEffect(json);
                if (instance == null) {
                    return DataResult.error(() -> "Invalid status effect definition for ability " + abilityId);
                }
                BuffEffect.Target target = RegistryJsonHelper.parseBuffTarget(
                    RegistryJsonHelper.getString(json, "target", "owner"));
                boolean onlyIfMounted = RegistryJsonHelper.getBoolean(json, "only_if_mounted", false);
                boolean onlyIfPerched = RegistryJsonHelper.getBoolean(json, "only_if_perched", false);
                return DataResult.success(new BuffEffect(target, instance, onlyIfMounted, onlyIfPerched));
            })
            .description("Applies a vanilla status effect to a configured target.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("projectile_dr_for_owner"), ProjectileDrConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ProjectileDrForOwnerEffect(
                config.percent().orElse(0.10), config.durationTicks().orElse(40))))
            .description("Grants temporary projectile damage reduction to the owner.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("perch_potion_sip_reduction"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new PerchPotionSipReductionEffect(json)))
            .description("Reduces potion consumption while perched.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("mounted_cone_aura"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MountedConeAuraEffect(json)))
            .description("Emits a buff cone while the pet is mounted.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("magnetize_drops_and_xp"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MagnetizeDropsAndXpEffect(json)))
            .description("Pulls item drops and XP orbs toward the pet.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("area_effect"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new AreaEffectEffect(json)))
            .description("Executes nested effects over an area.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("effect"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                StatusEffectInstance instance = RegistryJsonHelper.parseStatusEffect(json);
                if (instance == null) {
                    return DataResult.error(() -> "Invalid status effect definition for ability " + abilityId);
                }
                BuffEffect.Target target = RegistryJsonHelper.parseBuffTarget(
                    RegistryJsonHelper.getString(json, "target", "owner"));
                return DataResult.success(new BuffEffect(target, instance, false, false));
            })
            .description("Applies a direct status effect.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("heal_owner_flat_pct"), HealOwnerFlatPctConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new HealOwnerFlatPctEffect(
                config.value().orElse(0.15))))
            .description("Heals the owner for a flat percentage of max health.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("knockup"), KnockupConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new KnockupEffect(
                config.strength().orElse(0.35), config.target().orElse("victim"))))
            .description("Applies a vertical knockback impulse.")
            .build());
    }

    private static void registerLevelRewardTypes() {
        registerLevelRewardType("ability_unlock",
            new LevelRewardType(id("ability_unlock"), "Unlocks a new ability at a milestone level."));
    }

    private static Trigger simpleEventTrigger(String eventType, int cooldown) {
        Identifier triggerId = id(eventType);
        return new Trigger() {
            @Override
            public Identifier getId() {
                return triggerId;
            }

            @Override
            public int getInternalCooldownTicks() {
                return cooldown;
            }

            @Override
            public boolean shouldActivate(TriggerContext context) {
                return Objects.equals(context.getEventType(), eventType);
            }
        };
    }

    private static Identifier id(String path) {
        return Identifier.of(Petsplus.MOD_ID, path);
    }

    private record CooldownSettings(Optional<Integer> cooldownTicks, Optional<Integer> internalCooldownTicks) {
        static final com.mojang.serialization.MapCodec<CooldownSettings> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("cooldown_ticks").forGetter(CooldownSettings::cooldownTicks),
                Codec.INT.optionalFieldOf("internal_cd_ticks").forGetter(CooldownSettings::internalCooldownTicks)
            ).apply(instance, CooldownSettings::new));
        static final Codec<CooldownSettings> CODEC = MAP_CODEC.codec();

        int resolvedCooldown() {
            return cooldownTicks.orElse(internalCooldownTicks.orElse(0));
        }
    }

    private record OwnerDealtDamageConfig(Optional<Double> targetHpPctBelow, CooldownSettings cooldown) {
        static final Codec<OwnerDealtDamageConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("target_hp_pct_below").forGetter(OwnerDealtDamageConfig::targetHpPctBelow),
            CooldownSettings.MAP_CODEC.forGetter(OwnerDealtDamageConfig::cooldown)
        ).apply(instance, OwnerDealtDamageConfig::new));
    }

    private record OwnerBeginFallConfig(Optional<Double> minFall, CooldownSettings cooldown) {
        static final Codec<OwnerBeginFallConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("min_fall").forGetter(OwnerBeginFallConfig::minFall),
            CooldownSettings.MAP_CODEC.forGetter(OwnerBeginFallConfig::cooldown)
        ).apply(instance, OwnerBeginFallConfig::new));
    }

    private record IntervalWhileActiveConfig(Optional<Integer> ticks, Optional<Boolean> requirePerched,
                                             Optional<Boolean> requireMountedOwner, Optional<Boolean> requireInCombat) {
        static final Codec<IntervalWhileActiveConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("ticks").forGetter(IntervalWhileActiveConfig::ticks),
            Codec.BOOL.optionalFieldOf("require_perched").forGetter(IntervalWhileActiveConfig::requirePerched),
            Codec.BOOL.optionalFieldOf("require_mounted_owner").forGetter(IntervalWhileActiveConfig::requireMountedOwner),
            Codec.BOOL.optionalFieldOf("require_in_combat").forGetter(IntervalWhileActiveConfig::requireInCombat)
        ).apply(instance, IntervalWhileActiveConfig::new));
    }

    private record OwnerLowHealthConfig(Optional<Double> ownerHpPctBelow, CooldownSettings cooldown) {
        static final Codec<OwnerLowHealthConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("owner_hp_pct_below").forGetter(OwnerLowHealthConfig::ownerHpPctBelow),
            CooldownSettings.MAP_CODEC.forGetter(OwnerLowHealthConfig::cooldown)
        ).apply(instance, OwnerLowHealthConfig::new));
    }

    private record OwnerNextAttackBonusConfig(Optional<Double> bonusDamagePct, Optional<String> vsTag,
                                               Optional<Integer> expireTicks, Optional<JsonObject> onHitEffect) {
        static final Codec<OwnerNextAttackBonusConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("bonus_damage_pct").forGetter(OwnerNextAttackBonusConfig::bonusDamagePct),
            Codec.STRING.optionalFieldOf("vs_tag").forGetter(OwnerNextAttackBonusConfig::vsTag),
            Codec.INT.optionalFieldOf("expire_ticks").forGetter(OwnerNextAttackBonusConfig::expireTicks),
            RegistryJsonHelper.JSON_OBJECT_CODEC.optionalFieldOf("on_hit_effect").forGetter(OwnerNextAttackBonusConfig::onHitEffect)
        ).apply(instance, OwnerNextAttackBonusConfig::new));

    }

    private record TagTargetConfig(String targetKey, String key, int durationTicks) {
        static final Codec<TagTargetConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "target").forGetter(TagTargetConfig::targetKey),
            Codec.STRING.optionalFieldOf("key", "").forGetter(TagTargetConfig::key),
            Codec.INT.optionalFieldOf("duration_ticks", 80).forGetter(TagTargetConfig::durationTicks)
        ).apply(instance, TagTargetConfig::new));
    }

    private record RetargetNearestHostileConfig(Optional<Double> radius, Optional<String> storeAs) {
        static final Codec<RetargetNearestHostileConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius").forGetter(RetargetNearestHostileConfig::radius),
            Codec.STRING.optionalFieldOf("store_as").forGetter(RetargetNearestHostileConfig::storeAs)
        ).apply(instance, RetargetNearestHostileConfig::new));

    }

    private record ProjectileDrConfig(Optional<Double> percent, Optional<Integer> durationTicks) {
        static final Codec<ProjectileDrConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("percent").forGetter(ProjectileDrConfig::percent),
            Codec.INT.optionalFieldOf("duration_ticks").forGetter(ProjectileDrConfig::durationTicks)
        ).apply(instance, ProjectileDrConfig::new));

    }

    private record HealOwnerFlatPctConfig(Optional<Double> value) {
        static final Codec<HealOwnerFlatPctConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("value").forGetter(HealOwnerFlatPctConfig::value)
        ).apply(instance, HealOwnerFlatPctConfig::new));

    }

    private record KnockupConfig(Optional<Double> strength, Optional<String> target) {
        static final Codec<KnockupConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("strength").forGetter(KnockupConfig::strength),
            Codec.STRING.optionalFieldOf("target").forGetter(KnockupConfig::target)
        ).apply(instance, KnockupConfig::new));

    }
}
