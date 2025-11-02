package woflo.petsplus.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.morality.MalevolenceLedger;
import woflo.petsplus.state.modules.ProgressionModule;

import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds and displays the Pet Compendium as an interactive written book.
 * Features nature-themed coloring, clickable table of contents, and hover tooltips.
 */
public class CompendiumBookBuilder {
    
    private static final int MAX_EMOTION_CUES = 15;
    private static final int MAX_GOSSIP_ENTRIES = 15;
    private static final int MAX_HISTORY_EVENTS = 10;
    private static final int MAX_TITLE_LENGTH = 20;
    private static final int MAX_NATURE_NAME_LENGTH = 25;
    private static final int MAX_PAGE_NUMBER = 100; // Safety limit for page numbers
    private static final int MAX_BOOK_LINE_WIDTH = 50;
    
    /**
     * Opens the Pet Compendium book for a player.
     */
    public static void openCompendium(ServerPlayerEntity player, MobEntity pet, PetComponent pc, long currentTick) {
        if (player == null || pet == null || pc == null) {
            return;
        }
        
        Identifier natureId = pc.getNatureId();
        String title = buildTitle(pet);
        String author = player.getName().getString();
        
        List<RawFilteredPair<Text>> textPages = buildAllPages(player, pet, pc, currentTick, natureId);
        
        // Create a written book with the compendium content
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, 
            new WrittenBookContentComponent(RawFilteredPair.of(title), author, 0, textPages, true));

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            woflo.petsplus.Petsplus.LOGGER.warn("Unable to resolve server instance when opening Pet Compendium for {}", player.getName().getString());
            return;
        }
        
        // Store the current off-hand stack so we can restore it later
        ItemStack offHandBackup = player.getOffHandStack().copy();
        ItemStack currentOffHand = player.getOffHandStack();
        
        try {
            // Only replace the off-hand item if it's different from what we're trying to restore
            if (!ItemStack.areEqual(currentOffHand, book)) {
                player.setStackInHand(Hand.OFF_HAND, book);
                player.playerScreenHandler.sendContentUpdates();
            }
            
            // Let vanilla handle syncing and opening the written book screen
            player.useBook(player.getStackInHand(Hand.OFF_HAND), Hand.OFF_HAND);
        } catch (Exception exception) {
            // Fallback message if GUI fails to open
            player.sendMessage(
                Text.literal("Unable to open the Pet Compendium.")
                    .formatted(Formatting.RED)
                    .append(Text.literal(" Please report this issue.").formatted(Formatting.GRAY)),
                false
            );
            woflo.petsplus.Petsplus.LOGGER.error("Failed to open Pet Compendium written book UI", exception);
        } finally {
            // Restore the original off-hand item after book UI closes
            try {
                server.execute(() -> {
                    // Double-check the current stack before restoring
                    ItemStack currentStack = player.getOffHandStack();
                    if (currentStack.getItem() == Items.WRITTEN_BOOK) {
                        player.setStackInHand(Hand.OFF_HAND, offHandBackup);
                        player.playerScreenHandler.sendContentUpdates();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Server is stopping or cannot schedule, restore immediately as fallback
                ItemStack currentStack = player.getOffHandStack();
                if (currentStack.getItem() == Items.WRITTEN_BOOK) {
                    player.setStackInHand(Hand.OFF_HAND, offHandBackup);
                    player.playerScreenHandler.sendContentUpdates();
                }
            }
        }
    }

    /**
         * Builds the title for the compendium book.
         */
        private static String buildTitle(MobEntity pet) {
            String baseTitle;
            String petName = getPetDisplayName(pet);
    
            // Truncate long pet names without adding ellipses (keeps underscores intact)
            if (petName.length() > MAX_TITLE_LENGTH) {
                petName = petName.substring(0, MAX_TITLE_LENGTH);
            }
    
            if (pet.hasCustomName()) {
                baseTitle = petName + "'s Journey";
            } else {
                String typeName = getEntityTypeName(pet);
                baseTitle = typeName + " Compendium";
            }
    
            // Cap title length without ellipses for visual cohesion
            if (baseTitle.length() > 32) {
                baseTitle = baseTitle.substring(0, 32);
            }
    
            return baseTitle;
        }
    
        /**
         * Returns the display name for the pet, preferring custom name, then type name, then fallback.
         */
        private static String getPetDisplayName(MobEntity pet) {
            if (pet.hasCustomName()) {
                String customName = pet.getCustomName().getString();
                return (customName != null && !customName.isEmpty()) ? customName : "Unnamed";
            }
            return getEntityTypeName(pet);
        }
    
        /**
         * Returns the entity type name or "Unknown" if not available.
         */
        private static String getEntityTypeName(MobEntity pet) {
            if (pet.getType() != null && pet.getType().getName() != null) {
                String typeName = pet.getType().getName().getString();
                return (typeName != null && !typeName.isEmpty()) ? typeName : "Unknown";
            }
            return "Unknown";
        }

    /**
        // Do NOT add "Table of Contents" to sections:
        // Including it would create a circular reference, causing infinite navigation loops or incorrect page numbering in the ToC.
     */
    private static List<RawFilteredPair<Text>> buildAllPages(ServerPlayerEntity player, MobEntity pet, PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<Text> rawPages = new ArrayList<>();
        List<CompendiumSection> sections = new ArrayList<>();

        // Profile overview
        List<Text> profilePages = buildProfilePages(pet, pc, currentTick, natureId);
        rawPages.addAll(profilePages);
        sections.add(new CompendiumSection("Profile Overview", 1));

        // Table of Contents placeholder (will be replaced later)
        int tocIndex = rawPages.size();
        rawPages.add(Text.literal("")); // Placeholder for ToC

        // Capabilities timeline
        List<List<Text>> capabilitiesPages = PetCompendiumDataExtractor.buildCapabilitiesPages(pc, natureId);
        if (!capabilitiesPages.isEmpty()) {
            sections.add(new CompendiumSection("Capabilities", rawPages.size() + 1));
            for (List<Text> page : capabilitiesPages) {
                rawPages.add(linesToPage(page, natureId));
            }
        }

        // Pack Bonds (relationships)
        List<Text> relationshipPages = buildRelationshipSection(player, pet, pc, currentTick, natureId);
        if (!relationshipPages.isEmpty()) {
            sections.add(new CompendiumSection("Pack Bonds", rawPages.size() + 1));
            rawPages.addAll(relationshipPages);
        }

        // Behavioral Influence (personality drift & temperament)
        List<Text> moralityPages = buildMoralitySection(pc, currentTick, natureId);
        if (!moralityPages.isEmpty()) {
            sections.add(new CompendiumSection("Behavioral Influence", rawPages.size() + 1));
            rawPages.addAll(moralityPages);
        }

        // Species Memory
        List<Text> speciesMemoryPages = buildSpeciesMemorySection(pc, natureId);
        if (!speciesMemoryPages.isEmpty()) {
            sections.add(new CompendiumSection("Species Memory", rawPages.size() + 1));
            rawPages.addAll(speciesMemoryPages);
        }

        // Combat record
        sections.add(new CompendiumSection("Combat Record", rawPages.size() + 1));
        rawPages.addAll(buildCombatPages(pc, currentTick, natureId));

        // History journal
        sections.add(new CompendiumSection("History Journal", rawPages.size() + 1));
        rawPages.addAll(buildHistoryPages(pc, currentTick, natureId));

        // Emotion cues
        sections.add(new CompendiumSection("Emotion Cues", rawPages.size() + 1));
        rawPages.addAll(buildEmotionPages(pc, currentTick, natureId));

        // Gossip & rumors
        sections.add(new CompendiumSection("Gossip & Rumors", rawPages.size() + 1));
        rawPages.addAll(buildGossipPages(pc, currentTick, natureId));

        // Now build the actual ToC with correct page numbers
        rawPages.set(tocIndex, buildTableOfContents(sections, natureId, rawPages.size()));
        
        // Convert to RawFilteredPair
        return rawPages.stream()
            .map(RawFilteredPair::of)
            .toList();
    }
    
    private static List<Text> buildProfilePages(MobEntity pet, PetComponent pc, long currentTick,
                                                @Nullable Identifier natureId) {

        List<Text> logicalLines = new ArrayList<>();

        // Pet name
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() :
            pet.getType().getName().getString();
        if (petName.length() > MAX_TITLE_LENGTH) {
            petName = petName.substring(0, MAX_TITLE_LENGTH);
        }
        logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Name", petName, natureId)));

        // Role & nature
        Identifier roleId = pc.getRoleId();
        PetRoleType roleType = roleId != null ? PetsPlusRegistries.petRoleTypeRegistry().get(roleId) : null;
        String roleName = PetCompendiumDataExtractor.getRoleDisplayName(roleId, roleType);
        if (roleName == null || roleName.isBlank()) {
            roleName = "Unassigned";
        }
        logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Role", roleName, natureId)));

        Identifier nature = natureId != null ? natureId : pc.getNatureId();
        if (nature != null) {
            String natureName = NatureDisplayUtil.formatNatureName(pc, nature, MAX_NATURE_NAME_LENGTH);
            logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Nature", natureName, nature)));
        }

        long bondStrength = pc.getBondStrength();
        if (bondStrength > 0L) {
            String bondLevel = PetCompendiumDataExtractor.getBondLevel(bondStrength);
            logicalLines.add(Text.literal(
                CompendiumColorTheme.formatLabelValue("Bond", bondLevel, natureId)
                    + CompendiumColorTheme.DARK_GRAY + " (" + bondStrength + ")" + CompendiumColorTheme.RESET
            ));
        }

        String lifespan = PetCompendiumDataExtractor.formatLifespan(pc, currentTick);
        if (lifespan != null) {
            logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Known for", lifespan, natureId)));
        }

        // Level & experience
        int level = pc.getLevel();
        int xpProgress = Math.round(MathHelper.clamp(pc.getXpProgress(), 0f, 1f) * 100f);
        logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Level", level + " · " + xpProgress + "%", natureId)));
        String xpBar = PetCompendiumDataExtractor.buildMeterBar(MathHelper.clamp(pc.getXpProgress(), 0f, 1f), 8, natureId);
        logicalLines.add(Text.literal("  " + xpBar));

        // Vitality
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        float normalizedHealth = maxHealth <= 0f ? 0f : MathHelper.clamp(health / maxHealth, 0f, 1f);
        String healthBar = PetCompendiumDataExtractor.buildMeterBar(normalizedHealth, 6, natureId);
        logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Vitality",
            formatNumber(health, 1) + " / " + formatNumber(maxHealth, 1) + " " + healthBar, natureId)));

        MalevolenceLedger ledger = pc.getMalevolenceLedger();
        if (ledger != null) {
            MalevolenceLedger.MoralitySnapshot snapshot = ledger.describe();
            if (snapshot != null) {
                String state = snapshot.active() ? "malevolent" : "benign";
                logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Behavioral Influence",
                    formatNumber(snapshot.score(), 1) + " (" + state + ")", natureId)));
            }
        }

        PetComponent.HarmonyState harmonyState = pc.getHarmonyState();
        if (harmonyState != null) {
            float harmonyStrength = Math.max(0f, harmonyState.harmonyStrength());
            float disharmonyStrength = Math.max(0f, harmonyState.disharmonyStrength());
            logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Harmony",
                formatNumber(harmonyStrength, 1) + " / " + formatNumber(disharmonyStrength, 1), natureId)));
        }

        // Progression unlocks
        ProgressionModule progressionModule = pc.getProgressionModule();
        if (progressionModule != null) {
            int milestoneCount = progressionModule.getUnlockedMilestones().size();
            int abilityCount = progressionModule.getUnlockedAbilities().size();
            if (milestoneCount > 0) {
                logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Milestones",
                    milestoneCount + " unlocked", natureId)));
            }
            if (abilityCount > 0) {
                logicalLines.add(Text.literal(CompendiumColorTheme.formatLabelValue("Abilities",
                    abilityCount + " learned", natureId)));
            }
        }

        // Now paginate the logical lines to avoid exceeding the book's line limit per page
        final int MAX_LINES_PER_PAGE = 14;
        List<Text> pages = new ArrayList<>();
        List<Text> pageBuffer = new ArrayList<>();
        int used = 0;

        // Flush current page buffer into a page with header and divider
        java.util.function.Consumer<Boolean> flushPage = (isFirst) -> {
            if (pageBuffer.isEmpty()) {
                return;
            }
            List<Text> composed = new ArrayList<>();
            // Header and divider at the top of each page
            composed.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Profile Overview", natureId)));
            composed.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            composed.add(Text.empty());
            composed.addAll(pageBuffer);
            pages.add(linesToPage(composed, natureId));
            pageBuffer.clear();
        };

        for (Text line : logicalLines) {
            List<Text> wrapped = wrapLinePreservingFormatting(line);
            int needed = Math.max(1, wrapped.size());

            // Count header (3 lines) when starting a new page
            if (used == 0) {
                used = 3; // header + divider + blank
            }

            if (used + needed > MAX_LINES_PER_PAGE) {
                // Finish current page and start a new one
                flushPage.accept(false);
                used = 3; // header + divider + blank on new page
            }

            pageBuffer.add(line);
            used += needed;
        }

        // Flush remaining content
        flushPage.accept(true);

        // Ensure at least one page exists (with just header/divider) if no lines
        if (pages.isEmpty()) {
            List<Text> composed = new ArrayList<>();
            composed.add(Text.literal(CompendiumColorTheme.formatSectionHeader("Profile Overview", natureId)));
            composed.add(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId)));
            composed.add(Text.empty());
            pages.add(linesToPage(composed, natureId));
        }

        return pages;
    }

    private static Text buildTableOfContents(List<CompendiumSection> sections, @Nullable Identifier natureId,
                                             int totalPages) {
        MutableText page = Text.literal("");

        page.append(Text.literal(CompendiumColorTheme.formatSectionHeader("Table of Contents", natureId) + "\n"));
        page.append(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId) + "\n\n"));

        for (CompendiumSection section : sections) {
            if (section.page() <= 0) {
                continue;
            }
            page.append(createPageLink(section.label(), section.page(), natureId, totalPages));
        }

        return page;
    }

    private static Text createPageLink(String label, Integer pageNum, @Nullable Identifier natureId, int totalPages) {
        // Validate page number
        int finalPageNum = (pageNum != null && pageNum > 0 && pageNum <= totalPages && pageNum <= MAX_PAGE_NUMBER) ? pageNum : 1;

        String bullet = CompendiumColorTheme.getNatureDeepShadowCode(natureId) + "▹ ";
        String accent = CompendiumColorTheme.getNatureAccentCode(natureId);
        String pageRef = CompendiumColorTheme.DARK_GRAY + "[p" + finalPageNum + "]";

        return Text.literal(bullet + accent + label + " " + pageRef + "\n")
            .styled(style -> style
                .withClickEvent(new ClickEvent.ChangePage(finalPageNum))
                .withHoverEvent(new HoverEvent.ShowText(
                    Text.literal(CompendiumColorTheme.formatSoftNote("Jump to page " + finalPageNum, natureId))))
            );
    }
    
    private static List<Text> buildCombatPages(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<Text> pages = new ArrayList<>();
        List<Text> combatLines = PetCompendiumDataExtractor.buildCombatStatsPage(pc, currentTick, natureId);

        pages.add(linesToPage(combatLines, natureId));
        return pages;
    }
    
    private static List<Text> buildHistoryPages(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> historyPages = PetCompendiumDataExtractor.buildJournalPages(
            pc, currentTick, MAX_HISTORY_EVENTS, natureId);
        
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : historyPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        
        if (result.isEmpty()) {
            MutableText emptyPage = Text.literal(CompendiumColorTheme.formatSectionHeader("History Journal", natureId) + "\n");
            emptyPage.append(Text.literal(CompendiumColorTheme.buildSectionDivider(natureId) + "\n\n"));
            emptyPage.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "No recorded events."));
            result.add(emptyPage);
        }
        
        return result;
    }
    
    private static List<Text> buildEmotionPages(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> emotionPages = PetCompendiumDataExtractor.buildEmotionJournalPages(
            pc, currentTick, MAX_EMOTION_CUES, natureId);
        
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : emotionPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        
        return result;
    }
    
    private static List<Text> buildGossipPages(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> gossipPages = PetCompendiumDataExtractor.buildGossipPages(
            pc, currentTick, MAX_GOSSIP_ENTRIES, natureId);

        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : gossipPages) {
            result.add(linesToPage(pageLines, natureId));
        }

        return result;
    }

    private static List<Text> buildMoralitySection(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> moralityPages = PetCompendiumDataExtractor.buildMoralityPages(pc, currentTick, natureId);
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : moralityPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        return result;
    }

    private static List<Text> buildRelationshipSection(ServerPlayerEntity player, MobEntity pet, PetComponent pc,
                                                       long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> relationshipPages = PetCompendiumDataExtractor.buildRelationshipPages(
            player, pet, pc, currentTick, natureId);
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : relationshipPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        return result;
    }

    private static List<Text> buildSpeciesMemorySection(PetComponent pc, @Nullable Identifier natureId) {
        List<List<Text>> speciesMemoryPages = PetCompendiumDataExtractor.buildSpeciesMemoryPages(pc, natureId);
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : speciesMemoryPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        return result;
    }
    
    /**
     * Converts a list of Text lines into a single Text page with proper line wrapping.
     */
    private static Text linesToPage(List<Text> lines, @Nullable Identifier natureId) {
        MutableText page = Text.literal("");
        
        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            List<Text> wrappedSegments = wrapLinePreservingFormatting(line);

            for (int j = 0; j < wrappedSegments.size(); j++) {
                page.append(wrappedSegments.get(j));
                if (j < wrappedSegments.size() - 1 || i < lines.size() - 1) {
                    page.append("\n");
                }
            }
        }

        return page;
    }

    private static List<Text> wrapLinePreservingFormatting(Text line) {
        String literalString = extractLiteralWithFormatting(line);
        if (visibleLength(literalString) <= MAX_BOOK_LINE_WIDTH) {
            return List.of(line.copy());
        }

        List<String> wrapped = wrapLiteralWithFormatting(literalString, MAX_BOOK_LINE_WIDTH);
        List<Text> result = new ArrayList<>(wrapped.size());
        for (String fragment : wrapped) {
            MutableText fragmentText = Text.literal(fragment);
            fragmentText.setStyle(line.getStyle());
            result.add(fragmentText);
        }
        return result;
    }

    private static String extractLiteralWithFormatting(Text text) {
        String literalString = text.getLiteralString();
        if (literalString != null) {
            return literalString;
        }

        StringBuilder builder = new StringBuilder();
        TextVisitFactory.visitFormatted(text, Style.EMPTY, (index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });

        return builder.toString();
    }

    private static List<String> wrapLiteralWithFormatting(String text, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        LegacyFormattingState formattingState = new LegacyFormattingState();

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        StringBuilder current = new StringBuilder();
        int visibleCount = 0;
        String indentForContinuation = "";
        String pendingIndent = "";
        boolean capturingIndent = true;

        for (int index = 0; index < tokens.size(); ) {
            String token = tokens.get(index);
            boolean isWhitespace = token.chars().allMatch(Character::isWhitespace);
            int tokenVisible = visibleLength(token);

            if (visibleCount == 0 && !pendingIndent.isEmpty()) {
                current.append(pendingIndent);
                visibleCount += visibleLength(pendingIndent);
                formattingState.acceptSegment(pendingIndent);
                pendingIndent = "";
            }

            if (!isWhitespace && visibleCount > 0 && visibleCount + tokenVisible > maxWidth) {
                wrappedLines.add(current.toString());
                current = new StringBuilder();
                visibleCount = 0;
                String prefix = formattingState.getActiveFormatting();
                if (!prefix.isEmpty()) {
                    current.append(prefix);
                }
                pendingIndent = indentForContinuation;
                continue;
            }

            if (isWhitespace && visibleCount > 0 && visibleCount + tokenVisible > maxWidth) {
                wrappedLines.add(current.toString());
                current = new StringBuilder();
                visibleCount = 0;
                String prefix = formattingState.getActiveFormatting();
                if (!prefix.isEmpty()) {
                    current.append(prefix);
                }
                pendingIndent = indentForContinuation;
                index++;
                continue;
            }

            if (isWhitespace && visibleCount == 0) {
                current.append(token);
                visibleCount += tokenVisible;
                formattingState.acceptSegment(token);
                if (capturingIndent && tokenVisible > 0) {
                    indentForContinuation += token;
                }
                index++;
                continue;
            }

            if (visibleCount == 0 && !isWhitespace && !startsWithFormattingCode(token)) {
                String prefix = formattingState.getActiveFormatting();
                if (!prefix.isEmpty()) {
                    current.append(prefix);
                }
            }

            current.append(token);
            visibleCount += tokenVisible;
            formattingState.acceptSegment(token);

            if (!isWhitespace && tokenVisible > 0) {
                capturingIndent = false;
            }

            if (!isWhitespace && visibleCount >= maxWidth) {
                wrappedLines.add(current.toString());
                current = new StringBuilder();
                visibleCount = 0;
                String prefix = formattingState.getActiveFormatting();
                if (!prefix.isEmpty()) {
                    current.append(prefix);
                }
                pendingIndent = indentForContinuation;
            }

            index++;
        }

        if (current.length() > 0) {
            wrappedLines.add(current.toString());
        }

        if (wrappedLines.isEmpty()) {
            wrappedLines.add("");
        }

        return wrappedLines;
    }

    private static int visibleLength(String text) {
        int length = 0;
        boolean skipNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (c == '§' && i + 1 < text.length()) {
                skipNext = true;
                continue;
            }
            if (c != '\n' && c != '\r') {
                length++;
            }
        }
        return length;
    }

    private static boolean startsWithFormattingCode(String token) {
        if (token.length() < 2) {
            return false;
        }
        if (token.charAt(0) != '§') {
            return false;
        }
        char code = Character.toLowerCase(token.charAt(1));
        return LEGACY_FORMAT_CODES.indexOf(code) >= 0;
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");
    private static final String LEGACY_FORMAT_CODES = "0123456789abcdefklmnor";

    private static final class LegacyFormattingState {
        private String colorCode = "";
        private final List<Character> modifiers = new ArrayList<>();

        void acceptSegment(String segment) {
            for (int i = 0; i < segment.length() - 1; i++) {
                if (segment.charAt(i) == '§') {
                    char code = Character.toLowerCase(segment.charAt(i + 1));
                    ingestCode(code);
                    i++;
                }
            }
        }

        private void ingestCode(char code) {
            if (code == 'r') {
                colorCode = "";
                modifiers.clear();
                return;
            }

            if (isColorCode(code)) {
                colorCode = "§" + code;
                modifiers.clear();
                return;
            }

            if (isModifierCode(code) && modifiers.stream().noneMatch(existing -> existing == code)) {
                modifiers.add(code);
            }
        }

        String getActiveFormatting() {
            StringBuilder builder = new StringBuilder(colorCode);
            for (char modifier : modifiers) {
                builder.append('§').append(modifier);
            }
            return builder.toString();
        }

        private boolean isColorCode(char code) {
            return "0123456789abcdef".indexOf(code) >= 0;
        }

        private boolean isModifierCode(char code) {
            return "klmno".indexOf(code) >= 0;
        }
    }
    
    private static String formatNumber(float value, int precision) {
        return String.format(Locale.ROOT, "%1$." + Math.max(0, precision) + "f", value);
    }

    private record CompendiumSection(String label, int page) {
    }
}

