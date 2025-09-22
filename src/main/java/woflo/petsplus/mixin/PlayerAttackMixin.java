package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.roles.striker.StrikerExecution;

/**
 * Mixin to intercept player attacks and apply Striker execution bonuses.
 */
@Mixin(PlayerEntity.class)
public class PlayerAttackMixin {
    
    /**
     * Modify damage to include Striker execution bonus.
     */
    @ModifyVariable(
        method = "attack",
        at = @At("STORE"),
        ordinal = 0
    )
    private float petsplus_addStrikerExecutionBonus(float damage, Entity target) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        if (target instanceof LivingEntity livingTarget) {
            StrikerExecution.ExecutionResult execution = StrikerExecution.evaluateExecution(player, livingTarget, damage);
            StrikerExecution.cacheExecutionResult(player, livingTarget, execution);
            if (execution.triggered() && player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                // Emit feedback for successful execution
                woflo.petsplus.ui.FeedbackManager.emitStrikerExecution(player, livingTarget, serverWorld,
                        execution.momentumStacks(), execution.momentumFill());
            }
            return execution.totalDamage(damage);
        }

        return damage;
    }
    
    /**
     * Track when owner deals damage for Striker abilities.
     */
    @Inject(
        method = "attack",
        at = @At("TAIL")
    )
    private void petsplus_trackStrikerDamage(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        if (target instanceof LivingEntity livingTarget) {
            // Calculate the actual damage dealt (simplified)
            float damage = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
            
            // Notify Striker system of damage dealt
            StrikerExecution.onOwnerDealDamage(player, livingTarget, damage);
            
            // Handle finisher mark if present
            StrikerExecution.onAttackFinisherMark(player, livingTarget, damage);
        }
    }
}