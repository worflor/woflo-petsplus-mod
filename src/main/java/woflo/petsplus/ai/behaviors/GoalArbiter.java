package woflo.petsplus.ai.behaviors;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import woflo.petsplus.Petsplus;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intelligent goal arbiter that replaces the vanilla GoalSelector.
 * Selects goals based on urgency scores rather than simple priority lists.
 */
public class GoalArbiter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int EVALUATION_INTERVAL_TICKS = 5; // Evaluate every 5 ticks
    private static final float MINIMUM_URGENCY_THRESHOLD = 0.1f;
    
    private final MobEntity mob;
    private final PetComponent petComponent;
    private final Map<IAdaptableGoal, Goal> adaptableGoals = new LinkedHashMap<>();
    private final Map<IAdaptableGoal, Float> lastUrgencyScores = new ConcurrentHashMap<>();
    private IAdaptableGoal currentGoal = null;
    private int ticksSinceLastEvaluation = 0;
    
    public GoalArbiter(MobEntity mob, PetComponent petComponent) {
        this.mob = mob;
        this.petComponent = petComponent;
    }
    
    /**
     * Register an adaptable goal with this arbiter.
     */
    public void registerGoal(IAdaptableGoal adaptableGoal, Goal minecraftGoal) {
        adaptableGoals.put(adaptableGoal, minecraftGoal);
        lastUrgencyScores.put(adaptableGoal, 0.0f);
    }
    
    /**
     * Remove a goal from this arbiter.
     */
    public void removeGoal(IAdaptableGoal adaptableGoal) {
        adaptableGoals.remove(adaptableGoal);
        lastUrgencyScores.remove(adaptableGoal);
        if (currentGoal == adaptableGoal) {
            stopCurrentGoal();
        }
    }
    
    /**
     * Main update method - should be called every tick.
     */
    public void tick() {
        ticksSinceLastEvaluation++;
        
        if (ticksSinceLastEvaluation >= EVALUATION_INTERVAL_TICKS) {
            evaluateAndSelectGoal();
            ticksSinceLastEvaluation = 0;
        }
    }
    
    /**
     * Evaluate all goals and select the most urgent one.
     */
    private void evaluateAndSelectGoal() {
        Map<String, Float> contextValues = gatherContextValues();
        
        // Calculate urgency scores for all goals
        Map<IAdaptableGoal, Float> urgencyScores = new HashMap<>();
        for (IAdaptableGoal goal : adaptableGoals.keySet()) {
            if (goal.shouldBeConsidered(mob, petComponent, contextValues, MINIMUM_URGENCY_THRESHOLD)) {
                float urgency = goal.calculateUrgency(mob, petComponent, contextValues);
                urgencyScores.put(goal, urgency);
                lastUrgencyScores.put(goal, urgency);
            } else {
                lastUrgencyScores.put(goal, 0.0f);
            }
        }
        
        // Find the highest urgency goal
        IAdaptableGoal bestGoal = null;
        float bestUrgency = 0.0f;
        
        for (Map.Entry<IAdaptableGoal, Float> entry : urgencyScores.entrySet()) {
            IAdaptableGoal goal = entry.getKey();
            float urgency = entry.getValue();
            
            // Skip if mutually exclusive with current goal and current goal has higher or equal urgency
            if (currentGoal != null && currentGoal != goal && 
                currentGoal.isMutuallyExclusiveWith(goal) && 
                lastUrgencyScores.getOrDefault(currentGoal, 0.0f) >= urgency) {
                continue;
            }
            
            if (urgency > bestUrgency) {
                bestGoal = goal;
                bestUrgency = urgency;
            }
        }
        
        // Switch goals if necessary
        if (bestGoal != currentGoal) {
            if (currentGoal != null) {
                stopCurrentGoal();
            }
            
            if (bestGoal != null && bestUrgency >= MINIMUM_URGENCY_THRESHOLD) {
                startGoal(bestGoal);
            }
        }
    }
    
    /**
     * Start executing a goal.
     */
    private void startGoal(IAdaptableGoal goal) {
        Goal minecraftGoal = adaptableGoals.get(goal);
        if (minecraftGoal == null) {
            LOGGER.warn("No Minecraft goal found for adaptable goal: {}", goal);
            return;
        }
        
        try {
            // Stop any currently running goals in the vanilla selector
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            accessor.getGoalSelector().getGoals().stream()
                .filter(g -> g.isRunning() && g.getGoal() != minecraftGoal)
                .forEach(g -> g.getGoal().stop());
            
            // Start the new goal
            if (minecraftGoal.canStart()) {
                minecraftGoal.start();
                currentGoal = goal;
                
                // Add to vanilla selector for proper execution
                accessor.getGoalSelector().add(1, minecraftGoal);
                
                LOGGER.debug("Started goal: {} with urgency: {}", goal, 
                    lastUrgencyScores.getOrDefault(goal, 0.0f));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start goal: {}", goal, e);
        }
    }
    
    /**
     * Stop the current goal.
     */
    private void stopCurrentGoal() {
        if (currentGoal != null) {
            try {
                Goal minecraftGoal = adaptableGoals.get(currentGoal);
                if (minecraftGoal != null) {
                    minecraftGoal.stop();
                    
                    // Remove from vanilla selector
                    MobEntityAccessor accessor = (MobEntityAccessor) mob;
                    accessor.getGoalSelector().getGoals().removeIf(g -> g.getGoal() == minecraftGoal);
                }
                
                LOGGER.debug("Stopped goal: {}", currentGoal);
            } catch (Exception e) {
                LOGGER.error("Failed to stop goal: {}", currentGoal, e);
            } finally {
                currentGoal = null;
            }
        }
    }
    
    /**
     * Gather context values that might affect goal urgency.
     */
    private Map<String, Float> gatherContextValues() {
        Map<String, Float> context = new HashMap<>();
        
        // Owner proximity
        if (petComponent.getOwner() != null) {
            double distance = mob.squaredDistanceTo(petComponent.getOwner());
            context.put("owner_nearby", distance < 36.0f ? 1.0f : 0.0f); // 6 blocks
            context.put("owner_distant", distance > 144.0f ? 1.0f : 0.0f); // 12 blocks
            context.put("owner_engaged", isOwnerEngaged() ? 1.0f : 0.0f);
        }
        
        // Environmental context
        context.put("sheltered", isSheltered() ? 1.0f : 0.0f);
        context.put("danger_nearby", isDangerNearby() ? 1.0f : 0.0f);
        context.put("bond_strength", petComponent.getBondStrength());
        
        return context;
    }
    
    /**
     * Check if the owner is currently engaged with the pet.
     */
    private boolean isOwnerEngaged() {
        if (petComponent.getOwner() == null) return false;
        
        // Simple check: owner is looking at pet
        net.minecraft.entity.Entity owner = petComponent.getOwner();
        net.minecraft.util.Vec3d ownerLook = owner.getRotationVec(1.0f).normalize();
        net.minecraft.util.Vec3d toPet = mob.getPos().subtract(owner.getPos()).normalize();
        
        return ownerLook.dotProduct(toPet) > 0.5;
    }
    
    /**
     * Check if the pet is sheltered (under cover).
     */
    private boolean isSheltered() {
        return !mob.getEntityWorld().isSkyVisible(mob.getBlockPos());
    }
    
    /**
     * Check if there's danger nearby.
     */
    private boolean isDangerNearby() {
        return mob.getAttacker() != null || mob.getTarget() != null;
    }
    
    /**
     * Get the currently executing goal.
     */
    public IAdaptableGoal getCurrentGoal() {
        return currentGoal;
    }
    
    /**
     * Get the last calculated urgency score for a goal.
     */
    public float getLastUrgencyScore(IAdaptableGoal goal) {
        return lastUrgencyScores.getOrDefault(goal, 0.0f);
    }
    
    /**
     * Force re-evaluation of goals (useful when state changes significantly).
     */
    public void forceReevaluation() {
        ticksSinceLastEvaluation = EVALUATION_INTERVAL_TICKS;
    }
    
    /**
     * Get all registered goals.
     */
    public Collection<IAdaptableGoal> getAllGoals() {
        return Collections.unmodifiableCollection(adaptableGoals.keySet());
    }
    
    /**
     * Clear all goals (for cleanup).
     */
    public void clear() {
        stopCurrentGoal();
        adaptableGoals.clear();
        lastUrgencyScores.clear();
    }
}