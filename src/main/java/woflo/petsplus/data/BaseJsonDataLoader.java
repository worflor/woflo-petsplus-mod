package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for JSON data loaders that follow the same reload pattern.
 * Handles resource loading, JSON parsing, and error handling.
 * 
 * @param <T> The type of data being loaded
 */
public abstract class BaseJsonDataLoader<T> implements SimpleSynchronousResourceReloadListener {
    private final String rootPath;
    private final Identifier fabricId;

    protected BaseJsonDataLoader(String rootPath, String fabricIdPath) {
        this.rootPath = rootPath;
        this.fabricId = Identifier.of(Petsplus.MOD_ID, fabricIdPath);
    }

    @Override
    public final Identifier getFabricId() {
        return fabricId;
    }

    @Override
    public final void reload(ResourceManager manager) {
        Map<Identifier, JsonElement> prepared = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = manager.findResources(rootPath, id -> id.getPath().endsWith(".json"));
        
        for (Identifier resourceId : resources.keySet()) {
            Optional<Resource> resource = manager.getResource(resourceId);
            if (resource.isEmpty()) {
                Petsplus.LOGGER.warn("No primary resource found for {} definition {}", getResourceTypeName(), resourceId);
                continue;
            }

            try (Reader reader = resource.get().getReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                prepared.put(toResourceId(resourceId), json);
            } catch (IOException | JsonParseException e) {
                Petsplus.LOGGER.error("Failed to parse {} data from {}", getResourceTypeName(), resourceId, e);
            }
        }

        apply(prepared, manager);
    }

    /**
     * Convert a resource file identifier to the actual resource identifier.
     * Strips the root path and .json extension.
     */
    protected final Identifier toResourceId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (path.startsWith(rootPath + "/")) {
            path = path.substring(rootPath.length() + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return Identifier.of(resourceId.getNamespace(), path);
    }

    /**
     * Describe the source location for logging purposes.
     */
    protected final String describeSource(Identifier fileId) {
        return fileId.getNamespace() + ":" + rootPath + "/" + fileId.getPath() + ".json";
    }

    /**
     * Get the human-readable name of the resource type for logging.
     */
    protected abstract String getResourceTypeName();

    /**
     * Apply the prepared JSON data to the registry/system.
     * 
     * @param prepared Map of resource IDs to their JSON data
     * @param manager The resource manager
     */
    protected abstract void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager);
}
