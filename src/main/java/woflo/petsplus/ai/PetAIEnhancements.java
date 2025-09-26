package woflo.petsplus.ai;

import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.Petsplus;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.ai.goals.CrouchCuddleGoal;
import woflo.petsplus.ai.goals.EnhancedFollowOwnerGoal;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;

/**
 * Modular AI enhancements for pets in the PetsPlus system.
 * Provides improved following, pathfinding, and behavior modifications.
 */
public class PetAIEnhancements {
    
    /**
     * Apply AI enhancements to a pet based on their role and characteristics.
     * This is called when a pet is registered or when their role changes.
     */
    public static void enhancePetAI(MobEntity pet, PetComponent petComponent) {
        if (pet.getWorld().isClient) return;
        
        try {
            // Enhanced follow behavior for all pets
            addEnhancedFollowGoal(pet, petComponent);

            // Owner assist targeting to mirror vanilla wolf combat
            addOwnerAssistGoal(pet, petComponent);

            // Crouch cuddle handshake keeps pets cozy near crouching owners
            addCrouchCuddleGoal(pet, petComponent);
            
            // Improved pathfinding penalties
            adjustPathfindingPenalties(pet, petComponent);
            
            // Role-specific AI adjustments
            applyRoleSpecificAI(pet, petComponent);

            // Mood-based AI goals - new modular system
            MoodBasedAIManager.initializeMoodAI(pet);
            
            // Subtle mood-influenced behaviors - fidgets, movement variations, etc.
            MoodInfluencedBehaviors.addMoodBehaviors(pet);
            
            // Advanced mood-influenced vanilla AI enhancements
            MoodAdvancedAI.enhanceVanillaAI(pet);

        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to enhance AI for pet {}: {}", pet.getType(), e.getMessage());
        }
    }
    
    /**
     * Add enhanced follow goal that's smarter about distances and obstacles.
     */
    private static void addEnhancedFollowGoal(MobEntity pet, PetComponent petComponent) {
        if (!(pet instanceof TameableEntity tameable)) return;

        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        
        // Reset temporary assist flags each time AI is re-applied
        OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
        OwnerAssistAttackGoal.clearAssistRegroup(petComponent);

        // Remove existing follow goals to replace with enhanced version
        accessor.getGoalSelector().getGoals().removeIf(goal ->
            goal.getGoal() instanceof FollowOwnerGoal);

        // Add enhanced follow goal with role-based parameters
        float followDistance = getFollowDistance(petComponent);
        float teleportDistance = getTeleportDistance(petComponent);

        accessor.getGoalSelector().add(5, new EnhancedFollowOwnerGoal(tameable, petComponent, 1.0,
            followDistance, teleportDistance, false));
    }

    private static void addOwnerAssistGoal(MobEntity pet, PetComponent petComponent) {
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        accessor.getTargetSelector().getGoals().removeIf(entry -> entry.getGoal() instanceof OwnerAssistAttackGoal);
        accessor.getTargetSelector().add(2, new OwnerAssistAttackGoal(pet, petComponent));
    }

    private static void addCrouchCuddleGoal(MobEntity pet, PetComponent petComponent) {
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        accessor.getGoalSelector().getGoals().removeIf(entry -> entry.getGoal() instanceof CrouchCuddleGoal);
        accessor.getGoalSelector().add(4, new CrouchCuddleGoal(pet, petComponent));
    }
    
    /**
     * Adjust pathfinding penalties based on pet characteristics.
     */
    private static void adjustPathfindingPenalties(MobEntity pet, PetComponent petComponent) {
        // Pets are more willing to walk through water to reach their owner
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, 0.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER_BORDER, 0.0f);
        
        // Reduce lava penalty slightly (but still dangerous)
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LAVA, 12.0f);
        
        // Make pets less afraid of fence gates and doors when following
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.FENCE, -1.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DOOR_WOOD_CLOSED, 0.0f);
        
        // Scout pets get enhanced mobility
        if (petComponent.getRoleId().getPath().equals("scout")) {
            pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 2.0f);
            pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.STICKY_HONEY, 4.0f);
        }
    }
    
    /**
     * Apply role-specific AI enhancements.
     */
    private static void applyRoleSpecificAI(MobEntity pet, PetComponent petComponent) {
        String roleId = petComponent.getRoleId().getPath();
        
        switch (roleId) {
            case "guardian" -> applyGuardianAI(pet, petComponent);
            case "scout" -> applyScoutAI(pet, petComponent);
            case "striker" -> applyStrikerAI(pet, petComponent);
            case "support" -> applySupportAI(pet, petComponent);
        }
    }
    
    private static void applyGuardianAI(MobEntity pet, PetComponent petComponent) {
        // Guardians get enhanced defensive pathfinding
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 8.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 4.0f);
    }
    
    private static void applyScoutAI(MobEntity pet, PetComponent petComponent) {
        // Scouts are more mobile and explore further
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        
        // Enable scout mode on enhanced follow goal
        accessor.getGoalSelector().getGoals().stream()
            .filter(goal -> goal.getGoal() instanceof EnhancedFollowOwnerGoal)
            .forEach(goal -> ((EnhancedFollowOwnerGoal) goal.getGoal()).setScoutMode(true));
    }
    
    private static void applyStrikerAI(MobEntity pet, PetComponent petComponent) {
        // Strikers are more willing to take risks for combat positioning
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 2.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 1.0f);
    }
    
    private static void applySupportAI(MobEntity pet, PetComponent petComponent) {
        // Support pets are extra cautious
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 16.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 12.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LAVA, 20.0f);
    }
    
    /**
     * Calculate follow distance based on pet role and characteristics.
     */
    private static float getFollowDistance(PetComponent petComponent) {
        String roleId = petComponent.getRoleId().getPath();
        
        return switch (roleId) {
            case "guardian" -> 8.0f;  // Closer for protection
            case "scout" -> 15.0f;   // Further for exploration
            case "striker" -> 10.0f; // Medium for combat positioning
            case "support" -> 6.0f;  // Close for assistance
            default -> 10.0f;
        };
    }
    
    /**
     * Calculate teleport distance based on pet role.
     */
    private static float getTeleportDistance(PetComponent petComponent) {
        String roleId = petComponent.getRoleId().getPath();
        
        return switch (roleId) {
            case "scout" -> 20.0f;   // Scouts can range further before teleporting
            case "support" -> 12.0f; // Support pets teleport sooner to stay close
            default -> 16.0f;
        };
    }
}