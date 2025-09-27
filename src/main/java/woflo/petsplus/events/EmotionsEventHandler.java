package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.village.VillagerProfession;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.CampfireBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.BlockTags;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureFlavorHandler.Trigger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import woflo.petsplus.behavior.social.PackCircleRoutine;
import woflo.petsplus.behavior.social.PetSocialData;
import woflo.petsplus.behavior.social.SocialBehaviorRoutine;
import woflo.petsplus.behavior.social.SocialContextSnapshot;
import woflo.petsplus.behavior.social.WhisperRoutine;
import woflo.petsplus.events.EmotionCueConfig.EmotionCueDefinition;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.PlayerTickListener;
import woflo.petsplus.state.StateManager;

/**
 * Central, low-cost, event-driven emotion hooks.
 * Ties pet emotions to existing gameplay events (no per-tick polling).
 */
public final class EmotionsEventHandler {

    private static final PlayerTicker PLAYER_TICKER = new PlayerTicker();
    private static final List<SocialBehaviorRoutine> SOCIAL_ROUTINES = List.of(
        new PackCircleRoutine(),
        new WhisperRoutine()
    );
    private static final TagKey<Item> MUSIC_DISC_ITEMS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("minecraft", "music_discs"));

    private EmotionsEventHandler() {}

    public static PlayerTickListener ticker() {
        return PLAYER_TICKER;
    }

    public static void register() {
        // After a block is broken (server): mining satisfaction, discovery moments
        PlayerBlockBreakEvents.AFTER.register(EmotionsEventHandler::onAfterBlockBreak);

        // After a block is placed (server): building/creation
    // Note: Fabric API for block place events may vary by MC version.
    // We piggyback on UseBlockCallback and detect intended placement via held BlockItem.

        // When an entity kills another: triumph/relief/zeal
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(EmotionsEventHandler::onAfterKilledOther);

        // When player uses an item: food, potions, notable items
        UseItemCallback.EVENT.register(EmotionsEventHandler::onUseItem);

        // When player uses a block: beds, chests, stations, jukebox
        UseBlockCallback.EVENT.register(EmotionsEventHandler::onUseBlock);

        // When interacting with entities: leads/mounting
        UseEntityCallback.EVENT.register(EmotionsEventHandler::onUseEntity);

        // Before player attacks an entity: combat anticipation
        AttackEntityCallback.EVENT.register(EmotionsEventHandler::onOwnerAttack);

        // After player respawn: relief and resilience
        ServerPlayerEvents.AFTER_RESPAWN.register(EmotionsEventHandler::onAfterRespawn);

        // Owner death: grief/longing on pets
        ServerLivingEntityEvents.AFTER_DEATH.register(EmotionsEventHandler::onAfterDeath);

        // 5th Wave: Tag-based and data-driven emotion hooks
        // Enchanting events (via block use on enchanting table)
        UseBlockCallback.EVENT.register(EmotionsEventHandler::onEnchantingTableUse);

        // Trading events (via villager interaction)
        UseEntityCallback.EVENT.register(EmotionsEventHandler::onVillagerTrade);

        Petsplus.LOGGER.info("Emotions event handlers registered");
    }

    // ==== Block Break → KEFI, GLEE, WABI_SABI (building), STOIC (familiar grind resilience) ====
    private static void onAfterBlockBreak(World world, PlayerEntity player, BlockPos pos, net.minecraft.block.BlockState state,
net.minecraft.block.entity.BlockEntity blockEntity) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return;
        EmotionCueConfig config = EmotionCueConfig.get();
        String definitionId = config.findBlockBreakDefinition(state);
        if (definitionId != null) {
            triggerConfiguredCue(sp, definitionId, config.fallbackRadius(), null);
        }

        if (state.isOf(Blocks.RED_MUSHROOM) || state.isOf(Blocks.BROWN_MUSHROOM)
            || state.isOf(Blocks.RED_MUSHROOM_BLOCK) || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK)
            || state.isOf(Blocks.MUSHROOM_STEM) || state.isOf(Blocks.CRIMSON_FUNGUS)
            || state.isOf(Blocks.WARPED_FUNGUS)) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.BREAK_MUSHROOM);
        }
        if (state.isOf(Blocks.MUD) || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.BREAK_MUD);
        }
        if (state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.SNOW) || state.isOf(Blocks.POWDER_SNOW)) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.BREAK_SNOW);
        }
    }

    // (Block place detour handled in onUseBlock based on held BlockItem)

    // ==== Kill Events → PASSIONATE, PLAYFUL, HAPPY, FOCUSED ====
    private static void onAfterKilledOther(ServerWorld world, Entity killer, LivingEntity killed) {
        if (killer instanceof ServerPlayerEntity sp) {
            KillContext context = classifyKillTarget(killed);
            float healthPct = Math.max(0f, sp.getHealth() / sp.getMaxHealth());
            float reliefBonus = healthPct < 0.35f ? 0.2f : 0f;
            long now = world.getTime();
            applyConfiguredStimulus(sp, "combat.owner_kill", 32, pc -> {
                if (reliefBonus > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, reliefBonus);
                }
                switch (context) {
                    case HOSTILE -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_hostile", now, 200L)) {
                            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f + reliefBonus * 0.4f);
                            pc.pushEmotion(PetComponent.Emotion.SISU, 0.06f);
                        }
                    }
                    case PASSIVE -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_passive", now, 240L)) {
                            pc.pushEmotion(PetComponent.Emotion.REGRET, 0.06f);
                            pc.pushEmotion(PetComponent.Emotion.HIRAETH, 0.05f);
                        }
                    }
                    case BOSS -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_boss", now, 360L)) {
                            pc.pushEmotion(PetComponent.Emotion.PRIDE, 0.08f);
                            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.06f + reliefBonus * 0.4f);
                        }
                    }
                }
            });

            if (healthPct < 0.35f) {
                EmotionContextCues.sendCue(sp, "combat.owner_kill.close",
                    Text.translatable("petsplus.emotion_cue.combat.owner_kill_close"), 200);
            } else {
                String suffix = context.cueSuffix();
                Text cueText = resolveCueText("combat.owner_kill_" + suffix,
                    "petsplus.emotion_cue.combat.owner_kill_" + suffix,
                    killed.getDisplayName());
                EmotionContextCues.sendCue(sp, "combat.owner_kill." + suffix, cueText, 200);
            }
        }

        LAST_OWNER_ATTACK_TARGET.remove(killed.getUuid());

        if (killer instanceof MobEntity mob) {
            PetComponent pc = PetComponent.get(mob);
            if (pc != null && pc.getOwner() instanceof ServerPlayerEntity owner) {
                emitPetCue(owner, pc, "combat.pet_kill." + mob.getUuidAsString(), component -> {},
                    "petsplus.emotion_cue.combat.pet_kill", 200, mob.getDisplayName());
                if (pc.hasRole(PetRoleType.STRIKER)) {
                    emitPetCue(owner, pc, "role.striker.execute." + mob.getUuidAsString(), component -> {},
                        "petsplus.emotion_cue.role.striker_execute", 200, mob.getDisplayName());
                }
            }
        }
    }

    // ==== Item Use → Food/potion themed emotions ====
    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return ActionResult.PASS;

        EmotionCueConfig config = EmotionCueConfig.get();
        String definitionId = config.findItemUseDefinition(stack);
        if (definitionId != null) {
            triggerConfiguredCue(sp, definitionId, config.fallbackRadius(), null);
        }

        Item item = stack.getItem();
        if (item == Items.FIREWORK_ROCKET) {
            NatureFlavorHandler.triggerForOwner(sp, 32, Trigger.USE_FIREWORK);
        }
        if (item == Items.ENDER_PEARL || item == Items.ENDER_EYE || item == Items.CHORUS_FRUIT) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.USE_ENDER_ARTIFACT);
        }
        if (item == Items.LAVA_BUCKET) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.USE_LAVA_BUCKET);
        }
        if (item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.USE_FLINT_AND_STEEL);
        }
        if (item == Items.COD_BUCKET || item == Items.SALMON_BUCKET || item == Items.TROPICAL_FISH_BUCKET
            || item == Items.PUFFERFISH_BUCKET || item == Items.AXOLOTL_BUCKET) {
            NatureFlavorHandler.triggerForOwner(sp, 32, Trigger.BUCKET_FISH);
        }

        return ActionResult.PASS;
    }

    // ==== Block Use → Homey stations, beds, jukebox ====
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        BlockPos pos = hitResult.getBlockPos();
        var state = world.getBlockState(pos);
        EmotionCueConfig config = EmotionCueConfig.get();

        if (state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.CAMPFIRE_INTERACTION);
        }
        if (state.getBlock() instanceof BedBlock) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.BED_INTERACTION);
        }
        if (state.getBlock() instanceof JukeboxBlock) {
            NatureFlavorHandler.triggerForOwner(sp, 24, Trigger.JUKEBOX_PLAY);
        }
        if (state.isIn(BlockTags.BUTTONS)
            || state.isOf(Blocks.LEVER)
            || state.isOf(Blocks.REDSTONE_BLOCK)
            || state.isOf(Blocks.REPEATER)
            || state.isOf(Blocks.COMPARATOR)
            || state.isOf(Blocks.TRIPWIRE_HOOK)) {
            NatureFlavorHandler.triggerForOwner(sp, 20, Trigger.REDSTONE_INTERACTION);
        }

        String useDefinition = config.findBlockUseDefinition(state);
        ItemStack held = player.getStackInHand(hand);
        PetConsumer stateConsumer = pc -> {};

        if (state.isIn(BlockTags.CAMPFIRES) && state.contains(CampfireBlock.LIT)) {
            boolean lit = state.get(CampfireBlock.LIT);
            if (lit && held.isIn(ItemTags.SHOVELS)) {
                useDefinition = "block_use.campfire.douse";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.05f);
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.04f);
                };
            } else if (!lit && (held.isOf(Items.FLINT_AND_STEEL) || held.isOf(Items.FIRE_CHARGE))) {
                useDefinition = "block_use.campfire.ignite";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.05f);
                    pc.pushEmotion(PetComponent.Emotion.GLEE, 0.04f);
                };
            } else if (lit) {
                useDefinition = "block_use.campfire.stoke";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.CONTENT, 0.05f);
                    pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.04f);
                };
            }
        } else if (state.getBlock() instanceof JukeboxBlock && state.contains(JukeboxBlock.HAS_RECORD)) {
            boolean hasRecord = state.get(JukeboxBlock.HAS_RECORD);
            if (hasRecord) {
                useDefinition = "block_use.jukebox.stop";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.05f);
                    pc.pushEmotion(PetComponent.Emotion.NOSTALGIA, 0.04f);
                };
            } else if (held.isIn(MUSIC_DISC_ITEMS)) {
                useDefinition = "block_use.jukebox.start";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.GLEE, 0.06f);
                    pc.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.05f);
                    if (pc.getPet() instanceof ParrotEntity) {
                        pc.pushEmotion(PetComponent.Emotion.GLEE, 0.04f);
                        pc.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.04f);
                        pc.pushEmotion(PetComponent.Emotion.KEFI, 0.03f);
                    }
                };
            }
        } else if (state.getBlock() instanceof BedBlock && state.contains(BedBlock.OCCUPIED)) {
            boolean occupied = state.get(BedBlock.OCCUPIED);
            if (occupied) {
                useDefinition = "block_use.bed.occupied";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.04f);
                    pc.pushEmotion(PetComponent.Emotion.REGRET, 0.03f);
                };
            } else {
                useDefinition = "block_use.bed.rest";
                stateConsumer = pc -> {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.05f);
                    pc.pushEmotion(PetComponent.Emotion.CONTENT, 0.04f);
                };
            }
        }

        if (useDefinition != null) {
            triggerConfiguredCue(sp, useDefinition, config.fallbackRadius(), stateConsumer, null);
        }

        if (held.getItem() instanceof BlockItem blockItem) {
            BlockState defaultState = blockItem.getBlock().getDefaultState();
            if (defaultState.isIn(BlockTags.SAPLINGS)) {
                NatureFlavorHandler.triggerForOwner(sp, 32, Trigger.PLACE_SAPLING);
            }
            if (defaultState.isIn(BlockTags.BUTTONS)
                || defaultState.isOf(Blocks.LEVER)
                || defaultState.isOf(Blocks.REDSTONE_BLOCK)
                || defaultState.isOf(Blocks.REPEATER)
                || defaultState.isOf(Blocks.COMPARATOR)
                || defaultState.isOf(Blocks.TRIPWIRE_HOOK)) {
                NatureFlavorHandler.triggerForOwner(sp, 20, Trigger.REDSTONE_INTERACTION);
            }
            String placeDefinition = config.findBlockPlaceDefinition(defaultState);
            if (placeDefinition != null) {
                triggerConfiguredCue(sp, placeDefinition, config.fallbackRadius(), null);
            }
        }
        return ActionResult.PASS;
    }

    // ==== Entity Use → Lead/mount moments → PROTECTIVENESS, FERNWEH ====
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        EmotionCueConfig config = EmotionCueConfig.get();
        String definitionId = config.findEntityUseDefinition(entity, stack);
        if (definitionId != null) {
            triggerConfiguredCue(sp, definitionId, config.fallbackRadius(), null);
        }
        return ActionResult.PASS;
    }

    private static ActionResult onOwnerAttack(PlayerEntity player, World world, Hand hand,
                                             Entity target, @Nullable EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (sp.isSpectator() || sp.getAbilities().creativeMode) {
            return ActionResult.PASS;
        }
        if (!(target instanceof LivingEntity)) {
            return ActionResult.PASS;
        }

        UUID targetId = target.getUuid();
        UUID playerId = sp.getUuid();
        long now = serverWorld.getTime();
        ConcurrentHashMap<UUID, Long> perTarget = LAST_OWNER_ATTACK_TARGET.computeIfAbsent(targetId, id -> new ConcurrentHashMap<>());
        Long last = perTarget.get(playerId);
        if (last != null && now - last < 40L) {
            return ActionResult.PASS;
        }
        perTarget.put(playerId, now);

        float charge = sp.getAttackCooldownProgress(0.5f);
        float passionate = 0.04f + 0.04f * charge;
        float focused = 0.03f + 0.03f * charge;
        float startle = 0.015f + 0.02f * charge;

        pushToNearbyOwnedPets(sp, 28, pc -> {
            pc.pushEmotion(PetComponent.Emotion.KEFI, passionate);
            pc.pushEmotion(PetComponent.Emotion.FOCUSED, focused);
            pc.pushEmotion(PetComponent.Emotion.STARTLE, startle);
        });

        EmotionContextCues.sendCue(sp,
            "combat.owner_swing." + targetId,
            Text.translatable("petsplus.emotion_cue.combat.owner_swing", target.getDisplayName()),
            160);

        return ActionResult.PASS;
    }

    // ==== Respawn → RELIEF + STOIC/GAMAN ====
    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) return;
        EmotionContextCues.clear(oldPlayer);
        EmotionContextCues.clear(newPlayer);
        pushToNearbyOwnedPets(newPlayer, 32, pc -> {
            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.20f);
            pc.pushEmotion(PetComponent.Emotion.STOIC, 0.16f);
            pc.pushEmotion(PetComponent.Emotion.GAMAN, 0.08f);
        });
        EmotionContextCues.sendCue(newPlayer, "player.respawn", Text.translatable("petsplus.emotion_cue.player.respawn"), 200);
        NatureFlavorHandler.triggerForOwner(newPlayer, 32, Trigger.OWNER_RESPAWN);
    }

    // ==== Owner Death → SAUDADE + HIRAETH + REGRET (nearby pets) ====
    private static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayerEntity sp)) return;
        ServerWorld world = (ServerWorld) sp.getWorld();
        world.getEntitiesByClass(MobEntity.class, sp.getBoundingBox().expand(48), mob -> {
            PetComponent pc = PetComponent.get(mob);
            return pc != null && pc.isOwnedBy(sp);
        }).forEach(pet -> {
            PetComponent pc = PetComponent.get(pet);
            if (pc != null) {
                MoodService.getInstance().getStimulusBus().queueStimulus(pet, component -> {
                    component.pushEmotion(PetComponent.Emotion.SAUDADE, 0.25f);
                    component.pushEmotion(PetComponent.Emotion.HIRAETH, 0.20f);
                    component.pushEmotion(PetComponent.Emotion.REGRET, 0.15f);
                });
                MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            }
        });
    }

    // ==== Weather transitions (ultra-light) → SOBREMESA, YUGEN, FOREBODING, RELIEF ====
    private static final java.util.Map<ServerWorld, WeatherState> WEATHER = new java.util.WeakHashMap<>();
    private static final Map<ServerWorld, Long> LAST_WET_WEATHER_TICK = new WeakHashMap<>();
    private static final Map<UUID, Long> LAST_CLEAR_WEATHER_TRIGGER = new HashMap<>();
    private static final Map<UUID, ConcurrentHashMap<UUID, Long>> LAST_OWNER_ATTACK_TARGET = new ConcurrentHashMap<>();
    private static final Map<ServerPlayerEntity, String> INVENTORY_SIGNATURES = new WeakHashMap<>();
    private record WeatherState(boolean raining, boolean thundering) {}

    private static final TagKey<Item> VALUABLE_ITEMS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("petsplus", "emotion/valuable_items"));
    private static final TagKey<Item> MYSTIC_ITEMS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("petsplus", "emotion/magical_items"));
    private static final TagKey<Item> RANGED_WEAPONS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("petsplus", "emotion/ranged_weapons"));
    private static final TagKey<Item> SUPPORT_TOOLS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("petsplus", "emotion/support_tools"));
    private static final TagKey<Block> NATURE_PLANTS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "emotion/environment/nature_plants"));
    private static final TagKey<Biome> DESERT_LIKE_BIOMES = TagKey.of(RegistryKeys.BIOME,
        Identifier.of("petsplus", "emotion/biomes/desert_like"));
    private static final TagKey<Biome> OCEANIC_BIOMES = TagKey.of(RegistryKeys.BIOME,
        Identifier.of("petsplus", "emotion/biomes/oceanic"));
    private static final TagKey<Biome> SNOWY_BIOMES = TagKey.of(RegistryKeys.BIOME,
        Identifier.of("petsplus", "emotion/biomes/snowy"));
    private static final TagKey<Block> STABLE_FEED = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "emotion/environment/stable_feed"));

    // Time of day phases for subtle transitions
    private enum TimePhase { DAWN, DAY, DUSK, NIGHT }
    private static final Map<ServerWorld, TimePhase> TIME_PHASES = new WeakHashMap<>();

    // Lightweight per-player environment state for biome/dimension/idle tracking
    private static final Map<ServerPlayerEntity, PlayerEnvState> PLAYER_ENV = new WeakHashMap<>();
    private static final Map<UUID, RegistryKey<World>> PENDING_DIMENSION_CHANGES = new ConcurrentHashMap<>();
    private static class PlayerEnvState {
        RegistryKey<Biome> biomeKey;
        RegistryKey<World> dimensionKey;
        Vec3d lastPos;
        long lastMovementTick;
        long lastIdleCueTick;
        long lastRainyIdleCueTick;
        boolean lowHungerNotified;
        double lastFallDistance;
        long nextIdleCheckTick;
        long nextNuancedTick;

        PlayerEnvState(@Nullable RegistryKey<Biome> biomeKey, @Nullable RegistryKey<World> dimensionKey, Vec3d lastPos, long currentTick) {
            this.biomeKey = biomeKey;
            this.dimensionKey = dimensionKey;
            this.lastPos = lastPos;
            this.lastMovementTick = currentTick;
            this.lastIdleCueTick = 0L;
            this.lastRainyIdleCueTick = 0L;
            this.lowHungerNotified = false;
            this.lastFallDistance = 0d;
            this.nextIdleCheckTick = currentTick + 20L;
            this.nextNuancedTick = currentTick + 60L;
        }
    }

    private static void handlePlayerTick(ServerPlayerEntity player, long serverTick) {
        if (player == null || player.isRemoved()) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        WEATHER.computeIfAbsent(world, w -> new WeatherState(world.isRaining(), world.isThundering()));
        TIME_PHASES.computeIfAbsent(world, w -> computeTimePhase(world.getTimeOfDay()));

        long now = world.getTime();
        PlayerEnvState state = PLAYER_ENV.computeIfAbsent(player,
            p -> createEnvState(player, world, now));

        RegistryKey<World> pendingPrevious = PENDING_DIMENSION_CHANGES.remove(player.getUuid());
        if (pendingPrevious != null) {
            RegistryKey<World> currentDimension = world.getRegistryKey();
            if (!currentDimension.equals(pendingPrevious)) {
                handleDimensionChange(player, state, pendingPrevious, currentDimension);
            }
        }

        if (player.isSpectator() || player.isSleeping()) {
            state.nextIdleCheckTick = Math.max(state.nextIdleCheckTick, now + 20L);
            state.nextNuancedTick = Math.max(state.nextNuancedTick, now + 60L);
            state.lastFallDistance = player.fallDistance;
            return;
        }

        updateFallTracking(player, state);

        if (now >= state.nextIdleCheckTick) {
            state.nextIdleCheckTick = now + 20L;
            handleIdleMaintenance(world, player, state, now);
        }

        if (now >= state.nextNuancedTick) {
            state.nextNuancedTick = now + 60L;
            handleNuancedEmotions(player, world, now);
        }
    }

    private static PlayerEnvState createEnvState(ServerPlayerEntity player, ServerWorld world, long now) {
        RegistryKey<Biome> biomeKey = null;
        try {
            RegistryEntry<Biome> biomeEntry = world.getBiome(player.getBlockPos());
            biomeKey = biomeEntry.getKey().orElse(null);
        } catch (Throwable ignored) {}
        return new PlayerEnvState(biomeKey, world.getRegistryKey(), player.getPos(), now);
    }

    private static void updateFallTracking(ServerPlayerEntity player, PlayerEnvState state) {
        double current = player.fallDistance;
        if (current > state.lastFallDistance) {
            state.lastFallDistance = current;
            return;
        }

        if (state.lastFallDistance > 4.0d && player.isOnGround() && current < 0.5d) {
            handlePlayerFall(player, state.lastFallDistance);
            state.lastFallDistance = current;
        } else if (player.isOnGround()) {
            state.lastFallDistance = current;
        }
    }

    private static void handleIdleMaintenance(ServerWorld world, ServerPlayerEntity player, PlayerEnvState state, long now) {
        long idleTicks = Math.max(0L, now - state.lastMovementTick);
        if (idleTicks < 1200L) {
            return;
        }

        if (hasNearbyActivity(player, world)) {
            return;
        }

        if (idleTicks >= 3600L) {
            long sinceLast = now - state.lastIdleCueTick;
            if (state.lastIdleCueTick == 0L || sinceLast >= 2400L) {
                pushToNearbyOwnedPets(player, 32, pc -> pc.pushEmotion(PetComponent.Emotion.ENNUI, 0.15f));
                EmotionContextCues.sendCue(player, "idle.ennui", Text.translatable("petsplus.emotion_cue.idle.ennui"), 2400);
                state.lastIdleCueTick = now;
            }
        }

        if (world.isRaining() && TIME_PHASES.getOrDefault(world, TimePhase.NIGHT) == TimePhase.NIGHT && idleTicks >= 2400L) {
            long sinceRainy = now - state.lastRainyIdleCueTick;
            if (sinceRainy >= 1200L) {
                pushToNearbyOwnedPets(player, 32, pc -> pc.pushEmotion(PetComponent.Emotion.ENNUI, 0.06f));
                EmotionContextCues.sendCue(player, "idle.rainy_ennui", Text.translatable("petsplus.emotion_cue.idle.rainy"), 2400);
                state.lastRainyIdleCueTick = now;
            }
        }
    }

    public static void handleWeatherUpdated(ServerWorld world, boolean wasRaining, boolean wasThundering, boolean raining, boolean thundering) {
        WEATHER.put(world, new WeatherState(raining, thundering));
        long now = world.getTime();
        if (raining || thundering) {
            LAST_WET_WEATHER_TICK.put(world, now);
        } else if (wasRaining || wasThundering) {
            LAST_WET_WEATHER_TICK.put(world, Math.max(0L, now - 1L));
        }

        for (ServerPlayerEntity sp : world.getPlayers()) {
            if (raining && !wasRaining) {
                pushToNearbyOwnedPets(sp, 48, pc -> {
                    pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.08f);
                    pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.06f);
                    pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.04f);
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_RAIN_START);
                EmotionContextCues.sendCue(sp, "weather.rain_start", Text.translatable("petsplus.emotion_cue.weather.rain_start"), 600);
            } else if (!raining && wasRaining) {
                pushToNearbyOwnedPets(sp, 48, pc -> {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.10f);
                    pc.pushEmotion(PetComponent.Emotion.GLEE, 0.06f);
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_CLEAR);
                EmotionContextCues.sendCue(sp, "weather.rain_end", Text.translatable("petsplus.emotion_cue.weather.rain_end"), 600);
            }

            if (thundering && !wasThundering) {
                pushToNearbyOwnedPets(sp, 48, pc -> {
                    pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.12f);
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                    pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.06f);
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_THUNDER_START);
                EmotionContextCues.sendCue(sp, "weather.thunder", Text.translatable("petsplus.emotion_cue.weather.thunder"), 600);
            }
        }

        dispatchWeatherReactions(world);
    }

    public static void handleTimeOfDayUpdated(ServerWorld world, long previousTimeOfDay, long newTimeOfDay) {
        TimePhase previous = TIME_PHASES.getOrDefault(world, computeTimePhase(previousTimeOfDay));
        TimePhase next = computeTimePhase(newTimeOfDay);
        if (previous == next) {
            TIME_PHASES.put(world, next);
            return;
        }
        TIME_PHASES.put(world, next);

        for (ServerPlayerEntity sp : world.getPlayers()) {
            switch (next) {
                case DAWN -> {
                    pushToNearbyOwnedPets(sp, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.10f);
                        pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.06f);
                        pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f);
                    });
                    EmotionContextCues.sendCue(sp, "time.dawn", Text.translatable("petsplus.emotion_cue.time.dawn"), 1200);
                }
                case DUSK -> {
                    pushToNearbyOwnedPets(sp, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.08f);
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.06f);
                        pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.04f);
                    });
                    EmotionContextCues.sendCue(sp, "time.dusk", Text.translatable("petsplus.emotion_cue.time.dusk"), 1200);
                }
                case DAY -> {
                    pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.KEFI, 0.04f));
                    NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.DAYBREAK);
                    EmotionContextCues.sendCue(sp, "time.day", Text.translatable("petsplus.emotion_cue.time.day"), 2400);
                }
                case NIGHT -> {
                    pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.04f));
                    NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.NIGHTFALL);
                    EmotionContextCues.sendCue(sp, "time.night", Text.translatable("petsplus.emotion_cue.time.night"), 2400);
                }
            }
        }
    }

    private static void dispatchWeatherReactions(ServerWorld world) {
        for (ServerPlayerEntity owner : world.getPlayers()) {
            forEachOwnedPet(owner, 32.0d, (pet, component) -> {
                MoodService.getInstance().getStimulusBus().queueStimulus(pet,
                    pc -> addAdvancedWeatherTriggers(pet, pc, owner, world));
                MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            });
        }
    }

    public static void handleInventoryMutated(ServerPlayerEntity player) {
        String signature = computeInventorySignature(player);
        String previous = INVENTORY_SIGNATURES.get(player);
        if (previous != null && previous.equals(signature)) {
            return;
        }
        INVENTORY_SIGNATURES.put(player, signature);

        forEachOwnedPet(player, 32.0d, (pet, component) -> {
            MoodService.getInstance().getStimulusBus().queueStimulus(pet,
                pc -> addTagBasedItemTriggers(pet, pc, player));
            MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
        });
    }

    static String computeInventorySignature(ServerPlayerEntity owner) {
        return computeInventorySignature(owner.getInventory());
    }

    static String computeInventorySignature(PlayerInventory inventory) {
        boolean hasValuableItems = false;
        boolean hasFoodItems = false;
        boolean hasMagicalItems = false;
        boolean hasWeapons = false;
        boolean hasTools = false;
        boolean hasArchaeology = false;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.isIn(ItemTags.TRIM_MATERIALS) || stack.isIn(VALUABLE_ITEMS)) {
                hasValuableItems = true;
            }

            if (stack.get(DataComponentTypes.FOOD) != null ||
                stack.isIn(ItemTags.MEAT) ||
                stack.isIn(ItemTags.FISHES)) {
                hasFoodItems = true;
            }

            if (stack.hasEnchantments() ||
                stack.isIn(MYSTIC_ITEMS) ||
                stack.isIn(ItemTags.BOOKSHELF_BOOKS)) {
                hasMagicalItems = true;
            }

            if (stack.isIn(ItemTags.SWORDS) ||
                stack.isIn(ItemTags.AXES) ||
                stack.isIn(RANGED_WEAPONS)) {
                hasWeapons = true;
            }

            if (stack.isIn(ItemTags.PICKAXES) ||
                stack.isIn(ItemTags.SHOVELS) ||
                stack.isIn(ItemTags.HOES) ||
                stack.isIn(ItemTags.AXES) ||
                stack.isOf(Items.SHEARS) ||
                stack.isIn(SUPPORT_TOOLS)) {
                hasTools = true;
            }
        }

        return buildInventorySignature(hasValuableItems, hasFoodItems, hasMagicalItems,
            hasWeapons, hasTools, hasArchaeology);
    }

    static String buildInventorySignature(boolean hasValuableItems,
                                          boolean hasFoodItems,
                                          boolean hasMagicalItems,
                                          boolean hasWeapons,
                                          boolean hasTools,
                                          boolean hasArchaeology) {
        return new StringBuilder(6)
            .append(hasValuableItems ? '1' : '0')
            .append(hasFoodItems ? '1' : '0')
            .append(hasMagicalItems ? '1' : '0')
            .append(hasWeapons ? '1' : '0')
            .append(hasTools ? '1' : '0')
            .append(hasArchaeology ? '1' : '0')
            .toString();
    }

    public static void handleHungerLevelChanged(ServerPlayerEntity player, int previousLevel, int currentLevel) {
        ServerWorld world = player.getWorld();
        PlayerEnvState state = PLAYER_ENV.computeIfAbsent(player,
            p -> new PlayerEnvState(null, world.getRegistryKey(), player.getPos(), world.getTime()));

        if (currentLevel <= 4) {
            if (!state.lowHungerNotified || previousLevel > 4) {
                pushToNearbyOwnedPets(player, 32, pc -> {
                    pc.pushEmotion(PetComponent.Emotion.UBUNTU, 0.12f);
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                });
                EmotionContextCues.sendCue(player, "owner.low_hunger",
                    Text.translatable("petsplus.emotion_cue.owner.low_hunger"), 600);
                state.lowHungerNotified = true;
            }
        } else if (currentLevel > 6 && state.lowHungerNotified) {
            state.lowHungerNotified = false;
        }
    }

    public static void handlePlayerMovement(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();
        long now = world.getTime();
        Vec3d pos = player.getPos();
        RegistryEntry<Biome> biomeEntry = null;
        RegistryKey<Biome> biomeKey = null;
        try {
            biomeEntry = world.getBiome(player.getBlockPos());
            biomeKey = biomeEntry.getKey().orElse(null);
        } catch (Throwable ignored) {}
        RegistryKey<World> dimensionKey = world.getRegistryKey();

        PlayerEnvState state = PLAYER_ENV.get(player);
        if (state == null) {
            state = new PlayerEnvState(biomeKey, dimensionKey, pos, now);
            PLAYER_ENV.put(player, state);
        }

        if (biomeKey != null && (state.biomeKey == null || !biomeKey.equals(state.biomeKey))) {
            state.biomeKey = biomeKey;
            if (biomeEntry != null) {
                if (biomeEntry.isIn(DESERT_LIKE_BIOMES)) {
                    pushToNearbyOwnedPets(player, 48, pc -> pc.pushEmotion(PetComponent.Emotion.HANYAUKU, 0.12f), false);
                }
                if (biomeKey.equals(BiomeKeys.CHERRY_GROVE) || biomeKey.equals(BiomeKeys.FLOWER_FOREST)) {
                    pushToNearbyOwnedPets(player, 32, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.10f);
                        pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.08f);
                    }, false);
                }
                if (biomeKey.equals(BiomeKeys.MUSHROOM_FIELDS)) {
                    pushToNearbyOwnedPets(player, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.GLEE, 0.15f);
                        pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.08f);
                    }, false);
                }
                if (biomeKey.equals(BiomeKeys.DEEP_DARK)) {
                    pushToNearbyOwnedPets(player, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.18f);
                        pc.pushEmotion(PetComponent.Emotion.ANGST, 0.12f);
                    }, false);
                }
                if (biomeEntry.isIn(OCEANIC_BIOMES)) {
                    pushToNearbyOwnedPets(player, 48, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.08f), false);
                }
                if (biomeEntry.isIn(SNOWY_BIOMES)) {
                    pushToNearbyOwnedPets(player, 48, pc -> pc.pushEmotion(PetComponent.Emotion.STOIC, 0.10f), false);
                }
            }
            Identifier newBiomeId = biomeKey.getValue();
            Text biomeName = Text.translatable("biome." + newBiomeId.getNamespace() + "." + newBiomeId.getPath());
            pushToNearbyOwnedPets(player, 48, pc -> {
                if (pc.hasRole(PetRoleType.SCOUT)) {
                    MobEntity scoutPet = pc.getPet();
                    if (scoutPet != null) {
                        EmotionContextCues.sendCue(player,
                            "role.scout.biome." + scoutPet.getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.role.scout_biome",
                                scoutPet.getDisplayName(), biomeName),
                            600);
                    }
                }
            });
        }

        if (state.dimensionKey == null || !dimensionKey.equals(state.dimensionKey)) {
            RegistryKey<World> previous = state.dimensionKey;
            handleDimensionChange(player, state, previous, dimensionKey);
        }

        state.lastPos = pos;
        state.lastMovementTick = now;
        state.lastIdleCueTick = 0L;
        state.lastRainyIdleCueTick = 0L;
    }

    private static void handlePlayerFall(ServerPlayerEntity player, double distance) {
        float magnitude = (float) Math.min(0.12d, 0.02d * distance);
        if (distance < 1.5d) {
            pushToNearbyOwnedPets(player, 16, pc -> {
                long now = player.getWorld().getTime();
                if (tryMarkPetBeat(pc, "owner_small_hop", now, 40L)) {
                    pc.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.03f + magnitude * 0.6f);
                    pc.pushEmotion(PetComponent.Emotion.GLEE, 0.02f + magnitude * 0.4f);
                }
            });
            return;
        }

        if (distance < 6.0d) {
            pushToNearbyOwnedPets(player, 24, pc -> {
                pc.pushEmotion(PetComponent.Emotion.STARTLE, magnitude);
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.05f + magnitude * 0.5f);
            });
            return;
        }

        pushToNearbyOwnedPets(player, 28, pc -> {
            long now = player.getWorld().getTime();
            if (tryMarkPetBeat(pc, "owner_big_fall", now, 160L)) {
                pc.pushEmotion(PetComponent.Emotion.STARTLE, magnitude);
                pc.pushEmotion(PetComponent.Emotion.ANGST, 0.06f + magnitude * 0.6f);
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.07f + magnitude * 0.5f);
                if (player.getHealth() > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.05f + magnitude * 0.4f);
                }
            } else {
                pc.pushEmotion(PetComponent.Emotion.STARTLE, magnitude * 0.5f);
            }
        });
    }

    private enum KillContext {
        HOSTILE("hostile"),
        PASSIVE("passive"),
        BOSS("boss");

        private final String cueSuffix;

        KillContext(String cueSuffix) {
            this.cueSuffix = cueSuffix;
        }

        String cueSuffix() {
            return cueSuffix;
        }
    }

    private static KillContext classifyKillTarget(@Nullable LivingEntity killed) {
        if (killed == null) {
            return KillContext.PASSIVE;
        }
        if (isBossTarget(killed)) {
            return KillContext.BOSS;
        }
        if (isHostileTarget(killed)) {
            return KillContext.HOSTILE;
        }
        return KillContext.PASSIVE;
    }

    private static boolean isHostileTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return true;
        }
        SpawnGroup group = entity.getType().getSpawnGroup();
        return entity instanceof HostileEntity || group == SpawnGroup.MONSTER;
    }

    private static boolean isBossTarget(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return type.isIn(EntityTypeTags.RAIDERS)
            || type == EntityType.ELDER_GUARDIAN
            || type == EntityType.WARDEN
            || type == EntityType.ENDER_DRAGON
            || type == EntityType.WITHER;
    }

    private static TimePhase computeTimePhase(long timeOfDay) {
        long t = timeOfDay % 24000L;
        if (t < 1000L || t >= 23000L) return TimePhase.DAWN;
        if (t < 12000L) return TimePhase.DAY;
        if (t < 13000L) return TimePhase.DUSK;
        return TimePhase.NIGHT;
    }

    // Activity detection: check if player is building/crafting (recent block place, interaction, or item use)
    private static boolean hasNearbyActivity(ServerPlayerEntity player, ServerWorld world) {
        // Simple heuristic: if player has been interacting recently, skip ENNUI
        // This could be enhanced with more sophisticated detection
        long time = world.getTime();
        // Check if any owned pets are nearby and actively engaged
        return world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
            player.getBoundingBox().expand(16), mob -> {
                var pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(player) && 
                       (time - pc.getLastAttackTick()) < 600; // Pet active in last 30s
            }).size() > 0;
    }

    // ==== Utilities ====
    private interface PetConsumer { void accept(PetComponent pc); }

    private static StimulusSummary pushToNearbyOwnedPets(ServerPlayerEntity owner, double radius, PetConsumer consumer) {
        return pushToNearbyOwnedPets(owner, radius, consumer, true);
    }

    private static StimulusSummary pushToNearbyOwnedPets(ServerPlayerEntity owner, double radius, PetConsumer consumer,
                                                         boolean recordStimulus) {
        List<MobEntity> pets = owner.getWorld().getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(radius),
            mob -> {
                PetComponent pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(owner);
            }
        );
        StimulusSummary.Builder builder = StimulusSummary.builder(owner.getWorld().getTime());
        for (MobEntity pet : pets) {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) {
                continue;
            }
            try {
                MoodService.getInstance().getStimulusBus().queueStimulus(pet, component -> {
                    Map<PetComponent.Mood, Float> before = snapshotMoodBlend(component);
                    consumer.accept(component);
                    component.updateMood();
                    Map<PetComponent.Mood, Float> after = snapshotMoodBlend(component);
                    builder.addSample(before, after);
                });
                MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            } catch (Throwable ignored) {}
        }
        StimulusSummary summary = builder.build();
        if (recordStimulus) {
            EmotionContextCues.recordStimulus(owner, summary);
        }
        return summary;
    }

    private static void forEachOwnedPet(ServerPlayerEntity owner, double radius,
                                        BiConsumer<MobEntity, PetComponent> consumer) {
        List<MobEntity> pets = owner.getWorld().getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(radius),
            mob -> {
                PetComponent pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(owner);
            }
        );
        for (MobEntity pet : pets) {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                consumer.accept(pet, component);
            }
        }
    }

    private static Map<PetComponent.Mood, Float> snapshotMoodBlend(PetComponent pc) {
        return new EnumMap<>(pc.getMoodBlend());
    }

    private static StimulusSummary pushToSinglePet(ServerPlayerEntity owner, PetComponent pc, PetConsumer consumer) {
        if (owner == null || pc == null) {
            return StimulusSummary.empty(owner != null ? owner.getWorld().getTime() : 0L);
        }
        StimulusSummary.Builder builder = StimulusSummary.builder(owner.getWorld().getTime());
        MoodService.getInstance().getStimulusBus().queueStimulus(pc.getPet(), component -> {
            Map<PetComponent.Mood, Float> before = snapshotMoodBlend(component);
            consumer.accept(component);
            component.updateMood();
            Map<PetComponent.Mood, Float> after = snapshotMoodBlend(component);
            builder.addSample(before, after);
        });
        MoodService.getInstance().getStimulusBus().dispatchStimuli(pc.getPet());
        StimulusSummary summary = builder.build();
        EmotionContextCues.recordStimulus(owner, summary);
        return summary;
    }

    private static StimulusSummary applyConfiguredStimulus(ServerPlayerEntity owner, String definitionId,
                                                           double defaultRadius, PetConsumer consumer) {
        EmotionCueDefinition definition = EmotionCueConfig.get().definition(definitionId);
        double radius = definition != null ? definition.resolvedRadius(defaultRadius) : defaultRadius;
        return pushToNearbyOwnedPets(owner, radius, pc -> {
            if (definition != null) {
                definition.applyBaseEmotions(pc);
            }
            consumer.accept(pc);
        });
    }

    private static void triggerConfiguredCue(ServerPlayerEntity owner, String definitionId, double defaultRadius,
                                             String fallbackKey, Object... args) {
        triggerConfiguredCue(owner, definitionId, defaultRadius, pc -> {}, fallbackKey, args);
    }

    private static void triggerConfiguredCue(ServerPlayerEntity owner, String definitionId, double defaultRadius,
                                             PetConsumer consumer, String fallbackKey, Object... args) {
        applyConfiguredStimulus(owner, definitionId, defaultRadius, consumer);
        Text cueText = resolveCueText(definitionId, fallbackKey, args);
        if (cueText != null && !cueText.getString().isEmpty()) {
            EmotionContextCues.sendCue(owner, definitionId, cueText);
        }
    }

    private static void emitPetCue(ServerPlayerEntity owner, PetComponent pc, String cueId, PetConsumer consumer,
                                   String fallbackKey, long fallbackCooldown, Object... args) {
        String definitionId = resolveDefinitionId(cueId);
        EmotionCueDefinition definition = EmotionCueConfig.get().definition(definitionId);
        pushToSinglePet(owner, pc, petComponent -> {
            if (definition != null) {
                definition.applyBaseEmotions(petComponent);
            }
            consumer.accept(petComponent);
        });
        Text cueText = resolveCueText(definitionId, fallbackKey, args);
        if (cueText != null && !cueText.getString().isEmpty()) {
            EmotionContextCues.sendCue(owner, cueId, cueText, fallbackCooldown);
        }
    }

    private static Text resolveCueText(String definitionId, String fallbackKey, Object... args) {
        Text configured = EmotionCueConfig.get().resolveText(definitionId, args);
        if (configured != null) {
            String resolved = configured.getString();
            if (resolved != null && !resolved.isEmpty()) {
                return configured;
            }
        }
        if (fallbackKey != null) {
            return Text.translatable(fallbackKey, args);
        }
        return Text.empty();
    }

    private static String resolveDefinitionId(String cueId) {
        int idx = cueId.lastIndexOf('.');
        if (idx < 0) {
            return cueId;
        }
        String tail = cueId.substring(idx + 1);
        try {
            java.util.UUID.fromString(tail);
            return cueId.substring(0, idx);
        } catch (IllegalArgumentException ignored) {
            return cueId;
        }
    }

    // ==== 4th Wave: Nuanced Living System - Subtle Environmental & Social Awareness ====
    private static void handleNuancedEmotions(ServerPlayerEntity player, ServerWorld world, long currentTick) {
        PetSwarmIndex swarm = StateManager.forWorld(world).getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> nearbyPets = new ArrayList<>();
        swarm.forEachPetInRange(player, player.getPos(), 32.0, nearbyPets::add);

        if (nearbyPets.isEmpty()) {
            return;
        }

        processPetBatch(nearbyPets, player, world, currentTick, swarm);
    }

    // Optimized batch processing to reduce redundant calculations
    private static void processPetBatch(List<PetSwarmIndex.SwarmEntry> pets,
                                        ServerPlayerEntity owner,
                                        ServerWorld world,
                                        long currentTick,
                                        PetSwarmIndex swarm) {
        Map<MobEntity, PetSocialData> petDataCache = new HashMap<>(pets.size());

        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pc == null) {
                continue;
            }
            petDataCache.put(pet, new PetSocialData(entry, currentTick));
        }

        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pc == null) {
                continue;
            }

            PetSocialData petData = petDataCache.get(pet);
            if (petData == null) {
                continue;
            }

            MoodService.getInstance().getStimulusBus().queueStimulus(pet, component -> {
                addSocialAwarenessTriggers(pet, component, owner, world);
                addEnvironmentalMicroTriggers(pet, component, owner, world);
                addMovementActivityTriggers(pet, component, owner, world);
                addRoleSpecificAmbientTriggers(pet, component, owner, world);
                addSpeciesSpecificTriggers(pet, component, owner, world);

                processSocialRoutines(pet, component, petDataCache, owner, world, currentTick, swarm);
            });
            MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
        }
    }
    
    private static void processSocialRoutines(MobEntity pet, PetComponent pc,
                                              Map<MobEntity, PetSocialData> petDataCache,
                                              ServerPlayerEntity owner, ServerWorld world, long currentTick,
                                              PetSwarmIndex swarm) {
        PetSocialData petData = petDataCache.computeIfAbsent(pet, key -> new PetSocialData(pet, pc, currentTick));
        SocialContextSnapshot context = new SocialContextSnapshot(pet, pc, owner, world, currentTick, petData, petDataCache);
        for (SocialBehaviorRoutine routine : SOCIAL_ROUTINES) {
            context.resetTransientState();
            if (!routine.shouldRun(context)) {
                continue;
            }
            routine.gatherContext(context, swarm, currentTick);
            routine.applyEffects(context);
        }
        if (tryMarkPetBeat(pc, "social_hierarchy", currentTick, 1200)) {
            calculateSimplifiedHierarchy(pet, pc, petDataCache, owner, currentTick, swarm);
        }
    }

    // Simplified hierarchy system with reduced computational overhead
    private static void calculateSimplifiedHierarchy(MobEntity pet, PetComponent pc,
                                                    Map<MobEntity, PetSocialData> petDataCache,
                                                    ServerPlayerEntity owner, long currentTick,
                                                    PetSwarmIndex swarm) {
        PetSocialData petData = petDataCache.computeIfAbsent(pet, key -> new PetSocialData(pet, pc, currentTick));

        final int[] counts = new int[2];

        swarm.forEachNeighbor(pet, pc, 10.0, (entry, distance) -> {
            if (distance > 100) {
                return;
            }

            PetSocialData otherData = petDataCache.computeIfAbsent(entry.pet(),
                key -> new PetSocialData(entry, currentTick));

            counts[0]++;
            if (otherData.age() < petData.age()) {
                counts[1]++;
            }
        });

        int totalNearby = counts[0];
        int youngerPets = counts[1];

        if (totalNearby == 0) return; // No social context

        // Simplified rank calculation
        float hierarchyPosition = (float) youngerPets / totalNearby; // 0 = lowest, 1 = highest
        
        if (hierarchyPosition > 0.7f) {
            // High rank - leadership emotions
            pc.pushEmotion(PetComponent.Emotion.PRIDE, 0.06f);
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.04f);
            EmotionContextCues.sendCue(owner, "social.alpha." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.alpha", pet.getDisplayName()), 600);
        } else if (hierarchyPosition < 0.3f) {
            // Low rank - follower emotions
            pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.05f);
            pc.pushEmotion(PetComponent.Emotion.LOYALTY, 0.04f);
            EmotionContextCues.sendCue(owner, "social.follower." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.follower", pet.getDisplayName()), 500);
        }
    }

    private static void addSocialAwarenessTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        double distanceToOwner = pet.squaredDistanceTo(owner);
        Vec3d ownerLookDir = owner.getRotationVec(1.0f);
        Vec3d petToPet = pet.getPos().subtract(owner.getPos()).normalize();
        double lookAlignment = ownerLookDir.dotProduct(petToPet);

        // Owner looking directly at pet - awareness and attention
        if (distanceToOwner < 64 && lookAlignment > 0.8) { // Owner looking at pet
            pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f); // Reduced from 0.08f to 0.03f - Cozy attention
            if (pet.getHealth() / pet.getMaxHealth() < 0.7f) {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f); // Owner noticing when hurt
            }
            EmotionContextCues.sendCue(owner, "social.look." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.look", pet.getDisplayName()), 200);
        }

        // Owner proximity dynamics
        if (distanceToOwner < 4) { // Very close
            pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.025f); // Reduced from 0.06f to 0.025f - Home/safety feeling
            EmotionContextCues.sendCue(owner, "social.close." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.close", pet.getDisplayName()), 200);
        } else if (distanceToOwner > 256) { // Far away
            pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.04f); // Longing
            EmotionContextCues.sendCue(owner, "social.far." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.far", pet.getDisplayName()), 400);
        }

        // Owner's current activity awareness
        if (owner.getAttackCooldownProgress(0.0f) < 1.0f) {
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.05f);
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
            EmotionContextCues.sendCue(owner, "social.combat." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.combat", pet.getDisplayName()), 200);
        }

        if (owner.isSneaking() && distanceToOwner < 16) {
            pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.03f); // Mysterious quiet behavior
            EmotionContextCues.sendCue(owner, "social.sneak." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.sneak", pet.getDisplayName()), 200);
            if (pc.hasRole(PetRoleType.ECLIPSED)) {
                EmotionContextCues.sendCue(owner,
                    "role.eclipsed.shadow." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.eclipsed_shroud", pet.getDisplayName()),
                    200);
            }
        }

        // Owner health awareness
        float ownerHealthPercent = owner.getHealth() / owner.getMaxHealth();
        if (ownerHealthPercent < 0.3f && distanceToOwner < 64) {
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.15f); // Worry about hurt owner
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.12f);
            EmotionContextCues.sendCue(owner, "social.owner_hurt." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.owner_hurt", pet.getDisplayName()), 200);
            if (pc.hasRole(PetRoleType.GUARDIAN)) {
                EmotionContextCues.sendCue(owner,
                    "role.guardian.vigil." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.guardian_vigil", pet.getDisplayName()),
                    200);
            }
        }
    }

    private static void addEnvironmentalMicroTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        BlockPos petPos = pet.getBlockPos();

        // Light level awareness
        int lightLevel = world.getLightLevel(petPos);
        if (lightLevel <= 3) {
            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.04f); // Dark places feel ominous
            EmotionContextCues.sendCue(owner, "environment.dark." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.dark", pet.getDisplayName()), 200);
        } else if (lightLevel >= 12) {
            pc.pushEmotion(PetComponent.Emotion.KEFI, 0.02f); // Bright areas feel energizing
            EmotionContextCues.sendCue(owner, "environment.bright." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.bright", pet.getDisplayName()), 400);
        }

        // Height awareness
        int y = petPos.getY();
        if (y > 120) { // High altitude
            pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.03f); // Awe at heights
            pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.02f); // Wanderlust from vistas
            EmotionContextCues.sendCue(owner, "environment.high." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.high", pet.getDisplayName()), 400);
        } else if (y < 20) { // Deep underground
            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.05f); // Underground unease
            EmotionContextCues.sendCue(owner, "environment.deep." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.deep", pet.getDisplayName()), 400);
        }

        // Water proximity
        if (world.getBlockState(petPos.down()).getFluidState().isEmpty() == false) {
            pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.03f); // Water brings balance
            EmotionContextCues.sendCue(owner, "environment.water." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.water", pet.getDisplayName()), 400);
        }

        // Flowers and nature
        for (BlockPos offset : BlockPos.iterate(petPos.add(-2, -1, -2), petPos.add(2, 1, 2))) {
            if (world.getBlockState(offset).isIn(NATURE_PLANTS)) {
                pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.02f); // Beauty of nature
                EmotionContextCues.sendCue(owner, "environment.flower." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.environment.flower", pet.getDisplayName()), 400);
                break;
            }
        }

        // Hostile mob proximity (extended awareness) - more nuanced detection with fatigue
        var nearbyHostiles = world.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,
            pet.getBoundingBox().expand(16), monster -> true);
        if (!nearbyHostiles.isEmpty()) {
            // Scale fear based on count and variety, with clustering penalty
            java.util.Set<String> hostileTypes = new java.util.HashSet<>();
            for (var hostile : nearbyHostiles) {
                hostileTypes.add(hostile.getType().toString());
            }
            
            float varietyFactor = Math.min(hostileTypes.size() / 3.0f, 1.0f);
            float countFactor = Math.min(1.0f + (nearbyHostiles.size() - 1) * 0.15f, 2.0f);
            
            // Check if mobs are clustered (potential mob farm)
            boolean isClusteredFarm = nearbyHostiles.size() >= 4 && checkIfClustered(nearbyHostiles);
            float clusterPenalty = isClusteredFarm ? 0.3f : 1.0f;
            
            float baseForeboding = (0.03f + varietyFactor * 0.03f) * countFactor * clusterPenalty;
            float baseProtectiveness = (0.02f + varietyFactor * 0.02f) * countFactor * clusterPenalty;
            
            // Apply fatigue to reduce repetitive fear from the same hostile situation
            long currentTime = world.getTime();
            String hostileSignature = createHostileSignature(nearbyHostiles, hostileTypes);
            float fatigueFactor = calculateHostileFatigue(pc, hostileSignature, currentTime, baseForeboding, 300); // 15s fatigue window
            
            if (fatigueFactor > 0.2f) { // Only trigger if not heavily fatigued
                pc.pushEmotion(PetComponent.Emotion.FOREBODING, baseForeboding * fatigueFactor);
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, baseProtectiveness * fatigueFactor);
            }
            EmotionContextCues.sendCue(owner, "environment.hostiles." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.hostiles", pet.getDisplayName()), 200);
            if (pc.hasRole(PetRoleType.GUARDIAN)) {
                EmotionContextCues.sendCue(owner,
                    "role.guardian.hold." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.guardian_hold", pet.getDisplayName()),
                    200);
            }
        }

    }

    private static void addMovementActivityTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        Vec3d velocity = pet.getVelocity();
        double speed = velocity.length();
        boolean isCat = pet instanceof CatEntity;

        // Movement patterns
        if (speed > 0.2) { // Pet is moving fast
            pc.pushEmotion(PetComponent.Emotion.KEFI, 0.04f); // Energy from movement
            pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.02f); // Adventure spirit
            EmotionContextCues.sendCue(owner, "movement.run." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.run", pet.getDisplayName()), 200);
        } else if (speed < 0.01) { // Pet is very still
            pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.03f); // Peaceful stillness
            EmotionContextCues.sendCue(owner, "movement.still." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.still", pet.getDisplayName()), 200);
        }

        // Falling or jumping - more nuanced responses
        if (velocity.y < -0.8) { // Falling very fast - concern
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.06f);
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.04f);
            EmotionContextCues.sendCue(owner, "movement.fall." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.fall", pet.getDisplayName()), 200);
        } else if (velocity.y > 0.4) { // Jumping up - joy but reasonable
            pc.pushEmotion(PetComponent.Emotion.KEFI, 0.04f); // Reduced joy of leaping
            EmotionContextCues.sendCue(owner, "movement.jump." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.jump", pet.getDisplayName()), 200);
        }

        // Swimming - more balanced reactions
        if (pet.isInFluid()) {
            if (isCat) {
                pc.pushEmotion(PetComponent.Emotion.DISGUST, 0.08f); // Cats dislike water but not extreme
                pc.pushEmotion(PetComponent.Emotion.ANGST, 0.06f);
                EmotionContextCues.sendCue(owner, "movement.cat_swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.cat_swim", pet.getDisplayName()), 200);
            } else {
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f); // Others find it refreshing
                EmotionContextCues.sendCue(owner, "movement.swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.swim", pet.getDisplayName()), 200);
            }
        }
    }
    private static void addRoleSpecificAmbientTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        PetRoleType roleType = pc.getRoleType(false);
        if (roleType == null) {
            return;
        }

        Identifier roleId = roleType.id();

        if (PetRoleType.STRIKER_ID.equals(roleId)) {
            LivingEntity target = pet.getTarget();
            if (target != null && target.isAlive() && target != owner) {
                float maxHealth = target.getMaxHealth();
                float pct = maxHealth > 0.0f ? target.getHealth() / maxHealth : 1.0f;
                if (pct <= 0.35f) {
                    EmotionContextCues.sendCue(owner,
                        "role.striker.mark." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.role.striker_mark",
                            pet.getDisplayName(), target.getDisplayName()),
                        200);
                }
            }
        }

        if (PetRoleType.SUPPORT_ID.equals(roleId)) {
            Boolean present = pc.getStateData("support_potion_present", Boolean.class);
            if (Boolean.TRUE.equals(present)) {
                Double total = pc.getStateData("support_potion_total_charges", Double.class);
                Double remaining = pc.getStateData("support_potion_charges_remaining", Double.class);
                if (total != null && total > 0.0 && remaining != null) {
                    double ratio = remaining / total;
                    if (ratio <= 0.25d) {
                        EmotionContextCues.sendCue(owner,
                            "role.support.potion_low." + pet.getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.role.support_low", pet.getDisplayName()),
                            600);
                    }
                }
            }
        }

        if (PetRoleType.SKYRIDER_ID.equals(roleId)) {
            if (!owner.isOnGround() && owner.fallDistance > 4.0f && owner.getVelocity().y < -0.6f) {
                EmotionContextCues.sendCue(owner,
                    "role.skyrider.catch." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.skyrider_dive", pet.getDisplayName()),
                    200);
            }
        }

        if (PetRoleType.CURSED_ONE_ID.equals(roleId)) {
            RegistryKey<World> worldKey = world.getRegistryKey();
            if (worldKey == World.NETHER) {
                EmotionContextCues.sendCue(owner,
                    "role.cursed.nether." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.cursed_nether", pet.getDisplayName()),
                    600);
            } else if (world.getLightLevel(pet.getBlockPos()) <= 3) {
                EmotionContextCues.sendCue(owner,
                    "role.cursed.gloom." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.cursed_gloom", pet.getDisplayName()),
                    400);
            }
        }
    }

    private static void addSpeciesSpecificTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        long now = world.getTime();
        BlockPos petPos = pet.getBlockPos();

        if (pet instanceof AbstractHorseEntity) {
            if (pet.hasPassenger(owner) && tryMarkPetBeat(pc, "horse_ride", now, 200)) {
                double speed = pet.getVelocity().horizontalLength();
                emitPetCue(owner, pc, "species.horse.ride." + pet.getUuidAsString(), component -> {
                    if (speed > 0.1d) {
                        float boost = (float) Math.min(0.12d, speed * 0.3d);
                        component.pushEmotion(PetComponent.Emotion.KEFI, boost);
                        component.pushEmotion(PetComponent.Emotion.FERNWEH, boost * 0.8f);
                    }
                }, null, 200, pet.getDisplayName(), owner.getDisplayName());
            }

            if (isBlockInRange(world, petPos, STABLE_FEED, 2, 1)
                && tryMarkPetBeat(pc, "horse_graze", now, 320)) {
                emitPetCue(owner, pc, "species.horse.graze." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof ParrotEntity parrot) {
            if (isJukeboxPlaying(world, petPos, 4) && tryMarkPetBeat(pc, "parrot_dance", now, 240)) {
                emitPetCue(owner, pc, "species.parrot.dance." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }

            if (!parrot.isOnGround() && !parrot.isTouchingWater()
                && pet.getVelocity().lengthSquared() > 0.08d
                && tryMarkPetBeat(pc, "parrot_flight", now, 200)) {
                double altitude = petPos.getY() - owner.getBlockPos().getY();
                emitPetCue(owner, pc, "species.parrot.flight." + pet.getUuidAsString(), component -> {
                    if (altitude > 1.0d) {
                        float lift = (float) Math.max(0.03d, Math.min(0.12d, altitude * 0.02d));
                        component.pushEmotion(PetComponent.Emotion.FERNWEH, lift);
                    }
                }, null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof CatEntity cat) {
            if (cat.isSitting()) {
                if (isOnBlock(world, petPos, BedBlock.class)
                    && world.isNight()
                    && tryMarkPetBeat(pc, "cat_bed", now, 400)) {
                    emitPetCue(owner, pc, "species.cat.bed." + pet.getUuidAsString(), component -> {},
                        null, 200, pet.getDisplayName());
                }

                if (isOnStorageBlock(world, petPos)
                    && tryMarkPetBeat(pc, "cat_chest", now, 360)) {
                    emitPetCue(owner, pc, "species.cat.chest." + pet.getUuidAsString(), component -> {},
                        null, 200, pet.getDisplayName());
                }
            }
        }

        if (pet instanceof OcelotEntity) {
            if (pet.isSneaking() && tryMarkPetBeat(pc, "ocelot_stalk", now, 200)) {
                emitPetCue(owner, pc, "species.ocelot.stalk." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }

            if (world.getBiome(petPos).isIn(BiomeTags.IS_JUNGLE)
                && tryMarkPetBeat(pc, "ocelot_jungle", now, 400)) {
                emitPetCue(owner, pc, "species.ocelot.jungle." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof FoxEntity fox) {
            if (fox.isSleeping() && tryMarkPetBeat(pc, "fox_sleep", now, 320)) {
                emitPetCue(owner, pc, "species.fox.sleep." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }

            if ((!fox.getMainHandStack().isEmpty() || !fox.getOffHandStack().isEmpty())
                && tryMarkPetBeat(pc, "fox_treasure", now, 200)) {
                emitPetCue(owner, pc, "species.fox.treasure." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof WolfEntity) {
            if ((owner.getMainHandStack().isOf(Items.BONE) || owner.getOffHandStack().isOf(Items.BONE))
                && tryMarkPetBeat(pc, "wolf_treat", now, 160)) {
                emitPetCue(owner, pc, "species.wolf.treat." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName(), owner.getDisplayName());
            }

            if (isBlockInRange(world, petPos, BlockTags.CAMPFIRES, 3, 1)
                && tryMarkPetBeat(pc, "wolf_campfire", now, 320)) {
                emitPetCue(owner, pc, "species.wolf.campfire." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }

            if (world.isNight() && world.getMoonSize() > 0.75f
                && tryMarkPetBeat(pc, "wolf_howl", now, 600)) {
                emitPetCue(owner, pc, "species.wolf.howl." + pet.getUuidAsString(), component -> {},
                    null, 200, pet.getDisplayName());
            }
        }
    }

    private static boolean tryMarkPetBeat(PetComponent pc, String key, long now, long interval) {
        String stateKey = "species_" + key;
        Long last = pc.getStateData(stateKey, Long.class);
        if (last != null && now - last < interval) {
            return false;
        }
        pc.setStateData(stateKey, now);
        return true;
    }

    private static boolean isBlockInRange(ServerWorld world, BlockPos center, TagKey<Block> tag, int horizontal, int vertical) {
        for (BlockPos pos : BlockPos.iterate(center.add(-horizontal, -vertical, -horizontal),
            center.add(horizontal, vertical, horizontal))) {
            if (world.getBlockState(pos).isIn(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnBlock(ServerWorld world, BlockPos pos, Class<? extends Block> clazz) {
        Block stateBlock = world.getBlockState(pos).getBlock();
        if (clazz.isInstance(stateBlock)) {
            return true;
        }
        Block below = world.getBlockState(pos.down()).getBlock();
        return clazz.isInstance(below);
    }

    private static boolean isOnStorageBlock(ServerWorld world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof ChestBlock || block instanceof BarrelBlock) {
            return true;
        }
        Block below = world.getBlockState(pos.down()).getBlock();
        return below instanceof ChestBlock || below instanceof BarrelBlock;
    }

    private static boolean isJukeboxPlaying(ServerWorld world, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -1, -radius), center.add(radius, 1, radius))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof JukeboxBlock && state.get(JukeboxBlock.HAS_RECORD)) {
                return true;
            }
        }
        return false;
    }

    // ==== 5th Wave: Tag-Based and Data-Driven Advanced Emotion Hooks ====

    /**
     * Enchanting table interactions - magic and wonder
     */
    private static ActionResult onEnchantingTableUse(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        net.minecraft.block.BlockState state = world.getBlockState(pos);

        // Check if it's an enchanting table using block tags (flexible)
        if (state.isIn(BlockTags.ENCHANTMENT_POWER_PROVIDER) || state.isOf(Blocks.ENCHANTING_TABLE)) {

            pushToNearbyOwnedPets(sp, 24, pc -> {
                pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.18f); // Wonder at magic
                pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.08f); // Slight wariness of unknown
                pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.06f); // Beauty of magical sparkles
                if (pc.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
                    MobEntity petEntity = pc.getPet();
                    if (petEntity != null) {
                        EmotionContextCues.sendCue(sp,
                            "role.enchanter.attune." + petEntity.getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.role.enchanter_attune", petEntity.getDisplayName()),
                            200);
                    }
                }
            });
            EmotionContextCues.sendCue(sp, "block_use.enchanting", Text.translatable("petsplus.emotion_cue.block_use.enchanting"), 200);
        }

        return ActionResult.PASS;
    }

    /**
     * Trading interactions - using entity tags and villager profession data
     */
    private static ActionResult onVillagerTrade(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // Use entity tags and types rather than hardcoding
        if (entity instanceof VillagerEntity villager) {
            RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
            boolean isFoodTrader = profession.matchesKey(VillagerProfession.BUTCHER)
                || profession.matchesKey(VillagerProfession.FARMER);
            boolean isMystic = profession.matchesKey(VillagerProfession.CLERIC);
            boolean isGuard = profession.matchesKey(VillagerProfession.WEAPONSMITH)
                || profession.matchesKey(VillagerProfession.ARMORER);
            pushToNearbyOwnedPets(sp, 20, pc -> {
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.12f); // Social interaction comfort
                pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.08f); // Observing human customs

                // Professional-specific reactions
                if (isFoodTrader) {
                    pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f); // Food security feelings
                } else if (isMystic) {
                    pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.08f); // Mystical presence
                } else if (isGuard) {
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f); // Defense associations
                }
            });
            EmotionContextCues.sendCue(sp, "entity.trade", Text.translatable("petsplus.emotion_cue.entity.trade"), 200);
            if (isFoodTrader) {
                EmotionContextCues.sendCue(sp, "entity.trade.food", Text.translatable("petsplus.emotion_cue.entity.trade_food"), 400);
            } else if (isMystic) {
                EmotionContextCues.sendCue(sp, "entity.trade.mystic", Text.translatable("petsplus.emotion_cue.entity.trade_mystic"), 400);
            } else if (isGuard) {
                EmotionContextCues.sendCue(sp, "entity.trade.guard", Text.translatable("petsplus.emotion_cue.entity.trade_guard"), 400);
            }

            NatureFlavorHandler.triggerForOwner(sp, 20, Trigger.VILLAGER_TRADE);
        }

        return ActionResult.PASS;
    }

    /**
     * Advanced weather and item awareness using tags and component data
     */
    private static void onWorldTickAdvancedTriggers(ServerWorld world) {
        // Run every 5 seconds to keep it lightweight
        if (world.getTime() % 100 != 0) return;

        for (ServerPlayerEntity player : world.getPlayers()) {
            List<MobEntity> pets = world.getEntitiesByClass(MobEntity.class,
                player.getBoundingBox().expand(32),
                mob -> {
                    PetComponent pc = PetComponent.get(mob);
                    return pc != null && pc.isOwnedBy(player);
                }
            );

            for (MobEntity pet : pets) {
                PetComponent pc = PetComponent.get(pet);
                if (pc == null) continue;

                MoodService.getInstance().getStimulusBus().queueStimulus(pet, component -> {
                    // Enhanced weather awareness
                    addAdvancedWeatherTriggers(pet, component, player, world);

                    // Tag-based item awareness
                    addTagBasedItemTriggers(pet, component, player);
                });
                MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            }
        }
    }

    private static void addAdvancedWeatherTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        boolean isCat = pet instanceof CatEntity;
        boolean isWolf = pet instanceof WolfEntity;
        BlockPos petPos = pet.getBlockPos();
        boolean exposedToSky = world.isSkyVisible(petPos);
        boolean soaked = pet.isTouchingWater() || ((world.isRaining() || world.isThundering()) && exposedToSky);
        boolean shelteredAndDry = !exposedToSky && !soaked;
        long now = world.getTime();

        if (world.isThundering()) {
            float exposure = (exposedToSky ? 1.0f : 0.5f) + (soaked ? 0.3f : 0f);
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.05f + 0.05f * exposure);
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.04f + 0.04f * exposure);
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, shelteredAndDry ? 0.04f : 0.05f + 0.04f * exposure);

            if (!shelteredAndDry) {
                EmotionContextCues.sendCue(owner, "weather.thunder.pet." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.thunder_pet", pet.getDisplayName()), 400);
            } else if (tryMarkPetBeat(pc, "weather_shelter", now, 200L)) {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.04f);
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f);
                EmotionContextCues.sendCue(owner, "weather.shelter." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.shelter", pet.getDisplayName()), 400);
            }
        } else if (world.isRaining()) {
            if (shelteredAndDry) {
                if (tryMarkPetBeat(pc, "weather_shelter", now, 200L)) {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.03f);
                    pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.02f);
                    EmotionContextCues.sendCue(owner, "weather.shelter." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.weather.shelter", pet.getDisplayName()), 400);
                }
            } else if (isCat) {
                float discomfort = exposedToSky && soaked ? 0.10f : 0.08f;
                pc.pushEmotion(PetComponent.Emotion.DISGUST, discomfort);
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.06f + (soaked ? 0.02f : 0f));
                EmotionContextCues.sendCue(owner, "weather.rain.cat." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_cat", pet.getDisplayName()), 400);
            } else if (isWolf) {
                float delight = exposedToSky || soaked ? 0.05f : 0.03f;
                pc.pushEmotion(PetComponent.Emotion.KEFI, delight);
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f + (soaked ? 0.02f : 0f));
                EmotionContextCues.sendCue(owner, "weather.rain.dog." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_dog", pet.getDisplayName()), 400);
            } else {
                float refresh = exposedToSky || soaked ? 0.03f : 0.015f;
                pc.pushEmotion(PetComponent.Emotion.LAGOM, refresh);
                if (exposedToSky || soaked) {
                    EmotionContextCues.sendCue(owner, "weather.rain.pet." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.weather.rain_pet", pet.getDisplayName()), 400);
                }
            }
        } else {
            if (shouldCelebrateClearWeather(pet, world)) {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.06f);
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.04f); // Energy from clear skies
                EmotionContextCues.sendCue(owner, "weather.clear." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.clear", pet.getDisplayName()), 6000);
            }
        }

        // Temperature simulation based on biome data
        try {
            var biome = world.getBiome(pet.getBlockPos());
            float temperature = biome.value().getTemperature();

            if (temperature > 1.0f) { // Hot biomes
                pc.pushEmotion(PetComponent.Emotion.LAGOM, -0.02f); // Slight discomfort
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.03f); // Seeking shade
                EmotionContextCues.sendCue(owner, "environment.hot." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.environment.hot", pet.getDisplayName()), 600);
            } else if (temperature < 0.0f) { // Cold biomes
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.04f); // Seeking warmth/owner
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f); // Cozy feelings
                EmotionContextCues.sendCue(owner, "environment.cold." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.environment.cold", pet.getDisplayName()), 600);
            }
        } catch (Exception ignored) {}
    }

    private static boolean shouldCelebrateClearWeather(MobEntity pet, ServerWorld world) {
        if (world.isRaining() || world.isThundering()) {
            return false;
        }
        Long lastWet = LAST_WET_WEATHER_TICK.get(world);
        if (lastWet == null) {
            return false;
        }
        long now = world.getTime();
        if (now - lastWet > 200L) {
            return false;
        }
        Long lastCelebrated = LAST_CLEAR_WEATHER_TRIGGER.get(pet.getUuid());
        if (lastCelebrated != null && lastCelebrated >= lastWet) {
            return false;
        }
        LAST_CLEAR_WEATHER_TRIGGER.put(pet.getUuid(), now);
        return true;
    }

    private static void addTagBasedItemTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner) {
        // Analyze owner's inventory using item tags and components rather than hardcoding
        var inventory = owner.getInventory();

        boolean hasValuableItems = false;
        boolean hasFoodItems = false;
        boolean hasMagicalItems = false;
        boolean hasWeapons = false;
        boolean hasTools = false;
        boolean hasArchaeology = false;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            // Use item tags for flexible detection
            if (stack.isIn(ItemTags.TRIM_MATERIALS) || stack.isIn(VALUABLE_ITEMS)) {
                hasValuableItems = true;
            }

            if (stack.get(DataComponentTypes.FOOD) != null ||
                stack.isIn(ItemTags.MEAT) ||
                stack.isIn(ItemTags.FISHES)) {
                hasFoodItems = true;
            }

            if (stack.hasEnchantments() ||
                stack.isIn(MYSTIC_ITEMS) ||
                stack.isIn(ItemTags.BOOKSHELF_BOOKS)) {
                hasMagicalItems = true;
            }

            if (stack.isIn(ItemTags.SWORDS) ||
                stack.isIn(ItemTags.AXES) ||
                stack.isIn(RANGED_WEAPONS)) {
                hasWeapons = true;
            }

            if (stack.isIn(ItemTags.PICKAXES) ||
                stack.isIn(ItemTags.SHOVELS) ||
                stack.isIn(ItemTags.HOES) ||
                stack.isIn(ItemTags.AXES) ||
                stack.isOf(Items.SHEARS) ||
                stack.isIn(SUPPORT_TOOLS)) {
                hasTools = true;
            }

            if (stack.isOf(Items.BRUSH) || stack.isIn(ItemTags.DECORATED_POT_SHERDS)) {
                hasArchaeology = true;
            }
        }

        // Emotional reactions based on inventory composition
        long now = owner.getWorld().getTime();
        if (hasValuableItems) {
            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.04f); // Security from wealth
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.06f); // Guarding valuable things
            EmotionContextCues.sendCue(owner, "inventory.valuables",
                Text.translatable("petsplus.emotion_cue.inventory.valuables"), 1200);
            NatureFlavorHandler.triggerForPet(pet, pc, (ServerWorld) owner.getWorld(), owner,
                Trigger.INVENTORY_VALUABLE, now);
        }

        if (hasFoodItems) {
            pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.05f); // Food security
            pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f); // Comfort from sustenance
            EmotionContextCues.sendCue(owner, "inventory.food",
                Text.translatable("petsplus.emotion_cue.inventory.food"), 1200);
        }

        if (hasMagicalItems) {
            pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.06f); // Wonder at magical things
            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.02f); // Slight wariness
            EmotionContextCues.sendCue(owner, "inventory.magic",
                Text.translatable("petsplus.emotion_cue.inventory.magic"), 1200);
        }

        if (hasArchaeology) {
            NatureFlavorHandler.triggerForPet(pet, pc, (ServerWorld) owner.getWorld(), owner,
                Trigger.INVENTORY_RELIC, now);
        }

        if (hasWeapons) {
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.05f); // Ready for danger
            pc.pushEmotion(PetComponent.Emotion.STOIC, 0.03f); // Determination
            EmotionContextCues.sendCue(owner, "inventory.weapons",
                Text.translatable("petsplus.emotion_cue.inventory.weapons"), 1200);
        }

        if (hasTools) {
            pc.pushEmotion(PetComponent.Emotion.KEFI, 0.03f); // Energy from productivity
            pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f); // Balance from useful work
            EmotionContextCues.sendCue(owner, "inventory.tools",
                Text.translatable("petsplus.emotion_cue.inventory.tools"), 1200);
        }
    }

    /**
     * Create a signature string representing the current hostile situation for fatigue tracking
     */
    private static String createHostileSignature(java.util.List<net.minecraft.entity.mob.HostileEntity> hostiles, java.util.Set<String> hostileTypes) {
        StringBuilder sig = new StringBuilder();
        sig.append("count:").append(hostiles.size());
        sig.append(",types:").append(hostileTypes.size());
        
        // Add sorted type list for consistency
        var sortedTypes = new java.util.ArrayList<>(hostileTypes);
        java.util.Collections.sort(sortedTypes);
        for (String type : sortedTypes) {
            sig.append(",").append(type);
        }
        
        return sig.toString();
    }

    /**
     * Calculate fatigue for hostile mob encounters
     */
    private static float calculateHostileFatigue(PetComponent comp, String hostileSignature, long currentTime, float baseAmount, int fatigueWindow) {
        String lastTickKey = "hostile_fatigue_last_tick";
        String signatureKey = "hostile_fatigue_signature";
        String intensityKey = "hostile_fatigue_intensity";
        String countKey = "hostile_fatigue_count";

        long lastTick = comp.getStateData(lastTickKey, Long.class, 0L);
        String lastSignature = comp.getStateData(signatureKey, String.class, "");
        float lastIntensity = comp.getStateData(intensityKey, Float.class, 0f);
        int triggerCount = comp.getStateData(countKey, Integer.class, 0);

        // If outside fatigue window or signature changed significantly, reset counters
        boolean signatureChanged = !hostileSignature.equals(lastSignature);
        if (currentTime - lastTick > fatigueWindow || signatureChanged) {
            triggerCount = signatureChanged ? 1 : 0; // Reset to 1 if signature changed, 0 if timeout
            lastIntensity = signatureChanged ? baseAmount : 0f;
        } else {
            triggerCount++;
        }

        float newIntensity = Math.max(lastIntensity * 0.7f, baseAmount);

        // Calculate fatigue - less fatigue if situation changed
        float frequencyFatigue = Math.max(0.2f, 1.0f - (triggerCount - 1) * 0.12f); // Each repeat reduces by 12%
        
        // Boost if situation intensified
        float intensityBoost = 1.0f;
        if (newIntensity > lastIntensity * 1.2f) {
            intensityBoost = Math.min(1.3f, 1.0f + (newIntensity - lastIntensity) * 1.5f);
        }

        float finalMultiplier = Math.min(1.0f, frequencyFatigue * intensityBoost);

        // Store updated state
        comp.setStateData(lastTickKey, currentTime);
        comp.setStateData(signatureKey, hostileSignature);
        comp.setStateData(intensityKey, newIntensity);
        comp.setStateData(countKey, triggerCount);

        return finalMultiplier;
    }

    /**
     * Check if hostile mobs are clustered together (potential mob farm)
     */
    private static boolean checkIfClustered(java.util.List<net.minecraft.entity.mob.HostileEntity> hostiles) {
        if (hostiles.size() < 4) return false;

        // Calculate average distance between all mobs
        double totalDistance = 0;
        int comparisons = 0;

        for (int i = 0; i < hostiles.size(); i++) {
            for (int j = i + 1; j < hostiles.size(); j++) {
                net.minecraft.util.math.Vec3d pos1 = hostiles.get(i).getPos();
                net.minecraft.util.math.Vec3d pos2 = hostiles.get(j).getPos();
                double distance = pos1.distanceTo(pos2);
                totalDistance += distance;
                comparisons++;
            }
        }

        double avgDistance = totalDistance / comparisons;
        
        // If average distance between mobs is very small, they're likely in a farm
        return avgDistance < 3.0; // Less than 3 blocks average distance = clustered
    }

    private static void handleDimensionChange(ServerPlayerEntity player, PlayerEnvState state,
                                              @Nullable RegistryKey<World> previous,
                                              RegistryKey<World> current) {
        state.dimensionKey = current;
        EmotionContextCues.clearForDimensionChange(player);
        if (current == World.OVERWORLD && previous != null && previous != World.OVERWORLD) {
            pushToNearbyOwnedPets(player, 64, pc -> {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f);
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f);
            }, false);
        } else if (current == World.NETHER) {
            pushToNearbyOwnedPets(player, 64, pc -> {
                pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f);
                pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
            }, false);
        } else if (current == World.END) {
            pushToNearbyOwnedPets(player, 64, pc -> {
                pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.14f);
                pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.08f);
            }, false);
        }
    }

    private static final class PlayerTicker implements PlayerTickListener {
        private final Map<UUID, Long> nextRunTicks = new ConcurrentHashMap<>();

        @Override
        public long nextRunTick(ServerPlayerEntity player) {
            if (player == null) {
                return Long.MAX_VALUE;
            }
            return nextRunTicks.getOrDefault(player.getUuid(), 0L);
        }

        @Override
        public void run(ServerPlayerEntity player, long currentTick) {
            if (player == null) {
                return;
            }

            nextRunTicks.remove(player.getUuid());
            handlePlayerTick(player, currentTick);

            PlayerEnvState state = PLAYER_ENV.get(player);
            if (state == null) {
                nextRunTicks.put(player.getUuid(), currentTick + 20L);
                return;
            }

            long worldTick = player.getWorld().getTime();
            long nextIdleDelta = Math.max(1L, state.nextIdleCheckTick - worldTick);
            long nextNuancedDelta = Math.max(1L, state.nextNuancedTick - worldTick);
            long nextTick = currentTick + Math.min(nextIdleDelta, nextNuancedDelta);
            nextRunTicks.put(player.getUuid(), nextTick);
        }

        @Override
        public void onPlayerRemoved(ServerPlayerEntity player) {
            if (player == null) {
                return;
            }
            nextRunTicks.remove(player.getUuid());
            PlayerEnvState removed = PLAYER_ENV.remove(player);
            if (removed != null && removed.dimensionKey != null) {
                PENDING_DIMENSION_CHANGES.put(player.getUuid(), removed.dimensionKey);
            }
        }
    }
}

