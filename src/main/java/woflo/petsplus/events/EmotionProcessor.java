package woflo.petsplus.events;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.emotions.RoleEmotionRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Emotion processor that provides contextually appropriate emotional responses
 * with proper intensity scaling, personality considerations, and contradiction prevention.
 * Now includes role-based emotion modifiers for immersive role-specific behaviors.
 */
public class EmotionProcessor {
    
    private static final float INTENSITY_DAMPENING_FACTOR = 0.6f;
    private static final float MIN_EMOTION_THRESHOLD = 0.05f;
    
    /**
     * Processes combat damage events with context-aware emotion mapping
     */
    public static void processCombatDamage(MobEntity pet, PetComponent petComp, 
                                         DamageSource source, float amount, 
                                         boolean isOwnerAttacker, boolean isPetVictim) {
        
        try {
            // Get contextually appropriate emotions
            Map<PetComponent.Emotion, Float> newEmotions = EmotionContextMapper.mapCombatDamage(
                pet, petComp, source, amount, isOwnerAttacker, isPetVictim);
            
            if (newEmotions.isEmpty()) {
                return;
            }
            
            // Apply role-based emotion modifiers
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            if (!registry.isInitialized()) {
                registry.initialize();
            }
            Map<PetComponent.Emotion, Float> roleModifiedEmotions = registry.applyCombatModifiers(
                pet, petComp, source, amount, isOwnerAttacker, isPetVictim, newEmotions);
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions = getCurrentEmotionalState(petComp);
            
            // Apply intensity dampening to prevent whiplash
            Map<PetComponent.Emotion, Float> dampenedEmotions = EmotionContradictionResolver
                .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions = EmotionContradictionResolver
                .resolveContradictions(dampenedEmotions);
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions = EmotionContradictionResolver
                .validateContextualAppropriateness(resolvedEmotions, "combat");
            
            // Apply the processed emotions
            applyEmotions(petComp, validatedEmotions);
            
            Petsplus.LOGGER.debug("Processed combat damage emotions for pet {}: {}", 
                pet.getName().getString(), validatedEmotions);
                
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing combat damage emotions for pet {}", 
                pet.getName().getString(), e);
        }
    }
    
    /**
     * Processes social interaction events with context-aware emotion mapping
     */
    public static void processSocialInteraction(MobEntity pet, PetComponent petComp, 
                                              PlayerEntity player, 
                                              EmotionContextMapper.SocialInteractionType type, 
                                              Object context) {
        
        try {
            // Get contextually appropriate emotions
            Map<PetComponent.Emotion, Float> newEmotions = EmotionContextMapper.mapSocialInteraction(
                pet, petComp, player, type, context);
            
            if (newEmotions.isEmpty()) {
                return;
            }
            
            // Apply role-based emotion modifiers
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            if (!registry.isInitialized()) {
                registry.initialize();
            }
            Map<PetComponent.Emotion, Float> roleModifiedEmotions = registry.applySocialModifiers(
                pet, petComp, player, type, context, newEmotions);
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions = getCurrentEmotionalState(petComp);
            
            // Apply intensity dampening
            Map<PetComponent.Emotion, Float> dampenedEmotions = EmotionContradictionResolver
                .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions = EmotionContradictionResolver
                .resolveContradictions(dampenedEmotions);
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions = EmotionContradictionResolver
                .validateContextualAppropriateness(resolvedEmotions, "social");
            
            // Apply the processed emotions
            applyEmotions(petComp, validatedEmotions);
            
            Petsplus.LOGGER.debug("Processed social interaction emotions for pet {}: {}", 
                pet.getName().getString(), validatedEmotions);
                
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing social interaction emotions for pet {}", 
                pet.getName().getString(), e);
        }
    }
    
    /**
     * Processes environmental events with context-aware emotion mapping
     */
    public static void processEnvironmentalEvent(MobEntity pet, PetComponent petComp, 
                                               EmotionContextMapper.EnvironmentalEventType type, 
                                               Object context) {
        
        try {
            // Get contextually appropriate emotions
            Map<PetComponent.Emotion, Float> newEmotions = EmotionContextMapper.mapEnvironmentalEvent(
                pet, petComp, type, context);
            
            if (newEmotions.isEmpty()) {
                return;
            }
            
            // Apply role-based emotion modifiers
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            if (!registry.isInitialized()) {
                registry.initialize();
            }
            Map<PetComponent.Emotion, Float> roleModifiedEmotions = registry.applyEnvironmentalModifiers(
                pet, petComp, type.name(), context, newEmotions);
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions = getCurrentEmotionalState(petComp);
            
            // Apply intensity dampening
            Map<PetComponent.Emotion, Float> dampenedEmotions = EmotionContradictionResolver
                .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions = EmotionContradictionResolver
                .resolveContradictions(dampenedEmotions);
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions = EmotionContradictionResolver
                .validateContextualAppropriateness(resolvedEmotions, type.name().toLowerCase());
            
            // Apply the processed emotions
            applyEmotions(petComp, validatedEmotions);
            
            Petsplus.LOGGER.debug("Processed environmental event emotions for pet {}: {}", 
                pet.getName().getString(), validatedEmotions);
                
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing environmental event emotions for pet {}", 
                pet.getName().getString(), e);
        }
    }
    
    /**
     * Processes high-intensity moments (boss battles, rare discoveries, milestones)
     */
    public static void processHighIntensityMoment(MobEntity pet, PetComponent petComp, 
                                                 String eventType, Object context) {
        
        try {
            // Scale up intensity for high-intensity moments
            float intensityMultiplier = 1.8f;
            
            // Get base emotions for the event type
            Map<PetComponent.Emotion, Float> baseEmotions = getHighIntensityEmotions(eventType, context);
            
            // Scale intensities
            Map<PetComponent.Emotion, Float> scaledEmotions = new EnumMap<>(PetComponent.Emotion.class);
            baseEmotions.forEach((emotion, intensity) -> 
                scaledEmotions.put(emotion, Math.min(1.0f, intensity * intensityMultiplier)));
            
            // Resolve contradictions (high priority emotions win)
            Map<PetComponent.Emotion, Float> resolvedEmotions = EmotionContradictionResolver
                .resolveContradictions(scaledEmotions);
            
            // Apply the high-intensity emotions
            applyEmotions(petComp, resolvedEmotions);
            
            Petsplus.LOGGER.debug("Processed high-intensity moment emotions for pet {}: {}", 
                pet.getName().getString(), resolvedEmotions);
                
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing high-intensity moment emotions for pet {}", 
                pet.getName().getString(), e);
        }
    }
    
    /**
     * Applies emotions to the pet component
     */
    private static void applyEmotions(PetComponent petComp, 
                                            Map<PetComponent.Emotion, Float> emotions) {
        
        for (Map.Entry<PetComponent.Emotion, Float> entry : emotions.entrySet()) {
            PetComponent.Emotion emotion = entry.getKey();
            float intensity = entry.getValue();
            
            // Only apply emotions above minimum threshold
            if (intensity >= MIN_EMOTION_THRESHOLD) {
                petComp.pushEmotion(emotion, intensity);
            }
        }
        
        // Update mood after all emotions are applied
        petComp.updateMood();
    }
    
    /**
     * Gets the current emotional state of the pet
     */
    private static Map<PetComponent.Emotion, Float> getCurrentEmotionalState(PetComponent petComp) {
        Map<PetComponent.Emotion, Float> currentEmotions = new EnumMap<>(PetComponent.Emotion.class);
        
        // For now, return empty map as we don't have direct access to current emotional state
        // This could be enhanced in the future to query the mood engine
        
        return currentEmotions;
    }
    
    /**
     * Gets base emotions for high-intensity moments
     */
    private static Map<PetComponent.Emotion, Float> getHighIntensityEmotions(String eventType, Object context) {
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        
        switch (eventType.toLowerCase()) {
            case "boss_battle" -> {
                emotions.put(PetComponent.Emotion.ANGST, 0.7f);
                emotions.put(PetComponent.Emotion.PROTECTIVENESS, 0.8f);
                emotions.put(PetComponent.Emotion.STOIC, 0.6f);
                emotions.put(PetComponent.Emotion.VIGILANT, 0.7f);
            }
            case "rare_discovery" -> {
                emotions.put(PetComponent.Emotion.GLEE, 0.8f);
                emotions.put(PetComponent.Emotion.CURIOUS, 0.7f);
                emotions.put(PetComponent.Emotion.HOPEFUL, 0.6f);
                emotions.put(PetComponent.Emotion.CHEERFUL, 0.5f);
            }
            case "milestone_achievement" -> {
                emotions.put(PetComponent.Emotion.PRIDE, 0.8f);
                emotions.put(PetComponent.Emotion.HANYAUKU, 0.7f);
                emotions.put(PetComponent.Emotion.CHEERFUL, 0.6f);
                emotions.put(PetComponent.Emotion.UBUNTU, 0.5f);
            }
            case "near_death_experience" -> {
                emotions.put(PetComponent.Emotion.ANGST, 0.9f);
                emotions.put(PetComponent.Emotion.FOREBODING, 0.8f);
                emotions.put(PetComponent.Emotion.RELIEF, 0.7f);
                emotions.put(PetComponent.Emotion.STOIC, 0.6f);
            }
            default -> {
                // Default high-intensity emotions
                emotions.put(PetComponent.Emotion.VIGILANT, 0.6f);
                emotions.put(PetComponent.Emotion.CURIOUS, 0.5f);
            }
        }
        
        return emotions;
    }
    
    /**
     * Validates that emotion processing is working correctly
     */
    public static boolean validateEmotionProcessing(MobEntity pet, PetComponent petComp) {
        try {
            // Test basic emotion application
            Map<PetComponent.Emotion, Float> testEmotions = new EnumMap<>(PetComponent.Emotion.class);
            testEmotions.put(PetComponent.Emotion.CHEERFUL, 0.5f);
            
            applyEmotions(petComp, testEmotions);
            
            // Basic validation - just check that no exceptions were thrown
            return true;
            
        } catch (Exception e) {
            Petsplus.LOGGER.error("Emotion processing validation failed for pet {}",
                pet.getName().getString(), e);
            return false;
        }
    }
    
    /**
     * Gets emotion processing statistics for debugging
     */
    public static String getEmotionProcessingStats(PetComponent petComp) {
        StringBuilder stats = new StringBuilder();
        stats.append("Emotion Processing Stats:\n");
        stats.append("  Processor: Active\n");
        stats.append("  Component: ").append(petComp != null ? "Valid" : "Invalid").append("\n");
        stats.append("  Role Modifiers: ").append(RoleEmotionRegistry.getInstance().isInitialized() ? "Initialized" : "Not Initialized").append("\n");
        
        return stats.toString();
    }
}