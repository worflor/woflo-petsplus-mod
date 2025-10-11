package woflo.petsplus.ai.suggester.signal.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Holds the active signal rule configuration. Rules are loaded from data packs
 * via {@link SignalRuleDataLoader}, but the registry also bootstraps the built-
 * in defaults from the classpath so unit tests and environments without a
 * resource manager still have access to tuned values.
 */
public final class SignalRuleRegistry {
    private static final String MOOD_RULE_RESOURCE = "data/petsplus/ai_signal_rules/mood_blend.json";
    private static final String NATURE_RULE_RESOURCE = "data/petsplus/ai_signal_rules/nature.json";

    private static volatile MoodSignalRules moodRules = MoodSignalRules.empty();
    private static volatile NatureSignalRules natureRules = NatureSignalRules.defaults();

    private SignalRuleRegistry() {
    }

    static {
        loadBuiltInDefaults();
    }

    public static MoodSignalRules moodRules() {
        return moodRules;
    }

    public static NatureSignalRules natureRules() {
        return natureRules;
    }

    public static void setMoodRules(MoodSignalRules rules) {
        moodRules = rules != null ? rules : MoodSignalRules.empty();
    }

    public static void setNatureRules(NatureSignalRules rules) {
        natureRules = rules != null ? rules : NatureSignalRules.defaults();
    }

    public static void resetToDefaults() {
        setMoodRules(MoodSignalRules.empty());
        setNatureRules(NatureSignalRules.defaults());
        loadBuiltInDefaults();
    }

    private static void loadBuiltInDefaults() {
        loadMoodDefaults();
        loadNatureDefaults();
    }

    private static void loadMoodDefaults() {
        JsonObject root = readJsonResource(MOOD_RULE_RESOURCE);
        if (root == null) {
            return;
        }
        MoodSignalRules parsed = SignalRuleParser.parseMoodRules(Identifier.of("petsplus", "mood_blend"), root);
        if (parsed != null) {
            moodRules = parsed;
        }
    }

    private static void loadNatureDefaults() {
        JsonObject root = readJsonResource(NATURE_RULE_RESOURCE);
        if (root == null) {
            return;
        }
        NatureSignalRules parsed = SignalRuleParser.parseNatureRules(Identifier.of("petsplus", "nature"), root, natureRules);
        if (parsed != null) {
            natureRules = parsed;
        }
    }

    private static JsonObject readJsonResource(String path) {
        ClassLoader loader = SignalRuleRegistry.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                Petsplus.LOGGER.warn("Missing built-in signal rule resource: {}", path);
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to load built-in signal rule resource: {}", path, e);
            return null;
        }
    }
}
