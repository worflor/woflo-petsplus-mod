package woflo.petsplus.api.registry;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;

import java.util.IllegalFormatException;
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

    public static Text roleLabel(Identifier roleId, @Nullable PetRoleType roleType) {
        if (roleType != null) {
            Text translated = Text.translatable(roleType.translationKey());
            if (hasTranslation(translated, roleType.translationKey())) {
                return translated;
            }
        }

        return Text.literal(formatName(roleId));
    }

    public static String roleDescription(Identifier roleId, @Nullable PetRoleType roleType) {
        if (roleType != null) {
            String description = resolveMessageString(roleType.presentation().adminSummary(), formatName(roleId));
            if (!description.isBlank()) {
                return description;
            }
        }

        return formatName(roleId);
    }

    public static Text resolveMessageText(@Nullable PetRoleType.Message message, Object... args) {
        return resolveMessageText(message, null, args);
    }

    public static Text resolveMessageText(@Nullable PetRoleType.Message message, @Nullable String fallbackLiteral, Object... args) {
        if (message != null && message.isPresent()) {
            String translationKey = message.translationKey();
            if (translationKey != null && !translationKey.isBlank()) {
                Text translated = Text.translatable(translationKey, args);
                if (hasTranslation(translated, translationKey)) {
                    return translated;
                }
            }

            String messageFallback = message.fallback();
            if (messageFallback != null && !messageFallback.isBlank()) {
                return Text.literal(formatFallback(messageFallback, args));
            }

            if (translationKey != null && !translationKey.isBlank()) {
                return Text.translatable(translationKey, args);
            }
        }

        if (fallbackLiteral != null && !fallbackLiteral.isBlank()) {
            return Text.literal(formatFallback(fallbackLiteral, args));
        }

        return null;
    }

    public static String resolveMessageString(@Nullable PetRoleType.Message message, @Nullable String fallback, Object... args) {
        if (message != null && message.isPresent()) {
            String translationKey = message.translationKey();
            if (translationKey != null && !translationKey.isBlank()) {
                Text translated = Text.translatable(translationKey, args);
                if (hasTranslation(translated, translationKey)) {
                    return translated.getString();
                }
            }

            String messageFallback = message.fallback();
            if (messageFallback != null && !messageFallback.isBlank()) {
                return formatFallback(messageFallback, args);
            }

            if (translationKey != null && !translationKey.isBlank()) {
                return Text.translatable(translationKey, args).getString();
            }
        }

        if (fallback != null && !fallback.isBlank()) {
            return formatFallback(fallback, args);
        }

        return "";
    }

    private static boolean hasTranslation(Text text, String translationKey) {
        return !text.getString().equals(translationKey);
    }

    private static String formatFallback(String fallback, Object... args) {
        if (fallback == null) {
            return "";
        }

        if (args == null || args.length == 0) {
            return fallback;
        }

        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Text textArg) {
                sanitized[i] = textArg.getString();
            } else {
                sanitized[i] = arg;
            }
        }

        try {
            return String.format(fallback, sanitized);
        } catch (IllegalFormatException ignored) {
            return fallback;
        }
    }
}
