package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed representation of a datapack-provided pet role definition. Converts raw
 * JSON payloads into {@link PetRoleType.Definition} instances for registry
 * hydration.
 */
public record PetRoleDefinition(
    Identifier id,
    String translationKey,
    Map<String, Float> baseStatScalars,
    List<Identifier> defaultAbilities,
    Map<Integer, Identifier> tributeItems,
    PetRoleType.XpCurve xpCurve,
    PetRoleType.Visual visual,
    Map<String, Float> statAffinities,
    PetRoleType.AttributeScaling attributeScaling
) {
    private static final PetRoleType.XpCurve DEFAULT_XP_CURVE = new PetRoleType.XpCurve(
        30,
        List.of(3, 7, 12, 17, 23, 27),
        List.of(10, 20, 30),
        20,
        8,
        0.75f
    );

    private static final Map<Integer, Identifier> DEFAULT_TRIBUTES = Map.of(
        10, Identifier.of("minecraft", "gold_ingot"),
        20, Identifier.of("minecraft", "diamond"),
        30, Identifier.of("minecraft", "netherite_ingot")
    );

    public PetRoleType.Definition toDefinition() {
        return new PetRoleType.Definition(
            id,
            translationKey,
            baseStatScalars,
            defaultAbilities,
            new PetRoleType.TributeDefaults(tributeItems),
            xpCurve,
            visual,
            statAffinities,
            attributeScaling
        );
    }

    public static PetRoleDefinition fromJson(Identifier sourceId, JsonObject json, String sourceDescription) {
        Identifier id = parseId(json, sourceId, sourceDescription);
        String translationKey = RegistryJsonHelper.getString(json, "translation_key", null);

        Map<String, Float> scalars = parseScalars(json, sourceDescription);
        List<Identifier> abilities = parseAbilityList(json, sourceDescription);
        Map<Integer, Identifier> tributes = parseTributes(json, sourceDescription);
        PetRoleType.XpCurve xpCurve = parseXpCurve(json, sourceDescription);
        PetRoleType.Visual visual = parseVisual(json, sourceDescription);
        Map<String, Float> statAffinities = parseStatAffinities(json, sourceDescription);
        PetRoleType.AttributeScaling attributeScaling = parseAttributeScaling(json, sourceDescription);

        if (abilities.isEmpty()) {
            Petsplus.LOGGER.warn("Role {} from {} defines no default abilities; pets will not have loadout entries until datapacks provide them.", id, sourceDescription);
        }

        return new PetRoleDefinition(id, translationKey, scalars, abilities, tributes, xpCurve, visual, statAffinities, attributeScaling);
    }

    private static Identifier parseId(JsonObject json, Identifier fallback, String source) {
        String idValue = RegistryJsonHelper.getString(json, "id", null);
        if (idValue == null || idValue.isBlank()) {
            return fallback;
        }
        Identifier parsed = Identifier.tryParse(idValue);
        if (parsed == null) {
            Petsplus.LOGGER.error("Role definition at {} has invalid id '{}'", source, idValue);
            return fallback;
        }
        return parsed;
    }

    private static Map<String, Float> parseScalars(JsonObject json, String source) {
        Map<String, Float> scalars = new LinkedHashMap<>();
        JsonObject scalarsObject = RegistryJsonHelper.getObject(json, "base_stat_scalars");
        if (scalarsObject == null) {
            return scalars;
        }

        for (Map.Entry<String, JsonElement> entry : scalarsObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                scalars.put(key, value.getAsFloat());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric base stat scalar '{}'", source, key);
            }
        }
        return scalars;
    }

    private static List<Identifier> parseAbilityList(JsonObject json, String source) {
        List<Identifier> abilities = new ArrayList<>();
        JsonArray abilityArray = RegistryJsonHelper.getArray(json, "default_abilities");
        if (abilityArray == null) {
            return abilities;
        }

        for (int i = 0; i < abilityArray.size(); i++) {
            JsonElement element = abilityArray.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                Petsplus.LOGGER.error("Role definition at {} has non-string ability entry at index {}", source, i);
                continue;
            }
            String raw = element.getAsString();
            Identifier abilityId = Identifier.tryParse(raw);
            if (abilityId == null) {
                abilityId = Identifier.of(Petsplus.MOD_ID, raw.toLowerCase(Locale.ROOT));
                Petsplus.LOGGER.warn("Role definition at {} used non-namespaced ability id '{}' at index {}; assuming petsplus:{}", source, raw, i, abilityId.getPath());
            }
            abilities.add(abilityId);
        }
        return abilities;
    }

    private static Map<Integer, Identifier> parseTributes(JsonObject json, String source) {
        Map<Integer, Identifier> tributes = new LinkedHashMap<>(DEFAULT_TRIBUTES);
        JsonObject tributeObject = RegistryJsonHelper.getObject(json, "tribute_defaults");
        if (tributeObject == null) {
            return tributes;
        }

        for (Map.Entry<String, JsonElement> entry : tributeObject.entrySet()) {
            try {
                int milestone = Integer.parseInt(entry.getKey());
                String itemId = entry.getValue().getAsString();
                Identifier identifier = Identifier.tryParse(itemId);
                if (identifier == null) {
                    Petsplus.LOGGER.error("Role definition at {} has invalid tribute item '{}' for level {}", source, itemId, milestone);
                    continue;
                }
                tributes.put(milestone, identifier);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.error("Role definition at {} has non-numeric tribute milestone '{}': {}", source, entry.getKey(), e.getMessage());
            } catch (Exception e) {
                Petsplus.LOGGER.error("Role definition at {} failed to parse tribute entry '{}': {}", source, entry.getKey(), e.getMessage());
            }
        }
        return tributes;
    }

    private static Map<String, Float> parseStatAffinities(JsonObject json, String source) {
        Map<String, Float> affinities = new LinkedHashMap<>();
        JsonObject affinityObject = RegistryJsonHelper.getObject(json, "stat_affinities");
        if (affinityObject == null) {
            return affinities;
        }

        for (Map.Entry<String, JsonElement> entry : affinityObject.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                affinities.put(entry.getKey(), value.getAsFloat());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric stat affinity '{}'", source, entry.getKey());
            }
        }

        return affinities;
    }

    private static PetRoleType.AttributeScaling parseAttributeScaling(JsonObject json, String source) {
        JsonObject scalingObject = RegistryJsonHelper.getObject(json, "attribute_scaling");
        if (scalingObject == null) {
            return PetRoleType.AttributeScaling.DEFAULT;
        }

        PetRoleType.AttributeScaling.Builder builder = PetRoleType.AttributeScaling.builder();

        builder.healthBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "health_bonus_per_level", 0.0));
        builder.healthPostSoftcapBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "health_post_softcap_bonus_per_level", 0.0));
        builder.healthSoftcapLevel(RegistryJsonHelper.getInt(scalingObject, "health_softcap_level", PetRoleType.AttributeScaling.DEFAULT.healthSoftcapLevel()));
        builder.healthMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "health_max_bonus", PetRoleType.AttributeScaling.DEFAULT.healthMaxBonus()));

        builder.speedBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "speed_bonus_per_level", 0.0));
        builder.speedMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "speed_max_bonus", PetRoleType.AttributeScaling.DEFAULT.speedMaxBonus()));

        builder.attackBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "attack_bonus_per_level", 0.0));
        builder.attackPostSoftcapBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "attack_post_softcap_bonus_per_level", 0.0));
        builder.attackSoftcapLevel(RegistryJsonHelper.getInt(scalingObject, "attack_softcap_level", PetRoleType.AttributeScaling.DEFAULT.attackSoftcapLevel()));
        builder.attackMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "attack_max_bonus", PetRoleType.AttributeScaling.DEFAULT.attackMaxBonus()));

        return builder.build();
    }

    private static PetRoleType.XpCurve parseXpCurve(JsonObject json, String source) {
        JsonObject xpObject = RegistryJsonHelper.getObject(json, "xp_curve");
        if (xpObject == null) {
            return DEFAULT_XP_CURVE;
        }

        int maxLevel = RegistryJsonHelper.getInt(xpObject, "max_level", DEFAULT_XP_CURVE.maxLevel());
        List<Integer> featureLevels = parseIntArray(xpObject, "feature_levels", DEFAULT_XP_CURVE.featureLevels(), source);
        List<Integer> tributeMilestones = parseIntArray(xpObject, "tribute_milestones", DEFAULT_XP_CURVE.tributeMilestones(), source);
        int baseLinear = RegistryJsonHelper.getInt(xpObject, "base_linear_per_level", DEFAULT_XP_CURVE.baseLinearPerLevel());
        int quadratic = RegistryJsonHelper.getInt(xpObject, "quadratic_factor", DEFAULT_XP_CURVE.quadraticFactor());
        float featureBonus = (float) RegistryJsonHelper.getDouble(xpObject, "feature_level_bonus_multiplier", DEFAULT_XP_CURVE.featureLevelBonusMultiplier());

        return new PetRoleType.XpCurve(maxLevel, featureLevels, tributeMilestones, baseLinear, quadratic, featureBonus);
    }

    private static List<Integer> parseIntArray(JsonObject json, String key, List<Integer> fallback, String source) {
        JsonArray array = RegistryJsonHelper.getArray(json, key);
        if (array == null) {
            return fallback;
        }

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                values.add(element.getAsInt());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric entry in '{}' at index {}", source, key, i);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private static PetRoleType.Visual parseVisual(JsonObject json, String source) {
        JsonObject visualObject = RegistryJsonHelper.getObject(json, "visual");
        if (visualObject == null) {
            return PetRoleType.Visual.DEFAULT;
        }

        int primary = parseColor(visualObject, "primary_color", PetRoleType.Visual.DEFAULT.primaryColor(), source);
        int secondary = parseColor(visualObject, "secondary_color", PetRoleType.Visual.DEFAULT.secondaryColor(), source);
        String ambient = RegistryJsonHelper.getString(visualObject, "ambient_event", PetRoleType.Visual.DEFAULT.ambientEvent());
        String prefix = RegistryJsonHelper.getString(visualObject, "ability_event_prefix", PetRoleType.Visual.DEFAULT.abilityEventPrefix());
        return new PetRoleType.Visual(primary, secondary, ambient, prefix);
    }

    private static int parseColor(JsonObject json, String key, int fallback, String source) {
        String value = RegistryJsonHelper.getString(json, key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = "0x" + normalized.substring(1);
        }

        try {
            return Integer.decode(normalized);
        } catch (NumberFormatException e) {
            Petsplus.LOGGER.error("Role definition at {} has invalid color '{}' for key '{}'", source, value, key);
            return fallback;
        }
    }
}
