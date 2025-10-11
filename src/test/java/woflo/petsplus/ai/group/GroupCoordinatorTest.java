package woflo.petsplus.ai.group;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupCoordinatorTest {

    @Test
    void formsGroupForSharedOwner() {
        PetComponent a = Mockito.mock(PetComponent.class);
        PetComponent b = Mockito.mock(PetComponent.class);
        Mockito.when(a.getOwnerUuid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Mockito.when(b.getOwnerUuid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        GroupCoordinator coordinator = new GroupCoordinator();
        Optional<GroupContext> group = coordinator.formOwnerGroup(List.of(a, b));

        assertTrue(group.isPresent());
        assertEquals(2, group.get().members().size());
    }
}

