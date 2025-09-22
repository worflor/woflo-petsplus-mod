package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.stats.PetAttributeManager;

import java.util.List;
import java.util.WeakHashMap;
import java.util.Map;

/**
 * Handles pet XP gain when owners gain XP.
 */
public class XpEventHandler {
    private static final Map<PlayerEntity, Integer> PREVIOUS_XP = new WeakHashMap<>();
    private static final Map<PlayerEntity, Long> LAST_PLAYER_ACTION = new WeakHashMap<>();
    private static final Map<PlayerEntity, Long> LAST_COMBAT_TIME = new WeakHashMap<>();
    private static final Map<MobEntity, Long> LAST_PET_COMBAT = new WeakHashMap<>();
    
    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(XpEventHandler::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PREVIOUS_XP.clear();
            LAST_PLAYER_ACTION.clear();
            LAST_COMBAT_TIME.clear();
            LAST_PET_COMBAT.clear();
        });
    }
    
    private static void onWorldTick(ServerWorld world) {
        // Check all players for XP changes
        for (ServerPlayerEntity player : world.getPlayers()) {
            checkPlayerXpChange(player);
        }
    }
    
    private static void checkPlayerXpChange(ServerPlayerEntity player) {
        int currentXp = player.totalExperience;
        int previousXp = PREVIOUS_XP.getOrDefault(player, currentXp);
        
        if (currentXp > previousXp) {
            int xpGained = currentXp - previousXp;
            handlePlayerXpGain(player, xpGained);
        }
        
        PREVIOUS_XP.put(player, currentXp);
    }
    
    private static void handlePlayerXpGain(ServerPlayerEntity player, int xpGained) {
        // Find nearby pets owned by this player
        List<MobEntity> nearbyPets = player.getWorld().getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(32), // 32 block radius
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && petComp.isOwnedBy(player);
            }
        );
        
        if (nearbyPets.isEmpty()) return;
        
        // Base 1:1 XP sharing - much more generous than the old 50%
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double baseXpModifier = config.getDouble("pet_leveling", "xp_modifier", 1.0); // Now defaults to 1:1
        
        // Give XP to all nearby pets with individual scaling
        for (MobEntity pet : nearbyPets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null) {
                // Calculate level-scaled XP modifier - more generous early game
                float levelScaleModifier = getLevelScaleModifier(petComp.getLevel());
                
                // Apply pet's unique learning characteristic (±15% variation)
                float learningModifier = 1.0f;
                if (petComp.getCharacteristics() != null) {
                    learningModifier = petComp.getCharacteristics().getXpLearningModifier(petComp.getRole());
                }
                
                // Apply participation bonuses/penalties
                float participationModifier = getParticipationModifier(player, pet);
                
                // Calculate final XP: base * level_scaling * individual_learning * participation
                int petXpGain = Math.max(1, (int)(xpGained * baseXpModifier * levelScaleModifier * learningModifier * participationModifier));
                
                // Check if this is the first time gaining pet XP (first bond)
                boolean wasFirstBond = petComp.getLevel() == 1 && petComp.getExperience() == 0;

                boolean leveledUp = petComp.addExperience(petXpGain);

                if (wasFirstBond) {
                    // Trigger first pet bond advancement
                    AdvancementManager.triggerFirstPetBond(player);
                }

                if (leveledUp) {
                    handlePetLevelUp(player, pet, petComp);
                }
            }
        }
    }
    
    /**
     * Get level-scaled XP modifier for more engaging progression.
     * Early game gets bonus XP for fast bonding, late game is more challenging.
     */
    private static float getLevelScaleModifier(int petLevel) {
        if (petLevel <= 5) {
            return 1.6f;    // 160% XP for levels 1-5 (fast early bonding)
        } else if (petLevel <= 10) {
            return 1.3f;    // 130% XP for levels 6-10 (still generous)
        } else if (petLevel <= 15) {
            return 1.1f;    // 110% XP for levels 11-15 (slight bonus)
        } else if (petLevel <= 20) {
            return 1.0f;    // 100% XP for levels 16-20 (1:1 ratio)
        } else if (petLevel <= 25) {
            return 0.8f;    // 80% XP for levels 21-25 (more challenging)
        } else {
            return 0.7f;    // 70% XP for levels 26-30 (prestigious end-game)
        }
    }
    
    /**
     * Get participation modifier based on recent player and pet activity.
     * Rewards active gameplay and discourages AFK farming.
     */
    private static float getParticipationModifier(ServerPlayerEntity player, MobEntity pet) {
        long currentTime = player.getWorld().getTime();
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        
        // Check for recent combat activity (within last 30 seconds = 600 ticks)
        boolean playerRecentCombat = LAST_COMBAT_TIME.getOrDefault(player, 0L) > (currentTime - 600);
        boolean petRecentCombat = LAST_PET_COMBAT.getOrDefault(pet, 0L) > (currentTime - 600);
        
        // Check for AFK status (no input for 5 minutes = 6000 ticks)
        boolean playerAFK = LAST_PLAYER_ACTION.getOrDefault(player, currentTime) < (currentTime - 6000);
        
        float modifier = 1.0f;
        
        // Combat participation bonuses
        if (playerRecentCombat || petRecentCombat) {
            float participationBonus = (float) config.getDouble("pet_leveling", "participation_bonus", 0.5);
            modifier += participationBonus; // +50% default for combat participation
        }
        
        // AFK penalty
        if (playerAFK) {
            float afkPenalty = (float) config.getDouble("pet_leveling", "afk_penalty", 0.25);
            modifier = afkPenalty; // 25% of normal XP when AFK
        }
        
        return Math.max(0.1f, modifier); // Minimum 10% XP even when AFK
    }
    
    /**
     * Call this when a player deals damage to track combat activity.
     */
    public static void trackPlayerCombat(ServerPlayerEntity player) {
        long currentTime = player.getWorld().getTime();
        LAST_COMBAT_TIME.put(player, currentTime);
        LAST_PLAYER_ACTION.put(player, currentTime);
    }
    
    /**
     * Call this when a pet deals damage to track combat activity.
     */
    public static void trackPetCombat(MobEntity pet) {
        long currentTime = pet.getWorld().getTime();
        LAST_PET_COMBAT.put(pet, currentTime);
    }
    
    /**
     * Call this for any player action to track AFK status.
     */
    public static void trackPlayerActivity(ServerPlayerEntity player) {
        LAST_PLAYER_ACTION.put(player, player.getWorld().getTime());
    }
    
    private static void handlePetLevelUp(ServerPlayerEntity owner, MobEntity pet, PetComponent petComp) {
        int newLevel = petComp.getLevel();

        // Trigger advancement for milestone levels
        if (newLevel == 10 || newLevel == 20 || newLevel == 30) {
            AdvancementManager.triggerMilestoneUnlock(owner, newLevel);
        }

        // Trigger level 30 advancement
        if (newLevel == 30) {
            AdvancementManager.triggerPetLevel30(owner, pet);
        }

        // Trigger role-specific milestones
        PetRole role = petComp.getRole();
        if (role != null) {
            String roleId = "petsplus:" + role.name().toLowerCase();
            if (newLevel == 20 && role == PetRole.SKYRIDER) {
                AdvancementManager.triggerRoleMilestone(owner, roleId, newLevel);
            } else if (newLevel == 30 && role == PetRole.ECLIPSED) {
                AdvancementManager.triggerRoleMilestone(owner, roleId, newLevel);
            }
        }
        
        // Apply attribute modifiers based on new level and characteristics
        PetAttributeManager.applyAttributeModifiers(pet, petComp);
        
        // Play level up sound
        owner.getWorld().playSound(
            null, 
            pet.getX(), pet.getY(), pet.getZ(), 
            SoundEvents.ENTITY_VILLAGER_YES, 
            SoundCategory.NEUTRAL, 
            0.5f, 
            1.2f // Higher pitch to distinguish from player level up
        );
        
        // Send level up message to owner
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
        String roleDisplayName = getRoleDisplayName(petComp.getRole());
        
        if (petComp.isFeatureLevel()) {
            // Special message for feature levels that unlock new abilities
            owner.sendMessage(
                Text.of("§6§l" + petName + " §ereached level §6" + newLevel + "§e! §b" + roleDisplayName + " §eunlocked new abilities!"),
                true // Action bar
            );
        } else {
            // Regular level up message
            owner.sendMessage(
                Text.of("§e" + petName + " §7reached level §e" + newLevel + "§7! (" + roleDisplayName + ")"),
                true // Action bar
            );
        }
        
        // Additional effects for feature levels
        if (petComp.isFeatureLevel()) {
            // Play extra celebratory sound
            owner.getWorld().playSound(
                null,
                pet.getX(), pet.getY(), pet.getZ(),
                SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                SoundCategory.NEUTRAL,
                0.8f,
                1.0f
            );
            
            // Send chat message for major milestones
            owner.sendMessage(
                Text.of("§6✦ §e" + petName + " §6has unlocked new " + roleDisplayName + " abilities at level " + newLevel + "! §6✦"),
                false // Chat message
            );

            // Suggest inspecting XP info
            if (owner instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggest(sp,
                    new woflo.petsplus.ui.ChatLinks.Suggest("[View Pet XP]", "/petsplus xp info", "See levels and XP progress", "yellow", true));
            }
        }
    }
    
    private static String getRoleDisplayName(PetRole role) {
        return switch (role) {
            case GUARDIAN -> "Guardian";
            case STRIKER -> "Striker";
            case SUPPORT -> "Support";
            case SCOUT -> "Scout";
            case SKYRIDER -> "Skyrider";
            case ENCHANTMENT_BOUND -> "Enchantment-Bound";
            case CURSED_ONE -> "Cursed One";
            case EEPY_EEPER -> "Eepy Eeper";
            case ECLIPSED -> "Eclipsed";
        };
    }
}