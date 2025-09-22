package woflo.petsplus.roles.striker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Striker execution fallback behaviors.
 */
public class StrikerExecutionFallback {
    
    /**
     * Apply owner execution bonus vs targets under 35% HP if target has recent damage.
     */
    public static float applyOwnerExecuteBonus(PlayerEntity owner, LivingEntity target, float baseDamage) {
        StrikerExecution.ExecutionResult cached = StrikerExecution.consumeCachedExecutionResult(owner, target);
        if (cached != null) {
            return cached.totalDamage(baseDamage);
        }

        StrikerExecution.ExecutionResult result = StrikerExecution.evaluateExecution(owner, target, baseDamage, false);
        return result.totalDamage(baseDamage);
    }
    
    /**
     * Check if the target has recently taken damage from the owner or their pets.
     * This is a simplified implementation - could be enhanced with damage tracking.
     */
    // Delegated to StrikerExecution.hasRecentDamageWindow for correctness
}