package woflo.petsplus.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
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

/**
 * Builds and displays the Pet Compendium as an interactive written book.
 * Features nature-themed coloring, clickable table of contents, and hover tooltips.
 */
public class CompendiumBookBuilder {
    
    private static final int MAX_LINES_PER_PAGE = 14;
    private static final int MAX_EMOTION_CUES = 15;
    private static final int MAX_GOSSIP_ENTRIES = 15;
    private static final int MAX_HISTORY_EVENTS = 10;
    
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
        
        // Store the book in the player's off-hand temporarily to open it
        ItemStack offHandBackup = player.getOffHandStack().copy();
        player.setStackInHand(Hand.OFF_HAND, book);
        
        // Send packet to open the book in the off-hand
        player.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(Hand.OFF_HAND));
        
        // Restore the original off-hand item
        // We schedule this to happen after the client opens the GUI
        player.getEntityWorld().getServer().execute(() -> {
            player.setStackInHand(Hand.OFF_HAND, offHandBackup);
        });
    }
    
    private static String buildTitle(MobEntity pet) {
        if (pet.hasCustomName()) {
            return pet.getCustomName().getString() + "'s Journey";
        }
        return pet.getType().getName().getString() + " Compendium";
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
        rawPages.set(tocIndex, buildTableOfContents(pageMap, natureId));
        
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
        
        // Pet name
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
            pet.getType().getName().getString();
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
            String natureName = formatNatureName(natureId);
            page.append(Text.literal(CompendiumColorTheme.LIGHT_GRAY + "Nature:\n"));
            page.append(Text.literal(accentCode + "  " + natureName + "\n\n"));
            
            // Nature description hint
            page.append(Text.literal(CompendiumColorTheme.DARK_GRAY + "(This colors their\npersonality)\n\n"));
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
    
    private static Text buildTableOfContents(Map<String, Integer> pageMap, @Nullable Identifier natureId) {
        MutableText page = Text.literal("");
        
        page.append(Text.literal(CompendiumColorTheme.formatSectionHeader("Table of Contents", natureId) + "\n\n"));
        
        // Add clickable links to each section
        page.append(createPageLink("⚔ Combat Record", pageMap.get("combat"), natureId));
        page.append(createPageLink("📜 History Journal", pageMap.get("history"), natureId));
        page.append(createPageLink("💭 Emotion Cues", pageMap.get("emotions"), natureId));
        page.append(createPageLink("🗨 Gossip & Rumors", pageMap.get("gossip"), natureId));
        
        page.append(Text.literal("\n\n" + CompendiumColorTheme.DARK_GRAY + "(Click to jump)"));
        
        return page;
    }
    
    private static Text createPageLink(String label, Integer pageNum, @Nullable Identifier natureId) {
        final int finalPageNum = (pageNum != null) ? pageNum : 1;
        
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
     * Converts a list of Text lines into a single Text page.
     */
    private static Text linesToPage(List<Text> lines, @Nullable Identifier natureId) {
        MutableText page = Text.literal("");
        
        for (int i = 0; i < lines.size(); i++) {
            page.append(lines.get(i));
            if (i < lines.size() - 1) {
                page.append("\n");
            }
        }
        
        return page;
    }
    
    private static String buildHealthBar(float current, float max, @Nullable Identifier natureId) {
        float percentage = current / max;
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
        
        bar.append("\n  ").append(CompendiumColorTheme.LIGHT_GRAY)
            .append(String.format("%.1f", current))
            .append(CompendiumColorTheme.DARK_GRAY).append(" / ")
            .append(CompendiumColorTheme.WHITE).append(String.format("%.1f", max));
        
        return bar.toString();
    }
    
    private static String formatNatureName(Identifier natureId) {
        String path = natureId.getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1).toLowerCase().replace('_', ' ');
    }
}

