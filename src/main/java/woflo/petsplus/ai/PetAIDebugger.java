package woflo.petsplus.ai;

import java.util.Locale;

import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

/**
 * Utility class for debugging and testing pet AI enhancements.
 */
public class PetAIDebugger {
    
    /**
     * Log current AI and pathfinding state of a pet for debugging.
     */
    public static void logPetAIState(MobEntity pet) {
        if (pet.getEntityWorld().isClient()) return;
        
        PetComponent petComponent = PetComponent.get(pet);
        if (petComponent == null) {
            Petsplus.LOGGER.info("Pet {} has no PetComponent", pet.getType());
            return;
        }
        
        Petsplus.LOGGER.info("=== Pet AI State Debug ===");
        Petsplus.LOGGER.info("Pet Type: {}", pet.getType());
        Identifier roleId = petComponent.getRoleId();
        Petsplus.LOGGER.info("Role: {}", roleId != null ? roleId.getPath() : "<none>");
        Petsplus.LOGGER.info("Is Tamed: {}", pet instanceof PetsplusTameable tameable ? tameable.petsplus$isTamed() : "N/A");
        Petsplus.LOGGER.info("Current Mood: {} (level {})", petComponent.getCurrentMood(), petComponent.getMoodLevel());
        float focusedStrength = petComponent.getMoodStrength(PetComponent.Mood.FOCUSED);
        float yugenStrength = petComponent.getMoodStrength(PetComponent.Mood.YUGEN);
        float saudadeStrength = petComponent.getMoodStrength(PetComponent.Mood.SAUDADE);
        float passionateStrength = petComponent.getMoodStrength(PetComponent.Mood.PASSIONATE);
        float playfulStrength = petComponent.getMoodStrength(PetComponent.Mood.PLAYFUL);
        Petsplus.LOGGER.info(
            "Mood Strengths - Focused: {}, Passionate: {}, Playful: {}, Yugen: {}, Saudade: {}",
            Math.round(focusedStrength * 100f) / 100f,
            Math.round(passionateStrength * 100f) / 100f,
            Math.round(playfulStrength * 100f) / 100f,
            Math.round(yugenStrength * 100f) / 100f,
            Math.round(saudadeStrength * 100f) / 100f
        );

        // Log pathfinding penalties
        Petsplus.LOGGER.info("Pathfinding Penalties:");
        logPathfindingPenalty(pet, PathNodeType.WATER, "Water");
        logPathfindingPenalty(pet, PathNodeType.LAVA, "Lava");
        logPathfindingPenalty(pet, PathNodeType.FENCE, "Fence");
        logPathfindingPenalty(pet, PathNodeType.DOOR_WOOD_CLOSED, "Wooden Door");
        logPathfindingPenalty(pet, PathNodeType.DANGER_OTHER, "Danger");
        logPathfindingPenalty(pet, PathNodeType.DAMAGE_OTHER, "Damage");

        double spacingOffsetX = petComponent.getFollowSpacingOffsetX();
        double spacingOffsetZ = petComponent.getFollowSpacingOffsetZ();
        float spacingPadding = petComponent.getFollowSpacingPadding();
        long sampleTick = petComponent.getFollowSpacingSampleTick();
        Petsplus.LOGGER.info(
            "Follow Spacing Offset: ({}, {}) padding={} lastSampleTick={}",
            formatDouble(spacingOffsetX),
            formatDouble(spacingOffsetZ),
            formatDouble(spacingPadding),
            sampleTick == Long.MIN_VALUE ? "never" : Long.toString(sampleTick)
        );

        // Log goal counts
        try {
            woflo.petsplus.mixin.MobEntityAccessor accessor = (woflo.petsplus.mixin.MobEntityAccessor) pet;
            int goalCount = accessor.getGoalSelector().getGoals().size();
            int targetCount = accessor.getTargetSelector().getGoals().size();
            
            Petsplus.LOGGER.info("Goal Count: {}", goalCount);
            Petsplus.LOGGER.info("Target Goal Count: {}", targetCount);
            
            // Log enhanced follow goal status
            boolean hasAdaptiveFollow = accessor.getGoalSelector().getGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof woflo.petsplus.ai.goals.follow.FollowOwnerAdaptiveGoal);
            Petsplus.LOGGER.info("Has Adaptive Follow Goal: {}", hasAdaptiveFollow);
            
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to access goal selectors: {}", e.getMessage());
        }
        
        Petsplus.LOGGER.info("========================");
    }
    
    private static void logPathfindingPenalty(MobEntity pet, PathNodeType nodeType, String name) {
        float penalty = pet.getPathfindingPenalty(nodeType);
        Petsplus.LOGGER.info("  {}: {}", name, penalty);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}


