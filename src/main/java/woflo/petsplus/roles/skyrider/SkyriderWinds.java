package woflo.petsplus.roles.skyrider;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.api.registry.PetRoleType;
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
    private static final String WINDLASH_LAST_TRIGGER_KEY = "skyrider_windlash_last_trigger";

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
        boolean hasLastTrigger = ownerState.hasTempState(PROJ_LEVITATION_LAST_TRIGGER_KEY);
        long lastTriggerTick = ownerState.getTempState(PROJ_LEVITATION_LAST_TRIGGER_KEY);
        int cooldownTicks = getProjLevitateIcdTicks();

        if (cooldownTicks > 0 && hasLastTrigger && lastTriggerTick > 0) {
            long ticksSinceTrigger = currentTick - lastTriggerTick;
            if (ticksSinceTrigger >= 0 && ticksSinceTrigger < cooldownTicks) {
                return false;
            }
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
        return PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SKYRIDER.id(), "ownerProjLevitateChance", 0.10);
    }
    
    /**
     * Get the projectile levitation internal cooldown ticks.
     */
    public static int getProjLevitateIcdTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.SKYRIDER.id(), "ownerProjLevitateIcdTicks", 200);
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
     * Check if Windlash Rider can trigger based on cooldown state.
     */
    public static boolean isWindlashOffCooldown(ServerPlayerEntity owner) {
        int cooldownTicks = getWindlashCooldownTicks();
        if (cooldownTicks <= 0) {
            return true;
        }

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        if (!ownerState.hasTempState(WINDLASH_LAST_TRIGGER_KEY)) {
            return true;
        }

        long lastTriggerTick = ownerState.getTempState(WINDLASH_LAST_TRIGGER_KEY);
        long ticksSinceTrigger = getServerTick(owner) - lastTriggerTick;

        if (ticksSinceTrigger < 0) {
            // World changes can rewind per-dimension time, so treat negative
            // deltas as an expired cooldown and allow the trigger.
            return true;
        }

        return ticksSinceTrigger >= cooldownTicks;
    }

    /**
     * Track that Windlash Rider has triggered so the cooldown is enforced.
     */
    public static void markWindlashTriggered(ServerPlayerEntity owner) {
        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        ownerState.setTempState(WINDLASH_LAST_TRIGGER_KEY, getServerTick(owner));
    }

    /**
     * Get the remaining cooldown in ticks for Windlash Rider.
     */
    public static long getWindlashCooldownRemaining(ServerPlayerEntity owner) {
        int cooldownTicks = getWindlashCooldownTicks();
        if (cooldownTicks <= 0) {
            return 0;
        }

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        if (!ownerState.hasTempState(WINDLASH_LAST_TRIGGER_KEY)) {
            return 0;
        }

        long lastTriggerTick = ownerState.getTempState(WINDLASH_LAST_TRIGGER_KEY);
        long ticksSinceTrigger = getServerTick(owner) - lastTriggerTick;

        if (ticksSinceTrigger < 0) {
            return 0;
        }

        long remaining = cooldownTicks - ticksSinceTrigger;
        return Math.max(remaining, 0);
    }

    private static long getServerTick(ServerPlayerEntity owner) {
        var server = owner.getServer();
        if (server != null) {
            return server.getTicks();
        }
        return owner.getWorld().getTime();
    }
    
    /**
     * Apply skyrider role effects during server tick.
     */
    public static void onServerTick(TameableEntity pet, ServerPlayerEntity owner) {
        // Skyrider role effects are mostly event-driven
        // This method can be used for any periodic skyrider role effects in the future
    }
}