package woflo.petsplus.state;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks transient, high-level behavioral cues for a pet.
 * <p>
 * This state is intentionally ephemeral; it is reset when the owning
 * {@link PetComponent} unloads and should never be serialized.
 */
public final class PetAIState {
    private static final int MAX_RECENT_GOALS = 8;

    private @Nullable Identifier activeMajorGoal;
    private @Nullable Identifier activeAdaptiveGoalId;
    private long activeAdaptiveGoalStartTick = Long.MIN_VALUE;
    private boolean panicking;
    private final ArrayDeque<Identifier> recentGoals = new ArrayDeque<>(MAX_RECENT_GOALS);
    private final Map<Identifier, Long> goalExecutionTimestamps = new HashMap<>();
    private final Map<String, Integer> quirkCounters = new HashMap<>();
    private @Nullable Identifier lastSuggestedGoalId;
    private float lastSuggestionScore;
    private long lastSuggestionTick = Long.MIN_VALUE;

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
        this.lastSuggestedGoalId = null;
        this.lastSuggestionScore = 0f;
        this.lastSuggestionTick = Long.MIN_VALUE;
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

    public void recordGoalSuggestion(@Nullable Identifier goalId, float score, long tick) {
        if (goalId == null) {
            this.lastSuggestedGoalId = null;
            this.lastSuggestionScore = 0f;
            this.lastSuggestionTick = Long.MIN_VALUE;
            return;
        }
        this.lastSuggestedGoalId = goalId;
        this.lastSuggestionScore = MathHelper.clamp(score, 0f, 16f);
        this.lastSuggestionTick = tick;
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
}
