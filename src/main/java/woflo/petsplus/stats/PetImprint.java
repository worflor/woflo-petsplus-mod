package woflo.petsplus.stats;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.util.BehaviorSeedUtil;

import java.util.Random;
import java.util.UUID;

/**
 * Represents the unique "imprint" of a pet – small stat variances that ensure every companion
 * feels distinct even when sharing the same nature and role. Multipliers are generated at tame
 * time (or birth) and never change afterwards.
 *
 * <p>All multipliers are multiplicative values in the range {@link #MIN_MULTIPLIER} to
 * {@link #MAX_MULTIPLIER}. Consolidated stats align with the new six-stat progression model:
 * vitality, swiftness, might, guard, focus, agility.</p>
 */
public final class PetImprint implements StatModifierProvider {

    private static final float MIN_MULTIPLIER = 0.88f;
    private static final float MAX_MULTIPLIER = 1.12f;

    private static final String[] STAT_KEYS = java.util.Arrays.stream(StatType.values())
        .map(StatType::key)
        .toArray(String[]::new);

    private final float vitalityMultiplier;
    private final float swiftnessMultiplier;
    private final float mightMultiplier;
    private final float guardMultiplier;
    private final float focusMultiplier;
    private final float agilityMultiplier;
    private final long imprintSeed;

    private PetImprint(float vitality,
                       float swiftness,
                       float might,
                       float guard,
                       float focus,
                       float agility,
                       long seed) {
        this.vitalityMultiplier = clampMultiplier(vitality);
        this.swiftnessMultiplier = clampMultiplier(swiftness);
        this.mightMultiplier = clampMultiplier(might);
        this.guardMultiplier = clampMultiplier(guard);
        this.focusMultiplier = clampMultiplier(focus);
        this.agilityMultiplier = clampMultiplier(agility);
        this.imprintSeed = seed;
    }

    /**
     * Generate a brand-new imprint for a freshly tamed pet.
     */
    public static PetImprint generateForNewPet(MobEntity pet, long tameTime) {
        UUID uuid = pet.getUuid();
        long baseHash = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        long seed = BehaviorSeedUtil.mixBehaviorSeed(baseHash, tameTime);
        Random random = new Random(seed);

        return new PetImprint(
            generateMultiplier(random), // vitality
            generateMultiplier(random), // swiftness
            generateMultiplier(random), // might
            generateMultiplier(random), // guard
            generateMultiplier(random), // focus
            generateMultiplier(random), // agility
            seed
        );
    }

    /**
     * Blend the parent imprints to create an offspring profile.
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

        float[] a = primary != null ? primary.toArray() : generateDefaults();
        float[] b = partner != null ? partner.toArray() : generateDefaults();
        float[] result = new float[6];

        for (int i = 0; i < result.length; i++) {
            float blendRatio = random.nextFloat();
            float blended = (a[i] * blendRatio) + (b[i] * (1.0f - blendRatio));
            float mutation = (random.nextFloat() - 0.5f) * 0.08f; // +/-4% mutation
            result[i] = clampMultiplier(blended + mutation);
        }

        return new PetImprint(
            result[0], result[1], result[2],
            result[3], result[4], result[5],
            seed
        );
    }

    private static float generateMultiplier(Random random) {
        float val1 = MIN_MULTIPLIER + (MAX_MULTIPLIER - MIN_MULTIPLIER) * random.nextFloat();
        float val2 = MIN_MULTIPLIER + (MAX_MULTIPLIER - MIN_MULTIPLIER) * random.nextFloat();
        return (val1 + val2) * 0.5f;
    }

    private static float[] generateDefaults() {
        return new float[] {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    }

    private static float clampMultiplier(float value) {
        return MathHelper.clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public float[] toArray() {
        return new float[] {
            vitalityMultiplier,
            swiftnessMultiplier,
            mightMultiplier,
            guardMultiplier,
            focusMultiplier,
            agilityMultiplier
        };
    }

    // ------------------------------------------------------------------------
    // StatModifierProvider implementation
    // ------------------------------------------------------------------------

    @Override
    public float getVitalityMultiplier() {
        return vitalityMultiplier;
    }

    @Override
    public float getSwiftnessMultiplier() {
        return swiftnessMultiplier;
    }

    @Override
    public float getMightMultiplier() {
        return mightMultiplier;
    }

    @Override
    public float getGuardMultiplier() {
        return guardMultiplier;
    }

    @Override
    public float getFocusMultiplier() {
        return focusMultiplier;
    }

    @Override
    public float getAgilityMultiplier() {
        return agilityMultiplier;
    }

    public long getImprintSeed() {
        return imprintSeed;
    }

    /**
     * Human-readable short description summarising the imprint strengths.
     */
    public String getImprintDescription() {
        StringBuilder builder = new StringBuilder();
        float[] stats = toArray();

        String[] strongLabels = {"Stalwart", "Fleet", "Fierce", "Bulwark", "Insightful", "Acrobatic"};
        String[] weakLabels = {"Fragile", "Sluggish", "Gentle", "Brittle", "Distracted", "Clumsy"};

        float highest = 1.0f;
        float lowest = 1.0f;
        int highIndex = -1;
        int lowIndex = -1;

        for (int i = 0; i < stats.length; i++) {
            float value = stats[i];
            if (value > highest && value >= 1.08f) {
                highest = value;
                highIndex = i;
            }
            if (value < lowest && value <= 0.92f) {
                lowest = value;
                lowIndex = i;
            }
        }

        if (highIndex >= 0) {
            builder.append(strongLabels[highIndex]);
        }
        if (lowIndex >= 0) {
            if (builder.length() > 0) {
                builder.append(" but ");
            }
            builder.append(weakLabels[lowIndex]);
        }
        if (builder.length() == 0) {
            builder.append("Balanced");
        }

        return builder.toString();
    }

