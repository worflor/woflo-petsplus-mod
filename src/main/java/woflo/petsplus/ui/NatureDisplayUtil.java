package woflo.petsplus.ui;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

/**
 * Shared helpers for formatting nature identifiers for UI presentation.
 */
public final class NatureDisplayUtil {

    private static final String DEFAULT_UNKNOWN = "Unknown";

    private NatureDisplayUtil() {
    }

    /**
     * Formats a nature name without truncation.
     */
    public static String formatNatureName(@Nullable PetComponent component, @Nullable Identifier natureId) {
        if (natureId == null) {
            return DEFAULT_UNKNOWN;
        }

        if (AstrologyRegistry.LUNARIS_NATURE_ID.equals(natureId)) {
            Identifier signId = component != null ? component.getAstrologySignId() : null;
            String displayTitle = AstrologyRegistry.getDisplayTitle(signId);
            if (displayTitle != null && !displayTitle.isBlank()) {
                return displayTitle;
            }
        }

        String path = natureId.getPath();
        if (path.isEmpty()) {
            return DEFAULT_UNKNOWN;
        }

        String normalized = path.replace('/', ' ').replace('_', ' ');
        if (normalized.isEmpty()) {
            return DEFAULT_UNKNOWN;
        }

        if (normalized.length() == 1) {
            return normalized.toUpperCase();
        }

        char first = Character.toUpperCase(normalized.charAt(0));
        String rest = normalized.substring(1).toLowerCase();
        return first + rest;
    }

    /**
     * Formats a nature name and truncates the result when it exceeds {@code maxLength}.
     */
    public static String formatNatureName(@Nullable PetComponent component, @Nullable Identifier natureId, int maxLength) {
        String base = formatNatureName(component, natureId);
        if (maxLength <= 0) {
            return base;
        }
        if (base.length() <= maxLength) {
            return base;
        }
        // Hard trim without adding ellipses for consistent line rhythm
        return base.substring(0, Math.max(0, maxLength));
    }
}
