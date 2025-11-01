package woflo.petsplus.state.personality;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.stats.PetImprint;

/**
 * Normalised multi-axis projection of a pet's imprint stats. Values are centred around 0 and
 * clamped to the range [-1, 1] so downstream systems can reason about personality without
 * repeatedly decoding raw imprint multipliers.
 */
public record PersonalityProfile(
    float affection,
    float playfulness,
    float curiosity,
    float vigilance,
    float bravery,
    float composure
) {
    private static final float MAX_DELTA = 0.12f; // Imprint multipliers span [0.88, 1.12]
    private static final PersonalityProfile NEUTRAL = new PersonalityProfile(0f, 0f, 0f, 0f, 0f, 0f);

    public static final Codec<PersonalityProfile> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.optionalFieldOf("affection", 0f).forGetter(PersonalityProfile::affection),
            Codec.FLOAT.optionalFieldOf("playfulness", 0f).forGetter(PersonalityProfile::playfulness),
            Codec.FLOAT.optionalFieldOf("curiosity", 0f).forGetter(PersonalityProfile::curiosity),
            Codec.FLOAT.optionalFieldOf("vigilance", 0f).forGetter(PersonalityProfile::vigilance),
            Codec.FLOAT.optionalFieldOf("bravery", 0f).forGetter(PersonalityProfile::bravery),
            Codec.FLOAT.optionalFieldOf("composure", 0f).forGetter(PersonalityProfile::composure)
        ).apply(instance, PersonalityProfile::new)
    );

    public PersonalityProfile {
        affection = clampAxis(affection);
        playfulness = clampAxis(playfulness);
        curiosity = clampAxis(curiosity);
        vigilance = clampAxis(vigilance);
        bravery = clampAxis(bravery);
        composure = clampAxis(composure);
    }

    public static PersonalityProfile neutral() {
        return NEUTRAL;
    }

    public boolean isNeutral() {
        return Math.abs(affection) < 0.01f
            && Math.abs(playfulness) < 0.01f
            && Math.abs(curiosity) < 0.01f
            && Math.abs(vigilance) < 0.01f
            && Math.abs(bravery) < 0.01f
            && Math.abs(composure) < 0.01f;
    }

    public static PersonalityProfile fromImprint(@Nullable PetImprint imprint) {
        if (imprint == null) {
            return neutral();
        }

        float vitality = normalize(imprint.getVitalityMultiplier());
        float swiftness = normalize(imprint.getSwiftnessMultiplier());
        float might = normalize(imprint.getMightMultiplier());
        float guard = normalize(imprint.getGuardMultiplier());
        float focus = normalize(imprint.getFocusMultiplier());
        float agility = normalize(imprint.getAgilityMultiplier());

        float affection = clampAxis(vitality * 0.55f + focus * 0.35f + Math.max(0f, -guard) * 0.1f);
        float playfulness = clampAxis(agility * 0.5f + swiftness * 0.35f + vitality * 0.15f);
        float curiosity = clampAxis(focus * 0.6f + agility * 0.25f + swiftness * 0.15f);
        float vigilance = clampAxis(guard * 0.6f + focus * 0.25f - Math.max(0f, playfulness) * 0.15f);
        float bravery = clampAxis(might * 0.55f + guard * 0.3f + vitality * 0.15f - Math.max(0f, vigilance) * 0.2f);
        float composure = clampAxis(guard * 0.4f + vitality * 0.3f + focus * 0.3f - Math.max(0f, playfulness) * 0.2f);

        return new PersonalityProfile(affection, playfulness, curiosity, vigilance, bravery, composure);
    }

    private static float normalize(float multiplier) {
        float delta = multiplier - 1f;
        if (Math.abs(delta) < 1.0e-4f) {
            return 0f;
        }
        return MathHelper.clamp(delta / MAX_DELTA, -1f, 1f);
    }

    private static float clampAxis(float value) {
        return MathHelper.clamp(value, -1f, 1f);
    }
}
