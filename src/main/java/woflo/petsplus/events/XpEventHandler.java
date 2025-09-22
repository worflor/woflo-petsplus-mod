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
    
    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(XpEventHandler::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PREVIOUS_XP.clear());
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
        
        // Calculate pet XP gain with modifier from config
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double xpModifier = config.getDouble("pet_leveling", "xp_modifier", 0.5); // Default 50% of player XP
        int petXpGain = Math.max(1, (int)(xpGained * xpModifier));
        
        // Give XP to all nearby pets
        for (MobEntity pet : nearbyPets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null) {
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