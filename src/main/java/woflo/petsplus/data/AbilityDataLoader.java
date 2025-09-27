package woflo.petsplus.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityFactory;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.api.registry.RegistryJsonHelper;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Loads ability definitions from datapacks and registers them into the ability registry.
 */
public class AbilityDataLoader implements SimpleSynchronousResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String ROOT_PATH = "abilities";
    private static final Identifier FABRIC_ID = Identifier.of(Petsplus.MOD_ID, "ability_data");

    @Override
    public Identifier getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, JsonElement> prepared = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = manager.findResources(ROOT_PATH, id -> id.getPath().endsWith(".json"));
        for (Identifier resourceId : resources.keySet()) {
            Optional<Resource> resource = manager.getResource(resourceId);
            if (resource.isEmpty()) {
                Petsplus.LOGGER.warn("No primary resource found for ability definition {}", resourceId);
                continue;
            }

            try (Reader reader = resource.get().getReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                prepared.put(toAbilityId(resourceId), json);
            } catch (IOException | JsonParseException e) {
                Petsplus.LOGGER.error("Failed to parse ability data from {}", resourceId, e);
            }
        }

        apply(prepared, manager);
    }

    private void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, LoadedAbility> definitions = new LinkedHashMap<>();
        Map<Identifier, String> abilitySources = new HashMap<>();
        Set<Identifier> staleCandidates = new HashSet<>(PetsPlusRegistries.abilityTypeRegistry().getIds());

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

            Identifier triggerId = resolveIdentifier(event);
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

                Identifier effectId = resolveIdentifier(typeValue);
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
            staleCandidates.remove(abilityId);
        }

        int added = 0;
        int updated = 0;
        for (LoadedAbility ability : definitions.values()) {
            AbilityType existing = PetsPlusRegistries.abilityTypeRegistry().get(ability.id());
            if (existing != null) {
                existing.update(ability.factory(), ability.description());
                updated++;
            } else {
                // Skip registration of new abilities during resource reload to avoid registry freeze crash
                // New abilities must be registered during mod initialization, not during resource reloads
                Petsplus.LOGGER.warn("Skipping registration of new ability {} during resource reload - new abilities must be registered during mod initialization", ability.id());
            }
        }

        for (Identifier staleId : staleCandidates) {
            Petsplus.LOGGER.warn("Ability {} remains registered but has no JSON definition after reload", staleId);
        }

        if (!definitions.isEmpty()) {
            Petsplus.LOGGER.info("Loaded {} ability definitions ({} updated)",
                definitions.size(), updated);
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

    private static Identifier resolveIdentifier(String value) {
        if (value.contains(":")) {
            return Identifier.of(value);
        }
        return Identifier.of(Petsplus.MOD_ID, value);
    }

    private static String describeSource(Identifier fileId) {
        return fileId.getNamespace() + ":" + ROOT_PATH + "/" + fileId.getPath() + ".json";
    }

    private static Identifier toAbilityId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (path.startsWith(ROOT_PATH + "/")) {
            path = path.substring(ROOT_PATH.length() + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return Identifier.of(resourceId.getNamespace(), path);
    }

    private static Ability duplicateAbility(Ability template) {
        JsonObject config = template.getConfig();
        JsonObject configCopy = config == null ? null : config.deepCopy();
        return new Ability(template.getId(), template.getTrigger(), List.copyOf(template.getEffects()), configCopy);
    }

    private record LoadedAbility(Identifier id, String description, Supplier<Ability> factory) {
    }
}
