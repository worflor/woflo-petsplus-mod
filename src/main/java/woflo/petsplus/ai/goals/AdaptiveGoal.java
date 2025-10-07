package woflo.petsplus.ai.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Base class for all adaptive behavior goals.
 * Executes chosen goals with variable duration based on "engagement".
 * 
 * Works with ANY mob - vanilla or modded, PetsPlus or not.
 * Priority system ensures we never interfere with higher-priority goals.
 */
public abstract class AdaptiveGoal extends Goal {
    protected final MobEntity mob;
    protected final GoalType goalType;
    
    private int activeTicks = 0;
    private int committedDuration = 0;
    private float currentEngagement = 0.5f;
    
    // Engagement tracking
    private static final int BASE_MIN_DURATION = 40;  // 2 seconds
    private static final int BASE_MAX_DURATION = 200; // 10 seconds
    
    public AdaptiveGoal(MobEntity mob, GoalType goalType, EnumSet<Control> controls) {
        this.mob = mob;
        this.goalType = goalType;
        this.setControls(controls);
    }
    
    @Override
    public boolean canStart() {
        // 1. Capability check - can this mob physically do this action?
        if (!meetsCapabilityRequirements()) {
            return false;
        }
        
        // 2. Priority safety - don't start if higher priority goals are active
        if (hasActiveHigherPriorityGoals()) {
            return false;
        }
        
        // 3. Control check - are required controls available?
        if (isControlTaken()) {
            return false;
        }
        
        // 4. Cooldown check
        if (!isCooldownExpired()) {
            return false;
        }
        
        // 5. Energy compatibility check - gate based on behavioral momentum
        if (!isEnergyCompatible()) {
            return false;
        }
        
        // 6. Goal-specific conditions
        return canStartGoal();
    }
    
    @Override
    public boolean shouldContinue() {
        // Stop if higher priority goal needs to run
        if (hasActiveHigherPriorityGoals()) {
            return false;
        }
        
        // Stop if engagement dropped too low
        if (activeTicks > committedDuration && currentEngagement < 0.2f) {
            return false;
        }
        
        // Stop if exceeded max duration
        if (activeTicks > committedDuration * 2) {
            return false;
        }
        
        // Goal-specific continuation logic
        return shouldContinueGoal();
    }
    
    @Override
    public void start() {
        activeTicks = 0;
        currentEngagement = 0.5f; // Start with neutral engagement
        committedDuration = calculateInitialDuration();
        onStartGoal();
        
        // Record activity start for behavioral momentum
        recordActivityStart();
    }
    
    @Override
    public void stop() {
        // Record satisfaction/enjoyment for memory system
        float finalSatisfaction = calculateFinalSatisfaction();
        recordGoalExperience(finalSatisfaction);
        
        // Record activity completion for behavioral momentum
        recordActivityCompletion();
        
        onStopGoal();
        activeTicks = 0;
    }
    
    @Override
    public void tick() {
        activeTicks++;
        
        // Update engagement based on goal-specific logic
        currentEngagement = calculateEngagement();
        
        // Adaptive duration adjustment
        if (currentEngagement > 0.7f && committedDuration < BASE_MAX_DURATION) {
            committedDuration += 2; // Extend if enjoying
        }
        
        onTickGoal();
    }
    
    // === ABSTRACT METHODS - Implement in subclasses ===
    
    /**
     * Check if goal-specific start conditions are met.
     */
    protected abstract boolean canStartGoal();
    
    /**
     * Check if goal should continue running.
     */
    protected abstract boolean shouldContinueGoal();
    
    /**
     * Called when goal starts.
     */
    protected abstract void onStartGoal();
    
    /**
     * Called when goal stops.
     */
    protected abstract void onStopGoal();
    
    /**
     * Called each tick while goal is active.
     */
    protected abstract void onTickGoal();
    
    /**
     * Calculate current engagement level (0.0 = boring, 1.0 = very engaging).
     */
    protected abstract float calculateEngagement();
    
