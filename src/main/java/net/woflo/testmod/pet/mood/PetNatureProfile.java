package net.woflo.testmod.pet.mood;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Immutable container describing how a pet's nature biases each emotional
 * channel and the final mood preferences.
 */
public final class PetNatureProfile {
    private final EnumMap<PetEmotionType, Float> stimulusBias;
    private final EnumMap<PetEmotionType, Float> weightBias;
    private final EnumMap<PetEmotionType, Float> recoveryBias;
    private final EnumMap<PetMood, Float> moodBias;
    private final float protectivePersistenceMultiplier;

    private PetNatureProfile(
            EnumMap<PetEmotionType, Float> stimulusBias,
            EnumMap<PetEmotionType, Float> weightBias,
            EnumMap<PetEmotionType, Float> recoveryBias,
            EnumMap<PetMood, Float> moodBias,
            float protectivePersistenceMultiplier
    ) {
        this.stimulusBias = stimulusBias;
        this.weightBias = weightBias;
        this.recoveryBias = recoveryBias;
        this.moodBias = moodBias;
        this.protectivePersistenceMultiplier = protectivePersistenceMultiplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float getStimulusBias(PetEmotionType type) {
        return stimulusBias.getOrDefault(type, 1f);
    }

    public float getWeightBias(PetEmotionType type) {
        return weightBias.getOrDefault(type, 1f);
    }

    public float getRecoveryBias(PetEmotionType type) {
        return recoveryBias.getOrDefault(type, 1f);
    }

    public float getMoodBias(PetMood mood) {
        return moodBias.getOrDefault(mood, 1f);
    }

    public float getProtectivePersistenceMultiplier() {
        return protectivePersistenceMultiplier;
    }

    public static final class Builder {
        private final EnumMap<PetEmotionType, Float> stimulusBias = new EnumMap<>(PetEmotionType.class);
        private final EnumMap<PetEmotionType, Float> weightBias = new EnumMap<>(PetEmotionType.class);
        private final EnumMap<PetEmotionType, Float> recoveryBias = new EnumMap<>(PetEmotionType.class);
        private final EnumMap<PetMood, Float> moodBias = new EnumMap<>(PetMood.class);
        private float protectivePersistenceMultiplier = 1f;

        public Builder stimulusBias(PetEmotionType type, float value) {
            stimulusBias.put(Objects.requireNonNull(type, "type"), sanitize(value));
            return this;
        }

        public Builder weightBias(PetEmotionType type, float value) {
            weightBias.put(Objects.requireNonNull(type, "type"), sanitize(value));
            return this;
        }

        public Builder recoveryBias(PetEmotionType type, float value) {
            recoveryBias.put(Objects.requireNonNull(type, "type"), sanitize(value));
            return this;
        }

        public Builder moodBias(PetMood mood, float value) {
            moodBias.put(Objects.requireNonNull(mood, "mood"), sanitize(value));
            return this;
        }

        public Builder protectivePersistenceMultiplier(float value) {
            this.protectivePersistenceMultiplier = sanitize(value);
            return this;
        }

        private float sanitize(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Bias values must be finite");
            }
            return Math.max(0f, value);
        }

        public PetNatureProfile build() {
            return new PetNatureProfile(
                    new EnumMap<>(stimulusBias),
                    new EnumMap<>(weightBias),
                    new EnumMap<>(recoveryBias),
                    new EnumMap<>(moodBias),
                    protectivePersistenceMultiplier
            );
        }
    }
}
