package net.woflo.testmod.pet.mood;

import java.util.Objects;

/**
 * A single emotional stimulus applied to the engine. A positive magnitude
 * increases intensity, while a negative magnitude represents recovery.
 */
public record PetStimulus(PetEmotionType emotion, float magnitude) {

    public PetStimulus {
        Objects.requireNonNull(emotion, "emotion");
        if (Float.isNaN(magnitude) || Float.isInfinite(magnitude)) {
            throw new IllegalArgumentException("Stimulus magnitude must be a finite number");
        }
    }

    public static PetStimulus positive(PetEmotionType emotion, float magnitude) {
        return new PetStimulus(emotion, Math.abs(magnitude));
    }

    public static PetStimulus negative(PetEmotionType emotion, float magnitude) {
        return new PetStimulus(emotion, -Math.abs(magnitude));
    }

    public boolean isPositive() {
        return magnitude >= 0f;
    }

    public float absoluteMagnitude() {
        return Math.abs(magnitude);
    }
}
