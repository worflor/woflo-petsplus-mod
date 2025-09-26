package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Formatting;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.scoreboard.ScoreHolder;


import java.util.*;

/**
 * Enhanced pet inspection system with dynamic, contextual boss bar display.
 * Shows smart, prioritized information that adapts to pet state and context.
 */
public final class PetInspectionManager {
    private PetInspectionManager() {}

    private static final int VIEW_DIST = 12;
    private static final Map<UUID, InspectionState> inspecting = new HashMap<>();
    private static final int LINGER_TICKS = 40; // 2s linger after looking away

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateForPlayer(player);
        }
        
        // Clean up inspection states for disconnected players every 30 seconds
        if (server.getTicks() % 600 == 0) {
            cleanupDisconnectedPlayers(server);
        }
    }

    private static void updateForPlayer(ServerPlayerEntity player) {
        MobEntity pet = findLookedAtPet(player);
        UUID pid = player.getUuid();

        InspectionState state = inspecting.computeIfAbsent(pid, k -> new InspectionState());
        
        if (pet == null) {
            handleLookAway(player, state);
            return;
        }

        handleLookAt(player, pet, state);
    }

    private static void handleLookAway(ServerPlayerEntity player, InspectionState state) {
        if (state.lingerTicks == LINGER_TICKS) {
            // Just stopped looking: extend current bar duration for smooth transition
            BossBarManager.extendDuration(player, LINGER_TICKS);
        }

        if (state.lingerTicks > 0) {
            state.lingerTicks--;
        } else {
            BossBarManager.removeBossBar(player);
            clearEmotionScoreboard(player);
            inspecting.remove(player.getUuid());
        }

        ActionBarCueManager.onPlayerLookedAway(player);
    }

    private static void handleLookAt(ServerPlayerEntity player, MobEntity pet, InspectionState state) {
        // Reset linger and update pet tracking
        state.lingerTicks = LINGER_TICKS;
        UUID newPetId = pet.getUuid();
        
        if (state.lastPetId == null || !state.lastPetId.equals(newPetId)) {
            // New pet - reset all state
            state.reset(newPetId);
        }

        PetComponent comp = PetComponent.get(pet);
        if (comp == null || !comp.isOwnedBy(player)) {
            BossBarManager.removeBossBar(player);
            clearEmotionScoreboard(player);
            inspecting.remove(player.getUuid());
            return;
        }

        ActionBarCueManager.onPlayerLookedAtPet(player, pet);

        state.tick();
        updatePetDisplay(player, pet, comp, state);
    }

    private static void updatePetDisplay(ServerPlayerEntity player, MobEntity pet, PetComponent comp, InspectionState state) {
        // Analyze pet state for smart prioritization
        PetStatus status = analyzePetStatus(pet, comp, state);
        
        // Build display frames based on priority
        List<DisplayFrame> frames = buildDisplayFrames(pet, comp, status, state);
        
        if (frames.isEmpty()) return;

        // Smart frame selection with priority system
        DisplayFrame activeFrame = selectActiveFrame(frames, status, state);
        
        // Show the frame with appropriate styling
        showFrame(player, activeFrame);

        // Debug mode: Show emotion pool in scoreboard
        if (woflo.petsplus.Petsplus.DEBUG_MODE) {
            showEmotionPoolScoreboard(player, comp, pet.getDisplayName().getString());
        }
    }

    private static PetStatus analyzePetStatus(MobEntity pet, PetComponent comp, InspectionState state) {
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        float healthPercent = maxHealth > 0 ? health / maxHealth : 0;
        
        // Use the actual XP flash system from PetComponent instead of detecting changes manually
        // This ensures XP flashing only happens when XP is actually gained via addExperience()
        boolean recentXpGain = comp.isXpFlashing();
        boolean canLevelUp = comp.isFeatureLevel();
        boolean injured = healthPercent < 1.0f;
        boolean critical = healthPercent <= 0.25f;
        boolean inCombat = pet.getAttacking() != null || pet.getAttacker() != null ||
                          (pet.getWorld().getTime() - comp.getLastAttackTick()) < 60; // 3 seconds since last damage
        
        // Check for cooldowns and auras
        boolean hasCooldowns = hasActiveCooldowns(comp);
        boolean hasAura = hasActiveAura(comp);
        
        return new PetStatus(healthPercent, recentXpGain, canLevelUp, 
                           injured, critical, inCombat, hasCooldowns, hasAura);
    }

    private static List<DisplayFrame> buildDisplayFrames(MobEntity pet, PetComponent comp, PetStatus status, InspectionState state) {
        List<DisplayFrame> frames = new ArrayList<>();
        String name = pet.getDisplayName().getString();
        long currentTick = state.tickCounter;
        
        // Priority Frame 1: Critical health (always priority)
        if (status.critical) {
            Text text = UIStyle.statusIndicator("injured")
                .append(UIStyle.cleanPetDisplay(name, status.healthPercent, comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick, status.inCombat));
            frames.add(new DisplayFrame(text, status.healthPercent, BossBar.Color.RED, FramePriority.CRITICAL));
        }

        // Priority Frame 2: Tribute milestone required
        PetRoleType roleType = comp.getRoleType();
        if (roleType != null && roleType.xpCurve().tributeMilestones().contains(comp.getLevel()) && !comp.isMilestoneUnlocked(comp.getLevel())) {
            MutableText text = UIStyle.statusIndicator("happy")
                .append(UIStyle.cleanPetDisplay(name, status.healthPercent, comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick, status.inCombat))
                .append(UIStyle.sepDot())
                .append(UIStyle.tributeNeeded(getTributeItemName(comp, comp.getLevel())));
            frames.add(new DisplayFrame(text, 1.0f, BossBar.Color.YELLOW, FramePriority.HIGH));
        }
        
        // Single Context Bar: Shows the most relevant information with light gray base
        if (status.critical || (roleType != null && roleType.xpCurve().tributeMilestones().contains(comp.getLevel()) && !comp.isMilestoneUnlocked(comp.getLevel()))) {
            // For critical states or tribute milestones, keep the specific priority frames above
            return frames;
        }
        
        // Clean, simple display: "Name • Lv.X" with mood indicator
        // Bar color based on health, level color based on XP progress, name color responds to combat
        Text mainDisplay = UIStyle.cleanPetDisplay(
            name,
            status.healthPercent,
            comp.getLevel(),
            comp.getXpProgress(),
            status.canLevelUp,
            status.recentXpGain,
            currentTick,
            status.inCombat
        );
        
        // Add mood display to main text (with debug info if enabled)
        Text moodText = woflo.petsplus.Petsplus.DEBUG_MODE ? comp.getMoodTextWithDebug() : comp.getMoodText();

        MutableText displayWithMood = Text.empty()
            .append(mainDisplay)
            .append(Text.literal(" "))
            .append(moodText);

        // Note: Debug scoreboard will be handled in updatePetDisplay with player context

        // Boss bar always shows health-based color, only the text shows mood colors
        BossBar.Color barColor = UIStyle.getHealthBasedBossBarColor(status.healthPercent);
        float progress = status.healthPercent; // Bar shows health, not XP
        
        frames.add(new DisplayFrame(displayWithMood, progress, barColor, FramePriority.NORMAL));
        return frames;
    }

    private static DisplayFrame selectActiveFrame(List<DisplayFrame> frames, PetStatus status, InspectionState state) {
        // With the new simplified system, just use the highest priority frame
        // Sort by priority first
        frames.sort((a, b) -> b.priority.ordinal() - a.priority.ordinal());
        
        // Return the highest priority frame
        return frames.get(0);
    }

    private static void showFrame(ServerPlayerEntity player, DisplayFrame frame) {
        // Use forceUpdate=true for animated mood content to ensure smooth animations
        switch (frame.priority) {
            case CRITICAL -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 20, true);
            case HIGH -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 40, true);
            default -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 60, true);
        }
    }

    // Helper methods...
    private static MobEntity findLookedAtPet(ServerPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1f);
        Vec3d look = player.getRotationVec(1f);

        double bestDot = 0.98; // Require tight alignment
        MobEntity best = null;
        for (Entity e : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(VIEW_DIST))) {
            if (!(e instanceof MobEntity mob)) continue;
            Vec3d to = e.getPos().add(0, e.getStandingEyeHeight() * 0.5, 0).subtract(start).normalize();
            double dot = to.dotProduct(look);
            if (dot > bestDot && player.squaredDistanceTo(e) <= VIEW_DIST * VIEW_DIST) {
                bestDot = dot;
                best = mob;
            }
        }
        return best;
    }

    // Data classes
    private static class InspectionState {
        int tickCounter = 0;
        int lingerTicks = 0;
        UUID lastPetId = null;

        void tick() { 
            tickCounter++; 
        }

        void reset(UUID petId) {
            tickCounter = 0;
            lastPetId = petId;
        }
    }

    private record PetStatus(float healthPercent, boolean recentXpGain, 
                           boolean canLevelUp, boolean injured, boolean critical, 
                           boolean inCombat, boolean hasCooldowns, boolean hasAura) {}

    private record DisplayFrame(Text text, float progress, BossBar.Color color, FramePriority priority) {}

    private enum FramePriority {
        LOW, NORMAL, MEDIUM, HIGH, CRITICAL
    }



    private static String getTributeItemName(PetComponent component, int level) {
        if (component == null) {
            return "Special Item";
        }

        PetRoleType roleType = component.getRoleType();
        if (roleType == null) {
            return "Special Item";
        }

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier tributeId = config.resolveTributeItem(roleType, level);
        if (tributeId != null) {
            Item item = Registries.ITEM.get(tributeId);
            if (item != null) {
                return new ItemStack(item).getName().getString();
            }
        }

        return "Special Item";
    }

    private static boolean hasActiveCooldowns(PetComponent comp) {
        String[] cooldownKeys = {
            "ability_primary_cd", "ability_secondary_cd", "guardian_bulwark_cd",
            "striker_exec_cd", "scout_recon_cd", "skyrider_winds_cd", "eclipsed_blink_cd"
        };
        
        for (String key : cooldownKeys) {
            if (comp.getRemainingCooldown(key) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveAura(PetComponent comp) {
        Boolean hasPotion = comp.getStateData("support_potion_present", Boolean.class);
        return Boolean.TRUE.equals(hasPotion);
    }

    
    /**
     * Clean up inspection states for players who are no longer online
     */
    private static void cleanupDisconnectedPlayers(MinecraftServer server) {
        java.util.Set<UUID> onlinePlayerIds = new java.util.HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            onlinePlayerIds.add(player.getUuid());
        }
        
        // Remove inspection states for offline players
        inspecting.entrySet().removeIf(entry -> !onlinePlayerIds.contains(entry.getKey()));
    }

    /**
     * Show emotion pool debug information in the scoreboard panel
     */
    private static void showEmotionPoolScoreboard(ServerPlayerEntity player, PetComponent comp, String petName) {
        var emotions = comp.getEmotionPoolDebug();
        var paletteStops = comp.getEmotionPalette();
        TextColor headerColor = paletteStops.isEmpty()
                ? TextColor.fromFormatting(Formatting.YELLOW)
                : paletteStops.get(0).color();
        TextColor accentColor = paletteStops.size() > 1
                ? paletteStops.get(1).color()
                : headerColor;

        // Create or update scoreboard objective
        var scoreboard = player.getServer().getScoreboard();
        var objective = scoreboard.getNullableObjective("pet_emotions");

        if (objective == null) {
            objective = scoreboard.addObjective("pet_emotions",
                net.minecraft.scoreboard.ScoreboardCriterion.DUMMY,
                Text.literal("Pet Emotions").formatted(Formatting.YELLOW),
                net.minecraft.scoreboard.ScoreboardCriterion.RenderType.INTEGER,
                false, null);

            // Show the scoreboard on the sidebar
            scoreboard.setObjectiveSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR, objective);
        }

        // Clear existing scores for this objective by getting all score entries
        for (var entry : scoreboard.getScoreboardEntries(objective)) {
            scoreboard.removeScore(ScoreHolder.fromName(entry.owner()), objective);
        }

        // Add pet name header
        String header = toSectionColor(headerColor, "§e") + petName + " [" + comp.getMoodLevel() + "]";
        ScoreHolder headerHolder = ScoreHolder.fromName(header);
        scoreboard.getOrCreateScore(headerHolder, objective).setScore(15);

        // Add current mood
        String moodLine = "§7Mood: "
                + toSectionColor(accentColor, "§f")
                + comp.getCurrentMood().name().toLowerCase();
        ScoreHolder moodHolder = ScoreHolder.fromName(moodLine);
        scoreboard.getOrCreateScore(moodHolder, objective).setScore(14);

        // Add separator
        String separatorLine = "§8─────────────";
        ScoreHolder separatorHolder = ScoreHolder.fromName(separatorLine);
        scoreboard.getOrCreateScore(separatorHolder, objective).setScore(13);

        if (emotions.isEmpty()) {
            String noEmotionsLine = "§7No emotions";
            ScoreHolder noEmotionsHolder = ScoreHolder.fromName(noEmotionsLine);
            scoreboard.getOrCreateScore(noEmotionsHolder, objective).setScore(12);
        } else {
            int score = 12;
            for (var emotionInfo : emotions) {
                if (score < 1) break; // Scoreboard limit

                TextColor emotionColor = PetComponent.getEmotionColor(emotionInfo.emotion());
                String color = toSectionColor(emotionColor, "§f");
                String parkedMarker = emotionInfo.parked() ? toSectionColor(PetComponent.getEmotionAccentColor(emotionInfo.emotion()), "§b") + "*" : "";
                String line = color + emotionInfo.emotion().name().toLowerCase()
                             + " §7(" + String.format("%.2f", emotionInfo.weight()) + ")" + parkedMarker;

                ScoreHolder emotionHolder = ScoreHolder.fromName(line);
                scoreboard.getOrCreateScore(emotionHolder, objective).setScore(score--);
            }
        }
    }

    /**
     * Clear the emotion debug scoreboard
     */
    private static void clearEmotionScoreboard(ServerPlayerEntity player) {
        var scoreboard = player.getServer().getScoreboard();
        var objective = scoreboard.getNullableObjective("pet_emotions");

        if (objective != null) {
            // Clear the sidebar slot
            scoreboard.setObjectiveSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR, null);
            // Remove the objective entirely
            scoreboard.removeObjective(objective);
        }
    }

    /**
     * Clean up all inspection states during server shutdown.
     */
    public static void shutdown() {
        inspecting.clear();
    }

    private static String toSectionColor(TextColor color, String fallback) {
        if (color == null) {
            return fallback;
        }
        int rgb = color.getRgb() & 0xFFFFFF;
        String hex = String.format("%06X", rgb);
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            builder.append('§').append(c);
        }
        return builder.toString();
    }
}
