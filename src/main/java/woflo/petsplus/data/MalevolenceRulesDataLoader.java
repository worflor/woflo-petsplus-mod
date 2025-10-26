package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.morality.MalevolenceRules;
import woflo.petsplus.state.morality.MalevolenceRulesRegistry;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Datapack loader for malevolence morality rules.
 */
public final class MalevolenceRulesDataLoader extends BaseJsonDataLoader<MalevolenceRules> {
    private static final String ROOT = "petsplus/morality/malevolence_rules";

    public MalevolenceRulesDataLoader() {
        super(ROOT, "malevolence_rules");
    }

    @Override
    protected String getResourceTypeName() {
        return "malevolence rules";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, MalevolenceRules> parsed = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Ignoring malevolence rule {} because it is not a JSON object", id);
                continue;
            }
            try {
                MalevolenceRules rules = parseRules(element.getAsJsonObject(), describeSource(id));
                parsed.put(id, rules);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Failed to parse malevolence rule {}", id, ex);
            }
        }
        MalevolenceRulesRegistry.reload(parsed);
    }

    private static MalevolenceRules parseRules(JsonObject root, String source) {
        boolean replace = getBoolean(root, "replace", false);
        Map<String, MalevolenceRules.TagRule> tagRules = parseTagRules(getObject(root, "tags"), source);
        Map<Identifier, MalevolenceRules.VictimRule> victimRules = new LinkedHashMap<>();
        Map<Identifier, MalevolenceRules.VictimRule> tagVictimRules = new LinkedHashMap<>();
        parseVictimRules(getObject(root, "victims"), source, victimRules, tagVictimRules);
        Map<RelationshipType, Float> relationshipWeights = parseRelationshipWeights(getObject(root, "relationship_weights"));
        MalevolenceRules.TelemetrySettings telemetry = parseTelemetry(getObject(root, "telemetry"));
        MalevolenceRules.Thresholds thresholds = parseThresholds(getObject(root, "thresholds"));
        MalevolenceRules.SpreeSettings spree = parseSpree(getObject(root, "spree"));
        MalevolenceRules.DisharmonySettings disharmony = parseDisharmony(getObject(root, "disharmony"));
        MalevolenceRules.ForgivenessSettings forgiveness = parseForgiveness(getObject(root, "forgiveness"));
        Identifier disharmonySet = disharmonySet(getObject(root, "disharmony"));
        return new MalevolenceRules(tagRules, victimRules, tagVictimRules, relationshipWeights,
            telemetry, thresholds, spree, disharmony, forgiveness, disharmonySet, replace);
    }

    private static Map<String, MalevolenceRules.TagRule> parseTagRules(JsonObject tagsObject, String source) {
        Map<String, MalevolenceRules.TagRule> rules = new LinkedHashMap<>();
        if (tagsObject == null) {
            return rules;
        }
        for (Map.Entry<String, JsonElement> entry : tagsObject.entrySet()) {
            String key = MalevolenceRules.normalizeTag(entry.getKey());
            if (key == null) {
                continue;
            }
            JsonObject value = asObject(entry.getValue(), source, "tag rule " + key);
            if (value == null) {
                continue;
            }
            float base = getFloat(value, "base", 0f);
            Map<RelationshipType, Float> relationshipMultipliers = parseRelationshipWeights(getObject(value, "relationship_multipliers"));
            float telemetryBias = getFloat(value, "telemetry_bias", 0f);
            float spreeBonus = getFloat(value, "spree_bonus", 0f);
            rules.put(key, new MalevolenceRules.TagRule(base, relationshipMultipliers, telemetryBias, spreeBonus));
        }
        return rules;
    }

    private static void parseVictimRules(JsonObject victims,
                                         String source,
                                         Map<Identifier, MalevolenceRules.VictimRule> direct,
                                         Map<Identifier, MalevolenceRules.VictimRule> tagged) {
        if (victims == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : victims.entrySet()) {
            String rawKey = entry.getKey();
            JsonObject value = asObject(entry.getValue(), source, "victim rule " + rawKey);
            if (value == null) {
                continue;
            }
            Set<String> tags = parseStringSet(value, "tags");
            float base = getFloat(value, "base", 0f);
            MalevolenceRules.VictimRule rule = new MalevolenceRules.VictimRule(base, tags);
            if (rawKey.startsWith("#")) {
                Identifier id = Identifier.tryParse(rawKey.substring(1));
                if (id != null) {
                    tagged.put(id, rule);
                }
                continue;
            }
            Identifier id = Identifier.tryParse(rawKey);
            if (id == null) {
                Petsplus.LOGGER.warn("Invalid victim key '{}' in {}", rawKey, source);
                continue;
            }
            direct.put(id, rule);
        }
    }

    private static Map<RelationshipType, Float> parseRelationshipWeights(JsonObject object) {
        Map<RelationshipType, Float> weights = new EnumMap<>(RelationshipType.class);
        if (object == null) {
            return weights;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            RelationshipType type = parseRelationshipType(entry.getKey());
            if (type == null) {
                continue;
            }
            float value = getFloat(entry.getValue(), 0f);
            weights.put(type, value);
        }
        return weights;
    }

    private static MalevolenceRules.TelemetrySettings parseTelemetry(JsonObject object) {
        if (object == null) {
            return MalevolenceRules.TelemetrySettings.EMPTY;
        }
        float base = getFloat(object, "base", 1.0f);
        float trust = getFloat(object, "trust_weight", 0f);
        float affection = getFloat(object, "affection_weight", 0f);
        float respect = getFloat(object, "respect_weight", 0f);
        float delta = getFloat(object, "delta_weight", 0f);
        float friendly = getFloat(object, "friendly_fire_weight", 0f);
        float min = getFloat(object, "min", 0.2f);
        float max = getFloat(object, "max", 4.0f);
        return new MalevolenceRules.TelemetrySettings(base, trust, affection, respect, delta, friendly, min, max);
    }

    private static MalevolenceRules.Thresholds parseThresholds(JsonObject object) {
        if (object == null) {
            return MalevolenceRules.Thresholds.DEFAULT;
        }
        float trigger = getFloat(object, "trigger", MalevolenceRules.Thresholds.DEFAULT.triggerScore());
        float remission = getFloat(object, "remission", MalevolenceRules.Thresholds.DEFAULT.remissionScore());
        long cooldown = getLong(object, "cooldown", MalevolenceRules.Thresholds.DEFAULT.cooldownTicks());
        float halfLife = getFloat(object, "decay_half_life", MalevolenceRules.Thresholds.DEFAULT.decayHalfLifeTicks());
        float scale = getFloat(object, "intensity_scale", MalevolenceRules.Thresholds.DEFAULT.intensityScale());
        float min = getFloat(object, "min_intensity", MalevolenceRules.Thresholds.DEFAULT.minIntensity());
        float max = getFloat(object, "max_intensity", MalevolenceRules.Thresholds.DEFAULT.maxIntensity());
        return new MalevolenceRules.Thresholds(trigger, remission, cooldown, halfLife, scale, min, max);
    }

    private static MalevolenceRules.SpreeSettings parseSpree(JsonObject object) {
        if (object == null) {
            return MalevolenceRules.SpreeSettings.DEFAULT;
        }
        long window = getLong(object, "window", MalevolenceRules.SpreeSettings.DEFAULT.windowTicks());
        float base = getFloat(object, "base", MalevolenceRules.SpreeSettings.DEFAULT.baseMultiplier());
        float step = getFloat(object, "step", MalevolenceRules.SpreeSettings.DEFAULT.stepBonus());
        float max = getFloat(object, "max", MalevolenceRules.SpreeSettings.DEFAULT.maxBonus());
        float clutch = getFloat(object, "clutch_bonus", MalevolenceRules.SpreeSettings.DEFAULT.clutchBonus());
        return new MalevolenceRules.SpreeSettings(window, base, step, max, clutch);
    }

    private static MalevolenceRules.DisharmonySettings parseDisharmony(JsonObject object) {
        if (object == null) {
            return MalevolenceRules.DisharmonySettings.DEFAULT;
        }
        float base = getFloat(object, "base", MalevolenceRules.DisharmonySettings.DEFAULT.baseStrength());
        float scale = getFloat(object, "scale", MalevolenceRules.DisharmonySettings.DEFAULT.intensityScale());
        float max = getFloat(object, "max", MalevolenceRules.DisharmonySettings.DEFAULT.maxStrength());
        float floor = getFloat(object, "remission_floor", MalevolenceRules.DisharmonySettings.DEFAULT.remissionFloor());
        return new MalevolenceRules.DisharmonySettings(base, scale, max, floor);
    }

    private static MalevolenceRules.ForgivenessSettings parseForgiveness(JsonObject object) {
        if (object == null) {
            return MalevolenceRules.ForgivenessSettings.DEFAULT;
        }
        float friendlyFloor = getFloat(object, "friendly_fire_floor", MalevolenceRules.ForgivenessSettings.DEFAULT.friendlyFireFloor());
        int spreeGrace = getInt(object, "spree_grace", MalevolenceRules.ForgivenessSettings.DEFAULT.spreeGrace());
        float magnitudeFloor = getFloat(object, "magnitude_floor", MalevolenceRules.ForgivenessSettings.DEFAULT.magnitudeFloor());
        float highTrustThreshold = getFloat(object, "high_trust_threshold", MalevolenceRules.ForgivenessSettings.DEFAULT.highTrustThreshold());
        float highTrustSeverityLimit = getFloat(object, "high_trust_severity_limit", MalevolenceRules.ForgivenessSettings.DEFAULT.highTrustSeverityLimit());
        float trustWeight = getFloat(object, "trust_weight", MalevolenceRules.ForgivenessSettings.DEFAULT.magnitudeTrustWeight());
        float affectionWeight = getFloat(object, "affection_weight", MalevolenceRules.ForgivenessSettings.DEFAULT.magnitudeAffectionWeight());
        float respectWeight = getFloat(object, "respect_weight", MalevolenceRules.ForgivenessSettings.DEFAULT.magnitudeRespectWeight());
        return new MalevolenceRules.ForgivenessSettings(
            Math.max(0f, friendlyFloor),
            Math.max(0, spreeGrace),
            Math.max(0f, magnitudeFloor),
            MathHelper.clamp(highTrustThreshold, 0f, 1f),
            Math.max(0f, highTrustSeverityLimit),
            trustWeight,
            affectionWeight,
            respectWeight
        );
    }

    private static Identifier disharmonySet(JsonObject object) {
        if (object == null || !object.has("set")) {
            return Identifier.of("petsplus", "morality/malevolence");
        }
        Identifier id = Identifier.tryParse(object.get("set").getAsString());
        return id != null ? id : Identifier.of("petsplus", "morality/malevolence");
    }

    private static RelationshipType parseRelationshipType(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            return RelationshipType.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("Unknown relationship type '{}' in malevolence rules", key);
            return null;
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonObject asObject(JsonElement element, String source, String descriptor) {
        if (element == null || !element.isJsonObject()) {
            Petsplus.LOGGER.warn("Ignoring {} in {} because it is not an object", descriptor, source);
            return null;
        }
        return element.getAsJsonObject();
    }

    private static Set<String> parseStringSet(JsonObject object, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return values;
        }
        object.getAsJsonArray(key).forEach(elem -> {
            if (elem.isJsonPrimitive()) {
                values.add(MalevolenceRules.normalizeTag(elem.getAsString()));
            }
        });
        values.removeIf(v -> v == null || v.isEmpty());
        return values;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) {
            return element.getAsBoolean();
        }
        return defaultValue;
    }

    private static float getFloat(JsonObject object, String key, float defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        return getFloat(object.get(key), defaultValue);
    }

    private static float getFloat(JsonElement element, float defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsFloat();
            }
        }
        return defaultValue;
    }

    private static long getLong(JsonObject object, String key, long defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }
        return defaultValue;
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return defaultValue;
    }
}
