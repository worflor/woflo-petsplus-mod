package woflo.petsplus.ai.goals.support;

import woflo.petsplus.ai.context.PetContext;

/**
 * Compile-only placeholder for Bogged antidote support.
 *
 * Intended behavior (future wiring):
 * - When the owner or nearby allies are affected by Bogged-related poison, the pet
 *   biases toward supportive actions (e.g., hovering near owner, providing cleansing
 *   or mitigation support when available via abilities/items).
 * - This goal will coordinate with desirability signals (bogged_threat) to elevate
 *   protective/hovering postures while avoiding aggressive escalation.
 *
 * Note:
 * - Not wired in this subtask to avoid unintended behavior changes.
 * - Implementation should remain side-safe, minimal allocation, and respect cooldowns.
 */
public final class BoggedAntidoteSupportGoal {

    /**
     * Minimal stub. Performs no action; returns immediately to ensure safe compilation.
     *
     * @param ctx pet context (unused placeholder)
     */
    public void perform(PetContext ctx) {
        // no-op
    }

    /**
     * Identifier for future wiring reference.
     */
    public String id() {
        return "petsplus:bogged_antidote_support";
    }
}