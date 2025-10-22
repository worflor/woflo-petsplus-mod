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
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

/**
 * Builds and displays the Pet Compendium as an interactive written book.
 * Features nature-themed coloring, clickable table of contents, and hover tooltips.
 */
public class CompendiumBookBuilder {
    
    private static final int MAX_LINES_PER_PAGE = 14;
    private static final int MAX_EMOTION_CUES = 15;
    private static final int MAX_GOSSIP_ENTRIES = 15;
    private static final int MAX_HISTORY_EVENTS = 10;
    private static final int MAX_TITLE_LENGTH = 20;
    private static final int MAX_NATURE_NAME_LENGTH = 25;
    private static final int MAX_PAGE_NUMBER = 100; // Safety limit for page numbers
    private static final float MAX_HEALTH_VALUE = 10000.0f; // Maximum reasonable health value
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
            // If the GUI fails to open, provide a user-facing fallback message
            player.sendMessage(
                Text.literal("Unable to open the Pet Compendium.")
                    .formatted(Formatting.RED)
                    .append(Text.literal(" Please report this issue.").formatted(Formatting.GRAY)),
                false
            );
            woflo.petsplus.Petsplus.LOGGER.error("Failed to open Pet Compendium written book UI", exception);
        } finally {
            // Restore the original off-hand item with a delay to avoid race conditions
            // Use a longer delay to ensure the book UI is fully closed
            server.execute(() -> {
                // Double-check the current stack before restoring
                ItemStack currentStack = player.getOffHandStack();
                if (currentStack.getItem() == Items.WRITTEN_BOOK) {
                    player.setStackInHand(Hand.OFF_HAND, offHandBackup);
                    player.playerScreenHandler.sendContentUpdates();
                }
            });
        }
    }
    
    private static String buildTitle(MobEntity pet) {
        String baseTitle;
        if (pet.hasCustomName()) {
            String petName = pet.getCustomName().getString();
            // Truncate long pet names to prevent title overflow
            if (petName.length() > MAX_TITLE_LENGTH) {
                petName = petName.substring(0, MAX_TITLE_LENGTH - 3) + "...";
            }
            baseTitle = petName + "'s Journey";
        } else {
            baseTitle = pet.getType().getName().getString() + " Compendium";
        }
        
        // Ensure title doesn't exceed reasonable length
        if (baseTitle.length() > 32) {
            baseTitle = baseTitle.substring(0, 29) + "...";
        }
        
        return baseTitle;
    }
    
    private static List<RawFilteredPair<Text>> buildAllPages(
            ServerPlayerEntity player,
            MobEntity pet,
            PetComponent pc,
            long currentTick,
            @Nullable Identifier natureId) {
        
        List<Text> rawPages = new ArrayList<>();
        Map<String, Integer> pageMap = new HashMap<>();
        
        // Page 1: Title & Stats
        rawPages.add(buildTitlePage(pet, pc, currentTick, natureId));
        
        // Page 2: Role & Nature
        rawPages.add(buildRoleNaturePage(pet, pc, natureId));
        
        // Reserve slot for ToC (we'll build it after we know all page numbers)
        int tocIndex = rawPages.size();
        rawPages.add(Text.empty()); // Placeholder
        
        // Page 4+: Combat Stats
        pageMap.put("combat", rawPages.size() + 1);
        rawPages.addAll(buildCombatPages(pc, natureId));
        
        // Page N: History Journal
        pageMap.put("history", rawPages.size() + 1);
        rawPages.addAll(buildHistoryPages(pc, currentTick, natureId));
        
        // Page M: Emotion Cues
        pageMap.put("emotions", rawPages.size() + 1);
        rawPages.addAll(buildEmotionPages(player, currentTick, natureId));
        
        // Page K: Gossip & Rumors
        pageMap.put("gossip", rawPages.size() + 1);
        rawPages.addAll(buildGossipPages(pc, currentTick, natureId));
        
        // Now build the actual ToC with correct page numbers
        rawPages.set(tocIndex, buildTableOfContents(pageMap, natureId, rawPages.size()));
        
        // Convert to RawFilteredPair
        return rawPages.stream()
            .map(RawFilteredPair::of)
            .toList();
    }
    
    private static Text buildTitlePage(MobEntity pet, PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        MutableText page = Text.literal("");
        
        // Title with nature accent
        page.append(Text.literal(accentCode + "§l> ").append(Text.literal("§f§lPet Compendium§r\n")));
        page.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "─────────────────\n\n"));
        
        // Pet name with length validation
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
            pet.getType().getName().getString();
        
        // Truncate long pet names to prevent overflow
        if (petName.length() > MAX_TITLE_LENGTH) {
            petName = petName.substring(0, MAX_TITLE_LENGTH - 3) + "...";
        }
        
        page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Name:\n"));
        page.append(Text.literal(CompendiumColorTheme.WHITE + "  " + petName + "\n\n"));
        
        // Level & XP
        int level = pc.getLevel();
        int xpProgress = Math.round(pc.getXpProgress() * 100);
        page.append(Text.literal(CompendiumColorTheme.formatLabelValue("Level", String.valueOf(level), natureId) + "\n"));
        page.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "  (" + xpProgress + "% to next)\n\n"));
        
        // Health
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        String healthBar = buildHealthBar(health, maxHealth, natureId);
        page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Health:\n"));
        page.append(Text.literal("  " + healthBar));
        
        return page;
    }
    
    private static Text buildRoleNaturePage(MobEntity pet, PetComponent pc, @Nullable Identifier natureId) {
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        MutableText page = Text.literal("");
        page.append(Text.literal(CompendiumColorTheme.formatSectionHeader("Identity", natureId) + "\n\n"));
        
        // Role
        String roleName = PetCompendiumDataExtractor.getRoleDisplayName(pc.getRoleId(), 
            woflo.petsplus.api.registry.PetsPlusRegistries.petRoleTypeRegistry().get(pc.getRoleId()));
        page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Role:\n"));
        page.append(Text.literal(accentCode + "  " + roleName + "\n\n"));
        
        // Nature
        if (natureId != null) {
            String natureName = formatNatureName(pc, natureId);
            page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Nature:\n"));
            page.append(Text.literal(accentCode + "  " + natureName + "\n\n"));
            
            page.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "\n\n"));
        }
        
        // Bond strength
        long bondStrength = pc.getBondStrength();
        if (bondStrength > 0) {
            String bondLevel = PetCompendiumDataExtractor.getBondLevel(bondStrength);
            page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Bond:\n"));
            page.append(Text.literal(CompendiumColorTheme.WHITE + "  " + bondLevel));
        }
        
        return page;
    }
    
    private static Text buildTableOfContents(Map<String, Integer> pageMap, @Nullable Identifier natureId, int totalPages) {
        MutableText page = Text.literal("");
        
        page.append(Text.literal(CompendiumColorTheme.formatSectionHeader("Table of Contents", natureId) + "\n\n"));
        
        // Add clickable links to each section with page validation
        page.append(createPageLink("Combat Record", pageMap.get("combat"), natureId, totalPages));
        page.append(createPageLink("History Journal", pageMap.get("history"), natureId, totalPages));
        page.append(createPageLink("Emotion Cues", pageMap.get("emotions"), natureId, totalPages));
        page.append(createPageLink("Gossip & Rumors", pageMap.get("gossip"), natureId, totalPages));
        
        page.append(Text.literal("\n\n" + CompendiumColorTheme.DARK_GRAY + "(Click to jump)"));
        
        return page;
    }
    
    private static Text createPageLink(String label, Integer pageNum, @Nullable Identifier natureId, int totalPages) {
        // Validate page number to prevent out-of-bounds navigation
        int finalPageNum = (pageNum != null && pageNum > 0 && pageNum <= totalPages && pageNum <= MAX_PAGE_NUMBER) ? pageNum : 1;
        
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        return Text.literal(CompendiumColorTheme.DARK_GRAY + "- " + accentCode + label + "\n")
            .styled(style -> style
                .withClickEvent(new ClickEvent.ChangePage(finalPageNum))
                .withHoverEvent(new HoverEvent.ShowText(
                    Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Jump to page " + finalPageNum)))
            );
    }
    
    private static List<Text> buildCombatPages(PetComponent pc, @Nullable Identifier natureId) {
        List<Text> pages = new ArrayList<>();
        List<Text> combatLines = PetCompendiumDataExtractor.buildCombatStatsPage(pc);
        
        pages.add(linesToPage(combatLines, natureId));
        return pages;
    }
    
    private static List<Text> buildHistoryPages(PetComponent pc, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> historyPages = PetCompendiumDataExtractor.buildJournalPages(pc, currentTick, MAX_HISTORY_EVENTS);
        
        List<Text> result = new ArrayList<>();
        for (List<Text> pageLines : historyPages) {
            result.add(linesToPage(pageLines, natureId));
        }
        
        if (result.isEmpty()) {
            MutableText emptyPage = Text.literal(CompendiumColorTheme.formatSectionHeader("History Journal", natureId) + "\n\n");
            emptyPage.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "No recorded events\nyet. Adventures\nawait!"));
            result.add(emptyPage);
        }
        
        return result;
    }
    
    private static List<Text> buildEmotionPages(ServerPlayerEntity player, long currentTick, @Nullable Identifier natureId) {
        List<List<Text>> emotionPages = PetCompendiumDataExtractor.buildEmotionJournalPages(
            player, currentTick, MAX_EMOTION_CUES, natureId);
        
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
    
    private static String buildHealthBar(float current, float max, @Nullable Identifier natureId) {
        // Handle extreme health values to prevent display issues
        if (max <= 0) {
            max = 1.0f; // Prevent division by zero
        }
        
        // Cap health values to reasonable limits
        current = Math.min(current, MAX_HEALTH_VALUE);
        max = Math.min(max, MAX_HEALTH_VALUE);
        
        float percentage = current / max;
        // Ensure percentage is within valid range
        percentage = Math.max(0.0f, Math.min(1.0f, percentage));
        
        int filled = Math.round(percentage * 10);
        filled = Math.max(0, Math.min(10, filled));
        
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        String heartColor = percentage > 0.5f ? "§c" : (percentage > 0.2f ? "§e" : "§4");
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(heartColor).append("#");
            } else {
                bar.append(CompendiumColorTheme.DARK_GRAY).append("-");
            }
        }
        
        // Format health values with appropriate precision
        String currentStr, maxStr;
        if (current >= 1000) {
            currentStr = String.format("%.0f", current);
            maxStr = String.format("%.0f", max);
        } else if (current >= 100) {
            currentStr = String.format("%.1f", current);
            maxStr = String.format("%.1f", max);
        } else {
            currentStr = String.format("%.1f", current);
            maxStr = String.format("%.1f", max);
        }
        
        bar.append("\n  ").append(CompendiumColorTheme.LIGHT_GRAY)
            .append(currentStr)
            .append(CompendiumColorTheme.DARK_GRAY).append(" / ")
            .append(CompendiumColorTheme.WHITE).append(maxStr);
        
        return bar.toString();
    }
    
    private static String formatNatureName(PetComponent pc, Identifier natureId) {
        String natureName;
        
        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            natureName = AstrologyRegistry.getDisplayTitle(pc.getAstrologySignId());
        } else {
            String path = natureId.getPath();
            natureName = path.substring(0, 1).toUpperCase() + path.substring(1).toLowerCase().replace('_', ' ');
        }
        
        // Validate and truncate nature name if too long
        if (natureName.length() > MAX_NATURE_NAME_LENGTH) {
            natureName = natureName.substring(0, MAX_NATURE_NAME_LENGTH - 3) + "...";
        }
        
        return natureName;
    }
}

