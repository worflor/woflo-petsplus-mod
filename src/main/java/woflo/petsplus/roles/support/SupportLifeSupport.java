package woflo.petsplus.roles.support;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

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

        if (PetPerchUtil.ownerHasPerchedRole(serverOwner, PetRole.SUPPORT)) {
            return true;
        }

        PetComponent component = PetComponent.get(pet);
        return component != null &&
               component.getRole() == PetRole.SUPPORT &&
               component.isOwnedBy(serverOwner) &&
               PetPerchUtil.isPetPerched(component);
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
     * Apply support role effects during server tick.
     * Called from the main event handler.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Support role is mostly passive - effects are applied during potion/aura events
        // This method can be used for any periodic support role effects in the future
    }
}