package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Handles UI feedback for PetsPlus abilities including action bar messages,
 * boss bar pulses, and localization.
 */
public class UIFeedbackManager {
    
    /**
     * Send an action bar message to the player using the contextual cue system.
     */
    public static void sendActionBarMessage(ServerPlayerEntity player, String messageKey, Object... args) {
        ActionBarCueManager.queueCue(player, ActionBarCueManager.ActionBarCue.of(messageKey, args));
    }

    /**
     * Send a contextual action bar message tied to a specific pet.
     */
    public static void sendActionBarMessage(ServerPlayerEntity player, MobEntity pet, String messageKey, Object... args) {
        ActionBarCueManager.ActionBarCue cue = ActionBarCueManager.ActionBarCue.of(messageKey, args)
            .withSource(ActionBarCueManager.ActionBarCueSource.forPet(pet));
        ActionBarCueManager.queueCue(player, cue);
    }
    
    /**
     * Send a regular chat message to the player.
     */
    public static void sendChatMessage(ServerPlayerEntity player, String messageKey, Object... args) {
        Text message = Text.translatable(messageKey, args).formatted(Formatting.DARK_GRAY);
        player.sendMessage(message, false); // false = chat
    }
    
    // Guardian messages
    public static void sendGuardianBulwarkMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.guardian.bulwark", petName);
    }
    
    public static void sendGuardianProjectileDRMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.guardian.projectile_dr");
    }
    
    // Striker messages
    public static void sendStrikerFinisherMarkMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.striker.finisher_mark", petName);
    }
    
    public static void sendStrikerExecutionMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.striker.execution");
    }
    
    // Support messages
    public static void sendSupportPotionSipMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.support.potion_sip");
    }
    
    public static void sendSupportConeAuraMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.support.cone_aura");
    }
    
    // Scout messages
    public static void sendScoutSpotterMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.scout.spotter", petName);
    }
    
    public static void sendScoutLootWispMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.scout.loot_wisp", petName);
    }
    
    public static void sendScoutGalePaceMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.scout.gale_pace");
    }
    
    // Skyrider messages
    public static void sendSkyriderWindlashMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.skyrider.windlash");
    }
    
    public static void sendSkyriderLevitationMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.skyrider.levitation");
    }
    
    // Enchantment-Bound messages
    public static void sendEnchantedHasteMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.enchanted.haste");
    }
    
    public static void sendEnchantedExtraRollMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.enchanted.extra_roll");
    }
    
    // Cursed One messages
    public static void sendCursedDoomEchoMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.cursed.doom_echo", petName);
    }
    
    public static void sendCursedHealMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.cursed.heal");
    }
    
    // Eepy Eeper messages
    public static void sendEepyNapTimeMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.eepy.nap_time", petName);
    }
    
    // Eclipsed messages
    public static void sendEclipsedVoidbrandMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.eclipsed.voidbrand", petName);
    }
    
    public static void sendEclipsedPhasePartnerMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.eclipsed.phase_partner");
    }
    
    public static void sendEclipsedEventHorizonMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.eclipsed.event_horizon");
    }
    
    public static void sendEclipsedEdgeStepMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.eclipsed.edge_step");
    }
    
    // General utility messages
    public static void sendAbilityCooldownMessage(ServerPlayerEntity player, String abilityName, int secondsRemaining) {
        sendActionBarMessage(player, "petsplus.cooldown", abilityName, secondsRemaining);
    }
    
    public static void sendAbilityReadyMessage(ServerPlayerEntity player, String abilityName) {
        sendActionBarMessage(player, "petsplus.ready", abilityName);
    }
}