package woflo.petsplus.roles.scout;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.StateManager;

/**
 * Scout role implementation for info & mobility features.
 * 
 * Features:
 * - Spotter fallback: If no pet attack within 60 ticks after combat start, apply Glowing to next target hit by owner
 * - Gale Pace: If owner mounted, apply speed scalar to mount instead of owner
 * - Loot Wisp: On combat end, magnetize drops and XP in radius for duration
 */
public class ScoutRecon {
    
    /**
     * Check if spotter fallback should apply.
     * Called when owner hits a target to check if glowing should be applied.
     */
    public static boolean shouldApplySpotterGlowing(TameableEntity pet, PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }
        
        // Get owner combat state
        if (!(serverOwner.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return false;
        }
        
        OwnerCombatState ownerState = StateManager.forWorld(serverWorld).getOwnerState(serverOwner);
        if (ownerState == null) {
            return false;
        }
        
        // Check if we're in combat and pet hasn't attacked recently
        boolean inCombat = ownerState.isInCombat();
        
        if (!inCombat) {
            return false;
        }
        
        // Check if pet has been inactive for spotter threshold
        long ticksSinceLastHit = ownerState.getTimeSinceLastHit();
        
        return ticksSinceLastHit >= getSpotterThresholdTicks() && !hasPetAttackedRecently(pet);
    }
    
    /**
     * Check if speed buff should be applied to mount instead of owner.
     */
    public static boolean shouldApplySpeedToMount(TameableEntity pet, PlayerEntity owner) {
        // Check if owner is mounted
        return owner.getVehicle() != null;
    }
    
    /**
     * Get the threshold ticks for spotter fallback.
     */
    public static int getSpotterThresholdTicks() {
        return 60; // 3 seconds at 20 TPS
    }
    
    /**
     * Get the spotter effect ICD.
     */
    public static int getSpotterIcdTicks() {
        return 300; // 15 seconds
    }
    
    /**
     * Get the loot wisp duration from config.
     */
    public static int getLootWispDurationTicks() {
        return PetsPlusConfig.getInstance().getInt("scout", "lootWispDurationTicks", 80);
    }
    
    /**
     * Check if pet has attacked recently.
     * This is a simplified implementation.
     */
    private static boolean hasPetAttackedRecently(TameableEntity pet) {
        // In a full implementation, this would check the pet's recent attack history
        // For now, we'll use a simple proximity check to see if pet is engaged
        
        if (pet.getTarget() != null) {
            return true; // Pet has a target, so it's likely attacking
        }
        
        // Check if pet is moving aggressively (simplified)
        return pet.getVelocity().lengthSquared() > 0.1;
    }
    
    /**
     * Apply scout role effects during server tick.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Scout role effects are mostly event-driven
        // This method can be used for any periodic scout role effects in the future
    }
}