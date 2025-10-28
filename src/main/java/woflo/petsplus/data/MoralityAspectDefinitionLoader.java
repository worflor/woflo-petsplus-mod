package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.morality.MoralityAspectDefinition;
import woflo.petsplus.state.morality.MoralityAspectRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads virtue/vice aspect definitions from datapacks.
 */
public final class MoralityAspectDefinitionLoader extends BaseJsonDataLoader<MoralityAspectRegistry.Pack> {
    private static final String ROOT = "petsplus/morality/traits";

    public MoralityAspectDefinitionLoader() {
        super(ROOT, "morality_traits");
    }

    @Override
    protected String getResourceTypeName() {
        return "morality trait definitions";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, MoralityAspectRegistry.Pack> parsed = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Skipping morality trait {} because the root is not an object", id);
                continue;
            }
            try {
                MoralityAspectRegistry.Pack pack = parsePack(element.getAsJsonObject(), describeSource(id));
                parsed.put(id, pack);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Failed to parse morality trait {}", id, ex);
            }
        }
        MoralityAspectRegistry.reload(parsed);
    }

    private static MoralityAspectRegistry.Pack parsePack(JsonObject root, String source) {
        boolean replace = getBoolean(root, "replace", false);
        Map<Identifier, MoralityAspectDefinition> definitions = new LinkedHashMap<>();
        JsonArray aspects = getArray(root, "aspects");
        if (aspects != null) {
            for (JsonElement element : aspects) {
                JsonObject obj = asObject(element, source, "aspect entry");
                if (obj == null) {
                    continue;
                }
                Identifier id = identifier(obj, "id", source);
                if (id == null) {
                    continue;
                }
                MoralityAspectDefinition.Kind kind = MoralityAspectDefinition.Kind.fromString(string(obj, "kind"));
                float baseline = getFloat(obj, "baseline", 0f);
                boolean hasPersistence = obj.has("persistence");
                float persistence = getFloat(obj, "persistence", 1f);
                float passiveDrift = getFloat(obj, "passive_drift", 0f);
                float impressionability = getFloat(obj, "impressionability", 1f);
                if (obj.has("decay_half_life")) {
                    float halfLife = getFloat(obj, "decay_half_life", 48000f);
                    if (!hasPersistence) {
                        persistence = MoralityAspectDefinition.persistenceFromHalfLife(halfLife);
                    } else {
                        Petsplus.LOGGER.warn(
                            "Trait {} in {} declares both persistence and decay_half_life; using persistence",
                            id,
                            source
                        );
                    }
                }
                Map<Identifier, Float> synergy = parseSynergy(obj.getAsJsonObject("synergy"));
                definitions.put(id, new MoralityAspectDefinition(
                    id,
                    kind,
                    baseline,
                    persistence,
                    passiveDrift,
                    impressionability,
                    synergy
                ));
            }
        }
        return new MoralityAspectRegistry.Pack(replace, definitions);
    }

    private static Map<Identifier, Float> parseSynergy(JsonObject object) {
        Map<Identifier, Float> synergy = new LinkedHashMap<>();
        if (object == null) {
            return synergy;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            float value = getFloat(entry.getValue(), 0f);
            synergy.put(id, value);
        }
        return synergy;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static JsonObject asObject(JsonElement element, String source, String descriptor) {
        if (element == null || !element.isJsonObject()) {
            Petsplus.LOGGER.warn("Ignoring {} in {} because it is not an object", descriptor, source);
            return null;
        }
        return element.getAsJsonObject();
    }

    private static Identifier identifier(JsonObject object, String key, String source) {
        String raw = string(object, key);
        if (raw == null || raw.isEmpty()) {
            Petsplus.LOGGER.warn("Missing '{}' in {}", key, source);
            return null;
        }
        Identifier parsed = Identifier.tryParse(raw);
        if (parsed == null) {
            Petsplus.LOGGER.warn("Invalid identifier '{}' in {}", raw, source);
        }
        return parsed;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsString();
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
            return element.getAsFloat();
        }
        return defaultValue;
    }
}
