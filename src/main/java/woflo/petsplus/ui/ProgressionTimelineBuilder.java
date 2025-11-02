package woflo.petsplus.ui;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.LevelReward;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.api.rewards.AbilityUnlockReward;
import woflo.petsplus.api.rewards.StatBoostReward;
import woflo.petsplus.api.rewards.TributeRequiredReward;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a progression timeline from role level rewards.
 * Transforms raw LevelReward objects into display-ready timeline entries.
 */
public final class ProgressionTimelineBuilder {
    private ProgressionTimelineBuilder() {
    }

    /**
     * Builds a complete progression timeline for a pet's role.
     * Categorizes rewards into past (already unlocked), current (this level), and future.
     */
    public static List<TimelineEntry> buildTimeline(PetComponent pc, PetRoleType roleType) {
        if (pc == null || roleType == null) {
            return List.of();
        }

        int currentLevel = pc.getLevel();
        Map<Integer, List<LevelReward>> allRewards = roleType.definition().levelRewards();
        
        if (allRewards.isEmpty()) {
            return List.of();
        }

        Set<Integer> unlockedMilestones = pc.getProgressionModule().getUnlockedMilestones();
        List<TimelineEntry> timeline = new ArrayList<>();

        // Sort levels numerically
        List<Integer> sortedLevels = new ArrayList<>(allRewards.keySet());
        Collections.sort(sortedLevels);

        for (Integer level : sortedLevels) {
            TimelineState state;
            if (level < currentLevel) {
                state = TimelineState.PAST;
            } else if (level == currentLevel) {
                state = TimelineState.CURRENT;
            } else {
                state = TimelineState.FUTURE;
            }

            List<LevelReward> rewards = allRewards.get(level);
            List<String> displayLines = new ArrayList<>();

            // Format each reward for display
            for (LevelReward reward : rewards) {
                String displayText = formatRewardForDisplay(reward);
                if (displayText != null && !displayText.isBlank()) {
                    displayLines.add(displayText);
                }
            }

            // Check if this level has a tribute requirement
            boolean requiresTribute = rewards.stream()
                .anyMatch(r -> r instanceof TributeRequiredReward);
            boolean tributePaid = unlockedMilestones.contains(level);

            timeline.add(new TimelineEntry(level, state, requiresTribute, tributePaid, displayLines));
        }

        return timeline;
    }

    /**
     * Formats a single reward into human-readable display text.
     */
    private static String formatRewardForDisplay(LevelReward reward) {
        if (reward instanceof AbilityUnlockReward ar) {
            return formatAbilityDisplayName(ar.getAbilityId());
        } else if (reward instanceof StatBoostReward sr) {
            return "+" + formatNumber(sr.getAmount()) + " " + formatStatDisplayName(sr.getStat());
        } else if (reward instanceof TributeRequiredReward) {
            return null; // Handled separately as state flag
        }
        return null;
    }

    /**
     * Gets a human-readable name for an ability from its ID.
     */
    private static String formatAbilityDisplayName(Identifier abilityId) {
        if (abilityId == null) {
            return "Unknown Ability";
        }

        // Try to get from registry description first
        try {
            AbilityType abilityType = PetsPlusRegistries.abilityTypeRegistry().get(abilityId);
            if (abilityType != null && abilityType.description() != null && !abilityType.description().isBlank()) {
                return abilityType.description();
            }
        } catch (Exception e) {
            // Fall through to ID formatting
        }

        // Format ID path: "fortress_bond" → "Fortress Bond"
        return formatIdentifierPath(abilityId.getPath());
    }

    /**
     * Formats stat name for display.
     */
    private static String formatStatDisplayName(String stat) {
        if (stat == null || stat.isBlank()) {
            return "Unknown";
        }

        return switch (stat.toLowerCase(Locale.ROOT)) {
            case "health" -> "Health";
            case "attack" -> "Attack";
            case "defense" -> "Defense";
            case "speed" -> "Speed";
            case "vitality" -> "Vitality";
            case "might" -> "Might";
            case "guard" -> "Guard";
            case "focus" -> "Focus";
            case "agility" -> "Agility";
            default -> formatIdentifierPath(stat);
        };
    }

    /**
     * Converts snake_case ID path to Title Case.
     * "fortress_bond" → "Fortress Bond"
     */
    private static String formatIdentifierPath(String path) {
        if (path == null || path.isBlank()) {
            return "Unknown";
        }

        String[] words = path.split("_");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }
            String word = words[i];
            if (!word.isBlank()) {
                builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                       .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return builder.toString();
    }

    /**
     * Formats a float number, removing unnecessary trailing zeros.
     * 4.0 → "4", 2.5 → "2.5"
     */
    private static String formatNumber(float value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    /**
     * Represents a single level in the progression timeline.
     */
    public record TimelineEntry(
        int level,
        TimelineState state,
        boolean requiresTribute,
        boolean tributePaid,
        List<String> unlocks
    ) {
    }

    /**
     * Categorizes timeline entries by progression stage.
     */
    public enum TimelineState {
        PAST,
        CURRENT,
        FUTURE
    }
}
