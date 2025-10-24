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
        return pathStartsPerOwnerPerTick(ownerIsMoving, 1);
    }

    public static int pathStartsPerOwnerPerTick(boolean ownerIsMoving, int packSize) {
        int normalizedSize = Math.max(1, packSize);
        int base = ownerIsMoving ? 2 : 1;
        if (normalizedSize <= 2) {
            return base;
        }
        if (normalizedSize <= 5) {
            return Math.max(1, base - 1);
        }
        return 1;
    }
}

