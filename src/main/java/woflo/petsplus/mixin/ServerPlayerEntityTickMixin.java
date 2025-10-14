package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.state.StateManager;

/**
 * Runs lightweight, player-local upkeep without needing global world tick scans.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        StateManager.forWorld((ServerWorld) player.getEntityWorld()).handleOwnerTick(player);
    }
}


