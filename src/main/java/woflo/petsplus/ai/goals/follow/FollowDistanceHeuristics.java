package woflo.petsplus.ai.goals.follow;

/**
 * Shared heuristics for determining when follow behaviors should activate or yield.
 * Centralizes distance thresholds so idle and wander goals can mirror follow logic.
 */
public final class FollowDistanceHeuristics {
    private static final double START_DISTANCE_SLACK = 0.45d;
    private static final double STOP_DISTANCE_SLACK = 0.75d;
    private static final double MAX_START_SLACK_RATIO = 0.45d;
    private static final double MAX_STOP_SLACK_RATIO = 0.55d;
    private static final double MIN_THRESHOLD_DISTANCE = 1.75d;
    private static final double OWNER_MOVING_SLACK_SCALE = 0.6d;

    private FollowDistanceHeuristics() {
    }

    public static double computeStartThreshold(double baselineDistance, boolean ownerIsMoving) {
        double slack = Math.min(START_DISTANCE_SLACK, baselineDistance * MAX_START_SLACK_RATIO);
        if (ownerIsMoving) {
            slack *= OWNER_MOVING_SLACK_SCALE;
        }
        return Math.max(MIN_THRESHOLD_DISTANCE, Math.min(baselineDistance, baselineDistance - slack));
    }

    public static double computeStopThreshold(double baselineDistance, boolean ownerIsMoving) {
        double slack = Math.min(STOP_DISTANCE_SLACK, baselineDistance * MAX_STOP_SLACK_RATIO);
        if (ownerIsMoving) {
            slack *= OWNER_MOVING_SLACK_SCALE;
        }
        return Math.max(MIN_THRESHOLD_DISTANCE, Math.min(baselineDistance, baselineDistance - slack));
    }

    public static double computeStartThresholdSq(double baselineDistance, boolean ownerIsMoving) {
        double threshold = computeStartThreshold(baselineDistance, ownerIsMoving);
        return threshold * threshold;
    }

    public static double computeStopThresholdSq(double baselineDistance, boolean ownerIsMoving) {
        double threshold = computeStopThreshold(baselineDistance, ownerIsMoving);
        return threshold * threshold;
    }
}
