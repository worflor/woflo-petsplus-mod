package woflo.petsplus.ai.behaviors;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import woflo.petsplus.Petsplus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages loading and caching of behavior definitions from JSON files.
 * All behaviors are loaded from the data/petsplus/behaviors directory.
 */
public class BehaviorManager implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "behaviors");
    
    private static BehaviorManager instance;
    private final Map<Identifier, BehaviorData> behaviors = new HashMap<>();
    
    private BehaviorManager() {}
    
    public static BehaviorManager getInstance() {
        if (instance == null) {
            instance = new BehaviorManager();
        }
        return instance;
    }
    
    @Override
    public Identifier getFabricId() {
        return ID;
    }
    
    @Override
    public Collection<Identifier> getFabricDependencies() {
        return java.util.Collections.emptyList();
    }
    
    @Override
    public void load(ResourceManager manager) {
        behaviors.clear();
        
        manager.findResources("petsplus/behaviors", id -> id.getPath().endsWith(".json"))
            .forEach((id, resource) -> {
                try (InputStream stream = resource.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    
                    String jsonContent = reader.lines().reduce("", (acc, line) -> acc + line);
                    BehaviorData behavior = BehaviorData.CODEC.parse(JsonOps.INSTANCE, 
                        net.minecraft.util.JsonParser.parseString(jsonContent))
                        .getOrThrow(error -> {
                            LOGGER.error("Failed to parse behavior {}: {}", id, error);
                            return new RuntimeException("Parse error: " + error);
                        });
                    
                    // Convert file path to behavior ID
                    String behaviorPath = id.getPath().substring("petsplus/behaviors/".length());
                    behaviorPath = behaviorPath.substring(0, behaviorPath.length() - ".json".length());
                    Identifier behaviorId = Identifier.of(Petsplus.MOD_ID, behaviorPath);
                    
                    behaviors.put(behaviorId, behavior);
                    LOGGER.debug("Loaded behavior: {}", behaviorId);
                    
                } catch (IOException e) {
                    LOGGER.error("Failed to read behavior file: {}", id, e);
                } catch (Exception e) {
                    LOGGER.error("Failed to load behavior: {}", id, e);
                }
            });
        
        LOGGER.info("Loaded {} behaviors", behaviors.size());
    }
    
    @Override
    public void apply(ResourceManager manager, Profiler profiler) {
        // No additional application needed
    }
    
    /**
     * Get a behavior by ID.
     */
    public Optional<BehaviorData> getBehavior(Identifier id) {
        return Optional.ofNullable(behaviors.get(id));
    }
    
    /**
     * Get a behavior by ID, throwing if not found.
     */
    public BehaviorData requireBehavior(Identifier id) {
        BehaviorData behavior = behaviors.get(id);
        if (behavior == null) {
            throw new IllegalArgumentException("Unknown behavior: " + id);
        }
        return behavior;
    }
    
    /**
     * Get all loaded behaviors.
     */
    public Collection<BehaviorData> getAllBehaviors() {
        return behaviors.values();
    }
    
    /**
     * Get all behavior IDs.
     */
    public Collection<Identifier> getAllBehaviorIds() {
        return behaviors.keySet();
    }
    
    /**
     * Check if a behavior exists.
     */
    public boolean hasBehavior(Identifier id) {
        return behaviors.containsKey(id);
    }
    
    /**
     * Clear all behaviors (for testing purposes).
     */
    public void clear() {
        behaviors.clear();
    }
    
    /**
     * Get behaviors by goal class name.
     */
    public Collection<BehaviorData> getBehaviorsByGoalClass(String goalClass) {
        return behaviors.values().stream()
            .filter(behavior -> behavior.goalClass().equals(goalClass))
            .toList();
    }
}