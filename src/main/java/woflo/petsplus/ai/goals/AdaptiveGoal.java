package woflo.petsplus.ai.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Base class for all adaptive behavior goals.
 * Executes chosen goals with variable duration based on "engagement".
 * 
 * Works with ANY mob - vanilla or modded, PetsPlus or not.
 * Priority system ensures we never interfere with higher-priority goals.
 */
public abstract class AdaptiveGoal extends Goal {
    private static final Map<MobEntity, Map<Identifier, Long>> FALLBACK_COOLDOWNS = new WeakHashMap<>();
    private static final Map<MobEntity, ArrayDeque<Identifier>> FALLBACK_RECENT_GOALS = new WeakHashMap<>();
    private static final Map<MobEntity, Map<Identifier, Long>> FALLBACK_LAST_EXECUTED = new WeakHashMap<>();
    private static final int HISTORY_LIMIT = 8;
    private static final String COOLDOWN_PREFIX = "adaptive_goal:";

    protected final MobEntity mob;
    protected final GoalDefinition goalDefinition;
    protected final Identifier goalId;
    
    private int activeTicks = 0;
    private int committedDuration = 0;
    private float currentEngagement = 0.5f;
    
    // Emotion feedback caching
    private EmotionFeedback cachedFeedback = null;
    
    // Engagement tracking
    private static final int BASE_MIN_DURATION = 40;  // 2 seconds
    private static final int BASE_MAX_DURATION = 200; // 10 seconds
    
    public AdaptiveGoal(MobEntity mob, GoalDefinition goalDefinition, EnumSet<Control> controls) {
        this.mob = mob;
        this.goalDefinition = goalDefinition;
        this.goalId = goalDefinition.id();
        this.setControls(controls);
    }
    
    @Override
    public boolean canStart() {
        // 0. CRITICAL: Never interrupt combat or survival situations
        if (mob.getAttacker() != null || mob.getAttacking() != null) {
            return false;
        }
        if (mob.getTarget() != null) {
            return false; // Has attack target
        }
        
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
        // CRITICAL: Immediately stop if combat starts
        if (mob.getAttacker() != null || mob.getAttacking() != null) {
            return false;
        }
        if (mob.getTarget() != null) {
            return false; // Attack target acquired
        }
        
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
        cachedFeedback = null; // Reset emotion feedback for fresh context
        onStartGoal();

        // Record activity start for behavioral momentum
        recordActivityStart();
        trackGoalStart();
    }
    
    @Override
    public void stop() {
        // Record satisfaction/enjoyment for memory system
        float finalSatisfaction = calculateFinalSatisfaction();
        recordGoalExperience(finalSatisfaction);

        applyGoalCooldown();

        // Record activity completion for behavioral momentum
        recordActivityCompletion();

        trackGoalCompletion();
        
        // Trigger emotion feedback (Phase 2)
        triggerEmotionFeedback();
        cachedFeedback = null; // Ensure next run re-evaluates feedback

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
     * Define emotion feedback for this goal.
     * Called once and cached for performance.
     * Override to provide goal-specific emotional rewards.
     * 
     * @return EmotionFeedback definition, or EmotionFeedback.NONE for no feedback
     */
    protected EmotionFeedback defineEmotionFeedback() {
        return EmotionFeedback.NONE; // Default: no emotions
    }
    
    /**
     * Get the activity type for behavioral momentum tracking.
     * Override to specify different activity types.
     */
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        // Default mapping based on goal type
        return switch (goalDefinition.category()) {
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
        float baseIntensity = switch (goalDefinition.category()) {
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
        return goalDefinition.isCompatible(capabilities);
    }
    
    /**
     * Check if higher priority goals are currently active.
     */
    protected boolean hasActiveHigherPriorityGoals() {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            int ourPriority = goalDefinition.priority();

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
        int min = goalDefinition.minCooldownTicks();
        int max = goalDefinition.maxCooldownTicks();
        if (min <= 0 && max <= 0) {
            return true;
        }

        long currentTime = mob.getEntityWorld().getTime();
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            return !pc.isOnCooldown(getCooldownKey());
        }

        synchronized (FALLBACK_COOLDOWNS) {
            Map<Identifier, Long> cooldowns = FALLBACK_COOLDOWNS.get(mob);
            if (cooldowns == null) {
                return true;
            }
            Long until = cooldowns.get(goalId);
            return until == null || until <= currentTime;
        }
    }
    
    /**
     * Check if pet's current energy level is compatible with this goal.
     * Uses soft gating: allows some goals outside range for unpredictability.
     */
    protected boolean isEnergyCompatible() {
        MomentumState momentum = MomentumState.capture(mob);
        return goalDefinition.isEnergyCompatible(momentum.energyProfile());
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

    private void applyGoalCooldown() {
        int min = goalDefinition.minCooldownTicks();
        int max = goalDefinition.maxCooldownTicks();
        if (min <= 0 && max <= 0) {
            return;
        }

        int cooldownTicks;
        if (max <= min) {
            cooldownTicks = min;
        } else {
            cooldownTicks = min + mob.getRandom().nextInt((max - min) + 1);
        }

        if (cooldownTicks <= 0) {
            return;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            pc.setCooldown(getCooldownKey(), cooldownTicks);
            return;
        }

        long endTick = mob.getEntityWorld().getTime() + cooldownTicks;
        synchronized (FALLBACK_COOLDOWNS) {
            FALLBACK_COOLDOWNS
                .computeIfAbsent(mob, ignored -> new HashMap<>())
                .put(goalId, endTick);
        }
    }

    private String getCooldownKey() {
        return COOLDOWN_PREFIX + goalId.toString();
    }

    private void trackGoalStart() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            pc.recordGoalStart(goalId);
            return;
        }

        synchronized (FALLBACK_RECENT_GOALS) {
            ArrayDeque<Identifier> history = FALLBACK_RECENT_GOALS.computeIfAbsent(mob, ignored -> new ArrayDeque<>());
            history.remove(goalId);
            history.addFirst(goalId);
            while (history.size() > HISTORY_LIMIT) {
                history.removeLast();
            }
        }
    }

