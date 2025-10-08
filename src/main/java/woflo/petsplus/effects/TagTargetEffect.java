package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Effect that tags a target entity with a specific key for duration.
 */
public class TagTargetEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "tag_target");
    private static final Map<Entity, Map<String, Long>> ENTITY_TAGS = new HashMap<>();
    
    private final String targetKey;
    private final String key;
    private final int durationTicks;

    public TagTargetEffect(String targetKey, String key, int durationTicks) {
        this.targetKey = targetKey == null || targetKey.isBlank() ? "target" : targetKey;
        this.key = key;
        this.durationTicks = durationTicks;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        Entity target = getTarget(context);
        if (target == null) return false;

        ServerWorld world = context.getEntityWorld();
        if (world == null) {
            if (target.getEntityWorld() instanceof ServerWorld targetWorld) {
                world = targetWorld;
            } else {
                return false;
            }
        }

        long expiryTime = world.getTime() + durationTicks;
        
        ENTITY_TAGS.computeIfAbsent(target, k -> new HashMap<>()).put(key, expiryTime);
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
    
    private Entity getTarget(EffectContext context) {
        Entity target = context.getData(targetKey, Entity.class);
        if (target != null) {
            return target;
        }

        TriggerContext triggerContext = context.getTriggerContext();
        if (triggerContext != null) {
            target = triggerContext.getData(targetKey, Entity.class);
            if (target != null) {
                return target;
            }
        }

        target = context.getTarget();
        if (target != null) {
            return target;
        }

        // Fall back to victim from trigger context when available
        return triggerContext != null ? triggerContext.getVictim() : null;
    }
    
    /**
     * Check if an entity has a specific tag.
     */
    public static boolean hasTag(Entity entity, String key) {
        Map<String, Long> tags = ENTITY_TAGS.get(entity);
        if (tags == null) return false;
        
        Long expiryTime = tags.get(key);
        if (expiryTime == null) return false;
        
        // Check if tag has expired
        if (entity.getEntityWorld().getTime() >= expiryTime) {
            tags.remove(key);
            if (tags.isEmpty()) {
                ENTITY_TAGS.remove(entity);
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Remove a tag from an entity.
     */
    public static void removeTag(Entity entity, String key) {
        Map<String, Long> tags = ENTITY_TAGS.get(entity);
        if (tags != null) {
            tags.remove(key);
            if (tags.isEmpty()) {
                ENTITY_TAGS.remove(entity);
            }
        }
    }
    
    /**
     * Clean up expired tags for all entities.
     */
    public static void cleanupExpiredTags(long currentTime) {
        ENTITY_TAGS.entrySet().removeIf(entry -> {
            Map<String, Long> tags = entry.getValue();
            tags.entrySet().removeIf(tagEntry -> currentTime >= tagEntry.getValue());
            return tags.isEmpty();
        });
    }
}

