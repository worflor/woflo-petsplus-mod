package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalMovementConfig;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GoalDataParser {

    static record ParsedGoal(GoalDefinition definition, GoalMovementConfig movementConfig) {
    }

    private GoalDataParser() {
    }

    static ParsedGoal parse(Identifier id, JsonObject json) {
        if (json == null) {
            throw new JsonSyntaxException("Goal definition for " + id + " must be an object");
        }

        GoalDefinition.Category category = GoalDefinition.Category.valueOf(
            getString(json, "category").toUpperCase(Locale.ROOT)
        );
        int priority = getInt(json, "priority");

        JsonObject cooldown = getObject(json, "cooldown");
        int minCooldown = getInt(cooldown, "min");
        int maxCooldown = getInt(cooldown, "max");

        MobCapabilities.CapabilityRequirement requirement = resolveRequirement(json.get("requirement"));

        Vec2f energyRange = parseVec2(json.get("energy_range"));
        GoalDefinition.IdleStaminaBias idleBias = json.has("idle_stamina_bias")
            ? GoalDefinition.IdleStaminaBias.valueOf(getString(json, "idle_stamina_bias").toUpperCase(Locale.ROOT))
            : GoalDefinition.IdleStaminaBias.CENTERED;
        boolean socialBias = json.has("social_idle_bias") && json.get("social_idle_bias").getAsBoolean();
        boolean marksMajorActivity = json.has("marks_major_activity") && json.get("marks_major_activity").getAsBoolean();

        String factoryClass = getString(json, "goal_factory");
        GoalDefinition.GoalFactory factory = createFactory(factoryClass);

        GoalDefinition definition = new GoalDefinition(
            id,
            category,
            priority,
            minCooldown,
            maxCooldown,
            requirement,
            energyRange,
            idleBias,
            socialBias,
            marksMajorActivity,
            factory
        );

        GoalMovementConfig movementConfig = parseMovementConfig(json, priority);

        return new ParsedGoal(definition, movementConfig);
    }

    private static GoalMovementConfig parseMovementConfig(JsonObject json, int defaultPriority) {
        if (!json.has("movement_role")) {
            return null;
        }

        String role = getString(json, "movement_role").toUpperCase(Locale.ROOT);
        switch (role) {
            case "NONE" -> {
                return GoalMovementConfig.none();
            }
            case "INFLUENCER" -> {
                float weight = getOptionalFloat(json, "movement_influencer_weight", 1.0f);
                return GoalMovementConfig.influencer(weight);
            }
            case "ACTOR" -> {
                int actorPriority = getOptionalInt(json, "movement_actor_priority", defaultPriority);
                return GoalMovementConfig.actor(actorPriority);
            }
            default -> throw new JsonSyntaxException("Unknown movement role: " + role);
        }
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonSyntaxException("Missing string field '" + key + "'");
        }
        return element.getAsString();
    }

    private static int getInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonSyntaxException("Missing integer field '" + key + "'");
        }
        return element.getAsInt();
    }

    private static int getOptionalInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive()) {
            throw new JsonSyntaxException("Optional integer field '" + key + "' must be a number");
        }
        return element.getAsInt();
    }

    private static float getOptionalFloat(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive()) {
            throw new JsonSyntaxException("Optional float field '" + key + "' must be a number");
        }
        return element.getAsFloat();
    }

    private static JsonObject getObject(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new JsonSyntaxException("Missing object field '" + key + "'");
        }
        return element.getAsJsonObject();
    }

    private static Vec2f parseVec2(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new Vec2f(0.0f, 1.0f);
        }
        JsonObject obj = element.getAsJsonObject();
        float min = obj.has("min") ? obj.get("min").getAsFloat() : 0.0f;
        float max = obj.has("max") ? obj.get("max").getAsFloat() : 1.0f;
        return new Vec2f(min, max);
    }

    private static MobCapabilities.CapabilityRequirement resolveRequirement(JsonElement element) {
        if (element == null) {
            throw new JsonSyntaxException("Missing field 'requirement'");
        }

        if (element.isJsonPrimitive()) {
            if (!element.getAsJsonPrimitive().isString()) {
                throw new JsonSyntaxException("Requirement must be a string or object");
            }
            try {
                return MobCapabilities.CapabilityRequirement.fromToken(element.getAsString());
            } catch (IllegalArgumentException ex) {
                throw new JsonSyntaxException(ex.getMessage(), ex);
            }
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.entrySet().size() != 1) {
                throw new JsonSyntaxException("Requirement object must contain exactly one operator");
            }

            if (obj.has("all_of")) {
                JsonArray array = getArray(obj, "all_of");
                if (array.size() == 0) {
                    throw new JsonSyntaxException("'all_of' must contain at least one requirement");
                }
                return MobCapabilities.CapabilityRequirement.allOf(resolveChildren(array));
            }

            if (obj.has("any_of")) {
                JsonArray array = getArray(obj, "any_of");
                if (array.size() == 0) {
                    throw new JsonSyntaxException("'any_of' must contain at least one requirement");
                }
                return MobCapabilities.CapabilityRequirement.anyOf(resolveChildren(array));
            }

            if (obj.has("not")) {
                JsonElement child = obj.get("not");
                if (child == null) {
                    throw new JsonSyntaxException("'not' operator requires a requirement expression");
                }
                return MobCapabilities.CapabilityRequirement.not(resolveRequirement(child));
            }

            throw new JsonSyntaxException("Unknown requirement operator in object");
        }

        throw new JsonSyntaxException("Requirement must be a string or object");
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new JsonSyntaxException("'" + key + "' must be an array");
        }
        return element.getAsJsonArray();
    }

    private static List<MobCapabilities.CapabilityRequirement> resolveChildren(JsonArray array) {
        List<MobCapabilities.CapabilityRequirement> children = new ArrayList<>(array.size());
        for (JsonElement child : array) {
            children.add(resolveRequirement(child));
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    private static GoalDefinition.GoalFactory createFactory(String className) {
        try {
            Class<?> raw = Class.forName(className);
            if (!AdaptiveGoal.class.isAssignableFrom(raw)) {
                throw new JsonSyntaxException("Goal factory class " + className + " does not extend AdaptiveGoal");
            }
            Constructor<? extends AdaptiveGoal> ctor = (Constructor<? extends AdaptiveGoal>) raw.getDeclaredConstructor(net.minecraft.entity.mob.MobEntity.class);
            ctor.setAccessible(true);
            return mob -> {
                try {
                    return ctor.newInstance(mob);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to instantiate goal factory " + className, e);
                }
            };
        } catch (ClassNotFoundException e) {
            throw new JsonSyntaxException("Unknown goal factory class: " + className, e);
        } catch (NoSuchMethodException e) {
            throw new JsonSyntaxException("Goal factory class missing MobEntity constructor: " + className, e);
        }
    }
}

