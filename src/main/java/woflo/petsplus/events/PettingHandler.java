package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import woflo.petsplus.Petsplus;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.FeedbackManager;

/**
 * Handles petting interactions with tamed pets.
 * Provides immersive feedback and role-specific bonuses when players pet their companions.
 */
public class PettingHandler {

    private static final String LAST_PET_TIME_KEY = "last_pet_time";
    private static final String PET_COUNT_KEY = "pet_count";

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
        if (!(mob instanceof TameableEntity tameable) || !tameable.isTamed()) return ActionResult.PASS;
        if (tameable.getOwner() != player) return ActionResult.PASS;

        PetComponent petComp = PetComponent.get(mob);
        if (petComp == null) return ActionResult.PASS;

        // Check cooldown
        long currentTime = world.getTime();
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int cooldownTicks = config.getPettingCooldownTicks();
        
        Long lastPetTime = petComp.getStateData(LAST_PET_TIME_KEY, Long.class);
        if (lastPetTime != null && (currentTime - lastPetTime) < cooldownTicks) {
            // Still on cooldown, but provide subtle feedback
            player.sendMessage(net.minecraft.text.Text.literal("Your companion is still enjoying the last pets"), true);
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
        petComp.setStateData(LAST_PET_TIME_KEY, currentTime);
        Integer currentCount = petComp.getStateData(PET_COUNT_KEY, Integer.class);
        int newCount = (currentCount == null ? 0 : currentCount) + 1;
        petComp.setStateData(PET_COUNT_KEY, newCount);

        // Base petting effects
        emitBasePettingEffects(player, pet, world, newCount);
        
        // Role-specific effects
        emitRoleSpecificEffects(player, pet, world, roleId);
        
        // Bonding benefits (small XP bonus, temporary buffs)
        applyBondingBenefits(player, pet, petComp);

        // Achievement tracking
        if (newCount >= 100) {
            woflo.petsplus.advancement.AdvancementManager.triggerPettingMilestone(player, newCount);
        }
        if (newCount == 1) {
            woflo.petsplus.advancement.AdvancementManager.triggerPettingMilestone(player, newCount);
        }
    }

    private static void emitBasePettingEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world, int petCount) {
        // Heart particles - more hearts for higher pet counts (caps at 10)
        FeedbackManager.emitFeedback("pet_hearts", pet, world);
        
        // Pet-appropriate sound based on entity type
        String soundEffect = determinePetSound(pet);
        FeedbackManager.emitFeedback(soundEffect, pet, world);
        
        // Action bar message with personality
        String message = generatePettingMessage(pet, petCount);
        player.sendMessage(net.minecraft.text.Text.literal(message), true);
    }

    private static void emitRoleSpecificEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world, Identifier roleId) {
        if (roleId.equals(PetRoleType.GUARDIAN_ID)) {
            FeedbackManager.emitFeedback("guardian_protection_stance", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your guardian stands proudly, ready to protect"), true);
        } else if (roleId.equals(PetRoleType.STRIKER_ID)) {
            FeedbackManager.emitFeedback("striker_eagerness", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your striker's eyes gleam with hunting intent"), true);
        } else if (roleId.equals(PetRoleType.SUPPORT_ID)) {
            FeedbackManager.emitFeedback("support_gentle_aura", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your support radiates warmth and comfort"), true);
        } else if (roleId.equals(PetRoleType.SCOUT_ID)) {
            FeedbackManager.emitFeedback("scout_alertness", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your scout's senses sharpen, ears perked"), true);
        } else if (roleId.equals(PetRoleType.SKYRIDER_ID)) {
            FeedbackManager.emitFeedback("skyrider_wind_dance", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("The air stirs around your skyrider"), true);
        } else if (roleId.equals(PetRoleType.ENCHANTMENT_BOUND_ID)) {
            FeedbackManager.emitFeedback("enchantment_sparkle", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Arcane energies swirl gently around your companion"), true);
        } else if (roleId.equals(PetRoleType.CURSED_ONE_ID)) {
            FeedbackManager.emitFeedback("cursed_dark_affection", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your cursed companion's eyes glow with twisted loyalty"), true);
        } else if (roleId.equals(PetRoleType.ECLIPSED_ID)) {
            FeedbackManager.emitFeedback("eclipsed_void_pulse", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Reality flickers around your eclipsed companion"), true);
        } else if (roleId.equals(PetRoleType.EEPY_EEPER_ID)) {
            FeedbackManager.emitFeedback("eepy_sleepy_contentment", pet, world);
            player.sendMessage(net.minecraft.text.Text.literal("Your sleepy companion purrs drowsily"), true);
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

    /**
     * Get the petting boost multiplier (e.g., 1.1 for 10% boost)
     * Note: Currently unused as buffs are disabled per user request
     */
    public static double getPettingBoostMultiplier() {
        return PetsPlusConfig.getInstance().getPettingBoostMultiplier();
    }
}