package woflo.petsplus.api.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.Locale;

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
        if (json == null || !json.has(key)) {
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
        if (json == null || !json.has(key)) {
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
        if (json == null || !json.has(key)) {
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
        if (json == null || !json.has(key)) {
            return defaultValue;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }

        return defaultValue;
    }

    public static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        if (json == null || !json.has(key)) {
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
        if (json == null || !json.has(key)) {
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

    /** Extracts a top-level {@code schemaVersion} integer from a JSON element, if present. */
    public static OptionalInt getSchemaVersionFromJson(JsonElement elem) {
        if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) {
            return OptionalInt.empty();
        }
        return getSchemaVersionFromObject(elem.getAsJsonObject());
    }

    /** Extracts a top-level {@code schemaVersion} integer from a JSON object, if present. */
    public static OptionalInt getSchemaVersionFromObject(JsonObject obj) {
        if (obj == null) {
            return OptionalInt.empty();
        }
        JsonElement sv = obj.get("schemaVersion");
        if (sv != null && sv.isJsonPrimitive() && sv.getAsJsonPrimitive().isNumber()) {
            try {
                return OptionalInt.of(sv.getAsInt());
            } catch (Exception ignored) {
                // best-effort only
            }
        }
        return OptionalInt.empty();
    }

    /** Reads a classpath resource and returns the top-level {@code schemaVersion}, if it can be parsed. */
    public static OptionalInt readSchemaVersionFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return OptionalInt.empty();
        }
        try (InputStream in = RegistryJsonHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return OptionalInt.empty();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement elem = JsonParser.parseReader(reader);
                return getSchemaVersionFromJson(elem);
            }
        } catch (Exception ex) {
            return OptionalInt.empty();
        }
    }

    /**
     * Produces a concise summary string describing a safe reload plan. This intentionally performs
     * no I/O and is purely informational.
     *
     * @param what target scope: ai, abilities, tags, or all
     * @param safe whether the reload plan should run in safe mode
     * @return concise plan summary string
     */
    public static String planSafeReload(String what, boolean safe) {
        if (what == null) {
            return "unknown target: plan skipped (no-op)";
        }
        String w = what.trim().toLowerCase(Locale.ROOT);
        String suffix = "validate\u2192build-candidate\u2192safe=" + safe + "\u2192plan-swap (no-op)";

        switch (w) {
            case "ai":
                return "ai: " + suffix;
            case "abilities":
                return "abilities: " + suffix;
            case "tags":
                return "tags: " + suffix;
            case "all":
                return "ai: " + suffix + " | abilities: " + suffix + " | tags: " + suffix;
            default:
                return "unknown target: plan skipped (no-op)";
        }
    }

    /** Tri-state schemaVersion health indicator. */
    public enum SchemaVersionHealth {
        ABSENT,
        PRESENT_INTEGER,
        PRESENT_NON_INTEGER
    }

    /**
     * Best-effort health from a parsed JSON element.
     * If JSON is not an object, returns ABSENT.
     */
    public static SchemaVersionHealth getSchemaVersionHealthFromJson(JsonElement elem) {
        if (elem == null || elem.isJsonNull() || !elem.isJsonObject()) {
            return SchemaVersionHealth.ABSENT;
        }
        return getSchemaVersionHealthFromObject(elem.getAsJsonObject());
    }

    /**
     * Best-effort health from a parsed JSON object.
     * - If object missing 'schemaVersion' -> ABSENT
     * - If present and is a primitive number:
     *     - parse via BigDecimal and check scale()==0 -> PRESENT_INTEGER
     *     - otherwise PRESENT_NON_INTEGER
     * - Any other type -> PRESENT_NON_INTEGER
     */
    public static SchemaVersionHealth getSchemaVersionHealthFromObject(JsonObject obj) {
        try {
            if (obj == null) {
                return SchemaVersionHealth.ABSENT;
            }
            JsonElement sv = obj.get("schemaVersion");
            if (sv == null || sv.isJsonNull()) {
                return SchemaVersionHealth.ABSENT;
            }
            if (!sv.isJsonPrimitive()) {
                return SchemaVersionHealth.PRESENT_NON_INTEGER;
            }
            if (sv.getAsJsonPrimitive().isNumber()) {
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(sv.getAsString());
                    return (bd.scale() == 0)
                        ? SchemaVersionHealth.PRESENT_INTEGER
                        : SchemaVersionHealth.PRESENT_NON_INTEGER;
                } catch (Exception ignored) {
                    return SchemaVersionHealth.PRESENT_NON_INTEGER;
                }
            }
            // string/boolean primitive
            return SchemaVersionHealth.PRESENT_NON_INTEGER;
        } catch (Exception ignored) {
            return SchemaVersionHealth.ABSENT;
        }
    }

    /**
     * Best-effort health read from a classpath resource.
     * On any failure, returns ABSENT. No logging.
     */
    public static SchemaVersionHealth readSchemaVersionHealthFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return SchemaVersionHealth.ABSENT;
        }
        try (InputStream in = RegistryJsonHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return SchemaVersionHealth.ABSENT;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement elem = JsonParser.parseReader(reader);
                return getSchemaVersionHealthFromJson(elem);
            }
        } catch (Exception ex) {
            return SchemaVersionHealth.ABSENT;
        }
    }
}
