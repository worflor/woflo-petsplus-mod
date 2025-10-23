package woflo.petsplus.ai.director;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.planner.DeterministicPlanner;
import woflo.petsplus.ai.planner.PlanResolution;
import woflo.petsplus.ai.suggester.GoalSuggester;

import java.util.Optional;

/**
 * Lightweight director that picks the top suggestion and resolves a plan for it.
 */
public final class AdaptiveDirector {

    private final GoalSuggester suggester;
    private final DeterministicPlanner planner;
    private DirectorDecision lastDecision;

    public AdaptiveDirector(GoalSuggester suggester, DeterministicPlanner planner) {
        this.suggester = suggester;
        this.planner = planner;
    }

    public DirectorDecision decide(MobEntity mob, PetContext context) {
        Optional<GoalSuggester.Suggestion> suggestion = suggester.suggestBest(context);
        if (suggestion.isEmpty()) {
            lastDecision = new DirectorDecision(null, null, context.worldTime());
            return lastDecision;
        }

        GoalSuggester.Suggestion best = suggestion.get();
        PlanResolution resolution = planner.resolvePlanWithContext(best.definition(), context).orElse(null);
        lastDecision = new DirectorDecision(best, resolution, context.worldTime());
        return lastDecision;
    }

    public DirectorDecision lastDecision() {
        return lastDecision;
    }
}

