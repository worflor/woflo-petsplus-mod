package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.lang.reflect.Constructor;
import java.util.Locale;

final class GoalDataParser {

    private GoalDataParser() {
    }

    static GoalDefinition parse(Identifier id, JsonObject json) {
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

        MobCapabilities.CapabilityRequirement requirement = resolveRequirement(getString(json, "requirement"));

        Vec2f energyRange = parseVec2(json.get("energy_range"));
        GoalDefinition.IdleStaminaBias idleBias = json.has("idle_stamina_bias")
            ? GoalDefinition.IdleStaminaBias.valueOf(getString(json, "idle_stamina_bias").toUpperCase(Locale.ROOT))
            : GoalDefinition.IdleStaminaBias.CENTERED;
        boolean socialBias = json.has("social_idle_bias") && json.get("social_idle_bias").getAsBoolean();

        String factoryClass = getString(json, "goal_factory");
        GoalDefinition.GoalFactory factory = createFactory(factoryClass);

        return new GoalDefinition(
            id,
            category,
            priority,
            minCooldown,
            maxCooldown,
            requirement,
            energyRange,
            idleBias,
            socialBias,
            factory
        );
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

    private static MobCapabilities.CapabilityRequirement resolveRequirement(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "any" -> MobCapabilities.CapabilityRequirement.any();
            case "land" -> MobCapabilities.CapabilityRequirement.land();
            case "flying" -> MobCapabilities.CapabilityRequirement.flying();
            case "aquatic" -> MobCapabilities.CapabilityRequirement.aquatic();
            case "tamed" -> MobCapabilities.CapabilityRequirement.tamed();
            case "item_handler" -> MobCapabilities.CapabilityRequirement.itemHandler();
            default -> throw new JsonSyntaxException("Unknown requirement '" + value + "'");
        };
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

