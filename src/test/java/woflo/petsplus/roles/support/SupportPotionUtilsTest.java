package woflo.petsplus.roles.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.roles.support.SupportPotionUtils.ChargeState;
import woflo.petsplus.roles.support.SupportPotionUtils.MergeOutcome;
import woflo.petsplus.roles.support.SupportPotionUtils.RejectionReason;
import woflo.petsplus.roles.support.SupportPotionUtils.SupportPotionState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.modules.CharacteristicsModule;

class SupportPotionUtilsTest {

    @Test
    void canonicalizeSerializedEffectsSortsEntries() {
        List<String> unsorted = List.of(
            "minecraft:speed|0",
            "minecraft:regeneration|1",
            "minecraft:strength|2"
        );
        List<String> withBlanks = new ArrayList<>();
        withBlanks.add("minecraft:strength|2");
        withBlanks.add("");
        withBlanks.add(null);
        withBlanks.add("minecraft:regeneration|1");

        List<String> canonical = SupportPotionUtils.canonicalizeSerializedEffects(unsorted);
        List<String> canonicalWithBlanks = SupportPotionUtils.canonicalizeSerializedEffects(withBlanks);

        assertEquals(List.of(
            "minecraft:regeneration|1",
            "minecraft:speed|0",
            "minecraft:strength|2"
        ), canonical);
        assertEquals(List.of(
            "minecraft:regeneration|1",
            "minecraft:strength|2"
        ), canonicalWithBlanks);
    }

    @Test
    void harmfulEffectsAreNotAllowedForAura() {
        assertTrue(
            SupportPotionUtils.isAuraEffectCategoryAllowed(StatusEffectCategory.BENEFICIAL),
            "Beneficial effects should be accepted"
        );
        assertTrue(
            SupportPotionUtils.isAuraEffectCategoryAllowed(StatusEffectCategory.NEUTRAL),
            "Neutral effects should be accepted"
        );
        assertFalse(
            SupportPotionUtils.isAuraEffectCategoryAllowed(StatusEffectCategory.HARMFUL),
            "Harmful effects should be rejected"
        );
        assertFalse(
            SupportPotionUtils.isAuraEffectCategoryAllowed(null),
            "Null categories should be rejected"
        );
    }

    @Test
    void hasAnyAllowedAuraCategoryRejectsAllHarmful() {
        assertFalse(
            SupportPotionUtils.hasAnyAllowedAuraCategory(List.of(StatusEffectCategory.HARMFUL)),
            "Potions containing only harmful effects should be rejected"
        );
    }

    @Test
    void hasAnyAllowedAuraCategoryAcceptsMixed() {
        assertTrue(
            SupportPotionUtils.hasAnyAllowedAuraCategory(List.of(StatusEffectCategory.HARMFUL, StatusEffectCategory.BENEFICIAL)),
            "Mixed potions with at least one allowed effect should pass the validation"
        );
    }

    @Test
    void mergePotionStatesAcceptsTopUpForCanonicalizedEffects() {
        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.getLevel()).thenReturn(10);
        Mockito.when(component.getRoleType(false)).thenReturn(PetRoleType.SUPPORT);
        CharacteristicsModule mockCharModule = Mockito.mock(CharacteristicsModule.class);
        Mockito.when(mockCharModule.getImprint()).thenReturn(null);
        Mockito.when(component.getCharacteristicsModule()).thenReturn(mockCharModule);

        List<String> serialized = SupportPotionUtils.canonicalizeSerializedEffects(List.of(
            "minecraft:speed|0",
            "minecraft:regeneration|1"
        ));
        List<String> serializedIncoming = SupportPotionUtils.canonicalizeSerializedEffects(List.of(
            "minecraft:regeneration|1",
            "minecraft:speed|0"
        ));

        SupportPotionState current = new SupportPotionState(
            serialized,
            80,
            new ChargeState(8.0, 3.0, 1.0, 3600)
        );
        SupportPotionState incoming = new SupportPotionState(
            serializedIncoming,
            80,
            new ChargeState(8.0, 8.0, 1.0, 3600)
        );

        MergeOutcome outcome = SupportPotionUtils.mergePotionStates(component, current, incoming, false);

