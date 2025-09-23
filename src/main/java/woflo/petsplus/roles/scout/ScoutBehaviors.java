package woflo.petsplus.roles.scout;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Scout role fallback behaviors when pet isn't attacking.
 */
public class ScoutBehaviors {
    
    /**
     * Apply Spotter fallback: Glowing to next target hit by owner if no pet attack within 60 ticks.
     */
    public static void checkSpotterFallback(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null || !combatState.isInCombat()) {
            return;
        }
        
        // Check if we have Scout pets
        boolean hasScoutPet = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SCOUT) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
        
        if (!hasScoutPet) {
            return;
        }
        
        // Check if no pet attack in last 60 ticks since combat start
        long combatStartTime = combatState.getLastHitTick();
        long timeSinceCombatStart = owner.getWorld().getTime() - combatStartTime;
        
        if (timeSinceCombatStart >= 60) {
            // Mark that next target hit by owner should get glowing (ICD 15s)
            long lastSpotterTime = combatState.getTempState("last_spotter_fallback");
            long currentTime = owner.getWorld().getTime();
            
            if (currentTime - lastSpotterTime >= 300) { // 15s ICD
                combatState.setTempState("spotter_fallback_ready", currentTime);
                combatState.setTempState("last_spotter_fallback", currentTime);
            }
        }
    }
    
    /**
     * Apply glowing effect to target if spotter fallback is ready.
     */
    public static void applySpotterGlowing(PlayerEntity owner, net.minecraft.entity.LivingEntity target) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return;
        }
        
        if (combatState.hasTempState("spotter_fallback_ready")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20, 0)); // 1 second
            combatState.setTempState("spotter_fallback_ready", 0); // Clear the flag
        }
    }
    
    /**
     * Apply Gale Pace speed to mount instead of owner if mounted.
     */
    public static void applyGalePaceToMount(PlayerEntity owner, StatusEffectInstance speedEffect) {
        if (owner.getVehicle() instanceof net.minecraft.entity.LivingEntity mount) {
            // Apply speed to mount instead of owner
            mount.addStatusEffect(speedEffect);
        } else {
            // Apply to owner normally
            owner.addStatusEffect(speedEffect);
        }
    }
}