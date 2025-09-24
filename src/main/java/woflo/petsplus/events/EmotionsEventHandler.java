package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.JukeboxBlock;
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
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.BlockTags;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

import woflo.petsplus.events.EmotionCueConfig.EmotionCueDefinition;

/**
 * Central, low-cost, event-driven emotion hooks.
 * Ties pet emotions to existing gameplay events (no per-tick polling).
 */
public final class EmotionsEventHandler {

    private EmotionsEventHandler() {}

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

        // After player respawn: relief and resilience
        ServerPlayerEvents.AFTER_RESPAWN.register(EmotionsEventHandler::onAfterRespawn);

        // Owner death: grief/longing on pets
        ServerLivingEntityEvents.AFTER_DEATH.register(EmotionsEventHandler::onAfterDeath);

        // 5th Wave: Tag-based and data-driven emotion hooks
        // Enchanting events (via block use on enchanting table)
        UseBlockCallback.EVENT.register(EmotionsEventHandler::onEnchantingTableUse);

        // Trading events (via villager interaction)
        UseEntityCallback.EVENT.register(EmotionsEventHandler::onVillagerTrade);

        // Lightweight weather transition tracker (per world)
        ServerTickEvents.END_WORLD_TICK.register(EmotionsEventHandler::onWorldTickWeatherAndEnv);

        // 4th Wave: Nuanced living system triggers
        ServerTickEvents.END_WORLD_TICK.register(EmotionsEventHandler::onWorldTickNuancedEmotions);

