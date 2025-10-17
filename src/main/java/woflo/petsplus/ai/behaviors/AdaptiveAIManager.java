package woflo.petsplus.ai.behaviors;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.state.PetComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the new adaptive AI system using the GoalArbiter.
 * Replaces the old priority-based system with urgency-based selection.
 */
public class AdaptiveAIManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<MobEntity, GoalArbiter> ARBITERS = new ConcurrentHashMap<>();
    
    /**
     * Initialize the adaptive AI system for a pet.
     * This should be called when a pet is created or when the AI system needs to be reset.
     * 
     * @param pet The pet entity
     */
    public static void initializeAdaptiveAI(MobEntity pet) {
        PetComponent petComponent = PetComponent.get(pet);
        if (petComponent == null) {
            LOGGER.warn("Cannot initialize adaptive AI for pet without PetComponent");
            return;
        }
        
        // Remove existing arbiter if any
        removeAdaptiveAI(pet);
        
        // Create new arbiter
        GoalArbiter arbiter = new GoalArbiter(pet, petComponent);
        ARBITERS.put(pet, arbiter);
        
        // Register behaviors
        registerBehaviors(arbiter, pet, petComponent);
        
        LOGGER.debug("Initialized adaptive AI for pet: {}", pet);
    }
    
    /**
     * Reinitialize the adaptive AI system for a pet.
     * This is called when the AI system needs to be reset.
     * 
     * @param pet The pet entity
     */
    public static void reinitializeAdaptiveAI(MobEntity pet) {
        initializeAdaptiveAI(pet);
    }
    
    /**
     * Remove the adaptive AI system for a pet.
     * This should be called when a pet is removed or when switching back to the old system.
     * 
     * @param pet The pet entity
     */
    public static void removeAdaptiveAI(MobEntity pet) {
        GoalArbiter arbiter = ARBITERS.remove(pet);
        if (arbiter != null) {
            arbiter.clear();
            LOGGER.debug("Removed adaptive AI for pet: {}", pet);
        }
    }
    
    /**
     * Tick the adaptive AI system for a pet.
     * This should be called every tick for each pet.
     * 
     * @param pet The pet entity
     */
    public static void tickAdaptiveAI(MobEntity pet) {
        GoalArbiter arbiter = ARBITERS.get(pet);
        if (arbiter != null) {
            arbiter.tick();
        }
    }
    
    /**
     * Get the goal arbiter for a pet.
     * 
     * @param pet The pet entity
     * @return The goal arbiter, or null if not initialized
     */
    public static GoalArbiter getArbiter(MobEntity pet) {
        return ARBITERS.get(pet);
    }
    
    /**
     * Force re-evaluation of goals for a pet.
     * This should be called when the pet's state changes significantly.
     * 
     * @param pet The pet entity
     */
    public static void forceReevaluation(MobEntity pet) {
        GoalArbiter arbiter = ARBITERS.get(pet);
        if (arbiter != null) {
            arbiter.forceReevaluation();
        }
    }
    
    /**
     * Register behaviors with the goal arbiter.
     * 
     * @param arbiter The goal arbiter
     * @param pet The pet entity
     * @param petComponent The pet component
     */
    private static void registerBehaviors(GoalArbiter arbiter, MobEntity pet, PetComponent petComponent) {
        BehaviorManager behaviorManager = BehaviorManager.getInstance();
        
        // Register all available behaviors
        for (Identifier behaviorId : behaviorManager.getAllBehaviorIds()) {
            var goalOpt = BehaviorGoalFactory.createGoal(behaviorId, pet);
            if (goalOpt.isPresent()) {
                AdaptiveGoal goal = goalOpt.get();
                
                // If the goal implements IAdaptableGoal, register it with the arbiter
                if (goal instanceof IAdaptableGoal adaptableGoal) {
                    arbiter.registerGoal(adaptableGoal, goal);
                    LOGGER.debug("Registered adaptable goal: {} for pet: {}", behaviorId, pet);
                } else {
                    LOGGER.warn("Goal does not implement IAdaptableGoal: {}", behaviorId);
                }
            } else {
                LOGGER.debug("Failed to create goal for behavior: {}", behaviorId);
            }
        }
    }
    
    /**
     * Check if a pet has adaptive AI initialized.
     * 
     * @param pet The pet entity
     * @return true if adaptive AI is initialized
     */
    public static boolean hasAdaptiveAI(MobEntity pet) {
        return ARBITERS.containsKey(pet);
    }
    
    /**
     * Get the current goal for a pet.
     * 
     * @param pet The pet entity
     * @return The current adaptable goal, or null if none
     */
    public static IAdaptableGoal getCurrentGoal(MobEntity pet) {
        GoalArbiter arbiter = ARBITERS.get(pet);
        if (arbiter != null) {
            return arbiter.getCurrentGoal();
        }
        return null;
    }
    
    /**
     * Get the last calculated urgency score for a goal.
     * 
     * @param pet The pet entity
     * @param goal The goal
     * @return The urgency score, or 0.0 if not available
     */
    public static float getUrgencyScore(MobEntity pet, IAdaptableGoal goal) {
        GoalArbiter arbiter = ARBITERS.get(pet);
        if (arbiter != null) {
            return arbiter.getLastUrgencyScore(goal);
        }
        return 0.0f;
    }
    
    /**
     * Clear all adaptive AI systems (for testing purposes).
     */
    public static void clearAll() {
        for (GoalArbiter arbiter : ARBITERS.values()) {
            arbiter.clear();
        }
        ARBITERS.clear();
    }
    
    /**
     * Get statistics about the adaptive AI system.
     * 
     * @return A map of statistics
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_arbiters", ARBITERS.size());
        
        int totalGoals = 0;
        int activeGoals = 0;
        
        for (GoalArbiter arbiter : ARBITERS.values()) {
            totalGoals += arbiter.getAllGoals().size();
            if (arbiter.getCurrentGoal() != null) {
                activeGoals++;
            }
        }
        
        stats.put("total_registered_goals", totalGoals);
        stats.put("active_goals", activeGoals);
        stats.put("average_goals_per_pet", ARBITERS.isEmpty() ? 0 : (double) totalGoals / ARBITERS.size());
        
        return stats;
    }
}