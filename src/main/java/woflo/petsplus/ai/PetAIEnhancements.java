package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.AdaptiveAIManager;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.ai.goals.CrouchCuddleGoal;
import woflo.petsplus.ai.goals.PetSnuggleGoal;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
// OnFireScrambleGoal removed as part of AI simplification

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
        if (pet.getEntityWorld().isClient()) return;
        
        try {
            OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
            OwnerAssistAttackGoal.clearAssistRegroup(petComponent);
            // Remove vanilla follow goal - adaptive system handles following now
            removeVanillaFollowGoal(pet);

            // Owner assist targeting to mirror vanilla wolf combat
            addOwnerAssistGoal(pet, petComponent);

            // Crouch cuddle handshake keeps pets cozy near crouching owners
            addCrouchCuddleGoal(pet, petComponent);

            // Passive snuggle healing while cuddling (slow, gated, with cooldowns)
            addSnuggleGoal(pet, petComponent);

            // OnFireScrambleGoal removed - pets will use vanilla panic behavior

            // Improved pathfinding penalties
            adjustPathfindingPenalties(pet, petComponent);
            
            // Role-specific AI adjustments
            applyRoleSpecificAI(pet, petComponent);

            // === NEW ADAPTIVE AI SYSTEM ===
            // Replaces old mood-based, MoodInfluenced, and MoodAdvanced systems
            // This single call adds ALL adaptive goals based on mob capabilities
            AdaptiveAIManager.reinitializeAdaptiveAI(pet);

        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to enhance AI for pet {}: {}", pet.getType(), e.getMessage());
        }
    }
    
    /**
     * Add enhanced follow goal that's smarter about distances and obstacles.
     */
    private static void removeVanillaFollowGoal(MobEntity pet) {
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        accessor.getGoalSelector().getGoals().removeIf(goal -> goal.getGoal() instanceof net.minecraft.entity.ai.goal.FollowOwnerGoal);
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

    private static void addSnuggleGoal(MobEntity pet, PetComponent petComponent) {
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        accessor.getGoalSelector().getGoals().removeIf(entry -> entry.getGoal() instanceof PetSnuggleGoal);
        // Place alongside cuddle; it declares no controls, so it won't fight movement logic
        accessor.getGoalSelector().add(4, new PetSnuggleGoal(pet, petComponent));
    }

    // OnFireScrambleGoal removed - pets will use vanilla panic behavior
    
    /**
     * Adjust pathfinding penalties based on pet characteristics.
     * Simplified to use vanilla pathfinding system for hazards.
     */
    private static void adjustPathfindingPenalties(MobEntity pet, PetComponent petComponent) {
        // Pets are more willing to walk through water to reach their owner
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, 0.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER_BORDER, 0.0f);
        
        // Apply high pathfinding costs to genuine hazards using vanilla system
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LAVA, 20.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_FIRE, 15.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_FIRE, 12.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 10.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 8.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 6.0f);
        
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
        // Guardians get enhanced defensive pathfinding - simplified
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 6.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 3.0f);
    }
    
    private static void applyScoutAI(MobEntity pet, PetComponent petComponent) {
        // Scouts are more mobile and explore further
        // Scout pets rely on adaptive follow tuning; no extra wiring required here.
    }
    
    private static void applyStrikerAI(MobEntity pet, PetComponent petComponent) {
        // Strikers are more willing to take risks for combat positioning - simplified
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 4.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 2.0f);
    }
    
    private static void applySupportAI(MobEntity pet, PetComponent petComponent) {
        // Support pets are extra cautious - simplified
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 8.0f);
        pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 6.0f);
    }
    
    public static PetMobInteractionProfile createMobInteractionProfile(
        MobEntity pet,
        PetComponent petComponent,
        NearbyMobAgeProfile ageProfile,
        PetContextCrowdSummary summary
    ) {
        if (ageProfile == null) {
            return PetMobInteractionProfile.defaultProfile();
        }
        return PetMobInteractionProfile.derive(pet, petComponent, ageProfile, summary);
    }
}

