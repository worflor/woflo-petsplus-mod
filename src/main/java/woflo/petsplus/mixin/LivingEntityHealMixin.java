package woflo.petsplus.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.RelationshipEventHandler;
import woflo.petsplus.state.PetComponent;

/**
 * Tracks healing events for the relationship system.
 * Detects when pets are healed to update trust/affection dimensions.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
    
    // Removed unused LAST_HEALER ThreadLocal to eliminate dead code.

    @Inject(method = "heal(F)V", at = @At("HEAD"))
    private void petsplus$trackHealing(float amount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        if (entity.getEntityWorld().isClient() || amount <= 0.0f) {
            return;
        }
        
        if (!(entity instanceof MobEntity mob)) {
            return;
        }
        
        PetComponent petComponent = PetComponent.get(mob);
        if (petComponent == null) {
            return;
        }
        
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) {
            return;
        }
        
        // Check if owner is nearby and likely responsible for healing
        // This catches healing from splash potions, feeding, etc.
        double distanceToOwner = owner.squaredDistanceTo(mob);
        if (distanceToOwner <= 64.0) { // 8 block radius
            RelationshipEventHandler.onPetHealed(mob, owner, amount);
        }
    }
}


