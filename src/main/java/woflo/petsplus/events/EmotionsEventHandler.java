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
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.TropicalFishEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.CampfireBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
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
import woflo.petsplus.mood.EmotionBaselineTracker;
import woflo.petsplus.mood.EmotionStimulusBus;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureFlavorHandler.Trigger;
import woflo.petsplus.ui.FeedbackManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.BiConsumer;

import woflo.petsplus.behavior.social.GossipCircleRoutine;
import woflo.petsplus.behavior.social.GossipWhisperRoutine;
import woflo.petsplus.behavior.social.PackCircleRoutine;
import woflo.petsplus.behavior.social.PetSocialData;
import woflo.petsplus.behavior.social.SocialBehaviorRoutine;
import woflo.petsplus.behavior.social.SocialContextSnapshot;
import woflo.petsplus.behavior.social.WhisperRoutine;
import woflo.petsplus.events.EmotionCueConfig.EmotionCueDefinition;
import woflo.petsplus.events.StimulusSummary;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.AsyncJobPriority;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.gossip.GossipTopics;

/**
 * Central, low-cost, event-driven emotion hooks.
 * Ties pet emotions to existing gameplay events (no per-tick polling).
 */
public final class EmotionsEventHandler {

    private static final PlayerTicker PLAYER_TICKER = new PlayerTicker();
    private static final List<SocialBehaviorRoutine> SOCIAL_ROUTINES = List.of(
        new GossipCircleRoutine(),
        new GossipWhisperRoutine(),
        new WhisperRoutine()
    );
    private static final PackCircleRoutine PACK_ROUTINE = new PackCircleRoutine();
    private static final TagKey<Item> MUSIC_DISC_ITEMS = TagKey.of(RegistryKeys.ITEM,
        Identifier.of("minecraft", "music_discs"));

    // Music system optimization: World-level jukebox caching
    private static final Map<ServerWorld, Map<BlockPos, Long>> activeJukeboxes = new ConcurrentHashMap<>();
    private static final Map<ServerWorld, Long> lastJukeboxScan = new ConcurrentHashMap<>();
    private static final long JUKEBOX_CACHE_DURATION = 300; // 15 seconds in ticks
    private static final int OPTIMIZED_MUSIC_RADIUS = 12; // Reduced from 48 blocks
    
    // Arcane Overflow constants
    private static final long ARCANE_STREAK_TIMEOUT = 2400L; // 2 minutes in ticks
    private static final int ARCANE_MAX_ENCHANTING_POWER = 15; // Vanilla max bookshelf count
    private static final int ARCANE_MIN_STREAK = 3; // Enchants needed for max streak bonus
    private static final double ARCANE_TRIGGER_RADIUS = 20.0;
    private static final float ARCANE_BASE_THRESHOLD = 0.7f;
    private static final float ARCANE_POWER_REDUCTION = 0.4f;
    private static final float ARCANE_STREAK_REDUCTION = 0.3f;
    private static final float ARCANE_EB_BONUS = 0.5f;

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

        emitOwnerBrokeBlockTrigger(sp, pos, state);
        
