package woflo.petsplus.ui;

import woflo.petsplus.api.registry.RoleIdentifierUtil;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetImprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts and formats pet data for display in the Pet Compendium.
 * Follows the same formatting conventions as ProofOfExistence for UI coherence.
 */
public class PetCompendiumDataExtractor {
    
    private static final int TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_HOUR = 1000;
    private static final float MODIFIER_THRESHOLD = 0.01f;
    
    /**
     * Build page 1: General life statistics
     */
    public static List<Text> buildGeneralStatsPage(MobEntity pet, PetComponent pc, long currentTick) {
        List<Text> lines = new ArrayList<>();
        
        // Title
        lines.add(Text.literal("§f§lPet Compendium§r"));
        lines.add(Text.literal("§8─────────────────"));
        lines.add(Text.empty());
        
        // Name
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
            pet.getType().getName().getString();
        lines.add(Text.literal("§7Name:"));
        lines.add(Text.literal("  §f" + petName));
        lines.add(Text.empty());
        
        // Level & XP
        int level = pc.getLevel();
        int xp = pc.getExperience();
        int xpProgress = Math.round(pc.getXpProgress() * 100);
        
        lines.add(Text.literal("§7Level: §f" + level + " §8(§7" + xpProgress + "% to next§8)"));
        lines.add(Text.literal("§7Experience: §f" + xp));
        lines.add(Text.empty());
        
        // Health
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        String healthColor = getHealthColor(health / maxHealth);
        
        lines.add(Text.literal("§7Health:"));
        lines.add(Text.literal("  " + healthColor + String.format("%.1f", health) + 
            " §8/ §f" + String.format("%.1f", maxHealth)));
        lines.add(Text.empty());
        
        // Lifespan
        String lifespan = formatLifespan(pc, currentTick);
        if (lifespan != null) {
            lines.add(Text.literal("§7Lifespan:"));
            lines.add(Text.literal("  §f" + lifespan));
            lines.add(Text.empty());
        }
        
        // Bond strength (if available)
        long bondStrength = pc.getBondStrength();
        if (bondStrength > 0) {
            String bondLevel = getBondLevel(bondStrength);
            lines.add(Text.literal("§7Bond: §d" + bondLevel));
            lines.add(Text.literal("  §8(" + bondStrength + " strength)"));
        }
        
        return lines;
    }
    
