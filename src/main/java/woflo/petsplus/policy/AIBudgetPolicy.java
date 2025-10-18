package woflo.petsplus.policy;

/**
 * Centralized read-only policy for AI budgets. Keeps numbers consistent across call sites.
 */
public final class AIBudgetPolicy {
    private AIBudgetPolicy() {}

    public static int auraTokensPerOwnerPerTick() {
        // Conservative, smooths out spikes while remaining responsive
        return 24;
    }

    public static int pathStartsPerOwnerPerTick(boolean ownerIsMoving) {
        return ownerIsMoving ? 2 : 1;
    }
}

