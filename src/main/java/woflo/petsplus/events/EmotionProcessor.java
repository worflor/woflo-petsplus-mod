package woflo.petsplus.events;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.emotions.RoleEmotionRegistry;

import java.util.EnumMap;
import java.util.Map;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Emotion processor that provides contextually appropriate emotional responses
 * with proper intensity scaling, personality considerations, and contradiction prevention.
 * Now includes role-based emotion modifiers for immersive role-specific behaviors.
 */
public class EmotionProcessor {
    
    private static final float INTENSITY_DAMPENING_FACTOR = 0.6f;
    private static final float MIN_EMOTION_THRESHOLD = 0.05f;
    
    // Thread safety: Lock for synchronizing access to pet state
    private static final ReentrantLock petStateLock = new ReentrantLock();
    
    // Custom exceptions for specific error handling
    public static class EmotionProcessingException extends Exception {
        public EmotionProcessingException(String message) {
            super(message);
        }
        
        public EmotionProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class InvalidPetStateException extends EmotionProcessingException {
        public InvalidPetStateException(String message) {
            super(message);
        }
    }
    
    public static class EmotionMappingException extends EmotionProcessingException {
        public EmotionMappingException(String message) {
            super(message);
        }
        
        public EmotionMappingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Validates input parameters for emotion processing methods
     */
    private static void validateInputParameters(MobEntity pet, PetComponent petComp) throws EmotionProcessingException {
        if (pet == null) {
            throw new EmotionProcessingException("Pet entity cannot be null");
        }
        if (petComp == null) {
            throw new EmotionProcessingException("Pet component cannot be null");
        }
        if (!pet.isAlive()) {
            throw new EmotionProcessingException("Pet entity must be alive");
        }
    }
    
    /**
     * Safely gets pet name for logging purposes
     */
    private static String getPetNameForLogging(MobEntity pet) {
        if (pet == null || pet.getName() == null) {
            return "unknown_pet";
        }
        try {
            Text nameText = pet.getName();
            return nameText != null ? nameText.getString() : "unnamed_pet";
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to get pet name for logging", e);
            return "error_getting_name";
        }
    }
    
    /**
     * Validates emotional state map for consistency
     */
    private static void validateEmotionalState(Map<PetComponent.Emotion, Float> emotions) throws InvalidPetStateException {
        if (emotions == null) {
            throw new InvalidPetStateException("Emotion map cannot be null");
        }
        
        for (Map.Entry<PetComponent.Emotion, Float> entry : emotions.entrySet()) {
            if (entry.getKey() == null) {
                throw new InvalidPetStateException("Emotion key cannot be null");
            }
            if (entry.getValue() == null) {
                throw new InvalidPetStateException("Emotion intensity cannot be null");
            }
            if (entry.getValue() < 0f || entry.getValue() > 1f) {
                throw new InvalidPetStateException(
                    String.format("Emotion intensity must be between 0 and 1, got %f for emotion %s",
                        entry.getValue(), entry.getKey()));
            }
        }
    }
    
    /**
     * Recovers from invalid emotional state by creating a safe default state
     */
    private static Map<PetComponent.Emotion, Float> recoverEmotionalState() {
        Map<PetComponent.Emotion, Float> recoveryState = new EnumMap<>(PetComponent.Emotion.class);
        // Set a minimal neutral emotional state
        recoveryState.put(PetComponent.Emotion.CONTENT, 0.1f);
        return recoveryState;
    }
    
    /**
     * Validates and initializes registry with proper error handling
     */
    private static void validateAndInitializeRegistry() throws EmotionProcessingException {
        try {
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            if (registry == null) {
                throw new EmotionProcessingException("RoleEmotionRegistry instance is null");
            }
            
            if (!registry.isInitialized()) {
                try {
                    registry.initialize();
                    if (!registry.isInitialized()) {
                        throw new EmotionProcessingException("Failed to initialize RoleEmotionRegistry");
                    }
                } catch (Exception e) {
                    throw new EmotionProcessingException("Error initializing RoleEmotionRegistry", e);
                }
            }
        } catch (Exception e) {
            if (e instanceof EmotionProcessingException) {
                throw e;
            }
            throw new EmotionProcessingException("Unexpected error validating registry", e);
        }
    }
    
