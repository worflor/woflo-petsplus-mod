package woflo.petsplus.ai.director;

import woflo.petsplus.ai.planner.PlanResolution;
import woflo.petsplus.ai.suggester.GoalSuggester;

/**
 * Bundles a goal suggestion with the resolved plan and metadata used by the director.
 */
public record DirectorDecision(
    GoalSuggester.Suggestion suggestion,
    PlanResolution resolution,
    long tick
) {}

