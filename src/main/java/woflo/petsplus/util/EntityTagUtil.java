package woflo.petsplus.util;

import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for managing entity tags used by PetsPlus abilities.
 * Uses in-memory storage for performance, tags are temporary and reset on server restart.
 */
public class EntityTagUtil {
    private static final Map<Entity, Map<String, Long>> ENTITY_TAGS = new HashMap<>();
    
    /**
     * Tag an entity with a specific key for a duration.
     */
    public static void tagEntity(Entity entity, String key, int durationTicks) {
        if (entity == null || key == null) return;
        
        long expiryTime = entity.getWorld().getTime() + durationTicks;
        ENTITY_TAGS.computeIfAbsent(entity, k -> new HashMap<>()).put(key, expiryTime);
    }
    
    /**
     * Check if an entity has a specific tag and it hasn't expired.
     */
    public static boolean hasTag(Entity entity, String key) {
        if (entity == null || key == null) return false;
        
        long currentTime = entity.getWorld().getTime();
        
        Map<String, Long> entityTags = ENTITY_TAGS.get(entity);
        if (entityTags != null) {
            Long expiryTime = entityTags.get(key);
            if (expiryTime != null) {
                if (currentTime < expiryTime) {
                    return true;
                } else {
                    // Clean up expired tag
                    entityTags.remove(key);
                    if (entityTags.isEmpty()) {
                        ENTITY_TAGS.remove(entity);
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Remove a tag from an entity.
     */
    public static void removeTag(Entity entity, String key) {
        if (entity == null || key == null) return;
        
        Map<String, Long> entityTags = ENTITY_TAGS.get(entity);
        if (entityTags != null) {
            entityTags.remove(key);
            if (entityTags.isEmpty()) {
                ENTITY_TAGS.remove(entity);
            }
        }
    }
    
    /**
     * Clean up expired tags from memory periodically.
     * This should be called from a world tick event.
     */
    public static void cleanupExpiredTags(long currentTime) {
        ENTITY_TAGS.entrySet().removeIf(entry -> {
            Map<String, Long> entityTags = entry.getValue();
            entityTags.entrySet().removeIf(tagEntry -> currentTime >= tagEntry.getValue());
            return entityTags.isEmpty();
        });
    }
    
    /**
     * Clean up all tags for an entity (e.g., when entity is removed).
     */
    public static void cleanupEntity(Entity entity) {
        ENTITY_TAGS.remove(entity);
    }
}