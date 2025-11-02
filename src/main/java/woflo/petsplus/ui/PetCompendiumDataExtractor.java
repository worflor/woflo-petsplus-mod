package woflo.petsplus.ui;

import woflo.petsplus.api.registry.RoleIdentifierUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetComponent.HarmonyCompatibility;
import woflo.petsplus.state.morality.MalevolenceLedger;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;
import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.stats.PetImprint;
import woflo.petsplus.state.gossip.GossipTopics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


/**
 * Extracts and formats pet data for display in the Pet Compendium.
 * Follows the same formatting conventions as ProofOfExistence for UI coherence.
 */
public class PetCompendiumDataExtractor {
    
    private static final int TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_HOUR = 1000;
    private static final float MODIFIER_THRESHOLD = 0.01f;
    private static final int RELATIONSHIP_SCAN_RANGE = 12;
    
    /**
     * Build page 1: General life statistics
     */
    public static List<Text> buildGeneralStatsPage(MobEntity pet, PetComponent pc, long currentTick) {
        List<Text> lines = new ArrayList<>();
        Identifier natureId = pc.getNatureId();

        // Title
        lines.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Pet Compendium", natureId)));
        lines.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        lines.add(Text.empty());

        String labelColor = CompendiumColorTheme.LIGHT_GRAY;
        String highlight = CompendiumColorTheme.getNatureHighlightCode(natureId);
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String divider = CompendiumColorTheme.DARK_GRAY;
        
        // Name
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() :
            pet.getType().getName().getString();
        lines.add(Text.literal(labelColor + "Name:"));
        lines.add(Text.literal("  " + highlight + petName));
        lines.add(Text.empty());
        
        // Level & XP
        int level = pc.getLevel();
        int xp = pc.getExperience();
        int xpProgress = Math.round(pc.getXpProgress() * 100);
        
        lines.add(Text.literal(labelColor + "Level: " + highlight + level + divider + " ("
            + labelColor + xpProgress + "% to next" + divider + ")"));
        lines.add(Text.literal(labelColor + "Experience: " + highlight + xp));
        lines.add(Text.empty());
        
        // Health
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        String healthColor = getHealthColor(health / maxHealth);
        
        lines.add(Text.literal(labelColor + "Health:"));
        lines.add(Text.literal("  " + healthColor + String.format("%.1f", health)
            + divider + " / " + highlight + String.format("%.1f", maxHealth)));
        lines.add(Text.empty());
        
        // Lifespan
        String lifespan = formatLifespan(pc, currentTick);
        if (lifespan != null) {
            lines.add(Text.literal(labelColor + "Lifespan:"));
            lines.add(Text.literal("  " + highlight + lifespan));
            lines.add(Text.empty());
        }
        
        // Bond strength (if available)
        long bondStrength = pc.getBondStrength();
        if (bondStrength > 0) {
            String bondLevel = getBondLevel(bondStrength);
            lines.add(Text.literal(labelColor + "Bond: " + accent + bondLevel));
            lines.add(Text.literal("  " + divider + "(" + highlight + bondStrength + divider + " strength)"));
        }
        return lines;
    }
    
    /**
     * Build page 2: Role, nature, and characteristics
     */
    public static List<Text> buildCharacteristicsPage(MobEntity pet, PetComponent pc) {
        List<Text> lines = new ArrayList<>();
        Identifier natureId = pc.getNatureId();
        String labelColor = CompendiumColorTheme.LIGHT_GRAY;
        String highlight = CompendiumColorTheme.getNatureHighlightCode(natureId);
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String shadow = CompendiumColorTheme.DARK_GRAY;
        
        lines.add(Text.literal(labelColor + "ÃƒÂ¯Ã‚Â¿Ã‚Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½ Characteristics ÃƒÂ¯Ã‚Â¿Ã‚Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½"));
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
            System.err.println("Error retrieving role type for ID " + roleId + ": " + e.getMessage());
        }
        
        lines.add(Text.literal(labelColor + "Role: " + highlight + roleName));
        lines.add(Text.empty());
        
        // Nature (single nature per pet)
        if (natureId != null) {
            try {
                String natureName = NatureDisplayUtil.formatNatureName(pc, natureId);
                lines.add(Text.literal(labelColor + "Nature: " + accent + natureName));
            } catch (Exception e) {
                System.err.println("Error formatting nature name for ID " + natureId + ": " + e.getMessage());
                lines.add(Text.literal(labelColor + "Nature: " + shadow + "Unknown"));
            }
            lines.add(Text.empty());
        }
        
