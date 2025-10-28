package woflo.petsplus.state.morality;

import net.minecraft.util.math.MathHelper;

/**
 * Mutable runtime state for a single morality aspect on a pet. The ledger keeps
 * one instance per virtue and vice to track accumulated value, spree windows and
 * suppression caused by unmet virtue gates.
 */
public final class MoralityAspectState {
    private static final float SIGNIFICANT_DELTA = 5e-4f;
    private static final float CLEAR_EPSILON = 1e-4f;
    private static final float SUPPRESSION_CAP = 1000f;

    private float value;
    private int spreeCount;
    private long spreeAnchorTick = Long.MIN_VALUE;
    private long lastUpdatedTick = Long.MIN_VALUE;
    private float suppressedCharge;
    private long lastSuppressedTick = Long.MIN_VALUE;

    public MoralityAspectState() {
    }

    public float value() {
        return value;
    }

    public int spreeCount() {
        return spreeCount;
    }

    public long spreeAnchorTick() {
        return spreeAnchorTick;
    }

    public long lastUpdatedTick() {
        return lastUpdatedTick;
    }

    public float suppressedCharge() {
        return suppressedCharge;
    }

    public long lastSuppressedTick() {
        return lastSuppressedTick;
    }

    public void setValue(float value) {
        this.value = Math.max(0f, Float.isFinite(value) ? value : 0f);
    }

    public void setSpree(int count, long anchor) {
        this.spreeCount = Math.max(0, count);
        this.spreeAnchorTick = anchor;
    }

    public void setSuppression(float charge, long tick) {
        this.suppressedCharge = Math.max(0f, charge);
        this.lastSuppressedTick = tick;
    }

    public void stabilize(float baseline, double retentionLnPerTick, float passiveDriftPerTick, long now) {
        long reference = lastUpdatedTick != Long.MIN_VALUE ? lastUpdatedTick : now;
        long elapsed = Math.max(0L, now - reference);
        if (elapsed <= 0L) {
            lastUpdatedTick = now;
            return;
        }
        float difference = value - baseline;
        if (difference != 0f && !Double.isNaN(retentionLnPerTick)) {
            double factor = retentionLnPerTick == 0d
                ? 1.0d
                : Math.exp(retentionLnPerTick * elapsed);
            difference = (float) (difference * factor);
        }
        if (passiveDriftPerTick > 0f && difference != 0f) {
            float drift = passiveDriftPerTick * elapsed;
            if (difference > 0f) {
                difference = Math.max(0f, difference - drift);
            } else {
                difference = Math.min(0f, difference + drift);
            }
        }
        value = MathHelper.clamp(baseline + difference, 0f, Float.MAX_VALUE);
        lastUpdatedTick = now;
    }

    public void applyDelta(float delta, long now, long spreeWindowTicks) {
        if (!Float.isFinite(delta) || delta == 0f) {
            lastUpdatedTick = now;
            return;
        }
        value = Math.max(0f, value + delta);
        if (delta > SIGNIFICANT_DELTA) {
            if (spreeAnchorTick != Long.MIN_VALUE && now - spreeAnchorTick > spreeWindowTicks) {
                spreeCount = 0;
                spreeAnchorTick = now;
            }
            if (spreeAnchorTick == Long.MIN_VALUE) {
                spreeAnchorTick = now;
            }
            if (spreeCount < Integer.MAX_VALUE - 1) {
                spreeCount++;
            }
        } else if (delta < 0f && value <= CLEAR_EPSILON) {
            value = 0f;
        }
        lastUpdatedTick = now;
    }

    public void recordSuppressed(float severity, long now) {
        suppressedCharge = MathHelper.clamp(suppressedCharge + Math.max(0f, severity), 0f, SUPPRESSION_CAP);
        lastSuppressedTick = now;
    }

    public void bleedSuppression(long now, float bleedRate) {
        if (suppressedCharge <= 0f || bleedRate <= 0f) {
            return;
        }
        suppressedCharge = Math.max(0f, suppressedCharge - bleedRate);
        if (suppressedCharge <= 0f) {
            suppressedCharge = 0f;
            lastSuppressedTick = now;
        }
    }
}
