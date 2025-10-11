package woflo.petsplus.ai.group;

import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GroupCoordinator {

    public Optional<GroupContext> formOwnerGroup(List<PetComponent> components) {
        if (components == null || components.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, List<PetComponent>> byOwner = new HashMap<>();
        for (PetComponent component : components) {
            if (component == null) {
                continue;
            }
            UUID owner = component.getOwnerUuid();
            if (owner == null) {
                continue;
            }
            byOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(component);
        }
        return byOwner.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> new GroupContext(entry.getKey(), entry.getValue()))
            .findFirst();
    }
}

