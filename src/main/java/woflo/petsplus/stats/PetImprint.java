package woflo.petsplus.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.util.BehaviorSeedUtil;

import java.util.Random;
import java.util.UUID;

/**
 * Represents the unique "imprint" of a pet - random stat variance that makes each pet individual.
 * 
 * <p>Even two pets with the same Nature and Role will have different stats due to their imprint.
 * This creates the "this specific wolf is naturally faster/stronger" feeling.
 * 
 * <p>Imprint modifiers are multiplicative bonuses in the range 0.88x to 1.12x (±12% variance).
 * They are generated once when the pet is tamed or born and remain constant throughout its life.
 * 
 * <h2>Example:</h2>
 * <pre>
 * Wolf A: 1.08x health, 0.94x speed, 1.03x attack (fast and sturdy but clumsy)
 * Wolf B: 0.92x health, 1.09x speed, 1.02x attack (fragile but swift)
 * </pre>
 * 
 * @see StatModifierProvider
 * @see woflo.petsplus.stats.nature.NatureModifierSampler
 */
public class PetImprint implements StatModifierProvider {
    
    // Multiplier ranges: 0.88x to 1.12x (±12% variance)
    private static final float MIN_MULTIPLIER = 0.88f;
    private static final float MAX_MULTIPLIER = 1.12f;

    private static final String[] STAT_KEYS = java.util.Arrays.stream(StatType.values())
        .map(StatType::key)
        .toArray(String[]::new);

    // Individual stat multipliers
    private final float healthMultiplier;
    private final float speedMultiplier;
    private final float attackMultiplier;
    private final float defenseMultiplier;
    private final float agilityMultiplier;
    private final float vitalityMultiplier;
    
    // Calculated seed for this pet's imprint
    private final long imprintSeed;
    
    private PetImprint(float health, float speed, float attack, float defense, float agility, float vitality, long seed) {
        this.healthMultiplier = health;
        this.speedMultiplier = speed;
        this.attackMultiplier = attack;
        this.defenseMultiplier = defense;
        this.agilityMultiplier = agility;
        this.vitalityMultiplier = vitality;
        this.imprintSeed = seed;
    }
    
    /**
     * Generate imprint for a newly tamed pet.
     */
    public static PetImprint generateForNewPet(MobEntity pet, long tameTime) {
        UUID petUuid = pet.getUuid();
        long baseHash = petUuid.getMostSignificantBits() ^ petUuid.getLeastSignificantBits();
        long seed = BehaviorSeedUtil.mixBehaviorSeed(baseHash, tameTime);
        Random random = new Random(seed);
        
        return new PetImprint(
            generateMultiplier(random),  // health
            generateMultiplier(random),  // speed
            generateMultiplier(random),  // attack
            generateMultiplier(random),  // defense
            generateMultiplier(random),  // agility
            generateMultiplier(random),  // vitality
            seed
        );
    }
    
    /**
     * Generate a balanced random multiplier in range 0.88-1.12.
     * Uses a bell-curved distribution to favor moderate values.
     */
    private static float generateMultiplier(Random random) {
        // Generate two random values and average them for bell curve
        float val1 = MIN_MULTIPLIER + (MAX_MULTIPLIER - MIN_MULTIPLIER) * random.nextFloat();
        float val2 = MIN_MULTIPLIER + (MAX_MULTIPLIER - MIN_MULTIPLIER) * random.nextFloat();
        return (val1 + val2) / 2.0f;
    }

