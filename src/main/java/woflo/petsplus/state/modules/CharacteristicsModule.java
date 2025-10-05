package woflo.petsplus.state.modules;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetCharacteristics;

public interface CharacteristicsModule extends DataBackedModule<CharacteristicsModule.Data> {
    @Nullable PetCharacteristics getCharacteristics();
    boolean setCharacteristics(@Nullable PetCharacteristics characteristics);

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
        @Nullable PetCharacteristics characteristics,
        float natureVolatilityMultiplier,
        float natureResilienceMultiplier,
        float natureContagionModifier,
        float natureGuardModifier,
        PetComponent.NatureEmotionProfile natureEmotionProfile,
        List<AttributeKey> nameAttributes,
        java.util.Map<net.minecraft.util.Identifier, float[]> roleAffinityBonuses
    ) {}
}
