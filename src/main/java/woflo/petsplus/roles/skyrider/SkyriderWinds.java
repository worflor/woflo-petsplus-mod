package woflo.petsplus.roles.skyrider;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Skyrider role implementation for air control features.
 * 
 * Features:
 * - Projectile crit levitation: On owner projectile crit, chance to apply Levitation I (10-20 ticks)
 * - Windlash rider: On owner fall >3 blocks, apply jump boost and next attack knockup
 * - Skybond aura update: fall_reduction_near_owner gains apply_to_mount: true
 */
public class SkyriderWinds {

    private static final String PROJ_LEVITATION_LAST_TRIGGER_KEY = "skyrider_proj_levitation_last_trigger";

    /**
     * Check if projectile crit levitation should trigger.
     * Called when owner scores a projectile critical hit.
     */
    public static boolean shouldTriggerProjLevitation(TameableEntity pet, PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        long currentTick = serverOwner.getWorld().getTime();

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(serverOwner);
        long lastTriggerTick = ownerState.getTempState(PROJ_LEVITATION_LAST_TRIGGER_KEY);
        int cooldownTicks = getProjLevitateIcdTicks();

        if (cooldownTicks > 0 && currentTick - lastTriggerTick < cooldownTicks) {
            return false;
        }

        // Check chance for levitation trigger
        double chance = getProjLevitateChance();
        boolean shouldTrigger = Math.random() < chance;

        if (shouldTrigger) {
            ownerState.setTempState(PROJ_LEVITATION_LAST_TRIGGER_KEY, currentTick);
        }

        return shouldTrigger;
    }
    
    /**
     * Get the levitation duration for projectile crits.
     * Returns a random duration between 10-20 ticks (0.5-1.0 seconds).
     */
    public static int getRandomLevitationDuration() {
        return 10 + (int)(Math.random() * 11); // 10-20 ticks
    }
    
    /**
     * Check if owner is falling the minimum distance for windlash.
     */
    public static boolean isOwnerFallingMinDistance(PlayerEntity owner, double minBlocks) {
        // Check if owner is falling and has fallen at least minBlocks
        if (owner.isOnGround() || owner.getVelocity().y >= 0) {
            return false;
        }
        
        // This is a simplified check - in a full implementation you'd track
        // the actual fall distance from when the fall started
        return owner.fallDistance >= minBlocks;
    }
    
    /**
     * Check if fall reduction should apply to mount.
     */
    public static boolean shouldApplyFallReductionToMount(TameableEntity pet, PlayerEntity owner) {
        // For Skyrider, fall reduction should apply to mount as well
        return owner.getVehicle() != null;
    }
    
    /**
     * Get the projectile levitation chance from config.
     */
    public static double getProjLevitateChance() {
        return PetsPlusConfig.getInstance().getDouble("skyrider", "ownerProjLevitateChance", 0.10);
    }
    
    /**
     * Get the projectile levitation internal cooldown ticks.
     */
    public static int getProjLevitateIcdTicks() {
        return PetsPlusConfig.getInstance().getInt("skyrider", "ownerProjLevitateIcdTicks", 200);
    }
    
    /**
     * Get the minimum fall distance for windlash trigger.
     */
    public static double getWindlashMinFallBlocks() {
        return 3.0;
    }
    
    /**
     * Get the windlash cooldown ticks.
     */
    public static int getWindlashCooldownTicks() {
        return 120; // 6 seconds
    }
    
    /**
     * Apply skyrider role effects during server tick.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Skyrider role effects are mostly event-driven
        // This method can be used for any periodic skyrider role effects in the future
    }
}