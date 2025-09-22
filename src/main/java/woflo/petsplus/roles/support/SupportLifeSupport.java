package woflo.petsplus.roles.support;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.config.PetsPlusConfig;

/**
 * Support role implementation for perched potion sip reduction and mounted cone bias aura.
 * 
 * Features:
 * - Perched potion sip reduction: While pet is perched, reduce potion consumption per pulse
 * - Mounted cone bias aura: While owner is mounted, aura pulses use forward cone bias with extra radius
 */
public class SupportLifeSupport {
    
    /**
     * Check if potion sip reduction should apply for this pet.
     * Called during potion consumption logic.
     */
    public static boolean shouldReducePotionSip(TameableEntity pet, PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }
        
        // Check if pet is perched on owner's shoulder
        if (!isPetPerched(pet, serverOwner)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the potion sip discount percentage for perched pets.
     */
    public static double getPotionSipDiscount() {
        return PetsPlusConfig.getInstance().getDouble("support", "perchSipDiscount", 0.20);
    }
    
    /**
     * Check if cone bias should apply for aura pulses.
     * Called during aura pulse logic.
     */
    public static boolean shouldUseConeForAura(TameableEntity pet, PlayerEntity owner) {
        // Check if owner is mounted
        return owner.getVehicle() != null;
    }
    
    /**
     * Get the extra radius for mounted cone bias aura.
     */
    public static int getMountedConeExtraRadius() {
        return PetsPlusConfig.getInstance().getInt("support", "mountedConeExtraRadius", 2);
    }
    
    /**
     * Check if pet is perched on owner's shoulder.
     * This is a simplified check - in a full implementation you'd check actual shoulder mounting.
     */
    private static boolean isPetPerched(TameableEntity pet, ServerPlayerEntity owner) {
        // For parrots, check if they're on the shoulder
        if (pet instanceof net.minecraft.entity.passive.ParrotEntity) {
            // Check if parrot is sitting on owner's shoulder
            // This would need to check the actual shoulder mounting state
            return pet.getPos().distanceTo(owner.getPos()) < 2.0 && !pet.isOnGround();
        }
        
        // For other pets, check if they're very close and not on ground (simulating perch)
        return pet.getPos().distanceTo(owner.getPos()) < 1.5 && !pet.isOnGround();
    }
    
    /**
     * Apply support role effects during server tick.
     * Called from the main event handler.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Support role is mostly passive - effects are applied during potion/aura events
        // This method can be used for any periodic support role effects in the future
    }
}