    /**
     * Get the activity type for behavioral momentum tracking.
     * Override to specify different activity types.
     */
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        // Default mapping based on goal type
        return switch (goalType.getCategory()) {
            case PLAY -> woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.PHYSICAL;
            case WANDER -> woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.PHYSICAL;
            case IDLE_QUIRK -> woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.PHYSICAL;
            case SPECIAL -> woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.MENTAL;
            case SOCIAL -> woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.SOCIAL;
        };
    }
    
    /**
     * Get the activity intensity for this goal.
     * Override to provide goal-specific intensity.
     */
    protected float getActivityIntensity() {
        // Base intensity on engagement and goal type
        float baseIntensity = switch (goalType.getCategory()) {
            case PLAY -> 0.7f;
            case WANDER -> 0.5f;
            case SPECIAL -> 0.6f;
            case IDLE_QUIRK -> 0.3f;
            case SOCIAL -> 0.4f;
        };
        
        // Modulate by current engagement
        return baseIntensity * (0.5f + currentEngagement * 0.5f);
    }
    
    // === HELPER METHODS ===
    
    /**
     * Check if mob meets capability requirements for this goal.
     */
    protected boolean meetsCapabilityRequirements() {
        var capabilities = woflo.petsplus.ai.capability.MobCapabilities.analyze(mob);
        return goalType.isCompatible(capabilities);
    }
    
    /**
     * Check if higher priority goals are currently active.
     */
    protected boolean hasActiveHigherPriorityGoals() {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            int ourPriority = goalType.getPriority();
            
            return accessor.getGoalSelector().getGoals().stream()
                .filter(goal -> goal.getGoal() != this)
                .anyMatch(goal -> goal.getPriority() < ourPriority && goal.isRunning());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if required controls are already taken by other goals.
     */
    protected boolean isControlTaken() {
        EnumSet<Control> needed = getControls();
        if (needed.isEmpty()) {
            return false;
        }
        
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            return accessor.getGoalSelector().getGoals().stream()
                .filter(goal -> goal.getGoal() != this && goal.isRunning())
                .anyMatch(goal -> {
                    EnumSet<Control> theirControls = goal.getGoal().getControls();
                    return theirControls.stream().anyMatch(needed::contains);
                });
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if cooldown has expired.
     */
    protected boolean isCooldownExpired() {
        // TODO: Implement cooldown tracking in PetContext
        return true;
    }
    
    /**
     * Check if pet's current energy level is compatible with this goal.
     * Uses soft gating: allows some goals outside range for unpredictability.
     */
    protected boolean isEnergyCompatible() {
        MomentumState momentum = MomentumState.capture(mob);
        return goalType.isEnergyCompatible(momentum.momentum());
    }
    
    /**
     * Calculate initial committed duration based on context.
     */
    protected int calculateInitialDuration() {
        int base = (BASE_MIN_DURATION + BASE_MAX_DURATION) / 2;
        
        // Adjust for age (young = shorter attention span)
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            PetContext ctx = PetContext.capture(mob, pc);
            if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
                base = (int) (base * 0.7);
            }
        }
        
        return base;
    }
    
    /**
     * Calculate final satisfaction score for memory system.
     */
    protected float calculateFinalSatisfaction() {
        // Average engagement over lifetime
        float avgEngagement = currentEngagement; // Simplified - could track average
        
        // Bonus for completing vs being interrupted
        float completionBonus = (activeTicks >= committedDuration) ? 0.2f : 0.0f;
        
        return Math.min(1.0f, avgEngagement + completionBonus);
    }
    
    /**
     * Record experience for memory/learning system.
     */
    protected void recordGoalExperience(float satisfaction) {
        // TODO: Implement in GoalMemory system
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            // Will integrate with memory system in Phase 5
        }
    }
    
    /**
     * Get current pet context.
     */
    protected PetContext getContext() {
        PetComponent pc = PetComponent.get(mob);
        return pc != null ? PetContext.capture(mob, pc) : PetContext.captureVanilla(mob);
    }
    
    /**
     * Record activity start for behavioral momentum tracking.
     */
    protected void recordActivityStart() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getMoodEngine() != null) {
            // Record a pulse of activity when starting
            float intensity = getActivityIntensity();
            var activityType = getActivityType();
            pc.getMoodEngine().recordBehavioralActivity(intensity * 0.3f, 10, activityType);
        }
    }
    
    /**
     * Record activity completion for behavioral momentum tracking.
     */
    protected void recordActivityCompletion() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getMoodEngine() != null) {
            // Record accumulated activity over the goal's lifetime
            float intensity = getActivityIntensity();
            var activityType = getActivityType();
            long duration = Math.min(activeTicks, 400); // Cap at 20 seconds
            pc.getMoodEngine().recordBehavioralActivity(intensity, duration, activityType);
        }
    }
}
