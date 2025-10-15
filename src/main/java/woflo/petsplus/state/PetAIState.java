package woflo.petsplus.state;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.director.DirectorDecision;
import woflo.petsplus.ai.planner.ActionPlan;
import woflo.petsplus.ai.planner.PlanResolution;
import woflo.petsplus.ai.suggester.GoalSuggester;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks transient, high-level behavioral cues for a pet.
 * <p>
 * This state is intentionally ephemeral; it is reset when the owning
 * {@link PetComponent} unloads and should never be serialized.
 */
public final class PetAIState {
    private static final int MAX_RECENT_GOALS = 8;
    private static final int MAX_SUGGESTION_HISTORY = 12;

    private @Nullable Identifier activeMajorGoal;
    private @Nullable Identifier activeAdaptiveGoalId;
    private long activeAdaptiveGoalStartTick = Long.MIN_VALUE;
    private boolean panicking;
    private final ArrayDeque<Identifier> recentGoals = new ArrayDeque<>(MAX_RECENT_GOALS);
    private final Map<Identifier, Long> goalExecutionTimestamps = new HashMap<>();
    private final Map<String, Integer> quirkCounters = new HashMap<>();
    private final ArrayDeque<SuggestionSnapshot> suggestionHistory = new ArrayDeque<>(MAX_SUGGESTION_HISTORY);
    private @Nullable Identifier lastSuggestedGoalId;
    private float lastSuggestionScore;
    private long lastSuggestionTick = Long.MIN_VALUE;
    private @Nullable String lastSuggestionReason;
    private @Nullable PlanSummary lastPlanSummary;

    /**
     * Resets the AI state to its default, neutral configuration.
     */
    public void reset() {
        this.activeMajorGoal = null;
        this.activeAdaptiveGoalId = null;
        this.activeAdaptiveGoalStartTick = Long.MIN_VALUE;
        this.panicking = false;
        this.recentGoals.clear();
        this.goalExecutionTimestamps.clear();
        this.quirkCounters.clear();
        this.suggestionHistory.clear();
        this.lastSuggestedGoalId = null;
        this.lastSuggestionScore = 0f;
        this.lastSuggestionTick = Long.MIN_VALUE;
        this.lastSuggestionReason = null;
        this.lastPlanSummary = null;
    }

    /**
     * Returns the identifier of the currently active high level goal, if any.
     */
    public @Nullable Identifier getActiveMajorGoal() {
        return activeMajorGoal;
    }

    /**
     * Sets the identifier for the currently active major goal.
     */
    public void setActiveMajorGoal(@Nullable Identifier goalId) {
        this.activeMajorGoal = goalId;
    }

    public @Nullable Identifier getActiveAdaptiveGoalId() {
        return activeAdaptiveGoalId;
    }

    public void setActiveAdaptiveGoalId(@Nullable Identifier activeAdaptiveGoalId) {
        this.activeAdaptiveGoalId = activeAdaptiveGoalId;
    }

    public long getActiveAdaptiveGoalStartTick() {
        return activeAdaptiveGoalStartTick;
    }

    public void setActiveAdaptiveGoalStartTick(long activeAdaptiveGoalStartTick) {
        this.activeAdaptiveGoalStartTick = activeAdaptiveGoalStartTick;
    }

    /**
     * Returns whether the pet is presently panicking/fleeing.
     */
    public boolean isPanicking() {
        return panicking;
    }

    /**
     * Updates the panic flag to reflect if the pet is currently fleeing.
     */
    public void setPanicking(boolean panicking) {
        this.panicking = panicking;
    }

    public void recordGoalStart(@Nullable Identifier goalId) {
        if (goalId == null) {
            return;
        }
        recentGoals.remove(goalId);
        recentGoals.addFirst(goalId);
        while (recentGoals.size() > MAX_RECENT_GOALS) {
            recentGoals.removeLast();
        }
    }

    public void recordGoalCompletion(@Nullable Identifier goalId, long worldTime) {
        if (goalId == null) {
            return;
        }
        goalExecutionTimestamps.put(goalId, Math.max(0L, worldTime));
    }

    public Deque<Identifier> getRecentGoalsSnapshot() {
        return new ArrayDeque<>(recentGoals);
    }

    public Map<Identifier, Long> getGoalExecutionTimestamps() {
        return Map.copyOf(goalExecutionTimestamps);
    }

    public Map<String, Integer> getQuirkCountersSnapshot() {
        return Map.copyOf(quirkCounters);
    }

    public void setQuirkCounter(@Nullable String key, int value) {
        if (key == null) {
            return;
        }
        if (value <= 0) {
            quirkCounters.remove(key);
        } else {
            quirkCounters.put(key, value);
        }
    }

    public void incrementQuirkCounter(@Nullable String key) {
        if (key == null) {
            return;
        }
        quirkCounters.merge(key, 1, Integer::sum);
    }

