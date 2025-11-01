package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetImprint;
import woflo.petsplus.state.personality.PersonalityProfile;
import woflo.petsplus.util.CodecUtils;

public interface CharacteristicsModule extends DataBackedModule<CharacteristicsModule.Data> {
    @Nullable PetImprint getImprint();
    boolean setImprint(@Nullable PetImprint imprint);
    PersonalityProfile getPersonalityProfile();

    PetComponent.NatureEmotionProfile getNatureEmotionProfile();
    boolean setNatureEmotionProfile(PetComponent.NatureEmotionProfile profile);

    float getNatureVolatility();
    float getNatureResilience();
    float getNatureContagion();
    float getNatureGuardModifier();
    boolean updateNatureTuning(float volatility, float resilience, float contagion, float guard);

    List<AttributeKey> getNameAttributes();
    void setNameAttributes(List<AttributeKey> attributes);
    void addNameAttribute(AttributeKey attribute);
    void removeNameAttribute(AttributeKey attribute);

    // Role affinity bonuses
    void resetRoleAffinityBonuses();
    void applyRoleAffinityBonuses(net.minecraft.util.Identifier roleId, String[] statKeys, float[] bonuses);
    float resolveRoleAffinityBonus(@Nullable woflo.petsplus.api.registry.PetRoleType roleType, String statKey);
    java.util.Map<net.minecraft.util.Identifier, float[]> getRoleAffinityBonuses();

    record Data(
        @Nullable PetImprint imprint,
        @Nullable PersonalityProfile personalityProfile,
        float natureVolatilityMultiplier,
        float natureResilienceMultiplier,
        float natureContagionModifier,
        float natureGuardModifier,
        PetComponent.NatureEmotionProfile natureEmotionProfile,
        List<AttributeKey> nameAttributes,
        java.util.Map<net.minecraft.util.Identifier, float[]> roleAffinityBonuses
    ) {
        // Use PetImprint's built-in Codec directly

        private static final Codec<PetComponent.Emotion> EMOTION_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                if (value == null || value.isBlank()) {
                    return DataResult.success(null);
                }
                try {
                    return DataResult.success(PetComponent.Emotion.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(() -> "Unknown emotion '" + value + "'");
                }
            },
            emotion -> emotion == null ? "" : emotion.name().toLowerCase(Locale.ROOT)
        );

        private static final Codec<PetComponent.NatureEmotionProfile> NATURE_EMOTION_PROFILE_CODEC =
            RecordCodecBuilder.create(instance ->
                instance.group(
                    EMOTION_CODEC.optionalFieldOf("major").forGetter(profile -> Optional.ofNullable(profile.majorEmotion())),
                    Codec.FLOAT.fieldOf("majorStrength").orElse(0f).forGetter(PetComponent.NatureEmotionProfile::majorStrength),
                    EMOTION_CODEC.optionalFieldOf("minor").forGetter(profile -> Optional.ofNullable(profile.minorEmotion())),
                    Codec.FLOAT.fieldOf("minorStrength").orElse(0f).forGetter(PetComponent.NatureEmotionProfile::minorStrength),
                    EMOTION_CODEC.optionalFieldOf("quirk").forGetter(profile -> Optional.ofNullable(profile.quirkEmotion())),
                    Codec.FLOAT.fieldOf("quirkStrength").orElse(0f).forGetter(PetComponent.NatureEmotionProfile::quirkStrength)
                ).apply(instance, (major, majorStrength, minor, minorStrength, quirk, quirkStrength) ->
                    new PetComponent.NatureEmotionProfile(
                        major.orElse(null),
                        majorStrength,
                        minor.orElse(null),
                        minorStrength,
                        quirk.orElse(null),
                        quirkStrength
                    ))
            );

        private static final Codec<AttributeKey> ATTRIBUTE_KEY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.fieldOf("type").orElse("").forGetter(Data::sanitizeType),
                Codec.STRING.fieldOf("value").orElse("").forGetter(Data::sanitizeValue),
                Codec.INT.fieldOf("priority").orElse(0).forGetter(AttributeKey::priority)
            ).apply(instance, AttributeKey::new)
        );

        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                PetImprint.CODEC.optionalFieldOf("imprint")
                    .forGetter(data -> Optional.ofNullable(data.imprint())),
                PersonalityProfile.CODEC.optionalFieldOf("personalityProfile")
                    .forGetter(data -> Optional.ofNullable(data.personalityProfile())),
                Codec.FLOAT.fieldOf("natureVolatilityMultiplier").orElse(1.0f)
                    .forGetter(Data::natureVolatilityMultiplier),
                Codec.FLOAT.fieldOf("natureResilienceMultiplier").orElse(1.0f)
                    .forGetter(Data::natureResilienceMultiplier),
                Codec.FLOAT.fieldOf("natureContagionModifier").orElse(1.0f)
                    .forGetter(Data::natureContagionModifier),
                Codec.FLOAT.fieldOf("natureGuardModifier").orElse(1.0f)
                    .forGetter(Data::natureGuardModifier),
                NATURE_EMOTION_PROFILE_CODEC.optionalFieldOf("natureEmotionProfile")
                    .forGetter(data -> encodeEmotionProfile(data.natureEmotionProfile())),
                ATTRIBUTE_KEY_CODEC.listOf().fieldOf("nameAttributes").orElse(List.of())
                    .forGetter(Data::nameAttributes),
                Codec.unboundedMap(CodecUtils.identifierCodec(), Codec.FLOAT.listOf())
                    .fieldOf("roleAffinityBonuses").orElse(Map.of())
                    .forGetter(data -> encodeRoleAffinityBonuses(data.roleAffinityBonuses()))
            ).apply(instance, (imprint, personality, volatility, resilience, contagion, guard, profile, attributes, bonuses) ->
                new Data(
                    imprint.orElse(null),
                    personality.orElse(null),
                    volatility,
                    resilience,
                    contagion,
                    guard,
                    profile.orElse(PetComponent.NatureEmotionProfile.EMPTY),
                    List.copyOf(attributes),
                    decodeRoleAffinityBonuses(bonuses)
                ))
        );

        private static Map<net.minecraft.util.Identifier, float[]> decodeRoleAffinityBonuses(
            Map<net.minecraft.util.Identifier, List<Float>> encoded
        ) {
            Map<net.minecraft.util.Identifier, float[]> decoded = new HashMap<>();
            encoded.forEach((id, values) -> {
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = values.get(i);
                }
                decoded.put(id, vector);
            });
            return decoded;
        }

        private static Map<net.minecraft.util.Identifier, List<Float>> encodeRoleAffinityBonuses(
            Map<net.minecraft.util.Identifier, float[]> bonuses
        ) {
            Map<net.minecraft.util.Identifier, List<Float>> encoded = new HashMap<>();
            bonuses.forEach((id, vector) -> {
                if (vector == null || vector.length == 0) {
                    encoded.put(id, List.of());
                    return;
                }
                List<Float> values = new ArrayList<>(vector.length);
                for (float value : vector) {
                    values.add(value);
                }
                encoded.put(id, List.copyOf(values));
            });
            return encoded;
        }

        private static Optional<PetComponent.NatureEmotionProfile> encodeEmotionProfile(
            PetComponent.NatureEmotionProfile profile
        ) {
            if (profile == null || profile.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(profile);
        }

        private static String sanitizeType(AttributeKey key) {
            return key.type() == null ? "" : key.type();
        }

        private static String sanitizeValue(AttributeKey key) {
            return key.value() == null ? "" : key.value();
        }
    }
}
