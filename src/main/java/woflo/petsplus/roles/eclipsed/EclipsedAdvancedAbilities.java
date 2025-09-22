package woflo.petsplus.roles.eclipsed;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Eclipsed role advanced abilities including Event Horizon and Edge Step.
 */
public class EclipsedAdvancedAbilities {
    
    /**
     * Create Event Horizon zone after Abyss Recall void save.
     */
    public static void createEventHorizon(PlayerEntity owner, Vec3d position) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        double radius = PetsPlusConfig.getInstance().getDouble("eclipsed", "eventHorizonRadius", 6.0);
        int duration = PetsPlusConfig.getInstance().getInt("eclipsed", "eventHorizonDurationTicks", 100);
        double projectileDr = PetsPlusConfig.getInstance().getDouble("eclipsed", "eventHorizonProjectileDrPct", 0.25);
        
        // Apply slowness to nearby mobs
        serverWorld.getEntitiesByClass(
            HostileEntity.class,
            net.minecraft.util.math.Box.of(position, radius * 2, radius * 2, radius * 2),
            entity -> entity.squaredDistanceTo(position) <= radius * radius
        ).forEach(mob -> {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 0));
        });
        
        // Set up projectile DR for owner
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.setTempState("event_horizon_dr_end", owner.getWorld().getTime() + duration);
        combatState.setTempState("event_horizon_dr_amount", (long) (projectileDr * 100)); // Store as percentage
    }
    
    /**
     * Check if owner has active Event Horizon projectile DR.
     */
    public static double getEventHorizonProjectileDr(PlayerEntity owner) {
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return 0.0;
        }
        
        long currentTime = owner.getWorld().getTime();
        long endTime = combatState.getTempState("event_horizon_dr_end");
        
        if (currentTime < endTime) {
            return combatState.getTempState("event_horizon_dr_amount") / 100.0;
        }
        
        return 0.0;
    }
    
    /**
     * Apply Edge Step fall damage reduction when perched.
     */
    public static float applyEdgeStepFallReduction(PlayerEntity owner, float fallDamage) {
        // Check if pet is perched (would need pet component reference)
        // Check cooldown
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return fallDamage;
        }
        
        long currentTime = owner.getWorld().getTime();
        long lastEdgeStep = combatState.getTempState("last_edge_step");
        int cooldown = PetsPlusConfig.getInstance().getInt("eclipsed", "edgeStepCooldownTicks", 240);
        
        if (currentTime - lastEdgeStep < cooldown) {
            return fallDamage; // Still on cooldown
        }
        
        // Apply fall reduction
        double reductionPct = PetsPlusConfig.getInstance().getDouble("eclipsed", "edgeStepFallReductionPct", 0.25);
        combatState.setTempState("last_edge_step", currentTime);
        
        return fallDamage * (1.0f - (float) reductionPct);
    }
    
    /**
     * Check if Edge Step should trigger (>3 blocks fall while perched).
     */
    public static boolean shouldTriggerEdgeStep(PlayerEntity owner, double fallDistance) {
        return fallDistance > 3.0 && isOwnerPetPerched(owner);
    }
    
    /**
     * Check if any of owner's pets are perched.
     */
    private static boolean isOwnerPetPerched(PlayerEntity owner) {
        // This would need integration with pet perching system
        // For now, return true as placeholder
        return true;
    }
}