package woflo.petsplus.ai;

import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

/**
 * Utility class for debugging and testing pet AI enhancements.
 */
public class PetAIDebugger {
    
    /**
     * Log current AI and pathfinding state of a pet for debugging.
     */
    public static void logPetAIState(MobEntity pet) {
        if (pet.getWorld().isClient) return;
        
        PetComponent petComponent = PetComponent.get(pet);
        if (petComponent == null) {
            Petsplus.LOGGER.info("Pet {} has no PetComponent", pet.getType());
            return;
        }
        
        String roleId = petComponent.getRoleId().getPath();
        
        Petsplus.LOGGER.info("=== Pet AI State Debug ===");
        Petsplus.LOGGER.info("Pet Type: {}", pet.getType());
        Petsplus.LOGGER.info("Role: {}", roleId);
        Petsplus.LOGGER.info("Is Tamed: {}", pet instanceof TameableEntity tameable ? tameable.isTamed() : "N/A");
        
        // Log pathfinding penalties
        Petsplus.LOGGER.info("Pathfinding Penalties:");
        logPathfindingPenalty(pet, PathNodeType.WATER, "Water");
        logPathfindingPenalty(pet, PathNodeType.LAVA, "Lava");
        logPathfindingPenalty(pet, PathNodeType.FENCE, "Fence");
        logPathfindingPenalty(pet, PathNodeType.DOOR_WOOD_CLOSED, "Wooden Door");
        logPathfindingPenalty(pet, PathNodeType.DANGER_OTHER, "Danger");
        logPathfindingPenalty(pet, PathNodeType.DAMAGE_OTHER, "Damage");
        
        // Log goal counts
        try {
            woflo.petsplus.mixin.MobEntityAccessor accessor = (woflo.petsplus.mixin.MobEntityAccessor) pet;
            int goalCount = accessor.getGoalSelector().getGoals().size();
            int targetCount = accessor.getTargetSelector().getGoals().size();
            
            Petsplus.LOGGER.info("Goal Count: {}", goalCount);
            Petsplus.LOGGER.info("Target Goal Count: {}", targetCount);
            
            // Log enhanced follow goal status
            boolean hasEnhancedFollow = accessor.getGoalSelector().getGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof woflo.petsplus.ai.goals.EnhancedFollowOwnerGoal);
            Petsplus.LOGGER.info("Has Enhanced Follow Goal: {}", hasEnhancedFollow);
            
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to access goal selectors: {}", e.getMessage());
        }
        
        Petsplus.LOGGER.info("========================");
    }
    
    private static void logPathfindingPenalty(MobEntity pet, PathNodeType nodeType, String name) {
        float penalty = pet.getPathfindingPenalty(nodeType);
        Petsplus.LOGGER.info("  {}: {}", name, penalty);
    }
}