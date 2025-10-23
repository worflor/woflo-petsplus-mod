package woflo.petsplus.abilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.registry.EffectSerializer;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.api.registry.TriggerSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Factory for creating abilities from JSON configuration.
 */
public class AbilityFactory {

    /**
     * Create an ability from JSON configuration.
     */
    public static Ability fromJson(JsonObject json) {
        try {
            String idString = json.get("id").getAsString();
            Identifier abilityId = Identifier.of(idString);

            // Parse trigger
            JsonObject triggerJson = json.getAsJsonObject("trigger");
            DataResult<Trigger> triggerResult = createTrigger(abilityId, triggerJson);
            Optional<Trigger> triggerOptional = triggerResult.resultOrPartial(error ->
                Petsplus.LOGGER.error("Ability {} trigger parsing error: {}", abilityId, error));
            if (triggerOptional.isEmpty()) {
                return null;
            }
            Trigger trigger = triggerOptional.get();

            // Parse effects
            JsonArray effectsJson = json.getAsJsonArray("effects");
            List<Effect> effects = new ArrayList<>();
            boolean effectError = false;
            for (int i = 0; i < effectsJson.size(); i++) {
                JsonElement effectElement = effectsJson.get(i);
                if (!effectElement.isJsonObject()) {
                    Petsplus.LOGGER.error("Ability {} effect entry at index {} is not a JSON object", abilityId, i);
                    effectError = true;
                    continue;
                }
                JsonObject effectJson = effectElement.getAsJsonObject();
                DataResult<Effect> effectResult = deserializeEffect(abilityId, effectJson,
                    "effects[" + i + "]");
                Optional<Effect> effectOptional = effectResult.resultOrPartial(error ->
                    Petsplus.LOGGER.error("Ability {} effect parsing error: {}", abilityId, error));
                if (effectOptional.isEmpty()) {
                    effectError = true;
                    continue;
                }
                effects.add(effectOptional.get());
            }

            if (effectError || effects.isEmpty()) {
                if (effects.isEmpty()) {
                    Petsplus.LOGGER.error("Ability {} has no valid effects after parsing", abilityId);
                }
                return null;
            }

            return new Ability(abilityId, trigger, effects, json);
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse ability from JSON: {}", json, e);
            return null;
        }
    }

    private static DataResult<Trigger> createTrigger(Identifier abilityId, JsonObject triggerJson) {
        String eventType = triggerJson.has("event") ? triggerJson.get("event").getAsString() : null;
        if (eventType == null) {
            return DataResult.error(() -> "Missing trigger event for ability " + abilityId);
        }

        Identifier typeId = RegistryJsonHelper.resolvePetsplusIdentifier(eventType);
        TriggerSerializer<?> serializer = PetsPlusRegistries.triggerSerializerRegistry().get(typeId);
        if (serializer == null) {
            return DataResult.error(() -> "Unknown trigger serializer " + typeId);
        }

        JsonObject config = triggerJson.deepCopy();
        config.remove("event");
        return serializer.read(abilityId, config);
    }

    private static DataResult<Effect> deserializeEffect(Identifier abilityId, JsonObject effectJson, String pointer) {
        String typeValue = RegistryJsonHelper.getString(effectJson, "type", null);
        if (typeValue == null) {
            return DataResult.error(() -> "Missing effect type at " + pointer);
        }

        Identifier typeId = RegistryJsonHelper.resolvePetsplusIdentifier(typeValue);
        EffectSerializer<?> serializer = PetsPlusRegistries.effectSerializerRegistry().get(typeId);
        if (serializer == null) {
            return DataResult.error(() -> "Unknown effect serializer " + typeId + " at " + pointer);
        }

        JsonObject config = effectJson.deepCopy();
        config.remove("type");
        return serializer.read(abilityId, config, new NestedEffectContext(abilityId, pointer));
    }

    private static final class NestedEffectContext implements EffectSerializer.Context {
        private final Identifier abilityId;
        private final String pointer;

        private NestedEffectContext(Identifier abilityId, String pointer) {
            this.abilityId = abilityId;
            this.pointer = pointer == null ? "" : pointer;
        }

        @Override
        public DataResult<Effect> deserialize(JsonObject json, String childPointer) {
            return deserializeEffect(abilityId, json, appendPointer(pointer, childPointer));
        }

        @Override
        public DataResult<List<Effect>> deserializeList(JsonArray array, String childPointer) {
            String base = appendPointer(pointer, childPointer);
            List<Effect> effects = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                String elementPointer = appendPointer(base, "[" + i + "]");
                if (!element.isJsonObject()) {
                    errors.add(elementPointer + " is not a JSON object");
                    continue;
                }
                DataResult<Effect> result = deserializeEffect(abilityId, element.getAsJsonObject(), elementPointer);
                result.resultOrPartial(errors::add).ifPresent(effects::add);
            }

            if (!errors.isEmpty()) {
                return DataResult.error(() -> String.join("; ", errors));
            }

            return DataResult.success(effects);
        }

        private static String appendPointer(String base, String addition) {
            if (addition == null || addition.isEmpty()) {
                return base == null ? "" : base;
            }
            if (base == null || base.isEmpty()) {
                return addition;
            }
            if (addition.startsWith("[") || addition.startsWith(".")) {
                return base + addition;
            }
            return base + "." + addition;
        }
    }
}
