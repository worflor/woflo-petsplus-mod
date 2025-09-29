package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
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
import java.util.concurrent.ConcurrentHashMap;

import woflo.petsplus.state.PlayerTickListener;

/**
 * Enhanced pet inspection system with dynamic, contextual boss bar display.
 * Shows smart, prioritized information that adapts to pet state and context.
 */
public final class PetInspectionManager {
    private static final PlayerTicker PLAYER_TICKER = new PlayerTicker();

    private PetInspectionManager() {}

    public static PlayerTickListener listener() {
        return PLAYER_TICKER;
    }

    private static final int VIEW_DIST = 12;
    private static final Map<UUID, InspectionState> inspecting = new HashMap<>();
    private static final int LINGER_TICKS = 100; // 5s linger after looking away
    private static final int DEBUG_LINGER_TICKS = 200; // 10s linger in debug mode

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        inspecting.remove(player.getUuid());
        BossBarManager.removeBossBar(player);
        clearEmotionScoreboard(player);
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
        int maxLinger = state.getLingerTicks();
        if (state.lingerTicks == maxLinger) {
            // Just stopped looking: extend current bar duration for smooth transition
            BossBarManager.extendDuration(player, maxLinger);
            state.hasFocus = false;
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
        state.lingerTicks = state.getLingerTicks();
        UUID newPetId = pet.getUuid();

        if (state.lastPetId == null || !state.lastPetId.equals(newPetId)) {
            // New pet - reset all state
            state.reset(newPetId);
        }

        PetComponent comp = PetComponent.get(pet);
        if (comp == null || !comp.isOwnedBy(player)) {
            // Increment ownership failure counter instead of immediately clearing
            state.ownershipFailures++;
            if (state.shouldClearUI()) {
                BossBarManager.removeBossBar(player);
                clearEmotionScoreboard(player);
                inspecting.remove(player.getUuid());
            }
            return;
        }

        // Reset ownership failures on successful validation
        state.resetOwnershipFailures();
        state.hasFocus = true;

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
                .append(UIStyle.cleanPetDisplay(name, status.healthPercent, comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick, comp.getXpFlashStartTick(), status.inCombat));
            frames.add(new DisplayFrame(text, status.healthPercent, BossBar.Color.RED, FramePriority.CRITICAL));
        }

