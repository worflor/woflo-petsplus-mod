package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

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

    public static void sendGuardianFortressMessage(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.guardian.fortress", pet.getDisplayName());
    }

    public static void sendGuardianFortressFadeMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.guardian.fortress_fade");
    }

    public static void sendGuardianFortressOutOfRangeMessage(ServerPlayerEntity player, @Nullable MobEntity pet) {
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.guardian.fortress_break", pet.getDisplayName());
        } else {
            sendActionBarMessage(player, "petsplus.guardian.fortress_break_generic");
        }
    }

    public static void sendGuardianFortressPetDownMessage(ServerPlayerEntity player, @Nullable MobEntity pet) {
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.guardian.fortress_down", pet.getDisplayName());
        } else {
            sendActionBarMessage(player, "petsplus.guardian.fortress_down_generic");
        }
    }

    public static void sendGuardianFortressDimensionMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.guardian.fortress_dim");
    }

    public static void sendGuardianAegisMessage(ServerPlayerEntity player, MobEntity pet, int stacks) {
        sendActionBarMessage(player, pet, "petsplus.guardian.aegis", pet.getDisplayName(), stacks);
    }
    
    // Striker messages
    public static void sendStrikerFinisherMarkMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.striker.finisher_mark", petName);
    }

    public static void sendStrikerHuntFocusMessage(ServerPlayerEntity player, String targetName,
                                                   float thresholdPct, int momentumStacks) {
        int displayThreshold = MathHelper.clamp(Math.round(thresholdPct * 100f), 1, 100);
        sendActionBarMessage(player, "petsplus.striker.hunt_focus", targetName,
                displayThreshold, Math.max(0, momentumStacks));
    }

    public static void sendStrikerExecutionMessage(ServerPlayerEntity player, float thresholdPct, int stacks) {
        int displayThreshold = Math.round(thresholdPct * 100f);
        sendActionBarMessage(player, "petsplus.striker.execution", displayThreshold, Math.max(0, stacks));
    }

    public static void sendStrikerBloodlustMessage(ServerPlayerEntity player, int stacks, int durationSeconds) {
        sendActionBarMessage(player, "petsplus.striker.bloodlust", Math.max(1, stacks), Math.max(1, durationSeconds));
    }

    public static void sendStrikerMarkExpiryWarning(ServerPlayerEntity player, String targetName) {
        sendActionBarMessage(player, "petsplus.striker.mark_warning", targetName);
    }

    public static void sendStrikerMarkLostMessage(ServerPlayerEntity player, String targetName) {
        sendActionBarMessage(player, "petsplus.striker.mark_lost", targetName);
    }

    public static void sendStrikerMomentumWarning(ServerPlayerEntity player, int stacks) {
        sendActionBarMessage(player, "petsplus.striker.momentum_warning", Math.max(1, stacks));
    }

    public static void sendStrikerFinisherConfirmMessage(ServerPlayerEntity player, String targetName) {
        sendActionBarMessage(player, "petsplus.striker.finisher_confirm", targetName);
    }

    public static void sendStrikerMarkSpentMessage(ServerPlayerEntity player, String targetName) {
        sendActionBarMessage(player, "petsplus.striker.mark_spent", targetName);
    }
    
    // Support messages
    public static void sendSupportPotionSipMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.support.potion_sip");
    }

    public static void sendSupportPotionLocked(ServerPlayerEntity player, MobEntity pet, int requiredLevel) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_locked", pet.getDisplayName(), Math.max(1, requiredLevel));
    }

    public static void sendSupportPotionNeedsSitting(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_needs_sit", pet.getDisplayName());
    }

    public static void sendSupportPotionEmpty(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_empty", pet.getDisplayName());
    }

    public static void sendSupportPotionPulse(ServerPlayerEntity player, MobEntity pet, int alliesAffected,
                                              int durationSeconds) {
        int allyCount = Math.max(0, alliesAffected);
        int seconds = Math.max(1, durationSeconds);
        sendActionBarMessage(player, pet, "petsplus.support.potion_pulse", pet.getDisplayName(), allyCount, seconds);
    }

    public static void sendSupportPotionAssist(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_assist", pet.getDisplayName());
    }

    public static void sendSupportPotionRhythm(ServerPlayerEntity player, MobEntity pet, int streak) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_rhythm", pet.getDisplayName(), Math.max(1, streak));
    }

    public static void sendSupportPotionClutch(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_clutch", pet.getDisplayName());
    }

    public static void sendSupportPotionComfort(ServerPlayerEntity player, MobEntity pet) {
        sendActionBarMessage(player, pet, "petsplus.support.potion_comfort", pet.getDisplayName());
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

    public static void sendSkyriderGustMessage(ServerPlayerEntity player, @Nullable MobEntity pet,
                                               int slowfallSeconds) {
        int safeSeconds = Math.max(1, slowfallSeconds);
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.skyrider.gust", nameArg, safeSeconds);
        } else {
            sendActionBarMessage(player, "petsplus.skyrider.gust", nameArg, safeSeconds);
        }
    }
    
    // Enchantment-Bound messages
    public static void sendEnchantedHasteMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.enchanted.haste");
    }

    public static void sendEnchantedExtraRollMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.enchanted.extra_roll");
    }

    public static void sendEnchantmentStripSuccess(ServerPlayerEntity player, @Nullable MobEntity pet,
                                                   Text enchantName, Text itemName) {
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.enchanted.enchant_strip_success", nameArg, enchantName, itemName);
        } else {
            sendActionBarMessage(player, "petsplus.enchanted.enchant_strip_success", nameArg, enchantName, itemName);
        }
    }

    public static void sendEnchantmentStripNoTarget(ServerPlayerEntity player, @Nullable MobEntity pet) {
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.enchanted.enchant_strip_fail_none", nameArg);
        } else {
            sendActionBarMessage(player, "petsplus.enchanted.enchant_strip_fail_none", nameArg);
        }
    }

    public static void sendEnchantmentStripInsufficientXp(ServerPlayerEntity player, int requiredLevels) {
        sendActionBarMessage(player, "petsplus.enchanted.enchant_strip_fail_xp", Math.max(1, requiredLevels));
    }

    public static void sendEnchantmentGearSwapStored(ServerPlayerEntity player, @Nullable MobEntity pet) {
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.enchanted.gear_swap_store", nameArg);
        } else {
            sendActionBarMessage(player, "petsplus.enchanted.gear_swap_store", nameArg);
        }
    }

    public static void sendEnchantmentGearSwapSwapped(ServerPlayerEntity player, @Nullable MobEntity pet) {
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.enchanted.gear_swap_swap", nameArg);
        } else {
            sendActionBarMessage(player, "petsplus.enchanted.gear_swap_swap", nameArg);
        }
    }
    
    // Cursed One messages
    public static void sendCursedDoomEchoMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.cursed.doom_echo", petName);
    }

    public static void sendCursedHealMessage(ServerPlayerEntity player) {
        sendActionBarMessage(player, "petsplus.cursed.heal");
    }

    public static void sendCursedSoulSacrificeActivated(ServerPlayerEntity player, MobEntity pet,
                                                        int levelsSpent, int seconds) {
        int safeLevels = Math.max(0, levelsSpent);
        int safeSeconds = Math.max(1, seconds);
        Object nameArg = pet != null ? pet.getDisplayName() : player.getDisplayName();
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.cursed.soul_sacrifice", nameArg, safeLevels, safeSeconds);
        } else {
            sendActionBarMessage(player, "petsplus.cursed.soul_sacrifice", nameArg, safeLevels, safeSeconds);
        }
    }

    public static void sendCursedSoulSacrificeInsufficientXp(ServerPlayerEntity player, int requiredLevels) {
        sendActionBarMessage(player, "petsplus.cursed.soul_sacrifice_fail", Math.max(1, requiredLevels));
    }

    public static void sendCursedReanimationExtended(ServerPlayerEntity player, MobEntity pet, int seconds) {
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.cursed.reanimation_longer",
                pet.getDisplayName(), Math.max(1, seconds));
        } else {
            sendActionBarMessage(player, "petsplus.cursed.reanimation_longer_generic", Math.max(1, seconds));
        }
    }

    public static void sendCursedDeathBurstMessage(ServerPlayerEntity player, MobEntity pet) {
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.cursed.death_burst", pet.getDisplayName());
        } else {
            sendActionBarMessage(player, "petsplus.cursed.death_burst_generic");
        }
    }

    public static void sendCursedReanimationBurstMessage(ServerPlayerEntity player, MobEntity pet) {
        if (pet != null) {
            sendActionBarMessage(player, pet, "petsplus.cursed.reanimation_burst", pet.getDisplayName());
        } else {
            sendActionBarMessage(player, "petsplus.cursed.reanimation_burst_generic");
        }
    }

    // Eepy Eeper messages
    public static void sendEepyNapTimeMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.eepy.nap_time", petName);
    }

    public static void sendEepyDrowsyMistMessage(ServerPlayerEntity player, String petName) {
        sendActionBarMessage(player, "petsplus.eepy.drowsy_mist", petName);
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