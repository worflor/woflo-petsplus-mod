package woflo.petsplus.advancement;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import woflo.petsplus.state.PetComponent;

/**
 * Manages advancement triggering for Pets+ mod using vanilla advancement system.
 * In 1.21.8, advancements are data-driven through JSON files with criteria and triggers.
 * This class provides simple trigger methods that work with the advancement system.
 */
public class AdvancementManager {

    public static void triggerFirstPetBond(ServerPlayerEntity player) {
        triggerAdvancement(player, "petsplus:first_pet");
    }

    public static void triggerMilestoneUnlock(ServerPlayerEntity player, int level) {
        if (level == 10) {
            triggerAdvancement(player, "petsplus:trial_ready");
        }
    }

    public static void triggerRoleMilestone(ServerPlayerEntity player, String role, int level) {
        if ("petsplus:skyrider".equals(role) && level == 20) {
            triggerAdvancement(player, "petsplus:melody_wind");
        } else if ("petsplus:eclipsed".equals(role) && level == 30) {
            triggerAdvancement(player, "petsplus:edgewalker");
        }
    }

    public static void triggerConfiguredAdvancement(ServerPlayerEntity player, Identifier advancementId) {
        if (player == null || advancementId == null) {
            return;
        }
        triggerAdvancement(player, advancementId.toString());
    }

    public static void triggerPetLevel30(ServerPlayerEntity player, MobEntity pet) {
        // In 1.21.8, this should be handled by custom criteria in advancement JSON
        // For now, just trigger the advancement directly
        triggerAdvancement(player, "petsplus:even_bester");
    }

    public static void triggerPetPermanentDeath(ServerPlayerEntity player, MobEntity pet) {
        // In 1.21.8, this complex logic should be handled by custom criteria
        // The advancement JSON should define the conditions for when this triggers
        triggerAdvancement(player, "petsplus:or_not");
    }

    public static void triggerGuardianTankDamage(ServerPlayerEntity player, MobEntity pet, float damage) {
        // In 1.21.8, damage accumulation should be tracked by custom criteria
        // For now, we'll trigger on each damage event and let the advancement 
        // JSON handle the accumulation logic
        triggerAdvancement(player, "petsplus:sacrilege");
    }

    public static void triggerSupportHealAllies(ServerPlayerEntity player, PlayerEntity healedAlly) {
        if (player == null || healedAlly == null) {
            return;
        }

        // In 1.21.8, tracking unique allies healed should be done via custom criteria
        triggerAdvancement(player, "petsplus:mmm_healing_magic");
    }

    public static void triggerAbilityMaxRank(ServerPlayerEntity player) {
        triggerAdvancement(player, "petsplus:is_this_designer");
    }

    public static void triggerStargazeTimeout(ServerPlayerEntity player) {
        triggerAdvancement(player, "petsplus:i_love_you_and_me");
    }

    public static void triggerDreamEscape(ServerPlayerEntity player) {
        // In 1.21.8, counting dream escapes should be handled by custom criteria
        triggerAdvancement(player, "petsplus:noo_luna");
        // Additional advancements (at_what_cost, heartless_but_alive) should be 
        // triggered by their own criteria based on dream escape count
    }

    public static void triggerPettingMilestone(ServerPlayerEntity player, int petCount) {
        if (petCount == 1) {
            triggerAdvancement(player, "petsplus:gentle_touch");
        } else if (petCount >= 100) {
            triggerAdvancement(player, "petsplus:devoted_companion");
        }
    }

    public static void triggerRestlessRelax(ServerPlayerEntity player) {
        triggerAdvancement(player, "petsplus:restless_sit_glow");
    }

    public static void triggerAngryCooldown(ServerPlayerEntity player) {
        triggerAdvancement(player, "petsplus:are_you_mad_at_me");
    }

    public static void triggerMoodLevelThree(ServerPlayerEntity player, PetComponent.Mood mood) {
        if (player == null || mood == null) {
            return;
        }

        switch (mood) {
            case HAPPY -> triggerAdvancement(player, "petsplus:here_comes_the_sunbeam");
            case PLAYFUL -> triggerAdvancement(player, "petsplus:please_fcking_play_with_me_now");
            case CURIOUS -> triggerAdvancement(player, "petsplus:indiana_bones_zoomies");
            case BONDED -> triggerAdvancement(player, "petsplus:bond_voyage");
            case CALM -> triggerAdvancement(player, "petsplus:be_more_chilllllll");
            case PASSIONATE -> triggerAdvancement(player, "petsplus:hearts_on_pyre");
            case YUGEN -> triggerAdvancement(player, "petsplus:yugen_sigh_sigh");
            case SISU -> triggerAdvancement(player, "petsplus:finnish_him");
            case SAUDADE -> triggerAdvancement(player, "petsplus:wish_you_were_here_fur_real");
            case PROTECTIVE -> triggerAdvancement(player, "petsplus:guardian_of_the_grrr_laxy");
            case AFRAID -> triggerAdvancement(player, "petsplus:afraidiana_grande");
            default -> {
                // Other moods do not currently award direct level-three advancements.
            }
        }
    }

    /**
     * Triggers an advancement for a player.
     * In 1.21.8, this works with data-driven advancement JSON files.
     */
    private static void triggerAdvancement(ServerPlayerEntity player, String advancementId) {
        try {
            AdvancementEntry advancement = player.getServer().getAdvancementLoader()
                .get(Identifier.of(advancementId));
            if (advancement != null) {
                // Use a generic trigger criterion name
                // The actual criteria are defined in the advancement JSON files
                player.getAdvancementTracker().grantCriterion(advancement, "trigger");
            }
        } catch (Exception e) {
            // Log error but don't crash
            woflo.petsplus.Petsplus.LOGGER.warn("Failed to trigger advancement {}: {}", advancementId, e.getMessage());
        }
    }
}

