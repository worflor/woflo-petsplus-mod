package woflo.petsplus.mood.storm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.data.BaseJsonDataLoader;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads mood storm definitions from datapacks. Definitions allow datapacks to
 * opt moods into the storm system, customize the emotion push, and trigger
 * command functions when storms complete.
 */
final class MoodStormDataLoader extends BaseJsonDataLoader<MoodStormDefinition> {

    MoodStormDataLoader() {
        super("mood_storms", "mood_storms");
    }

    @Override
    protected String getResourceTypeName() {
        return "mood storm";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<PetComponent.Mood, MoodStormDefinition> definitions = new EnumMap<>(PetComponent.Mood.class);

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Mood storm definition at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();

            String moodValue = RegistryJsonHelper.getString(json, "mood", null);
            if (moodValue == null) {
                Petsplus.LOGGER.warn("Mood storm definition at {} is missing required field 'mood'", source);
                continue;
            }

            PetComponent.Mood mood = parseMood(moodValue, source);
            if (mood == null) {
                continue;
            }

            boolean eligible = RegistryJsonHelper.getBoolean(json, "eligible", true);

            List<Identifier> rewards = parseFunctionList(RegistryJsonHelper.getArray(json, "rewards"), source);
            List<Identifier> penalties = parseFunctionList(RegistryJsonHelper.getArray(json, "penalties"), source);

            PetComponent.Emotion emotionOverride = parseEmotion(
                RegistryJsonHelper.getString(json, "emotion", null), source);

            Identifier ambientSound = parseIdentifier(RegistryJsonHelper.getString(json, "ambient_sound", null));
            Identifier particle = parseIdentifier(RegistryJsonHelper.getString(json, "particle", null));

            Identifier id = toResourceId(fileId);
            MoodStormDefinition definition = new MoodStormDefinition(
                id,
                mood,
                eligible,
                rewards,
                penalties,
                emotionOverride,
                ambientSound,
                particle
            );
            definitions.put(mood, definition);
        }

        MoodStormRegistry.reload(definitions);
    }

    private static PetComponent.Mood parseMood(String value, String source) {
        try {
            return PetComponent.Mood.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("Unknown mood '{}' referenced in mood storm definition {}", value, source);
            return null;
        }
    }

    private static PetComponent.Emotion parseEmotion(@Nullable String value, String source) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PetComponent.Emotion.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("Unknown emotion '{}' referenced in mood storm definition {}", value, source);
            return null;
        }
    }

    private static List<Identifier> parseFunctionList(@Nullable JsonArray array, String source) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<Identifier> identifiers = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonPrimitive()) {
                Petsplus.LOGGER.warn("Mood storm definition {} has non-primitive entry in function list at index {}", source, i);
                continue;
            }
            String raw = element.getAsString();
            Identifier id = parseIdentifier(raw);
            if (id != null) {
                identifiers.add(id);
            } else {
                Petsplus.LOGGER.warn("Mood storm definition {} references invalid function identifier '{}' at index {}", source, raw, i);
            }
        }
        return List.copyOf(identifiers);
    }

    private static Identifier parseIdentifier(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            return null;
        }
        return id;
    }
}