    /**
     * Build page 2: Role, nature, and characteristics
     */
    public static List<Text> buildCharacteristicsPage(MobEntity pet, PetComponent pc) {
        List<Text> lines = new ArrayList<>();
        
        lines.add(Text.literal("§7═══ Characteristics ═══"));
        lines.add(Text.empty());
        
        // Role
        Identifier roleId = pc.getRoleId();
        PetRoleType roleType = null;
        String roleName = "Unknown";
        
        try {
            if (roleId != null) {
                roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
                roleName = getRoleDisplayName(roleId, roleType);
            }
        } catch (Exception e) {
            // Log error and use fallback
            System.err.println("Error retrieving role type for ID " + roleId + ": " + e.getMessage());
        }
        
        lines.add(Text.literal("§7Role: §f" + roleName));
        lines.add(Text.empty());
        
        // Nature (single nature per pet)
        Identifier natureId = pc.getNatureId();
        if (natureId != null) {
            try {
                String natureName = NatureDisplayUtil.formatNatureName(pc, natureId);
                lines.add(Text.literal("§7Nature: §b" + natureName));
            } catch (Exception e) {
                System.err.println("Error formatting nature name for ID " + natureId + ": " + e.getMessage());
                lines.add(Text.literal("§7Nature: §bUnknown"));
            }
            lines.add(Text.empty());
        }
        
        // Imprint/Traits
        PetImprint imprint = pc.getImprint();
        if (imprint != null) {
            try {
                float vitality = imprint.getVitalityMultiplier() - 1.0f; // Convert 1.08x → 0.08
                float might = imprint.getMightMultiplier() - 1.0f;
                float guard = imprint.getGuardMultiplier() - 1.0f;
                
                boolean hasNotableTraits = Math.abs(vitality) > MODIFIER_THRESHOLD ||
                                          Math.abs(might) > MODIFIER_THRESHOLD ||
                                          Math.abs(guard) > MODIFIER_THRESHOLD;
                
                if (hasNotableTraits) {
                    lines.add(Text.literal("§7Traits:"));
                    
                    if (Math.abs(vitality) > MODIFIER_THRESHOLD) {
                        String desc = vitality > 0 ? "Vigorous" : "Delicate";
                        lines.add(Text.literal("  §8" + desc + ": §f" + formatModifier(vitality)));
                    }
                    if (Math.abs(might) > MODIFIER_THRESHOLD) {
                        String desc = might > 0 ? "Fierce" : "Gentle";
                        lines.add(Text.literal("  §8" + desc + ": §f" + formatModifier(might)));
                    }
                    if (Math.abs(guard) > MODIFIER_THRESHOLD) {
                        String desc = guard > 0 ? "Stalwart" : "Brittle";
                        lines.add(Text.literal("  §8" + desc + ": §f" + formatModifier(guard)));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error extracting imprint data: " + e.getMessage());
                lines.add(Text.literal("§7Traits: §8Unable to retrieve"));
            }
        }
        
        return lines;
    }
    
    /**
     * Build page 3: Combat and achievement statistics
     */
    public static List<Text> buildCombatStatsPage(PetComponent pc) {
        List<Text> lines = new ArrayList<>();
        
        lines.add(Text.literal("§7═══ Combat Record ═══"));
        lines.add(Text.empty());
        
        // Count combat events from history
        List<HistoryEvent> history = pc.getHistory();
        int victories = 0;
        int defeats = 0;
        
        for (HistoryEvent event : history) {
            if (HistoryEvent.EventType.COMBAT.equals(event.eventType())) {
                if (event.eventData().contains("\"result\":\"victory\"")) {
                    victories++;
                } else if (event.eventData().contains("\"result\":\"defeat\"")) {
                    defeats++;
                }
            }
        }
        
        lines.add(Text.literal("§7Victories: §f" + victories));
        lines.add(Text.literal("§7Defeats: §f" + defeats));
        lines.add(Text.empty());
        
        // Special achievements based on role
        Identifier roleId = pc.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        
        if (roleType != null) {
            List<Text> specialStats = extractRoleSpecificStats(pc, roleType);
            if (!specialStats.isEmpty()) {
                lines.add(Text.literal("§7Special Feats:"));
                lines.addAll(specialStats);
            }
        }
        
        return lines;
    }
    
    /**
     * Build journal pages from history events (recent first)
     */
    public static List<List<Text>> buildJournalPages(PetComponent pc, long currentTick, int maxEvents) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();
        
        final String journalHeader = "§7═══ Recent Journal ═══";
        currentPage.add(Text.literal(journalHeader));
        currentPage.add(Text.empty());
        
        // Get history events in reverse order (most recent first)
        List<HistoryEvent> history = new ArrayList<>(pc.getHistory());
        Collections.reverse(history);
        
        // Filter and format significant events
        List<HistoryEvent> significantEvents = selectSignificantEvents(history, maxEvents);
        
        int linesOnPage = 2; // Already have title and empty line
        final int MAX_LINES_PER_PAGE = 14; // Typical book page capacity
        
        for (HistoryEvent event : significantEvents) {
            List<Text> eventLines = formatHistoryEvent(event, currentTick);

            // Check if we need a new page (include spacer line)
            if (linesOnPage + eventLines.size() + 1 > MAX_LINES_PER_PAGE && !currentPage.isEmpty()) {
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                currentPage.add(Text.literal(journalHeader));
                currentPage.add(Text.empty());
                linesOnPage = 2;
            }
            
            currentPage.addAll(eventLines);
            currentPage.add(Text.empty()); // Spacing between events
            linesOnPage += eventLines.size() + 1;
        }
        
        if (significantEvents.isEmpty()) {
            currentPage.add(Text.literal("§8No recorded events yet."));
            currentPage.add(Text.literal("§8Adventures await!"));
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        return pages;
    }
    
    // === HELPER METHODS ===
    
    public static String getRoleDisplayName(Identifier roleId, @Nullable PetRoleType roleType) {
        if (roleId == null) {
            return "";
        }

        String label = RoleIdentifierUtil.roleLabel(roleId, roleType).getString();
        if (label.isBlank()) {
            return RoleIdentifierUtil.formatName(roleId);
        }
        return label;
    }
    
    private static String getHealthColor(float healthPercent) {
        if (healthPercent <= 0.2f) return "§c"; // Red
        if (healthPercent <= 0.5f) return "§e"; // Yellow
        if (healthPercent >= 0.9f) return "§a"; // Green
        return "§f"; // White
    }
    
    private static String formatModifier(float modifier) {
        int percent = Math.round(modifier * 100);
        String sign = percent > 0 ? "+" : "";
        return sign + percent + "%";
    }
    
    public static String getBondLevel(long bondStrength) {
        if (bondStrength >= 10000) return "Unbreakable";
        if (bondStrength >= 5000) return "Legendary";
        if (bondStrength >= 2500) return "Deep";
        if (bondStrength >= 1000) return "Strong";
        if (bondStrength >= 500) return "Growing";
        return "Budding";
    }
    
    @Nullable
    private static String formatLifespan(PetComponent pc, long currentTick) {
        List<HistoryEvent> history = pc.getHistory();
        long tamedTick = -1;
        
        // Find when the pet was first tamed
        for (HistoryEvent event : history) {
            if (event.eventType().equals(HistoryEvent.EventType.OWNERSHIP_START)) {
                tamedTick = event.timestamp();
                break;
            }
        }
        
        if (tamedTick < 0) return null;
        
        long ticksAlive = currentTick - tamedTick;
        int days = (int) (ticksAlive / TICKS_PER_DAY);
        long remainingTicks = ticksAlive % TICKS_PER_DAY;
        int hours = (int) (remainingTicks / TICKS_PER_HOUR);
        
        if (days > 0) {
            return days + " day" + (days != 1 ? "s" : "") + 
                   (hours > 0 ? ", " + hours + " hour" + (hours != 1 ? "s" : "") : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else {
            return "Less than an hour";
        }
    }
    
    private static List<Text> extractRoleSpecificStats(PetComponent pc, PetRoleType roleType) {
        List<Text> stats = new ArrayList<>();
        List<HistoryEvent> history = pc.getHistory();
        
        String roleName = roleType.id().getPath().toLowerCase();
        
        // Guardian: damage redirected
        if (roleName.contains("guardian")) {
            double totalDamage = 0;
            int protectionCount = 0;
            for (HistoryEvent event : history) {
                try {
                    if (event != null && event.eventType() != null &&
                        HistoryEvent.EventType.ACHIEVEMENT.equals(event.eventType()) &&
                        event.eventData() != null &&
                        event.eventData().contains("\"achievement_type\":\"" + HistoryEvent.AchievementType.GUARDIAN_PROTECTION + "\"")) {
                        protectionCount++;
                        // Extract damage value from JSON
                        String data = event.eventData();
                        int damageIdx = data.indexOf("\"damage\":");
                        if (damageIdx != -1) {
                            try {
                                int start = damageIdx + 9;
                                int end = data.indexOf(',', start);
                                if (end == -1) end = data.indexOf('}', start);
                                if (end > start) {
                                    String damageStr = data.substring(start, end).trim();
                                    totalDamage += Double.parseDouble(damageStr);
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("Error parsing damage value: " + e.getMessage());
                            } catch (StringIndexOutOfBoundsException e) {
                                System.err.println("Error extracting damage substring: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing guardian protection event: " + e.getMessage());
                }
            }
            if (totalDamage > 0) {
                stats.add(Text.literal("  §8- §fProtected owner §e" + protectionCount + "§f times"));
                stats.add(Text.literal("  §8- §fRedirected §e" + String.format("%.1f", totalDamage) + "§f damage"));
            }
        }
        
        // Support: healing done (count healing events)
        if (roleName.contains("support")) {
            int healingEvents = 0;
            for (HistoryEvent event : history) {
                if (HistoryEvent.EventType.ACHIEVEMENT.equals(event.eventType()) &&
                    event.eventData() != null &&
                    event.eventData().contains("\"achievement_type\":\"" + HistoryEvent.AchievementType.ALLY_HEALED + "\"")) {
                    healingEvents++;
                }
            }
            if (healingEvents > 0) {
                stats.add(Text.literal("  §8- §fHealed allies §e" + healingEvents + "§f times"));
            }
        }
        
        // Add milestone levels achieved
        int level = pc.getLevel();
        if (level >= 10) {
            List<String> milestones = new ArrayList<>();
            if (pc.isMilestoneUnlocked(10)) milestones.add("10");
            if (pc.isMilestoneUnlocked(20)) milestones.add("20");
            if (pc.isMilestoneUnlocked(30)) milestones.add("30");
            
            if (!milestones.isEmpty()) {
                stats.add(Text.literal("  §8- §fMilestones: §e" + String.join(", ", milestones)));
            }
        }
        
        return stats;
    }
    
    private static List<HistoryEvent> selectSignificantEvents(List<HistoryEvent> allEvents, int maxEvents) {
        List<HistoryEvent> significant = new ArrayList<>();

        if (maxEvents <= 0) {
            return significant;
        }

        for (HistoryEvent event : allEvents) {
            String type = event.eventType();
            if (HistoryEvent.EventType.ACHIEVEMENT.equals(type) ||
                HistoryEvent.EventType.LEVEL_UP.equals(type) ||
                HistoryEvent.EventType.ROLE_CHANGE.equals(type) ||
                HistoryEvent.EventType.TRADE.equals(type) ||
                HistoryEvent.EventType.COMBAT.equals(type) ||
                HistoryEvent.EventType.MOOD_MILESTONE.equals(type)) {
                significant.add(event);
                if (significant.size() >= maxEvents) {
                    break;
                }
            }
        }

        return significant;
    }
    
    private static List<Text> formatHistoryEvent(HistoryEvent event, long currentTick) {
        List<Text> lines = new ArrayList<>();
        
        // Format timestamp
        long eventAge = currentTick - event.timestamp();
        String timeStr = formatEventAge(eventAge);
        
        lines.add(Text.literal("§8" + timeStr));
        
        // Format event based on type
        String eventDesc = switch (event.eventType()) {
            case HistoryEvent.EventType.LEVEL_UP -> {
                String data = event.eventData();
                int level = extractIntValue(data, "level");
                if (level == 0) {
                    level = extractIntValue(data, "new_level");
                }
                yield "§f- Reached Level " + level;
            }
            case HistoryEvent.EventType.ROLE_CHANGE -> {
                String data = event.eventData();
                String toRole = extractStringValue(data, "to");
                yield "§f- Became " + formatRoleName(toRole);
            }
            case HistoryEvent.EventType.ACHIEVEMENT -> {
                String achievementName = extractAchievementName(event.eventData());
                yield "§e- " + achievementName;
            }
            case HistoryEvent.EventType.TRADE -> {
                String toName = extractStringValue(event.eventData(), "to_name");
                yield "§f- Traded to " + toName;
            }
            case HistoryEvent.EventType.COMBAT -> {
                boolean victory = event.eventData().contains("\"result\":\"victory\"");
                String opponent = extractStringValue(event.eventData(), "opponent");
                String result = victory ? "Defeated" : "Lost to";
                yield "§8- " + result + " " + formatCombatOpponent(opponent);
            }
            case HistoryEvent.EventType.MOOD_MILESTONE -> {
                String mood = extractStringValue(event.eventData(), "mood");
                float intensity = extractFloatValue(event.eventData(), "intensity");
                String moodLabel = formatMoodName(mood);
                if (intensity > 0f) {
                    int percent = Math.round(Math.max(0f, Math.min(1f, intensity)) * 100);
                    yield "§f- Felt " + moodLabel + " (§7" + percent + "%§f)";
                }
                yield "§f- Felt " + moodLabel;
            }
            default -> "§8- " + event.eventType();
        };
        
        lines.add(Text.literal("  " + eventDesc));
        
        return lines;
    }
    
    private static String formatEventAge(long ticks) {
        if (ticks < TICKS_PER_DAY) {
            return "Today";
        } else if (ticks < TICKS_PER_DAY * 7) {
            int days = (int) (ticks / TICKS_PER_DAY);
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        } else if (ticks < TICKS_PER_DAY * 30) {
            int weeks = (int) (ticks / (TICKS_PER_DAY * 7));
            return weeks + " week" + (weeks != 1 ? "s" : "") + " ago";
        } else {
            int months = (int) (ticks / (TICKS_PER_DAY * 30));
            return months + " month" + (months != 1 ? "s" : "") + " ago";
        }
    }
    
    private static String formatRoleName(String roleId) {
        if (roleId == null || roleId.isEmpty()) return "Unknown";

        Identifier parsed = Identifier.tryParse(roleId);
        if (parsed != null) {
            PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(parsed);
            if (roleType != null) {
                return getRoleDisplayName(parsed, roleType);
            }
            return humanizeIdentifier(parsed);
        }

        return humanizeWords(roleId);
    }
    
    private static int extractIntValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) {
            return 0;
        }
        
        try {
            int keyIdx = json.indexOf("\"" + key + "\":");
            if (keyIdx == -1) return 0;
            
            int start = keyIdx + key.length() + 3;
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            
            if (end <= start || end > json.length()) {
                System.err.println("Invalid bounds for extracting int value for key: " + key);
                return 0;
            }
            
            String valueStr = json.substring(start, end).trim();
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse integer value for key " + key + ": " + e.getMessage());
            return 0;
        } catch (StringIndexOutOfBoundsException e) {
            System.err.println("String index error extracting int value for key " + key + ": " + e.getMessage());
            return 0;
        } catch (Exception e) {
            System.err.println("Unexpected error extracting int value for key " + key + ": " + e.getMessage());
            return 0;
        }
    }
    
    private static String extractStringValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) {
            return "Unknown";
        }
        
        try {
            int keyIdx = json.indexOf("\"" + key + "\":\"");
            if (keyIdx == -1) return "Unknown";
            
            int start = keyIdx + key.length() + 4;
            int end = json.indexOf('"', start);
            
            if (end <= start || end > json.length()) {
                System.err.println("Invalid bounds for extracting string value for key: " + key);
                return "Unknown";
            }
            
            return json.substring(start, end);
        } catch (StringIndexOutOfBoundsException e) {
            System.err.println("String index error extracting string value for key " + key + ": " + e.getMessage());
            return "Unknown";
        } catch (Exception e) {
            System.err.println("Unexpected error extracting string value for key " + key + ": " + e.getMessage());
            return "Unknown";
        }
    }
    
    private static String extractAchievementName(String json) {
        if (json == null || json.isEmpty()) {
            return "Achievement unlocked";
        }

        try {
            String type = extractStringValue(json, "achievement_type");
            
            // Format achievement type nicely
            if (type.equals("Unknown") || type.isEmpty()) return "Achievement unlocked";
            
            String formatted = type.replace('_', ' ');
            if (formatted.isEmpty()) return "Achievement unlocked";
            
            return formatted.substring(0, 1).toUpperCase() + formatted.substring(1).toLowerCase();
        } catch (StringIndexOutOfBoundsException e) {
            System.err.println("String index error formatting achievement name: " + e.getMessage());
            return "Achievement unlocked";
        } catch (Exception e) {
            System.err.println("Unexpected error extracting achievement name: " + e.getMessage());
            return "Achievement unlocked";
        }
    }

    private static String formatCombatOpponent(String opponentId) {
        if (opponentId == null || opponentId.isEmpty()) {
            return "an unknown foe";
        }

        Identifier identifier = Identifier.tryParse(opponentId);
        if (identifier != null) {
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> type = Registries.ENTITY_TYPE.get(identifier);
                if (type != null) {
                    return type.getName().getString();
                }
            }
            return humanizeIdentifier(identifier);
        }

        return humanizeWords(opponentId);
    }

    private static float extractFloatValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) {
            return 0f;
        }

        try {
            int keyIdx = json.indexOf("\"" + key + "\":");
            if (keyIdx == -1) return 0f;

            int start = keyIdx + key.length() + 3;
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);

            if (end <= start || end > json.length()) {
                System.err.println("Invalid bounds for extracting float value for key: " + key);
                return 0f;
            }

            String valueStr = json.substring(start, end).trim();
            return Float.parseFloat(valueStr);
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse float value for key " + key + ": " + e.getMessage());
            return 0f;
        } catch (StringIndexOutOfBoundsException e) {
            System.err.println("String index error extracting float value for key " + key + ": " + e.getMessage());
            return 0f;
        } catch (Exception e) {
            System.err.println("Unexpected error extracting float value for key " + key + ": " + e.getMessage());
            return 0f;
        }
    }

