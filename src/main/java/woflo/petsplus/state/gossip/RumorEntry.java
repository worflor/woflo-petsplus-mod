package woflo.petsplus.state.gossip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Represents a single rumor held by a pet. Stores lightweight metadata about
 * how recently the rumor was heard or shared so that gossip routines can
 * efficiently reason about freshness.
 */
public final class RumorEntry {

    public static final Codec<RumorEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("topic").forGetter(RumorEntry::topicId),
        Codec.FLOAT.fieldOf("intensity").forGetter(RumorEntry::intensity),
        Codec.FLOAT.fieldOf("confidence").forGetter(RumorEntry::confidence),
        Codec.LONG.fieldOf("lastHeard").forGetter(RumorEntry::lastHeardTick),
        Codec.LONG.optionalFieldOf("lastShared", 0L).forGetter(RumorEntry::lastSharedTick),
        Codec.INT.optionalFieldOf("shares").forGetter(entry -> entry.shareCount() == 0
            ? Optional.empty() : Optional.of(entry.shareCount())),
        Uuids.CODEC.optionalFieldOf("source").forGetter(entry -> Optional.ofNullable(entry.sourceUuid)),
        TextCodecs.CODEC.optionalFieldOf("paraphrase")
            .forGetter(entry -> Optional.ofNullable(entry.paraphrased)),
        Codec.LONG.optionalFieldOf("lastWitness")
            .forGetter(entry -> entry.lastWitnessTick <= 0L ? Optional.empty() : Optional.of(entry.lastWitnessTick))
    ).apply(instance, (topic, intensity, confidence, lastHeard, lastShared, shares, source, paraphrase, witness) ->
        new RumorEntry(topic, intensity, confidence, lastHeard, lastShared, shares.orElse(0),
            source.orElse(null), paraphrase.orElse(null), witness.orElse(0L))));

    private final long topicId;
    private float intensity;
    private float confidence;
    private long lastHeardTick;
    private long lastSharedTick;
    private int shareCount;
    private long lastWitnessTick;
    @Nullable
    private UUID sourceUuid;
    @Nullable
    private Text paraphrased;

    RumorEntry(long topicId, float intensity, float confidence, long lastHeardTick,
               long lastSharedTick, int shareCount, @Nullable UUID sourceUuid,
               @Nullable Text paraphrased, long lastWitnessTick) {
        this.topicId = topicId;
        this.intensity = clamp(intensity);
        this.confidence = clamp(confidence);
        this.lastHeardTick = Math.max(0L, lastHeardTick);
        this.lastSharedTick = Math.max(0L, lastSharedTick);
        this.shareCount = Math.max(0, shareCount);
        this.sourceUuid = sourceUuid;
        this.paraphrased = sanitizeParaphrased(paraphrased);
        this.lastWitnessTick = Math.max(0L, lastWitnessTick);
    }

    public static RumorEntry create(long topicId, float intensity, float confidence, long heardTick,
                                    @Nullable UUID sourceUuid, @Nullable Text paraphrased) {
        return new RumorEntry(topicId, intensity, confidence, heardTick, 0L, 0, sourceUuid, paraphrased, 0L);
    }

    public RumorEntry copy() {
        return new RumorEntry(topicId, intensity, confidence, lastHeardTick,
            lastSharedTick, shareCount, sourceUuid,
            paraphrased == null ? null : paraphrased.copy(), lastWitnessTick);
    }

    public long topicId() {
        return topicId;
    }

    public float intensity() {
        return intensity;
    }

    public float confidence() {
        return confidence;
    }

    public long lastHeardTick() {
        return lastHeardTick;
    }

    public long lastSharedTick() {
        return lastSharedTick;
    }

    public int shareCount() {
        return shareCount;
    }

    public long lastWitnessTick() {
        return lastWitnessTick;
    }

    @Nullable
    public UUID sourceUuid() {
        return sourceUuid;
    }

    public @Nullable Text paraphrased() {
        return paraphrased;
    }

    public @Nullable Text paraphrasedCopy() {
        return paraphrased == null ? null : paraphrased.copy();
    }

    void reinforce(float addedIntensity, float addedConfidence, long heardTick,
                   @Nullable UUID sourceUuid, @Nullable Text paraphrase,
                   boolean corroborated) {
        float intensitySample = clamp(addedIntensity);
        float confidenceSample = clamp(addedConfidence);
        if (corroborated) {
            this.intensity = clamp(Math.max(this.intensity, intensitySample));
            float gain = (1.0f - this.confidence) * 0.35f;
            this.confidence = clamp(this.confidence + gain + confidenceSample * 0.2f);
        } else {
            this.intensity = clamp((this.intensity * 0.65f) + (intensitySample * 0.35f));
            this.confidence = clamp(Math.max(this.confidence, confidenceSample));
            if (this.confidence < 0.85f) {
                this.confidence = clamp(this.confidence + 0.05f);
            }
        }
        this.lastHeardTick = Math.max(this.lastHeardTick, heardTick);
        if (sourceUuid != null) {
            this.sourceUuid = sourceUuid;
        }
        if (paraphrase != null) {
            this.paraphrased = sanitizeParaphrased(paraphrase);
        }
    }

    void markShared(long currentTick) {
        this.lastSharedTick = Math.max(this.lastSharedTick, currentTick);
        this.shareCount = Math.min(255, this.shareCount + 1);
        this.intensity = clamp(this.intensity * 0.94f);
    }

    void applyDecay(long currentTick) {
        long sinceHeard = Math.max(0L, currentTick - lastHeardTick);
        if (sinceHeard <= 0L) {
            return;
        }
        float decayFactor = Math.min(1.0f, sinceHeard / 1200.0f);
        float intensityDrop = 0.02f * decayFactor;
        float confidenceDrop = (shareCount == 0 ? 0.015f : 0.01f) * decayFactor;
        this.intensity = clamp(this.intensity - intensityDrop);
        this.confidence = clamp(this.confidence - confidenceDrop);
    }

    boolean shouldShare(long currentTick, long cooldownTicks, float minIntensity, float minConfidence) {
        if (this.intensity < minIntensity && this.confidence < minConfidence) {
            return false;
        }
        return currentTick - this.lastSharedTick >= cooldownTicks;
    }

    boolean isExpired(long currentTick, long maxAge) {
        if (this.intensity <= 0f && this.confidence <= 0f) {
            return true;
        }
        return currentTick - this.lastHeardTick > maxAge;
    }

    boolean heardRecently(long currentTick, long window) {
        if (window <= 0L) {
            return false;
        }
        return currentTick - this.lastHeardTick <= window;
    }

    void registerDuplicate(long currentTick) {
        this.lastHeardTick = Math.max(this.lastHeardTick, currentTick);
        this.intensity = clamp(this.intensity * 0.96f);
        this.confidence = clamp(this.confidence - 0.015f);
        if (this.shareCount > 0) {
            this.shareCount = Math.max(0, this.shareCount - 1);
        }
    }

    void markWitness(long currentTick) {
        long normalized = Math.max(0L, currentTick);
        if (normalized <= 0L) {
            return;
        }
        this.lastWitnessTick = Math.max(this.lastWitnessTick, normalized);
        this.lastHeardTick = Math.max(this.lastHeardTick, this.lastWitnessTick);
    }

    boolean witnessedRecently(long currentTick, long window) {
        if (window <= 0L || this.lastWitnessTick <= 0L) {
            return false;
        }
        return currentTick - this.lastWitnessTick <= window;
    }

    private static float clamp(float value) {
        return MathHelper.clamp(value, 0f, 1f);
    }

    private static @Nullable Text sanitizeParaphrased(@Nullable Text paraphrased) {
        if (paraphrased == null) {
            return null;
        }
        String raw = paraphrased.getString();
        if (raw == null || raw.strip().isEmpty()) {
            return null;
        }
        return paraphrased.copy();
    }
}
