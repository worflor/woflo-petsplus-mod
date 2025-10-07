package woflo.petsplus.ai.capability;

import net.minecraft.entity.mob.MobEntity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class MobCapabilitiesTest {

    @Test
    void hasOwnerReturnsTrueWhenPetsplusOwnerIsOffline() {
        MobEntity mob = mock(MobEntity.class, withSettings().extraInterfaces(PetsplusTameable.class));
        PetsplusTameable tameable = (PetsplusTameable) mob;
        UUID ownerUuid = UUID.randomUUID();

        when(tameable.petsplus$isTamed()).thenReturn(true);
        when(tameable.petsplus$getOwnerUuid()).thenReturn(ownerUuid);
        when(tameable.petsplus$getOwner()).thenReturn(null);

        assertTrue(MobCapabilities.hasOwner(mob));
    }

    @Test
    void hasOwnerReturnsTrueWhenComponentStoresOwner() {
        MobEntity mob = mock(MobEntity.class, withSettings().extraInterfaces(PetsplusTameable.class));
        PetsplusTameable petsplus = (PetsplusTameable) mob;
        UUID ownerUuid = UUID.randomUUID();

        when(petsplus.petsplus$isTamed()).thenReturn(true);
        when(petsplus.petsplus$getOwnerUuid()).thenReturn(null);

        PetComponent component = mock(PetComponent.class);
        when(component.getOwnerUuid()).thenReturn(ownerUuid);

        try (MockedStatic<PetComponent> components = Mockito.mockStatic(PetComponent.class)) {
            components.when(() -> PetComponent.get(mob)).thenReturn(component);

            assertTrue(MobCapabilities.hasOwner(mob));
        }
    }

    @Test
    void hasOwnerReturnsFalseWhenNotTamed() {
        MobEntity mob = mock(MobEntity.class, withSettings().extraInterfaces(PetsplusTameable.class));
        PetsplusTameable tameable = (PetsplusTameable) mob;

        when(tameable.petsplus$isTamed()).thenReturn(false);
        when(tameable.petsplus$getOwnerUuid()).thenReturn(null);

        try (MockedStatic<PetComponent> components = Mockito.mockStatic(PetComponent.class)) {
            components.when(() -> PetComponent.get(mob)).thenReturn(null);

            assertFalse(MobCapabilities.hasOwner(mob));
        }
    }
}
