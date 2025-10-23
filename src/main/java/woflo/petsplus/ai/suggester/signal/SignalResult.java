package woflo.petsplus.ai.suggester.signal;

/**
 * Lightweight result produced by a desirability/feasibility signal evaluation.
 *
 * <p>The runtime decision pipeline only cares about the raw and applied
 * multipliers, so this record intentionally avoids any allocation-heavy trace
 * payloads. Callers that want richer diagnostics can layer their own
 * instrumentation on top of the signal implementation without impacting the hot
 * path.</p>
 *
 * @param rawValue     unclamped/raw multiplier suggested by the signal
 * @param appliedValue multiplier applied to the aggregate after clamping or adjustments
 * @param reason       optional categorical reason for the adjustment (may be {@code null})
 */
public record SignalResult(float rawValue, float appliedValue, String reason) {

    public static final SignalResult IDENTITY = new SignalResult(1.0f, 1.0f, null);

    public static SignalResult identity() {
        return IDENTITY;
    }
}