        // 5th Wave: Enhanced weather and item awareness
        ServerTickEvents.END_WORLD_TICK.register(EmotionsEventHandler::onWorldTickAdvancedTriggers);

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
    }

    // (Block place detour handled in onUseBlock based on held BlockItem)

    // ==== Kill Events → PASSIONATE, PLAYFUL, HAPPY, FOCUSED ====
    private static void onAfterKilledOther(ServerWorld world, Entity killer, LivingEntity killed) {
        if (killer instanceof ServerPlayerEntity sp) {
            float healthPct = Math.max(0f, sp.getHealth() / sp.getMaxHealth());
            float reliefBonus = healthPct < 0.35f ? 0.2f : 0f;
            applyConfiguredStimulus(sp, "combat.owner_kill", 32, pc -> {
                if (reliefBonus > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, reliefBonus);
                }
            });

            if (healthPct < 0.35f) {
                EmotionContextCues.sendCue(sp, "combat.owner_kill",
                    Text.translatable("petsplus.emotion_cue.combat.owner_kill_close"), 200);
            } else {
                EmotionContextCues.sendCue(sp, "combat.owner_kill",
                    resolveCueText("combat.owner_kill", "petsplus.emotion_cue.combat.owner_kill"), 200);
            }
        }

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

        return ActionResult.PASS;
    }

    // ==== Block Use → Homey stations, beds, jukebox ====
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        BlockPos pos = hitResult.getBlockPos();
        var state = world.getBlockState(pos);
        EmotionCueConfig config = EmotionCueConfig.get();

        String useDefinition = config.findBlockUseDefinition(state);
        if (useDefinition != null) {
            triggerConfiguredCue(sp, useDefinition, config.fallbackRadius(), null);
        }

        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof BlockItem blockItem) {
            String placeDefinition = config.findBlockPlaceDefinition(blockItem.getBlock().getDefaultState());
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

    // ==== Respawn → RELIEF + STOIC/GAMAN ====
    private static void onAfterRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) return;
        EmotionContextCues.clear(oldPlayer);
        EmotionContextCues.clear(newPlayer);
        pushToNearbyOwnedPets(newPlayer, 32, pc -> {
            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.35f);
            pc.pushEmotion(PetComponent.Emotion.STOIC, 0.28f);
            pc.pushEmotion(PetComponent.Emotion.GAMAN, 0.12f);
        });
        EmotionContextCues.sendCue(newPlayer, "player.respawn", Text.translatable("petsplus.emotion_cue.player.respawn"), 200);
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
                pc.pushEmotion(PetComponent.Emotion.SAUDADE, 0.35f);
                pc.pushEmotion(PetComponent.Emotion.HIRAETH, 0.25f);
                pc.pushEmotion(PetComponent.Emotion.REGRET, 0.18f);
                pc.updateMood();
            }
        });
    }

    // ==== Weather transitions (ultra-light) → SOBREMESA, YUGEN, FOREBODING, RELIEF ====
    private static final java.util.Map<ServerWorld, WeatherState> WEATHER = new java.util.WeakHashMap<>();
    private static final Map<ServerWorld, Long> LAST_WET_WEATHER_TICK = new WeakHashMap<>();
    private static final Map<UUID, Long> LAST_CLEAR_WEATHER_TRIGGER = new HashMap<>();
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
    private static class PlayerEnvState {
        RegistryKey<Biome> biomeKey;
        RegistryKey<World> dimensionKey;
        Vec3d lastPos;
        int idleTicks;
        PlayerEnvState(@Nullable RegistryKey<Biome> biomeKey, @Nullable RegistryKey<World> dimensionKey, Vec3d lastPos) {
            this.biomeKey = biomeKey;
            this.dimensionKey = dimensionKey;
            this.lastPos = lastPos;
            this.idleTicks = 0;
        }
    }

    private static void onWorldTickWeatherAndEnv(ServerWorld world) {
        EmotionContextCues.tick(world);
        long now = world.getTime();
        WeatherState prev = WEATHER.get(world);
        boolean r = world.isRaining();
        boolean t = world.isThundering();
        if (r || t) {
            LAST_WET_WEATHER_TICK.put(world, now);
        }
        if (prev == null) {
            WEATHER.put(world, new WeatherState(r, t));
            // Initialize time phase too
            TIME_PHASES.put(world, computeTimePhase(world.getTimeOfDay()));
        } else {
            if (r != prev.raining || t != prev.thundering) {
                WEATHER.put(world, new WeatherState(r, t));
                // Push to pets near players in this world
                if (!r && !t && (prev.raining || prev.thundering)) {
                    LAST_WET_WEATHER_TICK.put(world, Math.max(0L, now - 1L));
                }
                for (ServerPlayerEntity sp : world.getPlayers()) {
                    if (r && !prev.raining) {
                        // Rain started → cozy introspection
                        pushToNearbyOwnedPets(sp, 48, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.08f);
                            pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.06f);
                            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.04f);
                        });
                        EmotionContextCues.sendCue(sp, "weather.rain_start", Text.translatable("petsplus.emotion_cue.weather.rain_start"), 600);
                    } else if (!r && prev.raining) {
                        // Rain ended → relief/joy
                        pushToNearbyOwnedPets(sp, 48, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.10f);
                            pc.pushEmotion(PetComponent.Emotion.GLEE, 0.06f);
                        });
                        EmotionContextCues.sendCue(sp, "weather.rain_end", Text.translatable("petsplus.emotion_cue.weather.rain_end"), 600);
                    }
                    if (t && !prev.thundering) {
                        // Thunder started → foreboding and protectiveness
                        pushToNearbyOwnedPets(sp, 48, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.12f);
                            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.06f);
                        });
                        EmotionContextCues.sendCue(sp, "weather.thunder", Text.translatable("petsplus.emotion_cue.weather.thunder"), 600);
                    }
                }
            }
        }

        // Time of day transitions (very cheap)
        TimePhase current = TIME_PHASES.getOrDefault(world, computeTimePhase(world.getTimeOfDay()));
        TimePhase next = computeTimePhase(world.getTimeOfDay());
        if (next != current) {
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
                        EmotionContextCues.sendCue(sp, "time.day", Text.translatable("petsplus.emotion_cue.time.day"), 2400);
                    }
                    case NIGHT -> {
                        pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.04f));
                        EmotionContextCues.sendCue(sp, "time.night", Text.translatable("petsplus.emotion_cue.time.night"), 2400);
                    }
                }
            }
        }

        // Per-player lightweight environment checks (biome/dimension/idle/hunger)
        long time = world.getTime();
        for (ServerPlayerEntity sp : world.getPlayers()) {
            // Resolve biome identifier
            RegistryEntry<Biome> biomeEntry = null;
            RegistryKey<Biome> biomeKey = null;
            try {
                biomeEntry = world.getBiome(sp.getBlockPos());
                biomeKey = biomeEntry.getKey().orElse(null);
            } catch (Throwable ignored) {}
            RegistryKey<World> worldKey = world.getRegistryKey();

            PlayerEnvState st = PLAYER_ENV.get(sp);
            Vec3d pos = sp.getPos();
            if (st == null) {
                st = new PlayerEnvState(biomeKey, worldKey, pos);
                PLAYER_ENV.put(sp, st);
            }

            // Biome change
            if (biomeKey != null && (st.biomeKey == null || !biomeKey.equals(st.biomeKey))) {
                st.biomeKey = biomeKey;
                if (biomeEntry != null) {
                    if (biomeEntry.isIn(DESERT_LIKE_BIOMES)) {
                        pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.HANYAUKU, 0.12f), false);
                    }
                    if (biomeKey.equals(BiomeKeys.CHERRY_GROVE) || biomeKey.equals(BiomeKeys.FLOWER_FOREST)) {
                        pushToNearbyOwnedPets(sp, 32, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.10f);
                            pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.08f);
                        }, false);
                    }
                    if (biomeKey.equals(BiomeKeys.MUSHROOM_FIELDS)) {
                        pushToNearbyOwnedPets(sp, 48, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.GLEE, 0.15f);
                            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.08f);
                        }, false);
                    }
                    if (biomeKey.equals(BiomeKeys.DEEP_DARK)) {
                        pushToNearbyOwnedPets(sp, 48, pc -> {
                            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.18f);
                            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.12f);
                        }, false);
                    }
                    if (biomeEntry.isIn(OCEANIC_BIOMES)) {
                        pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.08f), false);
                    }
                    if (biomeEntry.isIn(SNOWY_BIOMES)) {
                        pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.STOIC, 0.10f), false);
                    }
                }
                Identifier newBiomeId = biomeKey.getValue();
                Text biomeName = Text.translatable("biome." + newBiomeId.getNamespace() + "." + newBiomeId.getPath());
                pushToNearbyOwnedPets(sp, 48, pc -> {
                    if (pc.hasRole(PetRoleType.SCOUT)) {
                        MobEntity scoutPet = pc.getPet();
                        if (scoutPet != null) {
                            EmotionContextCues.sendCue(sp,
                                "role.scout.biome." + scoutPet.getUuidAsString(),
                                Text.translatable("petsplus.emotion_cue.role.scout_biome",
                                    scoutPet.getDisplayName(), biomeName),
                                600);
                        }
                    }
                });
            }

            // Dimension change
            if (worldKey != null && (st.dimensionKey == null || !worldKey.equals(st.dimensionKey))) {
                RegistryKey<World> previous = st.dimensionKey;
                st.dimensionKey = worldKey;
                EmotionContextCues.clearForDimensionChange(sp);
                if (worldKey == World.OVERWORLD && previous != null && previous != World.OVERWORLD) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f);
                    }, false);
                } else if (worldKey == World.NETHER) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f);
                        pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                    }, false);
                } else if (worldKey == World.END) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.14f);
                        pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.08f);
                    }, false);
                }
            }

            // Idle/AFK tracking for ENNUI (push after ~3 minutes, then every 2 minutes with activity detection)
            double dx = pos.x - st.lastPos.x;
            double dz = pos.z - st.lastPos.z;
            if ((dx * dx + dz * dz) < 0.0025) { // ~0.05 blocks
                st.idleTicks++;
                // Check for nearby activity (player building/crafting)
                boolean nearActivity = hasNearbyActivity(sp, world);
                if (!nearActivity) {
                    if (st.idleTicks == 3600 || (st.idleTicks > 3600 && st.idleTicks % 2400 == 0)) { // 3min, then every 2min
                        pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.ENNUI, 0.15f));
                        EmotionContextCues.sendCue(sp, "idle.ennui", Text.translatable("petsplus.emotion_cue.idle.ennui"), 2400);
                    }
                    // Long rainy nights weariness (reduced frequency and weight)
                    if (world.isRaining() && TIME_PHASES.getOrDefault(world, TimePhase.NIGHT) == TimePhase.NIGHT && st.idleTicks % 1200 == 0 && st.idleTicks >= 2400) {
                        pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.ENNUI, 0.06f));
                        EmotionContextCues.sendCue(sp, "idle.rainy_ennui", Text.translatable("petsplus.emotion_cue.idle.rainy"), 2400);
                    }
                }
            } else {
                st.idleTicks = 0;
                st.lastPos = pos;
            }

            // Low hunger empathy: UBUNTU + light PROTECTIVENESS (every 30s)
            try {
                if (sp.getHungerManager().getFoodLevel() <= 4 && (time % 600 == 0)) {
                    pushToNearbyOwnedPets(sp, 32, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.UBUNTU, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                    });
                    EmotionContextCues.sendCue(sp, "owner.low_hunger", Text.translatable("petsplus.emotion_cue.owner.low_hunger"), 600);
                }
            } catch (Throwable ignored) {}
        }
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
                Map<PetComponent.Mood, Float> before = snapshotMoodBlend(pc);
                consumer.accept(pc);
                pc.updateMood();
                Map<PetComponent.Mood, Float> after = snapshotMoodBlend(pc);
                builder.addSample(before, after);
            } catch (Throwable ignored) {}
        }
        StimulusSummary summary = builder.build();
        if (recordStimulus) {
            EmotionContextCues.recordStimulus(owner, summary);
        }
        return summary;
    }

    private static Map<PetComponent.Mood, Float> snapshotMoodBlend(PetComponent pc) {
        return new EnumMap<>(pc.getMoodBlend());
    }

    private static StimulusSummary pushToSinglePet(ServerPlayerEntity owner, PetComponent pc, PetConsumer consumer) {
        if (owner == null || pc == null) {
            return StimulusSummary.empty(owner != null ? owner.getWorld().getTime() : 0L);
        }
        Map<PetComponent.Mood, Float> before = snapshotMoodBlend(pc);
        consumer.accept(pc);
        pc.updateMood();
        Map<PetComponent.Mood, Float> after = snapshotMoodBlend(pc);
        StimulusSummary.Builder builder = StimulusSummary.builder(owner.getWorld().getTime());
        builder.addSample(before, after);
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
    private static void onWorldTickNuancedEmotions(ServerWorld world) {
        // Run every 3 seconds to keep performance light
        if (world.getTime() % 60 != 0) return;

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

                // Subtle social awareness triggers
                addSocialAwarenessTriggers(pet, pc, player, world);

                // Environmental micro-reactions
                addEnvironmentalMicroTriggers(pet, pc, player, world);

                // Movement and activity patterns
                addMovementActivityTriggers(pet, pc, player, world);

                // Inter-pet social dynamics
                addInterPetSocialTriggers(pet, pc, pets, player, world);

                // Role-specific ambient awareness
                addRoleSpecificAmbientTriggers(pet, pc, player, world);

                // Species-specific personality beats
                addSpeciesSpecificTriggers(pet, pc, player, world);

                pc.updateMood();
            }
        }
    }

    private static void addSocialAwarenessTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        double distanceToOwner = pet.squaredDistanceTo(owner);
        Vec3d ownerLookDir = owner.getRotationVec(1.0f);
        Vec3d petToPet = pet.getPos().subtract(owner.getPos()).normalize();
        double lookAlignment = ownerLookDir.dotProduct(petToPet);

        // Owner looking directly at pet - awareness and attention
        if (distanceToOwner < 64 && lookAlignment > 0.8) { // Owner looking at pet
            pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.08f); // Cozy attention
            if (pet.getHealth() / pet.getMaxHealth() < 0.7f) {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f); // Owner noticing when hurt
            }
            EmotionContextCues.sendCue(owner, "social.look." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.look", pet.getDisplayName()), 200);
        }

        // Owner proximity dynamics
        if (distanceToOwner < 4) { // Very close
            pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.06f); // Home/safety feeling
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

        // Hostile mob proximity (extended awareness)
        boolean hostilesNearby = !world.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,
            pet.getBoundingBox().expand(16), monster -> true).isEmpty();
        if (hostilesNearby) {
            pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.08f);
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.06f);
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

        // Falling or jumping
        if (velocity.y < -0.5) { // Falling fast
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.08f);
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.05f);
            EmotionContextCues.sendCue(owner, "movement.fall." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.fall", pet.getDisplayName()), 200);
        } else if (velocity.y > 0.3) { // Jumping up
            pc.pushEmotion(PetComponent.Emotion.KEFI, 0.06f); // Joy of leaping
            EmotionContextCues.sendCue(owner, "movement.jump." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.movement.jump", pet.getDisplayName()), 200);
        }

        // Swimming
        if (pet.isInFluid()) {
            if (isCat) {
                pc.pushEmotion(PetComponent.Emotion.DISGUST, 0.15f); // Cats hate water
                pc.pushEmotion(PetComponent.Emotion.ANGST, 0.10f);
                EmotionContextCues.sendCue(owner, "movement.cat_swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.cat_swim", pet.getDisplayName()), 200);
            } else {
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.05f); // Others find it refreshing
                EmotionContextCues.sendCue(owner, "movement.swim." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.movement.swim", pet.getDisplayName()), 200);
            }
        }
    }

    private static void addInterPetSocialTriggers(MobEntity pet, PetComponent pc, List<MobEntity> allPets, ServerPlayerEntity owner, ServerWorld world) {
        int nearbyPetCount = 0;
        boolean hasOlderPet = false;
        boolean hasYoungerPet = false;

        for (MobEntity otherPet : allPets) {
            if (otherPet == pet) continue;
            if (pet.squaredDistanceTo(otherPet) > 64) continue; // Within 8 blocks

            nearbyPetCount++;
            PetComponent otherPc = PetComponent.get(otherPet);
            if (otherPc != null) {
                // Age-based social dynamics
                if (otherPc.getLevel() > pc.getLevel()) {
                    hasOlderPet = true;
                } else if (otherPc.getLevel() < pc.getLevel()) {
                    hasYoungerPet = true;
                }

                // Mood contagion - pets influence each other's moods subtly
                PetComponent.Mood otherMood = otherPc.getCurrentMood();
                if (otherMood != null) {
                    switch (otherMood) {
                        case HAPPY -> pc.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.02f);
                        case AFRAID -> pc.pushEmotion(PetComponent.Emotion.ANGST, 0.03f);
                        case ANGRY -> pc.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.02f);
                        case CALM -> pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.03f);
                    }
                }
            }
        }

        // Pack dynamics
        if (nearbyPetCount >= 2) {
            pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.06f); // Cozy group feeling
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.04f); // Pack protection
            EmotionContextCues.sendCue(owner, "social.pack." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.pack", pet.getDisplayName()), 200);
        }

        if (hasOlderPet) {
            pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.03f); // Learning from elders
            EmotionContextCues.sendCue(owner, "social.elder." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.elder", pet.getDisplayName()), 200);
        }

        if (hasYoungerPet) {
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.05f); // Protective of youngsters
            EmotionContextCues.sendCue(owner, "social.young." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.young", pet.getDisplayName()), 200);
        }

        // Loneliness when isolated
        if (nearbyPetCount == 0) {
            PetComponent.Mood currentMood = pc.getCurrentMood();
            if (currentMood != PetComponent.Mood.CALM) { // Unless they're calm/content
                pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.03f); // Longing for companionship
                EmotionContextCues.sendCue(owner, "social.lonely." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.social.lonely", pet.getDisplayName()), 200);
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

                // Enhanced weather awareness
                addAdvancedWeatherTriggers(pet, pc, player, world);

                // Tag-based item awareness
                addTagBasedItemTriggers(pet, pc, player);

                pc.updateMood();
            }
        }
    }

    private static void addAdvancedWeatherTriggers(MobEntity pet, PetComponent pc, ServerPlayerEntity owner, ServerWorld world) {
        boolean isCat = pet instanceof CatEntity;
        boolean isWolf = pet instanceof WolfEntity;
        // Thunder detection - much more impactful than basic rain
        if (world.isThundering()) {
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.15f); // Thunder is startling
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.12f); // Weather anxiety
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f); // Protective of owner during storms
            EmotionContextCues.sendCue(owner, "weather.thunder.pet." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.weather.thunder_pet", pet.getDisplayName()), 400);
        } else if (world.isRaining()) {
            // Rain reactions based on pet type - data-driven approach
            if (isCat) {
                pc.pushEmotion(PetComponent.Emotion.DISGUST, 0.08f); // Cats dislike getting wet
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.06f); // Seeking shelter
                EmotionContextCues.sendCue(owner, "weather.rain.cat." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_cat", pet.getDisplayName()), 400);
            } else if (isWolf) {
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.04f); // Dogs often enjoy rain
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.05f); // Refreshing feeling
                EmotionContextCues.sendCue(owner, "weather.rain.dog." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_dog", pet.getDisplayName()), 400);
            } else {
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.03f); // General refresh feeling
                EmotionContextCues.sendCue(owner, "weather.rain.pet." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_pet", pet.getDisplayName()), 400);
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
        }

        // Emotional reactions based on inventory composition
        if (hasValuableItems) {
            pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.04f); // Security from wealth
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.06f); // Guarding valuable things
            EmotionContextCues.sendCue(owner, "inventory.valuables",
                Text.translatable("petsplus.emotion_cue.inventory.valuables"), 1200);
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
}
