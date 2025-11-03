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
            int playerXp = calculatePlayerPettingXp(player, pet, petComp);
            if (playerXp > 0) {
                player.addExperience(playerXp);
                if (world != null) {
                    FeedbackManager.emitFeedback("petting_xp", pet, world);
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

        // Immediate emotion stimulus so pet reacts (tier-based on streak, or base if no streak)
        try {
            var rel = petComp.getRelationshipWith(player.getUuid());
            float affection = rel != null ? rel.affection() : 0.0f;
            float ownerMultiplier = petComp.isOwnedBy(player) ? 1.0f : (0.45f + Math.max(0f, affection) * 0.55f);
            
            // Check if we have an active streak to use tier emotions instead
            String streakKey = "petting_streak_" + player.getUuid().toString();
            Integer currentStreak = petComp.getStateData(streakKey, Integer.class);
            int streak = (currentStreak != null) ? currentStreak : 0;
            
            if (streak >= 3) {
                // Use nuanced streak-tier emotions (and emit visual+quip on notable milestones)
                emitStreakTierEmotions(pet, streak, petComp.isOwnedBy(player));

                if (streak == 3 || streak == 7 || streak == 14 || streak % 10 == 0) {
                    if (world != null) {
                        FeedbackManager.emitFeedback("petting_streak", pet, world);
                        sendStreakQuip(player, pet, streak);
                    }
                }
            } else {
                // Base emotions for new/broken streaks
                MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                    collector.pushEmotion(PetComponent.Emotion.CONTENT, 0.18f * ownerMultiplier);
                    collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.08f * ownerMultiplier);
                    collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.06f * ownerMultiplier);
                });
            }
        } catch (Throwable t) {
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
            ServerWorld world = (ServerWorld) pet.getEntityWorld();
            if (world != null) {
                // Simple XP feedback (one particle type)
                FeedbackManager.emitFeedback("petting_xp", pet, world);
                // Inform player via action bar
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
        
        // Track consecutive days for scaling bonus
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
        
        // Infinite scaling with logarithmic falloff for balance
        // Days 1-10: Fast growth (~+0.33 XP per day)
        // Days 11-50: Moderate growth (~+0.15 XP per day)
        // Days 50+: Slow asymptotic growth (approaches ~+0.05 XP per day)
        // Formula: 1 + 0.4 * log₁₀(streak + 1)
        double streakBonus = 1.0 + 0.4 * Math.log(newStreak + 1) / Math.log(10.0);
        
        // Non-owners get half bonus (fairer for stranger petting)
        if (!isOwner) {
            streakBonus *= 0.5;
        }
        
        int totalXp = baseXp + (int) Math.round(streakBonus);
        
        // Record gossip for notable streaks (when bonus is impressive)
        try {
            if (streakBonus >= 1.5) {
                recordStreakGossip(player, pet, petComp, newStreak);
            }
        } catch (Throwable t) {
            Petsplus.LOGGER.debug("Failed to record petting streak rumor", t);
        }

        return totalXp;
    }
    
    /**
     * Send action bar quips for petting streak milestones.
     */
    private static void sendStreakQuip(ServerPlayerEntity player, MobEntity pet, int streakDays) {
        String quip;
        if (streakDays == 3) {
            quip = "✨ " + pet.getDisplayName().getString() + " feels your care!";
        } else if (streakDays == 7) {
            quip = "💕 A full week! " + pet.getDisplayName().getString() + " adores you!";
        } else if (streakDays == 14) {
            quip = "🔥 Two weeks straight! Your bond is unbreakable!";
        } else if (streakDays == 30) {
            quip = "⭐ A whole month! This is true companionship!";
        } else if (streakDays % 10 == 0) {
            quip = "🌟 Day " + streakDays + "! Your connection is legendary!";
        } else {
            quip = "💫 Streak milestone reached: Day " + streakDays;
        }
        
        EmotionContextCues.sendCue(player,
            "petting.streak.quip." + pet.getUuidAsString(),
            pet,
            Text.literal(quip),
            0);
    }
    
    /**
     * Emit nuanced emotions based on streak tier, not role.
     * Low streak = playfulness (yay, fun!)
     * Mid streak = loyalty + kefi (this matters to me)
     * High streak = bonded + pride (special connection)
     */
    private static void emitStreakTierEmotions(MobEntity pet, int streakDays, boolean isOwner) {
        float ownerMult = isOwner ? 1.0f : 0.6f;  // non-owners get weaker emotions
        
        try {
            if (streakDays <= 6) {
                // Early streak: playfulness & joy
                MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                    collector.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.25f * ownerMult);
                    collector.pushEmotion(PetComponent.Emotion.KEFI, 0.12f * ownerMult);  // exuberance
                });
            } else if (streakDays <= 20) {
                // Mid streak: loyalty & pride in the routine
                MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                    collector.pushEmotion(PetComponent.Emotion.LOYALTY, 0.22f * ownerMult);
                    collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.15f * ownerMult);
                    collector.pushEmotion(PetComponent.Emotion.UBUNTU, 0.08f * ownerMult);  // connection
                });
            } else {
                // High streak: deep bonded connection + pride
                MoodService.getInstance().getStimulusBus().queueSimpleStimulus(pet, collector -> {
                    collector.pushEmotion(PetComponent.Emotion.YUGEN, 0.18f * ownerMult);    // profound beauty
                    collector.pushEmotion(PetComponent.Emotion.QUERECIA, 0.16f * ownerMult); // serenity
                    collector.pushEmotion(PetComponent.Emotion.PRIDE, 0.12f * ownerMult);
                    collector.pushEmotion(PetComponent.Emotion.LOYALTY, 0.10f * ownerMult);
                });
            }
        } catch (Throwable t) {
            Petsplus.LOGGER.debug("Failed to emit streak tier emotions", t);
        }
    }

    /**
     * Record gossip rumor for notable streaks (simplified).
     */
    private static void recordStreakGossip(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, int streakDays) {
        // Simplified gossip: only notable streaks (7+) have a small, fixed chance to become gossip
        if (streakDays < 7) return;

        double chance = 0.12; // modest chance to avoid spam
        if (pet.getRandom().nextDouble() >= chance) return;

        long topicId = GossipTopics.concrete("social/petting/large_streak");
        long tick = pet.getEntityWorld() != null ? pet.getEntityWorld().getTime() : 0L;

        float intensity = MathHelper.clamp(0.3f + 0.03f * (streakDays - 7), 0.3f, 0.75f);
        float confidence = 0.65f;

        Text paraphrase = Text.translatable("petsplus.gossip.petting.large_streak",
            player.getName().getString(), streakDays);
        petComp.recordRumor(topicId, intensity, confidence, tick, player.getUuid(), paraphrase, true);
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
     * Get the petting boost multiplier (e.g., 1.1 for 10% boost).
     * Currently unused as buffs are disabled.
     */
    public static double getPettingBoostMultiplier() {
        return PetsPlusConfig.getInstance().getPettingBoostMultiplier();
    }

    private record PettingContext(int petCount, long worldTime) {}
}
