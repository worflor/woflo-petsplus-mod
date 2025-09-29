package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import woflo.petsplus.state.processing.OwnerBatchSnapshot.PetSummary;

/**
 * Computes {@link AbilityCooldownPlan} instances from owner batch snapshots.
 * The planner walks the sanitized pet summaries and captures cooldown keys that
 * should expire on the next main-thread pass.
 */
public final class AbilityCooldownPlanner {
    private AbilityCooldownPlanner() {
    }

    public static AbilityCooldownPlan plan(OwnerBatchSnapshot snapshot) {
        if (snapshot == null) {
            return AbilityCooldownPlan.empty();
        }

        List<PetSummary> pets = snapshot.pets();
        if (pets.isEmpty()) {
            return AbilityCooldownPlan.empty();
        }

        long snapshotTick = snapshot.snapshotTick();
        Map<UUID, AbilityCooldownPlan.PetCooldown> entries = new HashMap<>();

        for (PetSummary pet : pets) {
            UUID petId = pet.petUuid();
            if (petId == null) {
                continue;
            }
            Map<String, Long> cooldowns = pet.cooldowns();
            if (cooldowns == null || cooldowns.isEmpty()) {
                continue;
            }
            List<String> expired = null;
            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                Long expiry = entry.getValue();
                if (expiry == null || expiry > snapshotTick) {
                    continue;
                }
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(entry.getKey());
            }
            if (expired != null && !expired.isEmpty()) {
                entries.put(petId, new AbilityCooldownPlan.PetCooldown(petId, expired));
            }
        }

        if (entries.isEmpty()) {
            return AbilityCooldownPlan.empty();
        }

        return new AbilityCooldownPlan(snapshotTick, entries);
    }
}
