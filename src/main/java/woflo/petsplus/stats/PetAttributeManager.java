package woflo.petsplus.stats;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

/**
 * Manages attribute modifiers for pets based on level and characteristics.
 * Implements a balanced progression system that rewards grinding while maintaining balance.
 */
public class PetAttributeManager {
    
    // Unique identifiers for our modifiers
    private static final Identifier LEVEL_HEALTH_ID = Identifier.of("petsplus", "level_health");
    private static final Identifier LEVEL_SPEED_ID = Identifier.of("petsplus", "level_speed");
    private static final Identifier LEVEL_ATTACK_ID = Identifier.of("petsplus", "level_attack");
    
    private static final Identifier CHAR_HEALTH_ID = Identifier.of("petsplus", "char_health");
    private static final Identifier CHAR_SPEED_ID = Identifier.of("petsplus", "char_speed");
    private static final Identifier CHAR_ATTACK_ID = Identifier.of("petsplus", "char_attack");
    
    /**
     * Apply all attribute modifiers to a pet based on level and characteristics.
     * Uses a balanced progression system that scales meaningfully but not overpowered.
     */
    public static void applyAttributeModifiers(MobEntity pet, PetComponent petComponent) {
        removeAttributeModifiers(pet); // Clean slate
        
        int level = petComponent.getLevel();
        PetRole role = petComponent.getRole();
        PetCharacteristics characteristics = petComponent.getCharacteristics();
        
        // Apply level-based modifiers (core progression)
        applyLevelModifiers(pet, level, role);
        
        // Apply characteristic-based modifiers (uniqueness)
        if (characteristics != null) {
            applyCharacteristicModifiers(pet, characteristics, role);
        }
        
        // Ensure pet is at full health after modifiers
        pet.setHealth(pet.getMaxHealth());
    }
    
