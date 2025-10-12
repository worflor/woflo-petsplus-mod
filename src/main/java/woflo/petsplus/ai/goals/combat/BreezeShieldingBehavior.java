package woflo.petsplus.ai.goals.combat;

import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;

/**
 * Placeholder behavior for future Breeze shielding.
 * <p>
 * Intended behavior: When a Breeze is detected near the owner, the pet positions itself
 * between the owner and the Breeze, maintaining a protective offset and adjusting
 * pathing to body-block line-of-attack where possible. This placeholder is compile-only
 * and is not wired into any selectors yet to avoid side effects.
 */
public final class BreezeShieldingBehavior {

    /**
     * Evaluate whether shielding should be active and return a lightweight score
     * for future integration. Currently a no-op returning 0.0f to avoid behavior changes.
     *
     * @param goal the goal definition context (unused placeholder)
     * @param ctx  the pet context (unused placeholder)
     * @return 0.0f as a neutral score
     */
    public float evaluate(GoalDefinition goal, PetContext ctx) {
        return 0.0f;
    }
}