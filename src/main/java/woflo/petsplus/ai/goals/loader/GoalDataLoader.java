package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.data.BaseJsonDataLoader;

import java.util.Map;

/**
 * Loads goal definitions from data packs and registers them in the adaptive goal registry.
 */
public final class GoalDataLoader extends BaseJsonDataLoader<Void> {

    private static final String ROOT_PATH = "goal_catalogue";

    public GoalDataLoader() {
        super(ROOT_PATH, "goal_catalogue");
    }

    @Override
    protected String getResourceTypeName() {
        return "adaptive goal";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        GoalRegistry.clearDataDriven();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Skipping goal definition {} because it is not an object", describeSource(id));
                continue;
            }
            try {
                GoalDefinition definition = GoalDataParser.parse(id, (JsonObject) element);
                GoalRegistry.registerDataDriven(definition);
            } catch (Exception e) {
                Petsplus.LOGGER.error("Failed to parse adaptive goal {}", describeSource(id), e);
            }
        }
    }
}

