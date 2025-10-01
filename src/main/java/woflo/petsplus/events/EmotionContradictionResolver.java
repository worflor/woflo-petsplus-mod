package woflo.petsplus.events;

import woflo.petsplus.state.PetComponent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Prevents contradictory emotions from being applied simultaneously.
 * Analyzes emotion sets and resolves conflicts based on priority and context.
 */
public class EmotionContradictionResolver {
    
    /**
     * Groups of emotions that contradict each other
     */
    private static final Map<PetComponent.Emotion, Set<PetComponent.Emotion>> CONTRADICTIONS = Map.of(
        PetComponent.Emotion.ANGST, Set.of(PetComponent.Emotion.CHEERFUL, PetComponent.Emotion.GLEE, PetComponent.Emotion.HOPEFUL),
        PetComponent.Emotion.CHEERFUL, Set.of(PetComponent.Emotion.ANGST, PetComponent.Emotion.FOREBODING, PetComponent.Emotion.ENNUI),
        PetComponent.Emotion.FOREBODING, Set.of(PetComponent.Emotion.HOPEFUL, PetComponent.Emotion.CHEERFUL, PetComponent.Emotion.GLEE),
        PetComponent.Emotion.REGRET, Set.of(PetComponent.Emotion.STOIC, PetComponent.Emotion.GAMAN),
        PetComponent.Emotion.STOIC, Set.of(PetComponent.Emotion.ANGST, PetComponent.Emotion.FRUSTRATION),
        PetComponent.Emotion.FRUSTRATION, Set.of(PetComponent.Emotion.CONTENT, PetComponent.Emotion.CHEERFUL),
        PetComponent.Emotion.CONTENT, Set.of(PetComponent.Emotion.RESTLESS, PetComponent.Emotion.ANGST)
    );
    
    /**
     * Emotion priority levels (higher number = higher priority)
     */
    private static final Map<PetComponent.Emotion, Integer> PRIORITIES = createPriorityMap();
    
    private static Map<PetComponent.Emotion, Integer> createPriorityMap() {
        Map<PetComponent.Emotion, Integer> priorities = new EnumMap<>(PetComponent.Emotion.class);
        
        // Highest priority emotions
        priorities.put(PetComponent.Emotion.PROTECTIVENESS, 9);
        priorities.put(PetComponent.Emotion.PROTECTIVE, 9);
        
        // High priority emotions
        priorities.put(PetComponent.Emotion.ANGST, 8);
        priorities.put(PetComponent.Emotion.STARTLE, 8);
        priorities.put(PetComponent.Emotion.SISU, 8);
        
        priorities.put(PetComponent.Emotion.FOREBODING, 7);
        priorities.put(PetComponent.Emotion.STOIC, 7);
        priorities.put(PetComponent.Emotion.GAMAN, 7);
        priorities.put(PetComponent.Emotion.VIGILANT, 7);
        
        // Medium-high priority emotions
        priorities.put(PetComponent.Emotion.REGRET, 6);
        priorities.put(PetComponent.Emotion.UBUNTU, 6);
        priorities.put(PetComponent.Emotion.KEFI, 6);
        priorities.put(PetComponent.Emotion.LOYALTY, 6);
        priorities.put(PetComponent.Emotion.WORRIED, 6);
        priorities.put(PetComponent.Emotion.DISGUST, 6);
        
        // Medium priority emotions
        priorities.put(PetComponent.Emotion.GLEE, 5);
        priorities.put(PetComponent.Emotion.QUERECIA, 5);
        priorities.put(PetComponent.Emotion.FRUSTRATION, 5);
        priorities.put(PetComponent.Emotion.RESTLESS, 5);
        priorities.put(PetComponent.Emotion.EMPATHY, 4);
        priorities.put(PetComponent.Emotion.RELIEF, 4);
        priorities.put(PetComponent.Emotion.CHEERFUL, 4);
        priorities.put(PetComponent.Emotion.SOBREMESA, 4);
        priorities.put(PetComponent.Emotion.ENNUI, 4);
        priorities.put(PetComponent.Emotion.HIRAETH, 4);
        priorities.put(PetComponent.Emotion.FOCUSED, 5);
        priorities.put(PetComponent.Emotion.PRIDE, 5);
        priorities.put(PetComponent.Emotion.HANYAUKU, 5);
        
        // Medium-low priority emotions
        priorities.put(PetComponent.Emotion.HOPEFUL, 3);
        priorities.put(PetComponent.Emotion.CURIOUS, 3);
        priorities.put(PetComponent.Emotion.FERNWEH, 3);
        priorities.put(PetComponent.Emotion.LAGOM, 3);
        priorities.put(PetComponent.Emotion.SAUDADE, 3);
        priorities.put(PetComponent.Emotion.MELANCHOLY, 3);
        priorities.put(PetComponent.Emotion.PLAYFULNESS, 3);
        
        // Low-medium priority emotions
        priorities.put(PetComponent.Emotion.CONTENT, 2);
        priorities.put(PetComponent.Emotion.WABI_SABI, 2);
        priorities.put(PetComponent.Emotion.YUGEN, 2);
        priorities.put(PetComponent.Emotion.NOSTALGIA, 2);
        
        // Low priority emotions
        priorities.put(PetComponent.Emotion.MONO_NO_AWARE, 1);
        
        return priorities;
    }
    
