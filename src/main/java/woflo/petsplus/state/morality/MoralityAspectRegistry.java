package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global runtime registry for virtue and vice definitions loaded from datapacks.
 */
public final class MoralityAspectRegistry {
    public record Pack(boolean replace, Map<Identifier, MoralityAspectDefinition> aspects) {}

    private static final Map<Identifier, Pack> LOADED = new LinkedHashMap<>();
    private static Map<Identifier, MoralityAspectDefinition> active = Map.of();
    private static Identifier defaultVice = Identifier.of("petsplus", "vice/core");

    private MoralityAspectRegistry() {
    }

    public static synchronized void reload(Map<Identifier, Pack> packs) {
        LOADED.clear();
        if (packs != null) {
            LOADED.putAll(packs);
        }
        recomputeActive();
    }

    private static void recomputeActive() {
        if (LOADED.isEmpty()) {
            active = Map.of();
            defaultVice = Identifier.of("petsplus", "vice/core");
            return;
        }
        Map<Identifier, MoralityAspectDefinition> merged = new LinkedHashMap<>();
        boolean replaceEncountered = false;
        for (Pack pack : LOADED.values()) {
            if (pack == null || pack.aspects() == null) {
                continue;
            }
            if (!replaceEncountered && pack.replace()) {
                merged.clear();
                replaceEncountered = true;
            }
            merged.putAll(pack.aspects());
        }
        if (merged.isEmpty()) {
            active = Map.of();
            defaultVice = Identifier.of("petsplus", "vice/core");
            return;
        }
        active = Collections.unmodifiableMap(merged);
        defaultVice = active.values().stream()
            .filter(MoralityAspectDefinition::isVice)
            .map(MoralityAspectDefinition::id)
            .findFirst()
            .orElse(Identifier.of("petsplus", "vice/core"));
    }

    public static Collection<MoralityAspectDefinition> all() {
        return active.values();
    }

    public static Set<Identifier> keys() {
        return active.keySet();
    }

    @Nullable
    public static MoralityAspectDefinition get(Identifier id) {
        return active.get(id);
    }

    public static boolean isVirtue(Identifier id) {
        MoralityAspectDefinition def = active.get(id);
        return def != null && def.isVirtue();
    }

    public static boolean isVice(Identifier id) {
        MoralityAspectDefinition def = active.get(id);
        return def != null && def.isVice();
    }

    public static Identifier defaultViceAspectId() {
        return defaultVice;
    }
}
