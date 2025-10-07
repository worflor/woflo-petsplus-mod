package woflo.petsplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads mood engine configuration from moodengine.json.
 * Supports resource pack overrides.
 */
public final class MoodEngineConfig {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String RESOURCE_PATH = "/assets/petsplus/configs/moodengine.json";
    
    private static MoodEngineConfig instance;
    
    private final JsonObject root;
    private final JsonObject moodsSection;
    private final int version;
    private final String schema;
    
    private MoodEngineConfig(JsonObject root, JsonObject moodsSection, int version, String schema) {
        this.root = root;
        this.moodsSection = moodsSection;
        this.version = version;
        this.schema = schema;
    }
    
    /** Returns singleton instance. */
    public static MoodEngineConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    /** Reloads configuration from resources. */
    public static void reload() {
        instance = load();
        Petsplus.LOGGER.info("Reloaded mood engine configuration (version: {}, schema: {})",
            instance.version, instance.schema);
    }
    
    /** Returns root config object. */
    public JsonObject getRoot() {
        return root;
    }
    
    /** Returns moods section used by PetMoodEngine. */
    public JsonObject getMoodsSection() {
        return moodsSection;
    }
    
    /** Returns schema version. */
    public int getVersion() {
        return version;
    }
    
    /** Returns schema identifier. */
    public String getSchema() {
        return schema;
    }
    
    private static MoodEngineConfig load() {
        JsonObject root = loadRoot();
        
        if (root == null || root.entrySet().isEmpty()) {
            Petsplus.LOGGER.warn("Mood engine config is missing or empty, using hardcoded defaults");
            return createFallback();
        }
        
        int version = root.has("_version") && root.get("_version").isJsonPrimitive() 
            ? root.get("_version").getAsInt() 
            : 1;
        String schema = root.has("_schema") && root.get("_schema").isJsonPrimitive()
            ? root.get("_schema").getAsString()
            : "petsplus:emotions";
        JsonObject moodsSection = null;
        if (root.has("moods") && root.get("moods").isJsonObject()) {
            moodsSection = root.getAsJsonObject("moods");
        }
        
        if (moodsSection == null || moodsSection.entrySet().isEmpty()) {
            Petsplus.LOGGER.warn("Mood engine config missing 'moods' section, using hardcoded defaults");
            return createFallback();
        }
        
        Petsplus.LOGGER.info("Loaded mood engine configuration v{} ({})", version, schema);
        return new MoodEngineConfig(root, moodsSection, version, schema);
    }
    
    @Nullable
    private static JsonObject loadRoot() {
        try (InputStream stream = MoodEngineConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                Petsplus.LOGGER.error("Missing mood engine config resource: {}", RESOURCE_PATH);
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element == null || !element.isJsonObject()) {
                    Petsplus.LOGGER.error("Mood engine config {} is not a valid JSON object", RESOURCE_PATH);
                    return null;
                }
                return element.getAsJsonObject();
            }
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to load mood engine config from {}", RESOURCE_PATH, e);
            return null;
        } catch (JsonParseException e) {
            Petsplus.LOGGER.error("Mood engine config {} contains malformed JSON", RESOURCE_PATH, e);
            return null;
        }
    }
    
    private static MoodEngineConfig createFallback() {
        JsonObject root = new JsonObject();
        root.addProperty("_version", 1);
        root.addProperty("_schema", "petsplus:moodengine");
        root.addProperty("_comment", "Fallback mood engine config - using hardcoded defaults");
        
        JsonObject moods = new JsonObject();
        moods.addProperty("slotCount", 8);
        moods.addProperty("decayTickInterval", 100);
        moods.addProperty("defaultDecayRate", 0.9985);
        moods.addProperty("epsilon", 0.002);
        moods.addProperty("momentum", 0.65);
        moods.addProperty("switchMargin", 0.08);
        moods.addProperty("hysteresisTicks", 200);
        JsonElement thresholds = GSON.toJsonTree(new double[]{0.20, 0.45, 0.70});
        moods.add("levelThresholds", thresholds);
        JsonObject weight = new JsonObject();
        weight.addProperty("defaultMax", 6.0);
        weight.addProperty("saturationAlpha", 0.15);
        moods.add("weight", weight);
        JsonObject animation = new JsonObject();
        animation.addProperty("baseAnimationUpdateInterval", 20);
        animation.addProperty("animationSpeedMultiplier", 0.12);
        animation.addProperty("minAnimationInterval", 5);
        animation.addProperty("maxAnimationInterval", 60);
        moods.add("animation", animation);
        
        root.add("moods", moods);
        
        Petsplus.LOGGER.info("Created fallback mood engine config with hardcoded defaults");
        return new MoodEngineConfig(root, moods, 1, "petsplus:moodengine:fallback");
    }
}
