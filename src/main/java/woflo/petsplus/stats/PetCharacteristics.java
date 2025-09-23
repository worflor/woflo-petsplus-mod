package woflo.petsplus.stats;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.Random;
import java.util.UUID;

/**
 * Handles unique pet characteristics that make each pet individual.
 * Each pet gets randomized stat modifiers based on their UUID and tame time.
 */
public class PetCharacteristics {
    
    // Base modifier ranges (as percentages)
    private static final float MIN_MODIFIER = -0.15f; // -15%
    private static final float MAX_MODIFIER = 0.15f;  // +15%
    
    // Individual characteristic modifiers
    private final float healthModifier;
    private final float speedModifier;
    private final float attackModifier;
    private final float defenseModifier;
    private final float agilityModifier;   // Affects jump height, knockback resistance
    private final float vitalityModifier;  // Affects regeneration, status effect resistance
    
    // Calculated seed for this pet's characteristics
    private final long characteristicSeed;
    
    private PetCharacteristics(float health, float speed, float attack, float defense, float agility, float vitality, long seed) {
        this.healthModifier = health;
        this.speedModifier = speed;
        this.attackModifier = attack;
        this.defenseModifier = defense;
        this.agilityModifier = agility;
        this.vitalityModifier = vitality;
        this.characteristicSeed = seed;
    }
    
    /**
     * Generate characteristics for a newly tamed pet.
     */
    public static PetCharacteristics generateForNewPet(MobEntity pet, long tameTime) {
        UUID petUuid = pet.getUuid();
        long seed = petUuid.getMostSignificantBits() ^ petUuid.getLeastSignificantBits() ^ tameTime;
        Random random = new Random(seed);
        
        return new PetCharacteristics(
            generateModifier(random),  // health
            generateModifier(random),  // speed
            generateModifier(random),  // attack
            generateModifier(random),  // defense
            generateModifier(random),  // agility
            generateModifier(random),  // vitality
            seed
        );
    }
    
    /**
     * Generate a balanced random modifier.
     * Uses a slightly bell-curved distribution to favor moderate values.
     */
    private static float generateModifier(Random random) {
        // Generate two random values and average them for a more bell-curved distribution
        float val1 = MIN_MODIFIER + (MAX_MODIFIER - MIN_MODIFIER) * random.nextFloat();
        float val2 = MIN_MODIFIER + (MAX_MODIFIER - MIN_MODIFIER) * random.nextFloat();
        return (val1 + val2) / 2.0f;
    }
    
    /**
     * Get the health modifier with role affinity applied.
     */
    public float getHealthModifier(@Nullable PetRoleType roleType) {
        float base = healthModifier;
        return base + getRoleAffinity(roleType, StatType.HEALTH);
    }

    /**
     * Get the speed modifier with role affinity applied.
     */
    public float getSpeedModifier(@Nullable PetRoleType roleType) {
        float base = speedModifier;
        return base + getRoleAffinity(roleType, StatType.SPEED);
    }

    /**
     * Get the attack modifier with role affinity applied.
     */
    public float getAttackModifier(@Nullable PetRoleType roleType) {
        float base = attackModifier;
        return base + getRoleAffinity(roleType, StatType.ATTACK);
    }

    /**
     * Get the defense modifier with role affinity applied.
     */
    public float getDefenseModifier(@Nullable PetRoleType roleType) {
        float base = defenseModifier;
        return base + getRoleAffinity(roleType, StatType.DEFENSE);
    }

    /**
     * Get the agility modifier with role affinity applied.
     */
    public float getAgilityModifier(@Nullable PetRoleType roleType) {
        float base = agilityModifier;
        return base + getRoleAffinity(roleType, StatType.AGILITY);
    }

    /**
     * Get the vitality modifier with role affinity applied.
     */
    public float getVitalityModifier(@Nullable PetRoleType roleType) {
        float base = vitalityModifier;
        return base + getRoleAffinity(roleType, StatType.VITALITY);
    }

    /**
     * Get the XP learning modifier - how well this pet learns from experience.
     * Combines agility (quick learner) and vitality (focus/retention) with slight randomness.
     * Range: 0.85 to 1.15 (±15% XP gain variation)
     */
    public float getXpLearningModifier(@Nullable PetRoleType roleType) {
        // Base learning rate influenced by agility (quick thinking) and vitality (focus)
        float agility = getAgilityModifier(roleType);
        float vitality = getVitalityModifier(roleType);

        // Combine agility and vitality for learning potential
        // Agility = quick to pick up concepts, Vitality = retention and focus
        float learningBase = (agility * 0.6f + vitality * 0.4f);

        // Add role-specific learning bonuses
        float roleBonus = getRoleAffinity(roleType, StatType.LEARNING);

        // Convert to multiplier: -15% to +15% becomes 0.85 to 1.15
        return 1.0f + (learningBase + roleBonus);
    }
    
