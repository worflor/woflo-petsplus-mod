package woflo.petsplus.roles.striker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.BossSafetyUtil;

/**
 * Striker execution fallback behaviors.
 */
public class StrikerExecutionFallback {
    
    /**
     * Apply owner execution bonus vs targets under 35% HP if target has recent damage.
     */
    public static float applyOwnerExecuteBonus(PlayerEntity owner, LivingEntity target, float baseDamage) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return baseDamage;
        }
        
        // Safety: avoid allies/teammates and bosses
        if (owner.isTeammate(target)) return baseDamage;
        if (BossSafetyUtil.isBossEntity(target)) return baseDamage;

        // Check if target is under configured HP threshold
        double threshold = Math.max(0, Math.min(1, PetsPlusConfig.getInstance().getDouble("striker", "executeThresholdPct", 0.35)));
        float targetHealthPct = Math.max(0f, target.getHealth()) / Math.max(1f, target.getMaxHealth());
        if (targetHealthPct > (float) threshold) {
            return baseDamage;
        }
        
        // Check if we have Striker pets
        boolean hasStrikerPet = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole().equals(PetRole.STRIKER) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
        
        if (!hasStrikerPet) {
            return baseDamage;
        }
        
        // Check if target has recent damage from owner or pet
        if (woflo.petsplus.roles.striker.StrikerExecution.hasRecentDamageWindow(target, owner)) {
            double bonusPct = PetsPlusConfig.getInstance().getDouble("striker", "ownerExecuteBonusPct", 0.10);
            return baseDamage * (1.0f + (float) bonusPct);
        }
        
        return baseDamage;
    }
    
    /**
     * Check if the target has recently taken damage from the owner or their pets.
     * This is a simplified implementation - could be enhanced with damage tracking.
     */
    // Delegated to StrikerExecution.hasRecentDamageWindow for correctness
}