package net.woflo.testmod.pet.mood;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates mood blends from individual emotional channels while allowing the
 * pet's nature to influence both stimulus processing and the final mood.
 */
public final class PetMoodEngine {
    private static final float EPSILON = 1.0e-6f;

    private final PetNatureProfile natureProfile;
    private final EnumMap<PetEmotionType, EmotionState> emotions = new EnumMap<>(PetEmotionType.class);
    private OwnerContext ownerContext;

    public PetMoodEngine(PetNatureProfile natureProfile) {
        this(natureProfile, OwnerContext.safe());
    }

    public PetMoodEngine(PetNatureProfile natureProfile, OwnerContext ownerContext) {
        this.natureProfile = Objects.requireNonNull(natureProfile, "natureProfile");
        this.ownerContext = Objects.requireNonNull(ownerContext, "ownerContext");
        for (PetEmotionType type : PetEmotionType.values()) {
            emotions.put(type, new EmotionState());
        }
    }

    public void setOwnerContext(OwnerContext ownerContext) {
        this.ownerContext = Objects.requireNonNull(ownerContext, "ownerContext");
    }

    public OwnerContext getOwnerContext() {
        return ownerContext;
    }

    /**
     * Applies a stimulus to the tracked emotion, allowing nature to bias both
     * positive gains and negative recovery.
     */
    public void pushEmotion(PetStimulus stimulus) {
        Objects.requireNonNull(stimulus, "stimulus");
        EmotionState state = emotions.get(stimulus.emotion());
        float magnitude = stimulus.absoluteMagnitude();
        if (magnitude <= EPSILON) {
            return;
        }

        float weightBias = natureProfile.getWeightBias(stimulus.emotion());
        if (stimulus.isPositive()) {
            float stimulusBias = natureProfile.getStimulusBias(stimulus.emotion());
            float scaled = magnitude * stimulusBias;
            state.intensity += scaled;
            state.biasedWeight += scaled * weightBias;
        } else {
            float recoveryBias = natureProfile.getRecoveryBias(stimulus.emotion());
            float scaled = magnitude * recoveryBias;
            state.intensity = Math.max(0f, state.intensity - scaled);
            state.biasedWeight = Math.max(0f, state.biasedWeight - scaled * weightBias);
        }
    }

    /**
     * Produces a normalized mood blend map. The result is empty if every
     * emotional channel is fully dormant.
     */
    public Map<PetMood, Float> calculateMoodBlend() {
        EnumMap<PetMood, Float> targetBlend = new EnumMap<>(PetMood.class);
        for (Map.Entry<PetEmotionType, EmotionState> entry : emotions.entrySet()) {
            PetEmotionType emotion = entry.getKey();
            EmotionState state = entry.getValue();
            float danger = emotion == PetEmotionType.PROTECTIVE ? ownerContext.normalizedDangerLevel() : 0f;
            boolean persistent = hasOngoingCondition(emotion, danger);
            if (!persistent && state.isEmpty()) {
                continue;
            }

            float biasedIntensity = state.intensity * natureProfile.getWeightBias(emotion);
            float weight = Math.max(state.biasedWeight, biasedIntensity);

            if (emotion == PetEmotionType.PROTECTIVE && danger > EPSILON) {
                float persistence = danger * natureProfile.getProtectivePersistenceMultiplier();
                weight = Math.max(weight, persistence);
            }

            if (weight <= EPSILON) {
                continue;
            }

            PetMood mood = translate(emotion);
            float accumulated = targetBlend.getOrDefault(mood, 0f) + weight;
            targetBlend.put(mood, accumulated);
        }

        if (targetBlend.isEmpty()) {
            return Collections.emptyMap();
        }

        float biasedTotal = 0f;
        for (var iterator = targetBlend.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<PetMood, Float> entry = iterator.next();
            float biased = entry.getValue() * natureProfile.getMoodBias(entry.getKey());
            if (biased <= EPSILON) {
                iterator.remove();
                continue;
            }
            entry.setValue(biased);
            biasedTotal += biased;
        }

        if (biasedTotal <= EPSILON) {
            return Collections.emptyMap();
        }

        for (Map.Entry<PetMood, Float> entry : targetBlend.entrySet()) {
            entry.setValue(entry.getValue() / biasedTotal);
        }

        return Map.copyOf(targetBlend);
    }

    public float getEmotionIntensity(PetEmotionType emotion) {
        return emotions.get(emotion).intensity;
    }

    private boolean hasOngoingCondition(PetEmotionType emotion, float normalizedDanger) {
        if (emotion == PetEmotionType.PROTECTIVE) {
            return normalizedDanger > EPSILON;
        }
        return false;
    }

    private PetMood translate(PetEmotionType emotion) {
        return switch (emotion) {
            case HAPPY -> PetMood.HAPPY;
            case CALM -> PetMood.CALM;
            case PROTECTIVE -> PetMood.ALERT;
            case FEAR, FRUSTRATION -> PetMood.ANXIOUS;
        };
    }

    private static final class EmotionState {
        private float intensity;
        private float biasedWeight;

        private boolean isEmpty() {
            return intensity <= EPSILON && biasedWeight <= EPSILON;
        }
    }
}
