package woflo.petsplus.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.mechanics.CursedOneResurrection;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.AfterimageManager;

/**
 * Ensures pet-scoped upkeep runs alongside the entity's own tick rather than
 * through a world-level sweep.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$onTick(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (!(mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return;
        }

        StateManager.forWorld(serverWorld).handlePetTick(mob);
        AfterimageManager.handleMobTick(mob, serverWorld);
        CursedOneResurrection.handleMobTick(mob, serverWorld);
        MagnetizeDropsAndXpEffect.handleMobTick(mob, serverWorld);
    }
}

