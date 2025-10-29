package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Definition describing a single morality aspect (virtue or vice). Definitions
 * are loaded from datapacks so designers can author the aspects that feed into
 * the malevolence ledger without touching code.
 */
public final class MoralityAspectDefinition {
    public enum Kind {
        VIRTUE,
        VICE;

        public static Kind fromString(String raw) {
            if (raw == null || raw.isEmpty()) {
                return VICE;
            }
            return switch (raw.trim().toLowerCase()) {
                case "virtue" -> VIRTUE;
                case "vice" -> VICE;
                default -> VICE;
            };
        }
    }

    public static final float TICKS_PER_DAY = 24000f;

    private final Identifier id;
    private final Kind kind;
    private final float baseline;
    private final float persistencePerDay;
    private final double retentionLnPerTick;
    private final float passiveDriftPerDay;
    private final float passiveDriftPerTick;
    private final float impressionability;
    private final Map<Identifier, Float> synergy;

    public MoralityAspectDefinition(
        Identifier id,
        Kind kind,
        float baseline,
        float persistencePerDay,
        float passiveDriftPerDay,
        float impressionability,
        Map<Identifier, Float> synergy
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = kind == null ? Kind.VICE : kind;
        this.baseline = Math.max(0f, Float.isFinite(baseline) ? baseline : 0f);
        this.persistencePerDay = sanitizePersistence(persistencePerDay);
        this.retentionLnPerTick = retentionLnPerTick(this.persistencePerDay);
        this.passiveDriftPerDay = Math.max(0f, Float.isFinite(passiveDriftPerDay) ? passiveDriftPerDay : 0f);
        this.passiveDriftPerTick = this.passiveDriftPerDay / TICKS_PER_DAY;
        this.impressionability = MathHelper.clamp(
            Float.isFinite(impressionability) ? impressionability : 1f,
            0f,
            4f
        );
        if (synergy == null || synergy.isEmpty()) {
            this.synergy = Map.of();
        } else {
            Map<Identifier, Float> sanitized = new LinkedHashMap<>();
            synergy.forEach((key, value) -> {
                if (key == null || value == null || !Float.isFinite(value)) {
                    return;
                }
                sanitized.put(key, value);
            });
            this.synergy = sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
        }
    }

    public Identifier id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public float baseline() {
        return baseline;
    }

    public float persistencePerDay() {
        return persistencePerDay;
    }

    public float passiveDriftPerDay() {
        return passiveDriftPerDay;
    }

    public float passiveDriftPerTick() {
        return passiveDriftPerTick;
    }

    public double retentionLnPerTick() {
        return retentionLnPerTick;
    }

    public float impressionability() {
        return impressionability;
    }

    public Map<Identifier, Float> synergy() {
        return synergy;
    }

    public boolean isVirtue() {
        return kind == Kind.VIRTUE;
    }

    public boolean isVice() {
        return kind == Kind.VICE;
    }

    public static float persistenceFromHalfLife(float halfLifeTicks) {
        if (!Float.isFinite(halfLifeTicks) || halfLifeTicks <= 0f) {
            return 0f;
        }
        double perDay = Math.pow(0.5d, TICKS_PER_DAY / Math.max(1d, halfLifeTicks));
        return MathHelper.clamp((float) perDay, 0f, 1f);
    }

    public static double retentionLnPerTick(float persistencePerDay) {
        if (!Float.isFinite(persistencePerDay)) {
            return 0d;
        }
        float clamped = MathHelper.clamp(persistencePerDay, 0f, 1f);
        if (clamped >= 1f) {
            return 0d;
        }
        if (clamped <= 0f) {
            return Double.NEGATIVE_INFINITY;
        }
        return Math.log(clamped) / TICKS_PER_DAY;
    }

    public static float passiveDriftPerTick(float passiveDriftPerDay) {
        if (!Float.isFinite(passiveDriftPerDay)) {
            return 0f;
        }
        return Math.max(0f, passiveDriftPerDay) / TICKS_PER_DAY;
    }

    private static float sanitizePersistence(float candidate) {
        if (!Float.isFinite(candidate)) {
            return 1f;
        }
        return MathHelper.clamp(candidate, 0f, 1f);
    }
}
