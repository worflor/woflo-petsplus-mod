package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityFactory;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.api.registry.RegistryJsonHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loads ability definitions from datapacks and registers them into the ability registry.
 */
public class AbilityDataLoader extends BaseJsonDataLoader<Ability> {

    public AbilityDataLoader() {
        super("abilities", "ability_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "ability";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, LoadedAbility> definitions = new LinkedHashMap<>();
        Map<Identifier, String> abilitySources = new HashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Ability definition at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();

            Identifier abilityId = parseAbilityId(json, source);
            if (abilityId == null) {
                continue;
            }

            if (json.has("role")) {
                Petsplus.LOGGER.warn("Ability {} in {} still defines legacy field 'role'; loadouts are now provided by role definitions and this value will be ignored.", abilityId, source);
            }

            String description = RegistryJsonHelper.getString(json, "description", null);

            JsonObject triggerJson = RegistryJsonHelper.getObject(json, "trigger");
            if (triggerJson == null) {
                Petsplus.LOGGER.error("Ability {} in {} is missing trigger configuration", abilityId, source);
                continue;
            }

            String event = RegistryJsonHelper.getString(triggerJson, "event", null);
            if (event == null) {
                Petsplus.LOGGER.error("Ability {} in {} is missing a trigger event", abilityId, source);
                continue;
            }

            Identifier triggerId = RegistryJsonHelper.resolvePetsplusIdentifier(event);
            if (PetsPlusRegistries.triggerSerializerRegistry().get(triggerId) == null) {
                Petsplus.LOGGER.error("Ability {} in {} references unknown trigger {}", abilityId, source, triggerId);
                continue;
            }

            JsonArray effectsArray = RegistryJsonHelper.getArray(json, "effects");
            if (effectsArray == null || effectsArray.isEmpty()) {
                Petsplus.LOGGER.error("Ability {} in {} must define at least one effect", abilityId, source);
                continue;
            }

            boolean hasInvalidEffect = false;
            for (int i = 0; i < effectsArray.size(); i++) {
                JsonElement effectElement = effectsArray.get(i);
                if (!effectElement.isJsonObject()) {
                    Petsplus.LOGGER.error("Ability {} in {} has non-object effect entry at index {}", abilityId, source, i);
                    hasInvalidEffect = true;
                    continue;
                }

                JsonObject effectObject = effectElement.getAsJsonObject();
                String typeValue = RegistryJsonHelper.getString(effectObject, "type", null);
                if (typeValue == null) {
                    Petsplus.LOGGER.error("Ability {} in {} has an effect without a type at index {}", abilityId, source, i);
                    hasInvalidEffect = true;
                    continue;
                }

                Identifier effectId = RegistryJsonHelper.resolvePetsplusIdentifier(typeValue);
                if (PetsPlusRegistries.effectSerializerRegistry().get(effectId) == null) {
                    Petsplus.LOGGER.error("Ability {} in {} references unknown effect {} at index {}", abilityId, source, effectId, i);
                    hasInvalidEffect = true;
                }
            }

            if (hasInvalidEffect) {
                continue;
            }

            JsonObject baseDefinition = json.deepCopy();
            baseDefinition.remove("role");
            baseDefinition.remove("description");

            Ability parsedAbility = AbilityFactory.fromJson(baseDefinition);
            if (parsedAbility == null) {
                Petsplus.LOGGER.error("Ability {} in {} could not be instantiated; skipping", abilityId, source);
                continue;
            }

            Supplier<Ability> factory = () -> duplicateAbility(parsedAbility);
            definitions.put(abilityId, new LoadedAbility(abilityId, description, factory));
            String previousSource = abilitySources.put(abilityId, source);
            if (previousSource != null && !previousSource.equals(source)) {
                Petsplus.LOGGER.debug("Ability {} in {} overrides definition from {}", abilityId, source, previousSource);
            }
        }

        int updated = 0;
        int skipped = 0;
        for (LoadedAbility ability : definitions.values()) {
            AbilityType existing = PetsPlusRegistries.abilityTypeRegistry().get(ability.id());
            if (existing != null) {
                existing.update(ability.factory(), ability.description());
                updated++;
            } else {
                // Ability not pre-registered - this shouldn't happen if placeholder registration worked
                Petsplus.LOGGER.warn("Ability {} has no placeholder registration, skipping (add the JSON file before mod loads)", ability.id());
                skipped++;
            }
        }

        if (!definitions.isEmpty()) {
            Petsplus.LOGGER.info("Loaded {} ability definitions ({} updated, {} skipped)",
                definitions.size(), updated, skipped);
        } else {
            Petsplus.LOGGER.warn("No ability definitions were loaded from datapacks; using existing registry entries.");
        }

        AbilityManager.reloadFromRegistry();
    }

    private static Identifier parseAbilityId(JsonObject json, String source) {
        String idValue = RegistryJsonHelper.getString(json, "id", null);
        if (idValue == null) {
            Petsplus.LOGGER.error("Ability definition at {} is missing required field 'id'", source);
            return null;
        }

        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            Petsplus.LOGGER.error("Ability definition at {} has invalid id '{}'", source, idValue);
            return null;
        }
        return id;
    }

    private static Ability duplicateAbility(Ability template) {
        JsonObject config = template.getConfig();
        JsonObject configCopy = config == null ? null : config.deepCopy();
        return new Ability(template.getId(), template.getTrigger(), List.copyOf(template.getEffects()), configCopy);
    }

    private record LoadedAbility(Identifier id, String description, Supplier<Ability> factory) {
    }
}