    /**
     * Validates return values from EmotionContextMapper methods
     */
    private static Map<PetComponent.Emotion, Float> validateEmotionMappingResult(
            Map<PetComponent.Emotion, Float> emotions, String mappingType) throws EmotionMappingException {
        
        if (emotions == null) {
            Petsplus.LOGGER.warn("EmotionContextMapper returned null for mapping type: {}", mappingType);
            return new EnumMap<>(PetComponent.Emotion.class);
        }
        
        try {
            validateEmotionalState(emotions);
            return emotions;
        } catch (InvalidPetStateException e) {
            throw new EmotionMappingException("Invalid emotion mapping result for " + mappingType + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes combat damage events with context-aware emotion mapping
     */
    public static void processCombatDamage(MobEntity pet, PetComponent petComp,
                                         DamageSource source, float amount,
                                         boolean isOwnerAttacker, boolean isPetVictim) {
        
        String petName = getPetNameForLogging(pet);
        
        try {
            // Input validation
            validateInputParameters(pet, petComp);
            if (source == null) {
                throw new EmotionProcessingException("DamageSource cannot be null");
            }
            if (amount < 0) {
                throw new EmotionProcessingException("Damage amount cannot be negative");
            }
            
            // Validate and initialize registry
            validateAndInitializeRegistry();
            
            // Get contextually appropriate emotions with validation
            Map<PetComponent.Emotion, Float> newEmotions;
            try {
                newEmotions = EmotionContextMapper.mapCombatDamage(
                    pet, petComp, source, amount, isOwnerAttacker, isPetVictim);
                newEmotions = validateEmotionMappingResult(newEmotions, "combat_damage");
            } catch (Exception e) {
                throw new EmotionMappingException("Failed to map combat damage emotions", e);
            }
            
            if (newEmotions.isEmpty()) {
                Petsplus.LOGGER.debug("No emotions to process for pet {} in combat damage", petName);
                return;
            }
            
            // Apply role-based emotion modifiers with error handling
            Map<PetComponent.Emotion, Float> roleModifiedEmotions;
            try {
                RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
                roleModifiedEmotions = registry.applyCombatModifiers(
                    pet, petComp, source, amount, isOwnerAttacker, isPetVictim, newEmotions);
                validateEmotionalState(roleModifiedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying role combat modifiers for pet {}, using base emotions: {}",
                    petName, e.getMessage());
                roleModifiedEmotions = newEmotions; // Fallback to base emotions
            }
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions;
            try {
                currentEmotions = getCurrentEmotionalState(petComp);
                validateEmotionalState(currentEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error getting current emotional state for pet {}, using empty state: {}",
                    petName, e.getMessage());
                currentEmotions = new EnumMap<>(PetComponent.Emotion.class);
            }
            
            // Apply intensity dampening to prevent whiplash
            Map<PetComponent.Emotion, Float> dampenedEmotions;
            try {
                dampenedEmotions = EmotionContradictionResolver
                    .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
                validateEmotionalState(dampenedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying intensity dampening for pet {}, using role-modified emotions: {}",
                    petName, e.getMessage());
                dampenedEmotions = roleModifiedEmotions;
            }
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions;
            try {
                resolvedEmotions = EmotionContradictionResolver
                    .resolveContradictions(dampenedEmotions);
                validateEmotionalState(resolvedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error resolving contradictions for pet {}, using dampened emotions: {}",
                    petName, e.getMessage());
                resolvedEmotions = dampenedEmotions;
            }
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions;
            try {
                validatedEmotions = EmotionContradictionResolver
                    .validateContextualAppropriateness(resolvedEmotions, "combat");
                validateEmotionalState(validatedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error validating contextual appropriateness for pet {}, using resolved emotions: {}",
                    petName, e.getMessage());
                validatedEmotions = resolvedEmotions;
            }
            
            // Apply the processed emotions
            try {
                applyEmotions(petComp, validatedEmotions);
            } catch (Exception e) {
                throw new EmotionProcessingException("Failed to apply emotions to pet component", e);
            }
            
            Petsplus.LOGGER.debug("Processed combat damage emotions for pet {}: {}",
                petName, validatedEmotions);
                
        } catch (EmotionProcessingException e) {
            Petsplus.LOGGER.error("Emotion processing error for pet {}: {}", petName, e.getMessage(), e);
            // Attempt recovery by applying a minimal safe emotional state
            try {
                Map<PetComponent.Emotion, Float> recoveryEmotions = recoverEmotionalState();
                applyEmotions(petComp, recoveryEmotions);
                Petsplus.LOGGER.info("Applied recovery emotional state for pet {}", petName);
            } catch (Exception recoveryException) {
                Petsplus.LOGGER.error("Failed to apply recovery emotional state for pet {}", petName, recoveryException);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Unexpected error processing combat damage emotions for pet {}", petName, e);
        }
    }
    
    /**
     * Processes social interaction events with context-aware emotion mapping
     */
    public static void processSocialInteraction(MobEntity pet, PetComponent petComp,
                                              PlayerEntity player,
                                              EmotionContextMapper.SocialInteractionType type,
                                              Object context) {
        
        String petName = getPetNameForLogging(pet);
        
        try {
            // Input validation
            validateInputParameters(pet, petComp);
            if (player == null) {
                throw new EmotionProcessingException("PlayerEntity cannot be null");
            }
            if (type == null) {
                throw new EmotionProcessingException("SocialInteractionType cannot be null");
            }
            
            // Validate and initialize registry
            validateAndInitializeRegistry();
            
            // Get contextually appropriate emotions with validation
            Map<PetComponent.Emotion, Float> newEmotions;
            try {
                newEmotions = EmotionContextMapper.mapSocialInteraction(
                    pet, petComp, player, type, context);
                newEmotions = validateEmotionMappingResult(newEmotions, "social_interaction");
            } catch (Exception e) {
                throw new EmotionMappingException("Failed to map social interaction emotions", e);
            }
            
            if (newEmotions.isEmpty()) {
                Petsplus.LOGGER.debug("No emotions to process for pet {} in social interaction", petName);
                return;
            }
            
            // Apply role-based emotion modifiers with error handling
            Map<PetComponent.Emotion, Float> roleModifiedEmotions;
            try {
                RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
                roleModifiedEmotions = registry.applySocialModifiers(
                    pet, petComp, player, type, context, newEmotions);
                validateEmotionalState(roleModifiedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying role social modifiers for pet {}, using base emotions: {}",
                    petName, e.getMessage());
                roleModifiedEmotions = newEmotions; // Fallback to base emotions
            }
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions;
            try {
                currentEmotions = getCurrentEmotionalState(petComp);
                validateEmotionalState(currentEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error getting current emotional state for pet {}, using empty state: {}",
                    petName, e.getMessage());
                currentEmotions = new EnumMap<>(PetComponent.Emotion.class);
            }
            
            // Apply intensity dampening
            Map<PetComponent.Emotion, Float> dampenedEmotions;
            try {
                dampenedEmotions = EmotionContradictionResolver
                    .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
                validateEmotionalState(dampenedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying intensity dampening for pet {}, using role-modified emotions: {}",
                    petName, e.getMessage());
                dampenedEmotions = roleModifiedEmotions;
            }
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions;
            try {
                resolvedEmotions = EmotionContradictionResolver
                    .resolveContradictions(dampenedEmotions);
                validateEmotionalState(resolvedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error resolving contradictions for pet {}, using dampened emotions: {}",
                    petName, e.getMessage());
                resolvedEmotions = dampenedEmotions;
            }
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions;
            try {
                validatedEmotions = EmotionContradictionResolver
                    .validateContextualAppropriateness(resolvedEmotions, "social");
                validateEmotionalState(validatedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error validating contextual appropriateness for pet {}, using resolved emotions: {}",
                    petName, e.getMessage());
                validatedEmotions = resolvedEmotions;
            }
            
            // Apply the processed emotions
            try {
                applyEmotions(petComp, validatedEmotions);
            } catch (Exception e) {
                throw new EmotionProcessingException("Failed to apply emotions to pet component", e);
            }
            
            Petsplus.LOGGER.debug("Processed social interaction emotions for pet {}: {}",
                petName, validatedEmotions);
                
        } catch (EmotionProcessingException e) {
            Petsplus.LOGGER.error("Emotion processing error for pet {}: {}", petName, e.getMessage(), e);
            // Attempt recovery by applying a minimal safe emotional state
            try {
                Map<PetComponent.Emotion, Float> recoveryEmotions = recoverEmotionalState();
                applyEmotions(petComp, recoveryEmotions);
                Petsplus.LOGGER.info("Applied recovery emotional state for pet {}", petName);
            } catch (Exception recoveryException) {
                Petsplus.LOGGER.error("Failed to apply recovery emotional state for pet {}", petName, recoveryException);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Unexpected error processing social interaction emotions for pet {}", petName, e);
        }
    }
    
    /**
     * Processes environmental events with context-aware emotion mapping
     */
    public static void processEnvironmentalEvent(MobEntity pet, PetComponent petComp,
                                               EmotionContextMapper.EnvironmentalEventType type,
                                               Object context) {
        
        String petName = getPetNameForLogging(pet);
        
        try {
            // Input validation
            validateInputParameters(pet, petComp);
            if (type == null) {
                throw new EmotionProcessingException("EnvironmentalEventType cannot be null");
            }
            
            // Validate and initialize registry
            validateAndInitializeRegistry();
            
            // Get contextually appropriate emotions with validation
            Map<PetComponent.Emotion, Float> newEmotions;
            try {
                newEmotions = EmotionContextMapper.mapEnvironmentalEvent(
                    pet, petComp, type, context);
                newEmotions = validateEmotionMappingResult(newEmotions, "environmental_event");
            } catch (Exception e) {
                throw new EmotionMappingException("Failed to map environmental event emotions", e);
            }
            
            if (newEmotions.isEmpty()) {
                Petsplus.LOGGER.debug("No emotions to process for pet {} in environmental event", petName);
                return;
            }
            
            // Apply role-based emotion modifiers with error handling
            Map<PetComponent.Emotion, Float> roleModifiedEmotions;
            try {
                RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
                roleModifiedEmotions = registry.applyEnvironmentalModifiers(
                    pet, petComp, type.name(), context, newEmotions);
                validateEmotionalState(roleModifiedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying role environmental modifiers for pet {}, using base emotions: {}",
                    petName, e.getMessage());
                roleModifiedEmotions = newEmotions; // Fallback to base emotions
            }
            
            // Get current emotional state for dampening
            Map<PetComponent.Emotion, Float> currentEmotions;
            try {
                currentEmotions = getCurrentEmotionalState(petComp);
                validateEmotionalState(currentEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error getting current emotional state for pet {}, using empty state: {}",
                    petName, e.getMessage());
                currentEmotions = new EnumMap<>(PetComponent.Emotion.class);
            }
            
            // Apply intensity dampening
            Map<PetComponent.Emotion, Float> dampenedEmotions;
            try {
                dampenedEmotions = EmotionContradictionResolver
                    .applyIntensityDampening(roleModifiedEmotions, currentEmotions, INTENSITY_DAMPENING_FACTOR);
                validateEmotionalState(dampenedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error applying intensity dampening for pet {}, using role-modified emotions: {}",
                    petName, e.getMessage());
                dampenedEmotions = roleModifiedEmotions;
            }
            
            // Resolve contradictions
            Map<PetComponent.Emotion, Float> resolvedEmotions;
            try {
                resolvedEmotions = EmotionContradictionResolver
                    .resolveContradictions(dampenedEmotions);
                validateEmotionalState(resolvedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error resolving contradictions for pet {}, using dampened emotions: {}",
                    petName, e.getMessage());
                resolvedEmotions = dampenedEmotions;
            }
            
            // Validate contextual appropriateness
            Map<PetComponent.Emotion, Float> validatedEmotions;
            try {
                validatedEmotions = EmotionContradictionResolver
                    .validateContextualAppropriateness(resolvedEmotions, type.name().toLowerCase());
                validateEmotionalState(validatedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error validating contextual appropriateness for pet {}, using resolved emotions: {}",
                    petName, e.getMessage());
                validatedEmotions = resolvedEmotions;
            }
            
            // Apply the processed emotions
            try {
                applyEmotions(petComp, validatedEmotions);
            } catch (Exception e) {
                throw new EmotionProcessingException("Failed to apply emotions to pet component", e);
            }
            
            Petsplus.LOGGER.debug("Processed environmental event emotions for pet {}: {}",
                petName, validatedEmotions);
                
        } catch (EmotionProcessingException e) {
            Petsplus.LOGGER.error("Emotion processing error for pet {}: {}", petName, e.getMessage(), e);
            // Attempt recovery by applying a minimal safe emotional state
            try {
                Map<PetComponent.Emotion, Float> recoveryEmotions = recoverEmotionalState();
                applyEmotions(petComp, recoveryEmotions);
                Petsplus.LOGGER.info("Applied recovery emotional state for pet {}", petName);
            } catch (Exception recoveryException) {
                Petsplus.LOGGER.error("Failed to apply recovery emotional state for pet {}", petName, recoveryException);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Unexpected error processing environmental event emotions for pet {}", petName, e);
        }
    }
    
    /**
     * Processes high-intensity moments (boss battles, rare discoveries, milestones)
     */
    public static void processHighIntensityMoment(MobEntity pet, PetComponent petComp,
                                                 String eventType, Object context) {
        
        String petName = getPetNameForLogging(pet);
        
        try {
            // Input validation
            validateInputParameters(pet, petComp);
            if (eventType == null || eventType.trim().isEmpty()) {
                throw new EmotionProcessingException("EventType cannot be null or empty");
            }
            
            // Scale up intensity for high-intensity moments
            float intensityMultiplier = 1.8f;
            
            // Get base emotions for the event type with validation
            Map<PetComponent.Emotion, Float> baseEmotions;
            try {
                baseEmotions = getHighIntensityEmotions(eventType, context);
                validateEmotionalState(baseEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error getting high-intensity emotions for pet {}, using default emotions: {}",
                    petName, e.getMessage());
                baseEmotions = new EnumMap<>(PetComponent.Emotion.class);
                baseEmotions.put(PetComponent.Emotion.VIGILANT, 0.6f);
                baseEmotions.put(PetComponent.Emotion.CURIOUS, 0.5f);
            }
            
            // Scale intensities with validation
            Map<PetComponent.Emotion, Float> scaledEmotions = new EnumMap<>(PetComponent.Emotion.class);
            try {
                baseEmotions.forEach((emotion, intensity) -> {
                    if (emotion != null && intensity != null && intensity >= 0) {
                        float scaledIntensity = Math.min(1.0f, intensity * intensityMultiplier);
                        if (scaledIntensity >= MIN_EMOTION_THRESHOLD) {
                            scaledEmotions.put(emotion, scaledIntensity);
                        }
                    }
                });
                validateEmotionalState(scaledEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error scaling intensities for pet {}, using base emotions: {}",
                    petName, e.getMessage());
                scaledEmotions.clear();
                scaledEmotions.putAll(baseEmotions);
            }
            
            // Resolve contradictions (high priority emotions win)
            Map<PetComponent.Emotion, Float> resolvedEmotions;
            try {
                resolvedEmotions = EmotionContradictionResolver
                    .resolveContradictions(scaledEmotions);
                validateEmotionalState(resolvedEmotions);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Error resolving contradictions for pet {}, using scaled emotions: {}",
                    petName, e.getMessage());
                resolvedEmotions = scaledEmotions;
            }
            
            // Apply the high-intensity emotions
            try {
                applyEmotions(petComp, resolvedEmotions);
            } catch (Exception e) {
                throw new EmotionProcessingException("Failed to apply emotions to pet component", e);
            }
            
            Petsplus.LOGGER.debug("Processed high-intensity moment emotions for pet {}: {}",
                petName, resolvedEmotions);
                
        } catch (EmotionProcessingException e) {
            Petsplus.LOGGER.error("Emotion processing error for pet {}: {}", petName, e.getMessage(), e);
            // Attempt recovery by applying a minimal safe emotional state
            try {
                Map<PetComponent.Emotion, Float> recoveryEmotions = recoverEmotionalState();
                applyEmotions(petComp, recoveryEmotions);
                Petsplus.LOGGER.info("Applied recovery emotional state for pet {}", petName);
            } catch (Exception recoveryException) {
                Petsplus.LOGGER.error("Failed to apply recovery emotional state for pet {}", petName, recoveryException);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Unexpected error processing high-intensity moment emotions for pet {}", petName, e);
        }
    }
    
    /**
     * Applies emotions to the pet component with thread safety
     */
    private static void applyEmotions(PetComponent petComp,
                                            Map<PetComponent.Emotion, Float> emotions) {
        // Thread safety: Synchronize access to pet state to prevent race conditions
        petStateLock.lock();
        try {
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
        } finally {
            petStateLock.unlock();
        }
    }
    
    /**
     * Gets the current emotional state of the pet with thread safety
     */
    private static Map<PetComponent.Emotion, Float> getCurrentEmotionalState(PetComponent petComp) {
        // Thread safety: Synchronize access to pet state to prevent race conditions
        petStateLock.lock();
        try {
            Map<PetComponent.Emotion, Float> currentEmotions = new EnumMap<>(PetComponent.Emotion.class);
            
            // For now, return empty map as we don't have direct access to current emotional state
            // This could be enhanced in the future to query the mood engine
            
            return currentEmotions;
        } finally {
            petStateLock.unlock();
        }
    }
    
    /**
     * Gets base emotions for high-intensity moments
     */
    private static Map<PetComponent.Emotion, Float> getHighIntensityEmotions(String eventType, Object context) {
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        
        switch (eventType.toLowerCase()) {
            case "boss_battle" -> {
                emotions.put(PetComponent.Emotion.ANGST, 0.7f);
                emotions.put(PetComponent.Emotion.GUARDIAN_VIGIL, 0.8f);
                emotions.put(PetComponent.Emotion.STOIC, 0.6f);
                emotions.put(PetComponent.Emotion.VIGILANT, 0.7f);
            }
            case "rare_discovery" -> {
                emotions.put(PetComponent.Emotion.CHEERFUL, 0.8f);
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
        String petName = getPetNameForLogging(pet);
        
        try {
            // Input validation
            validateInputParameters(pet, petComp);
            
            // Test basic emotion application
            Map<PetComponent.Emotion, Float> testEmotions = new EnumMap<>(PetComponent.Emotion.class);
            testEmotions.put(PetComponent.Emotion.CHEERFUL, 0.5f);
            validateEmotionalState(testEmotions);
            
            // Validate and initialize registry
            validateAndInitializeRegistry();
            
            applyEmotions(petComp, testEmotions);
            
            // Basic validation - just check that no exceptions were thrown
            Petsplus.LOGGER.debug("Emotion processing validation successful for pet {}", petName);
            return true;
            
        } catch (EmotionProcessingException e) {
            Petsplus.LOGGER.error("Emotion processing validation failed for pet {}: {}", petName, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Unexpected error during emotion processing validation for pet {}", petName, e);
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
        
        try {
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            stats.append("  Role Modifiers: ").append(registry != null && registry.isInitialized() ? "Initialized" : "Not Initialized").append("\n");
            if (registry != null) {
                stats.append("  Registered Modifiers: ").append(registry.getAllModifiers().size()).append("\n");
            }
        } catch (Exception e) {
            stats.append("  Role Modifiers: Error accessing registry\n");
            Petsplus.LOGGER.warn("Error accessing RoleEmotionRegistry for stats", e);
        }
        
        stats.append("  Error Handling: Enhanced\n");
        stats.append("  State Validation: Active\n");
        
        return stats.toString();
    }
}
