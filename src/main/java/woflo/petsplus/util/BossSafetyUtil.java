package woflo.petsplus.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.Set;
import java.util.HashSet;

/**
 * Utility for validating boss safety and CC resistance.
 */
public class BossSafetyUtil {
    private static final Set<Identifier> CC_RESISTANT_ENTITIES = new HashSet<>();
    
    static {
        // Initialize CC-resistant entities based on our generated tag
        CC_RESISTANT_ENTITIES.add(Identifier.of("minecraft:warden"));
        CC_RESISTANT_ENTITIES.add(Identifier.of("minecraft:ender_dragon"));
        CC_RESISTANT_ENTITIES.add(Identifier.of("minecraft:wither"));
        CC_RESISTANT_ENTITIES.add(Identifier.of("minecraft:elder_guardian"));
    }
    
    /**
     * Check if an entity is considered a boss (high health or special mob).
     */
    public static boolean isBossEntity(LivingEntity entity) {
        // Check health threshold
        if (entity.getMaxHealth() >= 100.0f) {
            return true;
        }
        
        // Check entity type against known bosses
        Identifier entityType = Registries.ENTITY_TYPE.getId(entity.getType());
        return CC_RESISTANT_ENTITIES.contains(entityType);
    }
    
    /**
     * Check if an entity is resistant to crowd control effects.
     */
    public static boolean isCCResistant(LivingEntity entity) {
        Identifier entityType = Registries.ENTITY_TYPE.getId(entity.getType());
        return CC_RESISTANT_ENTITIES.contains(entityType);
    }
    
    /**
     * Validate that a CC effect should be applied to an entity.
     * Returns true if safe to apply, false if should be blocked.
     */
    public static boolean isSafeToApplyCC(LivingEntity entity, String effectType) {
        if (isCCResistant(entity)) {
            Petsplus.LOGGER.debug("Blocking CC effect {} on resistant entity {}", 
                effectType, Registries.ENTITY_TYPE.getId(entity.getType()));
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the number of boss entities near a location.
     */
    public static int countNearbyBosses(LivingEntity center, double radius) {
        return (int) center.getWorld().getOtherEntities(center, 
            center.getBoundingBox().expand(radius), 
            entity -> entity instanceof LivingEntity living && isBossEntity(living))
            .size();
    }
    
    /**
     * Check if any boss entities are nearby.
     */
    public static boolean hasBossesNearby(LivingEntity center, double radius) {
        return countNearbyBosses(center, radius) > 0;
    }
    
    /**
     * Add a custom entity type to the CC resistance list.
     */
    public static void addCCResistantEntity(Identifier entityType) {
        CC_RESISTANT_ENTITIES.add(entityType);
        Petsplus.LOGGER.info("Added CC-resistant entity: {}", entityType);
    }
    
    /**
     * Remove an entity type from the CC resistance list.
     */
    public static void removeCCResistantEntity(Identifier entityType) {
        CC_RESISTANT_ENTITIES.remove(entityType);
        Petsplus.LOGGER.info("Removed CC-resistant entity: {}", entityType);
    }
    
    /**
     * Get all CC-resistant entity types for debugging.
     */
    public static Set<Identifier> getCCResistantEntities() {
        return new HashSet<>(CC_RESISTANT_ENTITIES);
    }
}