package woflo.petsplus.ai.suggester.signal.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.data.BaseJsonDataLoader;

import java.util.Map;

/**
 * Reload listener that ingests declarative signal rule definitions from data
 * packs. The loader understands two resource types:
 * <ul>
 *     <li><strong>mood_blend</strong>: mood and emotion desirability rules.</li>
 *     <li><strong>nature</strong>: nature-slot desirability rules.</li>
 * </ul>
 */
public final class SignalRuleDataLoader extends BaseJsonDataLoader<Void> {
    private static final String ROOT_PATH = "ai_signal_rules";

    public SignalRuleDataLoader() {
        super(ROOT_PATH, "ai_signal_rules");
    }

    @Override
    protected String getResourceTypeName() {
        return "signal rule";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        MoodSignalRules moodRules = MoodSignalRules.empty();
        NatureSignalRules natureRules = NatureSignalRules.defaults();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Skipping signal rule {} because it is not a JSON object", describeSource(id));
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "";
            if (type.isBlank()) {
                type = inferTypeFromId(id);
            }
            switch (type) {
                case "mood_blend" -> moodRules = SignalRuleParser.parseMoodRules(id, object);
                case "nature" -> natureRules = SignalRuleParser.parseNatureRules(id, object, natureRules);
                default -> Petsplus.LOGGER.warn("Unknown signal rule type '{}' in {}", type, describeSource(id));
            }
        }

        SignalRuleRegistry.setMoodRules(moodRules);
        SignalRuleRegistry.setNatureRules(natureRules);
    }

    private static String inferTypeFromId(Identifier id) {
        String path = id.getPath();
        if (path == null || path.isEmpty()) {
            return "";
        }

        if (matchesSegment(path, "mood_blend")) {
            return "mood_blend";
        }
        if (matchesSegment(path, "nature")) {
            return "nature";
        }
        return "";
    }

    private static boolean matchesSegment(String path, String segment) {
        return segment.equals(path) || path.endsWith("/" + segment);
    }
}