        // Priority Frame 2: Tribute milestone required
        PetRoleType roleType = comp.getRoleType();
        if (roleType != null && roleType.xpCurve().tributeMilestones().contains(comp.getLevel()) && !comp.isMilestoneUnlocked(comp.getLevel())) {
            MutableText text = UIStyle.statusIndicator("happy")
                .append(UIStyle.cleanPetDisplay(name, status.healthPercent, comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick, comp.getXpFlashStartTick(), status.inCombat))
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
            comp.getXpFlashStartTick(),
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

        // Hysteresis: stricter threshold for gaining focus, looser for maintaining
        boolean currentlyHasFocus = inspecting.containsKey(player.getUuid());
        double bestDot = currentlyHasFocus ? 0.94 : 0.96; // Looser when maintaining focus
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
        int ownershipFailures = 0;
        boolean hasFocus = false;

        void tick() {
            tickCounter++;
        }

        void reset(UUID petId) {
            tickCounter = 0;
            lastPetId = petId;
            ownershipFailures = 0;
            hasFocus = true;
        }

        void resetOwnershipFailures() {
            ownershipFailures = 0;
        }

        boolean shouldClearUI() {
            return ownershipFailures >= 3;
        }

        int getLingerTicks() {
            return woflo.petsplus.Petsplus.DEBUG_MODE ? DEBUG_LINGER_TICKS : LINGER_TICKS;
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
     * Show emotion pool debug information in the scoreboard panel
     */
    private static void showEmotionPoolScoreboard(ServerPlayerEntity player, PetComponent comp, String petName) {
        var emotions = comp.getEmotionPoolDebug();
        var paletteStops = comp.getEmotionPalette();

        // Determine consistent colors using emotion palette as primary source
        TextColor headerColor;
        TextColor accentColor;

        if (paletteStops.isEmpty()) {
            // If no palette, fall back to a neutral emotion color
            headerColor = PetComponent.getEmotionColor(PetComponent.Emotion.CONTENT); // Neutral green
            accentColor = PetComponent.getEmotionAccentColor(PetComponent.Emotion.CONTENT);
        } else {
            headerColor = paletteStops.get(0).color();
            accentColor = paletteStops.size() > 1
                    ? paletteStops.get(1).color()
                    : PetComponent.getEmotionAccentColor(paletteStops.get(0).emotion());
        }

        // Create or update scoreboard objective
        var scoreboard = player.getServer().getScoreboard();
        var objective = scoreboard.getNullableObjective("pet_emotions");

        if (objective == null) {
            // Use the dominant emotion color for the title, or yellow as final fallback
            Text titleText = headerColor != null
                ? Text.literal("Pet Emotions").styled(style -> style.withColor(headerColor))
                : Text.literal("Pet Emotions").formatted(Formatting.YELLOW);

            objective = scoreboard.addObjective("pet_emotions",
                net.minecraft.scoreboard.ScoreboardCriterion.DUMMY,
                titleText,
                net.minecraft.scoreboard.ScoreboardCriterion.RenderType.INTEGER,
                false, null);

            // Show the scoreboard on the sidebar
            scoreboard.setObjectiveSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR, objective);
        }

        // Clear existing scores for this objective by getting all score entries
        for (var entry : scoreboard.getScoreboardEntries(objective)) {
            scoreboard.removeScore(ScoreHolder.fromName(entry.owner()), objective);
        }

        // Add pet name header - use consistent emotion-derived fallback
        String header = toSectionColor(headerColor, toSectionColor(PetComponent.getEmotionColor(PetComponent.Emotion.CONTENT), "§e"))
                + petName + " [" + comp.getMoodLevel() + "]";
        ScoreHolder headerHolder = ScoreHolder.fromName(header);
        scoreboard.getOrCreateScore(headerHolder, objective).setScore(15);

        // Add current mood - use consistent emotion-derived fallback
        String moodLine = "§7Mood: "
                + toSectionColor(accentColor, toSectionColor(PetComponent.getEmotionAccentColor(PetComponent.Emotion.CONTENT), "§f"))
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
                String color = toSectionColor(emotionColor, toSectionColor(PetComponent.getEmotionColor(PetComponent.Emotion.CONTENT), "§f"));
                String parkedMarker = emotionInfo.parked()
                    ? toSectionColor(PetComponent.getEmotionAccentColor(emotionInfo.emotion()), toSectionColor(PetComponent.getEmotionAccentColor(PetComponent.Emotion.CONTENT), "§b")) + "*"
                    : "";
                String line = color + emotionInfo.emotion().name().toLowerCase()
                             + " §7[" + Math.round(emotionInfo.weight() * 100) + "%]" + parkedMarker;

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

    /**
     * Helper method to get emotion-consistent fallback color
     */
    private static String getEmotionFallbackColor(TextColor preferredColor, boolean useAccent) {
        if (preferredColor != null) {
            return toSectionColor(preferredColor, getDefaultEmotionFallback(useAccent));
        }
        return getDefaultEmotionFallback(useAccent);
    }

    private static String getDefaultEmotionFallback(boolean useAccent) {
        TextColor fallbackColor = useAccent
            ? PetComponent.getEmotionAccentColor(PetComponent.Emotion.CONTENT)
            : PetComponent.getEmotionColor(PetComponent.Emotion.CONTENT);
        return toSectionColor(fallbackColor, useAccent ? "§b" : "§f");
    }

    private static final class PlayerTicker implements PlayerTickListener {
        private final Map<UUID, Long> nextRunTicks = new ConcurrentHashMap<>();

        @Override
        public long nextRunTick(ServerPlayerEntity player) {
            if (player == null) {
                return Long.MAX_VALUE;
            }
            return nextRunTicks.getOrDefault(player.getUuid(), 0L);
        }

        @Override
        public void run(ServerPlayerEntity player, long currentTick) {
            if (player == null || player.isRemoved()) {
                onPlayerDisconnect(player);
                if (player != null) {
                    nextRunTicks.remove(player.getUuid());
                }
                return;
            }

            updateForPlayer(player);
            // Update every 3 ticks instead of every tick for better performance
            nextRunTicks.put(player.getUuid(), currentTick + 3L);
        }

        @Override
        public void onPlayerRemoved(ServerPlayerEntity player) {
            onPlayerDisconnect(player);
            if (player != null) {
                nextRunTicks.remove(player.getUuid());
            }
        }
    }
}
