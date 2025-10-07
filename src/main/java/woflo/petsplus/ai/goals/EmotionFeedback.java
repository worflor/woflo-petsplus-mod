package woflo.petsplus.ai.goals;

import woflo.petsplus.state.PetComponent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lightweight, immutable emotion feedback definition for goals.
 * Uses flyweight pattern - goals define these once and reuse them.
 * Zero allocation during normal operation.
 */
public record EmotionFeedback(
    Map<PetComponent.Emotion, Float> emotions,
    boolean triggersContagion,
    PetComponent.Emotion contagionEmotion,
    float contagionStrength
) {
    
    /**
     * Empty feedback - no emotions triggered.
     */
    public static final EmotionFeedback NONE = new EmotionFeedback(
        Map.of(), false, null, 0f
    );
    
    /**
     * Builder for creating emotion feedback definitions.
     * Optimized for common cases to minimize allocations.
     */
    public static class Builder {
        private final Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        private boolean triggersContagion = false;
        private PetComponent.Emotion contagionEmotion = null;
        private float contagionStrength = 0f;
        
        /**
         * Add an emotion with intensity.
         * @param emotion The emotion to trigger
         * @param intensity 0.0-1.0+ intensity (typical range 0.05-0.50)
         */
        public Builder add(PetComponent.Emotion emotion, float intensity) {
            if (emotion != null && intensity > 0f) {
                emotions.put(emotion, intensity);
            }
            return this;
        }
        
        /**
         * Enable emotional contagion for this feedback.
         * Spreads the specified emotion to nearby pets at reduced intensity.
         * @param emotion The emotion to spread
         * @param strength Base strength for contagion (typically 0.010-0.025)
         */
        public Builder withContagion(PetComponent.Emotion emotion, float strength) {
            if (emotion != null && strength > 0f) {
                this.triggersContagion = true;
                this.contagionEmotion = emotion;
                this.contagionStrength = strength;
            }
            return this;
        }
        
        public EmotionFeedback build() {
            if (emotions.isEmpty()) {
                return NONE;
            }
            // Create immutable copy
            return new EmotionFeedback(
                Map.copyOf(emotions),
                triggersContagion,
                contagionEmotion,
                contagionStrength
            );
        }
    }
    
    /**
     * Create a simple feedback with one emotion.
     * Optimized factory method for common single-emotion case.
     */
    public static EmotionFeedback single(PetComponent.Emotion emotion, float intensity) {
        return new Builder().add(emotion, intensity).build();
    }
    
    /**
     * Create feedback with two emotions.
     * Optimized factory method for common two-emotion case.
     */
    public static EmotionFeedback dual(
        PetComponent.Emotion emotion1, float intensity1,
        PetComponent.Emotion emotion2, float intensity2
    ) {
        return new Builder()
            .add(emotion1, intensity1)
            .add(emotion2, intensity2)
            .build();
    }
    
    /**
     * Create feedback with three emotions.
     * Optimized factory method for common three-emotion case.
     */
    public static EmotionFeedback triple(
        PetComponent.Emotion emotion1, float intensity1,
        PetComponent.Emotion emotion2, float intensity2,
        PetComponent.Emotion emotion3, float intensity3
    ) {
        return new Builder()
            .add(emotion1, intensity1)
            .add(emotion2, intensity2)
            .add(emotion3, intensity3)
            .build();
    }
    
    /**
     * Check if this feedback is empty (no emotions).
     */
    public boolean isEmpty() {
        return emotions.isEmpty();
    }
    
    /**
     * Get the number of emotions in this feedback.
     */
    public int size() {
        return emotions.size();
    }
}
