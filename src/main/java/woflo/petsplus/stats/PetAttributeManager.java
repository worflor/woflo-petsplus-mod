package woflo.petsplus.stats;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureModifierSampler;

import java.util.Map;
import java.util.function.Function;

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

    private static final Identifier NATURE_HEALTH_ID = Identifier.of("petsplus", "nature_health");
    private static final Identifier NATURE_VITALITY_ID = Identifier.of("petsplus", "nature_vitality");
    private static final Identifier NATURE_SPEED_ID = Identifier.of("petsplus", "nature_speed");
    private static final Identifier NATURE_SWIM_SPEED_ID = Identifier.of("petsplus", "nature_swim_speed");
    private static final Identifier NATURE_ATTACK_ID = Identifier.of("petsplus", "nature_attack");
    private static final Identifier NATURE_DEFENSE_ID = Identifier.of("petsplus", "nature_defense");
    private static final Identifier NATURE_AGILITY_ID = Identifier.of("petsplus", "nature_agility");
    private static final Identifier NATURE_FOCUS_ID = Identifier.of("petsplus", "nature_focus");
    private static final Identifier NATURE_LOYALTY_ID = Identifier.of("petsplus", "nature_loyalty");

    private static final Map<NatureModifierSampler.NatureStat, NatureAttributeBinding> NATURE_ATTRIBUTE_BINDINGS =
        createNatureAttributeBindings();
    private static final Identifier VANILLA_SWIM_SPEED_ID = Identifier.of("minecraft", "generic.swim_speed");
    private static RegistryEntry<EntityAttribute> cachedSwimSpeedAttribute;

    private static final float BASE_HEALTH_PER_LEVEL = 0.05f;
    private static final float BASE_HEALTH_POST_SOFTCAP_PER_LEVEL = 0.02f;
    private static final int BASE_HEALTH_SOFTCAP_LEVEL = 20;
    private static final float BASE_HEALTH_MAX_BONUS = 2.0f;

    private static final float BASE_SPEED_PER_LEVEL = 0.02f;
    private static final float BASE_SPEED_MAX = 0.6f;

    private static final float BASE_ATTACK_PER_LEVEL = 0.03f;
    private static final float BASE_ATTACK_POST_SOFTCAP_PER_LEVEL = 0.015f;
    private static final int BASE_ATTACK_SOFTCAP_LEVEL = 15;
    private static final float BASE_ATTACK_MAX_BONUS = 1.5f;
    
    /**
     * Apply all attribute modifiers to a pet based on level and characteristics.
     * Uses a balanced progression system that scales meaningfully but not overpowered.
     */
    public static void applyAttributeModifiers(MobEntity pet, PetComponent petComponent) {
        removeAttributeModifiers(pet); // Clean slate
        
        int level = petComponent.getLevel();
        PetRoleType roleType = petComponent.getRoleType(false);
        PetCharacteristics characteristics = petComponent.getCharacteristics();

        // Apply level-based modifiers (core progression)
        applyLevelModifiers(pet, level, roleType);

        // Apply characteristic-based modifiers (uniqueness)
        if (characteristics != null) {
            applyCharacteristicModifiers(pet, characteristics, roleType);
        }

        applyNatureModifiers(pet, petComponent);

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

        if (pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(NATURE_HEALTH_ID);
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(NATURE_VITALITY_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(NATURE_SPEED_ID);
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(NATURE_SWIM_SPEED_ID);
        }
        RegistryEntry<EntityAttribute> swimAttribute = resolveSwimSpeedAttribute();
        if (swimAttribute != null) {
            EntityAttributeInstance instance = pet.getAttributeInstance(swimAttribute);
            if (instance != null) {
                instance.removeModifier(NATURE_SWIM_SPEED_ID);
            }
        }
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).removeModifier(NATURE_ATTACK_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ARMOR) != null) {
            pet.getAttributeInstance(EntityAttributes.ARMOR).removeModifier(NATURE_DEFENSE_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE) != null) {
            pet.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).removeModifier(NATURE_AGILITY_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.FOLLOW_RANGE) != null) {
            pet.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).removeModifier(NATURE_FOCUS_ID);
            pet.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).removeModifier(NATURE_LOYALTY_ID);
        }
    }
    
    /**
     * Apply level-based modifiers - the core progression system.
     * Uses a balanced algorithm that provides meaningful but not overpowered growth.
     */
    private static void applyLevelModifiers(MobEntity pet, int level, PetRoleType roleType) {
        // Health scaling: +5% per level, with role bonus
        float healthMultiplier = calculateHealthMultiplier(level, roleType);
        if (healthMultiplier > 0 && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier healthMod = new EntityAttributeModifier(
                LEVEL_HEALTH_ID,
                healthMultiplier, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(healthMod);
        }
        
        // Speed scaling: +2% per level, capped at +60%
        float speedMultiplier = calculateSpeedMultiplier(level, roleType);
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
            float attackMultiplier = calculateAttackMultiplier(level, roleType);
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

    private static void applyNatureModifiers(MobEntity pet, PetComponent component) {
        NatureModifierSampler.NatureAdjustment adjustment = NatureModifierSampler.sample(component);
        component.setNatureEmotionTuning(adjustment.volatilityMultiplier(),
            adjustment.resilienceMultiplier(), adjustment.contagionModifier(),
            adjustment.guardModifier());
        component.setNatureEmotionProfile(adjustment.emotionProfile());
        if (adjustment.isEmpty()) {
            return;
        }

        applyNatureAdjustment(pet::getAttributeInstance, adjustment, null);
    }

    static void applyNatureAdjustment(Function<RegistryEntry<EntityAttribute>, EntityAttributeInstance> lookup,
                                      NatureModifierSampler.NatureAdjustment adjustment) {
        applyNatureAdjustment(lookup, adjustment, null);
    }

    static void applyNatureAdjustment(Function<RegistryEntry<EntityAttribute>, EntityAttributeInstance> lookup,
                                      NatureModifierSampler.NatureAdjustment adjustment,
                                      Map<NatureModifierSampler.NatureStat, RegistryEntry<EntityAttribute>> attributeOverrides) {
        for (Map.Entry<NatureModifierSampler.NatureStat, NatureAttributeBinding> entry
            : NATURE_ATTRIBUTE_BINDINGS.entrySet()) {
            float bonus = adjustment.valueFor(entry.getKey());
            if (Math.abs(bonus) <= 0.0001f) {
                continue;
            }
            RegistryEntry<EntityAttribute> attribute = resolveAttribute(entry.getKey(), attributeOverrides);
            RegistryEntry<EntityAttribute> fallbackAttribute =
                fallbackAttributeFor(entry.getKey(), attributeOverrides);
            applyNatureModifier(lookup, entry.getKey(), attribute, fallbackAttribute, entry.getValue(), bonus);
        }
    }

    /**
     * Apply characteristic-based modifiers - the uniqueness system.
     * Provides individual variance that makes each pet special.
     */
    private static void applyCharacteristicModifiers(MobEntity pet, PetCharacteristics characteristics, PetRoleType roleType) {
        // Health characteristic modifier
        float healthCharMod = characteristics.getVitalityModifier(roleType) * 0.15f; // ±15% max
        if (Math.abs(healthCharMod) > 0.01f && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHAR_HEALTH_ID, 
                healthCharMod, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(modifier);
        }
        
        // Speed characteristic modifier
        float speedCharMod = characteristics.getAgilityModifier(roleType) * 0.12f; // ±12% max
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
            float attackCharMod = characteristics.getAttackModifier(roleType) * 0.10f; // ±10% max
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

    private static void applyNatureModifier(
        Function<RegistryEntry<EntityAttribute>, EntityAttributeInstance> lookup,
        NatureModifierSampler.NatureStat stat,
        RegistryEntry<EntityAttribute> attribute,
        RegistryEntry<EntityAttribute> fallbackAttribute,
        NatureAttributeBinding binding,
        float bonus
    ) {
        if (binding == null || attribute == null) {
            if (binding == null || fallbackAttribute == null) {
                return;
            }
            attribute = fallbackAttribute;
        }

        EntityAttributeInstance instance = lookup.apply(attribute);
        if (instance == null && fallbackAttribute != null && attribute != fallbackAttribute) {
            instance = lookup.apply(fallbackAttribute);
        }
        if (instance == null && stat == NatureModifierSampler.NatureStat.SWIM_SPEED) {
            RegistryEntry<EntityAttribute> resolvedFallback = resolveSwimFallbackAttribute();
            if (resolvedFallback != null) {
                instance = lookup.apply(resolvedFallback);
            }
        }
        if (instance == null) {
            return;
        }

        if (binding.type() == NatureModifierType.MULTIPLICATIVE) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                binding.id(),
                bonus,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            instance.addPersistentModifier(modifier);
            return;
        }

        double base = instance.getBaseValue();
        double scaleBase = base != 0.0 ? base : binding.fallbackBase();
        if (scaleBase == 0.0) {
            return;
        }

        double amount = scaleBase * bonus;
        if (Math.abs(amount) <= 0.0001) {
            return;
        }

        EntityAttributeModifier modifier = new EntityAttributeModifier(
            binding.id(),
            amount,
            EntityAttributeModifier.Operation.ADD_VALUE
        );
        instance.addPersistentModifier(modifier);
    }

    private static RegistryEntry<EntityAttribute> resolveAttribute(
        NatureModifierSampler.NatureStat stat,
        Map<NatureModifierSampler.NatureStat, RegistryEntry<EntityAttribute>> attributeOverrides
    ) {
        if (attributeOverrides != null) {
            RegistryEntry<EntityAttribute> override = attributeOverrides.get(stat);
            if (override != null) {
                return override;
            }
        }

        return switch (stat) {
            case HEALTH, VITALITY -> EntityAttributes.MAX_HEALTH;
            case SPEED -> EntityAttributes.MOVEMENT_SPEED;
            case SWIM_SPEED -> resolveSwimSpeedAttribute();
            case ATTACK -> EntityAttributes.ATTACK_DAMAGE;
            case DEFENSE -> EntityAttributes.ARMOR;
            case AGILITY -> EntityAttributes.KNOCKBACK_RESISTANCE;
            case FOCUS, LOYALTY -> EntityAttributes.FOLLOW_RANGE;
            case NONE -> null;
        };
    }

    private static RegistryEntry<EntityAttribute> fallbackAttributeFor(
        NatureModifierSampler.NatureStat stat,
        Map<NatureModifierSampler.NatureStat, RegistryEntry<EntityAttribute>> attributeOverrides
    ) {
        if (stat != NatureModifierSampler.NatureStat.SWIM_SPEED) {
            return null;
        }

        if (attributeOverrides != null) {
            RegistryEntry<EntityAttribute> movementOverride = attributeOverrides.get(NatureModifierSampler.NatureStat.SPEED);
            if (movementOverride != null) {
                return movementOverride;
            }
        }

        return resolveSwimFallbackAttribute();
    }

    private static Map<NatureModifierSampler.NatureStat, NatureAttributeBinding> createNatureAttributeBindings() {
        Map<NatureModifierSampler.NatureStat, NatureAttributeBinding> map =
            new java.util.EnumMap<>(NatureModifierSampler.NatureStat.class);
        map.put(NatureModifierSampler.NatureStat.HEALTH,
            NatureAttributeBinding.multiplicative(NATURE_HEALTH_ID));
        map.put(NatureModifierSampler.NatureStat.VITALITY,
            NatureAttributeBinding.multiplicative(NATURE_VITALITY_ID));
        map.put(NatureModifierSampler.NatureStat.SPEED,
            NatureAttributeBinding.multiplicative(NATURE_SPEED_ID));
        map.put(NatureModifierSampler.NatureStat.SWIM_SPEED,
            NatureAttributeBinding.multiplicative(NATURE_SWIM_SPEED_ID));
        map.put(NatureModifierSampler.NatureStat.ATTACK,
            NatureAttributeBinding.multiplicative(NATURE_ATTACK_ID));
        map.put(NatureModifierSampler.NatureStat.DEFENSE,
            NatureAttributeBinding.scaledAdditive(NATURE_DEFENSE_ID, 8.0));
        map.put(NatureModifierSampler.NatureStat.AGILITY,
            NatureAttributeBinding.scaledAdditive(NATURE_AGILITY_ID, 1.0));
        map.put(NatureModifierSampler.NatureStat.FOCUS,
            NatureAttributeBinding.multiplicative(NATURE_FOCUS_ID));
        map.put(NatureModifierSampler.NatureStat.LOYALTY,
            NatureAttributeBinding.multiplicative(NATURE_LOYALTY_ID));
        return java.util.Collections.unmodifiableMap(map);
    }

    private static RegistryEntry<EntityAttribute> resolveSwimSpeedAttribute() {
        if (cachedSwimSpeedAttribute == null) {
            cachedSwimSpeedAttribute = Registries.ATTRIBUTE.getEntry(VANILLA_SWIM_SPEED_ID).orElse(null);
        }
        return cachedSwimSpeedAttribute;
    }

    private static RegistryEntry<EntityAttribute> resolveSwimFallbackAttribute() {
        return EntityAttributes.MOVEMENT_SPEED;
    }

    private record NatureAttributeBinding(Identifier id,
                                          NatureModifierType type,
                                          double fallbackBase) {
        static NatureAttributeBinding multiplicative(Identifier id) {
            return new NatureAttributeBinding(id, NatureModifierType.MULTIPLICATIVE, 0.0);
        }

        static NatureAttributeBinding scaledAdditive(Identifier id, double fallbackBase) {
            return new NatureAttributeBinding(id, NatureModifierType.SCALED_ADDITIVE, fallbackBase);
        }
    }

    private enum NatureModifierType {
        MULTIPLICATIVE,
        SCALED_ADDITIVE
    }

    /**
     * Calculate health multiplier based on level and role.
     * Guardian pets get extra health scaling to emphasize their tanky nature.
     */
    private static float calculateHealthMultiplier(int level, PetRoleType roleType) {
        float baseMultiplier = level * BASE_HEALTH_PER_LEVEL;
        if (level > BASE_HEALTH_SOFTCAP_LEVEL) {
            float excessLevels = level - BASE_HEALTH_SOFTCAP_LEVEL;
            baseMultiplier = (BASE_HEALTH_SOFTCAP_LEVEL * BASE_HEALTH_PER_LEVEL)
                + (excessLevels * BASE_HEALTH_POST_SOFTCAP_PER_LEVEL);
        }

        PetRoleType.AttributeScaling scaling = roleType != null
            ? roleType.attributeScaling()
            : PetRoleType.AttributeScaling.DEFAULT;

        int roleSoftcap = Math.max(0, scaling.healthSoftcapLevel());
        float bonusMultiplier;
        if (level > roleSoftcap) {
            float cappedLevels = roleSoftcap;
            float excessLevels = level - roleSoftcap;
            bonusMultiplier = (cappedLevels * scaling.healthBonusPerLevel())
                + (excessLevels * scaling.healthPostSoftcapBonusPerLevel());
        } else {
            bonusMultiplier = level * scaling.healthBonusPerLevel();
        }

        float total = baseMultiplier + bonusMultiplier;
        float cap = scaling.healthMaxBonus() > 0 ? scaling.healthMaxBonus() : BASE_HEALTH_MAX_BONUS;
        return Math.min(total, cap);
    }

    /**
     * Calculate speed multiplier based on level and role.
     * Scout and Skyrider pets get extra speed scaling.
     */
    private static float calculateSpeedMultiplier(int level, PetRoleType roleType) {
        float baseMultiplier = level * BASE_SPEED_PER_LEVEL;
        PetRoleType.AttributeScaling scaling = roleType != null
            ? roleType.attributeScaling()
            : PetRoleType.AttributeScaling.DEFAULT;

        float bonusMultiplier = level * scaling.speedBonusPerLevel();
        float cap = scaling.speedMaxBonus() > 0 ? scaling.speedMaxBonus() : BASE_SPEED_MAX;
        return Math.min(baseMultiplier + bonusMultiplier, cap);
    }

    /**
     * Calculate attack multiplier based on level and role.
     * Striker pets get extra attack scaling.
     */
    private static float calculateAttackMultiplier(int level, PetRoleType roleType) {
        float baseMultiplier;
        if (level > BASE_ATTACK_SOFTCAP_LEVEL) {
            float excessLevels = level - BASE_ATTACK_SOFTCAP_LEVEL;
            baseMultiplier = (BASE_ATTACK_SOFTCAP_LEVEL * BASE_ATTACK_PER_LEVEL)
                + (excessLevels * BASE_ATTACK_POST_SOFTCAP_PER_LEVEL);
        } else {
            baseMultiplier = level * BASE_ATTACK_PER_LEVEL;
        }

        PetRoleType.AttributeScaling scaling = roleType != null
            ? roleType.attributeScaling()
            : PetRoleType.AttributeScaling.DEFAULT;
        int roleSoftcap = Math.max(0, scaling.attackSoftcapLevel());
        float bonusMultiplier;
        if (level > roleSoftcap) {
            float baseLevels = roleSoftcap;
            float excessLevels = level - roleSoftcap;
            bonusMultiplier = (baseLevels * scaling.attackBonusPerLevel())
                + (excessLevels * scaling.attackPostSoftcapBonusPerLevel());
        } else {
            bonusMultiplier = level * scaling.attackBonusPerLevel();
        }

        float cap = scaling.attackMaxBonus() > 0 ? scaling.attackMaxBonus() : BASE_ATTACK_MAX_BONUS;
        return Math.min(baseMultiplier + bonusMultiplier, cap);
    }
    
    /**
     * Get the effective scalar value for a role, modified by characteristics.
     * This drives the ability system and role-specific bonuses.
     */
    public static float getEffectiveScalar(String scalarType, PetRoleType roleType, PetCharacteristics characteristics, int level) {
        float baseScalar = getBaseScalar(scalarType, roleType, level);
        if (characteristics == null) {
            return baseScalar;
        }

        float characteristicBonus = getCharacteristicScalarBonus(scalarType, characteristics, roleType);

        return baseScalar + characteristicBonus;
    }
    
    /**
     * Get base scalar value without characteristic modifiers.
     * Uses a balanced progression that rewards grinding without being overpowered.
     */
    private static float getBaseScalar(String scalarType, PetRoleType roleType, int level) {
        // Base scalar increases by 1% per odd level (levels 1, 3, 5, 7, etc.)
        // This provides steady progression while keeping early game balanced
        int oddLevels = (level + 1) / 2; // Number of odd levels reached
        float baseIncrease = (oddLevels - 1) * 0.01f; // -1 because level 1 is the starting point

        // Role-specific base bonuses (2% bonus for primary scalar)
        float roleBonus = getRoleScalarBonus(scalarType, roleType);

        // Milestone bonuses every 10 levels
        float milestoneBonus = getMilestoneScalarBonus(level);

        return baseIncrease + roleBonus + milestoneBonus;
    }
    
    /**
     * Get characteristic-based scalar bonus.
     * Provides uniqueness while maintaining balance.
     */
    private static float getCharacteristicScalarBonus(String scalarType, PetCharacteristics characteristics, PetRoleType roleType) {
        float characteristicBonus = 0.0f;

        // Apply characteristic bonuses to role scalars (scaled down for balance)
        switch (scalarType) {
            case "offense":
                characteristicBonus = characteristics.getAttackModifier(roleType) * 0.05f; // Max ±5%
                break;
            case "defense":
                characteristicBonus = characteristics.getDefenseModifier(roleType) * 0.05f;
                break;
            case "aura":
                characteristicBonus = characteristics.getVitalityModifier(roleType) * 0.05f;
                break;
            case "mobility":
                characteristicBonus = characteristics.getAgilityModifier(roleType) * 0.05f;
                break;
            case "disruption":
                // Combination stat for Eclipsed role
                characteristicBonus = (characteristics.getAgilityModifier(roleType) + characteristics.getAttackModifier(roleType)) * 0.025f;
                break;
            case "echo":
                characteristicBonus = characteristics.getVitalityModifier(roleType) * 0.05f;
                break;
            case "curse":
                // Combination stat for Cursed One role
                characteristicBonus = (characteristics.getAttackModifier(roleType) + characteristics.getDefenseModifier(roleType)) * 0.025f;
                break;
            case "slumber":
                characteristicBonus = characteristics.getVitalityModifier(roleType) * 0.05f;
                break;
        }

        return characteristicBonus;
    }
    
    /**
     * Get role-specific scalar bonuses.
     * Each role excels in their primary scalar.
     */
    private static float getRoleScalarBonus(String scalarType, PetRoleType roleType) {
        if (roleType == null) return 0.0f;

        return roleType.baseStatScalars().getOrDefault(scalarType, 0.0f);
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