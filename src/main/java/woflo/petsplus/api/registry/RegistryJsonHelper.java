package woflo.petsplus.api.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.BuffEffect;

/**
 * Shared helpers for registry bootstrap JSON parsing.
 */
public final class RegistryJsonHelper {
    public static final Codec<JsonObject> JSON_OBJECT_CODEC = Codec.PASSTHROUGH.xmap(
        dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
        json -> new Dynamic<>(JsonOps.INSTANCE, json)
    );

    private RegistryJsonHelper() {
    }

    public static int getInt(JsonObject json, String key, int defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseConfigVariable(element.getAsString(), defaultValue);
        }

        return defaultValue;
    }

    public static double getDouble(JsonObject json, String key, double defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseConfigVariable(element.getAsString(), defaultValue);
        }

        return defaultValue;
    }

    public static float getFloat(JsonObject json, String key, float defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsFloat();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return (float) parseConfigVariable(element.getAsString(), defaultValue);
        }

        return defaultValue;
    }

    public static long getLong(JsonObject json, String key, long defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }

        return defaultValue;
    }

    public static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }

        return defaultValue;
    }

    @Nullable
    public static String getString(JsonObject json, String key, @Nullable String defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }

        return defaultValue;
    }

    @Nullable
    public static JsonObject getObject(JsonObject json, String key) {
        if (json == null || !json.has(key)) {
            return null;
        }

        JsonElement element = json.get(key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }

        return null;
    }

    @Nullable
    public static JsonArray getArray(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }

        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }

        return null;
    }

    @Nullable
    public static StatusEffectInstance parseStatusEffect(JsonObject effectJson) {
        try {
            JsonElement idElement = effectJson.get("id");
            if (idElement == null || idElement.isJsonNull()) {
                Petsplus.LOGGER.warn("Status effect missing 'id' field in JSON: {}", effectJson);
                return null;
            }

            String effectId = idElement.getAsString();
            int duration = getInt(effectJson, "duration", 60);
            int amplifier = getInt(effectJson, "amplifier", 0);

            StatusEffect statusEffect = Registries.STATUS_EFFECT.get(Identifier.of(effectId));
            if (statusEffect != null) {
                return new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(statusEffect), duration, amplifier);
            }

            Petsplus.LOGGER.warn("Unknown status effect: {}", effectId);
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse status effect from JSON: {}", effectJson, e);
        }

        return null;
    }

    public static BuffEffect.Target parseBuffTarget(String targetString) {
        if (targetString == null) {
            return BuffEffect.Target.OWNER;
        }

        return switch (targetString.toLowerCase()) {
            case "pet" -> BuffEffect.Target.PET;
            case "victim" -> BuffEffect.Target.VICTIM;
            case "mount" -> BuffEffect.Target.MOUNT;
            default -> BuffEffect.Target.OWNER;
        };
    }

    private static int parseConfigVariable(String value, int defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            int delimiter = configPath.lastIndexOf('.');
            if (delimiter > 0 && delimiter < configPath.length() - 1) {
                String scope = configPath.substring(0, delimiter);
                String key = configPath.substring(delimiter + 1);
                return PetsPlusConfig.getInstance().resolveScopedInt(scope, key, defaultValue);
            }
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseConfigVariable(String value, double defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            int delimiter = configPath.lastIndexOf('.');
            if (delimiter > 0 && delimiter < configPath.length() - 1) {
                String scope = configPath.substring(0, delimiter);
                String key = configPath.substring(delimiter + 1);
                return PetsPlusConfig.getInstance().resolveScopedDouble(scope, key, defaultValue);
            }
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
