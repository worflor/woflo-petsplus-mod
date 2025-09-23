package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads role definitions from datapacks so that ability loadouts, XP curves, and
 * other metadata can be customized without modifying code.
 */
public final class PetRoleDataLoader implements SimpleSynchronousResourceReloadListener {
    private static final String ROOT_PATH = "roles";
    private static final Identifier FABRIC_ID = Identifier.of(Petsplus.MOD_ID, "role_data");

    @Override
    public Identifier getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, JsonElement> prepared = new LinkedHashMap<>();
        Map<Identifier, Resource> located = manager.findResources(ROOT_PATH, id -> id.getPath().endsWith(".json"));
        for (Identifier resourceId : located.keySet()) {
            List<Resource> stack = manager.getAllResources(resourceId);
            JsonElement resolved = null;

            for (Resource resource : stack) {
                try (Reader reader = resource.getReader()) {
                    resolved = JsonParser.parseReader(reader);
                    break;
                } catch (IOException | JsonParseException e) {
                    Petsplus.LOGGER.error("Failed to parse role data from {}", resourceId, e);
                }
            }

            if (resolved != null) {
                prepared.put(toRoleId(resourceId), resolved);
            }
        }

        apply(prepared);
    }

    private void apply(Map<Identifier, JsonElement> prepared) {
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

            String existingSource = seenSources.putIfAbsent(roleId, source);
            if (existingSource != null) {
                Petsplus.LOGGER.error("Duplicate role definition {} encountered in {} (already defined in {})", roleId, source, existingSource);
                continue;
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

    private static Identifier toRoleId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (path.startsWith(ROOT_PATH + "/")) {
            path = path.substring(ROOT_PATH.length() + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return Identifier.of(resourceId.getNamespace(), path);
    }

    private static String describeSource(Identifier fileId) {
        return fileId.getNamespace() + ":" + ROOT_PATH + "/" + fileId.getPath() + ".json";
    }
}
