package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
            inspecting.remove(player.getUuid());
        }
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
            inspecting.remove(player.getUuid());
            return;
        }

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
    }

    private static PetStatus analyzePetStatus(MobEntity pet, PetComponent comp, InspectionState state) {
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        float healthPercent = maxHealth > 0 ? health / maxHealth : 0;
        
        float xp = comp.getXpProgress();
        boolean xpChanged = Math.abs(xp - state.lastXp) > 0.001f;
        if (xpChanged) {
            state.lastXp = xp;
            state.lastXpChangeTime = state.tickCounter;
        }
        
        boolean recentXpGain = (state.tickCounter - state.lastXpChangeTime) < 100; // 5s
        boolean canLevelUp = comp.isFeatureLevel();
        boolean injured = healthPercent < 1.0f;
        boolean critical = healthPercent <= 0.25f;
        boolean inCombat = pet.getAttacking() != null || pet.getAttacker() != null;
        
        // Check for cooldowns and auras
        boolean hasCooldowns = hasActiveCooldowns(comp);
        boolean hasAura = hasActiveAura(comp);
        
        return new PetStatus(healthPercent, xpChanged, recentXpGain, canLevelUp, 
                           injured, critical, inCombat, hasCooldowns, hasAura);
    }

    private static List<DisplayFrame> buildDisplayFrames(MobEntity pet, PetComponent comp, PetStatus status, InspectionState state) {
        List<DisplayFrame> frames = new ArrayList<>();
        String name = pet.getDisplayName().getString();
        long currentTick = state.tickCounter;
        
        // Priority Frame 1: Critical health (always priority)
        if (status.critical) {
            Text text = UIStyle.statusIndicator("injured")
                .append(UIStyle.dynamicPetName(name, status.healthPercent))
                .append(UIStyle.sepDot())
                .append(UIStyle.smartHealth(pet.getHealth(), pet.getMaxHealth(), status.inCombat));
            frames.add(new DisplayFrame(text, status.healthPercent, BossBar.Color.RED, FramePriority.CRITICAL));
        }
        
        // Priority Frame 2: Ready to level up (with pulsing effect)
        if (status.canLevelUp) {
            Text text = UIStyle.statusIndicator("happy")
                .append(UIStyle.dynamicPetName(name, status.healthPercent))
                .append(UIStyle.sepDot())
                .append(UIStyle.tributeNeeded(getTributeItemName(comp, comp.getLevel())));
            frames.add(new DisplayFrame(text, 1.0f, BossBar.Color.YELLOW, FramePriority.HIGH));
        }
        
        // Single Context Bar: Shows the most relevant information with light gray base
        if (status.critical || status.canLevelUp) {
            // For critical states, keep the specific priority frames above
            return frames;
        }
        
        // Context-aware main display - no rotating bars
        Text mainDisplay;
        BossBar.Color barColor;
        float progress;
        
        if (status.hasCooldowns) {
            // Show cooldown context
            String cooldownInfo = getActiveCooldownSummary(comp);
            mainDisplay = UIStyle.dynamicPetName(name, status.healthPercent)
                .append(UIStyle.sepDot())
                .append(UIStyle.contextBar("Cooldowns", cooldownInfo, Formatting.YELLOW, status.recentXpGain, currentTick));
            barColor = BossBar.Color.YELLOW;
            progress = 0.8f; // Visual indicator for active systems
        } else if (status.hasAura) {
            // Show aura context
            String auraInfo = getActiveAuraSummary(comp);
            mainDisplay = UIStyle.dynamicPetName(name, status.healthPercent)
                .append(UIStyle.sepDot())
                .append(UIStyle.contextBar("Aura", auraInfo, Formatting.LIGHT_PURPLE, status.recentXpGain, currentTick));
            barColor = BossBar.Color.PURPLE;
            progress = 1.0f; // Full bar for active aura
        } else if (status.injured) {
            // Show health context when injured
            mainDisplay = UIStyle.dynamicPetName(name, status.healthPercent)
                .append(UIStyle.sepDot())
                .append(UIStyle.levelWithXpFlash(comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick))
                .append(UIStyle.sepDot())
                .append(UIStyle.smartHealth(pet.getHealth(), pet.getMaxHealth(), status.inCombat));
            barColor = BossBar.Color.RED;
            progress = status.healthPercent;
        } else {
            // Default: Level progression with integrated XP flashing
            mainDisplay = UIStyle.dynamicPetName(name, status.healthPercent)
                .append(UIStyle.sepDot())
                .append(UIStyle.levelWithXpFlash(comp.getLevel(), comp.getXpProgress(), status.canLevelUp, status.recentXpGain, currentTick));
            barColor = BossBar.Color.GREEN;
            progress = comp.getXpProgress();
        }
        
        frames.add(new DisplayFrame(mainDisplay, progress, barColor, FramePriority.NORMAL));
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
        switch (frame.priority) {
            case CRITICAL -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 20);
            case HIGH -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 40);
            default -> BossBarManager.showOrUpdateBossBar(player, frame.text, frame.progress, frame.color, 60);
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
        float lastXp = -1f;
        long lastXpChangeTime = 0;

        void tick() { 
            tickCounter++; 
        }

        void reset(UUID petId) {
            tickCounter = 0;
            lastPetId = petId;
            lastXp = -1f;
            lastXpChangeTime = 0;
        }
    }

    private record PetStatus(float healthPercent, boolean xpChanged, boolean recentXpGain, 
                           boolean canLevelUp, boolean injured, boolean critical, 
                           boolean inCombat, boolean hasCooldowns, boolean hasAura) {}

    private record DisplayFrame(Text text, float progress, BossBar.Color color, FramePriority priority) {}

    private enum FramePriority {
        LOW, NORMAL, MEDIUM, HIGH, CRITICAL
    }

    private static String shortenId(String id) {
        int idx = id.indexOf(':');
        if (idx > 0 && idx < id.length()-1) {
            return id.substring(idx+1).replace('_',' ');
        }
        return id.replace('_',' ');
    }

    private static String humanizeKey(String key) {
        return key.replace('_', ' ');
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
    
    private static String getActiveCooldownSummary(PetComponent comp) {
        String[] cooldownKeys = {
            "ability_primary_cd", "ability_secondary_cd", "guardian_bulwark_cd",
            "striker_exec_cd", "scout_recon_cd", "skyrider_winds_cd", "eclipsed_blink_cd"
        };
        
        StringBuilder summary = new StringBuilder();
        int count = 0;
        
        for (String key : cooldownKeys) {
            long remaining = comp.getRemainingCooldown(key);
            if (remaining > 0 && count < 2) { // Limit to 2 for brevity
                if (count > 0) summary.append(", ");
                summary.append(humanizeKey(key.replace("_cd", "")));
                count++;
            }
        }
        
        return count > 0 ? summary.toString() : "None";
    }
    
    private static String getActiveAuraSummary(PetComponent comp) {
        @SuppressWarnings("unchecked")
        List<String> effects = comp.getStateData("support_potion_effects", List.class);
        Double chargesRemaining = comp.getStateData("support_potion_charges_remaining", Double.class);
        Double totalCharges = comp.getStateData("support_potion_total_charges", Double.class);

        if (effects != null && !effects.isEmpty()) {
            StringBuilder summary = new StringBuilder();
            int shown = 0;

            for (String effect : effects) {
                if (shown > 0) summary.append(", ");
                String[] parts = effect.split("\\|");
                String name = parts.length > 0 ? parts[0] : effect;
                summary.append(shortenId(name));
                if (++shown >= 2) break; // Limit to 2 effects
            }

            if (chargesRemaining != null) {
                int pulsesLeft = Math.max(0, (int) Math.ceil(chargesRemaining));
                summary.append(" [");
                if (totalCharges != null && totalCharges > 0) {
                    int pulsesTotal = Math.max(0, (int) Math.round(totalCharges));
                    summary.append(pulsesLeft).append('/').append(pulsesTotal);
                } else {
                    summary.append(pulsesLeft);
                }
                summary.append(']');
            }

            return summary.toString();
        }

        return "Active";
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
}
