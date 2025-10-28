package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;
import java.util.Random;

/**
 * Per-pet temperament data used to bias virtue/vice adjustments so that
 * repeated deeds feel earned instead of scripted.
 */
public final class MoralityPersona {
    private float viceSusceptibility;
    private float virtueResilience;
    private float traumaImprint;
    private long lastExperienceTick = Long.MIN_VALUE;

    public MoralityPersona(float viceSusceptibility, float virtueResilience, float traumaImprint) {
        this.viceSusceptibility = clampUnit(viceSusceptibility);
        this.virtueResilience = clampUnit(virtueResilience);
        this.traumaImprint = clampUnit(traumaImprint);
    }

    public static MoralityPersona seeded(long seed) {
        Random random = new Random(seed);
        float susceptibility = MathHelper.clamp(0.8f + random.nextFloat() * 0.6f, 0.6f, 1.4f);
        float resilience = MathHelper.clamp(0.8f + random.nextFloat() * 0.6f, 0.6f, 1.4f);
        float trauma = MathHelper.clamp(random.nextFloat() * 0.2f, 0f, 0.35f);
        return new MoralityPersona(susceptibility, resilience, trauma);
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) {
            return 1.0f;
        }
        return MathHelper.clamp(value, 0.0f, 2.0f);
    }

    public float adjustViceDelta(Identifier aspectId, float delta, MoralityAspectState state) {
        Objects.requireNonNull(aspectId, "aspectId");
        if (delta <= 0f) {
            return delta;
        }
        float inertia = 1.0f + MathHelper.clamp(state.suppressedCharge() * 0.05f, 0f, 0.75f);
        float traumaFactor = 1.0f + traumaImprint * 0.6f;
        return delta * viceSusceptibility * inertia * traumaFactor;
    }

    public float adjustVirtueDelta(float delta) {
        if (delta == 0f) {
            return 0f;
        }
        if (delta > 0f) {
            return delta * virtueResilience;
        }
        return delta / Math.max(0.2f, virtueResilience);
    }

    public void recordViceExperience(float severity, long now) {
        if (severity <= 0f) {
            return;
        }
        float increment = MathHelper.clamp(severity * 0.01f, 0f, 0.08f);
        traumaImprint = MathHelper.clamp(traumaImprint + increment, 0f, 1f);
        viceSusceptibility = MathHelper.clamp(viceSusceptibility + increment * 0.5f, 0.4f, 1.6f);
        lastExperienceTick = now;
    }

    public void recordVirtueExperience(float relief, long now) {
        if (relief <= 0f) {
            return;
        }
        float reduction = MathHelper.clamp(relief * 0.01f, 0f, 0.05f);
        traumaImprint = MathHelper.clamp(traumaImprint - reduction, 0f, 1f);
        virtueResilience = MathHelper.clamp(virtueResilience + reduction * 0.5f, 0.4f, 1.8f);
        lastExperienceTick = now;
    }

    public void relax(long now, double retentionLnPerTick) {
        if (lastExperienceTick == Long.MIN_VALUE) {
            lastExperienceTick = now;
            return;
        }
        long elapsed = now - lastExperienceTick;
        if (elapsed <= 0L) {
            return;
        }
        double factor = retentionLnPerTick == 0d
            ? 1.0d
            : Math.exp(retentionLnPerTick * elapsed);
        traumaImprint = MathHelper.clamp((float) (traumaImprint * factor), 0f, 1f);
        float viceToward = (float) (1.0 + (viceSusceptibility - 1.0) * factor);
        viceSusceptibility = MathHelper.clamp(viceToward, 0.4f, 1.6f);
        float virtueToward = (float) (1.0 + (virtueResilience - 1.0) * factor);
        virtueResilience = MathHelper.clamp(virtueToward, 0.4f, 1.8f);
        lastExperienceTick = now;
    }

    public float viceSusceptibility() {
        return viceSusceptibility;
    }

    public float virtueResilience() {
        return virtueResilience;
    }

    public float traumaImprint() {
        return traumaImprint;
    }
}
