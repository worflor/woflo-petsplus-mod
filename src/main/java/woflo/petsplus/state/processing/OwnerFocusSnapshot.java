package woflo.petsplus.state.processing;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Lightweight snapshot describing the owner's current focus or interaction
 * state. The snapshot deliberately captures only low-cardinality booleans so
 * it can be broadcast to every pet without incurring additional per-tick
 * processing cost.
 */
public record OwnerFocusSnapshot(boolean crouching,
                                 boolean usingItem,
                                 boolean screenOpen,
                                 boolean handsBusy,
                                 boolean isSleeping) {

    private static final OwnerFocusSnapshot IDLE =
        new OwnerFocusSnapshot(false, false, false, false, false);

    public static OwnerFocusSnapshot idle() {
        return IDLE;
    }

    /**
     * Captures the current focus signals from the supplied owner. Callers are
     * expected to reuse the resulting snapshot across the whole swarm for the
     * same tick to avoid redundant state checks.
     */
    public static OwnerFocusSnapshot capture(ServerPlayerEntity owner) {
        if (owner == null) {
            return IDLE;
        }
        boolean crouching = owner.isSneaking();
        boolean usingItem = owner.isUsingItem();
        boolean screenOpen = owner.currentScreenHandler != null
            && owner.currentScreenHandler != owner.playerScreenHandler;
        boolean handsBusy = owner.handSwinging || owner.isUsingItem();
        boolean sleeping = owner.isSleeping();
        if (!crouching && !usingItem && !screenOpen && !handsBusy && !sleeping) {
            return IDLE;
        }
        return new OwnerFocusSnapshot(crouching, usingItem, screenOpen, handsBusy, sleeping);
    }

    /**
     * @return {@code true} when the owner is engaged in an interaction that
     *         warrants a short courtesy hold from nearby pets.
     */
    public boolean isBusy() {
        return crouching || usingItem || screenOpen || handsBusy || isSleeping;
    }
}

