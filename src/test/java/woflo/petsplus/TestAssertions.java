package woflo.petsplus;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.data.Offset;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.Map;

/**
 * Custom AssertJ assertions for domain-specific validations.
 * Provides fluent, readable assertions for mood engine and pet state.
 */
public final class TestAssertions {
    private TestAssertions() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Entry point for MoodEngine assertions.
     */
    public static MoodEngineAssert assertThat(PetMoodEngine actual) {
        return new MoodEngineAssert(actual);
    }

    /**
     * Custom assertion for PetMoodEngine state validation.
     */
    public static class MoodEngineAssert extends AbstractAssert<MoodEngineAssert, PetMoodEngine> {
        private static final float DEFAULT_TOLERANCE = 0.01f;

        public MoodEngineAssert(PetMoodEngine actual) {
            super(actual, MoodEngineAssert.class);
        }

        /**
         * Asserts the dominant mood matches expected.
         */
        public MoodEngineAssert hasDominantMood(PetComponent.Mood expected) {
            isNotNull();
            PetComponent.Mood actual = this.actual.getCurrentMood();
            if (actual != expected) {
                failWithMessage("Expected dominant mood to be <%s> but was <%s>", expected, actual);
            }
            return this;
        }

        /**
         * Asserts the mood level is within expected range.
         */
        public MoodEngineAssert hasMoodLevel(int expected) {
            isNotNull();
            int actual = this.actual.getMoodLevel();
            if (actual != expected) {
                failWithMessage("Expected mood level to be <%d> but was <%d>", expected, actual);
            }
            return this;
        }

        /**
         * Asserts a specific emotion is active with expected intensity.
         */
        public MoodEngineAssert hasEmotion(PetComponent.Emotion emotion, float expectedIntensity) {
            return hasEmotion(emotion, expectedIntensity, DEFAULT_TOLERANCE);
        }

        /**
         * Asserts a specific emotion is active with expected intensity and tolerance.
         */
        public MoodEngineAssert hasEmotion(PetComponent.Emotion emotion, float expectedIntensity, float tolerance) {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            Float actualIntensity = activeEmotions.get(emotion);
            
            if (actualIntensity == null) {
                failWithMessage("Expected emotion <%s> to be active but it was not found", emotion);
            }
            
            if (Math.abs(actualIntensity - expectedIntensity) > tolerance) {
                failWithMessage(
                    "Expected emotion <%s> to have intensity <%f> (±%f) but was <%f>",
                    emotion, expectedIntensity, tolerance, actualIntensity
                );
            }
            return this;
        }

        /**
         * Asserts an emotion is NOT active.
         */
        public MoodEngineAssert doesNotHaveEmotion(PetComponent.Emotion emotion) {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            if (activeEmotions.containsKey(emotion)) {
                failWithMessage(
                    "Expected emotion <%s> to not be active but it had intensity <%f>",
                    emotion, activeEmotions.get(emotion)
                );
            }
            return this;
        }

        /**
         * Asserts mood strength for a specific mood.
         */
        public MoodEngineAssert hasMoodStrength(PetComponent.Mood mood, float expectedStrength) {
            return hasMoodStrength(mood, expectedStrength, DEFAULT_TOLERANCE);
        }

        /**
         * Asserts mood strength with custom tolerance.
         */
        public MoodEngineAssert hasMoodStrength(PetComponent.Mood mood, float expectedStrength, float tolerance) {
            isNotNull();
            float actualStrength = this.actual.getMoodStrength(mood);
            
            if (Math.abs(actualStrength - expectedStrength) > tolerance) {
                failWithMessage(
                    "Expected mood <%s> strength to be <%f> (±%f) but was <%f>",
                    mood, expectedStrength, tolerance, actualStrength
                );
            }
            return this;
        }

        /**
         * Asserts emotion intensity is greater than threshold.
         */
        public MoodEngineAssert hasEmotionAbove(PetComponent.Emotion emotion, float threshold) {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            Float intensity = activeEmotions.get(emotion);
            
            if (intensity == null || intensity <= threshold) {
                failWithMessage(
                    "Expected emotion <%s> intensity to be above <%f> but was <%s>",
                    emotion, threshold, intensity == null ? "not active" : intensity
                );
            }
            return this;
        }

        /**
         * Asserts emotion intensity is below threshold.
         */
        public MoodEngineAssert hasEmotionBelow(PetComponent.Emotion emotion, float threshold) {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            Float intensity = activeEmotions.getOrDefault(emotion, 0f);
            
            if (intensity > threshold) {
                failWithMessage(
                    "Expected emotion <%s> intensity to be below <%f> but was <%f>",
                    emotion, threshold, intensity
                );
            }
            return this;
        }

        /**
         * Asserts behavioral momentum is within expected range.
         */
        public MoodEngineAssert hasMomentumBetween(float min, float max) {
            isNotNull();
            float actual = this.actual.getBehavioralMomentum();
            
            if (actual < min || actual > max) {
                failWithMessage(
                    "Expected behavioral momentum to be between <%f> and <%f> but was <%f>",
                    min, max, actual
                );
            }
            return this;
        }

        /**
         * Asserts the mood blend is normalized (sums to ~1.0).
         */
        public MoodEngineAssert hasNormalizedMoodBlend() {
            isNotNull();
            Map<PetComponent.Mood, Float> blend = this.actual.getMoodBlend();
            
            float sum = (float) blend.values().stream()
                .mapToDouble(Float::doubleValue)
                .sum();
            
            if (Math.abs(sum - 1.0f) > 0.05f) {
                failWithMessage(
                    "Expected mood blend to sum to ~1.0 but was <%f>. Blend: %s",
                    sum, blend
                );
            }
            return this;
        }

        /**
         * Asserts no emotions are active.
         */
        public MoodEngineAssert hasNoActiveEmotions() {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            
            if (!activeEmotions.isEmpty()) {
                failWithMessage(
                    "Expected no active emotions but found: %s",
                    activeEmotions
                );
            }
            return this;
        }

        /**
         * Asserts at least one emotion is active.
         */
        public MoodEngineAssert hasSomeActiveEmotions() {
            isNotNull();
            Map<PetComponent.Emotion, Float> activeEmotions = this.actual.getActiveEmotions();
            
            if (activeEmotions.isEmpty()) {
                failWithMessage("Expected some active emotions but found none");
            }
            return this;
        }
    }

    /**
     * Asserts float values with default tolerance.
     */
    public static void assertFloatEquals(float actual, float expected) {
        org.assertj.core.api.Assertions.assertThat(actual).isCloseTo(expected, Offset.offset(0.01f));
    }

    /**
     * Asserts float values with custom tolerance.
     */
    public static void assertFloatEquals(float actual, float expected, float tolerance) {
        org.assertj.core.api.Assertions.assertThat(actual).isCloseTo(expected, Offset.offset(tolerance));
    }

    /**
     * Asserts a value is within a range (inclusive).
     */
    public static void assertInRange(float actual, float min, float max) {
        org.assertj.core.api.Assertions.assertThat(actual)
            .withFailMessage("Expected value to be between %f and %f but was %f", min, max, actual)
            .isBetween(min, max);
    }

    /**
     * Asserts a value is clamped (0.0 to 1.0).
     */
    public static void assertClamped(float actual) {
        assertInRange(actual, 0.0f, 1.0f);
    }
}
