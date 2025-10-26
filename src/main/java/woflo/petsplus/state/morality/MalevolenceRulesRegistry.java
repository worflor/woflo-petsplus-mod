package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maintains the authoritative malevolence rule set loaded from datapacks.
 */
public final class MalevolenceRulesRegistry {
    private static final Map<Identifier, MalevolenceRules> LOADED = new LinkedHashMap<>();
    private static MalevolenceRules active = MalevolenceRules.EMPTY;

    private MalevolenceRulesRegistry() {
    }

    public static MalevolenceRules get() {
        return active;
    }

    public static void reload(Map<Identifier, MalevolenceRules> rulesById) {
        LOADED.clear();
        if (rulesById != null) {
            LOADED.putAll(rulesById);
        }
        recomputeActive();
    }

    private static void recomputeActive() {
        if (LOADED.isEmpty()) {
            active = MalevolenceRules.EMPTY;
            return;
        }
        MalevolenceRules merged = MalevolenceRules.EMPTY;
        for (Map.Entry<Identifier, MalevolenceRules> entry : LOADED.entrySet()) {
            MalevolenceRules rules = entry.getValue();
            if (rules == null) {
                continue;
            }
            if (rules.isReplace()) {
                merged = rules;
            } else {
                merged = merged.overlay(rules);
            }
        }
        active = merged;
    }
}