    /**
     * Get role-based stat affinity bonus.
     * Each role gets small bonuses to stats that align with their theme.
     */
    private float getRoleAffinity(@Nullable PetRoleType roleType, StatType statType) {
        if (roleType == null) {
            return 0.0f;
        }

        return roleType.statAffinities().getOrDefault(statType.key(), 0.0f);
    }

    /**
     * Get a description of this pet's notable characteristics.
     */
    public String getCharacteristicDescription(@Nullable PetRoleType roleType) {
        StringBuilder desc = new StringBuilder();

        float health = getHealthModifier(roleType);
        float speed = getSpeedModifier(roleType);
        float attack = getAttackModifier(roleType);
        float defense = getDefenseModifier(roleType);
        float agility = getAgilityModifier(roleType);
        float vitality = getVitalityModifier(roleType);
        
        // Find the strongest and weakest traits
        float maxValue = Math.max(Math.max(Math.max(health, speed), Math.max(attack, defense)), Math.max(agility, vitality));
        float minValue = Math.min(Math.min(Math.min(health, speed), Math.min(attack, defense)), Math.min(agility, vitality));
        
        // Only mention significant traits (>±8%)
        if (maxValue > 0.08f) {
            if (health == maxValue) desc.append("Hardy ");
            else if (speed == maxValue) desc.append("Swift ");
            else if (attack == maxValue) desc.append("Fierce ");
            else if (defense == maxValue) desc.append("Sturdy ");
            else if (agility == maxValue) desc.append("Nimble ");
            else if (vitality == maxValue) desc.append("Resilient ");
        }
        
        if (minValue < -0.08f) {
            if (desc.length() > 0) desc.append("but ");
            if (health == minValue) desc.append("Frail");
            else if (speed == minValue) desc.append("Slow");
            else if (attack == minValue) desc.append("Gentle");
            else if (defense == minValue) desc.append("Fragile");
            else if (agility == minValue) desc.append("Clumsy");
            else if (vitality == minValue) desc.append("Sensitive");
        }
        
        if (desc.length() == 0) {
            desc.append("Balanced");
        }
        
        return desc.toString().trim();
    }
    
    /**
     * Save characteristics to NBT.
     */
    public void writeToNbt(NbtCompound nbt) {
        nbt.putFloat("healthMod", healthModifier);
        nbt.putFloat("speedMod", speedModifier);
        nbt.putFloat("attackMod", attackModifier);
        nbt.putFloat("defenseMod", defenseModifier);
        nbt.putFloat("agilityMod", agilityModifier);
        nbt.putFloat("vitalityMod", vitalityModifier);
        nbt.putLong("charSeed", characteristicSeed);
    }
    
    /**
     * Load characteristics from NBT.
     */
    public static PetCharacteristics readFromNbt(NbtCompound nbt) {
        if (!nbt.contains("healthMod")) {
            return null; // No characteristics saved
        }
        
        float[] values = new float[6];
        long[] seedContainer = new long[1];
        
        nbt.getFloat("healthMod").ifPresent(value -> values[0] = value);
        nbt.getFloat("speedMod").ifPresent(value -> values[1] = value);
        nbt.getFloat("attackMod").ifPresent(value -> values[2] = value);
        nbt.getFloat("defenseMod").ifPresent(value -> values[3] = value);
        nbt.getFloat("agilityMod").ifPresent(value -> values[4] = value);
        nbt.getFloat("vitalityMod").ifPresent(value -> values[5] = value);
        nbt.getLong("charSeed").ifPresent(value -> seedContainer[0] = value);
        
        return new PetCharacteristics(
            values[0], values[1], values[2], values[3], values[4], values[5], seedContainer[0]
        );
    }
    
    /**
     * Get the seed used to generate these characteristics.
     */
    public long getCharacteristicSeed() {
        return characteristicSeed;
    }
    
    /**
     * Enum for different stat types.
     */
    private enum StatType {
        HEALTH("health"),
        SPEED("speed"),
        ATTACK("attack"),
        DEFENSE("defense"),
        AGILITY("agility"),
        VITALITY("vitality"),
        LEARNING("learning");

        private final String key;

        StatType(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}