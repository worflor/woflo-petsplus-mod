package woflo.petsplus.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.effects.OwnerNextAttackBonusEffect;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Handles damage modification for owner attacks based on pet abilities.
 */
public class OwnerAttackRider {
    
    /**
     * Calculate the final damage amount for an owner's attack, applying any active riders.
     */
    public static float calculateDamage(PlayerEntity owner, LivingEntity target, float baseDamage, DamageSource damageSource) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return baseDamage;
        }
        
        float finalDamage = baseDamage;
        
        // Apply damage bonus riders
        if (combatState.hasNextAttackRider("damage_bonus")) {
            OwnerNextAttackBonusEffect.AttackRiderData riderData = 
                combatState.getNextAttackRider("damage_bonus", OwnerNextAttackBonusEffect.AttackRiderData.class);
            
            if (riderData != null) {
                // Check if the rider applies to this target
                if (riderData.appliesToTarget(target)) {
                    finalDamage *= (1.0f + riderData.bonusDamagePct);
                    
                    Petsplus.LOGGER.debug("Applied damage bonus of {}% to attack on {}", 
                        riderData.bonusDamagePct * 100, target.getType().toString());
                }
                
                // Clear the rider after use
                combatState.clearNextAttackRider("damage_bonus");
            }
        }
        
        return finalDamage;
    }
    
    /**
     * Apply on-hit effects from pet abilities after the owner damages a target.
     */
    public static void applyOnHitEffects(PlayerEntity owner, LivingEntity target, float damage, DamageSource damageSource) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return;
        }
        
        // Apply status effect riders
        if (combatState.hasNextAttackRider("damage_bonus")) {
            OwnerNextAttackBonusEffect.AttackRiderData riderData = 
                combatState.getNextAttackRider("damage_bonus", OwnerNextAttackBonusEffect.AttackRiderData.class);
            
            if (riderData != null && riderData.onHitEffect != null) {
                if (riderData.appliesToTarget(target)) {
                    // Create effect context for the on-hit effect
                    woflo.petsplus.api.EffectContext effectContext = new woflo.petsplus.api.EffectContext(
                        (net.minecraft.server.world.ServerWorld) owner.getWorld(), 
                        null, // Pet may be null for owner-triggered effects
                        owner,
                        null // TriggerContext may be null for on-hit effects
                    ).withData("target", target);
                    
                    // Execute the on-hit effect
                    riderData.onHitEffect.execute(effectContext);
                    
                    Petsplus.LOGGER.debug("Applied on-hit effect {} to {}", 
                        riderData.onHitEffect.getId(), target.getType().toString());
                }
            }
        }
        
        // Apply healing riders
        if (combatState.hasNextAttackRider("heal_on_hit")) {
            Double healAmount = combatState.getNextAttackRider("heal_on_hit", Double.class);
            if (healAmount != null) {
                float maxHealth = owner.getMaxHealth();
                float healValue = (float) (maxHealth * healAmount);
                owner.heal(healValue);
                
                combatState.clearNextAttackRider("heal_on_hit");
                
                Petsplus.LOGGER.debug("Healed owner for {} health ({}%)", healValue, healAmount * 100);
            }
        }
    }
    
    /**
     * Check if the owner has any active attack riders.
     */
    public static boolean hasActiveRiders(PlayerEntity owner) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        return combatState != null && (
            combatState.hasNextAttackRider("damage_bonus") ||
            combatState.hasNextAttackRider("heal_on_hit") ||
            combatState.hasNextAttackRider("status_effect")
        );
    }
    
    /**
     * Clear all expired attack riders based on current world time.
     */
    public static void clearExpiredRiders(PlayerEntity owner) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return;
        }
        
        long currentTime = owner.getWorld().getTime();
        
        // Check damage bonus rider expiry
        OwnerNextAttackBonusEffect.AttackRiderData riderData = 
            combatState.getNextAttackRider("damage_bonus", OwnerNextAttackBonusEffect.AttackRiderData.class);
        
        if (riderData != null && riderData.isExpired(currentTime)) {
            combatState.clearNextAttackRider("damage_bonus");
            Petsplus.LOGGER.debug("Cleared expired damage bonus rider for {}", owner.getName().getString());
        }
    }
}