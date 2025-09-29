package woflo.petsplus.state.processing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.state.PetComponent;

/**
 * Describes cooldown expirations that were detected while preparing an owner
 * batch off-thread. The plan only carries the minimal information required to
 * apply the expirations safely on the main thread.
 */
public final class AbilityCooldownPlan {
    private static final AbilityCooldownPlan EMPTY = new AbilityCooldownPlan(0L, Map.of());

    private final long snapshotTick;
    private final Map<UUID, PetCooldown> entries;

    AbilityCooldownPlan(long snapshotTick, Map<UUID, PetCooldown> entries) {
        this.snapshotTick = Math.max(0L, snapshotTick);
        this.entries = entries == null || entries.isEmpty() ? Map.of() : Map.copyOf(entries);
    }

    public static AbilityCooldownPlan empty() {
        return EMPTY;
    }

    public long snapshotTick() {
        return snapshotTick;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Nullable
    public PetCooldown planFor(@Nullable UUID petId) {
        if (petId == null) {
            return null;
        }
        return entries.get(petId);
    }

    public Map<UUID, PetCooldown> entries() {
        return entries;
    }

    /**
     * Applies the cooldown expirations for the supplied component. The caller
     * is responsible for invoking this on the main thread.
     *
     * @return {@code true} when expirations were applied, {@code false} if no
     *         plan was available for the component
     */
    public boolean applyTo(PetComponent component, long currentTick) {
        if (component == null || entries.isEmpty()) {
            return false;
        }
        UUID petId = OptionalUuidResolver.resolve(component);
        if (petId == null) {
            return false;
        }
        PetCooldown entry = entries.get(petId);
        if (entry == null || entry.expiredKeys.isEmpty()) {
            return false;
        }
        component.applyCooldownExpirations(entry.expiredKeys, currentTick);
        return true;
    }

    /**
     * Immutable description of the cooldown keys that should expire for a
     * specific pet.
     */
    public static final class PetCooldown {
        private final UUID petId;
        private final List<String> expiredKeys;

        PetCooldown(UUID petId, List<String> expiredKeys) {
            this.petId = Objects.requireNonNull(petId, "petId");
            this.expiredKeys = expiredKeys == null || expiredKeys.isEmpty()
                ? List.of()
                : List.copyOf(expiredKeys);
        }

        public UUID petId() {
            return petId;
        }

        public List<String> expiredKeys() {
            return expiredKeys;
        }
    }

    private static final class OptionalUuidResolver {
        private static UUID resolve(PetComponent component) {
            if (component == null) {
                return null;
            }
            var pet = component.getPetEntity();
            return pet == null ? null : pet.getUuid();
        }
    }
}