    private void trackGoalCompletion() {
        long worldTime = mob.getEntityWorld() != null ? mob.getEntityWorld().getTime() : 0L;
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            pc.recordGoalCompletion(goalId, worldTime);
            return;
        }

        synchronized (FALLBACK_LAST_EXECUTED) {
            Map<Identifier, Long> executions = FALLBACK_LAST_EXECUTED.computeIfAbsent(mob, ignored -> new HashMap<>());
            executions.put(goalId, Math.max(0L, worldTime));
        }
    }

    public static ArrayDeque<Identifier> getFallbackRecentGoals(MobEntity mob) {
        synchronized (FALLBACK_RECENT_GOALS) {
            ArrayDeque<Identifier> history = FALLBACK_RECENT_GOALS.get(mob);
            return history == null ? new ArrayDeque<>() : new ArrayDeque<>(history);
        }
    }

    public static Map<Identifier, Long> getFallbackLastExecuted(MobEntity mob) {
        synchronized (FALLBACK_LAST_EXECUTED) {
            Map<Identifier, Long> executions = FALLBACK_LAST_EXECUTED.get(mob);
            return executions == null ? Collections.emptyMap() : Map.copyOf(executions);
        }
    }

    public static void clearFallbackHistory() {
        synchronized (FALLBACK_RECENT_GOALS) {
            FALLBACK_RECENT_GOALS.clear();
        }
        synchronized (FALLBACK_LAST_EXECUTED) {
            FALLBACK_LAST_EXECUTED.clear();
        }
    }
    
    /**
     * Record experience for memory/learning system.
     */
    protected void recordGoalExperience(float satisfaction) {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            long tick = mob.getEntityWorld() != null ? mob.getEntityWorld().getTime() : 0L;
            pc.recordExperience(goalId, satisfaction, tick);
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
    
    /**
     * Trigger emotion feedback when goal completes.
     * Uses lazy evaluation and caching for maximum performance.
     * Only triggers for PetsPlus pets (vanilla mobs unaffected).
     */
    protected void triggerEmotionFeedback() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            cachedFeedback = null;
            return; // Vanilla mob, no emotion system
        }
        
        // Lazy-load and cache feedback definition for this completion
        EmotionFeedback feedback = cachedFeedback;
        if (feedback == null) {
            feedback = defineEmotionFeedback();
        }

        if (feedback == null) {
            feedback = EmotionFeedback.NONE;
        }

        if (feedback.isEmpty()) {
            cachedFeedback = null;
            return;
        }

        // Hold the freshly computed feedback during dispatch
        cachedFeedback = feedback;

        // Queue emotions via stimulus bus (async-safe)
        var stimulusBus = woflo.petsplus.mood.MoodService.getInstance().getStimulusBus();
        stimulusBus.queueSimpleStimulus(mob, collector -> {
            // Push all defined emotions
            for (var entry : feedback.emotions().entrySet()) {
                collector.pushEmotion(entry.getKey(), entry.getValue());
            }
        });

        // Dispatch immediately for responsive feedback
        stimulusBus.dispatchStimuli(mob);

        // Handle contagion if enabled
        if (feedback.triggersContagion() && mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            triggerEmotionContagion(serverWorld, feedback);
        }

        // Clear cache after dispatch so next completion recomputes context-sensitive feedback
        cachedFeedback = null;
    }
    
    /**
     * Trigger emotional contagion to nearby pets.
     * Performance-optimized: only scans when contagion is enabled.
     */
    private void triggerEmotionContagion(net.minecraft.server.world.ServerWorld world, EmotionFeedback feedback) {
        if (!feedback.triggersContagion() || feedback.contagionEmotion() == null) {
            return;
        }
        
        // Find nearby owned pets (within 8 blocks)
        var nearbyPets = world.getEntitiesByClass(
            MobEntity.class,
            mob.getBoundingBox().expand(8.0),
            entity -> {
                if (entity == mob) return false; // Not self
                PetComponent pc = PetComponent.get(entity);
                return pc != null && pc.getOwner() != null;
            }
        );
        
        if (nearbyPets.isEmpty()) {
            return;
        }
        
        // Queue contagion emotions
        var stimulusBus = woflo.petsplus.mood.MoodService.getInstance().getStimulusBus();
        for (MobEntity nearbyPet : nearbyPets) {
            stimulusBus.queueSimpleStimulus(nearbyPet, collector -> {
                collector.pushEmotion(feedback.contagionEmotion(), feedback.contagionStrength());
            });
            stimulusBus.dispatchStimuli(nearbyPet);
        }
        
        // Visual feedback for contagion (optional)
        PetComponent sourcePc = PetComponent.get(mob);
        if (sourcePc != null) {
            woflo.petsplus.ui.FeedbackManager.emitContagionFeedback(
                mob, world, feedback.contagionEmotion()
            );
        }
    }
}

