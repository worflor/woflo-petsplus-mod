package woflo.petsplus.ai.suggester.signal;

import java.util.Map;

/**
 * Result produced by a desirability/feasibility signal evaluation.
 *
 * @param rawValue     unclamped/raw multiplier suggested by the signal
 * @param appliedValue multiplier applied to the aggregate after clamping or adjustments
 * @param trace        additional trace metadata for debugging
 */
public record SignalResult(float rawValue, float appliedValue, Map<String, Object> trace) {
    public static SignalResult identity() {
        return new SignalResult(1.0f, 1.0f, Map.of());
    }
}
