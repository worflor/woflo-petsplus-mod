package woflo.petsplus.ai.planner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.data.BaseJsonDataLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads behaviour fragments and plans from data resources.
 */
public final class ActionPlanDataLoader extends BaseJsonDataLoader<Void> {

    private static final String ROOT_PATH = "action_plans";

    public ActionPlanDataLoader() {
        super(ROOT_PATH, "action_plans");
    }

    @Override
    protected String getResourceTypeName() {
        return "action plan";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        PlanRegistry.clearDataDriven();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Skipping action plan {} because it is not an object", describeSource(id));
                continue;
            }

            JsonObject json = element.getAsJsonObject();
            if (json.has("fragments")) {
                JsonArray fragments = json.getAsJsonArray("fragments");
                for (JsonElement fragmentElement : fragments) {
                    if (!fragmentElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = fragmentElement.getAsJsonObject();
                    Identifier fragmentId = Identifier.of(obj.get("id").getAsString());
                    String description = obj.has("description") ? obj.get("description").getAsString() : "";
                    List<String> tags = new ArrayList<>();
                    if (obj.has("tags")) {
                        for (JsonElement tag : obj.getAsJsonArray("tags")) {
                            tags.add(tag.getAsString());
                        }
                    }
                    PlanRegistry.registerFragment(new ActionFragment(fragmentId, description, tags));
                }
            }

            if (!json.has("plan")) {
                Petsplus.LOGGER.warn("Action plan {} is missing 'plan' section", describeSource(id));
                continue;
            }

            JsonObject plan = json.getAsJsonObject("plan");
            Identifier planId = plan.has("id") ? Identifier.of(plan.get("id").getAsString()) : id;
            Identifier goalId = Identifier.of(plan.get("goal").getAsString());
            boolean requiresGroup = plan.has("requires_owner_group") && plan.get("requires_owner_group").getAsBoolean();
            List<ActionPlan.Step> steps = new ArrayList<>();
            JsonArray arr = plan.getAsJsonArray("steps");
            for (JsonElement step : arr) {
                ActionPlan.Step parsed = parseStep(step);
                if (parsed != null) {
                    steps.add(parsed);
                }
            }
            PlanRegistry.registerPlan(new ActionPlan(planId, goalId, steps, requiresGroup));
        }
    }

    private ActionPlan.Step parseStep(JsonElement element) {
        if (element.isJsonPrimitive()) {
            Identifier fragmentId = Identifier.of(element.getAsString());
            return new ActionPlan.Step(fragmentId, List.of(), List.of());
        }
        if (!element.isJsonObject()) {
            Petsplus.LOGGER.warn("Ignoring malformed plan step entry: {}", element);
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        Identifier fragmentId = Identifier.of(obj.get("fragment").getAsString());
        List<Identifier> variantPool = new ArrayList<>();
        if (obj.has("variants")) {
            for (JsonElement variant : obj.getAsJsonArray("variants")) {
                variantPool.add(Identifier.of(variant.getAsString()));
            }
        }
        List<String> tags;
        if (obj.has("tags")) {
            List<String> parsedTags = new ArrayList<>();
            for (JsonElement tag : obj.getAsJsonArray("tags")) {
                parsedTags.add(tag.getAsString());
            }
            tags = List.copyOf(parsedTags);
        } else {
            tags = Collections.emptyList();
        }
        return new ActionPlan.Step(fragmentId, variantPool, tags);
    }
}

