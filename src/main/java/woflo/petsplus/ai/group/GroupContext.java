package woflo.petsplus.ai.group;

import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.UUID;

public record GroupContext(
    UUID ownerId,
    List<PetComponent> members
) {
    public GroupContext {
        members = members == null || members.isEmpty() ? List.of() : List.copyOf(members);
    }
}

