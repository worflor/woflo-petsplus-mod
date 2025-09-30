package woflo.petsplus.ai;

/**
 * Immutable configuration describing how frequently a mood-driven goal may activate.
 */
public record MoodGoalThrottleConfig(
    int minCooldownTicks,
    int minActiveTicks,
    int baseChance,
    int strengthChanceBonus,
    int pressureIntervalTicks,
    int pressureChanceBonus,
    int maxPressureBonus,
    int minRollBound,
    int maxRollBound,
    int followHoldTicks,
    float followDistanceBonus
) {
    public MoodGoalThrottleConfig {
        if (minCooldownTicks < 0) minCooldownTicks = 0;
        if (minActiveTicks < 0) minActiveTicks = 0;
        if (baseChance < 1) baseChance = 1;
        if (strengthChanceBonus < 0) strengthChanceBonus = 0;
        if (pressureIntervalTicks < 0) pressureIntervalTicks = 0;
        if (pressureChanceBonus < 0) pressureChanceBonus = 0;
        if (maxPressureBonus < 0) maxPressureBonus = 0;
        if (minRollBound < 1) minRollBound = 1;
        if (maxRollBound < minRollBound) {
            maxRollBound = Math.max(minRollBound, 1);
        }
        if (followHoldTicks < 0) followHoldTicks = 0;
        if (Float.isNaN(followDistanceBonus) || followDistanceBonus < 0.0f) {
            followDistanceBonus = 0.0f;
        }
    }
}
