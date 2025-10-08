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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        page.append(Text.literal(accentCode + "§l⬥ ").append(Text.literal("§f§lPet Compendium§r\n")));
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
        page.append(createPageLink("⚔ Combat Record", pageMap.get("combat"), natureId, totalPages));
        page.append(createPageLink("📜 History Journal", pageMap.get("history"), natureId, totalPages));
        page.append(createPageLink("💭 Emotion Cues", pageMap.get("emotions"), natureId, totalPages));
        page.append(createPageLink("🗨 Gossip & Rumors", pageMap.get("gossip"), natureId, totalPages));
        
        page.append(Text.literal("\n\n" + CompendiumColorTheme.DARK_GRAY + "(Click to jump)"));
        
        return page;
    }
    
    private static Text createPageLink(String label, Integer pageNum, @Nullable Identifier natureId, int totalPages) {
        // Validate page number to prevent out-of-bounds navigation
        int finalPageNum = (pageNum != null && pageNum > 0 && pageNum <= totalPages && pageNum <= MAX_PAGE_NUMBER) ? pageNum : 1;
        
        String accentCode = CompendiumColorTheme.getNatureAccentCode(natureId);
        
        return Text.literal(CompendiumColorTheme.DARK_GRAY + "▸ " + accentCode + label + "\n")
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
            
            // Check if we need to wrap the line to prevent overflow
            String lineString = line.getString();
            if (lineString.length() > 50) { // Approximate max line length for books
                // Split long lines into multiple lines
                String[] words = lineString.split(" ");
                StringBuilder currentLine = new StringBuilder();
                
                for (String word : words) {
                    if (currentLine.length() + word.length() + 1 > 50) {
                        if (currentLine.length() > 0) {
                            page.append(Text.literal(currentLine.toString()));
                            page.append("\n");
                            currentLine = new StringBuilder();
                        }
                    }
                    
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
                
                if (currentLine.length() > 0) {
                    page.append(Text.literal(currentLine.toString()));
                }
            } else {
                page.append(line);
            }
            
            if (i < lines.size() - 1) {
                page.append("\n");
            }
        }
        
        return page;
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
                bar.append(heartColor).append("♥");
            } else {
                bar.append(CompendiumColorTheme.DARK_GRAY).append("♡");
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

