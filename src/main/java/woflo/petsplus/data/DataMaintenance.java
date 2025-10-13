package woflo.petsplus.data;

import java.util.Locale;

/**
 * Phase A scaffolding for data maintenance operations.
 * This is a no-op stub providing placeholders for validation and reload flows.
 * To be replaced in a later chunk with real schema checks and safe reload behavior.
 */
public final class DataMaintenance {

    private DataMaintenance() {}

    /**
     * Stubbed validator. Produces a short summary string only.
     * @param what one of ai|abilities|all (normalized by caller; tolerant here)
     * @param output one of chat|file (ignored in stub; no I/O performed)
     * @return summary like "Validate (all): OK (stub)"
     */
    public static String validate(String what, String output) {
        String w = normalizeOrDefault(what, "all");
        // output target is intentionally ignored in Phase A (no I/O performed)

        if ("ai".equals(w)) {
            return "Validate (ai): " + buildAiVerboseSummary();
        } else if ("abilities".equals(w)) {
            return "Validate (abilities): schemaVersion scan pending (stub)";
        } else if ("all".equals(w)) {
            String aiShort = buildAiShortSummary();
            // Combine AI summary + abilities note into one line
            return "Validate (all): ai[" + aiShort + "] | abilities[pending] (stub)";
        }

        // Default fallback (should not normally hit)
        return "Validate (" + w + "): OK (stub)";
    }

    // Phase A helpers: tri-state schemaVersion health for AI signal rules (read-only)
    private static String buildAiVerboseSummary() {
        String moodBlend = formatVerbose("mood_blend", "data/petsplus/ai_signal_rules/mood_blend.json");
        String nature = formatVerbose("nature", "data/petsplus/ai_signal_rules/nature.json");
        return moodBlend + ", " + nature + " (stub)";
    }
 
    private static String buildAiShortSummary() {
        String moodBlend = formatShort("mood_blend", "data/petsplus/ai_signal_rules/mood_blend.json");
        String nature = formatShort("nature", "data/petsplus/ai_signal_rules/nature.json");
        return moodBlend + ", " + nature;
    }

    private static String formatVerbose(String label, String resourcePath) {
        woflo.petsplus.api.registry.RegistryJsonHelper.SchemaVersionHealth h =
            woflo.petsplus.api.registry.RegistryJsonHelper.readSchemaVersionHealthFromResource(resourcePath);
        switch (h) {
            case PRESENT_INTEGER -> {
                java.util.OptionalInt v = woflo.petsplus.api.registry.RegistryJsonHelper.readSchemaVersionFromResource(resourcePath);
                String token = v.isPresent() ? ("present(" + v.getAsInt() + ")") : "present(?)";
                return label + " schemaVersion=" + token;
            }
            case PRESENT_NON_INTEGER -> {
                return label + " schemaVersion=non-integer";
            }
            case ABSENT -> {
                return label + " schemaVersion=absent";
            }
        }
        return label + " schemaVersion=absent";
    }

    private static String formatShort(String label, String resourcePath) {
        woflo.petsplus.api.registry.RegistryJsonHelper.SchemaVersionHealth h =
            woflo.petsplus.api.registry.RegistryJsonHelper.readSchemaVersionHealthFromResource(resourcePath);
        switch (h) {
            case PRESENT_INTEGER -> {
                java.util.OptionalInt v = woflo.petsplus.api.registry.RegistryJsonHelper.readSchemaVersionFromResource(resourcePath);
                String token = v.isPresent() ? ("present(" + v.getAsInt() + ")") : "present(?)";
                return label + "=" + token;
            }
            case PRESENT_NON_INTEGER -> {
                return label + "=non-integer";
            }
            case ABSENT -> {
                return label + "=absent";
            }
        }
        return label + "=absent";
    }

    /**
     * Stubbed reloader. Produces a short summary string only.
     * @param what one of ai|abilities|tags|all (normalized by caller; tolerant here)
     * @param safe if true, implies a "safe" reload (no effect in stub)
     * @return summary like "Reload (all, safe=true): OK (stub)"
     */
    public static String reload(String what, boolean safe) {
        String w = normalizeOrDefault(what, "all");
        String plan = woflo.petsplus.api.registry.RegistryJsonHelper.planSafeReload(w, safe);
        return "Reload (" + w + ", safe=" + safe + "): " + plan;
    }

    private static String normalizeOrDefault(String s, String def) {
        if (s == null) return def;
        String v = s.trim();
        if (v.isEmpty()) return def;
        return v.toLowerCase(Locale.ROOT);
    }
}