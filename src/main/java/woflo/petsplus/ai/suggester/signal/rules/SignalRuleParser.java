package woflo.petsplus.ai.suggester.signal.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SignalRuleParser {
    private SignalRuleParser() {
    }

    static MoodSignalRules parseMoodRules(Identifier sourceId, JsonObject root) {
        Map<PetComponent.Mood, SignalRuleSet> moodRules = new EnumMap<>(PetComponent.Mood.class);
        Map<PetComponent.Emotion, SignalRuleSet> emotionRules = new EnumMap<>(PetComponent.Emotion.class);

        JsonObject moodsObject = RegistryJsonHelper.getObject(root, "moods");
        if (moodsObject != null) {
            for (Map.Entry<String, JsonElement> entry : moodsObject.entrySet()) {
                PetComponent.Mood mood = parseMood(entry.getKey());
                if (mood == null || !entry.getValue().isJsonObject()) {
                    Petsplus.LOGGER.warn("Skipping mood rule {} from {} because it is invalid", entry.getKey(), sourceId);
                    continue;
                }
                SignalRuleSet ruleSet = parseRuleSet(entry.getValue().getAsJsonObject(), true);
                moodRules.put(mood, ruleSet);
            }
        }

        JsonObject emotionsObject = RegistryJsonHelper.getObject(root, "emotions");
        if (emotionsObject != null) {
            for (Map.Entry<String, JsonElement> entry : emotionsObject.entrySet()) {
                PetComponent.Emotion emotion = parseEmotion(entry.getKey());
                if (emotion == null || !entry.getValue().isJsonObject()) {
                    Petsplus.LOGGER.warn("Skipping emotion rule {} from {} because it is invalid", entry.getKey(), sourceId);
                    continue;
                }
                SignalRuleSet ruleSet = parseRuleSet(entry.getValue().getAsJsonObject(), true);
                emotionRules.put(emotion, ruleSet);
            }
        }

        return new MoodSignalRules(Map.copyOf(moodRules), Map.copyOf(emotionRules));
    }

    static NatureSignalRules parseNatureRules(Identifier sourceId, JsonObject root, NatureSignalRules defaults) {
        float minIntensity = RegistryJsonHelper.getFloat(root, "minIntensity", defaults.minIntensity());
        JsonObject slots = RegistryJsonHelper.getObject(root, "slotWeights");
        float majorWeight = RegistryJsonHelper.getFloat(slots, "major", defaults.majorWeight());
        float minorWeight = RegistryJsonHelper.getFloat(slots, "minor", defaults.minorWeight());
        float quirkWeight = RegistryJsonHelper.getFloat(slots, "quirk", defaults.quirkWeight());

        Map<PetComponent.Emotion, SignalRuleSet> rules = new EnumMap<>(PetComponent.Emotion.class);
        JsonObject rulesObject = RegistryJsonHelper.getObject(root, "rules");
        if (rulesObject != null) {
            for (Map.Entry<String, JsonElement> entry : rulesObject.entrySet()) {
                PetComponent.Emotion emotion = parseEmotion(entry.getKey());
                if (emotion == null || !entry.getValue().isJsonObject()) {
                    Petsplus.LOGGER.warn("Skipping nature rule {} from {} because it is invalid", entry.getKey(), sourceId);
                    continue;
                }
                SignalRuleSet ruleSet = parseRuleSet(entry.getValue().getAsJsonObject(), false);
                rules.put(emotion, ruleSet);
            }
        }

        return new NatureSignalRules(minIntensity, majorWeight, minorWeight, quirkWeight, Map.copyOf(rules));
    }

    private static SignalRuleSet parseRuleSet(JsonObject object, boolean allowThreshold) {
        float threshold = allowThreshold ? RegistryJsonHelper.getFloat(object, "threshold", 0.0f) : 0.0f;
        Map<GoalDefinition.Category, List<Float>> categories = new EnumMap<>(GoalDefinition.Category.class);
        JsonObject categoryObject = RegistryJsonHelper.getObject(object, "categories");
        if (categoryObject != null) {
            for (Map.Entry<String, JsonElement> entry : categoryObject.entrySet()) {
                GoalDefinition.Category category = parseCategory(entry.getKey());
                if (category == null) {
                    Petsplus.LOGGER.warn("Unknown goal category {} in signal rule", entry.getKey());
                    continue;
                }
                categories.put(category, parseMultiplierList(entry.getValue()));
            }
        }

        Map<Identifier, List<Float>> goals = new java.util.HashMap<>();
        JsonObject goalsObject = RegistryJsonHelper.getObject(object, "goals");
        if (goalsObject != null) {
            for (Map.Entry<String, JsonElement> entry : goalsObject.entrySet()) {
                Identifier goalId = parseIdentifier(entry.getKey());
                if (goalId == null) {
                    Petsplus.LOGGER.warn("Unable to parse goal identifier {} in signal rule", entry.getKey());
                    continue;
                }
                goals.put(goalId, parseMultiplierList(entry.getValue()));
            }
        }

        return new SignalRuleSet(threshold, Map.copyOf(categories), Map.copyOf(goals));
    }

    private static List<Float> parseMultiplierList(JsonElement element) {
        List<Float> values = new ArrayList<>();
        if (element == null) {
            return List.of();
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    values.add(value.getAsFloat());
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            values.add(element.getAsFloat());
        }
        return List.copyOf(values);
    }

    private static Identifier parseIdentifier(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed != null) {
            return parsed;
        }
        return Identifier.of("petsplus", id);
    }

    private static GoalDefinition.Category parseCategory(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return GoalDefinition.Category.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PetComponent.Mood parseMood(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return PetComponent.Mood.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PetComponent.Emotion parseEmotion(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return PetComponent.Emotion.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