    private static String formatMoodName(String mood) {
        if (mood == null || mood.isEmpty()) {
            return "a mood";
        }

        Identifier parsed = Identifier.tryParse(mood);
        if (parsed != null) {
            return humanizeIdentifier(parsed);
        }

        return humanizeWords(mood);
    }

    private static String humanizeIdentifier(Identifier identifier) {
        if (identifier == null) {
            return "Unknown";
        }
        return humanizeWords(identifier.getPath());
    }

    private static String humanizeWords(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Unknown";
        }

        String cleaned = raw.replace(':', ' ').replace('_', ' ').trim();
        if (cleaned.isEmpty()) {
            return "Unknown";
        }

        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
        }

        return builder.length() > 0 ? builder.toString() : "Unknown";
    }
    
    /**
     * Build emotion cue journal pages from suppressed cues.
     * Shows the last N emotion cues that were filtered out by cooldowns/digests.
     */
    public static List<List<Text>> buildEmotionJournalPages(
            net.minecraft.server.network.ServerPlayerEntity player, 
            long currentTick, 
            int maxCues,
            @Nullable Identifier natureId) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();
        
        // Get suppressed cues from EmotionContextCues with null checks
        java.util.Deque<Text> journal = null;
        try {
            if (player != null && player.getUuid() != null) {
                journal = woflo.petsplus.events.EmotionContextCues.getJournalForPlayer(player.getUuid());
            }
        } catch (Exception e) {
            System.err.println("Error accessing emotion journal: " + e.getMessage());
        }
        
        String emotionHeaderText = CompendiumColorTheme.formatSectionHeader("Emotion Journal", natureId);

        if (journal == null || journal.isEmpty()) {
            currentPage.add(Text.literal(emotionHeaderText));
            currentPage.add(Text.empty());
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No suppressed cues yet."));
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Your pets are quiet."));
            pages.add(currentPage);
            return pages;
        }

        // Header
        currentPage.add(Text.literal(emotionHeaderText));
        currentPage.add(Text.empty());
        
        int linesOnPage = 2;
        final int MAX_LINES = 14;
        int count = 0;
        
        // Reverse to show most recent first
        List<Text> reversed = new ArrayList<>(journal);
        Collections.reverse(reversed);
        
        for (Text cue : reversed) {
            if (count >= maxCues) break;
            
            // Check if we need a new page
            if (linesOnPage + 2 > MAX_LINES) {
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                currentPage.add(Text.literal(emotionHeaderText));
                currentPage.add(Text.empty());
                linesOnPage = 2;
            }
            
            // Add entry with nature-themed formatting
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "- ")
                .append(Text.literal(CompendiumColorTheme.LIGHT_GRAY).append(cue)));
            linesOnPage++;
            count++;
        }
        
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
        
        return pages;
    }
    
    /**
     * Build gossip/rumor pages from pet's ledger.
     * Shows the last N rumors the pet knows about.
     */
    public static List<List<Text>> buildGossipPages(
            PetComponent pc, 
            long currentTick, 
            int maxRumors,
            @Nullable Identifier natureId) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();
        
        woflo.petsplus.state.gossip.PetGossipLedger ledger = null;
        try {
            if (pc != null) {
                ledger = pc.getGossipLedger();
            }
        } catch (Exception e) {
            System.err.println("Error accessing gossip ledger: " + e.getMessage());
        }
        
        String gossipHeaderText = CompendiumColorTheme.formatSectionHeader("Gossip & Rumors", natureId);

        if (ledger == null || ledger.isEmpty()) {
            currentPage.add(Text.literal(gossipHeaderText));
            currentPage.add(Text.empty());
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No rumors to share."));
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "They haven't heard much."));
            pages.add(currentPage);
            return pages;
        }

        // Header
        currentPage.add(Text.literal(gossipHeaderText));
        currentPage.add(Text.empty());
        
        int linesOnPage = 2;
        final int MAX_LINES = 14;
        int count = 0;
        
        // Collect rumors and sort by recency
        List<woflo.petsplus.state.gossip.RumorEntry> rumors = new ArrayList<>();
        ledger.forEachRumor(rumors::add);
        
        // Sort by last heard tick (most recent first)
        rumors.sort((a, b) -> Long.compare(b.lastHeardTick(), a.lastHeardTick()));
        
        for (woflo.petsplus.state.gossip.RumorEntry rumor : rumors) {
            if (count >= maxRumors) break;
            
            // Check if we need a new page
            if (linesOnPage + 3 > MAX_LINES) {
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                currentPage.add(Text.literal(gossipHeaderText));
                currentPage.add(Text.empty());
                linesOnPage = 2;
            }
            
            // Format rumor entry with hover details
            String topicName = getTopicName(rumor.topicId());
            float confidence = rumor.confidence() * 100;
            boolean witnessed = rumor.lastWitnessTick() > 0;
            String status = witnessed ? "seen" : "heard";
            
            // Main entry line
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "- " +
                CompendiumColorTheme.LIGHT_GRAY + topicName +
                CompendiumColorTheme.DARK_GRAY + " §8(" + status + ")"));
            
            // Confidence bar
            String confBar = buildConfidenceBar(confidence, natureId);
            currentPage.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + confBar));
            
            linesOnPage += 2;
            count++;
        }
        
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
        
        if (pages.isEmpty()) {
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Empty ledger"));
            pages.add(currentPage);
        }
        
        return pages;
    }
    
    private static String getTopicName(long topicId) {
        // Try to get topic name from GossipTopics
        try {
            String name = woflo.petsplus.state.gossip.GossipTopics.getTopicName(topicId);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
        }
        
        // Fallback to generic description
        return "Unknown topic";
    }
    
    private static String buildConfidenceBar(float percentage, @Nullable Identifier natureId) {
        int filled = Math.round(percentage / 10); // 0-10 blocks
        filled = Math.max(0, Math.min(10, filled));
        
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(accentCode).append("▮");
            } else {
                bar.append(CompendiumColorTheme.DARK_GRAY).append("▯");
            }
        }
        bar.append(CompendiumColorTheme.LIGHT_GRAY).append(" ").append(Math.round(percentage)).append("%");
        
        return bar.toString();
    }
}