        // Imprint/Traits
        PetImprint imprint = pc.getImprint();
        if (imprint != null) {
            try {
                float vitality = imprint.getVitalityMultiplier() - 1.0f;
                float might = imprint.getMightMultiplier() - 1.0f;
                float guard = imprint.getGuardMultiplier() - 1.0f;
                
                boolean hasNotableTraits = Math.abs(vitality) > MODIFIER_THRESHOLD
                    || Math.abs(might) > MODIFIER_THRESHOLD
                    || Math.abs(guard) > MODIFIER_THRESHOLD;
                
                if (hasNotableTraits) {
                    lines.add(Text.literal(labelColor + "Traits:"));
                    
                    if (Math.abs(vitality) > MODIFIER_THRESHOLD) {
                        String desc = vitality > 0 ? "Vigorous" : "Delicate";
                        lines.add(Text.literal("  " + shadow + desc + ": " + highlight + formatModifier(vitality)));
                    }
                    if (Math.abs(might) > MODIFIER_THRESHOLD) {
                        String desc = might > 0 ? "Fierce" : "Gentle";
                        lines.add(Text.literal("  " + shadow + desc + ": " + highlight + formatModifier(might)));
                    }
                    if (Math.abs(guard) > MODIFIER_THRESHOLD) {
                        String desc = guard > 0 ? "Stalwart" : "Brittle";
                        lines.add(Text.literal("  " + shadow + desc + ": " + highlight + formatModifier(guard)));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error extracting imprint data: " + e.getMessage());
                lines.add(Text.literal(labelColor + "Traits: " + shadow + "Unable to retrieve"));
            }
        }
        
        return lines;
    }

    /**
     * Build a polished combat overview aligned with the shared compendium styling.
     */
    public static List<Text> buildCombatStatsPage(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<Text> lines = new ArrayList<>();

        lines.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Combat Record", natureId)));
        lines.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        lines.add(Text.empty());

        List<HistoryEvent> history = pc.getHistory();
        int victories = 0;
        int defeats = 0;
        long latestTick = Long.MIN_VALUE;
        boolean latestWasVictory = false;
        String latestOpponent = null;
        java.util.Map<String, Integer> opponentCounts = new java.util.HashMap<>();

        for (HistoryEvent event : history) {
            if (!HistoryEvent.EventType.COMBAT.equals(event.eventType())) {
                continue;
            }

            boolean victory = event.eventData() != null && event.eventData().contains("\"result\":\"victory\"");
            if (victory) {
                victories++;
            } else {
                defeats++;
            }

            String opponent = formatCombatOpponent(extractStringValue(event.eventData(), "opponent"));
            opponentCounts.merge(opponent, 1, Integer::sum);

            if (event.timestamp() > latestTick) {
                latestTick = event.timestamp();
                latestWasVictory = victory;
                latestOpponent = opponent;
            }
        }

        int encounters = victories + defeats;
        if (encounters == 0) {
            lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No battles recorded."));
            return lines;
        }

        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        // Record: W-L format with win rate percentage
        float winRate = (victories * 100f) / encounters;
        lines.add(Text.literal(accent + victories + CompendiumColorTheme.DARK_GRAY + "-" 
            + accent + defeats + CompendiumColorTheme.LIGHT_GRAY + " Ãƒâ€šÃ‚Â· "
            + accent + formatNumber(winRate, 1) + "%" + CompendiumColorTheme.LIGHT_GRAY + " win rate"));

        // Most recent battle
        if (latestOpponent != null) {
            String verdict = latestWasVictory ? CompendiumColorTheme.getNatureAccentCode(natureId) + "W" : "Ãƒâ€šÃ‚Â§cL";
            String when = latestTick > Long.MIN_VALUE ? formatElapsed(latestTick, currentTick) : "moments ago";
            String opponentShort = latestOpponent.length() > 17 ? latestOpponent.substring(0, 14) + ".." : latestOpponent;
            lines.add(Text.literal(verdict + CompendiumColorTheme.LIGHT_GRAY + " vs " 
                + accent + opponentShort 
                + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + when));
        }

        // Most frequent opponent (if fought 2+ times)
        java.util.Map.Entry<String, Integer> frequent = opponentCounts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .max(java.util.Map.Entry.comparingByValue())
            .orElse(null);
        if (frequent != null) {
            String rivalShort = frequent.getKey().length() > 20 ? frequent.getKey().substring(0, 17) + ".." : frequent.getKey();
            lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Rival: "
                + accent + rivalShort
                + CompendiumColorTheme.DARK_GRAY + " ÃƒÆ’Ã¢â‚¬â€" + frequent.getValue()));
        }

        Identifier roleId = pc.getRoleId();
        PetRoleType roleType = roleId != null ? PetsPlusRegistries.petRoleTypeRegistry().get(roleId) : null;
        if (roleType != null) {
            List<Text> specialStats = extractRoleSpecificStats(pc, roleType, natureId);
            if (!specialStats.isEmpty()) {
                lines.add(Text.empty());
                lines.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Signature Feats:"));
                lines.addAll(specialStats);
            }
        }

