package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.social.gossip.handler.*;

import java.util.*;

/**
 * Loads gossip handler definitions from datapacks.
 * <p>
 * Handlers are defined in {@code data/<namespace>/gossip_handlers/*.json}
 * and follow the same pattern as roles/abilities for intuitive datapack creation.
 * <p>
 * <b>Integration Points:</b>
 * <ol>
 *   <li><b>Register Dataloader:</b> In {@code PetsPlusRegistries.bootstrap()}, add:
 *       <pre>ResourceManagerHelper.get(ResourceType.SERVER_DATA)
 *     .registerReloadListener(new GossipHandlerDataLoader());</pre>
 *   </li>
 *   <li><b>Register Perception Listener:</b> In {@code PetComponent} initialization, add:
 *       <pre>perceptionBus.addListener(new GossipPerceptionListener(this));</pre>
 *   </li>
 *   <li><b>Built-in Handlers:</b> Call {@code GossipHandlerRegistry.register()} for any
 *       programmatic handlers before datapack load.
 *   </li>
 * </ol>
 * <p>
 * Example JSON structure:
 * <pre>{@code
 * {
 *   "stimulus_type": "petsplus:emotion_sample",
 *   "priority": 100,
 *   "cooldown_ticks": 200,
 *   "context_checks": [
 *     {"state_key": "COMBAT_ENGAGED", "require": true},
 *     {"state_key": "OWNER_NEARBY", "require": true}
 *   ],
 *   "topic_builder": {
 *     "base_path": "emotion",
 *     "append_emotion": true,
 *     "append_intensity": true
 *   },
 *   "narrative_templates": {
 *     "default": "{pet} felt {emotion}",
 *     "high_intensity": "{pet} felt very {emotion}"
 *   },
 *   "intensity_formula": {
 *     "base": 0.5,
 *     "emotion_multiplier": 1.0,
 *     "combat_bonus": 0.2
 *   },
 *   "confidence_formula": {
 *     "base": 0.7,
 *     "witnessed_bonus": 0.2
 *   }
 * }
 * }</pre>
 */
public final class GossipHandlerDataLoader extends BaseJsonDataLoader<GossipStimulusHandler> {
    
    public GossipHandlerDataLoader() {
        super("gossip_handlers", "gossip_handler_data");
    }
    
    @Override
    protected String getResourceTypeName() {
        return "gossip handler";
    }
    
    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        // Clear existing handlers on reload
        GossipHandlerRegistry.clear();
        
