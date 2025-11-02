package woflo.petsplus.state.gossip;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates gossip topics and rumors dynamically from emotion snapshots.
 * Maps emotional states to relevant concrete topics with intensity/confidence derived
 * from the emotion's characteristics (intensity, cadence, volatility).
 */
public final class EmotionGossipGenerator {

    private EmotionGossipGenerator() {
    }

    /**
     * Represents a candidate gossip event generated from an emotion snapshot.
     */
    public record EmotionGossipCandidate(
        long topicId,
        float intensity,
        float confidence,
        @Nullable Text paraphrase,
        boolean witnessed
    ) {
    }

    /**
     * Generate a gossip candidate from an emotion snapshot.
     * Uses emotion type to select appropriate topic, then scales intensity/confidence
     * based on the spike characteristics.
     */
    public static Optional<EmotionGossipCandidate> fromSnapshot(
        PetMoodEngine.EmotionSnapshot snapshot,
        @Nullable UUID sourceUuid
    ) {
        if (snapshot == null) {
            return Optional.empty();
        }

        PetComponent.Emotion emotion = snapshot.emotion();
        float emotionIntensity = snapshot.intensity();
        boolean witnessed = snapshot.witnessed();

        // Map emotion → topic + base paraphrase
        EmotionTopicMapping mapping = getTopicMapping(emotion);
        if (mapping == null) {
            return Optional.empty();
        }

        // Derive rumor intensity from emotion intensity
        float rumorIntensity = deriveIntensity(emotionIntensity, mapping);

        // Derive rumor confidence from emotion characteristics
        // Witnessed emotions get confidence boost
        float rumorConfidence = deriveConfidence(emotionIntensity, witnessed, mapping);

        // Generate paraphrase with dynamic intensity + witness context
        Text paraphrase = mapping.paraphraseKey() != null
            ? buildParaphrase(rumorIntensity, witnessed, mapping)
            : null;

        return Optional.of(new EmotionGossipCandidate(
            mapping.topicId(),
            rumorIntensity,
            rumorConfidence,
            paraphrase,
            witnessed
        ));
    }

    private static float deriveIntensity(float emotionIntensity, EmotionTopicMapping mapping) {
        // Scale emotion intensity to rumor intensity (0-1 range)
        // High emotion intensity → strong rumor
        float base = emotionIntensity * mapping.intensityScale();
        return Math.min(1.0f, base * 0.9f + 0.1f);  // Avoid extremes
    }

    private static float deriveConfidence(float emotionIntensity, boolean witnessed, EmotionTopicMapping mapping) {
        // Base confidence from emotion intensity
        float base = emotionIntensity * 0.6f + 0.2f;

        // Boost if the owner was present
        if (witnessed) {
            base += 0.25f;
        }

        // Apply mapping-specific modifier
        base *= mapping.confidenceScale();

        return Math.min(1.0f, base);
    }

    private static Text buildParaphrase(float rumorIntensity, boolean witnessed, EmotionTopicMapping mapping) {
        String intensityKey = describeIntensityKey(rumorIntensity);
        Text intensityText = Text.translatable("petsplus.gossip.emotion.intensity." + intensityKey);
        Text baseText = Text.translatable(mapping.paraphraseKey());
        Text witnessText = Text.translatable(witnessed
            ? "petsplus.gossip.emotion.witness.owner"
            : "petsplus.gossip.emotion.witness.none");
        return Text.translatable("petsplus.gossip.emotion.story", intensityText, baseText, witnessText);
    }

    private static String describeIntensityKey(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (clamped >= 0.85f) {
            return "surging";
        }
        if (clamped >= 0.6f) {
            return "vivid";
        }
        if (clamped >= 0.4f) {
            return "warm";
        }
        return "fleeting";
    }

    @Nullable
    private static EmotionTopicMapping getTopicMapping(PetComponent.Emotion emotion) {
        return EMOTION_TO_TOPIC.get(emotion);
    }

    // ===== EMOTION-TO-TOPIC MAPPINGS =====
    private static final Map<PetComponent.Emotion, EmotionTopicMapping> EMOTION_TO_TOPIC = buildEmotionTopicMap();

    private static Map<PetComponent.Emotion, EmotionTopicMapping> buildEmotionTopicMap() {
        Map<PetComponent.Emotion, EmotionTopicMapping> map = new HashMap<>();

        // Combat-related emotions
        map.put(PetComponent.Emotion.ANGST,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/anxiety/tense_moment"),
                0.7f, 0.65f,
                "petsplus.gossip.emotion.base.angst"));

        map.put(PetComponent.Emotion.FOREBODING,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/fear/ominous"),
                0.8f, 0.70f,
                "petsplus.gossip.emotion.base.foreboding"));

        map.put(PetComponent.Emotion.STARTLE,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/fear/sudden_fright"),
                0.6f, 0.60f,
                "petsplus.gossip.emotion.base.startle"));

        map.put(PetComponent.Emotion.RELIEF,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/joy/safe_again"),
                0.5f, 0.55f,
                "petsplus.gossip.emotion.base.relief"));

        // Positive emotions
        map.put(PetComponent.Emotion.CHEERFUL,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/joy/cheerful_day"),
                0.7f, 0.65f,
                "petsplus.gossip.emotion.base.cheerful"));

        map.put(PetComponent.Emotion.PLAYFULNESS,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/joy/playful_romp"),
                0.6f, 0.60f,
                "petsplus.gossip.emotion.base.playfulness"));

        map.put(PetComponent.Emotion.CONTENT,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/peace/contentment"),
                0.5f, 0.65f,
                "petsplus.gossip.emotion.base.content"));

        // Loneliness/separation emotions
        map.put(PetComponent.Emotion.SAUDADE,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/longing/saudade"),
                0.75f, 0.70f,
                "petsplus.gossip.emotion.base.saudade"));

        map.put(PetComponent.Emotion.HOPEFUL,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/hope/optimism"),
                0.6f, 0.60f,
                "petsplus.gossip.emotion.base.hopeful"));

        // Frustration & conflict
        map.put(PetComponent.Emotion.FRUSTRATION,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/annoyance/frustration"),
                0.65f, 0.55f,
                "petsplus.gossip.emotion.base.frustration"));

        map.put(PetComponent.Emotion.REGRET,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/remorse/regret"),
                0.70f, 0.65f,
                "petsplus.gossip.emotion.base.regret"));

        // Curiosity & discovery
        map.put(PetComponent.Emotion.LAGOM,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/balance/harmony"),
                0.5f, 0.65f,
                "petsplus.gossip.emotion.base.lagom"));

        map.put(PetComponent.Emotion.UBUNTU,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/connection/togetherness"),
                0.6f, 0.70f,
                "petsplus.gossip.emotion.base.ubuntu"));

        // Philosophical emotions
        map.put(PetComponent.Emotion.STOIC,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/acceptance/stoicism"),
                0.55f, 0.60f,
                "petsplus.gossip.emotion.base.stoic"));

        map.put(PetComponent.Emotion.DISGUST,
            new EmotionTopicMapping(GossipTopics.concrete("emotion/aversion/disgust"),
                0.65f, 0.55f,
                "petsplus.gossip.emotion.base.disgust"));

        return map;
    }

    /**
     * Mapping of emotion to gossip topic with scaling factors.
     */
    private record EmotionTopicMapping(
        long topicId,
        float intensityScale,
        float confidenceScale,
        @Nullable String paraphraseKey
    ) {
    }
}
