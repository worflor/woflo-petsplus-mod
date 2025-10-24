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
    
    // Permanent stat boost modifiers from level rewards
    private static final Identifier BOOST_HEALTH_ID = Identifier.of("petsplus", "boost_health");
    private static final Identifier BOOST_SPEED_ID = Identifier.of("petsplus", "boost_speed");
    private static final Identifier BOOST_ATTACK_ID = Identifier.of("petsplus", "boost_attack");
    private static final Identifier BOOST_DEFENSE_ID = Identifier.of("petsplus", "boost_defense");

    private static final Identifier NATURE_VITALITY_ID = Identifier.of("petsplus", "nature_vitality");
    private static final Identifier NATURE_SWIFTNESS_ID = Identifier.of("petsplus", "nature_swiftness");
    private static final Identifier NATURE_MIGHT_ID = Identifier.of("petsplus", "nature_might");
    private static final Identifier NATURE_GUARD_ID = Identifier.of("petsplus", "nature_guard");
    private static final Identifier NATURE_FOCUS_ID = Identifier.of("petsplus", "nature_focus");
    private static final Identifier NATURE_AGILITY_ID = Identifier.of("petsplus", "nature_agility");

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
        PetImprint imprint = petComponent.getImprint();

        // Apply level-based modifiers (core progression)
        applyLevelModifiers(pet, level, roleType);

        // Apply imprint-based modifiers (uniqueness)
        if (imprint != null) {
            applyImprintModifiers(pet, imprint, roleType);
        }
        
        // Apply permanent stat boosts from level rewards
        applyPermanentStatBoosts(pet, petComponent);

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

        // Remove permanent stat boost modifiers
        if (pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(BOOST_HEALTH_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(BOOST_SPEED_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).removeModifier(BOOST_ATTACK_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ARMOR) != null) {
            pet.getAttributeInstance(EntityAttributes.ARMOR).removeModifier(BOOST_DEFENSE_ID);
        }

        // Remove nature modifiers
        if (pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(NATURE_VITALITY_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(NATURE_SWIFTNESS_ID);
        }
        RegistryEntry<EntityAttribute> swimAttribute = resolveSwimSpeedAttribute();
        if (swimAttribute != null) {
            EntityAttributeInstance instance = pet.getAttributeInstance(swimAttribute);
            if (instance != null) {
                instance.removeModifier(NATURE_SWIFTNESS_ID);
            }
        }
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).removeModifier(NATURE_MIGHT_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.ARMOR) != null) {
            pet.getAttributeInstance(EntityAttributes.ARMOR).removeModifier(NATURE_GUARD_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE) != null) {
            pet.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).removeModifier(NATURE_AGILITY_ID);
        }
        if (pet.getAttributeInstance(EntityAttributes.FOLLOW_RANGE) != null) {
            pet.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).removeModifier(NATURE_FOCUS_ID);
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
     * Apply imprint-based modifiers - the uniqueness system.
     * Provides individual variance that makes each pet special.
     * 
     * NOTE: Imprint multipliers are already in multiplicative format (0.88-1.12),
     * but we need to convert them to additive format for Minecraft's attribute system.
     * Multiplier 1.08 â†’ additive 0.08 (+8%)
     */
    private static void applyImprintModifiers(MobEntity pet, PetImprint imprint, PetRoleType roleType) {
        // Health imprint modifier
        float healthMult = imprint.getVitalityMultiplier();
        float healthMod = healthMult - 1.0f; // Convert 1.08x â†’ +0.08 (8%)
        if (Math.abs(healthMod) > 0.01f && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHAR_HEALTH_ID, 
                healthMod, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(modifier);
        }
        
        // Speed imprint modifier
        float speedMult = imprint.getSwiftnessMultiplier();
        float speedMod = speedMult - 1.0f;
        if (Math.abs(speedMod) > 0.01f && pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHAR_SPEED_ID, 
                speedMod, 
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addPersistentModifier(modifier);
        }
        
        // Attack imprint modifier (only for entities that can attack)
        if (pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            float attackMult = imprint.getMightMultiplier();
            float attackMod = attackMult - 1.0f;
            if (Math.abs(attackMod) > 0.01f) {
                EntityAttributeModifier modifier = new EntityAttributeModifier(
                    CHAR_ATTACK_ID,
                    attackMod,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).addPersistentModifier(modifier);
            }
        }
    }
    
    /**
     * Apply permanent stat boosts from level rewards.
     * These are flat additive bonuses granted by the level reward system.
     */
    private static void applyPermanentStatBoosts(MobEntity pet, PetComponent petComponent) {
        // Health boost
        float healthBoost = petComponent.getPermanentStatBoost("health");
        if (healthBoost > 0 && pet.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                BOOST_HEALTH_ID,
                healthBoost,
                EntityAttributeModifier.Operation.ADD_VALUE
            );
            pet.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(modifier);
        }
        
        // Speed boost
        float speedBoost = petComponent.getPermanentStatBoost("speed");
        if (speedBoost > 0 && pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                BOOST_SPEED_ID,
                speedBoost,
                EntityAttributeModifier.Operation.ADD_VALUE
            );
            pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addPersistentModifier(modifier);
        }
        
        // Attack boost
        float attackBoost = petComponent.getPermanentStatBoost("attack");
        if (attackBoost > 0 && pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                BOOST_ATTACK_ID,
                attackBoost,
                EntityAttributeModifier.Operation.ADD_VALUE
            );
            pet.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).addPersistentModifier(modifier);
        }
        
        // Defense boost (armor)
        float defenseBoost = petComponent.getPermanentStatBoost("defense");
        if (defenseBoost > 0 && pet.getAttributeInstance(EntityAttributes.ARMOR) != null) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                BOOST_DEFENSE_ID,
                defenseBoost,
                EntityAttributeModifier.Operation.ADD_VALUE
            );
            pet.getAttributeInstance(EntityAttributes.ARMOR).addPersistentModifier(modifier);
        }
        
        // Note: Learning boosts are stored in PetComponent and applied during XP calculation,
        // not as entity attributes like the stats above.
    }

    private static void applyNatureModifier(
        Function<RegistryEntry<EntityAttribute>, EntityAttributeInstance> lookup,
        NatureModifierSampler.NatureStat stat,
        RegistryEntry<EntityAttribute> attribute,
        RegistryEntry<EntityAttribute> secondaryAttribute,
        NatureAttributeBinding binding,
        float bonus
    ) {
        if (binding == null) {
            return;
        }

        applyNatureModifierToAttribute(lookup, attribute != null ? attribute : secondaryAttribute, binding, bonus);
        if (secondaryAttribute != null && secondaryAttribute != attribute) {
            applyNatureModifierToAttribute(lookup, secondaryAttribute, binding, bonus);
        }
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
            case VITALITY -> EntityAttributes.MAX_HEALTH;
            case SWIFTNESS -> EntityAttributes.MOVEMENT_SPEED;
            case MIGHT -> EntityAttributes.ATTACK_DAMAGE;
            case GUARD -> EntityAttributes.ARMOR;
            case FOCUS -> EntityAttributes.FOLLOW_RANGE;
            case AGILITY -> EntityAttributes.KNOCKBACK_RESISTANCE;
            case NONE -> null;
        };
    }

    private static RegistryEntry<EntityAttribute> fallbackAttributeFor(
        NatureModifierSampler.NatureStat stat,
        Map<NatureModifierSampler.NatureStat, RegistryEntry<EntityAttribute>> attributeOverrides
    ) {
        if (stat != NatureModifierSampler.NatureStat.SWIFTNESS) {
            return null;
        }
        return resolveSwimSpeedAttribute();
    }

    private static Map<NatureModifierSampler.NatureStat, NatureAttributeBinding> createNatureAttributeBindings() {
        Map<NatureModifierSampler.NatureStat, NatureAttributeBinding> map =
            new java.util.EnumMap<>(NatureModifierSampler.NatureStat.class);
        map.put(NatureModifierSampler.NatureStat.VITALITY,
            NatureAttributeBinding.multiplicative(NATURE_VITALITY_ID));
        map.put(NatureModifierSampler.NatureStat.SWIFTNESS,
            NatureAttributeBinding.multiplicative(NATURE_SWIFTNESS_ID));
        map.put(NatureModifierSampler.NatureStat.MIGHT,
            NatureAttributeBinding.multiplicative(NATURE_MIGHT_ID));
        map.put(NatureModifierSampler.NatureStat.GUARD,
            NatureAttributeBinding.scaledAdditive(NATURE_GUARD_ID, 8.0));
        map.put(NatureModifierSampler.NatureStat.FOCUS,
            NatureAttributeBinding.multiplicative(NATURE_FOCUS_ID));
        map.put(NatureModifierSampler.NatureStat.AGILITY,
            NatureAttributeBinding.scaledAdditive(NATURE_AGILITY_ID, 1.0));
        return java.util.Collections.unmodifiableMap(map);
    }

    private static RegistryEntry<EntityAttribute> resolveSwimSpeedAttribute() {
        if (cachedSwimSpeedAttribute == null) {
            cachedSwimSpeedAttribute = Registries.ATTRIBUTE.getEntry(VANILLA_SWIM_SPEED_ID).orElse(null);
        }
        return cachedSwimSpeedAttribute;
    }

    private static void applyNatureModifierToAttribute(
        Function<RegistryEntry<EntityAttribute>, EntityAttributeInstance> lookup,
        RegistryEntry<EntityAttribute> attribute,
        NatureAttributeBinding binding,
        float bonus
    ) {
        if (attribute == null) {
            return;
        }

        EntityAttributeInstance instance = lookup.apply(attribute);
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
     * Get the effective scalar value for a role, modified by imprint.
     * This drives the ability system and role-specific bonuses.
     */
    public static float getEffectiveScalar(String scalarType, PetRoleType roleType, PetImprint imprint, int level) {
        float baseScalar = getBaseScalar(scalarType, roleType, level);
        if (imprint == null) {
            return baseScalar;
        }

        float imprintBonus = getImprintScalarBonus(scalarType, imprint, roleType);

        return baseScalar + imprintBonus;
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
     * Get imprint-based scalar bonus.
     * Provides uniqueness while maintaining balance.
     * 
     * NOTE: Imprint multipliers are in range 0.88-1.12, so we subtract 1.0 to get additive bonus.
     * Example: 1.08x â†’ 0.08 â†’ scaled by 0.5 â†’ 0.04 (4% scalar bonus)
     */
    private static float getImprintScalarBonus(String scalarType, PetImprint imprint, PetRoleType roleType) {
        float imprintBonus = 0.0f;

        // Apply imprint bonuses to role scalars (scaled down for balance)
        switch (scalarType) {
            case "offense":
                imprintBonus = (imprint.getMightMultiplier() - 1.0f) * 0.5f; // +/-6% max
                break;
            case "defense":
                imprintBonus = (imprint.getGuardMultiplier() - 1.0f) * 0.5f;
                break;
            case "aura":
                imprintBonus = (imprint.getVitalityMultiplier() - 1.0f) * 0.5f;
                break;
            case "mobility":
                imprintBonus = (imprint.getAgilityMultiplier() - 1.0f) * 0.5f;
                break;
            case "disruption":
                // Combination stat for Eclipsed role
                float disruptAgility = (imprint.getAgilityMultiplier() - 1.0f);
                float disruptAttack = (imprint.getMightMultiplier() - 1.0f);
                imprintBonus = (disruptAgility + disruptAttack) * 0.25f;
                break;
            case "echo":
                imprintBonus = (imprint.getVitalityMultiplier() - 1.0f) * 0.5f;
                break;
            case "curse":
                // Combination stat for Cursed One role
                float curseAttack = (imprint.getMightMultiplier() - 1.0f);
                float curseDefense = (imprint.getGuardMultiplier() - 1.0f);
                imprintBonus = (curseAttack + curseDefense) * 0.25f;
                break;
            case "slumber":
                imprintBonus = (imprint.getVitalityMultiplier() - 1.0f) * 0.5f;
                break;
        }

        return imprintBonus;
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
