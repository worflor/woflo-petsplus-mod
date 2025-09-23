package woflo.petsplus.roles.eclipsed;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

/**
 * Eclipsed role implementation for owner/perch loop integration.
 * 
 * Features:
 * - Voidbrand: On aggro acquired, tag target and give owner bonus vs marked + next hit effect
 * - Phase Partner: On pet blink, buff owner if perched + next attack bonus with slowness
 * - Perch Ping: While perched and in combat, interval pulse to retarget and apply effects
 * - Event Horizon: On void save, spawn zone with slowness and projectile DR
 * - Edge Step: While perched on fall >3 blocks, apply fall damage reduction
 */
public class EclipsedVoid {
    
    /**
     * Get the voidbrand mark duration ticks.
     */
    public static int getMarkDurationTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "markDurationTicks", 80);
    }
    
    /**
     * Get the owner bonus damage vs marked targets.
     */
    public static double getOwnerBonusVsMarkedPct() {
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ECLIPSED.id(), "ownerBonusVsMarkedPct", 0.10);
    }
    
    /**
     * Get the next hit effect for marked targets.
     */
    public static String getOwnerNextHitEffect() {
        return PetsPlusConfig.getInstance().getRoleString(PetRoleType.ECLIPSED.id(), "ownerNextHitEffect", "minecraft:wither");
    }
    
    /**
     * Get the next hit effect duration ticks.
     */
    public static int getOwnerNextHitEffectDurationTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "ownerNextHitEffectDurationTicks", 40);
    }
    
    /**
     * Get the phase charge internal cooldown ticks.
     */
    public static int getPhaseChargeInternalCdTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "phaseChargeInternalCdTicks", 400);
    }
    
    /**
     * Get the phase charge bonus damage percentage.
     */
    public static double getPhaseChargeBonusDamagePct() {
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ECLIPSED.id(), "phaseChargeBonusDamagePct", 0.25);
    }
    
    /**
     * Get the phase charge window ticks.
     */
    public static int getPhaseChargeWindowTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "phaseChargeWindowTicks", 100);
    }
    
    /**
     * Get the perch ping interval ticks.
     */
    public static int getPerchPingIntervalTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "perchPingIntervalTicks", 140);
    }
    
    /**
     * Get the perch ping radius.
     */
    public static int getPerchPingRadius() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "perchPingRadius", 8);
    }
    
    /**
     * Get the event horizon duration ticks.
     */
    public static int getEventHorizonDurationTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "eventHorizonDurationTicks", 100);
    }
    
    /**
     * Get the event horizon radius.
     */
    public static double getEventHorizonRadius() {
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ECLIPSED.id(), "eventHorizonRadius", 6.0);
    }
    
    /**
     * Get the event horizon projectile DR percentage.
     */
    public static double getEventHorizonProjectileDrPct() {
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ECLIPSED.id(), "eventHorizonProjectileDrPct", 0.25);
    }
    
    /**
     * Get the edge step fall reduction percentage.
     */
    public static double getEdgeStepFallReductionPct() {
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ECLIPSED.id(), "edgeStepFallReductionPct", 0.25);
    }
    
    /**
     * Get the edge step cooldown ticks.
     */
    public static int getEdgeStepCooldownTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ECLIPSED.id(), "edgeStepCooldownTicks", 240);
    }
    
    /**
     * Check if pet is perched for eclipsed abilities.
     */
    public static boolean isPetPerched(TameableEntity pet, PlayerEntity owner) {
        if (pet == null || owner == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component != null &&
            component.hasRole(PetRoleType.ECLIPSED) &&
            component.isOwnedBy(owner) &&
            PetPerchUtil.isPetPerched(component)) {
            return true;
        }

        return PetPerchUtil.ownerHasPerchedRole(owner, PetRoleType.ECLIPSED);
    }
    
    /**
     * Check if owner is falling minimum distance for edge step.
     */
    public static boolean isOwnerFallingForEdgeStep(PlayerEntity owner) {
        return owner.fallDistance >= 3.0 && !owner.isOnGround();
    }
    
    /**
     * Check if pet has recently blinked (for phase partner).
     */
    public static boolean hasPetRecentlyBlinked(TameableEntity pet) {
        // In a full implementation, this would check if the pet has recently teleported/blinked
        // This would require tracking pet blink events
        
        // For now, this is a placeholder that returns false
        return false;
    }
    
    /**
     * Check if owner recently had a void save (for event horizon).
     */
    public static boolean hasOwnerRecentlyVoidSaved(PlayerEntity owner) {
        // In a full implementation, this would check if the owner was recently saved from void damage
        // This would require integration with void save mechanics
        
        // For now, this is a placeholder that returns false
        return false;
    }
    
    /**
     * Apply eclipsed role effects during server tick.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Check for phase partner trigger
        if (isPetPerched(pet, owner) && hasPetRecentlyBlinked(pet)) {
            // In a full implementation, this would trigger phase partner effects
        }
        
        // Check for edge step trigger
        if (isPetPerched(pet, owner) && isOwnerFallingForEdgeStep(owner)) {
            // In a full implementation, this would apply fall damage reduction
        }
        
        // Check for event horizon trigger
        if (hasOwnerRecentlyVoidSaved(owner)) {
            // In a full implementation, this would spawn the event horizon zone
        }
    }
}