        int loaded = 0;
        int failed = 0;
        
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);
            
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Gossip handler at {} must be a JSON object", source);
                failed++;
                continue;
            }
            
            JsonObject json = element.getAsJsonObject();
            
            try {
                GossipStimulusHandler handler = parseHandler(fileId, json, source);
                if (handler != null) {
                    GossipHandlerRegistry.register(handler);
                    loaded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                Petsplus.LOGGER.error("Failed to load gossip handler from {}", source, e);
                failed++;
            }
        }
        
        Petsplus.LOGGER.info("Loaded {} gossip handlers ({} failed) - {}", 
            loaded, failed, GossipHandlerRegistry.getStats());
    }
    
    private GossipStimulusHandler parseHandler(Identifier fileId, JsonObject json, String source) {
        // Required fields
        String stimulusTypeStr = RegistryJsonHelper.getString(json, "stimulus_type", null);
        if (stimulusTypeStr == null) {
            Petsplus.LOGGER.error("Gossip handler {} missing required field 'stimulus_type'", source);
            return null;
        }
        
        // Parse as Identifier for namespaced types like petsplus:perception/emotion_sample
        Identifier stimulusType = Identifier.tryParse(stimulusTypeStr);
        if (stimulusType == null) {
            Petsplus.LOGGER.error("Gossip handler {} has invalid stimulus_type: {}", source, stimulusTypeStr);
            return null;
        }
        
        // Optional fields with sensible defaults
        int priority = RegistryJsonHelper.getInt(json, "priority", 50);
        long cooldownTicks = RegistryJsonHelper.getLong(json, "cooldown_ticks", 1200L);
        boolean suppressive = RegistryJsonHelper.getBoolean(json, "suppressive", false);
        
        // Validate priority range
        if (priority < 0 || priority > 1000) {
            Petsplus.LOGGER.warn("Gossip handler {} has unusual priority {} (expected 0-1000), clamping", 
                source, priority);
            priority = Math.max(0, Math.min(1000, priority));
        }
        
        // Validate cooldown
        if (cooldownTicks < 0) {
            Petsplus.LOGGER.warn("Gossip handler {} has negative cooldown {}, setting to 0", 
                source, cooldownTicks);
            cooldownTicks = 0;
        }
        
        // Context checks (filter conditions using StateKeys)
        List<ContextCheck> contextChecks = parseContextChecks(json, source);
        
        // Topic builder configuration
        TopicBuilderConfig topicConfig = parseTopicBuilder(json, source);
        if (topicConfig == null) {
            Petsplus.LOGGER.error("Gossip handler {} failed to parse topic_builder", source);
            return null;
        }
        
        // Conditional narrative templates
        List<ConditionalTemplateGroup> conditionalTemplates = parseConditionalTemplates(json, source);
        if (conditionalTemplates.isEmpty()) {
            Petsplus.LOGGER.error("Gossip handler {} has no valid template groups, skipping", source);
            return null;
        }
        
        // Intensity/confidence formulas
        FormulaConfig intensityFormula = parseFormula(json, "intensity_formula", source);
        FormulaConfig confidenceFormula = parseFormula(json, "confidence_formula", source);
        
        // Validate formulas
        if (intensityFormula.base() < 0.0 || intensityFormula.base() > 1.0) {
            Petsplus.LOGGER.warn("Gossip handler {} has intensity base {} outside [0,1], clamping", 
                source, intensityFormula.base());
        }
        if (confidenceFormula.base() < 0.0 || confidenceFormula.base() > 1.0) {
            Petsplus.LOGGER.warn("Gossip handler {} has confidence base {} outside [0,1], clamping", 
                source, confidenceFormula.base());
        }
        
        // Create JSON-backed handler implementation
        return new JsonGossipHandler(
            fileId,
            stimulusType.toString(),
            priority,
            cooldownTicks,
            suppressive,
            contextChecks,
            topicConfig,
            conditionalTemplates,
            intensityFormula,
            confidenceFormula
        );
    }
    
    private List<ContextCheck> parseContextChecks(JsonObject json, String source) {
        List<ContextCheck> checks = new ArrayList<>();
        JsonArray arr = RegistryJsonHelper.getArray(json, "context_checks");
        
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) {
                    Petsplus.LOGGER.warn("Gossip handler {} has non-object context check at index {}", source, i);
                    continue;
                }
                
                JsonObject checkJson = arr.get(i).getAsJsonObject();
                String stateKey = RegistryJsonHelper.getString(checkJson, "state_key", null);
                boolean require = RegistryJsonHelper.getBoolean(checkJson, "require", true);
                String comparison = RegistryJsonHelper.getString(checkJson, "comparison", "exists");
                Object value = null; // Could extend to parse comparison values
                
                if (stateKey != null) {
                    checks.add(new ContextCheck(stateKey, require, comparison, value));
                }
            }
        }
        
        return checks;
    }
    
    private TopicBuilderConfig parseTopicBuilder(JsonObject json, String source) {
        JsonObject builderJson = RegistryJsonHelper.getObject(json, "topic_builder");
        if (builderJson == null) {
            Petsplus.LOGGER.warn("Gossip handler {} missing topic_builder, using default", source);
            return new TopicBuilderConfig("gossip", false, false, false);
        }
        
        // Support both "base_path" (full config) and "template" (simplified)
        String basePath = RegistryJsonHelper.getString(builderJson, "base_path", null);
        if (basePath == null) {
            basePath = RegistryJsonHelper.getString(builderJson, "template", "gossip");
        }
        
        // Validate base path
        if (basePath == null || basePath.isBlank()) {
            Petsplus.LOGGER.error("Gossip handler {} has empty topic builder path", source);
            return null;
        }
        
        boolean appendEmotion = RegistryJsonHelper.getBoolean(builderJson, "append_emotion", false);
        boolean appendIntensity = RegistryJsonHelper.getBoolean(builderJson, "append_intensity", false);
        boolean appendCombatState = RegistryJsonHelper.getBoolean(builderJson, "append_combat_state", false);
        
        return new TopicBuilderConfig(basePath, appendEmotion, appendIntensity, appendCombatState);
    }
    
    private List<ConditionalTemplateGroup> parseConditionalTemplates(JsonObject json, String source) {
        List<ConditionalTemplateGroup> groups = new ArrayList<>();
        JsonElement templatesElement = json.get("narrative_templates");
        
        if (templatesElement == null) {
            Petsplus.LOGGER.error("Gossip handler {} missing required field 'narrative_templates'", source);
            return Collections.emptyList();
        }
        
        if (!templatesElement.isJsonArray()) {
            Petsplus.LOGGER.error("Gossip handler {} has invalid narrative_templates format (expected array)", source);
            return Collections.emptyList();
        }
        
        JsonArray templatesArray = templatesElement.getAsJsonArray();
        for (int i = 0; i < templatesArray.size(); i++) {
            if (!templatesArray.get(i).isJsonObject()) {
                Petsplus.LOGGER.warn("Gossip handler {} has non-object template group at index {}", source, i);
                continue;
            }
            
            JsonObject groupJson = templatesArray.get(i).getAsJsonObject();
            List<TemplateCondition> conditions = parseTemplateConditions(groupJson, source, i);
            List<String> templates = parseTemplateList(groupJson, source, i);
            
            if (templates.isEmpty()) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} has no valid templates, skipping", source, i);
                continue;
            }
            
            groups.add(new ConditionalTemplateGroup(conditions, templates));
        }
        
        if (groups.isEmpty()) {
            Petsplus.LOGGER.error("Gossip handler {} has no valid template groups", source);
        }
        
        return groups;
    }
    
    private List<TemplateCondition> parseTemplateConditions(JsonObject groupJson, String source, int groupIndex) {
        List<TemplateCondition> conditions = new ArrayList<>();
        JsonArray conditionsArray = RegistryJsonHelper.getArray(groupJson, "conditions");
        
        if (conditionsArray == null || conditionsArray.isEmpty()) {
            return conditions; // Empty conditions = unconditional fallback group
        }
        
        for (int i = 0; i < conditionsArray.size(); i++) {
            if (!conditionsArray.get(i).isJsonObject()) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} has non-object condition at index {}", 
                    source, groupIndex, i);
                continue;
            }
            
            JsonObject condJson = conditionsArray.get(i).getAsJsonObject();
            String key = RegistryJsonHelper.getString(condJson, "key", null);
            String operator = RegistryJsonHelper.getString(condJson, "operator", "equals");
            
            if (key == null) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} condition {} missing 'key' field", 
                    source, groupIndex, i);
                continue;
            }
            
            // Validate operator
            if (!isValidOperator(operator)) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} condition {} has invalid operator '{}', using 'equals'", 
                    source, groupIndex, i, operator);
                operator = "equals";
            }
            
            // Parse value - support boolean, number, and string
            Object value = parseConditionValue(condJson, source, groupIndex, i);
            
            conditions.add(new TemplateCondition(key, operator, value));
        }
        
        return conditions;
    }
    
    private boolean isValidOperator(String operator) {
        return operator != null && (
            operator.equals("equals") ||
            operator.equals("greater_than") ||
            operator.equals("less_than") ||
            operator.equals("exists")
        );
    }
    
    private Object parseConditionValue(JsonObject condJson, String source, int groupIndex, int condIndex) {
        if (!condJson.has("value")) {
            return null; // No value required for "exists" operator
        }
        
        JsonElement valueElement = condJson.get("value");
        if (!valueElement.isJsonPrimitive()) {
            Petsplus.LOGGER.warn("Gossip handler {} group {} condition {} has non-primitive value", 
                source, groupIndex, condIndex);
            return null;
        }
        
        if (valueElement.getAsJsonPrimitive().isBoolean()) {
            return valueElement.getAsBoolean();
        } else if (valueElement.getAsJsonPrimitive().isNumber()) {
            return valueElement.getAsDouble();
        } else if (valueElement.getAsJsonPrimitive().isString()) {
            return valueElement.getAsString();
        }
        
        return null;
    }
    
    private List<String> parseTemplateList(JsonObject groupJson, String source, int groupIndex) {
        List<String> templates = new ArrayList<>();
        JsonArray templatesArray = RegistryJsonHelper.getArray(groupJson, "templates");
        
        if (templatesArray == null) {
            Petsplus.LOGGER.warn("Gossip handler {} group {} missing 'templates' array", source, groupIndex);
            return templates;
        }
        
        for (int i = 0; i < templatesArray.size(); i++) {
            JsonElement element = templatesArray.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} has non-string template at index {}", 
                    source, groupIndex, i);
                continue;
            }
            
            String template = element.getAsString().trim();
            if (template.isEmpty()) {
                Petsplus.LOGGER.warn("Gossip handler {} group {} has empty template at index {}", 
                    source, groupIndex, i);
                continue;
            }
            
            templates.add(template);
        }
        
        return templates;
    }
    
    private FormulaConfig parseFormula(JsonObject json, String formulaKey, String source) {
        JsonElement formulaElement = json.get(formulaKey);
        if (formulaElement == null) {
            return new FormulaConfig(0.5, Collections.emptyMap());
        }
        
        // Support simple string format: "intensity_formula": "0.6"
        if (formulaElement.isJsonPrimitive() && formulaElement.getAsJsonPrimitive().isString()) {
            try {
                double base = Double.parseDouble(formulaElement.getAsString());
                return new FormulaConfig(base, Collections.emptyMap());
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.warn("Gossip handler {} has invalid {} value: {}", 
                    source, formulaKey, formulaElement.getAsString());
                return new FormulaConfig(0.5, Collections.emptyMap());
            }
        }
        
        // Support object format: "intensity_formula": { "base": 0.5, "emotion_multiplier": 1.0 }
        if (!formulaElement.isJsonObject()) {
            Petsplus.LOGGER.warn("Gossip handler {} has invalid {} format (expected string or object)", 
                source, formulaKey);
            return new FormulaConfig(0.5, Collections.emptyMap());
        }
        
        JsonObject formulaJson = formulaElement.getAsJsonObject();
        double base = RegistryJsonHelper.getDouble(formulaJson, "base", 0.5);
        Map<String, Double> multipliers = new HashMap<>();
        
        for (String key : formulaJson.keySet()) {
            if (key.equals("base")) {
                continue;
            }
            double multiplier = RegistryJsonHelper.getDouble(formulaJson, key, 1.0);
            multipliers.put(key, multiplier);
        }
        
        return new FormulaConfig(base, multipliers);
    }
    
    // Configuration records for JSON handler
    
    /**
     * Defines a condition check against pet state before generating gossip.
     * Example: Only generate gossip when owner is nearby.
     */
    public record ContextCheck(String stateKey, boolean require, String comparison, Object value) {}
    
    /**
     * Configuration for building emergent topic IDs from stimulus context.
     * Topic IDs are hashed identifiers that group related rumors together.
     */
    public record TopicBuilderConfig(String basePath, boolean appendEmotion, boolean appendIntensity, boolean appendCombatState) {}
    
    /**
     * Formula configuration for calculating intensity or confidence values.
     * Base value is modified by multipliers derived from stimulus context.
     */
    public record FormulaConfig(double base, Map<String, Double> multipliers) {}
    
    /**
     * A condition that must be satisfied for a template group to be selected.
     * Conditions are evaluated against fields in the stimulus payload.
     * 
     * @param key The field name in the stimulus payload (e.g., "crouching", "momentum", "HAPPY")
     * @param operator Comparison operator: "equals", "greater_than", "less_than", "exists"
     * @param value The value to compare against (Boolean, Double, or String). Null for "exists" operator.
     */
    public record TemplateCondition(String key, String operator, Object value) {
        public TemplateCondition {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Condition key cannot be null or blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("Condition operator cannot be null or blank");
            }
        }
    }
    
    /**
     * A group of narrative templates that share the same activation conditions.
     * When all conditions match, a random template is selected from this group.
     * 
     * @param conditions List of conditions that must ALL be true (AND logic). Empty list = unconditional fallback.
     * @param templates List of narrative text strings. One will be randomly selected when conditions match.
     */
    public record ConditionalTemplateGroup(List<TemplateCondition> conditions, List<String> templates) {
        public ConditionalTemplateGroup {
            if (templates == null || templates.isEmpty()) {
                throw new IllegalArgumentException("Template group must have at least one template");
            }
            conditions = conditions == null ? Collections.emptyList() : List.copyOf(conditions);
            templates = List.copyOf(templates);
        }
        
        public boolean isUnconditional() {
            return conditions.isEmpty();
        }
    }
}
