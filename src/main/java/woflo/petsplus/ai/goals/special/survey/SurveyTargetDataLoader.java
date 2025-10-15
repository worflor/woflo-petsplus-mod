package woflo.petsplus.ai.goals.special.survey;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.special.survey.SurveyTargetRegistry.SurveyTarget;
import woflo.petsplus.ai.goals.special.survey.SurveyTargetRegistry.Kind;
import woflo.petsplus.data.BaseJsonDataLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data loader that reads survey target definitions from datapacks. Each JSON
 * file contributes one or more targets to the catalogue. When the reload
 * completes the registry is replaced wholesale with the newly parsed
 * definitions.
 */
public final class SurveyTargetDataLoader extends BaseJsonDataLoader<Void> {

    private static final String ROOT_PATH = "survey_targets";

    public SurveyTargetDataLoader() {
        super(ROOT_PATH, "survey_targets");
    }

    @Override
    protected String getResourceTypeName() {
        return "aerial survey target";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        List<SurveyTarget> collected = new ArrayList<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) {
                Petsplus.LOGGER.warn("Skipping survey target file {} because it is not a JSON object", describeSource(id));
                continue;
            }

            JsonObject obj = json.getAsJsonObject();
            Identifier dimension = null;
            if (obj.has("dimension")) {
                dimension = Identifier.tryParse(obj.get("dimension").getAsString());
                if (dimension == null) {
                    Petsplus.LOGGER.warn("Ignoring invalid dimension id in {}", describeSource(id));
                }
            }

            if (!obj.has("targets") || !obj.get("targets").isJsonArray()) {
                Petsplus.LOGGER.warn("Survey target definition {} missing 'targets' array", describeSource(id));
                continue;
            }

            JsonArray targetsArray = obj.getAsJsonArray("targets");
            for (JsonElement element : targetsArray) {
                if (!element.isJsonObject()) {
                    Petsplus.LOGGER.warn("Skipping malformed target entry in {} (expected object)", describeSource(id));
                    continue;
                }
                parseTarget(dimension, element.getAsJsonObject()).ifPresent(collected::add);
            }
        }

        SurveyTargetRegistry.reload(collected);
    }

    private static java.util.Optional<SurveyTarget> parseTarget(Identifier dimension, JsonObject obj) {
        if (!obj.has("id")) {
            Petsplus.LOGGER.warn("Survey target entry missing 'id'");
            return java.util.Optional.empty();
        }
        Identifier targetId = Identifier.tryParse(obj.get("id").getAsString());
        if (targetId == null) {
            Petsplus.LOGGER.warn("Survey target entry has invalid id '{}'", obj.get("id").getAsString());
            return java.util.Optional.empty();
        }

        String kindRaw = obj.has("kind") ? obj.get("kind").getAsString() : "structure_tag";
        Kind kind;
        try {
            kind = Kind.fromString(kindRaw);
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("{}", ex.getMessage());
            return java.util.Optional.empty();
        }

        float weight = obj.has("weight") ? obj.get("weight").getAsFloat() : 1.0f;
        double minDistance = obj.has("min_distance") ? obj.get("min_distance").getAsDouble() : 48.0;
        double maxDistance = obj.has("max_distance") ? obj.get("max_distance").getAsDouble() : 256.0;
        int searchRadius = obj.has("search_radius") ? obj.get("search_radius").getAsInt() : 192;
        boolean skipKnown = !obj.has("skip_known") || obj.get("skip_known").getAsBoolean();
        int cooldown = obj.has("cooldown_ticks") ? obj.get("cooldown_ticks").getAsInt() : 1200;

        SurveyTarget target = new SurveyTarget(
            targetId,
            kind,
            dimension,
            weight,
            minDistance,
            maxDistance,
            searchRadius,
            skipKnown,
            cooldown
        );
        return java.util.Optional.of(target);
    }
}
