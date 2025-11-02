package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.api.registry.RoleIdentifierUtil;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;

/**
 * Handles petting interactions with tamed pets.
 * Provides immersive feedback and role-specific bonuses when players pet their companions.
 */
public class PettingHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(PettingHandler::onUseEntity);
        Petsplus.LOGGER.info("Petting interaction handler registered");
    }

    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(entity instanceof MobEntity mob)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        // Only trigger if player has empty hand and is sneaking
        if (!player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;

        // Must be a pet (ownership optional; non-owners allowed with reduced effects)
        PetComponent petComp = PetComponent.get(mob);
        if (petComp == null) return ActionResult.PASS;
        boolean isOwner = petComp.isOwnedBy(player);

        // Skip petting for rideable entities when they could be mounted
        // Let vanilla handle mounting, then petting can happen when already mounted
        if (isRideable(mob) && !player.hasVehicle()) {
            return ActionResult.PASS;
        }

        // petComp is guaranteed non-null at this point

        // Check cooldown (pet-wide)
        long currentTime = world.getTime();
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int cooldownTicks = config.getPettingCooldownTicks();
        
        Long lastPetTime = petComp.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
        if (lastPetTime != null && (currentTime - lastPetTime) < cooldownTicks) {
            // Still on cooldown, but provide subtle feedback through the cue helper
            EmotionContextCues.sendCue(serverPlayer,
                "petting.cooldown." + mob.getUuidAsString(),
                mob,
                Text.translatable("petsplus.emotion_cue.petting.cooldown", mob.getDisplayName()),
                80);
            // Even if petting effects are on cooldown, allow post-cuddle ability trigger consumption
            if (isOwner) {
                OwnerAbilitySignalTracker.handlePostCuddlePetting(serverPlayer, mob);
            }
            return ActionResult.SUCCESS;
        }

        // Perform petting
        performPetting(serverPlayer, mob, petComp, currentTime, isOwner);
        
        return ActionResult.SUCCESS;
    }

    private static void performPetting(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, long currentTime, boolean isOwner) {
        ServerWorld world = (ServerWorld) pet.getEntityWorld();
        Identifier roleId = petComp.getRoleId();

        // Update petting state
        petComp.setStateData(PetComponent.StateKeys.LAST_PET_TIME, currentTime);
        Integer currentCount = petComp.getStateData(PetComponent.StateKeys.PET_COUNT, Integer.class);
        int newCount = (currentCount == null ? 0 : currentCount) + 1;
        petComp.setStateData(PetComponent.StateKeys.PET_COUNT, newCount);

        // Base petting effects (reduced for non-owners)
        if (isOwner) {
            emitBasePettingEffects(player, pet, world, newCount);
        } else {
            emitNonOwnerPettingEffects(player, pet, world);
        }

        // Role-specific effects (owner-only)
        if (isOwner) {
            emitRoleSpecificEffects(player, pet, world, roleId);
        }

        // Bonding benefits (owner-only XP rewards)
        if (isOwner) {
            applyBondingBenefits(player, pet, petComp);
        } else {
            // Non-owners also get XP, but without role-specific bonuses
            // This lets them slowly build affection through consistent petting
            int playerXp = calculatePlayerPettingXp(player, pet, petComp);
            if (playerXp > 0) {
                player.addExperience(playerXp);
                if (world != null) {
                    String xpEvent = playerXp <= 1 ? "petting_xp_small" : (playerXp == 2 ? "petting_xp_medium" : "petting_xp_large");
                    FeedbackManager.emitFeedback(xpEvent, pet, world);
                    EmotionContextCues.sendCue(player,
                        "petting.xp." + pet.getUuidAsString(),
                        pet,
                        Text.translatable("petsplus.emotion_cue.petting.xp", playerXp),
                        0);
                }
            }
        }

        // Petting cues
        if (isOwner) {
            emitPettingCues(player, pet, newCount);
        } else {
            EmotionContextCues.sendCue(player,
                "petting.curiosity." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.petting.curiosity", pet.getDisplayName()),
                80);
        }

        // Relationship tracking - record petting interaction
        RelationshipEventHandler.onPetPetted(pet, player);

        EmotionProcessor.processSocialInteraction(
            pet,
            petComp,
            player,
            EmotionContextMapper.SocialInteractionType.PETTING,
            new PettingContext(newCount, currentTime)
        );

        // Immediate, small emotion stimulus so pet reacts right away (batched by stimulus bus)
        try {
            var rel = petComp.getRelationshipWith(player.getUuid());
            float affection = rel != null ? rel.affection() : 0.0f;
            // Scale intensity: owners stronger, non-owners weaker but influenced by affection
            float ownerMultiplier = petComp.isOwnedBy(player) ? 1.0f : (0.45f + Math.max(0f, affection) * 0.55f);
            float baseContent = 0.18f * ownerMultiplier;    // contentment
            float basePlayful = 0.08f * ownerMultiplier;    // playful spark
            float basePride = 0.06f * ownerMultiplier;      // small pride boost

            MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                collector.pushEmotion(PetComponent.Emotion.CONTENT, baseContent);
                collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, basePlayful);
                collector.pushEmotion(PetComponent.Emotion.PRIDE, basePride);
            });
        } catch (Throwable t) {
            // Defensive: don't let mood emission break petting flow
            Petsplus.LOGGER.debug("Failed to queue petting stimulus", t);
        }

        // Achievement tracking - owner-only
        if (isOwner) {
            woflo.petsplus.advancement.AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
                player,
                woflo.petsplus.advancement.criteria.PetInteractionCriterion.INTERACTION_PETTING,
                newCount
            );
        }

        // If a proximity channel just completed for this owner-pet pair, consume it now
        if (isOwner) {
            OwnerAbilitySignalTracker.handlePostCuddlePetting(player, pet);
        }
    }

    private static void emitBasePettingEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world, int petCount) {
        // Heart particles - more hearts for higher pet counts (caps at 10)
        FeedbackManager.emitFeedback("pet_hearts", pet, world);

        // Pet-appropriate sound based on entity type
        String soundEffect = determinePetSound(pet);
        FeedbackManager.emitFeedback(soundEffect, pet, world);

        // Action bar message via emotion cue helper so it respects throttling with other cues
        String message = generatePettingMessage(pet, petCount);
        EmotionContextCues.sendCue(player,
            "petting.message." + pet.getUuidAsString(),
            pet,
            Text.literal(message),
            0);
    }

    private static void emitNonOwnerPettingEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world) {
        // Lighter hearts + neutral curiosity sparkles
        FeedbackManager.emitFeedback("pet_hearts_light", pet, world);
        FeedbackManager.emitFeedback("contagion_neutral", pet, world);

        // Softer, generic happy sound
        FeedbackManager.emitFeedback("pet_generic_happy", pet, world);

        // Subtle message to petter
        EmotionContextCues.sendCue(player,
            "petting.message.nonowner." + pet.getUuidAsString(),
            Text.literal("It tilts its head, curious."),
            0);
    }

    private static void emitRoleSpecificEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world, Identifier roleId) {
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        PetRoleType.Presentation presentation = roleType != null
            ? roleType.presentation()
            : PetRoleType.Presentation.DEFAULT;
        PetRoleType.Petting petting = presentation.petting();

        if (petting.hasFeedbackEvent()) {
            FeedbackManager.emitFeedback(petting.feedbackEvent(), pet, world);
        }

        Text message = RoleIdentifierUtil.resolveMessageText(petting.message(), "Your companion seems content.");
        if (!message.getString().isBlank()) {
            String cueId = "petting.role." + (roleId != null ? roleId.toString() : "default") + "." + pet.getUuidAsString();
            EmotionContextCues.sendCue(player, cueId, pet, message, 100);
        }
    }

    private static void emitPettingCues(ServerPlayerEntity player, MobEntity pet, int petCount) {
        EmotionContextCues.sendCue(player,
            "petting.affection." + pet.getUuidAsString(),
            pet,
            Text.translatable("petsplus.emotion_cue.petting.affection", pet.getDisplayName()),
            80);

        if (petCount == 1) {
            EmotionContextCues.sendCue(player,
                "petting.first." + pet.getUuidAsString(),
                pet,
                Text.translatable("petsplus.emotion_cue.petting.first", pet.getDisplayName()),
                6000);
        } else if (petCount % 25 == 0) {
            EmotionContextCues.sendCue(player,
                "petting.milestone." + pet.getUuidAsString(),
                pet,
                Text.translatable("petsplus.emotion_cue.petting.milestone", pet.getDisplayName(), petCount),
                6000);
        }
    }

    private static void applyBondingBenefits(ServerPlayerEntity player, MobEntity pet, PetComponent petComp) {
        if (!PetsPlusConfig.getInstance().isPettingXpBonusEnabled()) {
            return;
        }
        
        // Award player XP based on bonding level (once per day per pet)
        int playerXp = calculatePlayerPettingXp(player, pet, petComp);
        if (playerXp > 0) {
            player.addExperience(playerXp);
            // XpEventHandler will automatically catch this and distribute to pets
            // Emit dynamic feedback scaled to XP magnitude
            ServerWorld world = (ServerWorld) pet.getEntityWorld();
            if (world != null) {
                String xpEvent = playerXp <= 1 ? "petting_xp_small" : (playerXp == 2 ? "petting_xp_medium" : "petting_xp_large");
                FeedbackManager.emitFeedback(xpEvent, pet, world);
                // Inform player via action bar cue
                EmotionContextCues.sendCue(player,
                    "petting.xp." + pet.getUuidAsString(),
                    pet,
                    Text.translatable("petsplus.emotion_cue.petting.xp", playerXp),
                    0);
            }
        }
    }

    private static int calculatePlayerPettingXp(ServerPlayerEntity player, MobEntity pet, PetComponent petComp) {
        int todayDay = (int) (pet.getEntityWorld().getTime() / 24000L);
        Integer lastDay = petComp.getStateData(PetComponent.StateKeys.LAST_PETTING_DAY, Integer.class);
        
        // Already petted today, no XP
        if (lastDay != null && lastDay == todayDay) {
            return 0;
        }
        
        // Determine XP based on bonding level
        boolean isOwner = petComp.isOwnedBy(player);
        int baseXp;
        if (isOwner) {
            baseXp = 3;
        } else {
            var relationship = petComp.getRelationshipWith(player.getUuid());
            baseXp = (relationship != null && relationship.affection() > 0.25f) ? 2 : 1;
        }
        
        // Track consecutive days for escalating bonus
        // Every pet tracks their own streak per player
        String streakKey = "petting_streak_" + player.getUuid().toString();
        Integer currentStreak = petComp.getStateData(streakKey, Integer.class);
        
        int newStreak;
        if (lastDay != null && lastDay == todayDay - 1) {
            // Continuing streak
            newStreak = (currentStreak != null ? currentStreak : 0) + 1;
        } else {
            // Streak broken or first time
            newStreak = 1;
        }
        
        // Update state
        petComp.setStateData(PetComponent.StateKeys.LAST_PETTING_DAY, todayDay);
        petComp.setStateData(streakKey, newStreak);
        
        // Calculate bonus based on streak milestones
        // Day 1-3: no bonus
        // Day 4-6: +1 bonus
        // Day 7-9: +2 bonus
        // Day 10-12: +3 bonus
        // etc. (every 3 days another level, or +1 per 3 days)
        int streakBonus = Math.max(0, (newStreak - 1) / 3);
        
        // Non-owners get reduced streak bonus (half)
        if (!isOwner) {
            streakBonus = (int) Math.ceil(streakBonus * 0.5);
        }
        
        int totalXp = baseXp + streakBonus;
        
        // Emit streak visual when milestone is hit (every 3 days)
        if (newStreak > 1 && (newStreak - 1) % 3 == 0) {
            ServerWorld world = (ServerWorld) pet.getEntityWorld();
            if (world != null) {
                FeedbackManager.emitFeedback("petting_streak", pet, world);
            }
            // Record gossip for notable streaks
            try {
                if (streakBonus >= 2) {
                    recordStreakGossip(player, pet, petComp, newStreak);
                }
            } catch (Throwable t) {
                Petsplus.LOGGER.debug("Failed to record petting streak rumor", t);
            }
        }

        return totalXp;
    }

    /**
     * Conditionally record a gossip rumor when a petting streak bonus occurs.
     * Uses the pet's imprint focus to influence chattiness. Randomness is seeded
     * consistently so the same pet behaves predictably but varies from one pet to another.
     */
    private static void recordStreakGossip(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, int bonus) {
        var imprint = petComp.getCharacteristicsModule().getImprint();
        float focusMultiplier = imprint != null ? imprint.getFocusMultiplier() : 1.0f;
        
        // Noise gate: base chance with focus imprint influence
        // Streak of 2 has modest chance; focus multiplier scales it up/down.
        double baseChance = 0.12 * Math.pow(1.08, bonus - 2.0);  // exponential ramp
        double focusAdjustedChance = baseChance * MathHelper.clamp(focusMultiplier, 0.8f, 1.2f);
        double cappedChance = Math.min(0.65, focusAdjustedChance);  // hard cap at 65%
        
        if (pet.getRandom().nextDouble() < cappedChance) {
            long topicId = GossipTopics.concrete("social/petting/large_streak");
            long tick = pet.getEntityWorld() != null ? pet.getEntityWorld().getTime() : 0L;
            
            // Intensity scales with streak, with seeded variance per pet
            float baseIntensity = 0.3f + 0.08f * Math.min(8f, bonus);
            float jitter = 0.7f + 0.3f * pet.getRandom().nextFloat();  // ±15% centered jitter
            float intensity = baseIntensity * jitter;
            intensity = MathHelper.clamp(intensity, 0.2f, 0.95f);
            
            // Confidence: modest base, boosted by streak length
            float confidence = 0.65f + 0.05f * Math.min(5, bonus);
            confidence = MathHelper.clamp(confidence, 0.5f, 0.9f);
            
            Text paraphrase = Text.translatable("petsplus.gossip.petting.large_streak", 
                player.getName().getString(), bonus);
            petComp.recordRumor(topicId, intensity, confidence, tick, player.getUuid(), paraphrase, true);
        }
    }

    private static String determinePetSound(MobEntity pet) {
        // Map entity types to appropriate sound effects for feedback system
        String entityType = pet.getType().toString();
        
        if (entityType.contains("wolf")) return "pet_wolf_happy";
        if (entityType.contains("cat")) return "pet_cat_purr";
        if (entityType.contains("parrot")) return "pet_parrot_chirp";
        if (entityType.contains("horse") || entityType.contains("donkey") || entityType.contains("mule")) return "pet_horse_snort";
        if (entityType.contains("llama")) return "pet_llama_spit"; // Playful
        if (entityType.contains("camel")) return "pet_camel_grumble";
        
        // Default for any other tameable
        return "pet_generic_happy";
    }

    private static String generatePettingMessage(MobEntity pet, int petCount) {
        String[] baseMessages = {
            "Your companion leans into your touch",
            "Gentle affection strengthens your bond",
            "Your pet's eyes sparkle with contentment",
            "A moment of pure connection",
            "Your companion's trust in you grows"
        };
        
        String[] frequentPetMessages = {
            "Your devoted companion knows every gentle touch",
            "This bond has been forged through countless loving moments",
            "Your pet anticipates your affection with quiet joy",
            "Years of gentle care shine in your companion's eyes"
        };
        
        if (petCount > 50) {
            return frequentPetMessages[petCount % frequentPetMessages.length];
        } else {
            return baseMessages[petCount % baseMessages.length];
        }
    }

    /**
     * Check if this entity is rideable (horse, donkey, llama, camel, etc.)
     */
    private static boolean isRideable(MobEntity mob) {
        String entityType = mob.getType().toString();
        return entityType.contains("horse") ||
               entityType.contains("donkey") ||
               entityType.contains("mule") ||
               entityType.contains("llama") ||
               entityType.contains("camel") ||
               entityType.contains("pig") ||
               entityType.contains("strider");
    }

    /**
     * Get the petting boost multiplier (e.g., 1.1 for 10% boost)
     * Note: Currently unused as buffs are disabled per user request
     */
    public static double getPettingBoostMultiplier() {
        return PetsPlusConfig.getInstance().getPettingBoostMultiplier();
    }

    private record PettingContext(int petCount, long worldTime) {}
}
