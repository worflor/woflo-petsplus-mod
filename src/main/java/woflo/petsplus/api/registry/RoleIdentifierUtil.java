package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.Locale;

/**
 * Utility methods for working with role identifiers.
 * Centralizes parsing and formatting logic.
 */
public final class RoleIdentifierUtil {
    private RoleIdentifierUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parse a string into a role identifier, trying multiple formats.
     * Attempts:
     * 1. Parse as-is (e.g., "petsplus:guardian")
     * 2. Parse with mod namespace (e.g., "guardian" -> "petsplus:guardian")
     * 3. Normalize and retry (e.g., "GUARDIAN" -> "petsplus:guardian")
     */
    public static Identifier parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        
        // Try as-is
        Identifier id = Identifier.tryParse(trimmed);
        if (id != null) {
            return id;
        }

        // Try with mod namespace
        id = Identifier.tryParse(Petsplus.MOD_ID + ":" + trimmed);
        if (id != null) {
            return id;
        }

        // Try normalized (lowercase, replace spaces/dashes with underscores)
        String normalized = trimmed
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_');
        
        id = Identifier.tryParse(normalized);
        if (id != null) {
            return id;
        }

        // Try normalized with namespace
        return Identifier.tryParse(Petsplus.MOD_ID + ":" + normalized);
    }

    /**
     * Format an identifier path as "Title Case" for display.
     * E.g., "enchantment_bound" -> "Enchantment Bound"
     */
    public static String formatName(Identifier id) {
        if (id == null) {
            return "";
        }

        String path = id.getPath();
        String[] parts = path.split("[_:]");
        StringBuilder result = new StringBuilder();
        
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        
        return result.length() == 0 ? path : result.toString();
    }
}
