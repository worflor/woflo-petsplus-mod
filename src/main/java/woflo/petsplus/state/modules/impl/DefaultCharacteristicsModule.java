package woflo.petsplus.state.modules.impl;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetComponent.NatureEmotionProfile;
import woflo.petsplus.state.modules.CharacteristicsModule;
import woflo.petsplus.stats.PetCharacteristics;

public final class DefaultCharacteristicsModule implements CharacteristicsModule {
    private static final float MIN_VOLATILITY = 0.3f;
    private static final float MAX_VOLATILITY = 1.75f;
    private static final float MIN_RESILIENCE = 0.5f;
    private static final float MAX_RESILIENCE = 1.5f;
    private static final float MIN_CONTAGION = 0.5f;
    private static final float MAX_CONTAGION = 1.5f;
    private static final float MIN_GUARD = 0.5f;
    private static final float MAX_GUARD = 1.5f;
    private static final float ROLE_AFFINITY_EPSILON = 1.0e-5f;
    private static final String[] ROLE_AFFINITY_KEYS = PetCharacteristics.statKeyArray();
    private static final Map<String, Integer> ROLE_AFFINITY_INDEX = createRoleAffinityIndex();

    private static Map<String, Integer> createRoleAffinityIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < ROLE_AFFINITY_KEYS.length; i++) {
            index.put(ROLE_AFFINITY_KEYS[i].toLowerCase(Locale.ROOT), i);
        }
        return Collections.unmodifiableMap(index);
    }

    private PetComponent parent;
    private @Nullable PetCharacteristics characteristics;
    private float natureVolatilityMultiplier = 1.0f;
    private float natureResilienceMultiplier = 1.0f;
    private float natureContagionModifier = 1.0f;
    private float natureGuardModifier = 1.0f;
    private NatureEmotionProfile natureEmotionProfile = NatureEmotionProfile.EMPTY;
    private List<AttributeKey> nameAttributes = new ArrayList<>();
    private Map<Identifier, float[]> roleAffinityBonuses = new HashMap<>();

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    public void onDetach() {
        parent = null;
        nameAttributes = new ArrayList<>();
        characteristics = null;
        natureEmotionProfile = NatureEmotionProfile.EMPTY;
        natureVolatilityMultiplier = 1.0f;
        natureResilienceMultiplier = 1.0f;
        natureContagionModifier = 1.0f;
        natureGuardModifier = 1.0f;
    }

    @Override
    public @Nullable PetCharacteristics getCharacteristics() {
        return characteristics;
    }

    @Override
    public boolean setCharacteristics(@Nullable PetCharacteristics characteristics) {
        if (Objects.equals(this.characteristics, characteristics)) {
            return false;
        }
        this.characteristics = characteristics;
        return true;
    }

    @Override
    public NatureEmotionProfile getNatureEmotionProfile() {
        return natureEmotionProfile;
    }

    @Override
    public boolean setNatureEmotionProfile(NatureEmotionProfile profile) {
        if (profile == null) {
            profile = NatureEmotionProfile.EMPTY;
        }
        NatureEmotionProfile sanitized = sanitizeNatureProfile(profile);
        if (Objects.equals(this.natureEmotionProfile, sanitized)) {
            return false;
        }
        this.natureEmotionProfile = sanitized;
        return true;
    }

    @Override
    public float getNatureVolatility() {
        return natureVolatilityMultiplier;
    }

    @Override
    public float getNatureResilience() {
        return natureResilienceMultiplier;
    }

    @Override
    public float getNatureContagion() {
        return natureContagionModifier;
    }

    @Override
    public float getNatureGuardModifier() {
        return natureGuardModifier;
    }

    @Override
    public boolean updateNatureTuning(float volatility, float resilience, float contagion, float guard) {
        float clampedVolatility = clamp(volatility, MIN_VOLATILITY, MAX_VOLATILITY);
        float clampedResilience = clamp(resilience, MIN_RESILIENCE, MAX_RESILIENCE);
        float clampedContagion = clamp(contagion, MIN_CONTAGION, MAX_CONTAGION);
        float clampedGuard = clamp(guard, MIN_GUARD, MAX_GUARD);

        if (natureVolatilityMultiplier == clampedVolatility
            && natureResilienceMultiplier == clampedResilience
            && natureContagionModifier == clampedContagion
            && natureGuardModifier == clampedGuard) {
            return false;
        }

        natureVolatilityMultiplier = clampedVolatility;
        natureResilienceMultiplier = clampedResilience;
        natureContagionModifier = clampedContagion;
        natureGuardModifier = clampedGuard;
        return true;
    }

    @Override
    public List<AttributeKey> getNameAttributes() {
        return Collections.unmodifiableList(nameAttributes);
    }

    @Override
    public void setNameAttributes(List<AttributeKey> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            nameAttributes = new ArrayList<>();
        } else {
            nameAttributes = new ArrayList<>(attributes);
        }
    }

    @Override
    public void addNameAttribute(AttributeKey attribute) {
        if (attribute == null) {
            return;
        }
        if (nameAttributes.contains(attribute)) {
            return;
        }
        nameAttributes.add(attribute);
    }

    @Override
    public void removeNameAttribute(AttributeKey attribute) {
        nameAttributes.remove(attribute);
    }

    @Override
    public void resetRoleAffinityBonuses() {
        if (!roleAffinityBonuses.isEmpty()) {
            roleAffinityBonuses.clear();
            syncCharacteristicAffinityLookup();
        }
    }

    @Override
    public void applyRoleAffinityBonuses(Identifier roleId, String[] statKeys, float[] bonuses) {
        if (roleId == null || statKeys == null || bonuses == null) {
            return;
        }
        if (statKeys.length != bonuses.length) {
            throw new IllegalArgumentException("statKeys and bonuses must have the same length");
        }

        boolean created = !roleAffinityBonuses.containsKey(roleId);
        float[] vector = roleAffinityBonuses.computeIfAbsent(roleId, id -> new float[ROLE_AFFINITY_KEYS.length]);

        boolean anyNonZero = false;
        for (int i = 0; i < statKeys.length; i++) {
            Integer idx = ROLE_AFFINITY_INDEX.get(statKeys[i].toLowerCase(Locale.ROOT));
            if (idx == null) {
                continue;
            }
            vector[idx] = bonuses[i];
            if (Math.abs(bonuses[i]) > ROLE_AFFINITY_EPSILON) {
                anyNonZero = true;
            }
        }

        if (!anyNonZero && !created) {
            roleAffinityBonuses.remove(roleId, vector);
        }

        syncCharacteristicAffinityLookup();
    }

    @Override
    public float resolveRoleAffinityBonus(@Nullable PetRoleType roleType, String statKey) {
        if (roleType == null || statKey == null) {
            return 0.0f;
        }
        float[] vector = roleAffinityBonuses.get(roleType.id());
        if (vector == null) {
            return 0.0f;
        }
        Integer index = ROLE_AFFINITY_INDEX.get(statKey.toLowerCase(Locale.ROOT));
        if (index == null) {
            return 0.0f;
        }
        return vector[index];
    }

    @Override
    public Map<Identifier, float[]> getRoleAffinityBonuses() {
        return Collections.unmodifiableMap(roleAffinityBonuses);
    }

    private void syncCharacteristicAffinityLookup() {
        if (characteristics != null && parent != null) {
            characteristics.setRoleAffinityLookup(this::resolveRoleAffinityBonus);
        }
    }

    @Override
    public Data toData() {
        // Deep copy roleAffinityBonuses
        Map<Identifier, float[]> copiedBonuses = new HashMap<>();
        roleAffinityBonuses.forEach((id, arr) -> copiedBonuses.put(id, arr.clone()));
        
        return new Data(
            characteristics,
            natureVolatilityMultiplier,
            natureResilienceMultiplier,
            natureContagionModifier,
            natureGuardModifier,
            natureEmotionProfile,
            List.copyOf(nameAttributes),
            copiedBonuses
        );
    }

    @Override
    public void fromData(Data data) {
        characteristics = data.characteristics();
        natureVolatilityMultiplier = data.natureVolatilityMultiplier();
        natureResilienceMultiplier = data.natureResilienceMultiplier();
        natureContagionModifier = data.natureContagionModifier();
        natureGuardModifier = data.natureGuardModifier();
        natureEmotionProfile = sanitizeNatureProfile(data.natureEmotionProfile());
        nameAttributes = new ArrayList<>(data.nameAttributes());
        
        // Deep copy roleAffinityBonuses
        roleAffinityBonuses.clear();
        data.roleAffinityBonuses().forEach((id, arr) -> roleAffinityBonuses.put(id, arr.clone()));
        
        syncCharacteristicAffinityLookup();
    }

    private NatureEmotionProfile sanitizeNatureProfile(@Nullable NatureEmotionProfile profile) {
        if (profile == null) {
            return NatureEmotionProfile.EMPTY;
        }

        PetComponent.Emotion major = profile.majorEmotion();
        float majorStrength = clampEmotionStrength(profile.majorStrength());
        if (major == null || majorStrength <= 0f) {
            major = null;
            majorStrength = 0f;
        }

        PetComponent.Emotion minor = profile.minorEmotion();
        float minorStrength = clampEmotionStrength(profile.minorStrength());
        if (minor == null || minorStrength <= 0f) {
            minor = null;
            minorStrength = 0f;
        }

        PetComponent.Emotion quirk = profile.quirkEmotion();
        float quirkStrength = clampEmotionStrength(profile.quirkStrength());
        if (quirk == null || quirkStrength <= 0f) {
            quirk = null;
            quirkStrength = 0f;
        }

        if (major == null && minor == null && quirk == null) {
            return NatureEmotionProfile.EMPTY;
        }

        return new NatureEmotionProfile(major, majorStrength, minor, minorStrength, quirk, quirkStrength);
    }

    private static float clampEmotionStrength(float strength) {
        return MathHelper.clamp(strength, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return MathHelper.clamp(value, min, max);
    }
}
