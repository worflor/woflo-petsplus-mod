package woflo.petsplus.state;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, short‑lived memory to preserve a pet's role across
 * temporary despawns such as shoulder perching for parrots.
 *
 * <p>When a parrot perches on a player's shoulder, vanilla removes the
 * mob entity and later respawns it when unperching. That cycle can lose
 * Pets+ component state in some edge paths. This cache remembers the
 * last known role by pet UUID and applies it on the next load if the
 * component did not carry a role.</p>
 */
public final class PetRoleMemory {
    private PetRoleMemory() {}

    private static final class Entry {
        final Identifier roleId;
        final long expiresAtMillis;
        Entry(Identifier roleId, long expiresAtMillis) {
            this.roleId = roleId;
            this.expiresAtMillis = expiresAtMillis;
        }
        boolean expired(long now) { return now >= expiresAtMillis; }
    }

    // Keep entries for a generous window (24h) so that player logouts
    // while parrots are perched do not lose roles on next login.
    private static final long TTL_MILLIS = 24L * 60L * 60L * 1000L;
    private static final Map<UUID, Entry> LAST_ROLES = new ConcurrentHashMap<>();

    public static void remember(UUID petId, Identifier roleId) {
        if (petId == null || roleId == null) return;
        long now = System.currentTimeMillis();
        LAST_ROLES.put(petId, new Entry(roleId, now + TTL_MILLIS));
    }

    /**
     * Return the remembered role for the UUID if present and not expired,
     * removing the entry once read. Returns null when absent/expired.
     */
    public static Identifier recall(UUID petId) {
        if (petId == null) return null;
        long now = System.currentTimeMillis();
        Entry e = LAST_ROLES.remove(petId);
        if (e == null || e.expired(now)) return null;
        return e.roleId;
    }

    /**
     * Prune any stale entries. Safe to call opportunistically.
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        LAST_ROLES.entrySet().removeIf(en -> en.getValue() == null || en.getValue().expired(now));
    }
}