        // Add contagion for valuable block discovery
        if (isValuableBlock(state)) {
            pushToNearbyOwnedPets(sp, 24, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.GLEE, 0.020f);  // Discovery joy contagion
                collector.pushEmotion(PetComponent.Emotion.CURIOUS, 0.015f);  // Curiosity contagion
                // Add visual feedback for discovery contagion
                FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.CURIOUS);
            });
        }
    }

    static void emitOwnerBrokeBlockTrigger(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        Map<String, Object> payload = new HashMap<>();
        boolean blockValuable = isValuableBlock(state);
        payload.put("block_valuable", blockValuable);
        if (pos != null) {
            payload.put("block_pos", pos.toImmutable());
        }
        if (state != null) {
            payload.put("block_state", state);
        }
        resolveBlockIdentifier(state).ifPresent(blockId -> {
            payload.put("block_identifier", blockId);
            payload.put("block_id", blockId.toString());
            payload.put("block_id_no_namespace", blockId.getPath());
        });
        CombatEventHandler.triggerAbilitiesForOwner(player, "owner_broke_block", payload);
    }

    static Optional<Identifier> resolveBlockIdentifier(@Nullable BlockState state) {
        if (state == null) {
            return Optional.empty();
        }
        if (state instanceof BlockIdentifierProvider provider) {
            Identifier override = provider.petsplus$getIdentifier();
            if (override != null) {
                return Optional.of(override);
            }
        }
        Block block = state.getBlock();
        if (block == null) {
            return Optional.empty();
        }
        try {
            Identifier blockId = Registries.BLOCK.getId(block);
            return Optional.ofNullable(blockId);
        } catch (Exception e) {
            Petsplus.LOGGER.debug("Failed to resolve block identifier for payload", e);
            return Optional.empty();
        }
    }

    interface BlockIdentifierProvider {
        @Nullable Identifier petsplus$getIdentifier();
    }

    private static boolean isValuableBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.isIn(BlockTags.DIAMOND_ORES)
            || state.isIn(BlockTags.EMERALD_ORES)
            || state.isIn(BlockTags.GOLD_ORES)
            || state.isIn(BlockTags.LAPIS_ORES)
            || state.isIn(BlockTags.REDSTONE_ORES)
            || state.isIn(BlockTags.COAL_ORES)
            || state.isIn(BlockTags.COPPER_ORES)
            || state.isIn(BlockTags.IRON_ORES)
            || state.isIn(BlockTags.BEACON_BASE_BLOCKS)) {
            return true;
        }

        return state.isOf(Blocks.ANCIENT_DEBRIS)
            || state.isOf(Blocks.NETHERITE_BLOCK)
            || state.isOf(Blocks.AMETHYST_CLUSTER)
            || state.isOf(Blocks.BUDDING_AMETHYST)
            || state.isOf(Blocks.REINFORCED_DEEPSLATE)
            || state.isOf(Blocks.BEACON);
    }

    // (Block place detour handled in onUseBlock based on held BlockItem)

    // ==== Kill Events → PASSIONATE, PLAYFUL, HAPPY, FOCUSED ====
    private static void onAfterKilledOther(ServerWorld world, Entity killer, LivingEntity killed) {
        if (killer instanceof ServerPlayerEntity sp) {
            KillContext context = classifyKillTarget(killed);
            float healthPct = Math.max(0f, sp.getHealth() / sp.getMaxHealth());
            float reliefBonus = healthPct < 0.35f ? 0.2f : 0f;
            long now = world.getTime();
            OwnerKillStreak streak = OWNER_KILL_STREAKS.computeIfAbsent(sp.getUuid(), uuid -> new OwnerKillStreak());
            boolean lowHealthFinish = healthPct < 0.35f;
            streak.recordKill(now, healthPct, lowHealthFinish);
            applyConfiguredStimulus(sp, "combat.owner_kill", 32, (pc, collector) -> {
                if (reliefBonus > 0f) {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, reliefBonus * 3.0f);
                }
                switch (context) {
                    case HOSTILE -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_hostile", now, 200L)) {
                            // More nuanced hostile kill emotions
                            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.25f);  // Post-threat relief
                            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f);  // Victory joy
                            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.35f + reliefBonus * 0.3f);
                            // If guardian role, extra protective response
                            if (pc.hasRole(PetRoleType.GUARDIAN)) {
                                collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.50f);
                            }
                            // Add contagion for combat victory - spread relief and triumph to nearby pets
                            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.015f);  // Subtle contagion
                            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.020f);   // Victory contagion
                            // Add visual feedback for contagion
                            FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.KEFI);
                        }
                    }
                    case PASSIVE -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_passive", now, 240L)) {
                            // Reduced remorse for passive kills
                            collector.pushEmotion(PetComponent.Emotion.REGRET, 0.15f);  // Reduced from 0.35f
                            // Only add Hiraeth for non-guardian roles
                            if (!pc.hasRole(PetRoleType.GUARDIAN)) {
                                collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.15f);  // Reduced from 0.25f
                            }
                            // Add subtle contagion for mixed emotions
                            collector.pushEmotion(PetComponent.Emotion.REGRET, 0.010f);  // Subtle regret contagion
                        }
                    }
                    case BOSS -> {
                        if (tryMarkPetBeat(pc, "combat_owner_kill_boss", now, 360L)) {
                            collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.50f);
                            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.35f);  // Intense victory joy
                            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.45f + reliefBonus * 0.4f);
                            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.40f);  // Major threat eliminated
                            // Strong contagion for major victory
                            collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.025f);  // Pride contagion
                            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.030f);   // Strong victory contagion
                            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.020f); // Relief contagion
                            // Add visual feedback for major victory contagion
                            FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.PRIDE);
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

            Text killedName = killed.getDisplayName();
            float noteworthiness = calculateKillNoteworthiness(context, streak, killed);
            if (noteworthiness >= 0.55f) {
                KillRumorPayload payload = buildKillRumorPayload(context, streak, noteworthiness, killedName);
                shareOwnerRumor(sp, payload.radius(), payload.topicId(), payload.intensity(), payload.confidence(), payload.text());
            }
        }

        LAST_OWNER_ATTACK_TARGET.remove(killed.getUuid());

        if (killer instanceof MobEntity mob) {
            PetComponent pc = PetComponent.get(mob);
            if (pc != null && pc.getOwner() instanceof ServerPlayerEntity owner) {
                emitPetCue(owner, pc, "combat.pet_kill." + mob.getUuidAsString(), (component, collector) -> {},
                    "petsplus.emotion_cue.combat.pet_kill", 200, mob.getDisplayName());
                if (pc.hasRole(PetRoleType.STRIKER)) {
                    emitPetCue(owner, pc, "role.striker.execute." + mob.getUuidAsString(), (component, collector) -> {},
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
        
        // Check for feeding/food use
        if (stack.get(DataComponentTypes.FOOD) != null) {
            // Feeding emotions - transitions from anticipation to satisfaction
            pushToNearbyOwnedPets(sp, 24, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.25f);  // Pre-meal bonding
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.25f);  // Post-meal satisfaction
                collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.15f);  // Contentment
                // Add contagion for feeding - spread satisfaction to nearby pets
                collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.015f);  // Contentment contagion
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.020f);  // Social bonding contagion
                // Add visual feedback for feeding contagion
                FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.CONTENT);
            });
        }
        
        // Check for healing items (potions)
        if (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION)) {
            pushToNearbyOwnedPets(sp, 16, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.30f);  // Healing relief
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.20f);  // Balance restored
                // Add contagion for healing - spread relief to nearby pets
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.022f);  // Healing relief contagion
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.015f);  // Balance contagion
            });
        }
        
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
        
        // Ultra-rare: ARCANE OVERFLOW - Enchanting table/anvil use
        if (state.isOf(Blocks.ENCHANTING_TABLE) || state.isOf(Blocks.ANVIL) || 
            state.isOf(Blocks.CHIPPED_ANVIL) || state.isOf(Blocks.DAMAGED_ANVIL)) {
            triggerArcaneOverflow(sp, world);
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
        PetConsumer stateConsumer = (pc, collector) -> {};
        long gossipTopicId = 0L;
        Text gossipText = null;
        float gossipIntensity = 0.32f;
        float gossipConfidence = 0.44f;

        if (state.isIn(BlockTags.CAMPFIRES) && state.contains(CampfireBlock.LIT)) {
            boolean lit = state.get(CampfireBlock.LIT);
            if (lit && held.isIn(ItemTags.SHOVELS)) {
                useDefinition = "block_use.campfire.douse";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.05f);
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.04f);
                };
                gossipTopicId = GossipTopics.SOCIAL_CAMPFIRE;
                gossipText = Text.translatable("petsplus.gossip.social.campfire");
                gossipIntensity = 0.3f;
                gossipConfidence = 0.46f;
            } else if (!lit && (held.isOf(Items.FLINT_AND_STEEL) || held.isOf(Items.FIRE_CHARGE))) {
                useDefinition = "block_use.campfire.ignite";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.05f);
                    collector.pushEmotion(PetComponent.Emotion.GLEE, 0.04f);
                };
                gossipTopicId = GossipTopics.SOCIAL_CAMPFIRE;
                gossipText = Text.translatable("petsplus.gossip.social.campfire");
                gossipIntensity = 0.36f;
                gossipConfidence = 0.48f;
            } else if (lit) {
                useDefinition = "block_use.campfire.stoke";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.25f);
                    collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.20f);
                    // Campfire at night provides comfort
                    if (world.isNight()) {
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.30f);
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.20f);
                        // Reduce any existing vigilance/fear
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, -0.15f);
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, -0.20f);
                    }
                };
                gossipTopicId = GossipTopics.SOCIAL_CAMPFIRE;
                gossipText = Text.translatable("petsplus.gossip.social.campfire");
                gossipIntensity = 0.34f;
                gossipConfidence = 0.47f;
            }
        } else if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) {
            // Loot discovery emotions when opening containers
            useDefinition = "block_use.container.open";
            stateConsumer = (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.GLEE, 0.25f);  // Discovery joy
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.15f);  // Excitement
                collector.pushEmotion(PetComponent.Emotion.CURIOUS, 0.20f);  // What's inside?
            };
        } else if (state.getBlock() instanceof JukeboxBlock && state.contains(JukeboxBlock.HAS_RECORD)) {
            boolean hasRecord = state.get(JukeboxBlock.HAS_RECORD);
            if (hasRecord) {
                useDefinition = "block_use.jukebox.stop";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.05f);
                    collector.pushEmotion(PetComponent.Emotion.NOSTALGIA, 0.04f);
                };
            } else if (held.isIn(MUSIC_DISC_ITEMS)) {
                useDefinition = "block_use.jukebox.start";

                // Determine music disc type for specific emotions
                String discName = held.getItem().getTranslationKey();
                boolean isCalm = discName.contains("pigstep") || discName.contains("stal") ||
                                 discName.contains("mellohi") || discName.contains("wait");
                boolean isUpbeat = discName.contains("otherside") || discName.contains("cat") ||
                                   discName.contains("blocks") || discName.contains("chirp");
                boolean isEerie = discName.contains("13") || discName.contains("11") ||
                                  discName.contains("ward");

                stateConsumer = (pc, collector) -> {
                    // Store music state for prolonged emotional peaks
                    long currentTime = world.getTime();
                    pc.setStateData("music_playing", true);
                    pc.setStateData("music_start_time", currentTime);
                    pc.setStateData("music_disc_type", discName);

                    // Calculate trait-based affinity bonuses
                    float musicAffinity = 1.0f;
                    // Check for entertainment-related roles (if they exist)
                    // For now, just use characteristics

                    // Check for musical personality traits
                    Float playfulness = pc.getStateData("trait_playfulness", Float.class);
                    if (playfulness != null && playfulness > 0.7f) {
                        musicAffinity *= 1.2f;  // Playful pets enjoy music more
                    }

                    // Peak emotion application with affinity scaling
                    if (isCalm) {
                        // Calm music - peaceful emotions with prolonged effect
                        float peakIntensity = 0.35f * musicAffinity;
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, peakIntensity);  // Balanced serenity
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f * musicAffinity);  // Idle listening appreciation
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.15f * musicAffinity);  // Appreciation of beauty
                        pc.setStateData("music_peak_emotion", "LAGOM");
                        pc.setStateData("music_peak_intensity", peakIntensity);
                    } else if (isUpbeat) {
                        // Upbeat music - energetic emotions with burst
                        float peakIntensity = 0.40f * musicAffinity;
                        collector.pushEmotion(PetComponent.Emotion.KEFI, peakIntensity);  // Joyful exuberance peak
                        collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.25f * musicAffinity);
                        collector.pushEmotion(PetComponent.Emotion.GLEE, 0.20f * musicAffinity);
                        pc.setStateData("music_peak_emotion", "KEFI");
                        pc.setStateData("music_peak_intensity", peakIntensity);
                    } else if (isEerie) {
                        // Eerie music - mysterious/dread emotions with tension
                        if (world.isNight() || world.getLightLevel(pos) < 7) {
                            collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f * musicAffinity);  // Dark atmosphere
                        }
                        float peakIntensity = 0.30f * musicAffinity;
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, peakIntensity);  // Mysterious appreciation peak
                        collector.pushEmotion(PetComponent.Emotion.ANGST, 0.10f * musicAffinity);  // Slight tension
                        pc.setStateData("music_peak_emotion", "YUGEN");
                        pc.setStateData("music_peak_intensity", peakIntensity);
                    } else {
                        // Default music emotions
                        float peakIntensity = 0.25f * musicAffinity;
                        collector.pushEmotion(PetComponent.Emotion.GLEE, peakIntensity);
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f * musicAffinity);  // General appreciation
                        collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.15f * musicAffinity);  // Social bonding through music
                        pc.setStateData("music_peak_emotion", "GLEE");
                        pc.setStateData("music_peak_intensity", peakIntensity);
                    }

                    // Species-specific music responses
                    if (pc.getPet() instanceof ParrotEntity) {
                        // Parrots have special affinity for all music
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.25f);  // Extra musical joy
                        collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.20f);
                        collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.15f);  // Flock unity through music
                    } else if (pc.getPet() instanceof WolfEntity) {
                        // Wolves howl along with music
                        collector.pushEmotion(PetComponent.Emotion.LOYALTY, 0.15f * musicAffinity);
                        collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.20f * musicAffinity);  // Pack bonding
                    } else if (pc.getPet() instanceof CatEntity) {
                        // Cats are more selective about music
                        if (isCalm) {
                            collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.20f * musicAffinity);
                        } else {
                            collector.pushEmotion(PetComponent.Emotion.ENNUI, 0.10f);  // Mild annoyance at loud music
                        }
                    }

                    // Store music session data for ongoing effects
                    pc.setStateData("music_session_id", java.util.UUID.randomUUID().toString());
                };
            }
        } else if (state.getBlock() instanceof BedBlock && state.contains(BedBlock.OCCUPIED)) {
            boolean occupied = state.get(BedBlock.OCCUPIED);
            if (occupied) {
                useDefinition = "block_use.bed.occupied";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.04f);
                    collector.pushEmotion(PetComponent.Emotion.REGRET, 0.03f);
                };
            } else {
                useDefinition = "block_use.bed.rest";
                stateConsumer = (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.25f);
                    collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.20f);
                    // Bed provides strong safety at night
                    if (world.isNight()) {
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.35f);
                        collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.25f); // Home feeling
                        // Clear fear/vigilance
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, -0.20f);
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, -0.25f);
                    }
                };
            }
        }

        if (useDefinition != null) {
            triggerConfiguredCue(sp, useDefinition, config.fallbackRadius(), stateConsumer, null);
            if (gossipTopicId != 0L && gossipText != null) {
                shareOwnerRumor(sp, 24, gossipTopicId, gossipIntensity, gossipConfidence, gossipText);
            }
        }

        if (held.getItem() instanceof BlockItem blockItem) {
            BlockState defaultState = blockItem.getBlock().getDefaultState();
            
            // Building/placing blocks - appreciation of creation
            pushToNearbyOwnedPets(sp, 16, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.15f);  // Appreciation of creation
                collector.pushEmotion(PetComponent.Emotion.FOCUSED, 0.10f);  // Watching owner work
            });
            
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
        
        // Check if petting a pet
        if (entity instanceof MobEntity mob && PetComponent.get(mob) != null && stack.isEmpty()) {
            PetComponent pc = PetComponent.get(mob);
            // Petting emotions
            pushToSinglePet(sp, pc, (comp, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f);  // Initial joy
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);  // Settling into contentment
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.15f);  // Bonding
                // Add contagion for petting - spread joy to nearby pets
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.018f);  // Joy contagion
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.012f);  // Social bonding contagion
                // Add visual feedback for social contagion
                FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.SOBREMESA);
            });
        }
        
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
        float passionate = 0.20f + 0.20f * charge;
        float focused = 0.15f + 0.15f * charge;
        float startle = 0.08f + 0.10f * charge;

        CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 28, (pc, collector) -> {
            // Add Foreboding when owner health is low
            float ownerHealthPct = sp.getHealth() / sp.getMaxHealth();
            if (ownerHealthPct < 0.5f) {
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.45f);  // Dread when owner in danger
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.40f);
                // Add contagion for owner danger - spread fear and protectiveness
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.025f);  // Fear contagion
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.020f);  // Protective contagion
                // Add visual feedback for danger contagion
                FeedbackManager.emitContagionFeedback(pc.getPet(), (ServerWorld) sp.getWorld(), PetComponent.Emotion.FOREBODING);
            }
            collector.pushEmotion(PetComponent.Emotion.KEFI, passionate);
            collector.pushEmotion(PetComponent.Emotion.FOCUSED, focused);
            collector.pushEmotion(PetComponent.Emotion.STARTLE, startle * 0.7f);  // Reduced startle
            // Add contagion for combat anticipation
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.015f);  // Combat readiness contagion
        });
        sendCueAfterStimulus(sp, stimulusFuture,
            "combat.owner_swing." + targetId,
            () -> Text.translatable("petsplus.emotion_cue.combat.owner_swing", target.getDisplayName()),
            160);

        return ActionResult.PASS;
    }

    // ==== Respawn → RELIEF + STOIC/GAMAN ====
    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) return;
        EmotionContextCues.clear(oldPlayer);
        EmotionContextCues.clear(newPlayer);
        CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(newPlayer, 32, (pc, collector) -> {
            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.80f);
            collector.pushEmotion(PetComponent.Emotion.STOIC, 0.65f);
            collector.pushEmotion(PetComponent.Emotion.GAMAN, 0.40f);
            // Add contagion for respawn - spread relief to nearby pets
            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.025f);  // Respawn relief contagion
            collector.pushEmotion(PetComponent.Emotion.STOIC, 0.018f);  // Resilience contagion
        });
        sendCueAfterStimulus(newPlayer, stimulusFuture,
            "player.respawn",
            () -> Text.translatable("petsplus.emotion_cue.player.respawn"),
            200);
        NatureFlavorHandler.triggerForOwner(newPlayer, 32, Trigger.OWNER_RESPAWN);
    }

    // ==== Owner Death → SAUDADE + HIRAETH + REGRET (nearby pets) ====
    private static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayerEntity sp)) return;
        ServerWorld world = (ServerWorld) sp.getWorld();
        StateManager stateManager = StateManager.forWorld(world);
        PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
        Vec3d center = sp.getPos();
        List<PetSwarmIndex.SwarmEntry> pets = new ArrayList<>();
        swarmIndex.forEachPetInRange(sp, center, 48.0, pets::add);
        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null) {
                continue;
            }
            MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.SAUDADE, 0.95f);
                collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.85f);
                collector.pushEmotion(PetComponent.Emotion.REGRET, 0.70f);
            });
            MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
        }
    }

    // ==== Weather transitions (ultra-light) → SOBREMESA, YUGEN, FOREBODING, RELIEF ====
    private static final java.util.Map<ServerWorld, WeatherState> WEATHER = new java.util.WeakHashMap<>();
    private static final Map<ServerWorld, Long> LAST_WET_WEATHER_TICK = new WeakHashMap<>();
    private static final Map<UUID, Long> LAST_CLEAR_WEATHER_TRIGGER = new HashMap<>();
    private static final Map<UUID, ConcurrentHashMap<UUID, Long>> LAST_OWNER_ATTACK_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, OwnerKillStreak> OWNER_KILL_STREAKS = new ConcurrentHashMap<>();
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

    private static final long OWNER_KILL_STREAK_WINDOW = 200L;

    private static final class OwnerKillStreak {
        private int streakCount;
        private long lastKillTick = Long.MIN_VALUE;
        private boolean sawLowHealthFinish;
        private float lowestHealthPct = 1f;

        void recordKill(long tick, float healthPct, boolean lowHealthFinish) {
            if (tick - lastKillTick > OWNER_KILL_STREAK_WINDOW) {
                streakCount = 0;
                sawLowHealthFinish = false;
                lowestHealthPct = 1f;
            }
            streakCount++;
            lastKillTick = tick;
            if (streakCount == 1) {
                lowestHealthPct = healthPct;
                sawLowHealthFinish = lowHealthFinish;
            } else {
                lowestHealthPct = Math.min(lowestHealthPct, healthPct);
                sawLowHealthFinish = sawLowHealthFinish || lowHealthFinish;
            }
        }

        int streakCount() {
            return streakCount;
        }

        boolean sawLowHealthFinish() {
            return sawLowHealthFinish;
        }

        float lowestHealthPct() {
            return lowestHealthPct;
        }
    }

    private record KillRumorPayload(double radius, long topicId, float intensity, float confidence, Text text) {}

    private static float calculateKillNoteworthiness(KillContext context, OwnerKillStreak streak, LivingEntity killed) {
        float score = switch (context) {
            case PASSIVE -> 0.2f;
            case HOSTILE -> 0.45f;
            case BOSS -> 0.8f;
        };

        int streakCount = streak.streakCount();
        if (streakCount >= 2) {
            float streakBoost = 0.18f + 0.08f * Math.min(3, streakCount - 1);
            score += streakBoost;
        }

        if (streak.sawLowHealthFinish()) {
            float clutchFactor = MathHelper.clamp((0.35f - streak.lowestHealthPct()) / 0.35f, 0f, 1f);
            score += 0.22f + clutchFactor * 0.18f;
        }

        float targetDifficulty = MathHelper.clamp(killed.getMaxHealth() / 40f, 0f, 1f);
        if (targetDifficulty > 0f) {
            score += targetDifficulty * 0.1f;
        }

        return MathHelper.clamp(score, 0f, 1.4f);
    }

    private static KillRumorPayload buildKillRumorPayload(KillContext context, OwnerKillStreak streak, float score, Text killedName) {
        long topicId;
        double radius;
        float baseIntensity;
        float baseConfidence;
        MutableText text;
        switch (context) {
            case HOSTILE -> {
                topicId = GossipTopics.OWNER_KILL_HOSTILE;
                radius = 48;
                baseIntensity = 0.65f;
                baseConfidence = 0.55f;
                text = Text.translatable("petsplus.gossip.combat.owner_kill.hostile", killedName).copy();
            }
            case PASSIVE -> {
                topicId = GossipTopics.OWNER_KILL_PASSIVE;
                radius = 32;
                baseIntensity = 0.45f;
                baseConfidence = 0.4f;
                text = Text.translatable("petsplus.gossip.combat.owner_kill.passive", killedName).copy();
            }
            case BOSS -> {
                topicId = GossipTopics.OWNER_KILL_BOSS;
                radius = 64;
                baseIntensity = 0.85f;
                baseConfidence = 0.7f;
                text = Text.translatable("petsplus.gossip.combat.owner_kill.boss", killedName).copy();
            }
            default -> throw new IllegalStateException("Unexpected value: " + context);
        }

        if (streak.streakCount() >= 2) {
            text.append(Text.literal(" "))
                .append(Text.translatable("petsplus.gossip.combat.owner_kill.extra.streak", streak.streakCount()));
        }

        if (streak.sawLowHealthFinish()) {
            int pct = MathHelper.clamp(Math.round(streak.lowestHealthPct() * 100f), 0, 100);
            text.append(Text.literal(" "))
                .append(Text.translatable("petsplus.gossip.combat.owner_kill.extra.clutch", pct));
        }

        float intensityScale = MathHelper.clamp(0.65f + score * 0.4f, 0.65f, 1.3f);
        float confidenceScale = MathHelper.clamp(0.7f + score * 0.35f, 0.7f, 1.25f);

        float intensity = MathHelper.clamp(baseIntensity * intensityScale, 0f, 1f);
        float confidence = MathHelper.clamp(baseConfidence * confidenceScale, 0f, 1f);

        return new KillRumorPayload(radius, topicId, intensity, confidence, text);
    }

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
        long emotionVersionCounter;
        long appliedEmotionVersion;

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
            this.emotionVersionCounter = 0L;
            this.appliedEmotionVersion = 0L;
        }
    }

    private record EmotionOwnerTickPayload(long version,
                                           boolean runIdleMaintenance,
                                           boolean runNuanced) {
    }

    private static void handlePlayerTick(ServerPlayerEntity player, long serverTick) {
        if (player == null || player.isRemoved()) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        StateManager stateManager = StateManager.forWorld(world);
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

        long idleInterval = stateManager.scaleInterval(20L);
        long nuancedInterval = stateManager.scaleInterval(60L);

        if (player.isSpectator() || player.isSleeping()) {
            state.nextIdleCheckTick = Math.max(state.nextIdleCheckTick, now + idleInterval);
            state.nextNuancedTick = Math.max(state.nextNuancedTick, now + nuancedInterval);
            state.lastFallDistance = player.fallDistance;
            return;
        }

        updateFallTracking(player, state);

        boolean runIdleMaintenance = false;
        boolean runNuancedSweep = false;

        if (now >= state.nextIdleCheckTick) {
            state.nextIdleCheckTick = now + idleInterval;
            runIdleMaintenance = true;
        }

        if (now >= state.nextNuancedTick) {
            state.nextNuancedTick = now + nuancedInterval;
            runNuancedSweep = true;
        }

        if (runIdleMaintenance || runNuancedSweep) {
            PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
            List<PetSwarmIndex.SwarmEntry> ownerSnapshot = swarmIndex.snapshotOwner(player.getUuid());
            if (!ownerSnapshot.isEmpty()) {
                long version = ++state.emotionVersionCounter;
                EmotionOwnerTickPayload payload = new EmotionOwnerTickPayload(version, runIdleMaintenance, runNuancedSweep);
                stateManager.requestEmotionEvent(player, payload);
            } else if (runIdleMaintenance) {
                state.appliedEmotionVersion = Math.max(state.appliedEmotionVersion, state.emotionVersionCounter);
            }
        }
    }

    public static void onOwnerEmotionEvent(OwnerEventFrame frame) {
        if (frame == null) {
            return;
        }
        ServerPlayerEntity owner = frame.owner();
        if (owner == null || owner.isRemoved()) {
            return;
        }
        ServerWorld world = frame.world();
        if (world == null) {
            return;
        }

        EmotionOwnerTickPayload payload = frame.payload(EmotionOwnerTickPayload.class);
        if (payload == null) {
            return;
        }

        PlayerEnvState state = PLAYER_ENV.computeIfAbsent(owner,
            ignored -> createEnvState(owner, world, frame.currentTick()));
        if (payload.version() <= state.appliedEmotionVersion) {
            return;
        }

        boolean idleScheduled = false;
        if (payload.runIdleMaintenance()) {
            idleScheduled = processIdleMaintenance(frame, state, payload);
        }

        if (payload.runNuanced()) {
            handleNuancedEmotions(owner, world, frame.currentTick(), frame.swarmSnapshot());
        }

        if (payload.runIdleMaintenance()) {
            if (!idleScheduled) {
                state.appliedEmotionVersion = Math.max(state.appliedEmotionVersion, payload.version());
            }
        } else {
            state.appliedEmotionVersion = Math.max(state.appliedEmotionVersion, payload.version());
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

    private static boolean processIdleMaintenance(OwnerEventFrame frame,
                                                  PlayerEnvState state,
                                                  EmotionOwnerTickPayload payloadMeta) {
        ServerPlayerEntity owner = frame.owner();
        ServerWorld world = frame.world();
        if (owner == null || world == null) {
            return false;
        }

        Map<UUID, PetComponent> nearbyComponents = collectNearbyPetComponents(frame, 32.0d);
        if (nearbyComponents.isEmpty()) {
            return false;
        }

        List<IdlePetSample> activitySamples = new ArrayList<>(nearbyComponents.size());
        for (PetComponent component : nearbyComponents.values()) {
            if (component == null) {
                continue;
            }
            activitySamples.add(new IdlePetSample(component.getLastAttackTick()));
        }

        long now = frame.currentTick();
        IdleMaintenancePayload payload = new IdleMaintenancePayload(
            now,
            Math.max(0L, now - state.lastMovementTick),
            state.lastIdleCueTick,
            state.lastRainyIdleCueTick,
            world.isRaining(),
            TIME_PHASES.getOrDefault(world, computeTimePhase(world.getTimeOfDay())),
            activitySamples
        );

        StateManager stateManager = StateManager.forWorld(world);
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        long version = payloadMeta.version();
        coordinator.submitStandalone(
            "emotion-idle-" + owner.getUuid(),
            () -> computeIdleMaintenancePlan(payload),
            plan -> applyIdleMaintenancePlan(owner, world, state, nearbyComponents, plan, now, version),
            AsyncJobPriority.LOW
        ).exceptionally(error -> {
            Throwable cause = unwrapAsyncError(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Idle emotion processing rejected for owner {}", owner.getUuid());
            } else {
                Petsplus.LOGGER.error("Idle emotion processing failed for owner {}", owner.getUuid(), cause);
            }
            IdleMaintenancePlan fallback = computeIdleMaintenancePlan(payload);
            applyIdleMaintenancePlan(owner, world, state, nearbyComponents, fallback, now, version);
            return null;
        });
        return true;
    }

    private static IdleMaintenancePlan computeIdleMaintenancePlan(IdleMaintenancePayload payload) {
        if (payload == null) {
            return IdleMaintenancePlan.none();
        }
        if (payload.idleTicks() < 1200L) {
            return IdleMaintenancePlan.none();
        }

        boolean hasActivity = false;
        long now = payload.now();
        for (IdlePetSample sample : payload.petActivity()) {
            if (sample == null) {
                continue;
            }
            if (now - sample.lastAttackTick() < 600L) {
                hasActivity = true;
                break;
            }
        }
        if (hasActivity) {
            return IdleMaintenancePlan.none();
        }

        boolean triggerIdle = false;
        boolean triggerRainy = false;

        if (payload.idleTicks() >= 3600L) {
            long sinceLast = now - payload.lastIdleCueTick();
            if (payload.lastIdleCueTick() == 0L || sinceLast >= 2400L) {
                triggerIdle = true;
            }
        }

        if (payload.raining() && payload.idleTicks() >= 2400L) {
            long sinceRainy = now - payload.lastRainyCueTick();
            if (sinceRainy >= 1200L) {
                triggerRainy = true;
            }
        }

        if (!triggerIdle && !triggerRainy) {
            return IdleMaintenancePlan.none();
        }
        return new IdleMaintenancePlan(triggerIdle, triggerRainy, payload.idleTicks(), payload.timePhase() == TimePhase.NIGHT);
    }

    private static void applyIdleMaintenancePlan(ServerPlayerEntity player,
                                                 ServerWorld world,
                                                 PlayerEnvState state,
                                                 Map<UUID, PetComponent> nearbyComponents,
                                                 IdleMaintenancePlan plan,
                                                 long now,
                                                 long version) {
        if (plan == null || !plan.hasActions()) {
            state.appliedEmotionVersion = Math.max(state.appliedEmotionVersion, version);
            return;
        }
        StimulusSummary.Builder builder = StimulusSummary.builder(world.getTime());
        List<Runnable> pendingCues = new ArrayList<>(2);
        List<CompletableFuture<Void>> pendingWork = new ArrayList<>(2);

        if (plan.triggerIdleCue()) {
            // Context-dependent idle emotions from rebalanced logic
            if (plan.isNight()) {
                // Night idle - check distance from base for Hiraeth
                Vec3d spawnPos = Vec3d.ofCenter(world.getSpawnPos());
                double distanceFromBase = player.getPos().distanceTo(spawnPos);

                pendingWork.add(applyStimulusToComponents(nearbyComponents.values(),
                    (pc, collector) -> {
                        if (distanceFromBase > 200) {
                            collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.20f);  // Far from home at night
                        }
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.25f);  // Mysterious night beauty
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);  // Some peace
                    }, builder, world));
                pendingCues.add(() -> EmotionContextCues.sendCue(player, "idle.night_peaceful",
                    Text.translatable("petsplus.emotion_cue.idle.night_peaceful"), 2400));
            } else {
                // Day idle - energy-based response
                pendingWork.add(applyStimulusToComponents(nearbyComponents.values(),
                    (pc, collector) -> {
                        // High-energy pets get restless and playful
                        // TODO: Check pet's energy characteristic when available
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.15f);  // Bored energy
                        collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.20f);  // Want to play
                        // Cap Ennui for repetitive idle
                        if (plan.idleTicks() >= 6000L) {  // >10 minutes
                            collector.pushEmotion(PetComponent.Emotion.ENNUI, Math.min(0.10f, plan.idleTicks() / 36000f));
                        }
                    }, builder, world));
                pendingCues.add(() -> EmotionContextCues.sendCue(player, "idle.restless",
                    Text.translatable("petsplus.emotion_cue.idle.restless"), 2400));
            }
            state.lastIdleCueTick = now;
        }

        if (plan.triggerRainyCue()) {
            // Rainy idle - cozy but slightly bored from rebalanced logic
            pendingWork.add(applyStimulusToComponents(nearbyComponents.values(),
                (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.20f);  // Cozy indoor feeling
                    // Low energy pets get more Ennui
                    collector.pushEmotion(PetComponent.Emotion.ENNUI, 0.10f);  // Mild tedious discomfort
                    // Check if sheltered for extra coziness
                    BlockPos playerPos = player.getBlockPos();
                    if (world.getBlockState(playerPos.up(2)).isSolidBlock(world, playerPos.up(2))) {
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);  // Extra comfort when dry
                    }
                }, builder, world));
            pendingCues.add(() -> EmotionContextCues.sendCue(player, "idle.rainy_cozy",
                Text.translatable("petsplus.emotion_cue.idle.rainy_cozy"), 2400));
            state.lastRainyIdleCueTick = now;
        }

        CompletableFuture<Void> completion = pendingWork.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.allOf(pendingWork.toArray(CompletableFuture[]::new));

        completion.whenComplete((ignored, error) -> {
            StimulusSummary summary = builder.buildWithTick(world.getTime());
            if (!summary.isEmpty()) {
                EmotionContextCues.recordStimulus(player, summary);
            }
            pendingCues.forEach(Runnable::run);
            state.appliedEmotionVersion = Math.max(state.appliedEmotionVersion, version);
        });
    }

    private static Map<UUID, PetComponent> collectNearbyPetComponents(OwnerEventFrame frame,
                                                                      double radius) {
        ServerPlayerEntity owner = frame.owner();
        if (owner == null || owner.isRemoved()) {
            return Collections.emptyMap();
        }
        double radiusSq = radius * radius;
        Map<UUID, PetComponent> result = new HashMap<>();
        for (PetSwarmIndex.SwarmEntry entry : frame.swarmSnapshot()) {
            MobEntity pet = entry.pet();
            PetComponent component = entry.component();
            if (pet == null || component == null) {
                continue;
            }
            if (pet.squaredDistanceTo(owner) > radiusSq) {
                continue;
            }
            result.putIfAbsent(pet.getUuid(), component);
        }
        return result;
    }

    private static CompletableFuture<Void> applyStimulusToComponents(Collection<PetComponent> components,
                                                                     BiConsumer<PetComponent, EmotionStimulusBus.SimpleStimulusCollector> consumer,
                                                                     StimulusSummary.Builder builder,
                                                                     ServerWorld world) {
        if (components == null || components.isEmpty() || world == null) {
            return CompletableFuture.completedFuture(null);
        }

        StateManager stateManager = StateManager.forWorld(world);
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
        List<CompletableFuture<Void>> pendingDispatches = new ArrayList<>();

        for (PetComponent component : components) {
            if (component == null) {
                continue;
            }

            MobEntity pet = component.getPet();
            if (pet == null) {
                continue;
            }

            EnumMap<PetComponent.Emotion, Float> deltas = new EnumMap<>(PetComponent.Emotion.class);
            EmotionStimulusBus.SimpleStimulusCollector collector = (emotion, amount) -> {
                if (emotion == null || amount == 0f) {
                    return;
                }
                deltas.merge(emotion, amount, Float::sum);
            };

            consumer.accept(component, collector);
            if (deltas.isEmpty()) {
                continue;
            }

            Map<PetComponent.Mood, Float> before = snapshotMoodBlend(component);
            bus.queueSimpleStimulus(pet, replayCollector -> {
                for (Map.Entry<PetComponent.Emotion, Float> entry : deltas.entrySet()) {
                    replayCollector.pushEmotion(entry.getKey(), entry.getValue());
                }
            });
            CompletableFuture<Void> dispatch = bus.dispatchStimuliAsync(pet, coordinator);
            if (dispatch == null) {
                Map<PetComponent.Mood, Float> after = snapshotMoodBlend(component);
                builder.addSample(before, after);
                continue;
            }

            PetComponent capturedComponent = component;
            MobEntity capturedPet = pet;
            CompletableFuture<Void> completion = dispatch.handle((ignored, throwable) -> {
                if (throwable != null) {
                    Petsplus.LOGGER.error("Failed to dispatch emotion stimulus for pet {}", capturedPet.getUuid(),
                        unwrapAsyncError(throwable));
                } else {
                    Map<PetComponent.Mood, Float> after = snapshotMoodBlend(capturedComponent);
                    builder.addSample(before, after);
                }
                return null;
            });
            pendingDispatches.add(completion);
        }

        if (pendingDispatches.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(pendingDispatches.toArray(CompletableFuture[]::new));
    }

    private record IdleMaintenancePayload(long now,
                                          long idleTicks,
                                          long lastIdleCueTick,
                                          long lastRainyCueTick,
                                          boolean raining,
                                          TimePhase timePhase,
                                          List<IdlePetSample> petActivity) {
    }

    private record IdlePetSample(long lastAttackTick) {
    }

    private record IdleMaintenancePlan(boolean triggerIdleCue, boolean triggerRainyCue, long idleTicks, boolean isNight) {
        static IdleMaintenancePlan none() { return new IdleMaintenancePlan(false, false, 0L, false); }
        boolean hasActions() { return triggerIdleCue || triggerRainyCue; }
    }

    private record PendingCue(String cueId, Supplier<Text> textSupplier, long cooldown) {
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
                // Reduced rain intensity, check for shelter
                CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                    MobEntity pet = pc.getPet();
                    boolean isExposed = !world.getBlockState(pet.getBlockPos().up(2)).isSolidBlock(world, pet.getBlockPos().up(2));
                    if (isExposed) {
                        collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.25f);  // Reduced from 0.35f
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f);  // Reduced from 0.30f
                        collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.10f);  // Reduced from 0.15f
                        // Add contagion for rain exposure
                        collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.012f);  // Rain startle contagion
                    } else {
                        // Cozy indoors during rain
                        collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.35f);
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);
                        // Add contagion for cozy rain shelter
                        collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.018f);  // Cozy contagion
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.015f);     // Comfort contagion
                    }
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_RAIN_START);
                sendCueAfterStimulus(sp, stimulusFuture,
                    "weather.rain_start",
                    () -> Text.translatable("petsplus.emotion_cue.weather.rain_start"),
                    600);
            } else if (!raining && wasRaining) {
                // Clear weather after rain
                CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.35f);  // Reduced from 0.40f
                    collector.pushEmotion(PetComponent.Emotion.GLEE, 0.20f);  // Reduced from 0.25f
                    // Add Kefi for sunny energy after rain
                    if (world.isDay()) {
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f);
                    }
                    // Add contagion for clear weather relief
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.020f);  // Clear weather relief contagion
                    collector.pushEmotion(PetComponent.Emotion.GLEE, 0.018f);   // Joy contagion
                    if (world.isDay()) {
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.015f);  // Sunny energy contagion
                    }
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_CLEAR);
                sendCueAfterStimulus(sp, stimulusFuture,
                    "weather.rain_end",
                    () -> Text.translatable("petsplus.emotion_cue.weather.rain_end"),
                    600);
            }

            if (thundering && !wasThundering) {
                // Thunder with exposure check
                CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                    MobEntity pet = pc.getPet();
                    boolean isExposed = !world.getBlockState(pet.getBlockPos().up(2)).isSolidBlock(world, pet.getBlockPos().up(2));
                    if (isExposed) {
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.40f);  // Reduced from 0.50f
                        collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.35f);  // Reduced from 0.40f
                        collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.20f);  // Reduced from 0.25f
                        // Add contagion for thunder danger
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.025f);  // Thunder fear contagion
                        collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.020f);   // Thunder startle contagion
                    } else {
                        // Less fear when sheltered
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f);
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.30f);
                        // Add contagion for shared vigilance during storm
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.018f);  // Storm vigilance contagion
                    }
                });
                NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.WEATHER_THUNDER_START);
                sendCueAfterStimulus(sp, stimulusFuture,
                    "weather.thunder",
                    () -> Text.translatable("petsplus.emotion_cue.weather.thunder"),
                    600);
            }
            
            // Add clear night Yūgen trigger for moon phases
            if (!raining && !thundering && world.isNight() && !wasRaining) {
                long moonPhase = (world.getTimeOfDay() / 24000L) % 8L;
                if (moonPhase <= 3) {  // Near full moon
                    CompletableFuture<StimulusSummary> moonFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.30f);  // Subtle beauty of moonlit night
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.15f);  // Transient beauty
                    });
                    sendCueAfterStimulus(sp, moonFuture,
                        "weather.moonlight",
                        () -> Text.translatable("petsplus.emotion_cue.weather.moonlight"),
                        1200);
                }
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
                    CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.35f);
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.25f);
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);
                        // Add contagion for dawn beauty
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.018f);  // Dawn awe contagion
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.015f);       // Dawn relief contagion
                    });
                    sendCueAfterStimulus(sp, stimulusFuture,
                        "time.dawn",
                        () -> Text.translatable("petsplus.emotion_cue.time.dawn"),
                        1200);
                }
                case DUSK -> {
                    CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.35f);
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.20f);  // Alert, not afraid
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.20f);
                        // Add contagion for dusk atmosphere
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.020f);      // Dusk mystery contagion
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.015f);  // Dusk vigilance contagion
                    });
                    sendCueAfterStimulus(sp, stimulusFuture,
                        "time.dusk",
                        () -> Text.translatable("petsplus.emotion_cue.time.dusk"),
                        1200);
                }
                case DAY -> {
                    CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f);
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.15f);  // Relief from dawn
                    });
                    NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.DAYBREAK);
                    sendCueAfterStimulus(sp, stimulusFuture,
                        "time.day",
                        () -> Text.translatable("petsplus.emotion_cue.time.day"),
                        2400);
                }
                case NIGHT -> {
                    CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f);
                        collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.25f);  // Watchful at night
                        // Check if safe with owner for comfort
                        if (pc.getPet().distanceTo(sp) < 8) {
                            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);  // Calm when close to owner
                        }
                    });
                    NatureFlavorHandler.triggerForOwner(sp, 48, Trigger.NIGHTFALL);
                    sendCueAfterStimulus(sp, stimulusFuture,
                        "time.night",
                        () -> Text.translatable("petsplus.emotion_cue.time.night"),
                        2400);
                }
            }
        }
    }

    private static void dispatchWeatherReactions(ServerWorld world) {
        StateManager stateManager = StateManager.forWorld(world);
        for (ServerPlayerEntity owner : world.getPlayers()) {
            AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
            EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
            forEachOwnedPet(owner, 32.0d, (pet, component) -> {
                bus.queueSimpleStimulus(pet, collector -> {
                    addAdvancedWeatherTriggers(pet, component, owner, world, collector);
                    addTagBasedItemTriggers(pet, component, owner, collector);
                });
                bus.dispatchStimuli(pet, coordinator);
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

        StateManager stateManager = StateManager.forWorld(player.getWorld());
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
        forEachOwnedPet(player, 32.0d, (pet, component) -> {
            bus.queueSimpleStimulus(pet, collector -> addTagBasedItemTriggers(pet, component, player, collector));
            bus.dispatchStimuli(pet, coordinator);
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
                CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(player, 32, (pc, collector) -> {
                    collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.45f);
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.35f);
                });
                sendCueAfterStimulus(player, stimulusFuture,
                    "owner.low_hunger",
                    () -> Text.translatable("petsplus.emotion_cue.owner.low_hunger"),
                    600);
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
                if (biomeEntry.isIn(DESERT_LIKE_BIOMES) || biomeKey.equals(BiomeKeys.DESERT) || biomeKey.equals(BiomeKeys.BADLANDS)) {
                    // Desert/Mesa emotions with heat response
                    pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                        MobEntity pet = pc.getPet();
                        // Hiraeth for longing for cooler places
                        collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.30f);
                        // Disgust at the heat/sand
                        collector.pushEmotion(PetComponent.Emotion.DISGUST, 0.15f);
                        // Add contagion for biome discovery
                        collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.018f);  // Biome longing contagion
                        // Species modifier for desert-adapted pets
                        if (pet instanceof net.minecraft.entity.passive.CamelEntity) {
                            // Camels thrive in desert
                            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);
                            collector.pushEmotion(PetComponent.Emotion.DISGUST, -0.15f);  // Remove disgust
                        }
                    }, false);
                }
                if (biomeKey.equals(BiomeKeys.CHERRY_GROVE) || biomeKey.equals(BiomeKeys.FLOWER_FOREST)) {
                    pushToNearbyOwnedPets(player, 32, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.35f);  // Reduced from 0.40f
                        collector.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.30f);  // Reduced from 0.35f
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f);  // Added subtle beauty
                        // Add contagion for beautiful biome discovery
                        collector.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.022f);  // Beauty contagion
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.018f);  // Awe contagion
                    }, false);
                }
                if (biomeKey.equals(BiomeKeys.MUSHROOM_FIELDS)) {
                    // Mushroom fields - balanced harmony
                    pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);  // Perfect balance
                        collector.pushEmotion(PetComponent.Emotion.GLEE, 0.30f);  // Reduced from 0.50f
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.25f);  // Reduced from 0.35f
                    }, false);
                }
                if (biomeKey.equals(BiomeKeys.DEEP_DARK)) {
                    // Deep Dark - dread with mysterious allure
                    pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.50f);  // Reduced from 0.65f
                        collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f);  // Mysterious profundity
                        collector.pushEmotion(PetComponent.Emotion.ANGST, 0.35f);  // Reduced from 0.50f
                    }, false);
                }
                if (biomeEntry.isIn(OCEANIC_BIOMES)) {
                    pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                        MobEntity pet = pc.getPet();
                        collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.20f);  // Reduced from 0.30f
                        // Aquatic pets feel different in ocean
                        if (pet instanceof AxolotlEntity ||
                            pet instanceof TropicalFishEntity) {
                            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);  // Harmony in water
                            collector.pushEmotion(PetComponent.Emotion.FERNWEH, -0.10f);  // Less wanderlust
                        }
                    }, false);
                }
                if (biomeEntry.isIn(SNOWY_BIOMES)) {
                    pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                        collector.pushEmotion(PetComponent.Emotion.STOIC, 0.30f);  // Reduced from 0.35f
                        collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.15f);  // Longing for warmth
                    }, false);
                }
            }
            Identifier newBiomeId = biomeKey.getValue();
            Text biomeName = Text.translatable("biome." + newBiomeId.getNamespace() + "." + newBiomeId.getPath());
            shareOwnerRumor(player, 48, GossipTopics.EXPLORE_NEW_BIOME, 0.5f, 0.4f,
                Text.translatable("petsplus.gossip.exploration.new_biome", biomeName));
            List<PendingCue> pendingCues = new ArrayList<>();
            CompletableFuture<StimulusSummary> summaryFuture = pushToNearbyOwnedPets(player, 48, (pc, collector) -> {
                if (pc.hasRole(PetRoleType.SCOUT)) {
                    MobEntity scoutPet = pc.getPet();
                    if (scoutPet != null) {
                        String cueId = "role.scout.biome." + scoutPet.getUuidAsString();
                        Text cueText = Text.translatable("petsplus.emotion_cue.role.scout_biome",
                            scoutPet.getDisplayName(), biomeName);
                        pendingCues.add(new PendingCue(cueId, () -> cueText, 600));
                    }
                }
            });
            for (PendingCue pending : pendingCues) {
                sendCueAfterStimulus(player, summaryFuture, pending.cueId(), pending.textSupplier(), pending.cooldown());
            }
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
            // Small hop - playful but toned down
            pushToNearbyOwnedPets(player, 16, (pc, collector) -> {
                long now = player.getWorld().getTime();
                if (tryMarkPetBeat(pc, "owner_small_hop", now, 40L)) {
                    collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.02f + magnitude * 0.4f);
                    collector.pushEmotion(PetComponent.Emotion.GLEE, 0.01f + magnitude * 0.3f);
                }
            });
            return;
        }

        if (distance >= 3.0d && distance < 6.0d) {
            // Risky fall (3-6 blocks) - foreboding or startle
            pushToNearbyOwnedPets(player, 24, (pc, collector) -> {
                // Personality-dependent response
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f);  // Anticipatory dread
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.30f);  // Sudden surprise
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.15f + magnitude * 0.3f);
                // Add contagion for owner fall danger
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.020f);  // Fear contagion
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.018f);   // Startle contagion
            });
            return;
        }

        if (distance >= 6.0d && distance < 10.0d) {
            // Moderate fall - increasing worry
            pushToNearbyOwnedPets(player, 28, (pc, collector) -> {
                long now = player.getWorld().getTime();
                if (tryMarkPetBeat(pc, "owner_moderate_fall", now, 120L)) {
                    collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.35f);
                    collector.pushEmotion(PetComponent.Emotion.ANGST, 0.25f + magnitude * 0.4f);
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.20f + magnitude * 0.3f);
                    if (player.getHealth() > 0f) {
                        collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.15f);
                    }
                    // Add contagion for moderate fall
                    collector.pushEmotion(PetComponent.Emotion.ANGST, 0.022f);      // Anxiety contagion
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.018f); // Protective contagion
                }
            });
            return;
        }

        // Big fall (>10 blocks) - intense anxiety
        pushToNearbyOwnedPets(player, 32, (pc, collector) -> {
            long now = player.getWorld().getTime();
            if (tryMarkPetBeat(pc, "owner_big_fall", now, 160L)) {
                collector.pushEmotion(PetComponent.Emotion.ANGST, 0.40f);  // Intense anxiety for big falls
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.35f);
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.30f);  // Dread of impact
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.35f);
                if (player.getHealth() > 0f) {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.30f);  // Major relief on survival
                }
                // Add strong contagion for big fall
                collector.pushEmotion(PetComponent.Emotion.ANGST, 0.025f);       // Strong anxiety contagion
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.020f);  // Dread contagion
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.022f); // Strong protective contagion
                if (player.getHealth() > 0f) {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.018f);  // Relief contagion
                }
            } else {
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.20f);
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.015f);  // Startle contagion
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
    // ==== Utilities ====
    private interface PetConsumer {
        void accept(PetComponent component, EmotionStimulusBus.SimpleStimulusCollector collector);
    }

    private static CompletableFuture<StimulusSummary> pushToNearbyOwnedPets(ServerPlayerEntity owner,
                                                                            double radius,
                                                                            PetConsumer consumer) {
        return pushToNearbyOwnedPets(owner, radius, consumer, true);
    }

    private static CompletableFuture<StimulusSummary> pushToNearbyOwnedPets(ServerPlayerEntity owner,
                                                                            double radius,
                                                                            PetConsumer consumer,
                                                                            boolean recordStimulus) {
        StateManager stateManager = StateManager.forWorld((ServerWorld) owner.getWorld());
        PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        Vec3d center = owner.getPos();
        List<PetSwarmIndex.SwarmEntry> pets = new ArrayList<>();
        swarmIndex.forEachPetInRange(owner, center, radius, pets::add);

        // Early exit: if no pets found, return null to signal no work needed
        if (pets.isEmpty()) {
            return null;
        }

        long startTick = owner.getWorld().getTime();
        StimulusSummary.Builder builder = StimulusSummary.builder(startTick);
        List<CompletableFuture<Void>> pendingDispatches = new ArrayList<>();

        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null) {
                continue;
            }
            try {
                EnumMap<PetComponent.Emotion, Float> deltas = new EnumMap<>(PetComponent.Emotion.class);
                EmotionStimulusBus.SimpleStimulusCollector collector = (emotion, amount) -> {
                    if (emotion == null || amount == 0f) {
                        return;
                    }
                    deltas.merge(emotion, amount, Float::sum);
                };

                Map<PetComponent.Mood, Float> before = snapshotMoodBlend(pc);
                consumer.accept(pc, collector);
                if (deltas.isEmpty()) {
                    continue;
                }

                EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
                bus.queueSimpleStimulus(pet, replayCollector -> {
                    for (Map.Entry<PetComponent.Emotion, Float> delta : deltas.entrySet()) {
                        replayCollector.pushEmotion(delta.getKey(), delta.getValue());
                    }
                });
                CompletableFuture<Void> dispatch = bus.dispatchStimuliAsync(pet, coordinator);
                if (dispatch == null) {
                    Map<PetComponent.Mood, Float> after = snapshotMoodBlend(pc);
                    builder.addSample(before, after);
                    continue;
                }

                PetComponent capturedComponent = pc;
                MobEntity capturedPet = pet;
                CompletableFuture<Void> completion = dispatch.handle((ignored, throwable) -> {
                    if (throwable != null) {
                        Petsplus.LOGGER.error("Failed to dispatch emotion stimulus for pet {}", capturedPet.getUuid(),
                            unwrapAsyncError(throwable));
                    } else {
                        Map<PetComponent.Mood, Float> after = snapshotMoodBlend(capturedComponent);
                        builder.addSample(before, after);
                    }
                    return null;
                });
                pendingDispatches.add(completion);
            } catch (Throwable ignored) {}
        }

        return finalizeStimulusSummary(owner, builder, recordStimulus, pendingDispatches);
    }

    private static void forEachOwnedPet(ServerPlayerEntity owner, double radius,
                                        BiConsumer<MobEntity, PetComponent> consumer) {
        StateManager stateManager = StateManager.forWorld((ServerWorld) owner.getWorld());
        PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
        Vec3d center = owner.getPos();
        List<PetSwarmIndex.SwarmEntry> pets = new ArrayList<>();
        swarmIndex.forEachPetInRange(owner, center, radius, pets::add);
        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent component = entry.component();
            if (pet != null && component != null) {
                consumer.accept(pet, component);
            }
        }
    }

    private static void shareOwnerRumor(ServerPlayerEntity owner, double radius, long topicId,
                                        float intensity, float confidence, @Nullable Text message) {
        if (owner == null) {
            return;
        }
        long currentTick = owner.getWorld().getTime();
        Text payload = message == null ? null : message.copy();
        forEachOwnedPet(owner, radius, (pet, component) ->
            component.recordRumor(topicId, intensity, confidence, currentTick, owner.getUuid(), payload, true));
    }

    private static Map<PetComponent.Mood, Float> snapshotMoodBlend(PetComponent pc) {
        return EmotionBaselineTracker.snapshotBlend(pc);
    }

    private static CompletableFuture<StimulusSummary> pushToSinglePet(ServerPlayerEntity owner,
                                                                      PetComponent pc,
                                                                      PetConsumer consumer) {
        if (owner == null || pc == null) {
            return CompletableFuture.completedFuture(StimulusSummary.empty(
                owner != null ? owner.getWorld().getTime() : 0L));
        }
        long startTick = owner.getWorld().getTime();
        StimulusSummary.Builder builder = StimulusSummary.builder(startTick);
        MobEntity pet = pc.getPet();
        if (pet == null) {
            return CompletableFuture.completedFuture(builder.buildWithTick(startTick));
        }

        EnumMap<PetComponent.Emotion, Float> deltas = new EnumMap<>(PetComponent.Emotion.class);
        EmotionStimulusBus.SimpleStimulusCollector collector = (emotion, amount) -> {
            if (emotion == null || amount == 0f) {
                return;
            }
            deltas.merge(emotion, amount, Float::sum);
        };

        Map<PetComponent.Mood, Float> before = snapshotMoodBlend(pc);
        consumer.accept(pc, collector);
        List<CompletableFuture<Void>> pendingDispatches = new ArrayList<>(1);
        if (!deltas.isEmpty()) {
            StateManager stateManager = StateManager.forWorld((ServerWorld) owner.getWorld());
            AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
            EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
            bus.queueSimpleStimulus(pet, replayCollector -> {
                for (Map.Entry<PetComponent.Emotion, Float> entry : deltas.entrySet()) {
                    replayCollector.pushEmotion(entry.getKey(), entry.getValue());
                }
            });
            CompletableFuture<Void> dispatch = bus.dispatchStimuliAsync(pet, coordinator);
            if (dispatch == null) {
                Map<PetComponent.Mood, Float> after = snapshotMoodBlend(pc);
                builder.addSample(before, after);
            } else {
                PetComponent capturedComponent = pc;
                MobEntity capturedPet = pet;
                CompletableFuture<Void> completion = dispatch.handle((ignored, throwable) -> {
                    if (throwable != null) {
                        Petsplus.LOGGER.error("Failed to dispatch emotion stimulus for pet {}", capturedPet.getUuid(),
                            unwrapAsyncError(throwable));
                    } else {
                        Map<PetComponent.Mood, Float> after = snapshotMoodBlend(capturedComponent);
                        builder.addSample(before, after);
                    }
                    return null;
                });
                pendingDispatches.add(completion);
            }
        }

        return finalizeStimulusSummary(owner, builder, true, pendingDispatches);
    }

    private static CompletableFuture<StimulusSummary> finalizeStimulusSummary(ServerPlayerEntity owner,
                                                                              StimulusSummary.Builder builder,
                                                                              boolean recordStimulus,
                                                                              List<CompletableFuture<Void>> pendingDispatches) {
        if (pendingDispatches.isEmpty()) {
            StimulusSummary summary = builder.buildWithTick(owner.getWorld().getTime());
            if (recordStimulus) {
                EmotionContextCues.recordStimulus(owner, summary);
            }
            return CompletableFuture.completedFuture(summary);
        }

        CompletableFuture<Void> completion = CompletableFuture.allOf(pendingDispatches.toArray(CompletableFuture[]::new));
        CompletableFuture<StimulusSummary> result = new CompletableFuture<>();
        completion.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrapAsyncError(throwable));
                return;
            }

            MinecraftServer server = owner.getServer();
            if (server == null) {
                result.completeExceptionally(new IllegalStateException(
                    "Server unavailable when finalizing stimulus summary"));
                return;
            }

            Runnable task = () -> {
                try {
                    StimulusSummary summary = builder.buildWithTick(owner.getWorld().getTime());
                    if (recordStimulus) {
                        EmotionContextCues.recordStimulus(owner, summary);
                    }
                    result.complete(summary);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                    throw error;
                }
            };

            CompletableFuture<?> scheduled = server.submit(task);
            if (scheduled == null) {
                result.completeExceptionally(new IllegalStateException(
                    "Server rejected stimulus summary scheduling"));
                return;
            }
            scheduled.exceptionally(error -> {
                result.completeExceptionally(error);
                return null;
            });
        });
        return result;
    }

    private static CompletableFuture<StimulusSummary> applyConfiguredStimulus(ServerPlayerEntity owner,
                                                                              String definitionId,
                                                                              double defaultRadius,
                                                                              PetConsumer consumer) {
        EmotionCueDefinition definition = EmotionCueConfig.get().definition(definitionId);
        double radius = definition != null ? definition.resolvedRadius(defaultRadius) : defaultRadius;
        return pushToNearbyOwnedPets(owner, radius, (pc, collector) -> {
            if (definition != null) {
                for (Map.Entry<PetComponent.Emotion, Float> entry : definition.baseEmotions().entrySet()) {
                    collector.pushEmotion(entry.getKey(), entry.getValue());
                }
            }
            consumer.accept(pc, collector);
        });
    }

    private static void triggerConfiguredCue(ServerPlayerEntity owner, String definitionId, double defaultRadius,
                                             String fallbackKey, Object... args) {
        triggerConfiguredCue(owner, definitionId, defaultRadius, (pc, collector) -> {}, fallbackKey, args);
    }

    private static void triggerConfiguredCue(ServerPlayerEntity owner, String definitionId, double defaultRadius,
                                             PetConsumer consumer, String fallbackKey, Object... args) {
        CompletableFuture<StimulusSummary> summaryFuture = applyConfiguredStimulus(owner, definitionId, defaultRadius, consumer);
        sendCueAfterStimulus(owner, summaryFuture, definitionId,
            () -> resolveCueText(definitionId, fallbackKey, args));
    }
    
    /**
     * Trigger ultra-rare ARCANE OVERFLOW mood when player uses enchanting tables/anvils
     * Uses power-based buildup system instead of RNG - enchanting power determines intensity
     */
    private static void triggerArcaneOverflow(ServerPlayerEntity owner, World world) {
        List<MobEntity> nearbyPets = new ArrayList<>();
        int enchantmentBoundCount = 0;
        
        // O(1) entity query with filter
        for (MobEntity entity : world.getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(20), mob -> {
            PetComponent pc = PetComponent.get(mob);
            return pc != null && pc.isOwnedBy(owner);
        })) {
            PetComponent pc = PetComponent.get(entity);
            if (pc != null) {
                nearbyPets.add(entity);
                if (pc.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
                    enchantmentBoundCount++;
                }
            }
        }
        
        if (nearbyPets.isEmpty()) {
            return;
        }
        
        // Count bookshelves using vanilla enchanting table mechanics
        // Must be in 5x5x2 ring (excluding center 3x3) with air gap to table
        BlockPos tablePos = owner.getBlockPos();
        int bookshelfPower = 0;
        
        for (int yOffset = 0; yOffset <= 1; yOffset++) {
            for (int xOffset = -2; xOffset <= 2; xOffset++) {
                for (int zOffset = -2; zOffset <= 2; zOffset++) {
                    // Skip center 3x3 area (no bookshelves directly adjacent)
                    if (Math.abs(xOffset) <= 1 && Math.abs(zOffset) <= 1) {
                        continue;
                    }
                    
                    BlockPos shelfPos = tablePos.add(xOffset, yOffset, zOffset);
                    
                    // Check if bookshelf exists at position
                    if (!world.getBlockState(shelfPos).isOf(Blocks.BOOKSHELF)) {
                        continue;
                    }
                    
                    // Vanilla requires air gap - check blocks between table and shelf
                    boolean hasAirGap = true;
                    if (yOffset == 0) {
                        // Same level - check horizontal path
                        int dx = Integer.compare(xOffset, 0);
                        int dz = Integer.compare(zOffset, 0);
                        BlockPos checkPos = tablePos.add(dx, 0, dz);
                        if (!world.getBlockState(checkPos).isAir()) {
                            hasAirGap = false;
                        }
                    } else {
                        // Upper level - check position above table
                        if (!world.getBlockState(tablePos.up()).isAir()) {
                            hasAirGap = false;
                        }
                    }
                    
                    if (hasAirGap && bookshelfPower < ARCANE_MAX_ENCHANTING_POWER) {
                        bookshelfPower++;
                    }
                }
            }
        }
        
        // Buildup system using state data (O(1) lookup/write)
        long currentTime = world.getTime();
        for (MobEntity pet : nearbyPets) {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) continue;
            
            long lastEnchantTime = pc.getStateData("arcane_last_enchant_time", Long.class, 0L);
            int enchantStreak = pc.getStateData("arcane_enchant_streak", Integer.class, 0);
            
            // Reset streak if timeout elapsed
            if (currentTime - lastEnchantTime > ARCANE_STREAK_TIMEOUT) {
                enchantStreak = 0;
            }
            
            // Increment streak
            enchantStreak++;
            pc.setStateData("arcane_enchant_streak", enchantStreak);
            pc.setStateData("arcane_last_enchant_time", currentTime);
            
            // Power-based trigger (no RNG)
            float powerFactor = Math.min(1.0f, bookshelfPower / (float) ARCANE_MAX_ENCHANTING_POWER);
            float streakFactor = Math.min(1.0f, enchantStreak / (float) ARCANE_MIN_STREAK);
            float ebBonus = pc.hasRole(PetRoleType.ENCHANTMENT_BOUND) ? ARCANE_EB_BONUS : 0f;
            
            float triggerThreshold = ARCANE_BASE_THRESHOLD - (powerFactor * ARCANE_POWER_REDUCTION) 
                                    - (streakFactor * ARCANE_STREAK_REDUCTION) - ebBonus;
            
            // Trigger if threshold is met (high power + streaks + EB role = guaranteed)
            if (triggerThreshold <= 0f) {
                float intensityScale = 1.0f + powerFactor + (enchantmentBoundCount * 0.15f);
                
                pc.pushEmotion(PetComponent.Emotion.ARCANE_OVERFLOW, Math.min(1.0f, 0.70f * intensityScale));
                pc.pushEmotion(PetComponent.Emotion.CURIOUS, 0.40f * intensityScale);
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.35f * intensityScale);
                
                // Enchantment-Bound pets feel it more strongly
                if (pc.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
                    pc.pushEmotion(PetComponent.Emotion.ARCANE_OVERFLOW, 0.40f * intensityScale); // Stack bonus
                    pc.pushEmotion(PetComponent.Emotion.FOCUSED, 0.30f * intensityScale);
                }
                
                // Reset streak after successful trigger
                pc.setStateData("arcane_enchant_streak", 0);
            }
        }
        
        // Log only if significant power present
        if (bookshelfPower >= 10) {
            Petsplus.LOGGER.debug("Arcane Overflow buildup for {} pets ({} EB) near {}'s enchanting (power: {}/15)",
                nearbyPets.size(), enchantmentBoundCount, owner.getName().getString(), bookshelfPower);
        }
    }

    private static void emitPetCue(ServerPlayerEntity owner, PetComponent pc, String cueId, PetConsumer consumer,
                                   String fallbackKey, long fallbackCooldown, Object... args) {
        String definitionId = resolveDefinitionId(cueId);
        EmotionCueDefinition definition = EmotionCueConfig.get().definition(definitionId);
        CompletableFuture<StimulusSummary> summaryFuture = pushToSinglePet(owner, pc, (petComponent, collector) -> {
            if (definition != null) {
                for (Map.Entry<PetComponent.Emotion, Float> entry : definition.baseEmotions().entrySet()) {
                    collector.pushEmotion(entry.getKey(), entry.getValue());
                }
            }
            consumer.accept(petComponent, collector);
        });
        sendCueAfterStimulus(owner, summaryFuture, cueId,
            () -> resolveCueText(definitionId, fallbackKey, args),
            fallbackCooldown);
    }

    private static void sendCueAfterStimulus(ServerPlayerEntity owner,
                                             CompletableFuture<StimulusSummary> summaryFuture,
                                             String cueId,
                                             Supplier<Text> cueTextSupplier) {
        sendCueAfterStimulus(owner, summaryFuture, cueId, cueTextSupplier, 0L);
    }

    private static void sendCueAfterStimulus(ServerPlayerEntity owner,
                                             CompletableFuture<StimulusSummary> summaryFuture,
                                             String cueId,
                                             Supplier<Text> cueTextSupplier,
                                             long cooldownTicks) {
        // Early exit: if no pets were found/affected, don't send any cue
        if (summaryFuture == null) {
            return;
        }
        summaryFuture.whenComplete((summary, throwable) -> {
            if (throwable != null) {
                Petsplus.LOGGER.error("Failed to finalize stimulus summary before emitting cue {}", cueId,
                    unwrapAsyncError(throwable));
                return;
            }

            // Skip cue if no pets were actually affected
            if (summary == null || summary.isEmpty()) {
                return;
            }

            MinecraftServer server = owner.getServer();
            if (server == null) {
                Petsplus.LOGGER.warn("Skipping cue {} for owner {}: server unavailable for main-thread scheduling",
                    cueId, owner.getUuid());
                return;
            }

            Runnable task = () -> {
                try {
                    deliverCue(owner, cueId, cueTextSupplier, cooldownTicks);
                } catch (Throwable applyError) {
                    Petsplus.LOGGER.error("Failed to deliver cue {} on main thread", cueId, applyError);
                    throw applyError;
                }
            };

            CompletableFuture<?> scheduled = server.submit(task);
            if (scheduled == null) {
                Petsplus.LOGGER.warn("Server rejected cue {} scheduling for owner {}", cueId, owner.getUuid());
                return;
            }
            scheduled.exceptionally(error -> {
                Petsplus.LOGGER.error("Cue {} scheduling failed", cueId, error);
                return null;
            });
        });
    }

    private static void deliverCue(ServerPlayerEntity owner,
                                   String cueId,
                                   Supplier<Text> cueTextSupplier,
                                   long cooldownTicks) {
        if (cueTextSupplier == null) {
            return;
        }
        Text base = cueTextSupplier.get();
        if (base == null) {
            return;
        }
        MutableText cueText = base.copy();
        if (cueText.getString().isEmpty()) {
            return;
        }
        if (cooldownTicks > 0L) {
            EmotionContextCues.sendCue(owner, cueId, cueText, cooldownTicks);
        } else {
            EmotionContextCues.sendCue(owner, cueId, cueText);
        }
    }

    private static Throwable unwrapAsyncError(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
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
        handleNuancedEmotions(player, world, currentTick, null);
    }

    private static void handleNuancedEmotions(ServerPlayerEntity player,
                                              ServerWorld world,
                                              long currentTick,
                                              @Nullable List<PetSwarmIndex.SwarmEntry> presetSnapshot) {
        PetSwarmIndex swarm = StateManager.forWorld(world).getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> nearbyPets = new ArrayList<>();
        if (presetSnapshot != null && !presetSnapshot.isEmpty()) {
            double radiusSq = 32.0d * 32.0d;
            Vec3d ownerPos = player.getPos();
            for (PetSwarmIndex.SwarmEntry entry : presetSnapshot) {
                MobEntity pet = entry.pet();
                if (pet == null) {
                    continue;
                }
                if (pet.squaredDistanceTo(ownerPos) > radiusSq) {
                    continue;
                }
                nearbyPets.add(entry);
            }
        } else {
            swarm.forEachPetInRange(player, player.getPos(), 32.0, nearbyPets::add);
        }

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
        Map<UUID, PetSwarmIndex.SwarmEntry> entriesById = new HashMap<>(pets.size());
        List<PackPetInput> packInputs = new ArrayList<>(pets.size());
        StateManager stateManager = StateManager.forWorld(world);
        EmotionAccumulatorBatch accumulators = new EmotionAccumulatorBatch(
            stateManager.getAsyncWorkCoordinator(),
            world,
            owner
        );

        for (PetSwarmIndex.SwarmEntry entry : pets) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null) {
                continue;
            }
            entriesById.put(pet.getUuid(), entry);
            PetSocialData data = new PetSocialData(entry, currentTick);
            petDataCache.put(pet, data);
            PackPetInput input = PackPetInput.from(entry, data);
            if (input != null) {
                packInputs.add(input);
            }
        }

        schedulePackSummaries(owner, world, currentTick, stateManager, petDataCache, entriesById, packInputs);

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

            EmotionStimulusBus.SimpleStimulusCollector collector = accumulators.collectorFor(pc);
            addSocialAwarenessTriggers(pet, pc, owner, world, collector);
            addEnvironmentalMicroTriggers(pet, pc, owner, world, collector);
            addMovementActivityTriggers(pet, pc, owner, world, collector);
            addRoleSpecificAmbientTriggers(pet, pc, owner, world, collector);
            addSpeciesSpecificTriggers(pet, pc, owner, world, collector);
            sustainMusicEmotions(pet, pc, owner, world, currentTick, collector);

            processSocialRoutines(pet, pc, petDataCache, owner, world, currentTick, swarm, accumulators);
        }

        accumulators.flush();
    }

    private static void schedulePackSummaries(@Nullable ServerPlayerEntity owner,
                                              ServerWorld world,
                                              long currentTick,
                                              StateManager stateManager,
                                              Map<MobEntity, PetSocialData> petDataCache,
                                              Map<UUID, PetSwarmIndex.SwarmEntry> entriesById,
                                              List<PackPetInput> inputs) {
        if (world == null || inputs.isEmpty()) {
            return;
        }
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        List<PackPetInput> jobInputs = List.copyOf(inputs);
        String descriptor;
        if (owner != null) {
            descriptor = "pack-summary-" + owner.getUuid();
        } else {
            descriptor = "pack-summary-world-" + world.getRegistryKey().getValue();
        }
        coordinator.submitStandalone(
            descriptor,
            () -> computePackSummaries(jobInputs),
            summaries -> applyPackSummaries(owner, world, currentTick, stateManager,
                petDataCache, entriesById, summaries),
            AsyncJobPriority.NORMAL
        ).whenComplete((ignored, error) -> {
            if (error == null) {
                return;
            }
            Throwable cause = unwrapAsyncError(error);
            UUID ownerId = owner != null ? owner.getUuid() : null;
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Async pack summary rejected for owner {}", ownerId);
            } else {
                Petsplus.LOGGER.error("Async pack summary failed for owner {}", ownerId, cause);
            }
            Runnable fallback = () -> runPackSummaryFallback(owner, world, currentTick, stateManager,
                petDataCache, entriesById, jobInputs);
            executeOnMainThread(world, fallback).whenComplete((ignoredResult, fallbackError) -> {
                if (fallbackError != null) {
                    Petsplus.LOGGER.error("Synchronous pack summary fallback failed for owner {}", ownerId,
                        fallbackError);
                }
            });
        });
    }

    private static void runPackSummaryFallback(@Nullable ServerPlayerEntity owner,
                                               @Nullable ServerWorld world,
                                               long currentTick,
                                               StateManager stateManager,
                                               Map<MobEntity, PetSocialData> petDataCache,
                                               Map<UUID, PetSwarmIndex.SwarmEntry> entriesById,
                                               List<PackPetInput> inputs) {
        Map<UUID, PackSummary> summaries = computePackSummaries(inputs);
        applyPackSummaries(owner, world, currentTick, stateManager, petDataCache, entriesById, summaries);
    }

    private static Map<UUID, PackSummary> computePackSummaries(List<PackPetInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Map.of();
        }
        double packRadiusSq = SocialContextSnapshot.PACK_SAMPLE_RADIUS * SocialContextSnapshot.PACK_SAMPLE_RADIUS;
        Map<UUID, PackSummary> result = new HashMap<>(inputs.size());
        for (PackPetInput self : inputs) {
            int packCount = 0;
            boolean hasOlder = false;
            boolean hasYounger = false;
            boolean hasEldest = false;
            boolean hasSimilarAge = false;
            boolean hasNewborn = false;
            float strongestBond = 0f;
            double closestDistanceSq = Double.MAX_VALUE;
            UUID closestNeighbor = null;
            List<NeighborCandidate> moodCandidates = new ArrayList<>();

            for (PackPetInput other : inputs) {
                if (self == other) {
                    continue;
                }
                double dx = self.x - other.x;
                double dy = self.y - other.y;
                double dz = self.z - other.z;
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > packRadiusSq) {
                    continue;
                }

                packCount++;
                long selfAge = Math.max(1L, self.age);
                long otherAge = Math.max(0L, other.age);
                if (otherAge > selfAge * 2L) {
                    hasEldest = true;
                } else if (otherAge > selfAge) {
                    hasOlder = true;
                } else if (otherAge < Math.max(1L, selfAge) / 2L) {
                    hasYounger = true;
                }

                if (otherAge < 24000L) {
                    hasNewborn = true;
                }

                double ageRatio = selfAge > 0 ? (double) otherAge / (double) selfAge : 1.0;
                if (Math.abs(ageRatio - 1.0) <= 0.2) {
                    hasSimilarAge = true;
                }

                float bondDiff = Math.abs(self.bondStrength - other.bondStrength);
                if (bondDiff < 0.2f) {
                    strongestBond = Math.max(strongestBond,
                        Math.min(self.bondStrength, other.bondStrength));
                }

                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestNeighbor = other.petId;
                }

                if (other.mood != null) {
                    moodCandidates.add(new NeighborCandidate(other.petId, distSq));
                }
            }

            List<UUID> moodNeighbors = selectClosestMoodNeighbors(moodCandidates,
                SocialContextSnapshot.MAX_PACK_SAMPLE);
            moodNeighbors = moodNeighbors.isEmpty() ? List.of() : List.copyOf(moodNeighbors);
            double closestDistance = closestDistanceSq == Double.MAX_VALUE
                ? Double.MAX_VALUE
                : Math.sqrt(closestDistanceSq);

            result.put(self.petId, new PackSummary(packCount, hasOlder, hasYounger, hasEldest,
                hasSimilarAge, hasNewborn, strongestBond, closestNeighbor, closestDistance,
                moodNeighbors));
        }
        return result;
    }

    private static List<UUID> selectClosestMoodNeighbors(List<NeighborCandidate> candidates, int limit) {
        if (candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        candidates.sort(Comparator.comparingDouble(NeighborCandidate::distanceSq));
        int capped = Math.min(limit, candidates.size());
        List<UUID> ids = new ArrayList<>(capped);
        for (int i = 0; i < capped; i++) {
            ids.add(candidates.get(i).petId());
        }
        return ids;
    }

    private static void applyPackSummaries(@Nullable ServerPlayerEntity owner,
                                           ServerWorld world,
                                           long currentTick,
                                           StateManager stateManager,
                                           Map<MobEntity, PetSocialData> petDataCache,
                                           Map<UUID, PetSwarmIndex.SwarmEntry> entriesById,
                                           Map<UUID, PackSummary> summaries) {
        if (summaries == null || summaries.isEmpty() || world == null) {
            return;
        }
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        EmotionAccumulatorBatch accumulator = new EmotionAccumulatorBatch(coordinator, world, owner);

        for (Map.Entry<UUID, PackSummary> entry : summaries.entrySet()) {
            PackSummary summary = entry.getValue();
            if (summary == null) {
                continue;
            }
            PetSwarmIndex.SwarmEntry swarmEntry = entriesById.get(entry.getKey());
            if (swarmEntry == null) {
                continue;
            }
            MobEntity pet = swarmEntry.pet();
            PetComponent component = swarmEntry.component();
            if (pet == null || component == null) {
                continue;
            }
            PetSocialData petData = petDataCache.computeIfAbsent(pet,
                ignored -> new PetSocialData(pet, component, currentTick));
            SocialContextSnapshot context = new SocialContextSnapshot(pet, component, owner,
                world, currentTick, petData, petDataCache, accumulator);

            PetSocialData closest = null;
            if (summary.closestNeighborId() != null) {
                PetSwarmIndex.SwarmEntry closestEntry = entriesById.get(summary.closestNeighborId());
                if (closestEntry != null && closestEntry.pet() != null) {
                    closest = petDataCache.computeIfAbsent(closestEntry.pet(),
                        ignored -> new PetSocialData(closestEntry, currentTick));
                }
            }

            List<PetSocialData> moodNeighbors = new ArrayList<>();
            for (UUID neighborId : summary.moodNeighborIds()) {
                PetSwarmIndex.SwarmEntry neighborEntry = entriesById.get(neighborId);
                if (neighborEntry == null || neighborEntry.pet() == null) {
                    continue;
                }
                PetSocialData data = petDataCache.computeIfAbsent(neighborEntry.pet(),
                    ignored -> new PetSocialData(neighborEntry, currentTick));
                if (data.currentMood() != null) {
                    moodNeighbors.add(data);
                }
            }

            context.setPackObservations(summary.neighborCount(), summary.hasOlderPet(), summary.hasYoungerPet(),
                summary.hasEldestPet(), summary.hasSimilarAge(), summary.hasNewbornPet(),
                summary.strongestBondResonance(), closest, summary.closestDistance());
            context.setMoodNeighbors(moodNeighbors);
            PACK_ROUTINE.applyEffects(context);
        }

        accumulator.flush();
    }

    private static CompletableFuture<Void> executeOnMainThread(@Nullable ServerWorld world, Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (world != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                CompletableFuture<?> submitted = server.submit(task);
                if (submitted == null) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> future = new CompletableFuture<>();
                submitted.whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(null);
                    }
                });
                return future;
            }
        }
        try {
            task.run();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    private static void processSocialRoutines(MobEntity pet, PetComponent pc,
                                              Map<MobEntity, PetSocialData> petDataCache,
                                              ServerPlayerEntity owner, ServerWorld world, long currentTick,
                                              PetSwarmIndex swarm,
                                              EmotionAccumulatorBatch accumulators) {
        PetSocialData petData = petDataCache.computeIfAbsent(pet, key -> new PetSocialData(pet, pc, currentTick));
        SocialContextSnapshot context = new SocialContextSnapshot(pet, pc, owner, world, currentTick, petData, petDataCache,
            accumulators);
        for (SocialBehaviorRoutine routine : SOCIAL_ROUTINES) {
            context.resetTransientState();
            if (!routine.shouldRun(context)) {
                continue;
            }
            routine.gatherContext(context, swarm, currentTick);
            routine.applyEffects(context);
        }
        if (tryMarkPetBeat(pc, "social_hierarchy", currentTick, 1200)) {
            scheduleHierarchyEvaluation(context, swarm);
        }
    }

    private static void scheduleHierarchyEvaluation(SocialContextSnapshot context,
                                                    PetSwarmIndex swarm) {
        if (context == null) {
            return;
        }
        PetSocialData petData = context.petData();
        if (petData == null) {
            return;
        }
        ServerPlayerEntity owner = context.owner();
        if (!(owner != null && owner.getWorld() instanceof ServerWorld world)) {
            return;
        }

        SocialContextSnapshot.NeighborSummary summary = context.ensureNeighborSample(swarm);
        List<Integer> neighborAges = new ArrayList<>();
        for (SocialContextSnapshot.NeighborSample sample : summary.samplesWithin(100.0)) {
            PetSocialData otherData = sample.data();
            if (otherData == null) {
                continue;
            }
            long age = otherData.age();
            neighborAges.add((int) Math.min(Integer.MAX_VALUE, Math.max(0L, age)));
        }

        if (neighborAges.isEmpty()) {
            applyHierarchyEvaluation(context, HierarchyEvaluation.empty());
            return;
        }

        StateManager stateManager = StateManager.forWorld(world);
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        HierarchyJobPayload payload = new HierarchyJobPayload(
            (int) Math.min(Integer.MAX_VALUE, Math.max(0L, petData.age())),
            neighborAges
        );

        coordinator.submitStandalone(
            "social-hierarchy-" + context.pet().getUuid(),
            () -> computeHierarchyResult(payload),
            result -> applyHierarchyEvaluation(context, result),
            AsyncJobPriority.NORMAL
        ).exceptionally(error -> {
            Throwable cause = unwrapAsyncError(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Hierarchy analysis rejected for pet {}", context.pet().getUuid());
            } else {
                Petsplus.LOGGER.error("Hierarchy analysis failed for pet {}", context.pet().getUuid(), cause);
            }
            return null;
        });
    }

    private static HierarchyEvaluation computeHierarchyResult(HierarchyJobPayload payload) {
        if (payload == null || payload.neighborAges().isEmpty()) {
            return HierarchyEvaluation.empty();
        }
        int total = 0;
        int younger = 0;
        for (int neighborAge : payload.neighborAges()) {
            if (neighborAge < 0) {
                continue;
            }
            total++;
            if (neighborAge < payload.selfAge()) {
                younger++;
            }
        }
        if (total == 0) {
            return HierarchyEvaluation.empty();
        }
        return new HierarchyEvaluation(total, younger);
    }

    private static void applyHierarchyEvaluation(SocialContextSnapshot context,
                                                 HierarchyEvaluation evaluation) {
        if (context == null || evaluation == null || !evaluation.hasData()) {
            return;
        }

        float hierarchyPosition = (float) evaluation.youngerNeighbors() / evaluation.totalNeighbors();
        MobEntity pet = context.pet();
        PetComponent component = context.component();
        ServerWorld world = context.world();
        if (pet == null || component == null || world == null) {
            return;
        }

        StateManager stateManager = StateManager.forWorld(world);
        AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();

        if (hierarchyPosition > 0.7f) {
            // High rank - alpha assertion with cultural emotions
            bus.queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.25f);  // Joyful leadership passion
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.40f);  // Strong protective instinct
                collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.15f);  // Moderate pride
                // Add contagion spreading for alpha influence
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.015f);  // Subtle alpha influence contagion
            });
            bus.dispatchStimuli(pet, coordinator);
            EmotionContextCues.sendCue(context.owner(), "social.alpha." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.alpha", context.pet().getDisplayName()), 600);
        } else if (hierarchyPosition >= 0.3f && hierarchyPosition <= 0.7f) {
            // Middle rank - beta submission with harmony
            bus.queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.20f);  // Relaxed social bonding
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);  // Balanced acceptance
                collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.10f);  // Community feeling
            });
            bus.dispatchStimuli(pet, coordinator);
            EmotionContextCues.sendCue(context.owner(), "social.beta." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.beta", context.pet().getDisplayName()), 500);
        } else {
            // Low rank - omega with mild discontent
            bus.queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.ENNUI, 0.15f);  // Boredom in low status
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.10f);  // Some acceptance
                collector.pushEmotion(PetComponent.Emotion.LOYALTY, 0.15f);  // Loyal despite position
            });
            bus.dispatchStimuli(pet, coordinator);
            EmotionContextCues.sendCue(context.owner(), "social.omega." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.omega", context.pet().getDisplayName()), 500);
        }

        // Group bonding for all ranks when multiple pets present
        if (evaluation.totalNeighbors() >= 3) {
            bus.queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.25f);  // Strong group unity
            });
            bus.dispatchStimuli(pet, coordinator);
        }
    }

    private static void addOtherPetHurtTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                               EmotionStimulusBus.SimpleStimulusCollector collector) {
        // Check for other hurt pets nearby
        List<MobEntity> nearbyPets = world.getEntitiesByClass(
            MobEntity.class,
            pet.getBoundingBox().expand(16),
            entity -> entity != pet && PetComponent.get(entity) != null && entity.isAlive()
        );

        for (MobEntity otherPet : nearbyPets) {
            PetComponent otherPc = PetComponent.get(otherPet);
            if (otherPc != null && otherPc.getOwner() == owner) {
                float otherHealth = otherPet.getHealth() / otherPet.getMaxHealth();
                if (otherHealth < 0.5f) {
                    // Other pet is hurt
                    collector.pushEmotion(PetComponent.Emotion.EMPATHY, 0.25f);  // Empathy for hurt companion
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.20f);  // Want to protect
                    collector.pushEmotion(PetComponent.Emotion.WORRIED, 0.15f);  // Concern
                    break;  // Only react once
                }
            }
        }
    }

    private record HierarchyJobPayload(int selfAge, List<Integer> neighborAges) {
    }

    private record HierarchyEvaluation(int totalNeighbors, int youngerNeighbors) {
        static HierarchyEvaluation empty() { return new HierarchyEvaluation(0, 0); }
        boolean hasData() { return totalNeighbors > 0; }
    }

    private record PackPetInput(UUID petId,
                                long age,
                                float bondStrength,
                                @Nullable PetComponent.Mood mood,
                                double x,
                                double y,
                                double z) {
        static PackPetInput from(PetSwarmIndex.SwarmEntry entry, PetSocialData data) {
            MobEntity pet = entry.pet();
            if (pet == null) {
                return null;
            }
            return new PackPetInput(
                pet.getUuid(),
                data.age(),
                data.bondStrength(),
                data.currentMood(),
                data.x(),
                data.y(),
                data.z()
            );
        }
    }

    private record NeighborCandidate(UUID petId, double distanceSq) {
    }

    private record PackSummary(int neighborCount,
                               boolean hasOlderPet,
                               boolean hasYoungerPet,
                               boolean hasEldestPet,
                               boolean hasSimilarAge,
                               boolean hasNewbornPet,
                               float strongestBondResonance,
                               @Nullable UUID closestNeighborId,
                               double closestDistance,
                               List<UUID> moodNeighborIds) {
    }


    private static void addSocialAwarenessTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                                   EmotionStimulusBus.SimpleStimulusCollector collector) {
        double distanceToOwner = pet.squaredDistanceTo(owner);
        Vec3d ownerLookDir = owner.getRotationVec(1.0f);
        Vec3d petToPet = pet.getPos().subtract(owner.getPos()).normalize();
        double lookAlignment = ownerLookDir.dotProduct(petToPet);

        // Owner looking directly at pet - awareness and attention
        if (distanceToOwner < 64 && lookAlignment > 0.8) { // Owner looking at pet
            collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.30f); // Increased cozy attention
            if (pet.getHealth() / pet.getMaxHealth() < 0.7f) {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.25f); // Reduced relief when noticed
                collector.pushEmotion(PetComponent.Emotion.EMPATHY, 0.20f); // Owner's care
            }
            EmotionContextCues.sendCue(owner, "social.look." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.look", pet.getDisplayName()), 200);
        }

        // Owner proximity dynamics with context-aware separation emotions
        if (distanceToOwner < 4) { // Very close - petting range
            collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.30f); // Cozy bonding
            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f); // Perfect contentment
            // Add relationship guard increase for closeness
            pc.setStateData("relationship_boost", 0.10f);
            EmotionContextCues.sendCue(owner, "social.close." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.close", pet.getDisplayName()), 200);
        } else if (distanceToOwner > 50 * 50) { // >50 blocks away (squared for efficiency)
            collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.25f); // Homesick longing
            collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f); // Some wanderlust mixed in
            // Note: Decay for Hiraeth should be slower (0.998/tick) - handled in PetMoodEngine
            EmotionContextCues.sendCue(owner, "social.far." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.far", pet.getDisplayName()), 400);
        }

        // Owner's current activity awareness - combat aid
        if (owner.getAttackCooldownProgress(0.0f) < 1.0f) {
            collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.50f); // Strong protective instinct
            collector.pushEmotion(PetComponent.Emotion.EMPATHY, 0.25f); // Supportive empathy
            // Check if pet successfully aided (e.g., pet has recent attack)
            if ((world.getTime() - pc.getLastAttackTick()) < 100) {
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f); // Victory joy from helping
            }
            EmotionContextCues.sendCue(owner, "social.combat." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.combat", pet.getDisplayName()), 200);
        }

        if (owner.isSneaking() && distanceToOwner < 16) {
            collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f); // Increased subtle awareness
            collector.pushEmotion(PetComponent.Emotion.FOCUSED, 0.15f); // Alert to owner's stealth
            EmotionContextCues.sendCue(owner, "social.sneak." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.sneak", pet.getDisplayName()), 200);
            if (pc.hasRole(PetRoleType.ECLIPSED)) {
                collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.10f); // Extra mysterious feeling
                EmotionContextCues.sendCue(owner,
                    "role.eclipsed.shadow." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.eclipsed_shroud", pet.getDisplayName()),
                    200);
            }
        }

        // Owner health awareness with context-aware emotions
        float ownerHealthPercent = owner.getHealth() / owner.getMaxHealth();
        if (ownerHealthPercent < 0.3f && distanceToOwner < 64) {
            collector.pushEmotion(PetComponent.Emotion.ANGST, 0.45f); // Reduced worry
            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.50f); // Strong protection
            collector.pushEmotion(PetComponent.Emotion.EMPATHY, 0.30f); // Deep empathy for hurt owner
            EmotionContextCues.sendCue(owner, "social.owner_hurt." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.owner_hurt", pet.getDisplayName()), 200);
            if (pc.hasRole(PetRoleType.GUARDIAN)) {
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.20f); // Extra for guardians
                EmotionContextCues.sendCue(owner,
                    "role.guardian.vigil." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.role.guardian_vigil", pet.getDisplayName()),
                    200);
            }
        }
    }

    private static void addEnvironmentalMicroTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner,
                                                      ServerWorld world,
                                                      EmotionStimulusBus.SimpleStimulusCollector collector) {
        BlockPos petPos = pet.getBlockPos();

        // Graduated light level awareness with context
        int lightLevel = world.getLightLevel(petPos);
        boolean isNight = world.isNight();
        boolean nearOwner = pet.distanceTo(owner) < 6;
        boolean inShelter = world.getBlockState(petPos.up(2)).isSolidBlock(world, petPos.up(2));
        
        if (lightLevel <= 3) {
            // Very dark - check context before fear
            if (!nearOwner && !inShelter) {
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.20f); // Only afraid if alone in dark
                collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.25f);
            } else {
                collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.20f); // Just watchful if safe
                if (nearOwner) {
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.15f); // Protective of owner in dark
                }
            }
            EmotionContextCues.sendCue(owner, "environment.dark." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.dark", pet.getDisplayName()), 200);
        } else if (lightLevel <= 7) {
            // Dim light - vigilant but not afraid
            collector.pushEmotion(PetComponent.Emotion.VIGILANT, 0.15f);
            if (isNight) {
                collector.pushEmotion(PetComponent.Emotion.CURIOUS, 0.10f); // Curious about night sounds
            }
        } else if (lightLevel <= 11) {
            // Moderate light - comfortable
            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.10f);
        } else if (lightLevel >= 12) {
            // Bright light - energizing
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.12f);
            if (isNight && lightLevel >= 14) {
                // Well-lit at night = safety
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.15f);
                collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.10f);
            }
            EmotionContextCues.sendCue(owner, "environment.bright." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.bright", pet.getDisplayName()), 400);
        }

        // Height awareness
        int y = petPos.getY();
        if (y > 120) { // High altitude
            collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.18f); // Awe at heights
            collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f); // Wanderlust from vistas
            EmotionContextCues.sendCue(owner, "environment.high." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.high", pet.getDisplayName()), 400);
        } else if (y < 20) { // Deep underground
            collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f); // Underground unease
            EmotionContextCues.sendCue(owner, "environment.deep." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.deep", pet.getDisplayName()), 400);
        }

        // Water proximity
        if (world.getBlockState(petPos.down()).getFluidState().isEmpty() == false) {
            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f); // Water brings balance
            EmotionContextCues.sendCue(owner, "environment.water." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.environment.water", pet.getDisplayName()), 400);
        }

        // Flowers and nature
        for (BlockPos offset : BlockPos.iterate(petPos.add(-2, -1, -2), petPos.add(2, 1, 2))) {
            BlockState blockState = world.getBlockState(offset);
            if (blockState.isIn(NATURE_PLANTS)) {
                collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.12f); // Beauty of nature
                EmotionContextCues.sendCue(owner, "environment.flower." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.environment.flower", pet.getDisplayName()), 400);
                break;
            }
            // Check for safety indicators at night
            if (isNight && (blockState.isIn(BlockTags.CAMPFIRES) || blockState.getBlock() instanceof BedBlock)) {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.15f);
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.20f);
                // Reduce vigilance when near safe spaces
                collector.pushEmotion(PetComponent.Emotion.VIGILANT, -0.10f);
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

            // Context-aware threat response
            // Recalculate nearOwner for hostile response distance (different threshold)
            nearOwner = pet.distanceTo(owner) < 8;

            float baseVigilant = (0.20f + varietyFactor * 0.15f) * countFactor * clusterPenalty;
            float baseForeboding = (0.15f + varietyFactor * 0.10f) * countFactor * clusterPenalty;
            float baseProtectiveness = (0.18f + varietyFactor * 0.12f) * countFactor * clusterPenalty;

            // Apply fatigue to reduce repetitive fear from the same hostile situation
            long currentTime = world.getTime();
            String hostileSignature = createHostileSignature(nearbyHostiles, hostileTypes);
            float fatigueFactor = calculateHostileFatigue(pc, hostileSignature, currentTime, baseForeboding, 300); // 15s fatigue window

            if (fatigueFactor > 0.2f) { // Only trigger if not heavily fatigued
                // Always become vigilant first
                collector.pushEmotion(PetComponent.Emotion.VIGILANT, baseVigilant * fatigueFactor);

                // Only feel fear if conditions warrant it
                if (!nearOwner && (isNight || lightLevel <= 7)) {
                    collector.pushEmotion(PetComponent.Emotion.FOREBODING, baseForeboding * fatigueFactor);
                }

                // Always protective, especially near owner
                float protectiveBonus = nearOwner ? 1.5f : 1.0f;
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, baseProtectiveness * fatigueFactor * protectiveBonus);
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

    private static void addMovementActivityTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                                    EmotionStimulusBus.SimpleStimulusCollector collector) {
        Vec3d velocity = pet.getVelocity();
        double speed = velocity.length();
        boolean isCat = pet instanceof CatEntity;
        
        // Calculate distance from spawn/home
        Vec3d spawnPos = Vec3d.ofCenter(world.getSpawnPos());
        double distanceFromSpawn = pet.getPos().distanceTo(spawnPos);

        // Movement patterns with context-aware emotions
        if (speed > 0.3) { // Pet is moving very fast
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.15f * (float)Math.min(speed, 1.0));  // Scale with velocity
            collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.10f); // Adventure spirit
            EmotionContextCues.sendCue(owner, "movement.run." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.run", pet.getDisplayName()), 200);
        } else if (speed > 0.1 && speed <= 0.3) { // Safe exploration speed
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.15f); // Spirited exploration
            collector.pushEmotion(PetComponent.Emotion.CURIOUS, 0.30f); // Curiosity during exploration
        } else if (speed < 0.01) { // Pet is very still
            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.05f); // Peaceful stillness
            EmotionContextCues.sendCue(owner, "movement.still." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.still", pet.getDisplayName()), 200);
        }
        
        // Long trek detection
        if (distanceFromSpawn > 1000) {
            collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.20f); // Homesickness on long journeys
            collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f); // Mixed with wanderlust
        }

        // Falling or jumping - more nuanced responses
        if (velocity.y < -0.8) { // Falling very fast - concern
            collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f);  // Dread of falling
            collector.pushEmotion(PetComponent.Emotion.ANGST, 0.20f);
            EmotionContextCues.sendCue(owner, "movement.fall." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.fall", pet.getDisplayName()), 200);
        } else if (velocity.y > 0.4) { // Jumping up - moderate joy
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.10f); // Joy of leaping
            collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.08f);
            EmotionContextCues.sendCue(owner, "movement.jump." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.jump", pet.getDisplayName()), 200);
        }

        // Swimming - more balanced reactions
        if (pet.isInFluid()) {
            if (isCat) {
                collector.pushEmotion(PetComponent.Emotion.DISGUST, 0.08f); // Cats dislike water but not extreme
                collector.pushEmotion(PetComponent.Emotion.ANGST, 0.06f);
                EmotionContextCues.sendCue(owner, "movement.cat_swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.cat_swim", pet.getDisplayName()), 200);
            } else {
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f); // Others find it refreshing
                EmotionContextCues.sendCue(owner, "movement.swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.swim", pet.getDisplayName()), 200);
            }
        }
    }
    private static void addRoleSpecificAmbientTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                                       EmotionStimulusBus.SimpleStimulusCollector collector) {
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

    private static void sustainMusicEmotions(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world, long now,
                                              EmotionStimulusBus.SimpleStimulusCollector collector) {
        // Check if music is currently playing near pet
        Boolean musicPlaying = pc.getStateData("music_playing", Boolean.class);
        if (musicPlaying == null || !musicPlaying) {
            // Throttle initial music detection checks per pet
            Long lastMusicCheck = pc.getStateData("last_music_check", Long.class);
            if (lastMusicCheck != null && now - lastMusicCheck < 100) { // Only check every 5 seconds per pet
                return;
            }
            pc.setStateData("last_music_check", now);

            // Check if jukebox is playing nearby (using optimized radius)
            if (isJukeboxPlaying(world, pet.getBlockPos(), OPTIMIZED_MUSIC_RADIUS)) {
                // Start new music session
                pc.setStateData("music_playing", true);
                pc.setStateData("music_start_time", now);
                pc.setStateData("last_jukebox_pos", pet.getBlockPos().toImmutable());
            } else {
                return;
            }
        }

        Long musicStartTime = pc.getStateData("music_start_time", Long.class);
        if (musicStartTime == null) {
            musicStartTime = now;
            pc.setStateData("music_start_time", musicStartTime);
        }

        long musicDuration = now - musicStartTime;

        // Enhanced throttling: Only verify music is still playing every 15 seconds during active sessions
        Long lastMusicVerification = pc.getStateData("last_music_verification", Long.class);
        boolean shouldVerifyMusic = lastMusicVerification == null || now - lastMusicVerification > JUKEBOX_CACHE_DURATION;

        if (shouldVerifyMusic) {
            pc.setStateData("last_music_verification", now);

            // Check if music is still playing (with optimized detection)
            if (!isJukeboxPlaying(world, pet.getBlockPos(), OPTIMIZED_MUSIC_RADIUS)) {
                // Music stopped - apply decay
                if (musicDuration > 100) {
                    // Apply lingering emotions after music stops
                    String peakEmotion = pc.getStateData("music_peak_emotion", String.class);
                    Float peakIntensity = pc.getStateData("music_peak_intensity", Float.class);
                    if (peakEmotion != null && peakIntensity != null) {
                        try {
                            PetComponent.Emotion emotion = PetComponent.Emotion.valueOf(peakEmotion);
                            // Gradually decay the peak emotion
                            float decayFactor = (float) Math.exp(-(now - musicStartTime - 2400) / 1200.0); // Decay over 1 minute
                            if (decayFactor > 0.1f) {
                                collector.pushEmotion(emotion, peakIntensity * decayFactor * 0.3f);
                                collector.pushEmotion(PetComponent.Emotion.NOSTALGIA, 0.10f * decayFactor); // Nostalgia for the music
                            } else {
                                // Music fully decayed
                                pc.setStateData("music_playing", false);
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                } else {
                    pc.setStateData("music_playing", false);
                }
                return;
            }
        }
        
        // Music is playing - sustain emotions with PeakEMA pattern
        if (musicDuration % 100 == 0) { // Every 5 seconds
            String peakEmotion = pc.getStateData("music_peak_emotion", String.class);
            Float peakIntensity = pc.getStateData("music_peak_intensity", Float.class);
            
            if (peakEmotion != null && peakIntensity != null) {
                try {
                    PetComponent.Emotion emotion = PetComponent.Emotion.valueOf(peakEmotion);
                    
                    // Calculate wave pattern for music emotion (rising and falling)
                    float wavePhase = (float) Math.sin(musicDuration * 0.01); // Slow wave
                    float pulseIntensity = peakIntensity * (0.7f + 0.3f * wavePhase);
                    
                    // Apply sustained emotion with wave pattern
                    collector.pushEmotion(emotion, pulseIntensity);
                    
                    // Add complementary emotions based on music type
                    if ("LAGOM".equals(peakEmotion)) {
                        collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.10f);
                    } else if ("KEFI".equals(peakEmotion)) {
                        collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.15f * (0.5f + 0.5f * wavePhase));
                    } else if ("YUGEN".equals(peakEmotion)) {
                        collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.08f);
                    }
                    
                    // Dance behavior for certain pets
                    if (pet instanceof ParrotEntity && musicDuration % 200 == 0) {
                        collector.pushEmotion(PetComponent.Emotion.KEFI, 0.20f); // Dancing burst
                        EmotionContextCues.sendCue(owner, "music.parrot_dance." + pet.getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.music.parrot_dance", pet.getDisplayName()), 400);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
    
    private static void addSpeciesSpecificTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                                   EmotionStimulusBus.SimpleStimulusCollector collector) {
        // Species-specific emotional responses with cultural nuances
        if (pet instanceof WolfEntity) {
            // Dogs - loyal pack animals
            collector.pushEmotion(PetComponent.Emotion.LOYALTY, 0.30f);  // Strong loyalty trait
            collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.20f);  // Social bonding
            // Dogs are protective when owner is in danger
            if (owner.getHealth() / owner.getMaxHealth() < 0.5f) {
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.20f);
            }
        } else if (pet instanceof CatEntity cat) {
            // Cats - independent and mysterious
            // Check if cat is sitting (perching behavior)
            if (cat.isSitting()) {
                collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.25f);  // Graceful observation
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.15f);  // Content in their spot
            } else if (cat.getOwner() != null && cat.distanceTo(owner) > 10) {
                // Forced to follow when they'd rather be independent
                collector.pushEmotion(PetComponent.Emotion.ENNUI, 0.10f);  // Mild annoyance
            }
            // Cats are less affected by Regret for hunting
            // This is handled in combat emotions
        } else if (pet instanceof AbstractHorseEntity horse) {
            // Horses - love to roam and explore
            Vec3d velocity = horse.getVelocity();
            if (velocity.length() > 0.3) {  // Galloping
                collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.20f);  // Love of far places
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.15f);  // Joy of movement
            }
            // Horses feel cramped in small spaces
            if (world.getBlockState(horse.getBlockPos().up(3)).isSolidBlock(world, horse.getBlockPos().up(3))) {
                collector.pushEmotion(PetComponent.Emotion.RESTLESS, 0.15f);
            }
        } else if (pet instanceof AxolotlEntity || pet instanceof net.minecraft.entity.passive.TropicalFishEntity) {
            // Aquatic pets - need water
            if (pet.isInFluid()) {
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.25f);  // Perfect harmony in water
                collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.15f);
            } else {
                // Out of water - distress
                collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.30f);  // Longing for water
                collector.pushEmotion(PetComponent.Emotion.ANGST, 0.25f);  // Anxiety
            }
            // Check for arid biomes
            RegistryEntry<Biome> biome = world.getBiome(pet.getBlockPos());
            if (biome.isIn(DESERT_LIKE_BIOMES)) {
                collector.pushEmotion(PetComponent.Emotion.HIRAETH, 0.15f);  // Extra longing in desert
            }
        } else if (pet instanceof ParrotEntity) {
            // Parrots - social and musical
            collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.15f);
            // Check for other parrots nearby
            List<ParrotEntity> nearbyParrots = world.getEntitiesByClass(
                ParrotEntity.class, 
                pet.getBoundingBox().expand(10), 
                p -> p != pet && p.isAlive()
            );
            if (!nearbyParrots.isEmpty()) {
                collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.20f);  // Flock unity
            }
        } else if (pet instanceof FoxEntity) {
            // Foxes - clever and mischievous
            pc.pushEmotion(PetComponent.Emotion.CURIOUS, 0.20f);
            pc.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.15f);
            // Foxes are nocturnal
            if (world.isNight()) {
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.15f);  // More energetic at night
            }
        }
        
        // Apply nature volatility multiplier for species (would need species-specific config)
        // This affects how quickly emotions change for different species
    }
    
    private static void addLegacySpeciesHandlers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world, long now, BlockPos petPos) {
        // Legacy species handlers that were already in the code
        if (pet instanceof AbstractHorseEntity) {
            if (pet.hasPassenger(owner) && tryMarkPetBeat(pc, "horse_ride", now, 200)) {
                double speed = pet.getVelocity().horizontalLength();
                emitPetCue(owner, pc, "species.horse.ride." + pet.getUuidAsString(), (component, cueCollector) -> {
                    if (speed > 0.1d) {
                        float boost = (float) Math.min(0.12d, speed * 0.3d);
                        cueCollector.pushEmotion(PetComponent.Emotion.KEFI, boost);
                        cueCollector.pushEmotion(PetComponent.Emotion.FERNWEH, boost * 0.8f);
                    }
                }, null, 200, pet.getDisplayName(), owner.getDisplayName());
            }

            if (isBlockInRange(world, petPos, STABLE_FEED, 2, 1)
                && tryMarkPetBeat(pc, "horse_graze", now, 320)) {
                emitPetCue(owner, pc, "species.horse.graze." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof ParrotEntity parrot) {
            if (isJukeboxPlaying(world, petPos, 4) && tryMarkPetBeat(pc, "parrot_dance", now, 240)) {
                emitPetCue(owner, pc, "species.parrot.dance." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }

            if (!parrot.isOnGround() && !parrot.isTouchingWater()
                && pet.getVelocity().lengthSquared() > 0.08d
                && tryMarkPetBeat(pc, "parrot_flight", now, 200)) {
                double altitude = petPos.getY() - owner.getBlockPos().getY();
                emitPetCue(owner, pc, "species.parrot.flight." + pet.getUuidAsString(), (component, cueCollector) -> {
                    if (altitude > 1.0d) {
                        float lift = (float) Math.max(0.03d, Math.min(0.12d, altitude * 0.02d));
                        cueCollector.pushEmotion(PetComponent.Emotion.FERNWEH, lift);
                    }
                }, null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof CatEntity cat) {
            if (cat.isSitting()) {
                if (isOnBlock(world, petPos, BedBlock.class)
                    && world.isNight()
                    && tryMarkPetBeat(pc, "cat_bed", now, 400)) {
                    emitPetCue(owner, pc, "species.cat.bed." + pet.getUuidAsString(), (component, cueCollector) -> {},
                        null, 200, pet.getDisplayName());
                }

                if (isOnStorageBlock(world, petPos)
                    && tryMarkPetBeat(pc, "cat_chest", now, 360)) {
                    emitPetCue(owner, pc, "species.cat.chest." + pet.getUuidAsString(), (component, cueCollector) -> {},
                        null, 200, pet.getDisplayName());
                }
            }
        }

        if (pet instanceof OcelotEntity) {
            if (pet.isSneaking() && tryMarkPetBeat(pc, "ocelot_stalk", now, 200)) {
                emitPetCue(owner, pc, "species.ocelot.stalk." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }

            if (world.getBiome(petPos).isIn(BiomeTags.IS_JUNGLE)
                && tryMarkPetBeat(pc, "ocelot_jungle", now, 400)) {
                emitPetCue(owner, pc, "species.ocelot.jungle." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof FoxEntity fox) {
            if (fox.isSleeping() && tryMarkPetBeat(pc, "fox_sleep", now, 320)) {
                emitPetCue(owner, pc, "species.fox.sleep." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }

            if ((!fox.getMainHandStack().isEmpty() || !fox.getOffHandStack().isEmpty())
                && tryMarkPetBeat(pc, "fox_treasure", now, 200)) {
                emitPetCue(owner, pc, "species.fox.treasure." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }
        }

        if (pet instanceof WolfEntity) {
            if ((owner.getMainHandStack().isOf(Items.BONE) || owner.getOffHandStack().isOf(Items.BONE))
                && tryMarkPetBeat(pc, "wolf_treat", now, 160)) {
                emitPetCue(owner, pc, "species.wolf.treat." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName(), owner.getDisplayName());
            }

            if (isBlockInRange(world, petPos, BlockTags.CAMPFIRES, 3, 1)
                && tryMarkPetBeat(pc, "wolf_campfire", now, 320)) {
                emitPetCue(owner, pc, "species.wolf.campfire." + pet.getUuidAsString(), (component, cueCollector) -> {},
                    null, 200, pet.getDisplayName());
            }

            if (world.isNight() && world.getMoonSize() > 0.75f
                && tryMarkPetBeat(pc, "wolf_howl", now, 600)) {
                emitPetCue(owner, pc, "species.wolf.howl." + pet.getUuidAsString(), (component, cueCollector) -> {},
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
        return isJukeboxPlayingOptimized(world, center, radius, world.getTime());
    }

    private static boolean isJukeboxPlayingOptimized(ServerWorld world, BlockPos center, int radius, long currentTick) {
        // First, try cache lookup for quick response
        Map<BlockPos, Long> worldJukeboxes = activeJukeboxes.get(world);
        if (worldJukeboxes != null) {
            for (Map.Entry<BlockPos, Long> entry : worldJukeboxes.entrySet()) {
                BlockPos jukeboxPos = entry.getKey();
                // Check if jukebox is within range and still active
                if (center.isWithinDistance(jukeboxPos, radius)) {
                    // Verify it's still playing (cache might be stale)
                    BlockState state = world.getBlockState(jukeboxPos);
                    if (state.getBlock() instanceof JukeboxBlock && state.get(JukeboxBlock.HAS_RECORD)) {
                        return true;
                    } else {
                        // Remove stale entry
                        worldJukeboxes.remove(jukeboxPos);
                    }
                }
            }
        }

        // Check if we need to refresh the cache
        Long lastScan = lastJukeboxScan.get(world);
        if (lastScan == null || currentTick - lastScan > JUKEBOX_CACHE_DURATION) {
            refreshJukeboxCache(world, center, Math.max(radius, OPTIMIZED_MUSIC_RADIUS), currentTick);

            // Try cache lookup again after refresh
            worldJukeboxes = activeJukeboxes.get(world);
            if (worldJukeboxes != null) {
                for (BlockPos jukeboxPos : worldJukeboxes.keySet()) {
                    if (center.isWithinDistance(jukeboxPos, radius)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    static void resetSharedMoodBaselinesForTest() {
        EmotionBaselineTracker.resetForTest();
    }

    private static EnumMap<PetComponent.Mood, Float> sharedMoodBaseline(PetComponent component) {
        return EmotionBaselineTracker.copyBaseline(component);
    }

    private static void seedSharedMoodBaseline(PetComponent component) {
        EmotionBaselineTracker.ensureBaseline(component);
    }

    private static void updateSharedMoodBaseline(PetComponent component, Map<PetComponent.Mood, Float> snapshot) {
        EmotionBaselineTracker.updateBaseline(component, snapshot);
    }

    private static final class EmotionAccumulatorBatch implements SocialContextSnapshot.EmotionDispatcher {
        private final Map<PetComponent, EnumMap<PetComponent.Emotion, Float>> pending = new IdentityHashMap<>();
        private final AsyncWorkCoordinator coordinator;
        private final ServerPlayerEntity owner;
        private final LongSupplier timeSupplier;
        private final boolean captureSummary;
        private final MinecraftServer serverHint;

        EmotionAccumulatorBatch(AsyncWorkCoordinator coordinator,
                                ServerWorld world,
                                @Nullable ServerPlayerEntity owner) {
            this(coordinator, owner,
                world != null ? world::getTime : () -> 0L,
                owner != null && world != null,
                world != null ? world.getServer() : owner != null ? owner.getServer() : null);
        }

        EmotionAccumulatorBatch(AsyncWorkCoordinator coordinator,
                                @Nullable ServerPlayerEntity owner,
                                LongSupplier timeSupplier,
                                boolean captureSummary) {
            this(coordinator, owner, timeSupplier, captureSummary,
                owner != null ? owner.getServer() : null);
        }

        private EmotionAccumulatorBatch(AsyncWorkCoordinator coordinator,
                                        @Nullable ServerPlayerEntity owner,
                                        LongSupplier timeSupplier,
                                        boolean captureSummary,
                                        @Nullable MinecraftServer serverHint) {
            this.coordinator = coordinator;
            this.owner = owner;
            this.timeSupplier = timeSupplier != null ? timeSupplier : () -> 0L;
            this.captureSummary = captureSummary;
            this.serverHint = serverHint;
        }

        @Override
        public void push(PetComponent component, PetComponent.Emotion emotion, float amount) {
            if (component == null || emotion == null || amount == 0f) {
                return;
            }
            pending.computeIfAbsent(component, ignored -> new EnumMap<>(PetComponent.Emotion.class))
                .merge(emotion, amount, Float::sum);
        }

        @Override
        public EmotionStimulusBus.SimpleStimulusCollector collectorFor(PetComponent component) {
            return (emotion, amount) -> push(component, emotion, amount);
        }

        CompletableFuture<Void> flush() {
            if (pending.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
            StimulusSummary.Builder summaryBuilder = null;
            if (captureSummary) {
                summaryBuilder = StimulusSummary.builder(timeSupplier.getAsLong());
            }

            List<CompletableFuture<Void>> completions = new ArrayList<>();
            MinecraftServer serverForSummary = this.serverHint;

            for (Map.Entry<PetComponent, EnumMap<PetComponent.Emotion, Float>> entry : pending.entrySet()) {
                PetComponent component = entry.getKey();
                EnumMap<PetComponent.Emotion, Float> deltas = entry.getValue();
                if (component == null || deltas == null || deltas.isEmpty()) {
                    continue;
                }
                MobEntity pet = component.getPet();
                if (pet == null) {
                    continue;
                }

                MinecraftServer captureServer = serverForSummary;
                if (pet.getWorld() instanceof ServerWorld serverWorld) {
                    MinecraftServer worldServer = serverWorld.getServer();
                    if (worldServer != null) {
                        captureServer = worldServer;
                        serverForSummary = worldServer;
                    }
                }

                try {
                    if (summaryBuilder != null) {
                        seedSharedMoodBaseline(component);
                    }
                    CompletableFuture<Void> dispatch = scheduleStimulusDispatch(bus, coordinator, pet, component,
                        captureServer, summaryBuilder, deltas);
                    completions.add(dispatch);
                } catch (Throwable ex) {
                    throwUnchecked(ex);
                }
            }

            pending.clear();

            CompletableFuture<Void> dispatchFuture;
            if (completions.isEmpty()) {
                dispatchFuture = CompletableFuture.completedFuture(null);
            } else {
                dispatchFuture = CompletableFuture.allOf(
                    completions.toArray(new CompletableFuture[0])
                );
            }

            CompletableFuture<Void> errorAwareDispatch = dispatchFuture.whenComplete((ignored, throwable) -> {
                if (throwable == null) {
                    return;
                }
                Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
                throwUnchecked(cause);
            });

            if (summaryBuilder != null) {
                StimulusSummary.Builder builder = summaryBuilder;
                ServerPlayerEntity cueOwner = owner;
                MinecraftServer summaryServer = serverForSummary;
                if (summaryServer == null && cueOwner != null) {
                    summaryServer = cueOwner.getServer();
                }
                final MinecraftServer finalSummaryServer = summaryServer;
                Runnable recordCue = () -> {
                    StimulusSummary summary = builder.build();
                    if (!summary.isEmpty()) {
                        EmotionContextCues.recordStimulus(cueOwner, summary);
                    }
                };

                CompletableFuture<Void> summaryFuture = errorAwareDispatch.thenCompose(ignored ->
                    submitToServerAsync(finalSummaryServer, recordCue)
                );
                summaryFuture.whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        throwUnchecked(throwable);
                    }
                });
                return summaryFuture;
            }
            return errorAwareDispatch;
        }

        private CompletableFuture<Void> scheduleStimulusDispatch(EmotionStimulusBus bus,
                                                                  AsyncWorkCoordinator coordinator,
                                                                  MobEntity pet,
                                                                  PetComponent component,
                                                                  @Nullable MinecraftServer server,
                                                                  @Nullable StimulusSummary.Builder summaryBuilder,
                                                                  EnumMap<PetComponent.Emotion, Float> deltas) {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            CompletableFuture<Void> scheduling = submitToServerAsync(server, () -> {
                bus.queueSimpleStimulus(pet, collector -> {
                    for (Map.Entry<PetComponent.Emotion, Float> delta : deltas.entrySet()) {
                        collector.pushEmotion(delta.getKey(), delta.getValue());
                    }
                });

                CompletableFuture<Void> dispatchFuture = bus.dispatchStimuliAsync(pet, coordinator);
                if (dispatchFuture == null) {
                    try {
                        if (summaryBuilder != null) {
                            Map<PetComponent.Mood, Float> before = sharedMoodBaseline(component);
                            Map<PetComponent.Mood, Float> after = snapshotMoodBlend(component);
                            summaryBuilder.addSample(before, after);
                            updateSharedMoodBaseline(component, after);
                        }
                        completion.complete(null);
                    } catch (Throwable throwable) {
                        completion.completeExceptionally(throwable);
                    }
                    return;
                }

                CompletableFuture<Void> afterFuture;
                if (summaryBuilder != null) {
                    PetComponent componentCapture = component;
                    StimulusSummary.Builder builderCapture = summaryBuilder;
                    afterFuture = dispatchFuture.thenCompose(ignored ->
                        submitToServerAsync(server, () -> {
                            Map<PetComponent.Mood, Float> before = sharedMoodBaseline(componentCapture);
                            Map<PetComponent.Mood, Float> after = snapshotMoodBlend(componentCapture);
                            builderCapture.addSample(before, after);
                            updateSharedMoodBaseline(componentCapture, after);
                        })
                    );
                } else {
                    afterFuture = dispatchFuture;
                }

                afterFuture.whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        completion.completeExceptionally(unwrapCompletion(throwable));
                    } else {
                        completion.complete(null);
                    }
                });
            });

            scheduling.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    completion.completeExceptionally(unwrapCompletion(throwable));
                }
            });

            return completion;
        }

        private static Throwable unwrapCompletion(Throwable throwable) {
            if (throwable instanceof CompletionException completion && completion.getCause() != null) {
                return completion.getCause();
            }
            return throwable;
        }

        private static CompletableFuture<Void> submitToServerAsync(@Nullable MinecraftServer server, Runnable task) {
            if (task == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (server != null) {
                CompletableFuture<?> submitted = server.submit(task);
                if (submitted == null) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> future = new CompletableFuture<>();
                submitted.whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(null);
                    }
                });
                return future;
            }
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable throwable) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
        }

        private static void throwUnchecked(Throwable throwable) {
            if (throwable instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        }
    }

    private static void refreshJukeboxCache(ServerWorld world, BlockPos scanCenter, int scanRadius, long currentTick) {
        Map<BlockPos, Long> worldJukeboxes = activeJukeboxes.computeIfAbsent(world, k -> new ConcurrentHashMap<>());

        // Clear old entries first
        worldJukeboxes.clear();

        // Scan for active jukeboxes in area (limited radius)
        for (BlockPos pos : BlockPos.iterate(
                scanCenter.add(-scanRadius, -2, -scanRadius),
                scanCenter.add(scanRadius, 2, scanRadius))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof JukeboxBlock && state.get(JukeboxBlock.HAS_RECORD)) {
                worldJukeboxes.put(pos.toImmutable(), currentTick);
            }
        }

        lastJukeboxScan.put(world, currentTick);
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

            List<PendingCue> pendingCues = new ArrayList<>();
            CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 24, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.18f); // Wonder at magic
                collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.08f); // Slight wariness of unknown
                collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.06f); // Beauty of magical sparkles
                if (pc.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
                    MobEntity petEntity = pc.getPet();
                    if (petEntity != null) {
                        String cueId = "role.enchanter.attune." + petEntity.getUuidAsString();
                        Text cueText = Text.translatable("petsplus.emotion_cue.role.enchanter_attune", petEntity.getDisplayName());
                        pendingCues.add(new PendingCue(cueId, () -> cueText, 200));
                    }
                }
            });
            for (PendingCue pending : pendingCues) {
                sendCueAfterStimulus(sp, stimulusFuture, pending.cueId(), pending.textSupplier(), pending.cooldown());
            }
            sendCueAfterStimulus(sp, stimulusFuture,
                "block_use.enchanting",
                () -> Text.translatable("petsplus.emotion_cue.block_use.enchanting"),
                200);
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
            CompletableFuture<StimulusSummary> stimulusFuture = pushToNearbyOwnedPets(sp, 20, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.12f); // Social interaction comfort
                collector.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.08f); // Observing human customs

                // Professional-specific reactions
                if (isFoodTrader) {
                    collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f); // Food security feelings
                } else if (isMystic) {
                    collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.08f); // Mystical presence
                } else if (isGuard) {
                    collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f); // Defense associations
                }
            });
            sendCueAfterStimulus(sp, stimulusFuture,
                "entity.trade",
                () -> Text.translatable("petsplus.emotion_cue.entity.trade"),
                200);
            if (isFoodTrader) {
                sendCueAfterStimulus(sp, stimulusFuture,
                    "entity.trade.food",
                    () -> Text.translatable("petsplus.emotion_cue.entity.trade_food"),
                    400);
            } else if (isMystic) {
                sendCueAfterStimulus(sp, stimulusFuture,
                    "entity.trade.mystic",
                    () -> Text.translatable("petsplus.emotion_cue.entity.trade_mystic"),
                    400);
            } else if (isGuard) {
                sendCueAfterStimulus(sp, stimulusFuture,
                    "entity.trade.guard",
                    () -> Text.translatable("petsplus.emotion_cue.entity.trade_guard"),
                    400);
            }
            
            NatureFlavorHandler.triggerForOwner(sp, 20, Trigger.VILLAGER_TRADE);
        }

        return ActionResult.PASS;
    }

    public static void onVillagerTradeCompleted(ServerPlayerEntity player, VillagerEntity villager, TradeOffer tradeOffer) {
        if (player.getWorld().isClient()) {
            return;
        }

        RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
        boolean isFoodTrader = profession.matchesKey(VillagerProfession.BUTCHER)
            || profession.matchesKey(VillagerProfession.FARMER);
        boolean isMystic = profession.matchesKey(VillagerProfession.CLERIC);
        boolean isGuard = profession.matchesKey(VillagerProfession.WEAPONSMITH)
            || profession.matchesKey(VillagerProfession.ARMORER);

        ItemStack sellStack = tradeOffer.getSellItem();
        boolean sellsCombatGear = sellStack.isIn(ItemTags.TRIMMABLE_ARMOR)
            || sellStack.isIn(ItemTags.SWORDS)
            || sellStack.isIn(ItemTags.AXES)
            || sellStack.isOf(Items.SHIELD)
            || sellStack.isOf(Items.BOW)
            || sellStack.isOf(Items.CROSSBOW);

        long topicId = GossipTopics.TRADE_GENERIC;
        Text gossipText = Text.translatable("petsplus.gossip.life.trade.generic");
        float intensity = 0.35f;
        float confidence = 0.42f;

        if (isFoodTrader || sellStack.contains(DataComponentTypes.FOOD)) {
            topicId = GossipTopics.TRADE_FOOD;
            gossipText = Text.translatable("petsplus.gossip.life.trade.food");
            intensity = 0.38f;
        } else if (isMystic) {
            topicId = GossipTopics.TRADE_MYSTIC;
            gossipText = Text.translatable("petsplus.gossip.life.trade.mystic");
            confidence = 0.46f;
        } else if (isGuard || sellsCombatGear) {
            topicId = GossipTopics.TRADE_GUARD;
            gossipText = Text.translatable("petsplus.gossip.life.trade.guard");
            intensity = 0.4f;
        }

        shareOwnerRumor(player, 32, topicId, intensity, confidence, gossipText);
    }

    /**
     * Advanced weather and item awareness using tags and component data
     */
    private static void onWorldTickAdvancedTriggers(ServerWorld world) {
        // Run every 5 seconds to keep it lightweight
        if (world.getTime() % 100 != 0) return;

        StateManager stateManager = StateManager.forWorld(world);
        PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
        for (ServerPlayerEntity player : world.getPlayers()) {
            Vec3d center = player.getPos();
            List<PetSwarmIndex.SwarmEntry> pets = new ArrayList<>();
            swarmIndex.forEachPetInRange(player, center, 32.0, pets::add);

            AsyncWorkCoordinator coordinator = stateManager.getAsyncWorkCoordinator();
            EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
            for (PetSwarmIndex.SwarmEntry entry : pets) {
                MobEntity pet = entry.pet();
                PetComponent pc = entry.component();
                if (pet == null || pc == null) continue;

                bus.queueSimpleStimulus(pet, collector -> {
                    // Enhanced weather awareness
                    addAdvancedWeatherTriggers(pet, pc, player, world, collector);

                    // Tag-based item awareness
                    addTagBasedItemTriggers(pet, pc, player, collector);
                });
                bus.dispatchStimuli(pet, coordinator);
            }
        }
    }

    private static void addAdvancedWeatherTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world,
                                                  EmotionStimulusBus.SimpleStimulusCollector collector) {
        boolean isCat = pet instanceof CatEntity;
        boolean isWolf = pet instanceof WolfEntity;
        BlockPos petPos = pet.getBlockPos();
        boolean exposedToSky = world.isSkyVisible(petPos);
        boolean soaked = pet.isTouchingWater() || ((world.isRaining() || world.isThundering()) && exposedToSky);
        boolean shelteredAndDry = !exposedToSky && !soaked;
        long now = world.getTime();

        if (world.isThundering()) {
            float exposure = (exposedToSky ? 1.0f : 0.5f) + (soaked ? 0.3f : 0f);
            collector.pushEmotion(PetComponent.Emotion.STARTLE, 0.05f + 0.05f * exposure);
            collector.pushEmotion(PetComponent.Emotion.ANGST, 0.04f + 0.04f * exposure);
            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, shelteredAndDry ? 0.04f : 0.05f + 0.04f * exposure);

            if (!shelteredAndDry) {
                EmotionContextCues.sendCue(owner, "weather.thunder.pet." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.thunder_pet", pet.getDisplayName()), 400);
            } else if (tryMarkPetBeat(pc, "weather_shelter", now, 200L)) {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.04f);
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f);
                EmotionContextCues.sendCue(owner, "weather.shelter." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.shelter", pet.getDisplayName()), 400);
            }
        } else if (world.isRaining()) {
            if (shelteredAndDry) {
                if (tryMarkPetBeat(pc, "weather_shelter", now, 200L)) {
                    collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.03f);
                    collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.02f);
                    EmotionContextCues.sendCue(owner, "weather.shelter." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.weather.shelter", pet.getDisplayName()), 400);
                }
            } else if (isCat) {
                float discomfort = exposedToSky && soaked ? 0.10f : 0.08f;
                collector.pushEmotion(PetComponent.Emotion.DISGUST, discomfort);
                collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.06f + (soaked ? 0.02f : 0f));
                EmotionContextCues.sendCue(owner, "weather.rain.cat." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_cat", pet.getDisplayName()), 400);
            } else if (isWolf) {
                float delight = exposedToSky || soaked ? 0.05f : 0.03f;
                collector.pushEmotion(PetComponent.Emotion.KEFI, delight);
                collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f + (soaked ? 0.02f : 0f));
                EmotionContextCues.sendCue(owner, "weather.rain.dog." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_dog", pet.getDisplayName()), 400);
            } else {
                float refresh = exposedToSky || soaked ? 0.03f : 0.015f;
                collector.pushEmotion(PetComponent.Emotion.LAGOM, refresh);
                if (exposedToSky || soaked) {
                    EmotionContextCues.sendCue(owner, "weather.rain.pet." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.weather.rain_pet", pet.getDisplayName()), 400);
                }
            }
        } else {
            if (shouldCelebrateClearWeather(pet, world)) {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.06f);
                collector.pushEmotion(PetComponent.Emotion.KEFI, 0.04f); // Energy from clear skies
                EmotionContextCues.sendCue(owner, "weather.clear." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.clear", pet.getDisplayName()), 6000);
            }
        }

        // Temperature simulation based on biome data
        try {
            var biome = world.getBiome(pet.getBlockPos());
            float temperature = biome.value().getTemperature();

            if (temperature > 1.0f) { // Hot biomes
                collector.pushEmotion(PetComponent.Emotion.LAGOM, -0.02f); // Slight discomfort
                collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.03f); // Seeking shade
                EmotionContextCues.sendCue(owner, "environment.hot." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.environment.hot", pet.getDisplayName()), 600);
            } else if (temperature < 0.0f) { // Cold biomes
                collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.04f); // Seeking warmth/owner
                collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f); // Cozy feelings
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

    private static void addTagBasedItemTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner,
                                                EmotionStimulusBus.SimpleStimulusCollector collector) {
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
            collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.04f); // Security from wealth
            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.06f); // Guarding valuable things
            EmotionContextCues.sendCue(owner, "inventory.valuables",
                Text.translatable("petsplus.emotion_cue.inventory.valuables"), 1200);
            NatureFlavorHandler.triggerForPet(pet, pc, (ServerWorld) owner.getWorld(), owner,
                Trigger.INVENTORY_VALUABLE, now);
        }

        if (hasFoodItems) {
            collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.05f); // Food security
            collector.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f); // Comfort from sustenance
            EmotionContextCues.sendCue(owner, "inventory.food",
                Text.translatable("petsplus.emotion_cue.inventory.food"), 1200);
        }

        if (hasMagicalItems) {
            collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.06f); // Wonder at magical things
            collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.02f); // Slight wariness
            EmotionContextCues.sendCue(owner, "inventory.magic",
                Text.translatable("petsplus.emotion_cue.inventory.magic"), 1200);
        }

        if (hasArchaeology) {
            NatureFlavorHandler.triggerForPet(pet, pc, (ServerWorld) owner.getWorld(), owner,
                Trigger.INVENTORY_RELIC, now);
        }

        if (hasWeapons) {
            collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.05f); // Ready for danger
            collector.pushEmotion(PetComponent.Emotion.STOIC, 0.03f); // Determination
            EmotionContextCues.sendCue(owner, "inventory.weapons",
                Text.translatable("petsplus.emotion_cue.inventory.weapons"), 1200);
        }

        if (hasTools) {
            collector.pushEmotion(PetComponent.Emotion.KEFI, 0.03f); // Energy from productivity
            collector.pushEmotion(PetComponent.Emotion.LAGOM, 0.04f); // Balance from useful work
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
            pushToNearbyOwnedPets(player, 64, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f);
                collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f);
            }, false);
            shareOwnerRumor(player, 64, GossipTopics.RETURN_FROM_DIMENSION, 0.5f, 0.55f,
                Text.translatable("petsplus.gossip.exploration.dimension.return"));
        } else if (current == World.NETHER) {
            pushToNearbyOwnedPets(player, 64, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f);
                collector.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                collector.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
            }, false);
            shareOwnerRumor(player, 64, GossipTopics.ENTER_NETHER, 0.6f, 0.5f,
                Text.translatable("petsplus.gossip.exploration.dimension.nether"));
        } else if (current == World.END) {
            pushToNearbyOwnedPets(player, 64, (pc, collector) -> {
                collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.14f);
                collector.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                collector.pushEmotion(PetComponent.Emotion.FOREBODING, 0.08f);
            }, false);
            shareOwnerRumor(player, 64, GossipTopics.ENTER_END, 0.65f, 0.55f,
                Text.translatable("petsplus.gossip.exploration.dimension.end"));
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

