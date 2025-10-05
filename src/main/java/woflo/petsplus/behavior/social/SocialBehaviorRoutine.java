package woflo.petsplus.behavior.social;

import woflo.petsplus.state.coordination.PetSwarmIndex;

/**
 * Defines the lifecycle for modular social behaviour routines. Each routine
 * can gather the context it needs, then emit mood or ledger effects without
 * editing the central handler.
 */
public interface SocialBehaviorRoutine {

    /**
     * Determines whether the routine should execute for the current snapshot.
     */
    boolean shouldRun(SocialContextSnapshot context);

    /**
     * Collects any neighbour or environmental context required before
     * applying effects.
     */
    void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick);

    /**
     * Applies the behavioural effects (emotions, cues, ledger updates) based on
     * the previously gathered context.
     */
    void applyEffects(SocialContextSnapshot context);
}
