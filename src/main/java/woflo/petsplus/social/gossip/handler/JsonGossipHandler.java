package woflo.petsplus.social.gossip.handler;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.*;

import static woflo.petsplus.data.GossipHandlerDataLoader.*;

/**
 * JSON-backed implementation of GossipStimulusHandler.
 * <p>
 * Created from datapack JSON definitions, this handler evaluates context checks,
 * builds emergent topic IDs, and generates rumors using template-based narratives.
 */
public final class JsonGossipHandler implements GossipStimulusHandler {
    
    private final Identifier id;
    private final String stimulusType;
    private final int priority;
    private final long cooldownTicks;
    private final boolean suppressive;
    private final List<ContextCheck> contextChecks;
    private final TopicBuilderConfig topicConfig;
    private final List<ConditionalTemplateGroup> conditionalTemplates;
    private final FormulaConfig intensityFormula;
    private final FormulaConfig confidenceFormula;
    
    public JsonGossipHandler(
        Identifier id,
        String stimulusType,
        int priority,
        long cooldownTicks,
        boolean suppressive,
        List<ContextCheck> contextChecks,
        TopicBuilderConfig topicConfig,
        List<ConditionalTemplateGroup> conditionalTemplates,
        FormulaConfig intensityFormula,
        FormulaConfig confidenceFormula
    ) {
        this.id = Objects.requireNonNull(id, "Handler ID cannot be null");
        this.stimulusType = Objects.requireNonNull(stimulusType, "Stimulus type cannot be null");
        this.priority = priority;
        this.cooldownTicks = cooldownTicks;
        this.suppressive = suppressive;
        this.contextChecks = contextChecks != null ? List.copyOf(contextChecks) : Collections.emptyList();
        this.topicConfig = topicConfig;
        this.conditionalTemplates = conditionalTemplates != null ? List.copyOf(conditionalTemplates) : Collections.emptyList();
        this.intensityFormula = intensityFormula;
        this.confidenceFormula = confidenceFormula;
    }
    
    @Override
    public Identifier getId() {
        return id;
    }
    
