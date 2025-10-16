package woflo.petsplus.api.registry;

import com.google.gson.JsonArray;
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
import woflo.petsplus.util.CodecUtils;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.ai.planner.ActionPlanDataLoader;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleDataLoader;
import woflo.petsplus.ai.goals.loader.GoalDataLoader;
import woflo.petsplus.ai.goals.special.survey.SurveyTargetDataLoader;
import woflo.petsplus.data.AbilityDataLoader;
import woflo.petsplus.data.AstrologySignDataLoader;
import woflo.petsplus.data.NatureFlavorDataLoader;
import woflo.petsplus.data.NatureTabooDataLoader;
import woflo.petsplus.data.PetRoleDataLoader;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.AreaEffectEffect;
import woflo.petsplus.effects.ApplyPotionToSelfEffect;
import woflo.petsplus.effects.ApplyPotionToVictimEffect;
import woflo.petsplus.effects.BuffEffect;
import woflo.petsplus.effects.ClearPetStateDataEffect;
import woflo.petsplus.effects.CursedOneDeathBurstEffect;
import woflo.petsplus.effects.CursedOneMountResilienceEffect;
import woflo.petsplus.effects.CursedOneSacrificialRescueEffect;
import woflo.petsplus.effects.ParticleEffect;
import woflo.petsplus.effects.SoundEffect;
import woflo.petsplus.effects.CursedOneReanimationEffect;
import woflo.petsplus.effects.CursedOneSoulSacrificeEffect;
import woflo.petsplus.effects.ClearOwnerStateDataEffect;
import woflo.petsplus.effects.DarknessDamageShieldEffect;
import woflo.petsplus.effects.EclipsedVoidRescueEffect;
import woflo.petsplus.effects.EclipsedVoidStrikeEffect;
import woflo.petsplus.effects.EclipsedEventHorizonEffect;
import woflo.petsplus.effects.EclipsedPhasePartnerEffect;
import woflo.petsplus.effects.EclipsedEdgeStepEffect;
import woflo.petsplus.effects.DreamEscapeRescueEffect;
import woflo.petsplus.effects.EnchantmentBoundPerchedHasteEffect;
import woflo.petsplus.effects.EnchantStripEffect;
import woflo.petsplus.effects.EepyDrowsyMistEffect;
import woflo.petsplus.effects.EepyNapAuraEffect;
import woflo.petsplus.effects.EepyRestfulDreamsEffect;
import woflo.petsplus.effects.EnchantmentBoundArcaneFocusEffect;
import woflo.petsplus.effects.EnchantmentBoundDurabilityEffect;
import woflo.petsplus.effects.EnchantmentBoundExtraDropsEffect;
import woflo.petsplus.effects.EnchantmentBoundMiningHasteEffect;
import woflo.petsplus.effects.EnchantmentBoundSwimGraceEffect;
import woflo.petsplus.effects.EnchantmentResonanceArcEffect;
import woflo.petsplus.effects.GearSwapEffect;
import woflo.petsplus.effects.GuardianAegisProtocolEffect;
import woflo.petsplus.effects.GuardianBulwarkRedirectEffect;
import woflo.petsplus.effects.GuardianFortressBondEffect;
import woflo.petsplus.effects.GuardianFortressBondPetDrEffect;
import woflo.petsplus.effects.HealOwnerFlatPctEffect;
import woflo.petsplus.effects.KnockupEffect;
import woflo.petsplus.effects.MagicDamageShieldEffect;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.effects.OpenEnderChestEffect;
import woflo.petsplus.effects.OpenPetBackpackEffect;
import woflo.petsplus.effects.OwnerNextAttackBonusEffect;
import woflo.petsplus.effects.PerchPotionSipReductionEffect;
import woflo.petsplus.effects.ProjectileDrForOwnerEffect;
import woflo.petsplus.effects.RetargetNearestHostileEffect;
import woflo.petsplus.effects.ScoutEnhancedMarkEffect;
import woflo.petsplus.effects.ScoutSpotterFallbackEffect;
import woflo.petsplus.effects.SetOwnerStateDataEffect;
import woflo.petsplus.effects.SetPetStateDataEffect;
import woflo.petsplus.effects.SkyriderFallGuardEffect;
import woflo.petsplus.effects.SkyriderGustUpwardsEffect;
import woflo.petsplus.effects.SkyriderProjectileLevitationEffect;
import woflo.petsplus.effects.SkyriderWindlashEffect;
import woflo.petsplus.effects.StrikerBloodlustSurgeEffect;
import woflo.petsplus.effects.StrikerExecutionEffect;
import woflo.petsplus.effects.StrikerMarkFeedbackEffect;
import woflo.petsplus.effects.StrikerPetExecutionEffect;
import woflo.petsplus.effects.SupportPotionPulseEffect;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.util.TriggerConditions;

