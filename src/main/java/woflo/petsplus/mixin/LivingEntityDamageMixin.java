package woflo.petsplus.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import woflo.petsplus.roles.guardian.GuardianFortressBondManager;

/**
 * Applies Guardian fortress bond damage reduction to bonded pets.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    @ModifyVariable(
        method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
        at = @At("HEAD"),
        argsOnly = true
    )
    private float petsplus_guardianFortressBond(float amount, ServerWorld world, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof MobEntity mob) {
            return GuardianFortressBondManager.modifyPetDamage(mob, amount);
        }
        return amount;
    }
}