    /**
     * Remove all pet-related attribute modifiers.
     */
    public static void removeAttributeModifiers(MobEntity pet) {
        // Remove level-based modifiers
        if (pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(LEVEL_HEALTH_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(LEVEL_SPEED_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).removeModifier(LEVEL_ATTACK_ID);
        }
        
        // Remove characteristic-based modifiers
        if (pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(CHAR_HEALTH_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(CHAR_SPEED_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).removeModifier(CHAR_ATTACK_ID);
        }
    }
    
    /**
     * Apply level-based modifiers - the core progression system.
     * Uses a balanced algorithm that provides meaningful but not overpowered growth.
     */
    private static void applyLevelModifiers(MobEntity pet, int level, PetRole role) {
        // Health scaling: +5% per level, with role bonus
        float healthMultiplier = calculateHealthMultiplier(level, role);
        if (healthMultiplier > 0 && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier healthMod = new EntityAttributeModifier(
                LEVEL_HEALTH_ID, 
                healthMultiplier, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(healthMod);
        }
        
        // Speed scaling: +2% per level, capped at +60%
        float speedMultiplier = calculateSpeedMultiplier(level, role);
        if (speedMultiplier > 0 && pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            EntityAttributeModifier speedMod = new EntityAttributeModifier(
                LEVEL_SPEED_ID, 
                speedMultiplier, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addPersistentModifier(speedMod);
        }
        
        // Attack scaling: +3% per level for entities that can attack
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            float attackMultiplier = calculateAttackMultiplier(level, role);
            if (attackMultiplier > 0) {
                EntityAttributeModifier attackMod = new EntityAttributeModifier(
                    LEVEL_ATTACK_ID, 
                    attackMultiplier, 
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).addPersistentModifier(attackMod);
            }
        }
    }
    
    /**
     * Apply characteristic-based modifiers - the uniqueness system.
     * Provides individual variance that makes each pet special.
     */
    private static void applyCharacteristicModifiers(MobEntity pet, PetCharacteristics characteristics, PetRole role) {
        // Health characteristic modifier
        float healthCharMod = characteristics.getVitalityModifier(role) * 0.15f; // ±15% max
        if (Math.abs(healthCharMod) > 0.01f && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHAR_HEALTH_ID, 
                healthCharMod, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(modifier);
        }
        
        // Speed characteristic modifier
        float speedCharMod = characteristics.getAgilityModifier(role) * 0.12f; // ±12% max
        if (Math.abs(speedCharMod) > 0.01f && pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHAR_SPEED_ID, 
                speedCharMod, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addPersistentModifier(modifier);
        }
        
        // Attack characteristic modifier (only for entities that can attack)
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            float attackCharMod = characteristics.getAttackModifier(role) * 0.10f; // ±10% max
            if (Math.abs(attackCharMod) > 0.01f) {
                EntityAttributeModifier modifier = new EntityAttributeModifier(
                    CHAR_ATTACK_ID, 
                    attackCharMod, 
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).addPersistentModifier(modifier);
            }
        }
    }
    
    /**
     * Calculate health multiplier based on level and role.
     * Guardian pets get extra health scaling to emphasize their tanky nature.
     */
    private static float calculateHealthMultiplier(int level, PetRole role) {
        // Base: +5% per level
        float baseMultiplier = level * 0.05f;
        
        // Role bonus for Guardian (extra +2% per level)
        if (role == PetRole.GUARDIAN) {
            baseMultiplier += level * 0.02f;
        }
        
        // Soft cap at level 20 to prevent exponential growth
        if (level > 20) {
            float excessLevels = level - 20;
            baseMultiplier = (20 * 0.05f) + (excessLevels * 0.02f); // Reduced gains after 20
            if (role == PetRole.GUARDIAN) {
                baseMultiplier += (20 * 0.02f) + (excessLevels * 0.01f);
            }
        }
        
        return Math.min(baseMultiplier, 2.0f); // Cap at +200% health
    }
    
    /**
     * Calculate speed multiplier based on level and role.
     * Scout and Skyrider pets get extra speed scaling.
     */
    private static float calculateSpeedMultiplier(int level, PetRole role) {
        // Base: +2% per level
        float baseMultiplier = level * 0.02f;
        
        // Role bonus for Scout and Skyrider (+1% per level)
        if (role == PetRole.SCOUT || role == PetRole.SKYRIDER) {
            baseMultiplier += level * 0.01f;
        }
        
        return Math.min(baseMultiplier, 0.6f); // Cap at +60% speed
    }
    
    /**
     * Calculate attack multiplier based on level and role.
     * Striker pets get extra attack scaling.
     */
    private static float calculateAttackMultiplier(int level, PetRole role) {
        // Base: +3% per level
        float baseMultiplier = level * 0.03f;
        
        // Role bonus for Striker (+2% per level)
        if (role == PetRole.STRIKER) {
            baseMultiplier += level * 0.02f;
        }
        
        // Soft cap at level 15 to prevent overpowered damage
        if (level > 15) {
            float excessLevels = level - 15;
            baseMultiplier = (15 * 0.03f) + (excessLevels * 0.015f); // Half gains after 15
            if (role == PetRole.STRIKER) {
                baseMultiplier += (15 * 0.02f) + (excessLevels * 0.01f);
            }
        }
        
        return Math.min(baseMultiplier, 1.5f); // Cap at +150% attack
    }
    
    /**
     * Get the effective scalar value for a role, modified by characteristics.
     * This drives the ability system and role-specific bonuses.
     */
    public static float getEffectiveScalar(String scalarType, PetRole role, PetCharacteristics characteristics, int level) {
        if (characteristics == null) {
            return getBaseScalar(scalarType, role, level);
        }
        
        float baseScalar = getBaseScalar(scalarType, role, level);
        float characteristicBonus = getCharacteristicScalarBonus(scalarType, characteristics, role);
        
        return baseScalar + characteristicBonus;
    }
    
    /**
     * Get base scalar value without characteristic modifiers.
     * Uses a balanced progression that rewards grinding without being overpowered.
     */
    private static float getBaseScalar(String scalarType, PetRole role, int level) {
        // Base scalar increases by 1% per odd level (levels 1, 3, 5, 7, etc.)
        // This provides steady progression while keeping early game balanced
        int oddLevels = (level + 1) / 2; // Number of odd levels reached
        float baseIncrease = (oddLevels - 1) * 0.01f; // -1 because level 1 is the starting point
        
        // Role-specific base bonuses (2% bonus for primary scalar)
        float roleBonus = getRoleScalarBonus(scalarType, role);
        
        // Milestone bonuses every 10 levels
        float milestoneBonus = getMilestoneScalarBonus(level);
        
        return baseIncrease + roleBonus + milestoneBonus;
    }
    
    /**
     * Get characteristic-based scalar bonus.
     * Provides uniqueness while maintaining balance.
     */
    private static float getCharacteristicScalarBonus(String scalarType, PetCharacteristics characteristics, PetRole role) {
        float characteristicBonus = 0.0f;
        
        // Apply characteristic bonuses to role scalars (scaled down for balance)
        switch (scalarType) {
            case "offense":
                characteristicBonus = characteristics.getAttackModifier(role) * 0.05f; // Max ±5%
                break;
            case "defense":
                characteristicBonus = characteristics.getDefenseModifier(role) * 0.05f;
                break;
            case "aura":
                characteristicBonus = characteristics.getVitalityModifier(role) * 0.05f;
                break;
            case "mobility":
                characteristicBonus = characteristics.getAgilityModifier(role) * 0.05f;
                break;
            case "disruption":
                // Combination stat for Eclipsed role
                characteristicBonus = (characteristics.getAgilityModifier(role) + characteristics.getAttackModifier(role)) * 0.025f;
                break;
            case "echo":
                characteristicBonus = characteristics.getVitalityModifier(role) * 0.05f;
                break;
            case "curse":
                // Combination stat for Cursed One role
                characteristicBonus = (characteristics.getAttackModifier(role) + characteristics.getDefenseModifier(role)) * 0.025f;
                break;
            case "slumber":
                characteristicBonus = characteristics.getVitalityModifier(role) * 0.05f;
                break;
        }
        
        return characteristicBonus;
    }
    
    /**
     * Get role-specific scalar bonuses.
     * Each role excels in their primary scalar.
     */
    private static float getRoleScalarBonus(String scalarType, PetRole role) {
        if (role == null) return 0.0f;
        
        return switch (role) {
            case GUARDIAN -> "defense".equals(scalarType) ? 0.02f : 0.0f;
            case STRIKER -> "offense".equals(scalarType) ? 0.02f : 0.0f;
            case SUPPORT -> "aura".equals(scalarType) ? 0.02f : 0.0f;
            case SCOUT -> "mobility".equals(scalarType) ? 0.02f : 0.0f;
            case SKYRIDER -> "mobility".equals(scalarType) ? 0.02f : 0.0f;
            case ENCHANTMENT_BOUND -> "echo".equals(scalarType) ? 0.02f : 0.0f;
            case CURSED_ONE -> "curse".equals(scalarType) ? 0.02f : 0.0f;
            case ECLIPSED -> "disruption".equals(scalarType) ? 0.02f : 0.0f;
            case EEPY_EEPER -> "slumber".equals(scalarType) ? 0.02f : 0.0f;
        };
    }
    
    /**
     * Get milestone-based scalar bonuses.
     * Rewards long-term progression with meaningful milestones.
     */
    private static float getMilestoneScalarBonus(int level) {
        if (level >= 30) return 0.03f; // +3% at max level
        if (level >= 20) return 0.02f; // +2% at level 20
        if (level >= 10) return 0.01f; // +1% at level 10
        return 0.0f;
    }
}