import java.util.ArrayList;
import java.util.List;
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
        registerAbilityPlaceholders();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new PetRoleDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new AbilityDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new AstrologySignDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new NatureFlavorDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new NatureTabooDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new GoalDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new ActionPlanDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new SignalRuleDataLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new SurveyTargetDataLoader());
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
        T registered = Registry.register(registry, id, value);
        if (registry == PET_ROLE_TYPES) {
            PetsPlusConfig.onRoleRegistered(id);
        }
        return registered;
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

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_killed_entity"), OwnerKilledEntityConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_killed_entity");
                private final boolean requireExecution = config.requireExecution().orElse(false);
                private final boolean requireFinisher = config.requireFinisherMark().orElse(false);
                private final double thresholdMax = config.executionThresholdAtMost().orElse(1.0);
                private final double thresholdMin = config.executionThresholdAtLeast().orElse(0.0);
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
                    if (!Objects.equals(context.getEventType(), "owner_killed_entity")) {
                        return false;
                    }
                    if (requireExecution && !context.wasExecutionKill()) {
                        return false;
                    }
                    if (requireFinisher && !context.targetHadFinisherMark()) {
                        return false;
                    }
                    double threshold = context.getExecutionThresholdPercent();
                    if (thresholdMax < 1.0 && threshold > thresholdMax + 1.0e-5) {
                        return false;
                    }
                    if (thresholdMin > 0.0 && threshold + 1.0e-5 < thresholdMin) {
                        return false;
                    }
                    return true;
                }
            }))
            .description("Triggered after the owner (or their pet) kills an entity, with optional execution filters.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("loot_table_modify"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("loot_table_modify", config.resolvedCooldown())))
            .description("Runs while owner-related loot drops are being processed, enabling drop modification effects.")
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

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_took_fall_damage"), OwnerTookFallDamageConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_took_fall_damage");
                private final double minimumDamage = config.minDamage().orElse(0.0);
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
                    if (!Objects.equals(context.getEventType(), "owner_took_fall_damage")) {
                        return false;
                    }
                    double damage = context.getDamage();
                    return damage >= minimumDamage;
                }
            }))
            .description("Owner takes fall damage that meets an optional damage threshold.")
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

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_shot_projectile"), OwnerShotProjectileConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_shot_projectile");
                private final String projectileFilter = config.projectileType()
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                    .orElse(null);
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
                    if (!Objects.equals(context.getEventType(), "owner_shot_projectile")) {
                        return false;
                    }
                    if (projectileFilter == null) {
                        return true;
                    }
                    String projectileType = context.getData("projectile_type", String.class);
                    if (projectileType == null) {
                        projectileType = context.getData("projectile_id", String.class);
                    }
                    if (projectileType == null) {
                        Identifier identifier = context.getData("projectile_identifier", Identifier.class);
                        projectileType = identifier != null ? identifier.toString() : null;
                    }
                    if (projectileType == null) {
                        projectileType = context.getData("projectile_type_no_namespace", String.class);
                        if (projectileType != null) {
                            projectileType = projectileType.toLowerCase(java.util.Locale.ROOT);
                            return projectileFilter.equals(projectileType);
                        }
                        return false;
                    }
                    projectileType = projectileType.toLowerCase(java.util.Locale.ROOT);
                    if (projectileFilter.equals(projectileType)) {
                        return true;
                    }
                    int colonIndex = projectileType.indexOf(':');
                    if (colonIndex >= 0 && colonIndex + 1 < projectileType.length()) {
                        String withoutNamespace = projectileType.substring(colonIndex + 1);
                        if (projectileFilter.equals(withoutNamespace)) {
                            return true;
                        }
                    }
                    return false;
                }
            }))
            .description("Owner fires a projectile, optionally filtered by projectile type.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("aggro_acquired"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("aggro_acquired", config.resolvedCooldown())))
            .description("Pet gains aggro on a hostile target.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("on_combat_end"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("on_combat_end", config.resolvedCooldown())))
            .description("Triggered when the pet exits combat.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_signal_double_crouch"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_signal_double_crouch", config.resolvedCooldown())))
            .description("Fires when the owner performs the double crouch manual trigger on their pet.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_signal_proximity_channel"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_signal_proximity_channel", config.resolvedCooldown())))
            .description("Fires when the owner completes the crouch-and-hold proximity channel with their pet.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_signal_shift_interact"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_signal_shift_interact", config.resolvedCooldown())))
            .description("Fires when the owner sneaks and interacts directly with their pet.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_sleep_complete"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_sleep_complete", config.resolvedCooldown())))
            .description("Owner completes a full sleep cycle or equivalent rest event.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_respawn"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_respawn", config.resolvedCooldown())))
            .description("Owner respawns after death or via a respawn anchor.")
            .build());

        Identifier ownerBrokeBlockId = id("owner_broke_block");
        registerTriggerSerializer(TriggerSerializer.builder(ownerBrokeBlockId, OwnerBrokeBlockConfig.CODEC,
            (abilityId, config) -> DataResult.success(OwnerBrokeBlockTriggerFactory.create(ownerBrokeBlockId,
                config.blockValuable(), config.blockId(), config.cooldown().resolvedCooldown())))
            .description("Owner breaks a block, optionally requiring it to be marked valuable.")
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

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_lethal_damage"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_lethal_damage");
                private final int cooldown = config.resolvedCooldown();

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
                    if (!Objects.equals(context.getEventType(), "owner_lethal_damage")) {
                        return false;
                    }
                    return context.hasDamageContext() && context.isLethalDamage();
                }
            }))
            .description("Fires immediately before lethal owner damage is applied, enabling interception abilities.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_outgoing_damage"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_outgoing_damage");
                private final int cooldown = config.resolvedCooldown();

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
                    return context.hasDamageContext()
                        && Objects.equals(context.getEventType(), "owner_outgoing_damage");
                }
            }))
            .description("Runs before owner damage is applied, enabling executions to modify the hit.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_incoming_damage"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("owner_incoming_damage");
                private final int cooldown = config.resolvedCooldown();

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
                    return context.hasDamageContext()
                        && Objects.equals(context.getEventType(), "owner_incoming_damage");
                }
            }))
            .description("Runs whenever the owner is about to take damage, before lethal checks.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_incoming_damage"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_incoming_damage");
                private final int cooldown = config.resolvedCooldown();

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
                    return context.hasDamageContext()
                        && Objects.equals(context.getEventType(), "pet_incoming_damage");
                }
            }))
            .description("Runs whenever a pet is about to take damage, before lethal checks.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_lethal_damage"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_lethal_damage");
                private final int cooldown = config.resolvedCooldown();

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
                    return context.hasDamageContext()
                        && context.isLethalDamage()
                        && Objects.equals(context.getEventType(), "pet_lethal_damage");
                }
            }))
            .description("Fires immediately before lethal pet damage is applied, enabling interception abilities.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_dealt_damage"), OwnerDealtDamageConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_dealt_damage");
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
                    if (!Objects.equals(context.getEventType(), "pet_dealt_damage")) {
                        return false;
                    }
                    return hpThreshold < 0 || context.getVictimHpPercent() <= hpThreshold;
                }
            }))
            .description("Triggered when the pet damages a target, optionally filtered by remaining HP.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_outgoing_damage"), OwnerDealtDamageConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_outgoing_damage");
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
                    if (!Objects.equals(context.getEventType(), "pet_outgoing_damage")) {
                        return false;
                    }
                    return hpThreshold < 0 || context.getVictimHpPercent() <= hpThreshold;
                }
            }))
            .description("Triggered before the pet applies damage, allowing damage modification. Optionally filtered by target HP.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_killed_entity"), OwnerKilledEntityConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_killed_entity");
                private final boolean requireExecution = config.requireExecution().orElse(false);
                private final boolean requireFinisher = config.requireFinisherMark().orElse(false);
                private final double thresholdMax = config.executionThresholdAtMost().orElse(1.0);
                private final double thresholdMin = config.executionThresholdAtLeast().orElse(0.0);
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
                    if (!Objects.equals(context.getEventType(), "pet_killed_entity")) {
                        return false;
                    }
                    if (requireExecution && !context.wasExecutionKill()) {
                        return false;
                    }
                    if (requireFinisher && !context.targetHadFinisherMark()) {
                        return false;
                    }
                    double threshold = context.getExecutionThresholdPercent();
                    if (thresholdMax < 1.0 && threshold > thresholdMax + 1.0e-5) {
                        return false;
                    }
                    if (thresholdMin > 0.0 && threshold + 1.0e-5 < thresholdMin) {
                        return false;
                    }
                    return true;
                }
            }))
            .description("Triggered after a pet kills an entity, with optional execution filters.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("pet_low_health"), OwnerLowHealthConfig.CODEC,
            (abilityId, config) -> DataResult.success(new Trigger() {
                private final Identifier triggerId = id("pet_low_health");
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
                    if (!Objects.equals(event, "pet_low_health") && !Objects.equals(event, "on_pet_low_health")) {
                        return false;
                    }
                    Double petHpPct = context.getData("pet_hp_pct", Double.class);
                    return petHpPct == null || petHpPct <= threshold;
                }
            }))
            .description("Pet reaches a low health threshold.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_mounted_pet"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_mounted_pet", config.resolvedCooldown())))
            .description("Fires when the owner mounts or shoulder-perches a pet.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("owner_dismounted_pet"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("owner_dismounted_pet", config.resolvedCooldown())))
            .description("Fires when the owner dismounts or releases a shoulder-perched pet.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("on_pet_resurrect"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("on_pet_resurrect", config.resolvedCooldown())))
            .description("Triggered when a pet is resurrected.")
            .build());

        registerTriggerSerializer(TriggerSerializer.builder(id("on_pet_death"), CooldownSettings.CODEC,
            (abilityId, config) -> DataResult.success(simpleEventTrigger("on_pet_death", config.resolvedCooldown())))
            .description("Triggered when a pet dies permanently.")
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

        registerEffectSerializer(EffectSerializer.builder(id("striker_mark_feedback"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new StrikerMarkFeedbackEffect(json)))
            .description("Sends Striker mark UI and particle feedback when a finisher tag lands.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("striker_execution"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new StrikerExecutionEffect(json)))
            .description("Calculates and applies the Striker execution damage bonus before hits resolve.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("striker_pet_execution"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new StrikerPetExecutionEffect(json)))
            .description("Pet execution effect - allows striker pets to execute marked low-health targets.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("striker_bloodlust_surge"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new StrikerBloodlustSurgeEffect(json)))
            .description("Applies execution-fueled speed and strength buffs for Striker bloodlust.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_resonance_arc"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentResonanceArcEffect(json)))
            .description("Creates enchantment arcs to nearby enemies during resonance windows. Perfect timing deals more damage and refunds cooldown.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("set_owner_state_data"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SetOwnerStateDataEffect(json)))
            .description("Sets temporary state data on the owner for timing windows and synergy tracking.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("clear_owner_state_data"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new ClearOwnerStateDataEffect(json)))
            .description("Clears state data from the owner.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("cursed_one_reanimation"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new CursedOneReanimationEffect(json)))
            .description("Places a Cursed One pet into its reanimation state when lethal damage is intercepted.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("darkness_damage_shield"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new DarknessDamageShieldEffect(json)))
            .description("Cancels small hits while the Eclipsed duo remains in darkness.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eclipsed_void_strike"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EclipsedVoidStrikeEffect(json)))
            .description("Applies bonus damage to Eclipsed pet attacks when striking in darkness.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("magic_damage_shield"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MagicDamageShieldEffect(json)))
            .description("Cancels minor magical damage for Enchantment-Bound pets.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("skyrider_gust_upwards"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SkyriderGustUpwardsEffect(json)))
            .description("Launches the owner upward with Skyrider gust slow-fall assistance.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("skyrider_windlash"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SkyriderWindlashEffect(json)))
            .description("Primes the owner with a jump surge and attack rider after a dramatic fall start.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("skyrider_projectile_levitation"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SkyriderProjectileLevitationEffect(json)))
            .description("Applies the Skyrider projectile levitation pulse and supportive slow-fall effects.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("skyrider_fall_guard"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SkyriderFallGuardEffect(json)))
            .description("Cancels or reduces fall impacts for Skyrider pairs and their mounts.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("scout_spotter_fallback"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new ScoutSpotterFallbackEffect(json)))
            .description("Applies the Scout spotter fallback glow after a lull in pet attacks.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("apply_potion_to_victim"), ApplyPotionToVictimConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ApplyPotionToVictimEffect(
                config.effect(), config.duration(), config.amplifier())))
            .description("Applies a potion effect to the victim entity.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("apply_potion_to_self"), ApplyPotionToSelfConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ApplyPotionToSelfEffect(
                config.effect(), config.duration(), config.amplifier())))
            .description("Applies a potion effect to the pet itself.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("set_pet_state_data"), SetPetStateDataConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new SetPetStateDataEffect(
                config.key(), config.durationTicks())))
            .description("Sets temporary state data on the pet with an expiration time.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("clear_pet_state_data"), ClearPetStateDataConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ClearPetStateDataEffect(config.key())))
            .description("Clears temporary state data from the pet.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("scout_enhanced_mark"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new ScoutEnhancedMarkEffect()))
            .description("Scout: Applies enhanced mark if pet has scout_enhanced_mark state.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("particle_effect"), ParticleEffectConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ParticleEffect(
                config.particle(), config.target(), config.count(),
                config.offsetX(), config.offsetY(), config.offsetZ(),
                config.speed(), config.heightOffset())))
            .description("Spawns particles at an entity location for visual feedback.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("sound_effect"), SoundEffectConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new SoundEffect(
                config.sound(), config.target(), config.volume(), config.pitch(), config.category())))
            .description("Plays a sound at an entity location for audio feedback.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("tag_target"), TagTargetConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new TagTargetEffect(
                config.targetKey(), config.key(), config.durationTicks())))
            .description("Applies a named tag to the current target.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("retarget_nearest_hostile"), RetargetNearestHostileConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new RetargetNearestHostileEffect(
                config.radius().orElse(8.0),
                config.storeAs().orElse("target"),
                config.requireDataFlag().orElse(null),
                config.requireDataValue().orElse(Boolean.TRUE))))
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
                String requireFlag = RegistryJsonHelper.getString(json, "require_data_flag", null);
                boolean requireValue = RegistryJsonHelper.getBoolean(json, "require_data_value", true);
                return DataResult.success(new BuffEffect(target, instance, onlyIfMounted, onlyIfPerched, requireFlag, requireValue));
            })
            .description("Applies a vanilla status effect to a configured target.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("guardian_bulwark_redirect"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new GuardianBulwarkRedirectEffect(json)))
            .description("Redirects incoming owner damage through nearby Guardian protectors before applying follow-up buffs.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("guardian_fortress_bond"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                double reduction = RegistryJsonHelper.getDouble(json, "reduction_pct", 0.5);
                int duration = RegistryJsonHelper.getInt(json, "duration_ticks", 200);
                return DataResult.success(new GuardianFortressBondEffect(reduction, duration));
            })
            .description("Activates the Guardian fortress bond barrier between owner and pet.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("guardian_fortress_bond_pet_dr"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new GuardianFortressBondPetDrEffect(json)))
            .description("Reduces bonded guardian damage through the pet incoming damage trigger.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("guardian_aegis_protocol"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                int maxStacks = RegistryJsonHelper.getInt(json, "max_stacks", 3);
                int duration = RegistryJsonHelper.getInt(json, "duration_ticks", 200);
                return DataResult.success(new GuardianAegisProtocolEffect(maxStacks, duration));
            })
            .description("Applies Guardian Aegis Protocol defense stacks after a redirect.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchant_strip"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                int xpCost = RegistryJsonHelper.getInt(json, "xp_cost_levels", 3);
                boolean preferMainHand = RegistryJsonHelper.getBoolean(json, "prefer_mainhand", true);
                boolean allowOffhand = RegistryJsonHelper.getBoolean(json, "allow_offhand", true);
                boolean dropBook = RegistryJsonHelper.getBoolean(json, "drop_as_book", true);
                return DataResult.success(new EnchantStripEffect(xpCost, preferMainHand, allowOffhand, dropBook));
            })
            .description("Removes the strongest enchantment from the owner's held item for a level cost.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("gear_swap"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                String storeSoundId = RegistryJsonHelper.getString(json, "store_sound", "minecraft:item.armor.equip_chain");
                String swapSoundId = RegistryJsonHelper.getString(json, "swap_sound", "minecraft:item.armor.equip_diamond");
                return DataResult.success(GearSwapEffect.fromConfig(storeSoundId, swapSoundId));
            })
            .description("Swaps the owner's stored gear loadouts through their Enchantment-Bound pet.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_mining_haste"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundMiningHasteEffect(json)))
            .description("Applies the Enchantment-Bound mining haste pulse when the owner breaks blocks.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_durability_refund"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundDurabilityEffect(json)))
            .description("Provides the Enchantment-Bound durability refund echo for owner tools.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_arcane_focus"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundArcaneFocusEffect(json)))
            .description("Activates Arcane Focus surges for Enchantment-Bound owners.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_extra_drops"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundExtraDropsEffect(json)))
            .description("Duplicates drops for Enchantment-Bound mining and combat echoes.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_swim_grace"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundSwimGraceEffect(json)))
            .description("Applies the Enchantment-Bound swim grace aura to the owner.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("cursed_one_death_burst"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                double radius = RegistryJsonHelper.getDouble(json, "radius", 6.0);
                double damage = RegistryJsonHelper.getDouble(json, "damage", 14.0);
                boolean ignite = RegistryJsonHelper.getBoolean(json, "ignite", true);
                JsonObject reanimation = RegistryJsonHelper.getObject(json, "reanimation");
                double reanimationRadiusScale = reanimation != null
                    ? RegistryJsonHelper.getDouble(reanimation, "radius_scale", 0.6)
                    : 0.6;
                double reanimationDamageScale = reanimation != null
                    ? RegistryJsonHelper.getDouble(reanimation, "damage_scale", 0.5)
                    : 0.5;
                boolean reanimationIgnite = reanimation != null
                    ? RegistryJsonHelper.getBoolean(reanimation, "ignite", false)
                    : false;
                double reanimationEffectScale = reanimation != null
                    ? RegistryJsonHelper.getDouble(reanimation, "effect_duration_scale", 0.5)
                    : 0.5;
                JsonArray enemyEffectsJson = RegistryJsonHelper.getArray(json, "enemy_effects");
                List<StatusEffectInstance> enemyEffects = new ArrayList<>();
                if (enemyEffectsJson != null) {
                    for (int i = 0; i < enemyEffectsJson.size(); i++) {
                        if (!enemyEffectsJson.get(i).isJsonObject()) {
                            continue;
                        }
                        StatusEffectInstance effect = RegistryJsonHelper.parseStatusEffect(enemyEffectsJson.get(i).getAsJsonObject());
                        if (effect != null) {
                            enemyEffects.add(effect);
                        }
                    }
                }
                return DataResult.success(new CursedOneDeathBurstEffect(radius, damage, ignite, enemyEffects,
                    reanimationRadiusScale, reanimationDamageScale, reanimationIgnite, reanimationEffectScale));
            })
            .description("Detonates a cursed explosion when the pet dies.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("cursed_one_soul_sacrifice"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                int xpCost = RegistryJsonHelper.getInt(json, "xp_cost_levels", 4);
                int duration = RegistryJsonHelper.getInt(json, "duration_ticks", 600);
                double multiplier = RegistryJsonHelper.getDouble(json, "reanimation_multiplier", 2.0);
                double healPct = RegistryJsonHelper.getDouble(json, "owner_heal_pct", 0.25);
                JsonArray ownerEffectsJson = RegistryJsonHelper.getArray(json, "owner_effects");
                List<StatusEffectInstance> ownerEffects = new ArrayList<>();
                if (ownerEffectsJson != null) {
                    for (int i = 0; i < ownerEffectsJson.size(); i++) {
                        if (!ownerEffectsJson.get(i).isJsonObject()) {
                            continue;
                        }
                        StatusEffectInstance effect = RegistryJsonHelper.parseStatusEffect(ownerEffectsJson.get(i).getAsJsonObject());
                        if (effect != null) {
                            ownerEffects.add(effect);
                        }
                    }
                }
                return DataResult.success(new CursedOneSoulSacrificeEffect(xpCost, duration, multiplier, healPct, ownerEffects));
            })
            .description("Channels soul sacrifice to trade XP for power and longer reanimation downtime.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("cursed_one_sacrificial_rescue"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new CursedOneSacrificialRescueEffect(json)))
            .description("Allows a Cursed One pet to sacrifice itself to save the owner from lethal damage.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("cursed_mount_resilience"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new CursedOneMountResilienceEffect(json)))
            .description("Applies a brief resistance buff to the owner's mount after a nearby Cursed One rescues them.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("projectile_dr_for_owner"), ProjectileDrConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ProjectileDrForOwnerEffect(
                config.percent().orElse(0.10),
                config.durationTicks().orElse(40),
                config.requireDataFlag().orElse(null),
                config.requireDataValue().orElse(Boolean.TRUE))))
            .description("Grants temporary projectile damage reduction to the owner.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("perch_potion_sip_reduction"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new PerchPotionSipReductionEffect(json)))
            .description("Reduces potion consumption while perched.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("enchantment_perched_haste"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EnchantmentBoundPerchedHasteEffect(json)))
            .description("Applies the Enchantment-Bound perched haste aura when the pet remains perched.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("support_potion_pulse"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SupportPotionPulseEffect(json)))
            .description("Shares the Support pet's stored potion energy on demand.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("magnetize_drops_and_xp"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MagnetizeDropsAndXpEffect(json)))
            .description("Pulls item drops and XP orbs toward the pet.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eepy_dream_escape_rescue"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new DreamEscapeRescueEffect(json)))
            .description("Intercepts lethal owner damage to perform the Dream's Escape rescue.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eclipsed_void_rescue"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EclipsedVoidRescueEffect(json)))
            .description("Pulls the owner from the void when an Eclipsed pet intercepts lethal damage.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eepy_drowsy_mist"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EepyDrowsyMistEffect(json)))
            .description("Blankets nearby hostiles in a lingering, sleep-inducing mist.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("open_ender_chest"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new OpenEnderChestEffect()))
            .description("Opens the owner's ender chest.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("open_pet_backpack"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new OpenPetBackpackEffect()))
            .description("Opens the pet's persistent backpack inventory.")
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
            (abilityId, config, context) -> {
                double percent = config.pctAmount().orElseGet(() -> config.value().orElse(0.15));
                double flat = config.flatAmount().orElse(0.0);
                return DataResult.success(new HealOwnerFlatPctEffect(flat, percent));
            })
            .description("Heals the owner by a flat amount plus a percent of max health.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("knockup"), KnockupConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new KnockupEffect(
                config.strength().orElse(0.35), config.target().orElse("victim"))))
            .description("Applies a vertical knockback impulse.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eepy_nap_aura"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EepyNapAuraEffect(json)))
            .description("Applies the Eepy nap-time regeneration aura around a sitting companion.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eepy_restful_dreams"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EepyRestfulDreamsEffect(json)))
            .description("Distributes Restful Dreams healing and buffs after the owner completes a sleep cycle.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eclipsed_event_horizon"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EclipsedEventHorizonEffect(json)))
            .description("Spawns the Event Horizon zone and grants the owner eclipse protections.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eclipsed_phase_partner"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EclipsedPhasePartnerEffect(json)))
            .description("Marks a nearby hostile for the Eclipsed phase partner tether.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("eclipsed_edge_step"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new EclipsedEdgeStepEffect(json)))
            .description("Reduces fall damage for the owner when an Eclipsed pet cushions the landing.")
            .build());
    }

    private static void registerLevelRewardTypes() {
        registerLevelRewardType("ability_unlock",
            new LevelRewardType(id("ability_unlock"), "Unlocks a new ability at a milestone level."));
    }

    /**
     * Scans ability JSON files and registers placeholder ability types before registry freeze.
     * Uses Fabric's mod container API - works in dev, production, and datapack environments.
     */
    private static void registerAbilityPlaceholders() {
        try {
            net.fabricmc.loader.api.FabricLoader fabricLoader = net.fabricmc.loader.api.FabricLoader.getInstance();
            var modContainer = fabricLoader.getModContainer(Petsplus.MOD_ID);
            
            if (modContainer.isEmpty()) {
                Petsplus.LOGGER.error("Could not find mod container for {}. Ability placeholders will not be registered.", Petsplus.MOD_ID);
                return;
            }
            
            var mod = modContainer.get();
            var abilitiesPathOptional = mod.findPath("data/petsplus/abilities");
            
            if (abilitiesPathOptional.isEmpty()) {
                Petsplus.LOGGER.warn("Abilities directory not found in mod resources. Ensure abilities are packaged correctly.");
                return;
            }
            
            java.nio.file.Path abilitiesPath = abilitiesPathOptional.get();
            int registeredCount = 0;
            
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(abilitiesPath)) {
                var jsonFiles = paths.filter(java.nio.file.Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".json"))
                                    .toList();
                
                for (java.nio.file.Path path : jsonFiles) {
                    try {
                        String content = java.nio.file.Files.readString(path);
                        if (registerPlaceholderFromJson(content, path.getFileName().toString())) {
                            registeredCount++;
                        }
                    } catch (Exception e) {
                        Petsplus.LOGGER.warn("Failed to pre-scan ability file {}: {}", path.getFileName(), e.getMessage());
                    }
                }
                
                Petsplus.LOGGER.info("Registered {} ability placeholders from bundled resources", registeredCount);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to scan abilities directory for placeholder registration", e);
        }
    }

    private static boolean registerPlaceholderFromJson(String content, String fileName) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            
            if (!json.has("id")) {
                Petsplus.LOGGER.warn("Ability file {} missing 'id' field, skipping placeholder registration", fileName);
                return false;
            }
            
            String idStr = json.get("id").getAsString();
            Identifier abilityId = Identifier.tryParse(idStr);
            
            if (abilityId == null) {
                Petsplus.LOGGER.warn("Invalid ability ID '{}' in file {}, skipping", idStr, fileName);
                return false;
            }
            
            if (ABILITY_TYPES.get(abilityId) != null) {
                Petsplus.LOGGER.debug("Ability {} already registered, skipping placeholder", abilityId);
                return false;
            }
            
            registerAbilityType(abilityId, createPlaceholderAbilityType(abilityId));
            Petsplus.LOGGER.debug("Registered placeholder for ability {}", abilityId);
            return true;
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to parse ability JSON from {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    private static AbilityType createPlaceholderAbilityType(Identifier id) {
        return new AbilityType(id, () -> createPlaceholderAbility(id), "Auto-registered from JSON");
    }

    private static woflo.petsplus.api.Ability createPlaceholderAbility(Identifier abilityId) {
        // Placeholder ability that does nothing until replaced by JSON data
        Trigger placeholderTrigger = new Trigger() {
            @Override
            public Identifier getId() {
                return abilityId;
            }

            @Override
            public int getInternalCooldownTicks() {
                return 0;
            }

            @Override
            public boolean shouldActivate(TriggerContext context) {
                return false;
            }
        };
        
        return new woflo.petsplus.api.Ability(abilityId, placeholderTrigger, java.util.List.of(), new com.google.gson.JsonObject());
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

    private record OwnerKilledEntityConfig(Optional<Boolean> requireExecution,
                                           Optional<Boolean> requireFinisherMark,
                                           Optional<Double> executionThresholdAtMost,
                                           Optional<Double> executionThresholdAtLeast,
                                           CooldownSettings cooldown) {
        static final Codec<OwnerKilledEntityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("require_execution").forGetter(OwnerKilledEntityConfig::requireExecution),
            Codec.BOOL.optionalFieldOf("require_finisher_mark").forGetter(OwnerKilledEntityConfig::requireFinisherMark),
            Codec.DOUBLE.optionalFieldOf("max_execution_threshold_pct").forGetter(OwnerKilledEntityConfig::executionThresholdAtMost),
            Codec.DOUBLE.optionalFieldOf("min_execution_threshold_pct").forGetter(OwnerKilledEntityConfig::executionThresholdAtLeast),
            CooldownSettings.MAP_CODEC.forGetter(OwnerKilledEntityConfig::cooldown)
        ).apply(instance, OwnerKilledEntityConfig::new));
    }

    private record OwnerBeginFallConfig(Optional<Double> minFall, CooldownSettings cooldown) {
        static final Codec<OwnerBeginFallConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("min_fall").forGetter(OwnerBeginFallConfig::minFall),
            CooldownSettings.MAP_CODEC.forGetter(OwnerBeginFallConfig::cooldown)
        ).apply(instance, OwnerBeginFallConfig::new));
    }

    private record OwnerTookFallDamageConfig(Optional<Double> minDamage, CooldownSettings cooldown) {
        static final Codec<OwnerTookFallDamageConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("min_damage").forGetter(OwnerTookFallDamageConfig::minDamage),
            CooldownSettings.MAP_CODEC.forGetter(OwnerTookFallDamageConfig::cooldown)
        ).apply(instance, OwnerTookFallDamageConfig::new));
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

    private record OwnerShotProjectileConfig(Optional<String> projectileType, CooldownSettings cooldown) {
        static final Codec<OwnerShotProjectileConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("projectile_type").forGetter(OwnerShotProjectileConfig::projectileType),
            CooldownSettings.MAP_CODEC.forGetter(OwnerShotProjectileConfig::cooldown)
        ).apply(instance, OwnerShotProjectileConfig::new));
    }

    private record OwnerLowHealthConfig(Optional<Double> ownerHpPctBelow, CooldownSettings cooldown) {
        static final Codec<OwnerLowHealthConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("owner_hp_pct_below").forGetter(OwnerLowHealthConfig::ownerHpPctBelow),
            CooldownSettings.MAP_CODEC.forGetter(OwnerLowHealthConfig::cooldown)
        ).apply(instance, OwnerLowHealthConfig::new));
    }

    private record OwnerBrokeBlockConfig(Optional<Boolean> blockValuable, Optional<Identifier> blockId,
        CooldownSettings cooldown) {
        static final Codec<OwnerBrokeBlockConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("block_valuable").forGetter(OwnerBrokeBlockConfig::blockValuable),
            CodecUtils.identifierCodec().optionalFieldOf("block_id").forGetter(OwnerBrokeBlockConfig::blockId),
            CooldownSettings.MAP_CODEC.forGetter(OwnerBrokeBlockConfig::cooldown)
        ).apply(instance, OwnerBrokeBlockConfig::new));
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

    private record RetargetNearestHostileConfig(Optional<Double> radius,
                                                Optional<String> storeAs,
                                                Optional<String> requireDataFlag,
                                                Optional<Boolean> requireDataValue) {
        static final Codec<RetargetNearestHostileConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius").forGetter(RetargetNearestHostileConfig::radius),
            Codec.STRING.optionalFieldOf("store_as").forGetter(RetargetNearestHostileConfig::storeAs),
            Codec.STRING.optionalFieldOf("require_data_flag").forGetter(RetargetNearestHostileConfig::requireDataFlag),
            Codec.BOOL.optionalFieldOf("require_data_value").forGetter(RetargetNearestHostileConfig::requireDataValue)
        ).apply(instance, RetargetNearestHostileConfig::new));

    }

    private record ProjectileDrConfig(Optional<Double> percent,
                                      Optional<Integer> durationTicks,
                                      Optional<String> requireDataFlag,
                                      Optional<Boolean> requireDataValue) {
        static final Codec<ProjectileDrConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("percent").forGetter(ProjectileDrConfig::percent),
            Codec.INT.optionalFieldOf("duration_ticks").forGetter(ProjectileDrConfig::durationTicks),
            Codec.STRING.optionalFieldOf("require_data_flag").forGetter(ProjectileDrConfig::requireDataFlag),
            Codec.BOOL.optionalFieldOf("require_data_value").forGetter(ProjectileDrConfig::requireDataValue)
        ).apply(instance, ProjectileDrConfig::new));

    }

    private record HealOwnerFlatPctConfig(Optional<Double> value, Optional<Double> pctAmount,
                                         Optional<Double> flatAmount) {
        static final Codec<HealOwnerFlatPctConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("value").forGetter(HealOwnerFlatPctConfig::value),
            Codec.DOUBLE.optionalFieldOf("pct_amount").forGetter(HealOwnerFlatPctConfig::pctAmount),
            Codec.DOUBLE.optionalFieldOf("flat_amount").forGetter(HealOwnerFlatPctConfig::flatAmount)
        ).apply(instance, HealOwnerFlatPctConfig::new));

    }

    private record KnockupConfig(Optional<Double> strength, Optional<String> target) {
        static final Codec<KnockupConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("strength").forGetter(KnockupConfig::strength),
            Codec.STRING.optionalFieldOf("target").forGetter(KnockupConfig::target)
        ).apply(instance, KnockupConfig::new));

    }

    private record ApplyPotionToVictimConfig(String effect, int duration, int amplifier) {
        static final Codec<ApplyPotionToVictimConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("effect").forGetter(ApplyPotionToVictimConfig::effect),
            Codec.INT.optionalFieldOf("duration", 100).forGetter(ApplyPotionToVictimConfig::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyPotionToVictimConfig::amplifier)
        ).apply(instance, ApplyPotionToVictimConfig::new));
    }

    private record ApplyPotionToSelfConfig(String effect, int duration, int amplifier) {
        static final Codec<ApplyPotionToSelfConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("effect").forGetter(ApplyPotionToSelfConfig::effect),
            Codec.INT.optionalFieldOf("duration", 100).forGetter(ApplyPotionToSelfConfig::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyPotionToSelfConfig::amplifier)
        ).apply(instance, ApplyPotionToSelfConfig::new));
    }

    private record SetPetStateDataConfig(String key, int durationTicks) {
        static final Codec<SetPetStateDataConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(SetPetStateDataConfig::key),
            Codec.INT.fieldOf("duration_ticks").forGetter(SetPetStateDataConfig::durationTicks)
        ).apply(instance, SetPetStateDataConfig::new));
    }

    private record ClearPetStateDataConfig(String key) {
        static final Codec<ClearPetStateDataConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(ClearPetStateDataConfig::key)
        ).apply(instance, ClearPetStateDataConfig::new));
    }

    private record ParticleEffectConfig(String particle, String target, int count,
                                       double offsetX, double offsetY, double offsetZ,
                                       double speed, double heightOffset) {
        static final Codec<ParticleEffectConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("particle").forGetter(ParticleEffectConfig::particle),
            Codec.STRING.optionalFieldOf("target", "victim").forGetter(ParticleEffectConfig::target),
            Codec.INT.optionalFieldOf("count", 5).forGetter(ParticleEffectConfig::count),
            Codec.DOUBLE.optionalFieldOf("offset_x", 0.3).forGetter(ParticleEffectConfig::offsetX),
            Codec.DOUBLE.optionalFieldOf("offset_y", 0.3).forGetter(ParticleEffectConfig::offsetY),
            Codec.DOUBLE.optionalFieldOf("offset_z", 0.3).forGetter(ParticleEffectConfig::offsetZ),
            Codec.DOUBLE.optionalFieldOf("speed", 0.05).forGetter(ParticleEffectConfig::speed),
            Codec.DOUBLE.optionalFieldOf("height_offset", 0.5).forGetter(ParticleEffectConfig::heightOffset)
        ).apply(instance, ParticleEffectConfig::new));
    }

    private record SoundEffectConfig(String sound, String target, float volume, float pitch, String category) {
        static final Codec<SoundEffectConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("sound").forGetter(SoundEffectConfig::sound),
            Codec.STRING.optionalFieldOf("target", "victim").forGetter(SoundEffectConfig::target),
            Codec.FLOAT.optionalFieldOf("volume", 0.5f).forGetter(SoundEffectConfig::volume),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(SoundEffectConfig::pitch),
            Codec.STRING.optionalFieldOf("category", "neutral").forGetter(SoundEffectConfig::category)
        ).apply(instance, SoundEffectConfig::new));
    }
}
