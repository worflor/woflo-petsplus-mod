package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads role definitions from datapacks so that ability loadouts, XP curves, and
 * other metadata can be customized without modifying code.
 */
public final class PetRoleDataLoader extends BaseJsonDataLoader<PetRoleDefinition> {

    public PetRoleDataLoader() {
        super("roles", "role_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "role";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, PetRoleDefinition> definitions = new LinkedHashMap<>();
        Map<Identifier, String> seenSources = new LinkedHashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Role definition at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();
            PetRoleDefinition definition = PetRoleDefinition.fromJson(fileId, json, source);
            Identifier roleId = definition.id();

            String previousSource = seenSources.put(roleId, source);
            if (previousSource != null && !previousSource.equals(source)) {
                Petsplus.LOGGER.debug("Role definition {} in {} overrides {}", roleId, source, previousSource);
            }

            definitions.put(roleId, definition);
        }

        if (definitions.isEmpty()) {
            Petsplus.LOGGER.warn("No pet role definitions were loaded; existing registry entries remain unchanged.");
            return;
        }

        int added = 0;
        int updated = 0;
        Set<Identifier> registryIds = new HashSet<>(PetsPlusRegistries.petRoleTypeRegistry().getIds());

        for (PetRoleDefinition definition : definitions.values()) {
            Identifier id = definition.id();
            PetRoleType existing = PetsPlusRegistries.petRoleTypeRegistry().get(id);
            if (existing != null) {
                existing.applyDefinition(definition.toDefinition());
                updated++;
            } else {
                PetsPlusRegistries.registerPetRoleType(id, new PetRoleType(id, definition.toDefinition()));
                added++;
            }
            registryIds.remove(id);
        }

        if (!registryIds.isEmpty()) {
            for (Identifier staleId : registryIds) {
                Petsplus.LOGGER.debug("Role {} remains registered without an overriding datapack definition", staleId);
            }
        }

        Petsplus.LOGGER.info("Loaded {} pet role definitions ({} new, {} updated)", definitions.size(), added, updated);
        AbilityManager.reloadFromRegistry();
    }
}