    /**
     * Blend parent imprints to create a child profile.
     * If no parents provided, generates a fresh imprint.
     */
    public static PetImprint blendFromParents(MobEntity child,
                                              long tameTime,
                                              @Nullable PetImprint primary,
                                              @Nullable PetImprint partner) {
        if (primary == null && partner == null) {
            return generateForNewPet(child, tameTime);
        }

        UUID uuid = child.getUuid();
        long baseHash = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        long seed = BehaviorSeedUtil.mixBehaviorSeed(baseHash, tameTime);
        Random random = new Random(seed);

        float[] primaryStats = primary != null ? primary.toArray() : generateDefaults();
        float[] partnerStats = partner != null ? partner.toArray() : generateDefaults();
        float[] childStats = new float[6];

        // Blend with slight mutation
        for (int i = 0; i < 6; i++) {
            float blendRatio = random.nextFloat();
            float blended = primaryStats[i] * blendRatio + partnerStats[i] * (1.0f - blendRatio);
            float mutation = (random.nextFloat() - 0.5f) * 0.08f; // ±4% mutation
            childStats[i] = clampMultiplier(blended + mutation);
        }

        return new PetImprint(
            childStats[0], childStats[1], childStats[2],
            childStats[3], childStats[4], childStats[5],
            seed
        );
    }

    private static float[] generateDefaults() {
        return new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    }

    private static float clampMultiplier(float value) {
        return MathHelper.clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    private float[] toArray() {
        return new float[]{
            healthMultiplier,
            speedMultiplier,
            attackMultiplier,
            defenseMultiplier,
            agilityMultiplier,
            vitalityMultiplier
        };
    }
    
    // StatModifierProvider implementation
    @Override
    public float getHealthMultiplier() {
        return healthMultiplier;
    }

    @Override
    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    @Override
    public float getAttackMultiplier() {
        return attackMultiplier;
    }

    @Override
    public float getDefenseMultiplier() {
        return defenseMultiplier;
    }

    @Override
    public float getAgilityMultiplier() {
        return agilityMultiplier;
    }

    @Override
    public float getVitalityMultiplier() {
        return vitalityMultiplier;
    }

    /**
     * Get the seed used to generate this imprint.
     */
    public long getImprintSeed() {
        return imprintSeed;
    }
    
    /**
     * Get a description of this pet's notable traits.
     * Returns strings like "Swift", "Hardy", "Fierce", etc.
     */
    public String getImprintDescription() {
        StringBuilder desc = new StringBuilder();

        float[] stats = toArray();
        String[] labels = {"Hardy", "Swift", "Fierce", "Sturdy", "Nimble", "Resilient"};
        String[] weakLabels = {"Frail", "Slow", "Gentle", "Fragile", "Clumsy", "Sensitive"};
        
        // Find the strongest and weakest traits (only if significant)
        float maxValue = 1.0f;
        float minValue = 1.0f;
        int maxIndex = -1;
        int minIndex = -1;
        
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] > maxValue && stats[i] >= 1.08f) {
                maxValue = stats[i];
                maxIndex = i;
            }
            if (stats[i] < minValue && stats[i] <= 0.92f) {
                minValue = stats[i];
                minIndex = i;
            }
        }
        
        if (maxIndex >= 0) {
            desc.append(labels[maxIndex]);
        }
        
        if (minIndex >= 0) {
            if (desc.length() > 0) desc.append(" but ");
            desc.append(weakLabels[minIndex]);
        }
        
        if (desc.length() == 0) {
            desc.append("Balanced");
        }
        
        return desc.toString().trim();
    }
    
    /**
     * Codec for serialization.
     */
    public static final Codec<PetImprint> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("healthMult").forGetter(i -> i.healthMultiplier),
            Codec.FLOAT.fieldOf("speedMult").forGetter(i -> i.speedMultiplier),
            Codec.FLOAT.fieldOf("attackMult").forGetter(i -> i.attackMultiplier),
            Codec.FLOAT.fieldOf("defenseMult").forGetter(i -> i.defenseMultiplier),
            Codec.FLOAT.fieldOf("agilityMult").forGetter(i -> i.agilityMultiplier),
            Codec.FLOAT.fieldOf("vitalityMult").forGetter(i -> i.vitalityMultiplier),
            Codec.LONG.fieldOf("imprintSeed").forGetter(i -> i.imprintSeed)
        ).apply(instance, PetImprint::new)
    );

    public static String[] statKeyArray() {
        return STAT_KEYS.clone();
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
        VITALITY("vitality");

        private final String key;

        StatType(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
