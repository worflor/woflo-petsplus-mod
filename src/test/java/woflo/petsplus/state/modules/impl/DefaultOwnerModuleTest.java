package woflo.petsplus.state.modules.impl;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class DefaultOwnerModuleTest {

    private DefaultOwnerModule module;
    private PetComponent parent;
    private ServerWorld serverWorld;

    @BeforeEach
    void setUp() {
        module = new DefaultOwnerModule();
        parent = mock(PetComponent.class);
        serverWorld = mock(ServerWorld.class);
        module.onAttach(parent);
    }

    @Test
    void setOwnerUuidDoesNotClearCacheOrNotifyWhenUnchanged() {
        UUID ownerUuid = UUID.randomUUID();
        PlayerEntity owner = mock(PlayerEntity.class);
        when(owner.getUuid()).thenReturn(ownerUuid);
        when(owner.isRemoved()).thenReturn(false);

        module.setOwner(owner);

        module.setOwnerUuid(ownerUuid);

        verify(parent, times(1)).onOwnerChanged(null, ownerUuid);
        verifyNoMoreInteractions(parent);

        assertSame(owner, module.getOwner(serverWorld));
    }

    @Test
    void setOwnerUuidClearsCacheAndNotifiesWhenChanged() {
        UUID firstOwnerUuid = UUID.randomUUID();
        PlayerEntity firstOwner = mock(PlayerEntity.class);
        when(firstOwner.getUuid()).thenReturn(firstOwnerUuid);
        when(firstOwner.isRemoved()).thenReturn(false);

        module.setOwner(firstOwner);

        UUID secondOwnerUuid = UUID.randomUUID();
        PlayerEntity secondOwner = mock(PlayerEntity.class);
        when(serverWorld.getPlayerByUuid(secondOwnerUuid)).thenReturn(secondOwner);
        when(secondOwner.isRemoved()).thenReturn(false);

        module.setOwnerUuid(secondOwnerUuid);

        InOrder order = inOrder(parent);
        order.verify(parent).onOwnerChanged(null, firstOwnerUuid);
        order.verify(parent).onOwnerChanged(firstOwnerUuid, secondOwnerUuid);
        order.verifyNoMoreInteractions();

        assertSame(secondOwner, module.getOwner(serverWorld));
    }
}