        assertTrue(outcome.accepted(), "Expected matching brews to merge");
        assertTrue(outcome.toppedUp(), "Expected merge to register as a top-up");
        assertEquals(RejectionReason.NONE, outcome.rejectionReason());
        assertTrue(
            outcome.result().chargeState().remaining() > current.chargeState().remaining(),
            "Merged state should gain additional remaining charges"
        );
        assertFalse(outcome.replaced(), "Top-ups should not be treated as replacements");
    }

    @Test
    void mergePotionStatesNormalizesLegacyOrdering() {
        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.getLevel()).thenReturn(12);
        Mockito.when(component.getRoleType(false)).thenReturn(PetRoleType.SUPPORT);
        CharacteristicsModule mockCharModule = Mockito.mock(CharacteristicsModule.class);
        Mockito.when(mockCharModule.getImprint()).thenReturn(null);
        Mockito.when(component.getCharacteristicsModule()).thenReturn(mockCharModule);

        List<String> legacyOrder = List.of(
            "minecraft:strength|1",
            "minecraft:regeneration|0"
        );
        List<String> canonical = SupportPotionUtils.canonicalizeSerializedEffects(List.of(
            "minecraft:regeneration|0",
            "minecraft:strength|1"
        ));

        SupportPotionState current = new SupportPotionState(
            legacyOrder,
            80,
            new ChargeState(8.0, 3.0, 1.0, 3600)
        );
        SupportPotionState incoming = new SupportPotionState(
            canonical,
            80,
            new ChargeState(8.0, 8.0, 1.0, 3600)
        );

        assertEquals(canonical, current.serializedEffects(), "Legacy state should canonicalize on construction");

        MergeOutcome outcome = SupportPotionUtils.mergePotionStates(component, current, incoming, false);

        assertTrue(outcome.accepted(), "Legacy stored potions should accept matching top-ups");
        assertTrue(outcome.toppedUp(), "Expected merge to register as a top-up");
        assertEquals(RejectionReason.NONE, outcome.rejectionReason());
        assertTrue(
            outcome.result().chargeState().remaining() > current.chargeState().remaining(),
            "Merged state should gain additional remaining charges"
        );
    }

    @Test
    void hasAnyAllowedAuraEffectRejectsAllHarmfulPotions() {
        assertFalse(
            SupportPotionUtils.hasAnyAllowedAuraEffect(List.of()),
            "Empty effect lists should be rejected"
        );
        assertFalse(
            SupportPotionUtils.hasAnyAllowedAuraCategory(List.of(StatusEffectCategory.HARMFUL, StatusEffectCategory.HARMFUL)),
            "Potions containing only harmful effects should be rejected"
        );
    }

    @Test
    void hasAnyAllowedAuraEffectAcceptsMixedPotions() {
        assertTrue(
            SupportPotionUtils.hasAnyAllowedAuraCategory(List.of(StatusEffectCategory.NEUTRAL, StatusEffectCategory.BENEFICIAL)),
            "Mixed potions with at least one allowed effect should pass the validation"
        );
    }

    @Test
    void consumptionUsesStoredAbilityMultiplier() {
        PetComponent component = Mockito.mock(PetComponent.class);
        ServerWorld world = Mockito.mock(ServerWorld.class);
        Mockito.when(world.getTime()).thenReturn(120L);

        MobEntity pet = Mockito.mock(MobEntity.class);
        Mockito.when(pet.getEntityWorld()).thenReturn(world);
        Mockito.when(component.getPetEntity()).thenReturn(pet);
        Mockito.when(component.hasRole(PetRoleType.SUPPORT)).thenReturn(true);

        Map<String, Object> state = new HashMap<>();
        state.put(SupportPotionUtils.STATE_PERCH_SIP_MULTIPLIER, 0.65);
        state.put(SupportPotionUtils.STATE_PERCH_SIP_EXPIRY_TICK, 200L);

        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Double.class)))
            .thenAnswer(invocation -> (Double) state.get(invocation.getArgument(0)));
        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Long.class)))
            .thenAnswer(invocation -> (Long) state.get(invocation.getArgument(0)));

        assertEquals(0.65, SupportPotionUtils.getConsumptionPerPulse(component), 1.0E-6,
            "Stored multiplier should drive consumption without requiring config lookups");
    }

    @Test
    void resolvePerchDiscountReadsAbilityState() {
        PlayerEntity owner = Mockito.mock(PlayerEntity.class);
        ServerWorld world = Mockito.mock(ServerWorld.class);
        UUID ownerId = UUID.randomUUID();
        Mockito.when(owner.getEntityWorld()).thenReturn(world);
        Mockito.when(owner.getUuid()).thenReturn(ownerId);
        Mockito.when(world.getTime()).thenReturn(300L);

        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.hasRole(PetRoleType.SUPPORT)).thenReturn(true);
        Mockito.when(component.isOwnedBy(owner)).thenReturn(true);

        Map<String, Object> state = new HashMap<>();
        state.put(SupportPotionUtils.STATE_PERCH_SIP_DISCOUNT, 0.35);
        state.put(SupportPotionUtils.STATE_PERCH_SIP_EXPIRY_TICK, 400L);

        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Double.class)))
            .thenAnswer(invocation -> (Double) state.get(invocation.getArgument(0)));
        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Long.class)))
            .thenAnswer(invocation -> (Long) state.get(invocation.getArgument(0)));

        PetSwarmIndex.SwarmEntry entry = Mockito.mock(PetSwarmIndex.SwarmEntry.class);
        Mockito.when(entry.component()).thenReturn(component);

        PetSwarmIndex index = Mockito.mock(PetSwarmIndex.class);
        Mockito.when(index.snapshotOwner(ownerId)).thenReturn(List.of(entry));

        StateManager manager = Mockito.mock(StateManager.class);
        Mockito.when(manager.getSwarmIndex()).thenReturn(index);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            assertEquals(0.35, SupportPotionUtils.resolvePerchSipDiscount(owner), 1.0E-6,
                "Resolve discount should return the ability-provided value");
        }
    }
}