    public void recordDirectorDecision(@Nullable DirectorDecision decision) {
        if (decision == null) {
            clearSuggestionState();
            return;
        }
        recordDirectorDecision(decision.suggestion(), decision.resolution(), decision.tick());
    }

    public void recordDirectorDecision(@Nullable GoalSuggester.Suggestion suggestion,
                                       @Nullable PlanResolution resolution,
                                       long tick) {
        if (suggestion == null) {
            clearSuggestionState();
            return;
        }

        Identifier goalId = suggestion.definition() != null ? suggestion.definition().id() : null;
        this.lastSuggestedGoalId = goalId;
        this.lastSuggestionScore = MathHelper.clamp(suggestion.score(), 0f, 16f);
        this.lastSuggestionTick = tick;
        this.lastSuggestionReason = suggestion.reason();
        PlanSummary planSummary = summarizePlan(resolution);
        this.lastPlanSummary = planSummary;
        pushSuggestionSnapshot(new SuggestionSnapshot(goalId, lastSuggestionScore, tick, lastSuggestionReason, planSummary));
    }

    public void recordGoalSuggestion(@Nullable Identifier goalId, float score, long tick) {
        if (goalId == null) {
            clearSuggestionState();
            return;
        }
        this.lastSuggestedGoalId = goalId;
        this.lastSuggestionScore = MathHelper.clamp(score, 0f, 16f);
        this.lastSuggestionTick = tick;
        this.lastSuggestionReason = null;
        this.lastPlanSummary = null;
        pushSuggestionSnapshot(new SuggestionSnapshot(goalId, lastSuggestionScore, tick, null, null));
    }

    public @Nullable Identifier getLastSuggestedGoalId() {
        return lastSuggestedGoalId;
    }

    public float getLastSuggestionScore() {
        return lastSuggestionScore;
    }

    public long getLastSuggestionTick() {
        return lastSuggestionTick;
    }

    public @Nullable String getLastSuggestionReason() {
        return lastSuggestionReason;
    }

    public @Nullable PlanSummary getLastPlanSummary() {
        return lastPlanSummary;
    }

    public List<SuggestionSnapshot> getSuggestionHistorySnapshot() {
        return List.copyOf(suggestionHistory);
    }

    private void clearSuggestionState() {
        this.lastSuggestedGoalId = null;
        this.lastSuggestionScore = 0f;
        this.lastSuggestionTick = Long.MIN_VALUE;
        this.lastSuggestionReason = null;
        this.lastPlanSummary = null;
    }

    private void pushSuggestionSnapshot(SuggestionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        suggestionHistory.addFirst(snapshot);
        while (suggestionHistory.size() > MAX_SUGGESTION_HISTORY) {
            suggestionHistory.removeLast();
        }
    }

    private @Nullable PlanSummary summarizePlan(@Nullable PlanResolution resolution) {
        if (resolution == null) {
            return null;
        }
        ActionPlan plan = resolution.plan();
        if (plan == null) {
            return null;
        }

        Identifier planId = plan.id();
        Identifier firstFragmentId = null;
        List<String> tags = List.of();
        ActionPlan.Step firstStep = plan.firstStep();
        if (firstStep != null) {
            firstFragmentId = firstStep.fragmentId();
            tags = firstStep.tags();
        }

        Identifier variantId = null;
        List<PlanResolution.ResolvedStep> resolvedSteps = resolution.steps();
        if (resolvedSteps != null && !resolvedSteps.isEmpty()) {
            PlanResolution.ResolvedStep resolvedStep = resolvedSteps.get(0);
            if (resolvedStep != null && resolvedStep.variant() != null) {
                variantId = resolvedStep.variant().id();
            }
        }

        int groupSize = resolution.groupContext() != null ? resolution.groupContext().members().size() : 0;
        int stepCount = resolvedSteps != null ? resolvedSteps.size() : 0;

        return new PlanSummary(
            planId,
            firstFragmentId,
            tags,
            variantId,
            plan.requiresOwnerGroup(),
            stepCount,
            groupSize,
            resolution.signature()
        );
    }

    public record PlanSummary(
        Identifier planId,
        @Nullable Identifier firstFragmentId,
        List<String> firstFragmentTags,
        @Nullable Identifier selectedVariantId,
        boolean requiresOwnerGroup,
        int resolvedStepCount,
        int groupSize,
        @Nullable String signature
    ) {
        public PlanSummary {
            if (planId == null) {
                throw new IllegalArgumentException("planId");
            }
            firstFragmentTags = firstFragmentTags == null || firstFragmentTags.isEmpty()
                ? List.of()
                : List.copyOf(firstFragmentTags);
        }
    }

    public record SuggestionSnapshot(
        @Nullable Identifier goalId,
        float score,
        long tick,
        @Nullable String reason,
        @Nullable PlanSummary planSummary
    ) {}
}