    public static String[] statKeyArray() {
        return STAT_KEYS.clone();
    }

    // ------------------------------------------------------------------------
    // Serialization (with legacy migration support)
    // ------------------------------------------------------------------------

    public static final Codec<PetImprint> CODEC = Codec.of(
        new com.mojang.serialization.Encoder<PetImprint>() {
            @Override
            public <T> DataResult<T> encode(PetImprint imprint, DynamicOps<T> ops, T prefix) {
                RecordBuilder<T> builder = ops.mapBuilder();
                encodeToBuilder(imprint, ops, builder);
                return builder.build(prefix);
            }
        },
        new com.mojang.serialization.Decoder<PetImprint>() {
            @Override
            public <T> DataResult<Pair<PetImprint, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    PetImprint imprint = fromDynamic(new Dynamic<>(ops, input));
                    return DataResult.success(Pair.of(imprint, input));
                } catch (Exception error) {
                    return DataResult.error(() -> "Failed to decode PetImprint: " + error.getMessage());
                }
            }
        }
    );

    private static PetImprint fromDynamic(Dynamic<?> dynamic) {
        float vitality = mergeHighest(dynamic,
            "vitalityMult",
            "healthMult",
            "vitality");

        float swiftness = mergeHighest(dynamic,
            "swiftnessMult",
            "speedMult",
            "swimSpeedMult",
            "swim_speedMult");

        float might = mergeHighest(dynamic,
            "mightMult",
            "attackMult");

        float guard = mergeHighest(dynamic,
            "guardMult",
            "defenseMult");

        float focus = mergeHighest(dynamic,
            "focusMult",
            "loyaltyMult");

        float agility = mergeHighest(dynamic,
            "agilityMult",
            "dexterityMult");

        long seed = dynamic.get("imprintSeed").asLong(0L);
        if (seed == 0L) {
            // Attempt fallback for older saves that might have stored "seed"
            seed = dynamic.get("seed").asLong(0L);
        }

        return new PetImprint(vitality, swiftness, might, guard, focus, agility, seed);
    }

    private static float mergeHighest(Dynamic<?> dynamic, String... keys) {
        float best = Float.NaN;

        for (String key : keys) {
            var optional = dynamic.get(key);
            if (optional.result().isPresent()) {
                float value = optional.asFloat(1.0f);
                if (Float.isNaN(best) || value > best) {
                    best = value;
                }
            }
        }

        if (Float.isNaN(best)) {
            return 1.0f;
        }
        return clampMultiplier(best);
    }

    private static <T> RecordBuilder<T> encodeToBuilder(PetImprint imprint,
                                                        DynamicOps<T> ops,
                                                        RecordBuilder<T> builder) {
        builder.add("vitalityMult", ops.createFloat(imprint.vitalityMultiplier));
        builder.add("swiftnessMult", ops.createFloat(imprint.swiftnessMultiplier));
        builder.add("mightMult", ops.createFloat(imprint.mightMultiplier));
        builder.add("guardMult", ops.createFloat(imprint.guardMultiplier));
        builder.add("focusMult", ops.createFloat(imprint.focusMultiplier));
        builder.add("agilityMult", ops.createFloat(imprint.agilityMultiplier));
        builder.add("imprintSeed", ops.createLong(imprint.imprintSeed));
        return builder;
    }

    private enum StatType {
        VITALITY("vitality"),
        SWIFTNESS("swiftness"),
        MIGHT("might"),
        GUARD("guard"),
        FOCUS("focus"),
        AGILITY("agility");

        private final String key;

        StatType(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
