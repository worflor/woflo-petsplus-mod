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
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.data.AbilityDataLoader;
import woflo.petsplus.data.PetRoleDataLoader;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.AreaEffectEffect;
import woflo.petsplus.effects.BuffEffect;
import woflo.petsplus.effects.CursedOneDeathBurstEffect;
import woflo.petsplus.effects.CursedOneSoulSacrificeEffect;
import woflo.petsplus.effects.EnchantStripEffect;
import woflo.petsplus.effects.EepyDrowsyMistEffect;
import woflo.petsplus.effects.GearSwapEffect;
import woflo.petsplus.effects.GuardianAegisProtocolEffect;
import woflo.petsplus.effects.GuardianFortressBondEffect;
import woflo.petsplus.effects.HealOwnerFlatPctEffect;
import woflo.petsplus.effects.KnockupEffect;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.effects.MountedConeAuraEffect;
import woflo.petsplus.effects.OpenEnderChestEffect;
import woflo.petsplus.effects.OpenPetBackpackEffect;
import woflo.petsplus.effects.OwnerNextAttackBonusEffect;
import woflo.petsplus.effects.PerchPotionSipReductionEffect;
import woflo.petsplus.effects.ProjectileDrForOwnerEffect;
import woflo.petsplus.effects.RetargetNearestHostileEffect;
import woflo.petsplus.effects.StrikerMarkFeedbackEffect;
import woflo.petsplus.effects.SkyriderGustUpwardsEffect;
import woflo.petsplus.effects.StrikerBloodlustSurgeEffect;
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
        registerDefaultAbilities();

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

        registerEffectSerializer(EffectSerializer.builder(id("striker_bloodlust_surge"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new StrikerBloodlustSurgeEffect(json)))
            .description("Applies execution-fueled speed and strength buffs for Striker bloodlust.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("skyrider_gust_upwards"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SkyriderGustUpwardsEffect(json)))
            .description("Launches the owner upward with Skyrider gust slow-fall assistance.")
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

        registerEffectSerializer(EffectSerializer.builder(id("guardian_fortress_bond"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> {
                double reduction = RegistryJsonHelper.getDouble(json, "reduction_pct", 0.5);
                int duration = RegistryJsonHelper.getInt(json, "duration_ticks", 200);
                return DataResult.success(new GuardianFortressBondEffect(reduction, duration));
            })
            .description("Activates the Guardian fortress bond barrier between owner and pet.")
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

        registerEffectSerializer(EffectSerializer.builder(id("projectile_dr_for_owner"), ProjectileDrConfig.CODEC,
            (abilityId, config, context) -> DataResult.success(new ProjectileDrForOwnerEffect(
                config.percent().orElse(0.10), config.durationTicks().orElse(40))))
            .description("Grants temporary projectile damage reduction to the owner.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("perch_potion_sip_reduction"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new PerchPotionSipReductionEffect(json)))
            .description("Reduces potion consumption while perched.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("support_potion_pulse"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new SupportPotionPulseEffect(json)))
            .description("Shares the Support pet's stored potion energy on demand.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("mounted_cone_aura"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MountedConeAuraEffect(json)))
            .description("Emits a buff cone while the pet is mounted.")
            .build());

        registerEffectSerializer(EffectSerializer.builder(id("magnetize_drops_and_xp"), RegistryJsonHelper.JSON_OBJECT_CODEC,
            (abilityId, json, context) -> DataResult.success(new MagnetizeDropsAndXpEffect(json)))
            .description("Pulls item drops and XP orbs toward the pet.")
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

    private static void registerDefaultAbilities() {
        // Register placeholder abilities that will be properly loaded from data later
        // This prevents role validation errors during startup
        registerAbilityType("shield_bash_rider", createPlaceholderAbility("shield_bash_rider"));
        registerAbilityType("aegis_protocol", createPlaceholderAbility("aegis_protocol"));
        registerAbilityType("fortress_bond", createPlaceholderAbility("fortress_bond"));
        registerAbilityType("finisher_mark", createPlaceholderAbility("finisher_mark"));
        registerAbilityType("bloodlust_surge", createPlaceholderAbility("bloodlust_surge"));
        registerAbilityType("perch_potion_efficiency", createPlaceholderAbility("perch_potion_efficiency"));
        registerAbilityType("mounted_cone_aura", createPlaceholderAbility("mounted_cone_aura"));
        registerAbilityType("loot_wisp", createPlaceholderAbility("loot_wisp"));
        registerAbilityType("windlash_rider", createPlaceholderAbility("windlash_rider"));
        registerAbilityType("gust_upwards", createPlaceholderAbility("gust_upwards"));
        registerAbilityType("doom_echo", createPlaceholderAbility("doom_echo"));
        registerAbilityType("death_burst", createPlaceholderAbility("death_burst"));
        registerAbilityType("soul_sacrifice", createPlaceholderAbility("soul_sacrifice"));
        registerAbilityType("voidbrand", createPlaceholderAbility("voidbrand"));
        registerAbilityType("phase_partner", createPlaceholderAbility("phase_partner"));
        registerAbilityType("perch_ping", createPlaceholderAbility("perch_ping"));
        registerAbilityType("bulwark_redirect", createPlaceholderAbility("bulwark_redirect"));
        registerAbilityType("execution_bonus", createPlaceholderAbility("execution_bonus"));
        registerAbilityType("spotter_fallback", createPlaceholderAbility("spotter_fallback"));
        registerAbilityType("gale_pace", createPlaceholderAbility("gale_pace"));
        registerAbilityType("projectile_levitation", createPlaceholderAbility("projectile_levitation"));
        registerAbilityType("skybond_mount_extension", createPlaceholderAbility("skybond_mount_extension"));
        registerAbilityType("perched_haste_bonus", createPlaceholderAbility("perched_haste_bonus"));
        registerAbilityType("mounted_extra_rolls", createPlaceholderAbility("mounted_extra_rolls"));
        registerAbilityType("enchant_strip", createPlaceholderAbility("enchant_strip"));
        registerAbilityType("gear_swap", createPlaceholderAbility("gear_swap"));
        registerAbilityType("auto_resurrect_mount_buff", createPlaceholderAbility("auto_resurrect_mount_buff"));
        registerAbilityType("nap_time_radius", createPlaceholderAbility("nap_time_radius"));
        registerAbilityType("drowsy_mist", createPlaceholderAbility("drowsy_mist"));
        registerAbilityType("event_horizon", createPlaceholderAbility("event_horizon"));
        registerAbilityType("edge_step", createPlaceholderAbility("edge_step"));
        registerAbilityType("void_storage", createPlaceholderAbility("void_storage"));
        registerAbilityType("scout_backpack", createPlaceholderAbility("scout_backpack"));
    }

    private static AbilityType createPlaceholderAbility(String name) {
        return new AbilityType(id(name), () -> createFallbackAbility(name), "Placeholder for " + name);
    }

    private static Ability createFallbackAbility(String name) {
        // Create a fallback ability that will work temporarily until data loading replaces it
        Identifier abilityId = id(name);
        Trigger fallbackTrigger = new Trigger() {
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
                return false; // Placeholder abilities don't activate
            }
        };

        return new woflo.petsplus.api.Ability(abilityId, fallbackTrigger, java.util.List.of(), new com.google.gson.JsonObject());
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
