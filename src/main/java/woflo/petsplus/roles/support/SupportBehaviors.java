package woflo.petsplus.roles.support;

import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.roles.support.SupportPotionUtils;

/**
 * Support role behaviors for pet-agnostic utility enhancement.
 */
public class SupportBehaviors {

    /**
     * Check if the owner should get perch sip discount.
     * Called during potion consumption to reduce sip cost.
     */
    public static double getPotionSipDiscount(PlayerEntity owner) {
        return SupportPotionUtils.resolvePerchSipDiscount(owner);
    }

}
