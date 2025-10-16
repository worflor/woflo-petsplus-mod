package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.stats.nature.harmony.NatureHarmonyRegistry;
import woflo.petsplus.stats.nature.harmony.NatureHarmonySet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads datapack-authored harmony/disharmony definitions.
 */
public final class NatureHarmonyDataLoader extends BaseJsonDataLoader<NatureHarmonyRegistry.HarmonyEntry> {

    public NatureHarmonyDataLoader() {
        super("nature_harmony_sets", "nature_harmony_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "nature harmony";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, NatureHarmonyRegistry.HarmonyEntry> parsed = new LinkedHashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Nature harmony definition at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();
            boolean replace = RegistryJsonHelper.getBoolean(json, "replace", false);
            Identifier id = parseIdentifier(json, fileId, source);
            if (id == null) {
                continue;
            }

            NatureHarmonySet.Type type = parseType(json, source);
            List<NatureHarmonySet.Member> members = parseMembers(json, source);
            if (members.size() < 2 || members.size() > 3) {
                Petsplus.LOGGER.error("Nature harmony {} must list two or three member natures", source);
                continue;
            }

            double radius = RegistryJsonHelper.getDouble(json, "radius", 5.5d);
            float mood = RegistryJsonHelper.getFloat(json, "mood_scalar", 1.0f);
            float contagion = RegistryJsonHelper.getFloat(json, "contagion_scalar", 1.0f);
            float volatility = RegistryJsonHelper.getFloat(json, "volatility_scalar", 1.0f);
            float resilience = RegistryJsonHelper.getFloat(json, "resilience_scalar", 1.0f);
            float guard = RegistryJsonHelper.getFloat(json, "guard_scalar", 1.0f);
            int linger = RegistryJsonHelper.getInt(json, "linger_ticks", 100);
            List<String> tags = parseTags(json);

            NatureHarmonySet set;
            try {
                set = new NatureHarmonySet(id, type, members, radius, mood, contagion, volatility, resilience, guard, linger, tags);
            } catch (IllegalArgumentException ex) {
                Petsplus.LOGGER.error("Failed to parse nature harmony {}: {}", source, ex.getMessage());
                continue;
            }

            parsed.put(id, new NatureHarmonyRegistry.HarmonyEntry(replace, set));
        }

        NatureHarmonyRegistry.reload(parsed);
    }

    private static Identifier parseIdentifier(JsonObject json, Identifier fileId, String source) {
        String idValue = RegistryJsonHelper.getString(json, "id", null);
        Identifier id = idValue != null ? Identifier.tryParse(idValue) : null;
        if (id == null) {
            id = fileId;
        }
        if (id == null) {
            Petsplus.LOGGER.error("Nature harmony {} is missing an identifier", source);
            return null;
        }
        return id;
    }

    private static NatureHarmonySet.Type parseType(JsonObject json, String source) {
        String typeValue = RegistryJsonHelper.getString(json, "type", "harmony");
        if (typeValue == null) {
            return NatureHarmonySet.Type.HARMONY;
        }
        String normalized = typeValue.trim().toUpperCase(Locale.ROOT);
        try {
            return NatureHarmonySet.Type.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.error("Nature harmony {} has unknown type '{}'", source, typeValue);
            return NatureHarmonySet.Type.HARMONY;
        }
    }

    private static List<NatureHarmonySet.Member> parseMembers(JsonObject json, String source) {
        JsonArray membersArray = RegistryJsonHelper.getArray(json, "members");
        List<NatureHarmonySet.Member> members = new ArrayList<>();
        if (membersArray == null) {
            Petsplus.LOGGER.error("Nature harmony {} is missing 'members' array", source);
            return members;
        }
        for (int i = 0; i < membersArray.size(); i++) {
            JsonElement entry = membersArray.get(i);
            NatureHarmonySet.Member member = parseMemberEntry(entry, source, i);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    private static NatureHarmonySet.Member parseMemberEntry(JsonElement element, String source, int index) {
        if (element.isJsonPrimitive()) {
            Identifier nature = Identifier.tryParse(element.getAsString());
            if (nature == null) {
                Petsplus.LOGGER.error("Nature harmony {} references invalid identifier '{}' at index {}", source, element.getAsString(), index);
                return null;
            }
            return new NatureHarmonySet.Member(nature, Set.of());
        }
        if (!element.isJsonObject()) {
            Petsplus.LOGGER.error("Nature harmony {} has invalid member entry at index {}", source, index);
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String natureValue = RegistryJsonHelper.getString(obj, "nature", null);
        Identifier nature = natureValue != null ? Identifier.tryParse(natureValue) : null;
        if (nature == null) {
            Petsplus.LOGGER.error("Nature harmony {} is missing valid 'nature' for member at index {}", source, index);
            return null;
        }
        Set<Identifier> signs = new HashSet<>();
        JsonArray signArray = RegistryJsonHelper.getArray(obj, "signs");
        if (signArray != null) {
            for (int i = 0; i < signArray.size(); i++) {
                JsonElement signElement = signArray.get(i);
                if (!signElement.isJsonPrimitive()) {
                    Petsplus.LOGGER.error("Nature harmony {} has non-string sign entry for member {} at index {}", source, index, i);
                    continue;
                }
                Identifier signId = Identifier.tryParse(signElement.getAsString());
                if (signId == null) {
                    Petsplus.LOGGER.error("Nature harmony {} references invalid sign identifier '{}' for member {}", source, signElement.getAsString(), index);
                    continue;
                }
                signs.add(signId);
            }
        }
        String signValue = RegistryJsonHelper.getString(obj, "sign", null);
        if (signValue != null) {
            Identifier signId = Identifier.tryParse(signValue);
            if (signId != null) {
                signs.add(signId);
            } else {
                Petsplus.LOGGER.error("Nature harmony {} references invalid sign identifier '{}' for member {}", source, signValue, index);
            }
        }
        return new NatureHarmonySet.Member(nature, signs);
    }

    private static List<String> parseTags(JsonObject json) {
        JsonArray tagsArray = RegistryJsonHelper.getArray(json, "tags");
        if (tagsArray == null || tagsArray.isEmpty()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(tagsArray.size());
        for (int i = 0; i < tagsArray.size(); i++) {
            JsonElement element = tagsArray.get(i);
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString();
            if (value != null && !value.isBlank()) {
                tags.add(value);
            }
        }
        return List.copyOf(tags);
    }
}
