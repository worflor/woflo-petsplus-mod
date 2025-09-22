package woflo.petsplus.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import woflo.petsplus.roles.guardian.GuardianBulwark;

/**
 * Mixin to intercept damage dealt to players and allow Guardian pets to redirect it.
 */
@Mixin(PlayerEntity.class)
public class PlayerDamageMixin {
    
    /**
     * Intercept damage before it's applied to allow Guardian redirection.
     */
    @ModifyVariable(
        method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
        at = @At("HEAD"),
        argsOnly = true
    )
    private float petsplus_redirectGuardianDamage(float amount, net.minecraft.server.world.ServerWorld world, DamageSource source) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Try to redirect damage through Guardian Bulwark
        return GuardianBulwark.tryRedirectDamage(player, amount, source);
    }
}