    /**
     * Resolves contradictory emotions in a set, keeping the highest priority ones
     */
    public static Map<PetComponent.Emotion, Float> resolveContradictions(Map<PetComponent.Emotion, Float> emotions) {
        if (emotions == null || emotions.isEmpty()) {
            return emotions;
        }
        
        Map<PetComponent.Emotion, Float> resolved = new EnumMap<>(PetComponent.Emotion.class);
        
        // Process emotions in priority order
        emotions.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(
                PRIORITIES.getOrDefault(e2.getKey(), 0),
                PRIORITIES.getOrDefault(e1.getKey(), 0)
            ))
            .forEach(entry -> {
                PetComponent.Emotion emotion = entry.getKey();
                float intensity = entry.getValue();
                
                // Check if this emotion contradicts any already-resolved emotions
                if (!hasContradiction(emotion, resolved.keySet())) {
                    resolved.put(emotion, intensity);
                } else {
                    // Remove contradictory lower-priority emotions
                    removeContradictory(emotion, resolved);
                    resolved.put(emotion, intensity);
                }
            });
        
        return resolved;
    }
    
    /**
     * Checks if an emotion contradicts any emotions in the given set
     */
    private static boolean hasContradiction(PetComponent.Emotion emotion, Set<PetComponent.Emotion> existing) {
        Set<PetComponent.Emotion> contradictions = CONTRADICTIONS.get(emotion);
        if (contradictions == null) {
            return false;
        }
        
        return existing.stream().anyMatch(contradictions::contains);
    }
    
    /**
     * Removes emotions that contradict the given emotion from the resolved map
     */
    private static void removeContradictory(PetComponent.Emotion emotion, Map<PetComponent.Emotion, Float> resolved) {
        Set<PetComponent.Emotion> contradictions = CONTRADICTIONS.get(emotion);
        if (contradictions == null) {
            return;
        }
        
        contradictions.forEach(resolved::remove);
    }
    
    /**
     * Applies intensity dampening to reduce emotional whiplash
     */
    public static Map<PetComponent.Emotion, Float> applyIntensityDampening(
            Map<PetComponent.Emotion, Float> newEmotions,
            Map<PetComponent.Emotion, Float> currentEmotions,
            float dampeningFactor) {
        
        if (newEmotions == null || newEmotions.isEmpty()) {
            return newEmotions;
        }
        
        Map<PetComponent.Emotion, Float> dampened = new EnumMap<>(PetComponent.Emotion.class);
        
        for (Map.Entry<PetComponent.Emotion, Float> entry : newEmotions.entrySet()) {
            PetComponent.Emotion emotion = entry.getKey();
            float newIntensity = entry.getValue();
            
            // Check if this emotion contradicts current emotions
            Float currentIntensity = currentEmotions.get(emotion);
            if (currentIntensity != null && currentIntensity > 0f) {
                // Same emotion family - allow full intensity
                dampened.put(emotion, newIntensity);
            } else if (hasContradiction(emotion, currentEmotions.keySet())) {
                // Contradictory emotion - apply dampening
                float dampenedIntensity = newIntensity * dampeningFactor;
                if (dampenedIntensity > 0.05f) { // Minimum threshold
                    dampened.put(emotion, dampenedIntensity);
                }
            } else {
                // New emotion family - allow full intensity
                dampened.put(emotion, newIntensity);
            }
        }
        
        return dampened;
    }
    
    /**
     * Validates that emotions are contextually appropriate
     */
    public static Map<PetComponent.Emotion, Float> validateContextualAppropriateness(
            Map<PetComponent.Emotion, Float> emotions,
            String context) {
        
        if (emotions == null || emotions.isEmpty()) {
            return emotions;
        }
        
        Map<PetComponent.Emotion, Float> validated = new EnumMap<>(PetComponent.Emotion.class);
        
        for (Map.Entry<PetComponent.Emotion, Float> entry : emotions.entrySet()) {
            PetComponent.Emotion emotion = entry.getKey();
            float intensity = entry.getValue();
            
            if (isContextuallyAppropriate(emotion, context)) {
                validated.put(emotion, intensity);
            } else {
                // Reduce intensity for inappropriate emotions
                float reducedIntensity = intensity * 0.3f;
                if (reducedIntensity > 0.05f) {
                    validated.put(emotion, reducedIntensity);
                }
            }
        }
        
        return validated;
    }
    
    /**
     * Checks if an emotion is appropriate for the given context
     */
    private static boolean isContextuallyAppropriate(PetComponent.Emotion emotion, String context) {
        if (context == null) {
            return true;
        }
        
        return switch (context.toLowerCase()) {
            case "combat", "danger" -> !isPositiveEmotion(emotion) || isHighPriorityEmotion(emotion);
            case "social", "affection" -> !isNegativeEmotion(emotion) || isHighPriorityEmotion(emotion);
            case "discovery", "exploration" -> emotion == PetComponent.Emotion.CURIOUS || 
                                              emotion == PetComponent.Emotion.HOPEFUL ||
                                              emotion == PetComponent.Emotion.GLEE ||
                                              isHighPriorityEmotion(emotion);
            case "rest", "safety" -> emotion == PetComponent.Emotion.CONTENT ||
                                   emotion == PetComponent.Emotion.CHEERFUL ||
                                   emotion == PetComponent.Emotion.RELIEF ||
                                   isHighPriorityEmotion(emotion);
            default -> true;
        };
    }
    
    private static boolean isPositiveEmotion(PetComponent.Emotion emotion) {
        return emotion == PetComponent.Emotion.CHEERFUL ||
               emotion == PetComponent.Emotion.GLEE ||
               emotion == PetComponent.Emotion.HOPEFUL ||
               emotion == PetComponent.Emotion.CONTENT ||
               emotion == PetComponent.Emotion.RELIEF ||
               emotion == PetComponent.Emotion.UBUNTU ||
               emotion == PetComponent.Emotion.QUERECIA ||
               emotion == PetComponent.Emotion.KEFI ||
               emotion == PetComponent.Emotion.LOYALTY ||
               emotion == PetComponent.Emotion.PLAYFULNESS ||
               emotion == PetComponent.Emotion.EMPATHY ||
               emotion == PetComponent.Emotion.PRIDE;
    }
    
    private static boolean isNegativeEmotion(PetComponent.Emotion emotion) {
        return emotion == PetComponent.Emotion.ANGST ||
               emotion == PetComponent.Emotion.FOREBODING ||
               emotion == PetComponent.Emotion.REGRET ||
               emotion == PetComponent.Emotion.FRUSTRATION ||
               emotion == PetComponent.Emotion.STARTLE ||
               emotion == PetComponent.Emotion.DISGUST ||
               emotion == PetComponent.Emotion.ENNUI ||
               emotion == PetComponent.Emotion.WORRIED ||
               emotion == PetComponent.Emotion.RESTLESS ||
               emotion == PetComponent.Emotion.MELANCHOLY ||
               emotion == PetComponent.Emotion.SAUDADE ||
               emotion == PetComponent.Emotion.HIRAETH;
    }
    
    private static boolean isHighPriorityEmotion(PetComponent.Emotion emotion) {
        return PRIORITIES.getOrDefault(emotion, 0) >= 7;
    }
}