    @Override
    public String getStimulusType() {
        return stimulusType;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public long getCooldownTicks() {
        return cooldownTicks;
    }
    
    @Override
    public boolean isSuppressive() {
        return suppressive;
    }
    
    @Override
    public boolean shouldHandle(PetComponent pet, PerceptionStimulus stimulus) {
        // Check if stimulus type matches
        if (!stimulus.type().toString().equals(stimulusType) && !stimulusType.equals("*")) {
            return false;
        }
        
        // Evaluate all context checks
        for (ContextCheck check : contextChecks) {
            if (!evaluateContextCheck(pet, stimulus, check)) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public List<RumorEntry> generateRumors(PetComponent pet, PerceptionStimulus stimulus) {
        List<RumorEntry> rumors = new ArrayList<>();
        
        try {
            // Build topic ID using context
            long topicId = buildTopicId(pet, stimulus);
            
            // Calculate intensity and confidence
            double intensity = calculateIntensity(pet, stimulus);
            double confidence = calculateConfidence(pet, stimulus);
            
            // Select narrative template based on conditions
            String narrativeText = selectConditionalTemplate(pet, stimulus, intensity);
            if (narrativeText == null) {
                Petsplus.LOGGER.debug("Gossip handler {} could not select a template for stimulus {}", 
                    id, stimulus.type());
                return rumors; // No matching template
            }
            
            net.minecraft.text.Text paraphrased = net.minecraft.text.Text.literal(narrativeText);
            
            // Create rumor entry using factory method
            RumorEntry rumor = RumorEntry.create(
                topicId,
                (float) intensity,
                (float) confidence,
                stimulus.tick(),
                null, // source UUID - gossip is self-generated
                paraphrased
            );
            
            rumors.add(rumor);
            
            // Debug logging for successful rumor generation
            if (Petsplus.LOGGER.isDebugEnabled()) {
                Petsplus.LOGGER.debug("Gossip handler {} generated rumor: {} (intensity={}, confidence={})",
                    id, narrativeText, intensity, confidence);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error generating rumor from handler {}", id, e);
        }
        
        return rumors;
    }
    
    private boolean evaluateContextCheck(PetComponent pet, PerceptionStimulus stimulus, ContextCheck check) {
        // Context checks evaluate state conditions
        // For now, return true for all checks since StateKey integration needs more work
        // TODO: Implement actual StateKey lookup:
        // Object stateValue = pet.getStateData(StateKeys.valueOf(check.stateKey()));
        // Then apply comparison logic (exists, equals, greater_than, etc.)
        return true; // Permissive until StateKey integration
    }
    
    private long buildTopicId(PetComponent pet, PerceptionStimulus stimulus) {
        GossipTopicBuilder builder = GossipTopicBuilder.fromPath(topicConfig.basePath());
        
        // Append emotion if configured
        if (topicConfig.appendEmotion() && stimulus.payload() instanceof PetMoodEngine.EmotionSnapshot emotion) {
            builder.append(emotion.emotion().name().toLowerCase());
        }
        
        // Append intensity qualifier if configured
        if (topicConfig.appendIntensity()) {
            double intensity = calculateIntensity(pet, stimulus);
            builder.appendIntensity(intensity);
        }
        
        // Append combat state if configured
        if (topicConfig.appendCombatState()) {
            // Would check StateKeys.COMBAT_ENGAGED here
            // builder.appendState("combat", pet.getStateData(StateKeys.COMBAT_ENGAGED) != null);
        }
        
        return builder.build();
    }
    
    /**
     * Selects a narrative template based on conditional logic.
     * Evaluates conditions against stimulus payload and returns a random matching template.
     * Uses seeded randomization for determinism based on topic and tick.
     * 
     * @param pet The pet component (for potential future context)
     * @param stimulus The perception stimulus containing the payload to evaluate
     * @param intensity The calculated intensity value (for potential future use)
     * @return A randomly selected template from the first matching group, or null if no match
     */
    private String selectConditionalTemplate(PetComponent pet, PerceptionStimulus stimulus, double intensity) {
        if (conditionalTemplates.isEmpty()) {
            Petsplus.LOGGER.warn("Gossip handler {} has no template groups configured", id);
            return null;
        }
        
        // Iterate through template groups in order
        for (int i = 0; i < conditionalTemplates.size(); i++) {
            ConditionalTemplateGroup group = conditionalTemplates.get(i);
            if (evaluateConditions(group.conditions(), stimulus)) {
                // All conditions matched - select random template from this group
                List<String> templates = group.templates();
                if (templates.isEmpty()) {
                    Petsplus.LOGGER.warn("Gossip handler {} group {} has no templates", id, i);
                    continue;
                }
                
                // Use seeded random for determinism (same stimulus tick = same template)
                long seed = stimulus.tick() ^ id.hashCode();
                Random random = new Random(seed);
                String selected = templates.get(random.nextInt(templates.size()));
                
                if (Petsplus.LOGGER.isDebugEnabled()) {
                    Petsplus.LOGGER.debug("Gossip handler {} matched group {} ({} conditions, {} templates)",
                        id, i, group.conditions().size(), templates.size());
                }
                
                return selected;
            }
        }
        
        // No conditional groups matched
        if (Petsplus.LOGGER.isDebugEnabled()) {
            Petsplus.LOGGER.debug("Gossip handler {} found no matching template group for stimulus {}", 
                id, stimulus.type());
        }
        return null;
    }
    
    /**
     * Evaluates all conditions in a group using AND logic.
     * 
     * @param conditions List of conditions to evaluate (empty = unconditional match)
     * @param stimulus The stimulus containing the payload to check
     * @return true if all conditions pass, false if any fail
     */
    private boolean evaluateConditions(List<TemplateCondition> conditions, PerceptionStimulus stimulus) {
        if (conditions.isEmpty()) {
            return true; // Unconditional fallback group
        }
        
        Object payload = stimulus.payload();
        if (payload == null) {
            return false; // Cannot evaluate conditions without payload
        }
        
        // ALL conditions must pass (AND logic)
        for (TemplateCondition condition : conditions) {
            if (!evaluateSingleCondition(condition, payload)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Evaluates a single condition by extracting the field value and applying the operator.
     * 
     * @param condition The condition to evaluate
     * @param payload The stimulus payload to extract values from
     * @return true if the condition passes, false otherwise
     */
    private boolean evaluateSingleCondition(TemplateCondition condition, Object payload) {
        try {
            String key = condition.key();
            String operator = condition.operator();
            Object expectedValue = condition.value();
            
            // Extract actual value from payload
            Object actualValue = extractFieldValue(payload, key);
            
            // Apply operator
            boolean result = switch (operator.toLowerCase()) {
                case "equals" -> compareEquals(actualValue, expectedValue);
                case "greater_than" -> compareGreaterThan(actualValue, expectedValue);
                case "less_than" -> compareLessThan(actualValue, expectedValue);
                case "exists" -> actualValue != null;
                default -> {
                    Petsplus.LOGGER.warn("Gossip handler {} has unknown operator '{}' for key '{}'",
                        id, operator, key);
                    yield false;
                }
            };
            
            if (Petsplus.LOGGER.isDebugEnabled() && !result) {
                Petsplus.LOGGER.debug("Gossip handler {} condition failed: {} {} {} (actual: {})",
                    id, key, operator, expectedValue, actualValue);
            }
            
            return result;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error evaluating condition in gossip handler {}: {}", 
                id, condition, e);
            return false;
        }
    }
    
    /**
     * Extracts a field value from a stimulus payload object.
     * Uses type-specific extraction for performance, with reflection fallback.
     * 
     * @param payload The stimulus payload object
     * @param fieldName The field/property name to extract
     * @return The extracted value, or null if field doesn't exist
     */
    private Object extractFieldValue(Object payload, String fieldName) {
        // Fast path: Type-specific extraction for known payload types
        
        // OwnerFocusSnapshot (OWNER_ACTIVITY stimulus)
        if (payload instanceof woflo.petsplus.state.processing.OwnerFocusSnapshot snapshot) {
            return switch (fieldName) {
                case "crouching" -> snapshot.crouching();
                case "usingItem" -> snapshot.usingItem();
                case "screenOpen" -> snapshot.screenOpen();
                case "handsBusy" -> snapshot.handsBusy();
                case "isSleeping" -> snapshot.isSleeping();
                default -> null;
            };
        }
        
        // BehaviouralEnergyProfile (ENERGY_PROFILE stimulus)
        if (payload instanceof woflo.petsplus.state.emotions.BehaviouralEnergyProfile profile) {
            return switch (fieldName) {
                case "momentum" -> profile.momentum();
                case "rawPhysicalActivity" -> profile.rawPhysicalActivity();
                case "rawMentalActivity" -> profile.rawMentalActivity();
                case "rawSocialActivity" -> profile.rawSocialActivity();
                case "normalizedPhysicalActivity" -> profile.normalizedPhysicalActivity();
                case "normalizedMentalActivity" -> profile.normalizedMentalActivity();
                case "normalizedSocialActivity" -> profile.normalizedSocialActivity();
                case "physicalStamina" -> profile.physicalStamina();
                case "mentalFocus" -> profile.mentalFocus();
                case "socialCharge" -> profile.socialCharge();
                default -> null;
            };
        }
        
        // PetContextCrowdSummary (CROWD_SUMMARY stimulus)
        if (payload instanceof woflo.petsplus.ai.context.PetContextCrowdSummary summary) {
            return switch (fieldName) {
                case "friendlyCount" -> summary.friendlyCount();
                case "hostileCount" -> summary.hostileCount();
                case "neutralCount" -> summary.neutralCount();
                case "nearestFriendlyDistance" -> summary.nearestFriendlyDistance();
                case "nearestHostileDistance" -> summary.nearestHostileDistance();
                case "babyFriendlyCount" -> summary.babyFriendlyCount();
                case "babyHostileCount" -> summary.babyHostileCount();
                case "babyNeutralCount" -> summary.babyNeutralCount();
                default -> null;
            };
        }
        
        // Map<Mood, Float> (MOOD_BLEND stimulus)
        if (payload instanceof java.util.Map<?, ?> map) {
            try {
                PetComponent.Mood mood = PetComponent.Mood.valueOf(fieldName);
                Object value = map.get(mood);
                return value instanceof Number number ? number.floatValue() : null;
            } catch (IllegalArgumentException e) {
                return null; // Not a valid mood enum name
            }
        }
        
        // EmotionSnapshot (EMOTION_SAMPLE stimulus)
        if (payload instanceof PetMoodEngine.EmotionSnapshot emotion) {
            return switch (fieldName) {
                case "emotion" -> emotion.emotion().name();
                case "intensity" -> emotion.intensity();
                case "witnessed" -> emotion.witnessed();
                default -> null;
            };
        }
        
        // EnvironmentSnapshot (ENVIRONMENTAL_SNAPSHOT stimulus)
        if (payload instanceof woflo.petsplus.ai.context.perception.EnvironmentPerceptionBridge.EnvironmentSnapshot env) {
            return switch (fieldName) {
                case "raining" -> env.raining();
                case "thundering" -> env.thundering();
                case "daytime" -> env.daytime();
                case "dimensionId" -> env.dimensionId();
                default -> null;
            };
        }
        
        // Slow path: Generic reflection fallback for unknown payload types
        try {
            java.lang.reflect.Method method = payload.getClass().getMethod(fieldName);
            return method.invoke(payload);
        } catch (Exception e) {
            return null; // Field doesn't exist or access failed
        }
    }
    
    // ============================================================================
    // Comparison Operators
    // ============================================================================
    
    /**
     * Equality comparison supporting numbers, strings, and booleans.
     * Numbers use tolerance-based comparison to handle floating point precision.
     * Strings use case-insensitive comparison for enum matching.
     */
    private boolean compareEquals(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        
        // Numeric comparison with tolerance for floating point precision
        if (actual instanceof Number actualNum && expected instanceof Number expectedNum) {
            double diff = Math.abs(actualNum.doubleValue() - expectedNum.doubleValue());
            return diff < 0.0001;
        }
        
        // String comparison (case-insensitive for enum names)
        if (actual instanceof String || expected instanceof String) {
            return actual.toString().equalsIgnoreCase(expected.toString());
        }
        
        // Direct equality for booleans and other types
        return actual.equals(expected);
    }
    
    /**
     * Greater-than comparison for numeric values only.
     */
    private boolean compareGreaterThan(Object actual, Object expected) {
        if (!(actual instanceof Number actualNum) || !(expected instanceof Number expectedNum)) {
            return false; // Non-numeric values cannot be compared
        }
        return actualNum.doubleValue() > expectedNum.doubleValue();
    }
    
    /**
     * Less-than comparison for numeric values only.
     */
    private boolean compareLessThan(Object actual, Object expected) {
        if (!(actual instanceof Number actualNum) || !(expected instanceof Number expectedNum)) {
            return false; // Non-numeric values cannot be compared
        }
        return actualNum.doubleValue() < expectedNum.doubleValue();
    }
    
    // ============================================================================
    // Intensity & Confidence Calculation
    // ============================================================================
    
    /**
     * Calculates rumor intensity from formula config and stimulus context.
     * Intensity represents how strongly the pet feels about this gossip topic.
     */
    private double calculateIntensity(PetComponent pet, PerceptionStimulus stimulus) {
        double intensity = intensityFormula.base();
        
        // Apply emotion intensity multiplier
        if (stimulus.payload() instanceof PetMoodEngine.EmotionSnapshot emotion) {
            double emotionMult = intensityFormula.multipliers().getOrDefault("emotion_multiplier", 1.0);
            intensity *= emotionMult * emotion.intensity();
        }
        
        return Math.clamp(intensity, 0.0, 1.0);
    }
    
    /**
     * Calculates rumor confidence from formula config and stimulus context.
     * Confidence represents how certain the pet is about this information.
     */
    private double calculateConfidence(PetComponent pet, PerceptionStimulus stimulus) {
        double confidence = confidenceFormula.base();
        
        // Witnessed events get confidence bonus
        if (determineWitnessed(stimulus)) {
            double witnessedBonus = confidenceFormula.multipliers().getOrDefault("witnessed_bonus", 0.0);
            confidence += witnessedBonus;
        }
        
        return Math.clamp(confidence, 0.0, 1.0);
    }
    
    /**
     * Determines if the stimulus represents a directly witnessed event.
     */
    private boolean determineWitnessed(PerceptionStimulus stimulus) {
        return stimulus.payload() instanceof PetMoodEngine.EmotionSnapshot emotion 
            && emotion.witnessed();
    }
}