        return lines;
    }
    
    /**
     * Build journal pages from history events (recent first)
     */
    public static List<List<Text>> buildJournalPages(
        PetComponent pc,
        long currentTick,
        int maxEvents,
        @Nullable Identifier natureId
    ) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();

        String journalHeader = CompendiumColorTheme.formatSectionHeader("History Journal", natureId);
        
        // Get history events in reverse order (most recent first)
        List<HistoryEvent> history = new ArrayList<>(pc.getHistory());
        Collections.reverse(history);
        
        // Filter and format significant events
        List<HistoryEvent> significantEvents = selectSignificantEvents(history, maxEvents);
        
        // Header with summary
        currentPage.add(Text.literal(journalHeader));
        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        String highlight = CompendiumColorTheme.getNatureHighlightCode(natureId);
        
        // Calculate timeline info
        long oldestEvent = significantEvents.isEmpty() ? currentTick : significantEvents.get(significantEvents.size() - 1).timestamp();
        long timespan = currentTick - oldestEvent;
        String spanText = formatEventAge(timespan);
        
        currentPage.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Recorded: " 
            + highlight + significantEvents.size() + CompendiumColorTheme.DARK_GRAY + " events Ãƒâ€šÃ‚Â· "
            + CompendiumColorTheme.LIGHT_GRAY + "Span: " + highlight + spanText));
        currentPage.add(Text.empty());
        
        int linesOnPage = 4;
        final int MAX_LINES_PER_PAGE = 14;
        
        // Cluster events by time period and type
        List<EventCluster> clusters = clusterHistoryEvents(significantEvents, currentTick);
        
        for (EventCluster cluster : clusters) {
            // Add time period header if starting new period
            if (cluster.showTimePeriodHeader) {
                if (linesOnPage + 1 > MAX_LINES_PER_PAGE && !currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentPage.add(Text.literal(journalHeader));
                    currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                    currentPage.add(Text.empty());
                    linesOnPage = 3;
                }
                
                String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
                currentPage.add(Text.literal(accent + cluster.timePeriodLabel));
                linesOnPage++;
            }
            
            // Format cluster (single event or grouped events)
            if (cluster.events.size() == 1) {
                // Single event - display normally
                List<Text> eventLines = formatHistoryEvent(cluster.events.get(0), currentTick, natureId);
                
                if (linesOnPage + eventLines.size() + 1 > MAX_LINES_PER_PAGE && !currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentPage.add(Text.literal(journalHeader));
                    currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                    currentPage.add(Text.empty());
                    linesOnPage = 3;
                }
                
                currentPage.addAll(eventLines);
                currentPage.add(Text.empty());
                linesOnPage += eventLines.size() + 1;
            } else {
                // Multiple events - show as clustered group
                String clusterSummary = formatClusteredEvents(cluster, currentTick, natureId);
                int lineCount = 2; // Cluster takes 2 lines typically
                
                if (linesOnPage + lineCount + 1 > MAX_LINES_PER_PAGE && !currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentPage.add(Text.literal(journalHeader));
                    currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                    currentPage.add(Text.empty());
                    linesOnPage = 3;
                }
                
                currentPage.add(Text.literal(clusterSummary));
                currentPage.add(Text.empty());
                linesOnPage += lineCount;
            }
        }

        if (significantEvents.isEmpty()) {
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No events recorded."));
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        return pages;
    }
    
    /**
     * Cluster sequential events of the same type within time windows
     */
    private static List<EventCluster> clusterHistoryEvents(List<HistoryEvent> events, long currentTick) {
        List<EventCluster> clusters = new ArrayList<>();
        if (events.isEmpty()) {
            return clusters;
        }
        
        final long TICKS_PER_DAY = 24000L;
        final long CLUSTER_WINDOW = TICKS_PER_DAY; // Events within 1 day can cluster
        
        String currentPeriod = null;
        EventCluster currentCluster = null;
        
        for (HistoryEvent event : events) {
            // Determine time period
            long age = currentTick - event.timestamp();
            String period = getTimePeriod(age);
            
            boolean newPeriod = !period.equals(currentPeriod);
            currentPeriod = period;
            
            // Check if we can add to current cluster
            boolean canCluster = currentCluster != null
                && !newPeriod
                && currentCluster.events.get(currentCluster.events.size() - 1).eventType().equals(event.eventType())
                && (currentCluster.events.get(currentCluster.events.size() - 1).timestamp() - event.timestamp()) <= CLUSTER_WINDOW
                && isClusterableType(event.eventType());
            
            if (canCluster) {
                currentCluster.events.add(event);
            } else {
                // Start new cluster
                currentCluster = new EventCluster();
                currentCluster.events.add(event);
                currentCluster.timePeriodLabel = period;
                currentCluster.showTimePeriodHeader = newPeriod;
                clusters.add(currentCluster);
            }
        }
        
        return clusters;
    }
    
    private static String getTimePeriod(long ageInTicks) {
        long days = ageInTicks / 24000L;
        if (days < 1) {
            return "Today";
        } else if (days < 7) {
            return "This Week";
        } else if (days < 30) {
            return "This Month";
        } else {
            return "Earlier";
        }
    }
    
    private static boolean isClusterableType(String eventType) {
        // Combat, trades, and achievements can cluster
        return "combat".equals(eventType) || "trade".equals(eventType) || "achievement".equals(eventType);
    }
    
    private static String formatClusteredEvents(EventCluster cluster, long currentTick, @Nullable Identifier natureId) {
        if (cluster.events.isEmpty()) {
            return "";
        }
        
        String eventType = cluster.events.get(0).eventType();
        int count = cluster.events.size();
        long mostRecent = cluster.events.get(0).timestamp();
        String timeAgo = formatElapsed(mostRecent, currentTick);
        
        String typeLabel = eventType.substring(0, 1).toUpperCase() + eventType.substring(1).replace('_', ' ');
        if ("combat".equals(eventType)) {
            typeLabel = "Battles";
        } else if ("trade".equals(eventType)) {
            typeLabel = "Trades";
        } else if ("achievement".equals(eventType)) {
            typeLabel = "Achievements";
        }
        
        return CompendiumColorTheme.DARK_GRAY + "  ÃƒÆ’Ã¢â‚¬â€ " + CompendiumColorTheme.getNatureHighlightCode(natureId) + count + " "
            + CompendiumColorTheme.LIGHT_GRAY + typeLabel
            + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + timeAgo;
    }
    
    private static class EventCluster {
        List<HistoryEvent> events = new ArrayList<>();
        String timePeriodLabel;
        boolean showTimePeriodHeader;
    }

    public static List<List<Text>> buildMoralityPages(
        PetComponent pc,
        long currentTick,
        @Nullable Identifier natureId
    ) {
        List<List<Text>> pages = new ArrayList<>();
        if (pc == null) {
            return pages;
        }

        MalevolenceLedger ledger = pc.getMalevolenceLedger();
        if (ledger == null) {
            return pages;
        }

        MalevolenceLedger.MoralitySnapshot snapshot = ledger.describe();
        if (snapshot == null) {
            return pages;
        }

                        List<Text> first = new ArrayList<>();
        String header = CompendiumColorTheme.formatSectionHeader("Morality & Harmony", natureId);
        first.add(Text.literal(header));
        first.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        first.add(Text.empty());

        String highlight = CompendiumColorTheme.getNatureHighlightCode(natureId);
        String malevolenceValue = formatNumber(snapshot.score(), 1);
        String moodState = snapshot.active() ? "active" : "calm";
        String viceFocus = snapshot.dominantViceName() != null ? snapshot.dominantViceName() : "Balanced";
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Malevolence: "
            + highlight + malevolenceValue + CompendiumColorTheme.DARK_GRAY + " | "
            + CompendiumColorTheme.LIGHT_GRAY + "Vice: " + highlight + viceFocus
            + CompendiumColorTheme.DARK_GRAY + " (" + moodState + ")"));

        String spreeIndicator = snapshot.spreeCount() > 0 ? "ACTIVE" : "Calm";
        String spreeCount = snapshot.spreeCount() > 0 ? " x" + snapshot.spreeCount() : "";
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Spree: "
            + highlight + spreeIndicator + spreeCount
            + CompendiumColorTheme.DARK_GRAY + " | " + CompendiumColorTheme.LIGHT_GRAY + "Last deed: "
            + highlight + formatElapsed(snapshot.lastDeedTick(), currentTick) + " ago"));

        PetComponent.HarmonyState harmonyState = pc.getHarmonyState();
        List<Identifier> harmonySets = harmonyState != null ? harmonyState.harmonySetIds() : List.of();
        List<Identifier> disharmonySets = harmonyState != null ? harmonyState.disharmonySetIds() : List.of();
        
        float harmonyStrength = harmonyState != null ? Math.max(0f, harmonyState.harmonyStrength()) : 0f;
        float disharmonyStrength = harmonyState != null ? Math.max(0f, harmonyState.disharmonyStrength()) : 0f;
        String harmonyBar = buildMeterBar(MathHelper.clamp(harmonyStrength / 3f, 0f, 1f), 6, natureId);
        String disharmonyBar = buildMeterBar(MathHelper.clamp(disharmonyStrength / 3f, 0f, 1f), 6, natureId);
        
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Harmony " + CompendiumColorTheme.DARK_GRAY + harmonyBar
            + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + "Disharmony " + CompendiumColorTheme.DARK_GRAY + disharmonyBar
            + CompendiumColorTheme.DARK_GRAY + " (" + harmonySets.size() + " / " + disharmonySets.size() + " sets)"));

        first.add(Text.empty());
        pages.add(first);

        boolean hasVices = snapshot.topVices() != null && !snapshot.topVices().isEmpty();
        boolean hasVirtues = snapshot.topVirtues() != null && !snapshot.topVirtues().isEmpty();
        if (hasVices || hasVirtues) {
            List<Text> second = new ArrayList<>();
            second.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Moral Highlights", natureId)));
            second.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            second.add(Text.empty());

            second.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Vice Highlights:"));
            if (hasVices) {
                for (MalevolenceLedger.AspectSnapshot aspect : snapshot.topVices()) {
                    second.add(Text.literal(formatAspectLine(aspect, natureId)));
                }
            } else {
                second.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "  No vices drawing attention."));
            }

            second.add(Text.empty());
            second.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Virtue Pillars:"));
            if (hasVirtues) {
                for (MalevolenceLedger.AspectSnapshot aspect : snapshot.topVirtues()) {
                    second.add(Text.literal(formatAspectLine(aspect, natureId)));
                }
            } else {
                second.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "  Virtues resting at baseline."));
            }

            pages.add(second);
        }

        return pages;
    }

    public static List<List<Text>> buildSpeciesMemoryPages(
        PetComponent pc,
        @Nullable Identifier natureId) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();
        
        if (pc == null) {
            return pages;
        }

        // Get species memory data
        RelationshipModule relationshipModule = pc.getRelationshipModule();
        if (relationshipModule == null) {
            return pages;
        }

        String headerText = CompendiumColorTheme.formatSectionHeader("Species Memory", natureId);
        currentPage.add(Text.literal(headerText));
        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        currentPage.add(Text.empty());

        // Collect species from relationship module data
        try {
            RelationshipModule.Data moduleData = relationshipModule.toData();
            if (moduleData != null && moduleData.speciesMemory() != null && moduleData.speciesMemory().memories() != null) {
                java.util.List<woflo.petsplus.state.relationships.SpeciesMemory.SerializedSpeciesMemory> memories = 
                    moduleData.speciesMemory().memories();
                
                if (memories == null || memories.isEmpty()) {
                    currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No species memories recorded."));
                    pages.add(currentPage);
                    return pages;
                }

                // Sort by significance (sum of fear + hunting + caution)
                java.util.List<woflo.petsplus.state.relationships.SpeciesMemory.SerializedSpeciesMemory> sorted = 
                    memories.stream()
                        .sorted((a, b) -> {
                            float sigA = a.fear() + a.huntingPreference() + a.caution();
                            float sigB = b.fear() + b.huntingPreference() + b.caution();
                            return Float.compare(sigB, sigA);
                        })
                        .limit(20)
                        .collect(java.util.stream.Collectors.toList());

                if (sorted.isEmpty()) {
                    currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No significant memories."));
                    pages.add(currentPage);
                    return pages;
                }

                currentPage.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Known: " 
                    + CompendiumColorTheme.getNatureHighlightCode(natureId) + sorted.size() + CompendiumColorTheme.DARK_GRAY + " species"));
                currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "F=Fear H=Hunt C=Caution"));
                currentPage.add(Text.empty());

                int linesOnPage = 5;
                final int MAX_LINES = 14;
                String accent = CompendiumColorTheme.getNatureAccentCode(natureId);

                for (woflo.petsplus.state.relationships.SpeciesMemory.SerializedSpeciesMemory species : sorted) {
                    // Check page limit (3 lines per species: name, bars, blank)
                    if (linesOnPage + 3 > MAX_LINES) {
                        pages.add(new ArrayList<>(currentPage));
                        currentPage.clear();
                        currentPage.add(Text.literal(headerText));
                        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                        currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "F=Fear H=Hunt C=Caution"));
                        currentPage.add(Text.empty());
                        linesOnPage = 4;
                    }

                    // Format species name from ID
                    String speciesName = species.speciesId();
                    try {
                        Identifier speciesId = Identifier.tryParse(species.speciesId());
                        if (speciesId != null && Registries.ENTITY_TYPE.containsId(speciesId)) {
                            EntityType<?> type = Registries.ENTITY_TYPE.get(speciesId);
                            if (type != null) {
                                speciesName = type.getName().getString();
                            }
                        }
                    } catch (Exception e) {
                        // Use raw ID if lookup fails
                    }

                    if (speciesName.length() > 20) {
                        speciesName = speciesName.substring(0, 17) + "...";
                    }

                    // Display species entry
                    currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Ãƒâ€šÃ‚Â· " 
                        + accent + speciesName));

                    // Create bars for fear, hunting, caution
                    String fearBar = buildMeterBar(Math.min(species.fear(), 1.0f), 5, natureId);
                    String huntBar = buildMeterBar(Math.min(species.huntingPreference(), 1.0f), 5, natureId);
                    String cautBar = buildMeterBar(Math.min(species.caution(), 1.0f), 5, natureId);

                    currentPage.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY
                        + "F" + fearBar + " H" + huntBar + " C" + cautBar));

                    linesOnPage += 2;
                }

                if (!currentPage.isEmpty()) {
                    pages.add(currentPage);
                }

                return pages;
            }
        } catch (Exception e) {
            System.err.println("Error accessing species memory: " + e.getMessage());
        }

        // Fallback if error occurs
        currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Unable to read species memory."));
        pages.add(currentPage);
        return pages;
    }

    public static List<List<Text>> buildRelationshipPages(
        ServerPlayerEntity viewer,
        MobEntity pet,
        PetComponent pc,
        long currentTick,
        @Nullable Identifier natureId
    ) {
        List<List<Text>> pages = new ArrayList<>();
        if (pet == null || pc == null) {
            return pages;
        }

        RelationshipModule relationshipModule = pc.getRelationshipModule();
        if (relationshipModule == null) {
            return pages;
        }

        List<RelationshipProfile> profiles = relationshipModule.getAllRelationships();
        if (profiles == null || profiles.isEmpty()) {
            return pages;
        }

        ServerWorld serverWorld = pet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        if (serverWorld == null) {
            return pages;
        }

        double maxDistanceSq = RELATIONSHIP_SCAN_RANGE * RELATIONSHIP_SCAN_RANGE;
        List<RelationshipView> nearby = new ArrayList<>();
        for (RelationshipProfile profile : profiles) {
            UUID id = profile.entityId();
            if (id == null) {
                continue;
            }
            Entity candidate = serverWorld.getEntity(id);
            if (!(candidate instanceof MobEntity other)) {
                continue;
            }
            if (other == pet) {
                continue;
            }
            if (pet.squaredDistanceTo(other) > maxDistanceSq) {
                continue;
            }
            PetComponent otherComponent = PetComponent.get(other);
            if (otherComponent == null) {
                continue;
            }
            HarmonyCompatibility compatibility = null;
            PetComponent.HarmonyState harmonyState = pc.getHarmonyState();
            if (harmonyState != null) {
                compatibility = harmonyState.compatibility(id);
            }
            nearby.add(new RelationshipView(other, profile, compatibility));
        }

        if (nearby.isEmpty()) {
            return pages;
        }

        nearby.sort((a, b) -> {
            int trustCompare = Float.compare(b.profile().trust(), a.profile().trust());
            if (trustCompare != 0) {
                return trustCompare;
            }
            int affectionCompare = Float.compare(b.profile().affection(), a.profile().affection());
            if (affectionCompare != 0) {
                return affectionCompare;
            }
            long durationA = a.profile().getRelationshipDuration(currentTick);
            long durationB = b.profile().getRelationshipDuration(currentTick);
            return Long.compare(durationB, durationA);
        });

        String header = CompendiumColorTheme.formatSectionHeader("Pack Bonds", natureId);
        List<Text> currentPage = new ArrayList<>();
        currentPage.add(Text.literal(header));
        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "T=Trust W=Warmth R=Respect"));
        currentPage.add(Text.empty());

        int linesOnPage = 4;
        final int MAX_LINES = 14;
        for (RelationshipView view : nearby) {
            List<Text> entryLines = formatRelationshipEntry(view, currentTick, natureId);
            if (linesOnPage + entryLines.size() > MAX_LINES) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentPage.add(Text.literal(header));
                currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "T=Trust W=Warmth R=Respect"));
                currentPage.add(Text.empty());
                linesOnPage = 4;
            }
            currentPage.addAll(entryLines);
            linesOnPage += entryLines.size();
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
        if (healthPercent <= 0.2f) return "Ãƒâ€šÃ‚Â§c"; // Red
        if (healthPercent <= 0.5f) return "Ãƒâ€šÃ‚Â§e"; // Yellow
        if (healthPercent >= 0.9f) return "Ãƒâ€šÃ‚Â§a"; // Green
        return CompendiumColorTheme.LIGHT_GRAY; // Neutral
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

    private static String formatAspectLine(MalevolenceLedger.AspectSnapshot aspect, @Nullable Identifier natureId) {
        String highlight = CompendiumColorTheme.getNatureHighlightCode(natureId);
        StringBuilder builder = new StringBuilder();
        builder.append(CompendiumColorTheme.DARK_GRAY).append("A ")
            .append(highlight).append(aspect.name())
            .append(CompendiumColorTheme.DARK_GRAY).append(" ")
            .append(highlight).append(formatNumber(aspect.value(), 1));
        if (aspect.spreeCount() > 1) {
            builder.append(CompendiumColorTheme.DARK_GRAY).append(" x")
                .append(highlight).append(aspect.spreeCount());
        }
        if (aspect.suppressedCharge() > 0.05f) {
            builder.append(CompendiumColorTheme.DARK_GRAY).append(" ~")
                .append(highlight).append(formatNumber(aspect.suppressedCharge(), 1));
        }
        return builder.toString();
    }

    private static List<Text> formatRelationshipEntry(RelationshipView view, long currentTick, @Nullable Identifier natureId) {
        List<Text> lines = new ArrayList<>();
        MobEntity other = view.partner();
        RelationshipProfile profile = view.profile();

        String name = other.hasCustomName()
            ? other.getCustomName().getString()
            : other.getType().getName().getString();
        
        if (name.length() > 18) {
            name = name.substring(0, 15) + "..";
        }
        
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String typeLabel = formatRelationshipType(profile.getType());
        String knownDuration = formatDurationSimple(profile.getRelationshipDuration(currentTick));
        
        String harmonySymbol = "";
        if (view.compatibility() != null) {
            float harmonyScore = view.compatibility().harmonyStrength() - view.compatibility().disharmonyStrength();
            if (harmonyScore > 0.5f) {
                harmonySymbol = " ÃƒÂ¢Ã¢â€žÂ¢Ã‚Â¦";
            } else if (harmonyScore < -0.5f) {
                harmonySymbol = " ÃƒÂ¢Ã…â€œÃ¢â‚¬â€";
            }
        }

        // Compact first line: name Ãƒâ€šÃ‚Â· type Ãƒâ€šÃ‚Â· duration Ãƒâ€šÃ‚Â· compatibility
        lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Ãƒâ€šÃ‚Â· "
                + accent + name 
                + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + typeLabel
                + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + knownDuration
                + CompendiumColorTheme.DARK_GRAY + harmonySymbol));

        float trustNormalized = MathHelper.clamp((profile.trust() + 1.0f) / 2.0f, 0f, 1f);
        float affectionNormalized = MathHelper.clamp(profile.affection(), 0f, 1f);
        float respectNormalized = MathHelper.clamp(profile.respect(), 0f, 1f);

        // Metrics on second line: inline single-char labels with bars
        String trustBar = buildMeterBar(trustNormalized, 5, natureId);
        String affectionBar = buildMeterBar(affectionNormalized, 5, natureId);
        String respectBar = buildMeterBar(respectNormalized, 5, natureId);
        
        lines.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY 
            + "T" + trustBar + " W" + affectionBar + " R" + respectBar));

        return lines;
    }

    private static String formatRelationshipType(RelationshipType type) {
        if (type == null) {
            return "Unknown";
        }
        return humanizeWords(type.name());
    }

    private static String formatNumber(float value, int precision) {
        // Guard against NaN and Infinity
        if (Float.isNaN(value)) {
            return "0";
        }
        if (Float.isInfinite(value)) {
            return value > 0 ? "ÃƒÂ¢Ã‹â€ Ã…Â¾" : "-ÃƒÂ¢Ã‹â€ Ã…Â¾";
        }
        
        int clampedPrecision = Math.max(0, precision);
        String pattern = "%." + clampedPrecision + "f";
        return String.format(Locale.ROOT, pattern, value);
    }

    private static String formatDurationSimple(long ticks) {
        if (ticks <= 0) {
            return "moments";
        }
        long days = ticks / TICKS_PER_DAY;
        if (days > 0) {
            return days + "d";
        }
        long hours = ticks / TICKS_PER_HOUR;
        if (hours > 0) {
            return hours + "h";
        }
        long minutes = ticks / Math.max(1, TICKS_PER_HOUR / 60);
        if (minutes > 0) {
            return minutes + "m";
        }
        long seconds = ticks / 20;
        if (seconds > 0) {
            return seconds + "s";
        }
        return "moments";
    }

    private static String formatElapsed(long eventTick, long currentTick) {
        if (eventTick == Long.MIN_VALUE) {
            return "Never";
        }
        long delta = currentTick - eventTick;
        if (delta <= 0L) {
            return "moments ago";
        }
        return formatDurationSimple(delta) + " ago";
    }

    public static String buildMeterBar(float normalized, int segments, @Nullable Identifier natureId) {
        float clamped = MathHelper.clamp(normalized, 0f, 1f);
        int filled = Math.round(clamped * segments);
        String fill = CompendiumColorTheme.getNatureHighlightCode(natureId);
        String faded = CompendiumColorTheme.getNatureShadowCode(natureId);
        String deep = CompendiumColorTheme.getNatureDeepShadowCode(natureId);
        StringBuilder bar = new StringBuilder();
        bar.append(deep).append("[");
        for (int i = 0; i < segments; i++) {
            if (i < filled) {
                bar.append(fill).append("ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â®");
            } else {
                bar.append(faded).append("ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â¯");
            }
        }
        bar.append(deep).append("]");
        return bar.toString();
    }

    private record RelationshipView(
        MobEntity partner,
        RelationshipProfile profile,
        @Nullable HarmonyCompatibility compatibility
    ) {
    }
    
    @Nullable
    public static String formatLifespan(PetComponent pc, long currentTick) {
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
    
    private static List<Text> extractRoleSpecificStats(PetComponent pc, PetRoleType roleType, @Nullable Identifier natureId) {
        List<Text> stats = new ArrayList<>();
        List<HistoryEvent> history = pc.getHistory();

        String roleName = roleType.id().getPath().toLowerCase();
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);

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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ "
                    + CompendiumColorTheme.LIGHT_GRAY + "Shielded "
                    + accent + protectionCount + CompendiumColorTheme.LIGHT_GRAY + " hits"
                    + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· "
                    + CompendiumColorTheme.LIGHT_GRAY + "Redirected "
                    + accent + formatNumber((float) totalDamage, 1)
                    + CompendiumColorTheme.LIGHT_GRAY + " dmg"));
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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ "
                    + CompendiumColorTheme.LIGHT_GRAY + "Soothed allies "
                    + accent + healingEvents + CompendiumColorTheme.LIGHT_GRAY + " times"));
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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ "
                    + CompendiumColorTheme.LIGHT_GRAY + "Milestones "
                    + accent + String.join(", ", milestones)
                    + CompendiumColorTheme.LIGHT_GRAY));
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
    
    private static List<Text> formatHistoryEvent(HistoryEvent event, long currentTick, @Nullable Identifier natureId) {
        List<Text> lines = new ArrayList<>();

        // Format timestamp compactly
        long eventAge = currentTick - event.timestamp();
        String timeStr = formatEventAge(eventAge);

        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String eventDesc;
        
        switch (event.eventType()) {
            case HistoryEvent.EventType.LEVEL_UP -> {
                String data = event.eventData();
                int level = extractIntValue(data, "level");
                if (level == 0) {
                    level = extractIntValue(data, "new_level");
                }
                eventDesc = "Level " + accent + level;
            }
            case HistoryEvent.EventType.ROLE_CHANGE -> {
                String toRole = formatRoleName(extractStringValue(event.eventData(), "to"));
                eventDesc = "Became " + accent + toRole;
            }
            case HistoryEvent.EventType.ACHIEVEMENT -> {
                String achievementName = extractAchievementName(event.eventData());
                eventDesc = "ÃƒÂ¢Ã‹Å“Ã¢â‚¬Â¦ " + accent + achievementName;
            }
            case HistoryEvent.EventType.TRADE -> {
                String toName = extractStringValue(event.eventData(), "to_name");
                String partner = "Unknown".equals(toName) ? "another" : toName;
                eventDesc = "Traded to " + accent + partner;
            }
            case HistoryEvent.EventType.COMBAT -> {
                boolean victory = event.eventData() != null && event.eventData().contains("\"result\":\"victory\"");
                String opponent = formatCombatOpponent(extractStringValue(event.eventData(), "opponent"));
                if (opponent.length() > 15) {
                    opponent = opponent.substring(0, 12) + "..";
                }
                String result = victory ? CompendiumColorTheme.getNatureAccentCode(natureId) + "ÃƒÂ¢Ã…Â¡Ã¢â‚¬Â" : "Ãƒâ€šÃ‚Â§cÃƒÂ¢Ã…â€œÃ¢â‚¬â€";
                eventDesc = result + CompendiumColorTheme.LIGHT_GRAY + " " + accent + opponent;
            }
            case HistoryEvent.EventType.MOOD_MILESTONE -> {
                String mood = formatMoodName(extractStringValue(event.eventData(), "mood"));
                float intensity = extractFloatValue(event.eventData(), "intensity");
                int percent = Math.round(Math.max(0f, Math.min(1f, intensity)) * 100);
                eventDesc = "Felt " + accent + mood + CompendiumColorTheme.DARK_GRAY + " " + percent + "%";
            }
            default -> eventDesc = humanizeWords(event.eventType());
        }

        lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã…Â¸Ã‚Â¨" + timeStr + "ÃƒÂ¢Ã…Â¸Ã‚Â© " + CompendiumColorTheme.LIGHT_GRAY + eventDesc));

        return lines;
    }
    
    private static String formatEventAge(long ticks) {
        if (ticks < TICKS_PER_DAY) {
            return "today";
        } else if (ticks < TICKS_PER_DAY * 7) {
            int days = (int) (ticks / TICKS_PER_DAY);
            return days + "d ago";
        } else if (ticks < TICKS_PER_DAY * 30) {
            int weeks = (int) (ticks / (TICKS_PER_DAY * 7));
            return weeks + "w ago";
        } else {
            int months = (int) (ticks / (TICKS_PER_DAY * 30));
            return months + "mo ago";
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
     * Build active emotion pages from pet's mood engine.
     * Shows current active emotions with intensity bars.
     */
    public static List<List<Text>> buildEmotionJournalPages(
            PetComponent pc, 
            long currentTick, 
            int maxCues,
            @Nullable Identifier natureId) {
        List<List<Text>> pages = new ArrayList<>();
        List<Text> currentPage = new ArrayList<>();
        
        if (pc == null) {
            currentPage.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Active Emotions", natureId)));
            currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            currentPage.add(Text.empty());
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Unable to sense emotions."));
            pages.add(currentPage);
            return pages;
        }
        
        // Get active emotions from PetMoodEngine
        PetMoodEngine moodEngine = null;
        java.util.Map<PetComponent.Emotion, Float> emotions = new java.util.HashMap<>();
        try {
            moodEngine = pc.getMoodEngine();
            if (moodEngine != null) {
                emotions = moodEngine.getActiveEmotions();
            }
        } catch (Exception e) {
            System.err.println("Error accessing mood engine: " + e.getMessage());
        }
        
        String emotionHeaderText = CompendiumColorTheme.formatSectionHeader("Active Emotions", natureId);

        if (emotions == null || emotions.isEmpty()) {
            currentPage.add(Text.literal(emotionHeaderText));
            currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            currentPage.add(Text.empty());
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Emotionally neutral right now."));
            pages.add(currentPage);
            return pages;
        }

        // Sort by intensity descending
        java.util.List<java.util.Map.Entry<PetComponent.Emotion, Float>> sorted = 
            emotions.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(maxCues)
                .collect(java.util.stream.Collectors.toList());
        
        // Header with summary
        currentPage.add(Text.literal(emotionHeaderText));
        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        currentPage.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Active: " 
            + CompendiumColorTheme.getNatureHighlightCode(natureId) + sorted.size() + CompendiumColorTheme.DARK_GRAY + " emotions"));
        currentPage.add(Text.empty());
        
        int linesOnPage = 4;
        final int MAX_LINES = 14;
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        for (java.util.Map.Entry<PetComponent.Emotion, Float> entry : sorted) {
            PetComponent.Emotion emotion = entry.getKey();
            float intensity = entry.getValue();
            
            // Check if we need a new page (2 lines per emotion: name+bar, then blank)
            if (linesOnPage + 2 > MAX_LINES) {
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                currentPage.add(Text.literal(emotionHeaderText));
                currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                currentPage.add(Text.empty());
                linesOnPage = 3;
            }
            
            // Format emotion name with intensity bar
            String emotionName = emotion.name().replace('_', ' ');
            emotionName = emotionName.substring(0, 1).toUpperCase() + emotionName.substring(1).toLowerCase();
            String bar = buildMeterBar(Math.min(intensity, 1.0f), 8, natureId);
            int intensityPercent = Math.round(intensity * 100);
            
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Ãƒâ€šÃ‚Â· "
                + accent + emotionName 
                + CompendiumColorTheme.LIGHT_GRAY + " " + bar
                + CompendiumColorTheme.DARK_GRAY + " " + intensityPercent + "%"));
            linesOnPage++;
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
            currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            currentPage.add(Text.empty());
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No rumors logged."));
            pages.add(currentPage);
            return pages;
        }

        // Collect rumors and sort by recency
        List<woflo.petsplus.state.gossip.RumorEntry> rumors = new ArrayList<>();
        ledger.forEachRumor(rumors::add);
        rumors.sort((a, b) -> Long.compare(b.lastHeardTick(), a.lastHeardTick()));
        
        // Count witnessed
        int witnessed = (int) rumors.stream().filter(r -> r.lastWitnessTick() > 0).count();

        // Header with summary
        currentPage.add(Text.literal(gossipHeaderText));
        currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
        currentPage.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Known: " + CompendiumColorTheme.getNatureHighlightCode(natureId) + rumors.size()
            + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + "Witnessed: " + CompendiumColorTheme.getNatureHighlightCode(natureId) + witnessed));
        currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â‚¬â€Ã‚Â=Witnessed ÃƒÂ¢Ã¢â‚¬â€Ã¢â‚¬Â¹=Hearsay"));
        currentPage.add(Text.empty());
        
        int linesOnPage = 5;
        final int MAX_LINES = 14;
        int count = 0;
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);

        // Group rumors by abstract topic
        java.util.Map<GossipTopics.AbstractTopic, List<woflo.petsplus.state.gossip.RumorEntry>> groupedByTopic = new java.util.LinkedHashMap<>();
        for (woflo.petsplus.state.gossip.RumorEntry rumor : rumors) {
            java.util.Optional<GossipTopics.AbstractTopic> abstractTopic = GossipTopics.findAbstract(rumor.topicId());
            if (abstractTopic.isPresent()) {
                groupedByTopic.computeIfAbsent(abstractTopic.get(), k -> new ArrayList<>()).add(rumor);
            }
        }

        // Display rumors grouped by abstract topic
        for (java.util.Map.Entry<GossipTopics.AbstractTopic, List<woflo.petsplus.state.gossip.RumorEntry>> entry : groupedByTopic.entrySet()) {
            GossipTopics.AbstractTopic topic = entry.getKey();
            List<woflo.petsplus.state.gossip.RumorEntry> topicRumors = entry.getValue();
            
            if (topicRumors.isEmpty() || count >= maxRumors) continue;

            // Add category header
            if (linesOnPage + 1 > MAX_LINES) {
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                currentPage.add(Text.literal(gossipHeaderText));
                currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â‚¬â€Ã‚Â=Witnessed ÃƒÂ¢Ã¢â‚¬â€Ã¢â‚¬Â¹=Hearsay"));
                currentPage.add(Text.empty());
                linesOnPage = 4;
                currentPage.add(Text.empty());
                linesOnPage = 3;
            }

            String topicDisplayName = topic.name().replace('_', ' ');
            topicDisplayName = topicDisplayName.substring(0, 1).toUpperCase() + topicDisplayName.substring(1).toLowerCase();
            currentPage.add(Text.literal(accent + topicDisplayName + CompendiumColorTheme.DARK_GRAY + " (" + topicRumors.size() + ")"));
            linesOnPage++;

            // Add rumors in this category
            for (woflo.petsplus.state.gossip.RumorEntry rumor : topicRumors) {
                if (count >= maxRumors) break;

                int projectedLines = 2;
                boolean hasQuote = rumor.paraphrased() != null && !rumor.paraphrased().getString().isBlank();
                if (hasQuote) {
                    projectedLines++;
                }

                // Check if we need a new page
                if (linesOnPage + projectedLines > MAX_LINES) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentPage.add(Text.literal(gossipHeaderText));
                    currentPage.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
                    currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "ÃƒÂ¢Ã¢â‚¬â€Ã‚Â=Witnessed ÃƒÂ¢Ã¢â‚¬â€Ã¢â‚¬Â¹=Hearsay"));
                    currentPage.add(Text.empty());
                    linesOnPage = 4;
                }

                String topicName = getTopicName(rumor.topicId());
                if (topicName.length() > 24) {
                    topicName = topicName.substring(0, 21) + "..";
                }
                String heardAgo = formatElapsed(rumor.lastHeardTick(), currentTick);
                boolean isWitnessed = rumor.lastWitnessTick() > 0;
                String statusMark = isWitnessed ? "ÃƒÂ¢Ã¢â‚¬â€Ã‚Â" : "ÃƒÂ¢Ã¢â‚¬â€Ã¢â‚¬Â¹";
                String shareCount = rumor.shareCount() > 0 ? " ÃƒÆ’Ã¢â‚¬â€" + rumor.shareCount() : "";

                currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "  " + statusMark + " "
                    + CompendiumColorTheme.LIGHT_GRAY + topicName
                    + CompendiumColorTheme.DARK_GRAY + " Ãƒâ€šÃ‚Â· " + CompendiumColorTheme.LIGHT_GRAY + heardAgo
                    + CompendiumColorTheme.DARK_GRAY + shareCount));

                String confBar = buildConfidenceBar(rumor.confidence() * 100, natureId);
                int confPercent = Math.round(rumor.confidence() * 100);
                currentPage.add(Text.literal("    " + confBar 
                    + CompendiumColorTheme.DARK_GRAY + " " + confPercent + "%"));

                if (hasQuote) {
                    Text paraphrased = rumor.paraphrasedCopy();
                    currentPage.add(Text.literal("      " + CompendiumColorTheme.DARK_GRAY + "\"")
                        .append(paraphrased)
                        .append(Text.literal(CompendiumColorTheme.DARK_GRAY + "\"")));
                }

                linesOnPage += projectedLines;
                count++;
            }
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
        float normalized = MathHelper.clamp(percentage / 100f, 0f, 1f);
        return buildMeterBar(normalized, 10, natureId);
    }
}
