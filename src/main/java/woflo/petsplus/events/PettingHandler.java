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
import woflo.petsplus.Petsplus;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.FeedbackManager;

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

        // Must be a tamed entity owned by this player
        if (!(mob instanceof PetsplusTameable tameable) || !tameable.petsplus$isTamed()) return ActionResult.PASS;
        if (tameable.petsplus$getOwner() != player) return ActionResult.PASS;

        // Skip petting for rideable entities when they could be mounted
        // Let vanilla handle mounting, then petting can happen when already mounted
        if (isRideable(mob) && !player.hasVehicle()) {
            return ActionResult.PASS;
        }

        PetComponent petComp = PetComponent.get(mob);
        if (petComp == null) return ActionResult.PASS;

        // Check cooldown
        long currentTime = world.getTime();
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int cooldownTicks = config.getPettingCooldownTicks();
        
        Long lastPetTime = petComp.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
        if (lastPetTime != null && (currentTime - lastPetTime) < cooldownTicks) {
            // Still on cooldown, but provide subtle feedback through the cue helper
            EmotionContextCues.sendCue(serverPlayer,
                "petting.cooldown." + mob.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.petting.cooldown", mob.getDisplayName()),
                80);
            return ActionResult.SUCCESS;
        }

        // Perform petting
        performPetting(serverPlayer, mob, petComp, currentTime);
        
        return ActionResult.SUCCESS;
    }

    private static void performPetting(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, long currentTime) {
        ServerWorld world = (ServerWorld) pet.getWorld();
        Identifier roleId = petComp.getRoleId();

        // Update petting state
        petComp.setStateData(PetComponent.StateKeys.LAST_PET_TIME, currentTime);
        Integer currentCount = petComp.getStateData(PetComponent.StateKeys.PET_COUNT, Integer.class);
        int newCount = (currentCount == null ? 0 : currentCount) + 1;
        petComp.setStateData(PetComponent.StateKeys.PET_COUNT, newCount);

        // Base petting effects
        emitBasePettingEffects(player, pet, world, newCount);

        // Role-specific effects
        emitRoleSpecificEffects(player, pet, world, roleId);

        // Bonding benefits (small XP bonus, temporary buffs)
        applyBondingBenefits(player, pet, petComp);

        emitPettingCues(player, pet, newCount);

        // Emotion push: affiliative uplift
        petComp.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.6f);
        petComp.pushEmotion(PetComponent.Emotion.UBUNTU, 0.4f);
        petComp.pushEmotion(PetComponent.Emotion.QUERECIA, 0.4f);
        petComp.updateMood();

        // Achievement tracking - fire interaction criterion
        woflo.petsplus.advancement.AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
            player,
            woflo.petsplus.advancement.criteria.PetInteractionCriterion.INTERACTION_PETTING,
            newCount
        );
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
            Text.literal(message),
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

        Text message = resolveMessage(petting.message(), "Your companion seems content.");
        if (!message.getString().isBlank()) {
            String cueId = "petting.role." + (roleId != null ? roleId.toString() : "default") + "." + pet.getUuidAsString();
            EmotionContextCues.sendCue(player, cueId, message, 100);
        }
    }

    private static void emitPettingCues(ServerPlayerEntity player, MobEntity pet, int petCount) {
        EmotionContextCues.sendCue(player,
            "petting.affection." + pet.getUuidAsString(),
            Text.translatable("petsplus.emotion_cue.petting.affection", pet.getDisplayName()),
            80);

        if (petCount == 1) {
            EmotionContextCues.sendCue(player,
                "petting.first." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.petting.first", pet.getDisplayName()),
                6000);
        } else if (petCount % 25 == 0) {
            EmotionContextCues.sendCue(player,
                "petting.milestone." + pet.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.petting.milestone", pet.getDisplayName(), petCount),
                6000);
        }
    }

    private static void applyBondingBenefits(ServerPlayerEntity player, MobEntity pet, PetComponent petComp) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        
        // Small XP bonus based on pet level and role (only actual benefit)
        int xpBonus = Math.max(1, petComp.getLevel() / 5);
        if (config.isPettingXpBonusEnabled()) {
            petComp.addExperience(xpBonus);
        }
        
        // Note: No buffs/debuffs per user request - purely cosmetic with XP gain only
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

    private static Text resolveMessage(PetRoleType.Message message, String fallback) {
        if (message != null) {
            String key = message.translationKey();
            String fallbackText = message.fallback();
            if (key != null && !key.isBlank()) {
                if (Language.getInstance().hasTranslation(key)) {
                    return Text.translatable(key);
                }
                if (fallbackText != null && !fallbackText.isBlank()) {
                    return Text.literal(fallbackText);
                }
                return Text.translatable(key);
            }
            if (fallbackText != null && !fallbackText.isBlank()) {
                return Text.literal(fallbackText);
            }
        }
        return (fallback == null || fallback.isBlank()) ? Text.empty() : Text.literal(fallback);
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
}