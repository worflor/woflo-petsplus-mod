package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.PlayerTickDispatcher;

/**
 * Runs lightweight, player-local upkeep without needing global world tick scans.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        StateManager.forWorld((ServerWorld) player.getWorld()).handleOwnerTick(player);
        PlayerTickDispatcher.dispatch(player, player.getServer().getTicks());
    }
}
