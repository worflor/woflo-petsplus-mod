package woflo.petsplus.stats;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureModifierSampler.NatureAdjustment;
import woflo.petsplus.stats.nature.NatureModifierSampler.NatureRoll;
import woflo.petsplus.stats.nature.NatureModifierSampler.NatureStat;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetAttributeManagerNatureTest {

    @Test
    void appliesAgilityAndDefenseNatureBonuses() {
        EntityAttributeInstance healthInstance = mock(EntityAttributeInstance.class);
        EntityAttributeInstance speedInstance = mock(EntityAttributeInstance.class);
        EntityAttributeInstance attackInstance = mock(EntityAttributeInstance.class);
        EntityAttributeInstance armorInstance = mock(EntityAttributeInstance.class);
        EntityAttributeInstance knockbackInstance = mock(EntityAttributeInstance.class);
        EntityAttributeInstance followInstance = mock(EntityAttributeInstance.class);

        RegistryEntry<EntityAttribute> healthAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> speedAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> attackAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> armorAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> knockbackAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> followAttribute = mock(RegistryEntry.class);

        when(armorInstance.getBaseValue()).thenReturn(10.0);
        when(knockbackInstance.getBaseValue()).thenReturn(0.0);
        when(followInstance.getBaseValue()).thenReturn(35.0);

        NatureAdjustment adjustment = new NatureAdjustment(
            new NatureRoll(NatureStat.AGILITY, 0.05f, 1.0f),
            new NatureRoll(NatureStat.DEFENSE, 0.02f, 1.0f),
            1.0f, 1.0f, 1.0f, 1.0f,
            PetComponent.NatureEmotionProfile.EMPTY);

        Map<NatureStat, RegistryEntry<EntityAttribute>> attributeMap = new EnumMap<>(NatureStat.class);
        attributeMap.put(NatureStat.HEALTH, healthAttribute);
        attributeMap.put(NatureStat.VITALITY, healthAttribute);
        attributeMap.put(NatureStat.SPEED, speedAttribute);
        attributeMap.put(NatureStat.SWIM_SPEED, speedAttribute);
        attributeMap.put(NatureStat.ATTACK, attackAttribute);
        attributeMap.put(NatureStat.DEFENSE, armorAttribute);
        attributeMap.put(NatureStat.AGILITY, knockbackAttribute);
        attributeMap.put(NatureStat.FOCUS, followAttribute);
        attributeMap.put(NatureStat.LOYALTY, followAttribute);

        PetAttributeManager.applyNatureAdjustment(attribute -> {
            if (attribute == healthAttribute) {
                return healthInstance;
            }
            if (attribute == speedAttribute) {
                return speedInstance;
            }
            if (attribute == attackAttribute) {
                return attackInstance;
            }
            if (attribute == armorAttribute) {
                return armorInstance;
            }
            if (attribute == knockbackAttribute) {
                return knockbackInstance;
            }
            if (attribute == followAttribute) {
                return followInstance;
            }
            return null;
        }, adjustment, attributeMap);

        ArgumentCaptor<EntityAttributeModifier> defenseCaptor = ArgumentCaptor.forClass(EntityAttributeModifier.class);
        verify(armorInstance).addPersistentModifier(defenseCaptor.capture());
        EntityAttributeModifier defenseModifier = defenseCaptor.getValue();
        assertEquals(EntityAttributeModifier.Operation.ADD_VALUE, defenseModifier.operation(),
            "Defense nature bonus should apply as an additive value");
        assertEquals(0.2, defenseModifier.value(), 1.0e-6,
            "Defense nature bonus should scale with base armor");

        ArgumentCaptor<EntityAttributeModifier> agilityCaptor = ArgumentCaptor.forClass(EntityAttributeModifier.class);
        verify(knockbackInstance).addPersistentModifier(agilityCaptor.capture());
        EntityAttributeModifier agilityModifier = agilityCaptor.getValue();
        assertEquals(EntityAttributeModifier.Operation.ADD_VALUE, agilityModifier.operation(),
            "Agility nature bonus should apply knockback resistance additively");
        assertEquals(0.05, agilityModifier.value(), 1.0e-6,
            "Agility nature bonus should use the fallback scale when the base is zero");

        verify(speedInstance, times(0)).addPersistentModifier(any());
        verify(attackInstance, times(0)).addPersistentModifier(any());
        verify(healthInstance, times(0)).addPersistentModifier(any());
    }

    @Test
    void appliesFocusAndLoyaltyNatureBonusesToFollowRange() {
        EntityAttributeInstance followInstance = mock(EntityAttributeInstance.class);
        RegistryEntry<EntityAttribute> followAttribute = mock(RegistryEntry.class);

        NatureAdjustment adjustment = new NatureAdjustment(
            new NatureRoll(NatureStat.FOCUS, 0.05f, 1.0f),
            new NatureRoll(NatureStat.LOYALTY, 0.03f, 1.0f),
            1.0f, 1.0f, 1.0f, 1.0f,
            PetComponent.NatureEmotionProfile.EMPTY);

        Map<NatureStat, RegistryEntry<EntityAttribute>> attributeMap = new EnumMap<>(NatureStat.class);
        attributeMap.put(NatureStat.FOCUS, followAttribute);
        attributeMap.put(NatureStat.LOYALTY, followAttribute);

        PetAttributeManager.applyNatureAdjustment(attribute -> attribute == followAttribute ? followInstance : null,
            adjustment, attributeMap);

        ArgumentCaptor<EntityAttributeModifier> followCaptor = ArgumentCaptor.forClass(EntityAttributeModifier.class);
        verify(followInstance, times(2)).addPersistentModifier(followCaptor.capture());
        assertEquals(2, followCaptor.getAllValues().size(), "Focus and loyalty should add two follow range modifiers");

        boolean focusApplied = followCaptor.getAllValues().stream()
            .anyMatch(mod -> mod.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                && Math.abs(mod.value() - 0.05) < 1.0e-6);
        boolean loyaltyApplied = followCaptor.getAllValues().stream()
            .anyMatch(mod -> mod.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                && Math.abs(mod.value() - 0.03) < 1.0e-6);

        assertTrue(focusApplied, "Focus nature bonus should multiply follow range");
        assertTrue(loyaltyApplied, "Loyalty nature bonus should multiply follow range");
    }

    @Test
    void appliesSwimNatureBonusesToMovementSpeedWhenSwimAttributeMissing() {
        EntityAttributeInstance movementInstance = mock(EntityAttributeInstance.class);
        RegistryEntry<EntityAttribute> swimAttribute = mock(RegistryEntry.class);
        RegistryEntry<EntityAttribute> movementAttribute = mock(RegistryEntry.class);

        NatureAdjustment adjustment = new NatureAdjustment(
            new NatureRoll(NatureStat.SWIM_SPEED, 0.06f, 1.0f),
            NatureRoll.EMPTY,
            1.0f, 1.0f, 1.0f, 1.0f,
            PetComponent.NatureEmotionProfile.EMPTY);

        Map<NatureStat, RegistryEntry<EntityAttribute>> attributeMap = new EnumMap<>(NatureStat.class);
        attributeMap.put(NatureStat.SWIM_SPEED, swimAttribute);
        attributeMap.put(NatureStat.SPEED, movementAttribute);

        PetAttributeManager.applyNatureAdjustment(attribute -> {
            if (attribute == swimAttribute) {
                return null;
            }
            if (attribute == movementAttribute) {
                return movementInstance;
            }
            return null;
        }, adjustment, attributeMap);

        ArgumentCaptor<EntityAttributeModifier> modifierCaptor = ArgumentCaptor.forClass(EntityAttributeModifier.class);
        verify(movementInstance).addPersistentModifier(modifierCaptor.capture());

        EntityAttributeModifier modifier = modifierCaptor.getValue();
        assertEquals(EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, modifier.operation(),
            "Swim nature bonuses should multiply the fallback attribute when swim speed is missing");
        assertEquals(0.06, modifier.value(), 1.0e-6,
            "Swim nature bonus should reuse the rolled multiplier against movement speed");
    }
}
