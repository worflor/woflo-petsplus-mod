package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.stats.nature.NatureMoralityProfile;

import java.util.Objects;
import java.util.Random;

/**
 * Per-pet behavioral personality system with 5 experiential axes.
 * 
 * Tracks how the pet's behavioral tendencies have shifted based on experiences
 * (deeds, combat, trauma, etc). Axes start near baseline (0.5 neutral, seeded by Nature)
 * and drift based on recorded deeds, decaying back toward neutral over time.
 * 
 * The 5 axes are:
 * - aggressionTendency: 0.0=pacifist, 1.0=bloodthirsty
 * - empathyLevel: 0.0=callous, 1.0=compassionate
 * - courageBaseline: 0.0=cowardly, 1.0=brave
 * - socialOrientation: 0.0=solitary, 1.0=pack-focused
 * - resourceAttitude: 0.0=selfish, 1.0=generous
 * 
 * All axes use exponential decay with configurable retention per tick,
 * identical to the legacy virtue/vice system's persistence mechanics.
 */
public final class MoralityPersona {
    private float viceSusceptibility;
    private float virtueResilience;
    private float traumaImprint;
    
    // Behavioral axes (0.0 = minimum, 1.0 = maximum, seeded from Nature baseline)
    private float aggressionTendency;    // 0.0 = pacifist, 1.0 = bloodthirsty
    private float empathyLevel;          // 0.0 = callous, 1.0 = compassionate
    private float courageBaseline;       // 0.0 = cowardly, 1.0 = brave
    private float socialOrientation;     // 0.0 = solitary, 1.0 = pack-focused
    private float resourceAttitude;      // 0.0 = selfish, 1.0 = generous
    
    private long lastExperienceTick = Long.MIN_VALUE;

    public MoralityPersona(float viceSusceptibility, float virtueResilience, float traumaImprint) {
        this.viceSusceptibility = clampUnit(viceSusceptibility);
        this.virtueResilience = clampUnit(virtueResilience);
        this.traumaImprint = clampUnit(traumaImprint);
        
        // Initialize behavioral axes to neutral (0.5)
        this.aggressionTendency = 0.5f;
        this.empathyLevel = 0.5f;
        this.courageBaseline = 0.5f;
        this.socialOrientation = 0.5f;
        this.resourceAttitude = 0.5f;
    }

    public static MoralityPersona seeded(long seed) {
        Random random = new Random(seed);
        float susceptibility = MathHelper.clamp(0.8f + random.nextFloat() * 0.6f, 0.6f, 1.4f);
        float resilience = MathHelper.clamp(0.8f + random.nextFloat() * 0.6f, 0.6f, 1.4f);
        float trauma = MathHelper.clamp(random.nextFloat() * 0.2f, 0f, 0.35f);
        MoralityPersona persona = new MoralityPersona(susceptibility, resilience, trauma);
        
        // Seed behavioral axes from Nature baseline (0.5 = neutral)
        float variance = 0.15f;
        persona.aggressionTendency = MathHelper.clamp(0.5f + (random.nextFloat() - 0.5f) * variance * 2, 0f, 1f);
        persona.empathyLevel = MathHelper.clamp(0.5f + (random.nextFloat() - 0.5f) * variance * 2, 0f, 1f);
        persona.courageBaseline = MathHelper.clamp(0.5f + (random.nextFloat() - 0.5f) * variance * 2, 0f, 1f);
        persona.socialOrientation = MathHelper.clamp(0.5f + (random.nextFloat() - 0.5f) * variance * 2, 0f, 1f);
        persona.resourceAttitude = MathHelper.clamp(0.5f + (random.nextFloat() - 0.5f) * variance * 2, 0f, 1f);
        
        return persona;
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
        relax(now, retentionLnPerTick, null);
    }

    /**
     * Decay behavioral axes toward Nature-specific baselines.
     * 
     * <p>If a profile is provided, axes decay toward Nature baselines with drift-rate
     * modulation. Otherwise, uses legacy behavior (decay toward 0.5 neutral with normal rate).
     * 
     * @param now current server tick
     * @param retentionLnPerTick base decay rate (negative ln, e.g., -0.001)
     * @param profile Nature morality profile with custom baselines and retention multiplier,
     *                or null to use neutral defaults
     */
    public void relax(long now, double retentionLnPerTick, @Nullable NatureMoralityProfile profile) {
        if (lastExperienceTick == Long.MIN_VALUE) {
            lastExperienceTick = now;
            return;
        }
        long elapsed = now - lastExperienceTick;
        if (elapsed <= 0L) {
            return;
        }
        
        // Use profile if provided, otherwise neutral defaults
        if (profile == null) {
            profile = NatureMoralityProfile.NEUTRAL;
        }
        
        // Apply Nature-specific retention multiplier to base decay rate
        double adjustedRetention = retentionLnPerTick * profile.retentionMultiplier();
        double factor = adjustedRetention == 0d
            ? 1.0d
            : Math.exp(adjustedRetention * elapsed);
        
        // Decay old fields toward baseline
        traumaImprint = MathHelper.clamp((float) (traumaImprint * factor), 0f, 1f);
        float viceToward = (float) (1.0 + (viceSusceptibility - 1.0) * factor);
        viceSusceptibility = MathHelper.clamp(viceToward, 0.4f, 1.6f);
        float virtueToward = (float) (1.0 + (virtueResilience - 1.0) * factor);
        virtueResilience = MathHelper.clamp(virtueToward, 0.4f, 1.8f);
        
        // Decay behavioral axes toward Nature-specific baselines
        aggressionTendency = (float) MathHelper.clamp(
            profile.aggressionBaseline() + (aggressionTendency - profile.aggressionBaseline()) * factor, 0f, 1f);
        empathyLevel = (float) MathHelper.clamp(
            profile.empathyBaseline() + (empathyLevel - profile.empathyBaseline()) * factor, 0f, 1f);
        courageBaseline = (float) MathHelper.clamp(
            profile.courageBaseline() + (courageBaseline - profile.courageBaseline()) * factor, 0f, 1f);
        socialOrientation = (float) MathHelper.clamp(
            profile.socialBaseline() + (socialOrientation - profile.socialBaseline()) * factor, 0f, 1f);
        resourceAttitude = (float) MathHelper.clamp(
            profile.resourceBaseline() + (resourceAttitude - profile.resourceBaseline()) * factor, 0f, 1f);
        
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
    
    // Behavioral axis accessors
    public float aggressionTendency() {
        return aggressionTendency;
    }
    
    public float empathyLevel() {
        return empathyLevel;
    }
    
    public float courageBaseline() {
        return courageBaseline;
    }
    
    public float socialOrientation() {
        return socialOrientation;
    }
    
    public float resourceAttitude() {
        return resourceAttitude;
    }
    
    // Methods to adjust behavioral axes directly
    public void adjustAggression(float delta) {
        aggressionTendency = MathHelper.clamp(aggressionTendency + delta, 0f, 1f);
    }
    
    public void adjustEmpathy(float delta) {
        empathyLevel = MathHelper.clamp(empathyLevel + delta, 0f, 1f);
    }
    
    public void adjustCourage(float delta) {
        courageBaseline = MathHelper.clamp(courageBaseline + delta, 0f, 1f);
    }
    
    public void adjustSocial(float delta) {
        socialOrientation = MathHelper.clamp(socialOrientation + delta, 0f, 1f);
    }
    
    public void adjustResourceAttitude(float delta) {
        resourceAttitude = MathHelper.clamp(resourceAttitude + delta, 0f, 1f);
    }

}
