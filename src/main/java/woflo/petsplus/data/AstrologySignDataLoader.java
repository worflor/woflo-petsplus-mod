package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureModifierSampler;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;
import woflo.petsplus.stats.nature.astrology.AstrologySignDefinition;
import woflo.petsplus.stats.nature.astrology.AstrologySignDefinition.AstrologyFlavorHookSlot;
import woflo.petsplus.stats.nature.astrology.AstrologySignDefinition.DisplayStyle;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Datapack loader for Lunaris star sign definitions.
 */
public final class AstrologySignDataLoader extends BaseJsonDataLoader<AstrologySignDefinition> {

    public AstrologySignDataLoader() {
        super("astrology/signs", "astrology_sign_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "astrology sign";
    }

    @Override
    protected void apply(java.util.Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        List<AstrologySignDefinition> definitions = new ArrayList<>();
        for (java.util.Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Astrology sign data at {} must be an object", source);
                continue;
            }

            try {
                AstrologySignDefinition definition = parseDefinition(fileId, element.getAsJsonObject(), source);
                if (definition != null) {
                    definitions.add(definition);
                }
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Failed to parse astrology sign definition at {}", source, ex);
            }
        }

        AstrologyRegistry.reload(definitions);
    }

    private static AstrologySignDefinition parseDefinition(Identifier fileId, JsonObject json, String source) {
        Identifier id = Identifier.of(fileId.getNamespace(), fileId.getPath());
        AstrologySignDefinition.Builder builder = AstrologySignDefinition.builder(id);

        int order = RegistryJsonHelper.getInt(json, "order", RegistryJsonHelper.getInt(json, "start_day", 0));
        builder.order(order);
        builder.displayName(RegistryJsonHelper.getString(json, "display_name", null));

        JsonObject rangeJson = RegistryJsonHelper.getObject(json, "range");
        int startDay = rangeJson != null ? RegistryJsonHelper.getInt(rangeJson, "start_day", order) : order;
        int endDay = rangeJson != null ? RegistryJsonHelper.getInt(rangeJson, "end_day", (startDay + 29) % 360) : (startDay + 29) % 360;
        boolean wrap = rangeJson != null && RegistryJsonHelper.getBoolean(rangeJson, "wrap", startDay > endDay);
        builder.dayRange(new AstrologySignDefinition.Range(startDay, endDay, wrap));

        String dayWindowStr = RegistryJsonHelper.getString(json, "day_window", "any");
        builder.dayWindow(parseEnum(dayWindowStr, AstrologySignDefinition.DayWindow.class, AstrologySignDefinition.DayWindow.ANY, source));

        String weatherStr = RegistryJsonHelper.getString(json, "weather", "any");
        builder.weatherWindow(parseEnum(weatherStr, AstrologySignDefinition.WeatherWindow.class, AstrologySignDefinition.WeatherWindow.ANY, source));

        builder.requiresOpenSky(RegistryJsonHelper.getBoolean(json, "requires_open_sky", false));
        builder.allowIndoors(RegistryJsonHelper.getBoolean(json, "allow_indoors", true));

        JsonArray moonArray = RegistryJsonHelper.getArray(json, "moon_phases");
        if (moonArray != null) {
            Set<Integer> phases = new HashSet<>();
            for (JsonElement phaseElement : moonArray) {
                if (phaseElement.isJsonPrimitive() && phaseElement.getAsJsonPrimitive().isNumber()) {
                    int value = Math.floorMod(phaseElement.getAsInt(), 8);
                    phases.add(value);
                }
            }
            builder.moonPhases(phases);
        }

        JsonArray dimensionArray = RegistryJsonHelper.getArray(json, "allowed_dimensions");
        if (dimensionArray != null) {
            Set<Identifier> dimensions = new HashSet<>();
            for (JsonElement dimElement : dimensionArray) {
                String dimStr = dimElement.getAsString();
                Identifier dimId = Identifier.tryParse(dimStr);
                if (dimId != null) {
                    dimensions.add(dimId);
                } else {
                    Petsplus.LOGGER.warn("Astrology sign {} references invalid dimension '{}' in {}", id, dimStr, source);
                }
            }
            builder.allowedDimensions(dimensions);
        }

        JsonArray envArray = RegistryJsonHelper.getArray(json, "required_environment");
        if (envArray != null) {
            EnumSet<AstrologySignDefinition.EnvironmentTag> tags = EnumSet.noneOf(AstrologySignDefinition.EnvironmentTag.class);
            for (JsonElement envElement : envArray) {
                String value = envElement.getAsString();
                AstrologySignDefinition.EnvironmentTag tag = parseEnum(value, AstrologySignDefinition.EnvironmentTag.class, null, source);
                if (tag != null) {
                    tags.add(tag);
                }
            }
            builder.requiredEnvironment(tags);
        }

        JsonObject nearbyJson = RegistryJsonHelper.getObject(json, "nearby");
        if (nearbyJson != null) {
            int minPlayers = RegistryJsonHelper.getInt(nearbyJson, "min_players", 0);
            int maxPlayers = RegistryJsonHelper.getInt(nearbyJson, "max_players", Integer.MAX_VALUE);
            int minPets = RegistryJsonHelper.getInt(nearbyJson, "min_pets", 0);
            int maxPets = RegistryJsonHelper.getInt(nearbyJson, "max_pets", Integer.MAX_VALUE);
            builder.nearbyConstraints(new AstrologySignDefinition.NearbyConstraints(minPlayers, maxPlayers, minPets, maxPets));
        }

        String epithet = RegistryJsonHelper.getString(json, "display_epithet", null);
        builder.displayEpithet(epithet);
        String styleValue = RegistryJsonHelper.getString(json, "display_style", null);
        if (styleValue != null) {
            builder.displayStyle(parseEnum(styleValue, DisplayStyle.class, DisplayStyle.SUFFIX, source));
        }
        String connector = RegistryJsonHelper.getString(json, "display_connector", null);
        if (connector != null) {
            builder.displayConnector(connector);
        }

        JsonObject statsJson = RegistryJsonHelper.getObject(json, "stat_profile");
        if (statsJson == null) {
            throw new IllegalArgumentException("Missing stat_profile for " + source);
        }
        NatureModifierSampler.NatureStat majorStat = parseEnum(RegistryJsonHelper.getString(statsJson, "major", "speed"),
            NatureModifierSampler.NatureStat.class, NatureModifierSampler.NatureStat.SPEED, source);
        NatureModifierSampler.NatureStat minorStat = parseEnum(RegistryJsonHelper.getString(statsJson, "minor", "agility"),
            NatureModifierSampler.NatureStat.class, NatureModifierSampler.NatureStat.AGILITY, source);
        float majorBase = RegistryJsonHelper.getFloat(statsJson, "major_base", 0.05f);
        float minorBase = RegistryJsonHelper.getFloat(statsJson, "minor_base", 0.03f);
        float volatility = RegistryJsonHelper.getFloat(statsJson, "volatility", 1.0f);
        float resilience = RegistryJsonHelper.getFloat(statsJson, "resilience", 1.0f);
        float contagion = RegistryJsonHelper.getFloat(statsJson, "contagion", 1.0f);
        float guard = RegistryJsonHelper.getFloat(statsJson, "guard", 1.0f);
        builder.statProfile(new AstrologySignDefinition.StatProfile(
            majorStat, majorBase,
            minorStat, minorBase,
            volatility, resilience, contagion, guard
        ));

        JsonObject emotionsJson = RegistryJsonHelper.getObject(json, "emotions");
        if (emotionsJson == null) {
            throw new IllegalArgumentException("Missing emotions for " + source);
        }
        PetComponent.Emotion majorEmotion = parseEnum(RegistryJsonHelper.getString(emotionsJson, "major", "HOPEFUL"),
            PetComponent.Emotion.class, null, source);
        PetComponent.Emotion minorEmotion = parseEnum(RegistryJsonHelper.getString(emotionsJson, "minor", "CURIOUS"),
            PetComponent.Emotion.class, null, source);
        PetComponent.Emotion quirkEmotion = parseEnum(RegistryJsonHelper.getString(emotionsJson, "quirk", "CHEERFUL"),
            PetComponent.Emotion.class, null, source);
        float majorStrength = RegistryJsonHelper.getFloat(emotionsJson, "major_strength", 0.35f);
        float minorStrength = RegistryJsonHelper.getFloat(emotionsJson, "minor_strength", 0.25f);
        float quirkStrength = RegistryJsonHelper.getFloat(emotionsJson, "quirk_strength", 0.20f);
        builder.emotionProfile(new AstrologySignDefinition.EmotionProfile(
            majorEmotion, majorStrength,
            minorEmotion, minorStrength,
            quirkEmotion, quirkStrength
        ));

        JsonArray flavorArray = RegistryJsonHelper.getArray(json, "flavor_hooks");
        if (flavorArray != null) {
            List<AstrologySignDefinition.AstrologyFlavorHook> hooks = new ArrayList<>();
            for (JsonElement hookElement : flavorArray) {
                if (!hookElement.isJsonObject()) {
                    continue;
                }
                JsonObject hookJson = hookElement.getAsJsonObject();
                String triggerStr = RegistryJsonHelper.getString(hookJson, "trigger", null);
                String slotStr = RegistryJsonHelper.getString(hookJson, "slot", null);
                if (triggerStr == null || slotStr == null) {
                    continue;
                }
                NatureFlavorHandler.Trigger trigger = parseEnum(triggerStr, NatureFlavorHandler.Trigger.class, null, source);
                AstrologyFlavorHookSlot slot =
                    parseEnum(slotStr, AstrologyFlavorHookSlot.class, null, source);
                if (trigger == null || slot == null) {
                    continue;
                }
                float scale = RegistryJsonHelper.getFloat(hookJson, "scale", 0.4f);
                long cooldown = RegistryJsonHelper.getLong(hookJson, "cooldown", 200L);
                boolean append = RegistryJsonHelper.getBoolean(hookJson, "append", false);
                hooks.add(new AstrologySignDefinition.AstrologyFlavorHook(slot, trigger, scale, cooldown, append));
            }
            builder.flavorHooks(hooks);
        }

        return builder.build();
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> type, T fallback, String source) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ex) {
            if (fallback == null) {
                Petsplus.LOGGER.warn("Unknown {} '{}' encountered in {}", type.getSimpleName(), value, source);
            }
            return fallback;
        }
    }
}
