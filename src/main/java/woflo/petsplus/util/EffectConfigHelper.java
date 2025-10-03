package woflo.petsplus.util;

import com.google.gson.JsonObject;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.registry.RegistryJsonHelper;

/**
 * Shared configuration parsing and validation utilities for effects.
 * Provides common patterns like clamped values, validated durations, and safe defaults.
 */
public final class EffectConfigHelper {

    private EffectConfigHelper() {
    }

    /**
     * Parse a radius value from JSON, ensuring it's at least the minimum.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default radius if not present
     * @param minRadius Minimum allowed radius
     * @return Validated radius value
     */
    public static double parseRadius(JsonObject json, String key, double defaultValue, double minRadius) {
        return Math.max(minRadius, RegistryJsonHelper.getDouble(json, key, defaultValue));
    }

    /**
     * Parse a radius value from JSON with default minimum of 0.25.
     */
    public static double parseRadius(JsonObject json, String key, double defaultValue) {
        return parseRadius(json, key, defaultValue, 0.25);
    }

    /**
     * Parse a duration in ticks, ensuring it's at least the minimum.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default duration if not present
     * @param minTicks Minimum allowed duration
     * @return Validated duration in ticks
     */
    public static int parseDuration(JsonObject json, String key, int defaultValue, int minTicks) {
        return Math.max(minTicks, RegistryJsonHelper.getInt(json, key, defaultValue));
    }

    /**
     * Parse a duration in ticks with default minimum of 1.
     */
    public static int parseDuration(JsonObject json, String key, int defaultValue) {
        return parseDuration(json, key, defaultValue, 1);
    }

    /**
     * Parse an amplifier value, ensuring it's non-negative.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default amplifier if not present
     * @return Validated amplifier value
     */
    public static int parseAmplifier(JsonObject json, String key, int defaultValue) {
        return Math.max(0, RegistryJsonHelper.getInt(json, key, defaultValue));
    }

    /**
     * Parse a level requirement, ensuring it's at least 1.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default level if not present
     * @return Validated level requirement
     */
    public static int parseMinLevel(JsonObject json, String key, int defaultValue) {
        return Math.max(1, RegistryJsonHelper.getInt(json, key, defaultValue));
    }

    /**
     * Parse a clamped double value.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default value if not present
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    public static double parseClampedDouble(JsonObject json, String key, double defaultValue, double min, double max) {
        return MathHelper.clamp(RegistryJsonHelper.getDouble(json, key, defaultValue), min, max);
    }

    /**
     * Parse a percentage value (0.0 to 1.0).
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default percentage if not present
     * @return Clamped percentage value
     */
    public static double parsePercentage(JsonObject json, String key, double defaultValue) {
        return parseClampedDouble(json, key, defaultValue, 0.0, 1.0);
    }

    /**
     * Parse a clamped integer value.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default value if not present
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    public static int parseClampedInt(JsonObject json, String key, int defaultValue, int min, int max) {
        return MathHelper.clamp(RegistryJsonHelper.getInt(json, key, defaultValue), min, max);
    }

    /**
     * Parse a non-negative double value.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default value if not present
     * @return Non-negative value
     */
    public static double parseNonNegativeDouble(JsonObject json, String key, double defaultValue) {
        return Math.max(0.0, RegistryJsonHelper.getDouble(json, key, defaultValue));
    }

    /**
     * Parse a non-negative integer value.
     * @param json The JSON configuration
     * @param key The key to read
     * @param defaultValue Default value if not present
     * @return Non-negative value
     */
    public static int parseNonNegativeInt(JsonObject json, String key, int defaultValue) {
        return Math.max(0, RegistryJsonHelper.getInt(json, key, defaultValue));
    }

    /**
     * Parse a particle count, ensuring it's non-negative.
     */
    public static int parseParticleCount(JsonObject json, String key, int defaultValue) {
        return parseNonNegativeInt(json, key, defaultValue);
    }

    /**
     * Parse a damage value, ensuring it's non-negative.
     */
    public static double parseDamage(JsonObject json, String key, double defaultValue) {
        return parseNonNegativeDouble(json, key, defaultValue);
    }
}
