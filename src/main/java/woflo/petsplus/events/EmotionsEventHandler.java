package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
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
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

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
    private static void onAfterBlockBreak(World world, PlayerEntity player, BlockPos pos, net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return;
        String idPath = getBlockPath(state.getBlock());

        // Determine emotion mix based on block type
        float kefi = 0.12f; // general vigor for doing stuff
        float glee = 0.0f;
        float wabi = 0.0f;
        float stoic = 0.0f;

        if (idPath.contains("ore") || idPath.contains("ancient_debris") || idPath.contains("trial_ore") || idPath.contains("raw_")) {
            glee = 0.35f; // shiny find!
        } else if (idPath.contains("log") || idPath.contains("planks") || idPath.contains("brick") || idPath.contains("stone_bricks") || idPath.contains("concrete")) {
            wabi = 0.18f; // building/crafting aesthetic
        } else if (idPath.contains("dirt") || idPath.contains("sand") || idPath.contains("gravel") || idPath.contains("deepslate")) {
            stoic = 0.10f; // resilient resource grind
        }

        final float kefiF = kefi;
        final float gleeF = glee;
        final float wabiF = wabi;
        final float stoicF = stoic;
        pushToNearbyOwnedPets(sp, 32, pc -> {
            if (kefiF > 0) pc.pushEmotion(PetComponent.Emotion.KEFI, kefiF);
            if (gleeF > 0) pc.pushEmotion(PetComponent.Emotion.GLEE, gleeF);
            if (wabiF > 0) pc.pushEmotion(PetComponent.Emotion.WABI_SABI, wabiF);
            if (stoicF > 0) pc.pushEmotion(PetComponent.Emotion.STOIC, stoicF);
        });

        if (gleeF > 0) {
            EmotionContextCues.sendCue(sp, "block_break.ore", Text.translatable("petsplus.emotion_cue.block_break.ore"), 200);
        } else if (wabiF > 0) {
            EmotionContextCues.sendCue(sp, "block_break.crafting", Text.translatable("petsplus.emotion_cue.block_break.crafting"), 200);
        } else if (stoicF > 0) {
            EmotionContextCues.sendCue(sp, "block_break.resource", Text.translatable("petsplus.emotion_cue.block_break.resource"), 200);
        } else if (kefiF > 0) {
            EmotionContextCues.sendCue(sp, "block_break.generic", Text.translatable("petsplus.emotion_cue.block_break.generic"), 400);
        }
    }

    private static String getBlockPath(Block block) {
        try {
            var id = net.minecraft.registry.Registries.BLOCK.getId(block);
            return id == null ? "" : id.getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    // (Block place detour handled in onUseBlock based on held BlockItem)

    // ==== Kill Events → PASSIONATE, PLAYFUL, HAPPY, FOCUSED ====
    private static void onAfterKilledOther(ServerWorld world, Entity killer, LivingEntity killed) {
        if (killer instanceof ServerPlayerEntity sp) {
            // Owner kill → triumph and relief (more if low health)
            float healthPct = Math.max(0f, sp.getHealth() / sp.getMaxHealth());
            float relief = healthPct < 0.35f ? 0.45f : 0.25f;
            pushToNearbyOwnedPets(sp, 32, pc -> {
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.25f);
                pc.pushEmotion(PetComponent.Emotion.GLEE, 0.40f);
                pc.pushEmotion(PetComponent.Emotion.RELIEF, relief);
                pc.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.18f);
            });

            String translationKey = healthPct < 0.35f
                ? "petsplus.emotion_cue.combat.owner_kill_close"
                : "petsplus.emotion_cue.combat.owner_kill";
            EmotionContextCues.sendCue(sp, "combat.owner_kill", Text.translatable(translationKey), 200);
        }

        if (killer instanceof MobEntity mob) {
            PetComponent pc = PetComponent.get(mob);
            if (pc != null && pc.getOwner() instanceof ServerPlayerEntity owner) {
                // Pet secured a kill → zeal (aspiration) and resilience
                pc.pushEmotion(PetComponent.Emotion.KEFI, 0.35f);
                pc.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.40f);
                pc.pushEmotion(PetComponent.Emotion.STOIC, 0.30f);
                pc.updateMood();
                EmotionContextCues.sendCue(owner,
                    "combat.pet_kill." + mob.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.combat.pet_kill", mob.getDisplayName()),
                    200);
                if (pc.hasRole(PetRoleType.STRIKER)) {
                    EmotionContextCues.sendCue(owner,
                        "role.striker.execute." + mob.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.role.striker_execute", mob.getDisplayName()),
                        200);
                }
            }
        }
    }

    // ==== Item Use → Food/potion themed emotions ====
    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return ActionResult.PASS;

        // Food in general → SOBREMESA + QUERECIA (home/comfort)
        if (stack.get(DataComponentTypes.FOOD) != null) {
            pushToNearbyOwnedPets(sp, 24, pc -> {
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.22f);
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.18f);
            });
            EmotionContextCues.sendCue(sp, "item.food", Text.translatable("petsplus.emotion_cue.item.food"), 160);
            return ActionResult.PASS;
        }

        // Specific notable items
        if (stack.isOf(Items.GOLDEN_APPLE)) {
            pushToNearbyOwnedPets(sp, 32, pc -> {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.35f);
                pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.20f);
            });
            EmotionContextCues.sendCue(sp, "item.golden_apple", Text.translatable("petsplus.emotion_cue.item.golden_apple"), 400);
        } else if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            pushToNearbyOwnedPets(sp, 32, pc -> {
                pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.45f);
                pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.30f);
            });
            EmotionContextCues.sendCue(sp, "item.enchanted_golden_apple", Text.translatable("petsplus.emotion_cue.item.enchanted_golden_apple"), 400);
        } else if (stack.isOf(Items.HONEY_BOTTLE)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.25f));
            EmotionContextCues.sendCue(sp, "item.honey_bottle", Text.translatable("petsplus.emotion_cue.item.honey_bottle"), 200);
        } else if (stack.isOf(Items.MILK_BUCKET)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.22f));
            EmotionContextCues.sendCue(sp, "item.milk_bucket", Text.translatable("petsplus.emotion_cue.item.milk_bucket"), 200);
        } else if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
            pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.5f));
            EmotionContextCues.sendCue(sp, "item.totem", Text.translatable("petsplus.emotion_cue.item.totem"), 600);
        } else if (stack.isOf(Items.ENDER_PEARL)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.18f));
            EmotionContextCues.sendCue(sp, "item.ender_pearl", Text.translatable("petsplus.emotion_cue.item.ender_pearl"), 200);
        } else if (stack.isOf(Items.FIREWORK_ROCKET)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.KEFI, 0.2f));
            EmotionContextCues.sendCue(sp, "item.firework", Text.translatable("petsplus.emotion_cue.item.firework"), 200);
        } else if (stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.PUFFERFISH)) {
            pushToNearbyOwnedPets(sp, 16, pc -> pc.pushEmotion(PetComponent.Emotion.DISGUST, 0.28f));
            EmotionContextCues.sendCue(sp, "item.sus_food", Text.translatable("petsplus.emotion_cue.item.sus_food"), 200);
        } else if (stack.isOf(Items.SPYGLASS)) {
            pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.20f));
            EmotionContextCues.sendCue(sp, "item.spyglass", Text.translatable("petsplus.emotion_cue.item.spyglass"), 200);
        } else if (stack.isOf(Items.FILLED_MAP)) {
            pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f));
            EmotionContextCues.sendCue(sp, "item.map", Text.translatable("petsplus.emotion_cue.item.map"), 200);
        } else if (stack.isOf(Items.COMPASS)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.12f));
            EmotionContextCues.sendCue(sp, "item.compass", Text.translatable("petsplus.emotion_cue.item.compass"), 200);
        } else if (stack.isOf(Items.RECOVERY_COMPASS)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.REGRET, 0.15f));
            EmotionContextCues.sendCue(sp, "item.recovery_compass", Text.translatable("petsplus.emotion_cue.item.recovery_compass"), 200);
        } else if (stack.isOf(Items.WRITABLE_BOOK) || stack.isOf(Items.WRITTEN_BOOK) || stack.isOf(Items.BOOK)) {
            pushToNearbyOwnedPets(sp, 24, pc -> {
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.12f);
                pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.08f);
            });
            EmotionContextCues.sendCue(sp, "item.book", Text.translatable("petsplus.emotion_cue.item.book"), 200);
        } else if (stack.isOf(Items.SHIELD)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.15f));
            EmotionContextCues.sendCue(sp, "item.shield", Text.translatable("petsplus.emotion_cue.item.shield"), 200);
        } else if (stack.isOf(Items.SADDLE)) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.12f));
            EmotionContextCues.sendCue(sp, "item.saddle", Text.translatable("petsplus.emotion_cue.item.saddle"), 200);
        } else if (stack.isOf(Items.BRUSH)) { // archaeology
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.12f));
            EmotionContextCues.sendCue(sp, "item.brush", Text.translatable("petsplus.emotion_cue.item.brush"), 200);
        }

        return ActionResult.PASS;
    }

    // ==== Block Use → Homey stations, beds, jukebox ====
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        BlockPos pos = hitResult.getBlockPos();
        var state = world.getBlockState(pos);
        String idPath = getBlockPath(state.getBlock());

        float querecia = 0f, sobremesa = 0f, wabi = 0f, bliss = 0f;
        if (idPath.contains("bed") || idPath.contains("chest") || idPath.contains("barrel")) {
            querecia += 0.25f;
        }
        if (idPath.contains("campfire") || idPath.contains("furnace") || idPath.contains("smoker") || idPath.contains("blast_furnace")) {
            sobremesa += 0.22f; // cozy food vibes
        }
        if (idPath.contains("crafting_table") || idPath.contains("anvil") || idPath.contains("grindstone") || idPath.contains("enchanting_table") || idPath.contains("cartography_table") || idPath.contains("loom")) {
            wabi += 0.2f; // craft/repair aesthetic
        }
        if (idPath.contains("jukebox") || idPath.contains("note_block")) {
            bliss += 0.22f; // music joy
        }

        // Niche block interactions
        if (idPath.contains("smithing_table")) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.12f));
            EmotionContextCues.sendCue(sp, "block_use.smithing", Text.translatable("petsplus.emotion_cue.block_use.smithing"), 200);
        }
        if (idPath.contains("grindstone")) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.REGRET, 0.10f));
            EmotionContextCues.sendCue(sp, "block_use.grindstone", Text.translatable("petsplus.emotion_cue.block_use.grindstone"), 200);
        }
        if (idPath.contains("beacon")) {
            pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.30f));
            EmotionContextCues.sendCue(sp, "block_use.beacon", Text.translatable("petsplus.emotion_cue.block_use.beacon"), 600);
        }
        if (idPath.contains("bell")) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.18f));
            EmotionContextCues.sendCue(sp, "block_use.bell", Text.translatable("petsplus.emotion_cue.block_use.bell"), 200);
        }
        if (idPath.contains("beehive") || idPath.contains("bee_nest")) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.18f));
            EmotionContextCues.sendCue(sp, "block_use.bee", Text.translatable("petsplus.emotion_cue.block_use.bee"), 200);
        }
        if (idPath.contains("flower_pot") || idPath.contains("potted_")) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.10f));
            EmotionContextCues.sendCue(sp, "block_use.decor", Text.translatable("petsplus.emotion_cue.block_use.decor"), 200);
        }

        // Detected intent to place a block: use held BlockItem to infer "building/homey" emotions
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof BlockItem blockItem) {
            String placePath = getBlockPath(blockItem.getBlock());
            float kefi = 0.0f, wabiPlace = 0.0f, havenPlace = 0.0f;
            // General building vigor
            kefi = 0.10f;
            // Aesthetic crafting/building
            if (placePath.contains("log") || placePath.contains("planks") || placePath.contains("brick") || placePath.contains("stone_bricks") || placePath.contains("concrete")) {
                wabiPlace += 0.16f;
            }
            // Homey decor
            if (placePath.contains("bed") || placePath.contains("campfire") || placePath.contains("lantern") || placePath.contains("candle") || placePath.contains("flower") || placePath.contains("carpet") || placePath.contains("bookshelf")) {
                havenPlace += 0.22f;
            }
            if (kefi > 0 || wabiPlace > 0 || havenPlace > 0) {
                final float kF = kefi, wF = wabiPlace, gF = havenPlace;
                pushToNearbyOwnedPets(sp, 32, pc -> {
                    if (kF > 0) pc.pushEmotion(PetComponent.Emotion.KEFI, kF);
                    if (wF > 0) pc.pushEmotion(PetComponent.Emotion.WABI_SABI, wF);
                    if (gF > 0) pc.pushEmotion(PetComponent.Emotion.QUERECIA, gF);
                });
                if (gF > 0) {
                    EmotionContextCues.sendCue(sp, "block_place.decor", Text.translatable("petsplus.emotion_cue.block_place.decor"), 200);
                } else if (wF > 0) {
                    EmotionContextCues.sendCue(sp, "block_place.crafting", Text.translatable("petsplus.emotion_cue.block_place.crafting"), 200);
                } else {
                    EmotionContextCues.sendCue(sp, "block_place.generic", Text.translatable("petsplus.emotion_cue.block_place.generic"), 200);
                }
            }
        }

        if (querecia + sobremesa + wabi + bliss > 0) {
            final float gF = querecia, sF = sobremesa, wF = wabi, aF = bliss;
            pushToNearbyOwnedPets(sp, 24, pc -> {
                if (gF > 0) pc.pushEmotion(PetComponent.Emotion.QUERECIA, gF);
                if (sF > 0) pc.pushEmotion(PetComponent.Emotion.SOBREMESA, sF);
                if (wF > 0) pc.pushEmotion(PetComponent.Emotion.WABI_SABI, wF);
                if (aF > 0) pc.pushEmotion(PetComponent.Emotion.BLISSFUL, aF);
            });
            String cueId = null;
            Text cueText = null;
            if (aF > 0) {
                cueId = "block_use.music";
                cueText = Text.translatable("petsplus.emotion_cue.block_use.music");
            } else if (sF > 0) {
                cueId = "block_use.cooking";
                cueText = Text.translatable("petsplus.emotion_cue.block_use.cooking");
            } else if (gF > 0) {
                cueId = "block_use.home";
                cueText = Text.translatable("petsplus.emotion_cue.block_use.home");
            } else if (wF > 0) {
                cueId = "block_use.crafting";
                cueText = Text.translatable("petsplus.emotion_cue.block_use.crafting");
            }
            if (cueId != null && cueText != null) {
                EmotionContextCues.sendCue(sp, cueId, cueText, 200);
            }
        }
        return ActionResult.PASS;
    }

    // ==== Entity Use → Lead/mount moments → PROTECTIVENESS, FERNWEH ====
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);

        // Leashing or unleashing signals protectiveness/affection
        if (stack.isOf(Items.LEAD) && entity instanceof MobEntity) {
            pushToNearbyOwnedPets(sp, 24, pc -> pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.20f));
            EmotionContextCues.sendCue(sp, "entity.lead", Text.translatable("petsplus.emotion_cue.entity.lead"), 200);
            return ActionResult.PASS; // don't consume event
        }

        // Right-clicking ridables often uses entity interact; give wanderlust when mounting mounts/boats
        String type = entity.getType().toString().toLowerCase();
        if (type.contains("boat") || type.contains("horse") || type.contains("camel") || type.contains("donkey") || type.contains("mule") || type.contains("llama")) {
            pushToNearbyOwnedPets(sp, 32, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.22f));
            EmotionContextCues.sendCue(sp, "entity.mount", Text.translatable("petsplus.emotion_cue.entity.mount"), 200);
        }

        // Trading with villagers: balance and cozy community
        if (entity instanceof VillagerEntity) {
            pushToNearbyOwnedPets(sp, 24, pc -> {
                pc.pushEmotion(PetComponent.Emotion.LAGOM, 0.12f);
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.10f);
            });
            EmotionContextCues.sendCue(sp, "entity.villager_greet", Text.translatable("petsplus.emotion_cue.entity.villager_greet"), 200);
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
    private record WeatherState(boolean raining, boolean thundering) {}

    // Time of day phases for subtle transitions
    private enum TimePhase { DAWN, DAY, DUSK, NIGHT }
    private static final Map<ServerWorld, TimePhase> TIME_PHASES = new WeakHashMap<>();

    // Lightweight per-player environment state for biome/dimension/idle tracking
    private static final Map<ServerPlayerEntity, PlayerEnvState> PLAYER_ENV = new WeakHashMap<>();
    private static class PlayerEnvState {
        Identifier biomeId;
        Identifier dimensionId;
        Vec3d lastPos;
        int idleTicks;
        PlayerEnvState(Identifier biomeId, Identifier dimensionId, Vec3d lastPos) {
            this.biomeId = biomeId;
            this.dimensionId = dimensionId;
            this.lastPos = lastPos;
            this.idleTicks = 0;
        }
    }

    private static void onWorldTickWeatherAndEnv(ServerWorld world) {
        WeatherState prev = WEATHER.get(world);
        boolean r = world.isRaining();
        boolean t = world.isThundering();
        if (prev == null) {
            WEATHER.put(world, new WeatherState(r, t));
            // Initialize time phase too
            TIME_PHASES.put(world, computeTimePhase(world.getTimeOfDay()));
        } else {
            if (r != prev.raining || t != prev.thundering) {
                WEATHER.put(world, new WeatherState(r, t));
                // Push to pets near players in this world
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
            Identifier biomeId = null;
            try {
                var entry = world.getBiome(sp.getBlockPos());
                Optional<RegistryKey<Biome>> key = entry.getKey();
                biomeId = key.map(RegistryKey::getValue).orElse(null);
            } catch (Throwable ignored) {}
            Identifier dimId = world.getRegistryKey().getValue();

            PlayerEnvState st = PLAYER_ENV.get(sp);
            Vec3d pos = sp.getPos();
            if (st == null) {
                st = new PlayerEnvState(biomeId, dimId, pos);
                PLAYER_ENV.put(sp, st);
            }

            // Biome change
            if (biomeId != null && (st.biomeId == null || !biomeId.equals(st.biomeId))) {
                st.biomeId = biomeId;
                String path = biomeId.getPath();
                if (containsAny(path, "desert", "badlands", "beach")) {
                    pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.HANYAUKU, 0.12f));
                }
                if (containsAny(path, "cherry_grove", "flower_forest")) {
                    pushToNearbyOwnedPets(sp, 32, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.10f);
                        pc.pushEmotion(PetComponent.Emotion.BLISSFUL, 0.08f);
                    });
                }
                if (path.contains("mushroom_fields")) {
                    pushToNearbyOwnedPets(sp, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.GLEE, 0.15f);
                        pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.08f);
                    });
                }
                if (containsAny(path, "deep_dark")) {
                    pushToNearbyOwnedPets(sp, 48, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.18f);
                        pc.pushEmotion(PetComponent.Emotion.ANGST, 0.12f);
                    });
                }
                if (containsAny(path, "ocean")) {
                    pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.08f));
                }
                if (containsAny(path, "snow", "ice_spikes", "grove")) {
                    pushToNearbyOwnedPets(sp, 48, pc -> pc.pushEmotion(PetComponent.Emotion.STOIC, 0.10f));
                }
                Identifier newBiomeId = biomeId;
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
            if (dimId != null && (st.dimensionId == null || !dimId.equals(st.dimensionId))) {
                Identifier old = st.dimensionId;
                st.dimensionId = dimId;
                if (dimId.getPath().contains("overworld") && old != null && !old.getPath().contains("overworld")) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f);
                    });
                } else if (dimId.getPath().contains("the_nether")) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.FERNWEH, 0.15f);
                        pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f);
                    });
                } else if (dimId.getPath().contains("the_end")) {
                    pushToNearbyOwnedPets(sp, 64, pc -> {
                        pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.14f);
                        pc.pushEmotion(PetComponent.Emotion.STOIC, 0.12f);
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, 0.08f);
                    });
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

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
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

    private static void pushToNearbyOwnedPets(ServerPlayerEntity owner, double radius, PetConsumer consumer) {
        List<MobEntity> pets = owner.getWorld().getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(radius),
            mob -> {
                PetComponent pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(owner);
            }
        );
        for (MobEntity pet : pets) {
            PetComponent pc = PetComponent.get(pet);
            if (pc != null) {
                try {
                    consumer.accept(pc);
                    pc.updateMood();
                } catch (Throwable ignored) {}
            }
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
            String blockName = world.getBlockState(offset).getBlock().toString().toLowerCase();
            if (blockName.contains("flower") || blockName.contains("grass") || blockName.contains("fern")) {
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
            if (pet.getType().toString().contains("cat")) {
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
            Identifier dimension = world.getRegistryKey().getValue();
            if (dimension != null && dimension.getPath().contains("the_nether")) {
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

    // ==== 5th Wave: Tag-Based and Data-Driven Advanced Emotion Hooks ====

    /**
     * Enchanting table interactions - magic and wonder
     */
    private static ActionResult onEnchantingTableUse(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        net.minecraft.block.BlockState state = world.getBlockState(pos);

        // Check if it's an enchanting table using block tags (flexible)
        if (state.isIn(net.minecraft.registry.tag.BlockTags.ENCHANTMENT_POWER_PROVIDER) ||
            state.getBlock().toString().contains("enchanting_table")) {

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
            pushToNearbyOwnedPets(sp, 20, pc -> {
                pc.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.12f); // Social interaction comfort
                pc.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.08f); // Observing human customs

                // Professional-specific reactions
                String profession = villager.getVillagerData().profession().toString();
                if (profession.contains("butcher") || profession.contains("farmer")) {
                    pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.10f); // Food security feelings
                } else if (profession.contains("cleric")) {
                    pc.pushEmotion(PetComponent.Emotion.YUGEN, 0.08f); // Mystical presence
                } else if (profession.contains("weaponsmith") || profession.contains("armorer")) {
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f); // Defense associations
                }
            });
            EmotionContextCues.sendCue(sp, "entity.trade", Text.translatable("petsplus.emotion_cue.entity.trade"), 200);
            String profession = villager.getVillagerData().profession().toString();
            if (profession.contains("butcher") || profession.contains("farmer")) {
                EmotionContextCues.sendCue(sp, "entity.trade.food", Text.translatable("petsplus.emotion_cue.entity.trade_food"), 400);
            } else if (profession.contains("cleric")) {
                EmotionContextCues.sendCue(sp, "entity.trade.mystic", Text.translatable("petsplus.emotion_cue.entity.trade_mystic"), 400);
            } else if (profession.contains("weaponsmith") || profession.contains("armorer")) {
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
        // Thunder detection - much more impactful than basic rain
        if (world.isThundering()) {
            pc.pushEmotion(PetComponent.Emotion.STARTLE, 0.15f); // Thunder is startling
            pc.pushEmotion(PetComponent.Emotion.ANGST, 0.12f); // Weather anxiety
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.08f); // Protective of owner during storms
            EmotionContextCues.sendCue(owner, "weather.thunder.pet." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.weather.thunder_pet", pet.getDisplayName()), 400);
        } else if (world.isRaining()) {
            // Rain reactions based on pet type - data-driven approach
            String petType = pet.getType().toString().toLowerCase();
            if (petType.contains("cat")) {
                pc.pushEmotion(PetComponent.Emotion.DISGUST, 0.08f); // Cats dislike getting wet
                pc.pushEmotion(PetComponent.Emotion.QUERECIA, 0.06f); // Seeking shelter
                EmotionContextCues.sendCue(owner, "weather.rain.cat." + pet.getUuidAsString(),
                    Text.translatable("petsplus.emotion_cue.weather.rain_cat", pet.getDisplayName()), 400);
            } else if (petType.contains("wolf") || petType.contains("dog")) {
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
            // Clear weather after storm - relief
            if (world.getTime() % 6000 == 0) { // Check periodically
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
            if (stack.isIn(net.minecraft.registry.tag.ItemTags.TRIM_MATERIALS) ||
                stack.getItem().toString().contains("diamond") ||
                stack.getItem().toString().contains("gold") ||
                stack.getItem().toString().contains("emerald")) {
                hasValuableItems = true;
            }

            if (stack.get(net.minecraft.component.DataComponentTypes.FOOD) != null ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.MEAT) ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.FISHES)) {
                hasFoodItems = true;
            }

            if (stack.hasEnchantments() ||
                stack.getItem().toString().contains("potion") ||
                stack.getItem().toString().contains("enchanted") ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.BOOKSHELF_BOOKS)) {
                hasMagicalItems = true;
            }

            if (stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.AXES) ||
                stack.getItem().toString().contains("bow") ||
                stack.getItem().toString().contains("crossbow")) {
                hasWeapons = true;
            }

            if (stack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES) ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS) ||
                stack.isIn(net.minecraft.registry.tag.ItemTags.HOES)) {
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
