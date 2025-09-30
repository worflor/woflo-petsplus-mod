package woflo.petsplus.ai;

import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Locale;
import net.minecraft.util.JsonHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

/**
 * Registry of mood goal throttle presets with optional configuration overrides.
 */
public final class MoodGoalThrottles {
    private static final EnumMap<PetComponent.Mood, MoodGoalThrottleConfig> ACTIVE = new EnumMap<>(PetComponent.Mood.class);

    private MoodGoalThrottles() {}

    static {
        loadDefaults();
        applyConfigOverrides();
    }

    private static void loadDefaults() {
        ACTIVE.clear();
        ACTIVE.put(PetComponent.Mood.HAPPY, new MoodGoalThrottleConfig(110, 60, 105, 40, 60, 6, 24, 12, 150, 60, 1.6f));
        ACTIVE.put(PetComponent.Mood.PLAYFUL, new MoodGoalThrottleConfig(110, 70, 100, 42, 60, 6, 24, 12, 145, 70, 1.75f));
        ACTIVE.put(PetComponent.Mood.PASSIONATE, new MoodGoalThrottleConfig(140, 80, 120, 45, 70, 6, 24, 14, 160, 80, 2.0f));
        ACTIVE.put(PetComponent.Mood.CURIOUS, new MoodGoalThrottleConfig(105, 70, 110, 36, 55, 5, 20, 12, 150, 60, 1.55f));
        ACTIVE.put(PetComponent.Mood.SAUDADE, new MoodGoalThrottleConfig(165, 85, 150, 32, 70, 4, 16, 14, 170, 90, 2.35f));
        ACTIVE.put(PetComponent.Mood.YUGEN, new MoodGoalThrottleConfig(185, 95, 150, 30, 75, 4, 16, 14, 175, 110, 2.6f));
        ACTIVE.put(PetComponent.Mood.CALM, new MoodGoalThrottleConfig(150, 80, 135, 34, 60, 4, 16, 14, 165, 80, 2.1f));
        ACTIVE.put(PetComponent.Mood.BONDED, new MoodGoalThrottleConfig(125, 75, 120, 36, 55, 5, 20, 12, 155, 70, 1.85f));
        ACTIVE.put(PetComponent.Mood.FOCUSED, new MoodGoalThrottleConfig(135, 80, 125, 32, 55, 5, 20, 12, 150, 65, 1.55f));
        ACTIVE.put(PetComponent.Mood.RESTLESS, new MoodGoalThrottleConfig(130, 70, 105, 40, 50, 6, 24, 10, 145, 70, 1.45f));
        ACTIVE.put(PetComponent.Mood.AFRAID, new MoodGoalThrottleConfig(80, 50, 1, 0, 0, 0, 0, 1, 25, 40, 1.2f));
        ACTIVE.put(PetComponent.Mood.PROTECTIVE, new MoodGoalThrottleConfig(90, 60, 1, 0, 0, 0, 0, 1, 30, 55, 1.3f));
        ACTIVE.put(PetComponent.Mood.ANGRY, new MoodGoalThrottleConfig(100, 60, 1, 0, 0, 0, 0, 1, 25, 60, 1.45f));
        // SISU currently reuses FOCUSED behaviour
        ACTIVE.put(PetComponent.Mood.SISU, new MoodGoalThrottleConfig(140, 90, 125, 32, 55, 5, 20, 12, 155, 70, 1.6f));
    }

    private static void applyConfigOverrides() {
        PetsPlusConfig config;
        try {
            config = PetsPlusConfig.getInstance();
        } catch (Throwable e) {
            if (Petsplus.DEBUG_MODE) {
                Petsplus.LOGGER.warn("[MoodAI] Failed to load config overrides; using defaults", e);
            } else {
                Petsplus.LOGGER.debug("[MoodAI] Config overrides unavailable; using defaults");
            }
            return;
        }
        if (config == null) {
            return;
        }
        JsonObject moodSection = config.getSection("mood_ai");
        applyOverridesFromJson(moodSection);
    }

    static void applyOverridesFromJson(JsonObject moodSection) {
        if (moodSection == null || !moodSection.has("goals")) {
            return;
        }
        JsonObject goalsObject = moodSection.get("goals").isJsonObject()
            ? JsonHelper.getObject(moodSection, "goals", null)
            : null;
        if (goalsObject == null) {
            return;
        }
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            String key = mood.name().toLowerCase(Locale.ROOT);
            if (!goalsObject.has(key) || !goalsObject.get(key).isJsonObject()) {
                continue;
            }
            JsonObject override = JsonHelper.getObject(goalsObject, key, null);
            if (override == null) {
                continue;
            }
            MoodGoalThrottleConfig base = ACTIVE.get(mood);
            if (base == null) {
                continue;
            }
            ACTIVE.put(mood, merge(base, override, mood));
        }
    }

    private static MoodGoalThrottleConfig merge(MoodGoalThrottleConfig base, JsonObject override, PetComponent.Mood mood) {
        if (override == null) {
            return base;
        }
        int minCooldown = readInt(override, "min_cooldown_ticks", base != null ? base.minCooldownTicks() : 0);
        int minActive = readInt(override, "min_active_ticks", base != null ? base.minActiveTicks() : 0);
        int baseChance = readInt(override, "base_chance", base != null ? base.baseChance() : 1);
        int strengthBonus = readInt(override, "strength_chance_bonus", base != null ? base.strengthChanceBonus() : 0);
        int pressureInterval = readInt(override, "pressure_interval_ticks", base != null ? base.pressureIntervalTicks() : 0);
        int pressureBonus = readInt(override, "pressure_chance_bonus", base != null ? base.pressureChanceBonus() : 0);
        int maxPressure = readInt(override, "max_pressure_bonus", base != null ? base.maxPressureBonus() : 0);
        int minRoll = readInt(override, "min_roll", base != null ? base.minRollBound() : 1);
        int maxRoll = readInt(override, "max_roll", base != null ? base.maxRollBound() : Math.max(minRoll, 1));
        int followHold = readInt(override, "follow_hold_ticks", base != null ? base.followHoldTicks() : 0);
        float followBonus = readFloat(override, "follow_distance_bonus", base != null ? base.followDistanceBonus() : 0.0f);
        MoodGoalThrottleConfig config = new MoodGoalThrottleConfig(
            minCooldown,
            minActive,
            baseChance,
            strengthBonus,
            pressureInterval,
            pressureBonus,
            maxPressure,
            minRoll,
            maxRoll,
            followHold,
            followBonus
        );
        if (Petsplus.DEBUG_MODE) {
            Petsplus.LOGGER.debug("[MoodAI] Loaded throttle override for {}: {}", mood, config);
        }
        return config;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return object.get(key).getAsInt();
            } catch (NumberFormatException ignored) {
                Petsplus.LOGGER.warn("Invalid integer for mood throttle key '{}'", key);
            }
        }
        return fallback;
    }

    private static float readFloat(JsonObject object, String key, float fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return object.get(key).getAsFloat();
            } catch (NumberFormatException ignored) {
                Petsplus.LOGGER.warn("Invalid float for mood throttle key '{}'", key);
            }
        }
        return fallback;
    }

    public static MoodActionThrottle createThrottle(PetComponent.Mood mood) {
        MoodGoalThrottleConfig config = ACTIVE.get(mood);
        return config != null ? new MoodActionThrottle(config) : null;
    }

    public static MoodGoalThrottleConfig getConfig(PetComponent.Mood mood) {
        return ACTIVE.get(mood);
    }

    public static void reloadFromConfig() {
        loadDefaults();
        applyConfigOverrides();
    }
}
