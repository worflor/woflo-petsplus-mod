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
import woflo.petsplus.stats.PetImprint;

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
     * Build a polished combat overview aligned with the shared compendium styling.
     */
    public static List<Text> buildCombatStatsPage(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<Text> lines = new ArrayList<>();

        lines.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Combat Record", natureId)));
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
            lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No battles recorded yet."));
            return lines;
        }

        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        lines.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Encounters: "
            + CompendiumColorTheme.WHITE + encounters));
        lines.add(Text.literal("  " + accent + victories + CompendiumColorTheme.LIGHT_GRAY + " wins "
            + CompendiumColorTheme.DARK_GRAY + "· "
            + accent + defeats + CompendiumColorTheme.LIGHT_GRAY + " losses"));

        float winRate = encounters > 0 ? (victories * 100f) / encounters : 0f;
        lines.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Win Rate: "
            + accent + formatNumber(winRate, 1) + "%"
            + CompendiumColorTheme.DARK_GRAY + " (" + victories + "/" + encounters + ")"));

        if (latestOpponent != null) {
            String verdict = latestWasVictory ? "Victory" : "Defeat";
            String when = latestTick > Long.MIN_VALUE ? formatElapsed(latestTick, currentTick) : "moments ago";
            lines.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Last Battle: "
                + accent + verdict
                + CompendiumColorTheme.DARK_GRAY + " vs "
                + accent + latestOpponent
                + CompendiumColorTheme.DARK_GRAY + " · "
                + CompendiumColorTheme.LIGHT_GRAY + when));
        }

        java.util.Map.Entry<String, Integer> frequent = opponentCounts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .max(java.util.Map.Entry.comparingByValue())
            .orElse(null);
        if (frequent != null) {
            lines.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Frequent Foe: "
                + accent + frequent.getKey()
                + CompendiumColorTheme.DARK_GRAY + " (" + frequent.getValue() + ")"));
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
            List<Text> eventLines = formatHistoryEvent(event, currentTick, natureId);

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
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "No recorded events yet."));
            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "Adventures await!"));
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        return pages;
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
        first.add(Text.empty());

        String malevolenceValue = formatNumber(snapshot.score(), 1);
        String moodState = snapshot.active() ? " (active)" : " (calm)";
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Malevolence: "
            + CompendiumColorTheme.WHITE + malevolenceValue
            + CompendiumColorTheme.DARK_GRAY + moodState));

        String viceFocus = snapshot.dominantViceName() != null ? snapshot.dominantViceName() : "Balanced";
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Vice Focus: "
            + CompendiumColorTheme.WHITE + viceFocus));

        String spreeLine;
        if (snapshot.spreeCount() <= 0) {
            spreeLine = "Cooling (" + formatDurationSimple(snapshot.spreeWindowTicks()) + " window)";
        } else {
            spreeLine = snapshot.spreeCount() + " flare" + (snapshot.spreeCount() > 1 ? "s" : "");
            String anchorAge = formatElapsed(snapshot.spreeAnchorTick(), currentTick);
            if (!"Never".equals(anchorAge)) {
                spreeLine += " · anchored " + anchorAge;
            }
            spreeLine += " (" + formatDurationSimple(snapshot.spreeWindowTicks()) + " window)";
        }
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Spree Heat: "
            + CompendiumColorTheme.WHITE + spreeLine));

        String lastDeed = formatElapsed(snapshot.lastDeedTick(), currentTick);
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Last Dark Deed: "
            + CompendiumColorTheme.WHITE + lastDeed));

        String ledgerDisharmony = formatNumber(snapshot.disharmony(), 1);
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Ledger Disharmony: "
            + CompendiumColorTheme.WHITE + ledgerDisharmony));

        first.add(Text.empty());
        first.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Harmony Flow:"));

        PetComponent.HarmonyState harmonyState = pc.getHarmonyState();
        List<Identifier> harmonySets = harmonyState != null ? harmonyState.harmonySetIds() : List.of();
        List<Identifier> disharmonySets = harmonyState != null ? harmonyState.disharmonySetIds() : List.of();

        if (harmonySets.isEmpty()) {
            first.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "Harmony sets: none"));
        } else {
            first.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Harmony Sets: "
                + CompendiumColorTheme.WHITE + formatIdentifierSummary(harmonySets)));
        }
        if (disharmonySets.isEmpty()) {
            first.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "Disharmony sets: none"));
        } else {
            first.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Disharmony Sets: "
                + CompendiumColorTheme.WHITE + formatIdentifierSummary(disharmonySets)));
        }

        float harmonyStrength = harmonyState != null ? Math.max(0f, harmonyState.harmonyStrength()) : 0f;
        float disharmonyStrength = harmonyState != null ? Math.max(0f, harmonyState.disharmonyStrength()) : 0f;
        String harmonyBar = buildMeterBar(MathHelper.clamp(harmonyStrength / 3f, 0f, 1f), 8, natureId);
        String disharmonyBar = buildMeterBar(MathHelper.clamp(disharmonyStrength / 3f, 0f, 1f), 8, natureId);
        first.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Harmony "
            + CompendiumColorTheme.DARK_GRAY + harmonyBar));
        first.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Disharmony "
            + CompendiumColorTheme.DARK_GRAY + disharmonyBar));

        pages.add(first);

        boolean hasVices = snapshot.topVices() != null && !snapshot.topVices().isEmpty();
        boolean hasVirtues = snapshot.topVirtues() != null && !snapshot.topVirtues().isEmpty();
        if (hasVices || hasVirtues) {
            List<Text> second = new ArrayList<>();
            second.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Moral Highlights", natureId)));
            second.add(Text.empty());

            second.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Vice Highlights:"));
            if (hasVices) {
                for (MalevolenceLedger.AspectSnapshot aspect : snapshot.topVices()) {
                    second.add(Text.literal(formatAspectLine(aspect)));
                }
            } else {
                second.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "  No vices drawing attention."));
            }

            second.add(Text.empty());
            second.add(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Virtue Pillars:"));
            if (hasVirtues) {
                for (MalevolenceLedger.AspectSnapshot aspect : snapshot.topVirtues()) {
                    second.add(Text.literal(formatAspectLine(aspect)));
                }
            } else {
                second.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "  Virtues resting at baseline."));
            }

            pages.add(second);
        }

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
        currentPage.add(Text.empty());

        int linesOnPage = 2;
        final int MAX_LINES = 14;
        for (RelationshipView view : nearby) {
            List<Text> entryLines = formatRelationshipEntry(view, currentTick, natureId);
            if (linesOnPage + entryLines.size() > MAX_LINES) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentPage.add(Text.literal(header));
                currentPage.add(Text.empty());
                linesOnPage = 2;
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

    private static String formatAspectLine(MalevolenceLedger.AspectSnapshot aspect) {
        StringBuilder builder = new StringBuilder();
        builder.append(CompendiumColorTheme.DARK_GRAY).append("- ")
            .append(CompendiumColorTheme.WHITE).append(aspect.name())
            .append(CompendiumColorTheme.DARK_GRAY).append(" (")
            .append(CompendiumColorTheme.WHITE).append(formatNumber(aspect.value(), 1));
        if (aspect.spreeCount() > 1) {
            builder.append(CompendiumColorTheme.DARK_GRAY).append(" · streak ")
                .append(CompendiumColorTheme.WHITE).append(aspect.spreeCount());
        }
        if (aspect.suppressedCharge() > 0.05f) {
            builder.append(CompendiumColorTheme.DARK_GRAY).append(" · latent ")
                .append(CompendiumColorTheme.WHITE).append(formatNumber(aspect.suppressedCharge(), 1));
        }
        builder.append(CompendiumColorTheme.DARK_GRAY).append(")");
        return builder.toString();
    }

    private static List<Text> formatRelationshipEntry(RelationshipView view, long currentTick, @Nullable Identifier natureId) {
        List<Text> lines = new ArrayList<>();
        MobEntity other = view.partner();
        RelationshipProfile profile = view.profile();

        String name = other.hasCustomName()
            ? other.getCustomName().getString()
            : other.getType().getName().getString();
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String typeLabel = formatRelationshipType(profile.getType());
        String knownDuration = formatDurationSimple(profile.getRelationshipDuration(currentTick));

        lines.add(Text.literal(
            CompendiumColorTheme.DARK_GRAY + "- "
                + accent + name + CompendiumColorTheme.RESET
                + CompendiumColorTheme.DARK_GRAY + " (" + typeLabel + ") "
                + CompendiumColorTheme.LIGHT_GRAY + "· known " + knownDuration
        ));

        float trustNormalized = MathHelper.clamp((profile.trust() + 1.0f) / 2.0f, 0f, 1f);
        float affectionNormalized = MathHelper.clamp(profile.affection(), 0f, 1f);
        float respectNormalized = MathHelper.clamp(profile.respect(), 0f, 1f);

        lines.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Trust "
            + CompendiumColorTheme.DARK_GRAY + buildMeterBar(trustNormalized, 6, natureId)));
        lines.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Warmth "
            + CompendiumColorTheme.DARK_GRAY + buildMeterBar(affectionNormalized, 6, natureId)));
        lines.add(Text.literal("  " + CompendiumColorTheme.LIGHT_GRAY + "Regard "
            + CompendiumColorTheme.DARK_GRAY + buildMeterBar(respectNormalized, 6, natureId)));

        String harmonyHint = formatHarmonyHint(view.compatibility(), natureId);
        if (harmonyHint != null) {
            lines.add(Text.literal("  " + harmonyHint));
        }

        return lines;
    }

    private static String formatRelationshipType(RelationshipType type) {
        if (type == null) {
            return "Unknown";
        }
        return humanizeWords(type.name());
    }

    private static String formatNumber(float value, int precision) {
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

    private static String formatIdentifierSummary(List<Identifier> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(identifiers.size());
        for (Identifier identifier : identifiers) {
            names.add(humanizeIdentifier(identifier));
        }
        return String.join(", ", names);
    }

    private static String formatHarmonyHint(@Nullable HarmonyCompatibility compatibility, @Nullable Identifier natureId) {
        if (compatibility == null) {
            return null;
        }
        float harmony = Math.max(0f, compatibility.harmonyStrength());
        float disharmony = Math.max(0f, compatibility.disharmonyStrength());
        if (harmony < 0.05f && disharmony < 0.05f) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(CompendiumColorTheme.DARK_GRAY).append("Resonance: ");
        if (harmony >= disharmony + 0.1f) {
            builder.append(CompendiumColorTheme.getNatureAccentCode(natureId))
                .append(describeHarmonyFeeling(harmony));
            String sets = formatIdentifierSummary(compatibility.harmonySetIds());
            if (!sets.isEmpty()) {
                builder.append(CompendiumColorTheme.DARK_GRAY).append(" [")
                    .append(CompendiumColorTheme.WHITE).append(sets)
                    .append(CompendiumColorTheme.DARK_GRAY).append("]");
            }
        } else if (disharmony >= harmony + 0.1f) {
            builder.append("§c").append(describeDisharmonyFeeling(disharmony));
            String sets = formatIdentifierSummary(compatibility.disharmonySetIds());
            if (!sets.isEmpty()) {
                builder.append(CompendiumColorTheme.DARK_GRAY).append(" [")
                    .append(CompendiumColorTheme.WHITE).append(sets)
                    .append(CompendiumColorTheme.DARK_GRAY).append("]");
            }
        } else {
            builder.append(CompendiumColorTheme.getNatureAccentCode(natureId)).append("mixed currents");
            List<String> fragments = new ArrayList<>();
            String positive = formatIdentifierSummary(compatibility.harmonySetIds());
            if (!positive.isEmpty()) {
                fragments.add(CompendiumColorTheme.WHITE + positive + CompendiumColorTheme.DARK_GRAY);
            }
            String negative = formatIdentifierSummary(compatibility.disharmonySetIds());
            if (!negative.isEmpty()) {
                fragments.add("§c" + negative + CompendiumColorTheme.DARK_GRAY);
            }
            if (!fragments.isEmpty()) {
                builder.append(" [").append(String.join(CompendiumColorTheme.DARK_GRAY + " · ", fragments)).append("]");
            }
        }
        return builder.toString();
    }

    private static String describeHarmonyFeeling(float strength) {
        if (strength >= 2.5f) {
            return "strong alignment";
        }
        if (strength >= 1.5f) {
            return "in tune";
        }
        if (strength >= 0.5f) {
            return "soft echoes";
        }
        return "faint pull";
    }

    private static String describeDisharmonyFeeling(float strength) {
        if (strength >= 2.5f) {
            return "sharp discord";
        }
        if (strength >= 1.5f) {
            return "uneasy tension";
        }
        if (strength >= 0.5f) {
            return "restless drift";
        }
        return "subtle friction";
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
                bar.append(fill).append("▮");
            } else {
                bar.append(faded).append("▯");
            }
        }
        bar.append(deep).append("]").append(CompendiumColorTheme.RESET)
            .append(" ")
            .append(CompendiumColorTheme.formatInlineBadge(Math.round(clamped * 100) + "%", natureId));
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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "• "
                    + CompendiumColorTheme.LIGHT_GRAY + "Shielded "
                    + accent + protectionCount + CompendiumColorTheme.LIGHT_GRAY + " hits"
                    + CompendiumColorTheme.DARK_GRAY + " · "
                    + CompendiumColorTheme.LIGHT_GRAY + "Redirected "
                    + CompendiumColorTheme.WHITE + formatNumber((float) totalDamage, 1)
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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "• "
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
                stats.add(Text.literal("  " + CompendiumColorTheme.DARK_GRAY + "• "
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

        // Format timestamp
        long eventAge = currentTick - event.timestamp();
        String timeStr = formatEventAge(eventAge);

        lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "⏱ " + timeStr));

        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String eventDesc;
        switch (event.eventType()) {
            case HistoryEvent.EventType.LEVEL_UP -> {
                String data = event.eventData();
                int level = extractIntValue(data, "level");
                if (level == 0) {
                    level = extractIntValue(data, "new_level");
                }
                StringBuilder builder = new StringBuilder();
                builder.append(CompendiumColorTheme.LIGHT_GRAY).append("Reached level ")
                    .append(accent).append(level).append(CompendiumColorTheme.LIGHT_GRAY);
                eventDesc = builder.toString();
            }
            case HistoryEvent.EventType.ROLE_CHANGE -> {
                String toRole = formatRoleName(extractStringValue(event.eventData(), "to"));
                eventDesc = CompendiumColorTheme.LIGHT_GRAY + "Assumed role "
                    + accent + toRole + CompendiumColorTheme.LIGHT_GRAY;
            }
            case HistoryEvent.EventType.ACHIEVEMENT -> {
                String achievementName = extractAchievementName(event.eventData());
                eventDesc = CompendiumColorTheme.LIGHT_GRAY + "Achievement: "
                    + accent + achievementName + CompendiumColorTheme.LIGHT_GRAY;
            }
            case HistoryEvent.EventType.TRADE -> {
                String toName = extractStringValue(event.eventData(), "to_name");
                String partner = "Unknown".equals(toName) ? "a new handler" : toName;
                eventDesc = CompendiumColorTheme.LIGHT_GRAY + "Traded to "
                    + accent + partner + CompendiumColorTheme.LIGHT_GRAY;
            }
            case HistoryEvent.EventType.COMBAT -> {
                boolean victory = event.eventData() != null && event.eventData().contains("\"result\":\"victory\"");
                String opponent = formatCombatOpponent(extractStringValue(event.eventData(), "opponent"));
                String verdictColor = victory ? accent : "§c";
                StringBuilder builder = new StringBuilder();
                builder.append(verdictColor).append(victory ? "Victory" : "Fell")
                    .append(CompendiumColorTheme.LIGHT_GRAY).append(victory ? " over " : " to ")
                    .append(accent).append(opponent).append(CompendiumColorTheme.LIGHT_GRAY);
                eventDesc = builder.toString();
            }
            case HistoryEvent.EventType.MOOD_MILESTONE -> {
                String mood = formatMoodName(extractStringValue(event.eventData(), "mood"));
                float intensity = extractFloatValue(event.eventData(), "intensity");
                StringBuilder builder = new StringBuilder();
                builder.append(CompendiumColorTheme.LIGHT_GRAY).append("Felt ")
                    .append(accent).append(mood);
                if (intensity > 0f) {
                    int percent = Math.round(Math.max(0f, Math.min(1f, intensity)) * 100);
                    builder.append(CompendiumColorTheme.DARK_GRAY).append(" (" + percent + "%)");
                }
                builder.append(CompendiumColorTheme.LIGHT_GRAY);
                eventDesc = builder.toString();
            }
            default -> eventDesc = CompendiumColorTheme.LIGHT_GRAY + humanizeWords(event.eventType());
        }

        lines.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "• " + eventDesc));

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
                currentPage.add(Text.empty());
                linesOnPage = 2;
            }

            String topicName = getTopicName(rumor.topicId());
            String heardAgo = formatElapsed(rumor.lastHeardTick(), currentTick);
            String heardFragment = "Never".equals(heardAgo) ? "never heard" : "heard " + heardAgo;
            boolean witnessed = rumor.lastWitnessTick() > 0;
            String status = witnessed ? "witnessed" : "overheard";
            String shareDetail = rumor.shareCount() > 0
                ? "shared " + rumor.shareCount() + "x"
                : "kept quiet";

            currentPage.add(Text.literal(CompendiumColorTheme.DARK_GRAY + "• "
                + CompendiumColorTheme.LIGHT_GRAY + topicName
                + CompendiumColorTheme.DARK_GRAY + " · "
                + CompendiumColorTheme.LIGHT_GRAY + status
                + CompendiumColorTheme.DARK_GRAY + " · "
                + CompendiumColorTheme.LIGHT_GRAY + heardFragment));

            String confBar = buildConfidenceBar(rumor.confidence() * 100, natureId);
            currentPage.add(Text.literal("    " + confBar
                + CompendiumColorTheme.DARK_GRAY + " · "
                + CompendiumColorTheme.LIGHT_GRAY + shareDetail));

            if (hasQuote) {
                Text paraphrased = rumor.paraphrasedCopy();
                currentPage.add(Text.literal("    " + CompendiumColorTheme.DARK_GRAY + "“")
                    .append(paraphrased)
                    .append(Text.literal(CompendiumColorTheme.DARK_GRAY + "”")));
            }

            linesOnPage += projectedLines;
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
        float normalized = MathHelper.clamp(percentage / 100f, 0f, 1f);
        return buildMeterBar(normalized, 10, natureId);
    }
}


