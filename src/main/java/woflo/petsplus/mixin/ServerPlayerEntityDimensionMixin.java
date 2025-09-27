package woflo.petsplus.mixin;

import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import woflo.petsplus.state.PlayerTickDispatcher;

/**
 * Clears scheduled tick listeners when a player changes dimension so that
 * subsystems can rebuild their cadence in the new world without inheriting
 * stale state from the previous dimension.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityDimensionMixin {

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"))
    private void petsplus$clearDispatcherOnDimensionChange(
        ServerWorld targetWorld,
        double x,
        double y,
        double z,
        Set<PositionFlag> movementFlags,
        float yaw,
        float pitch,
        boolean teleportComplete,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (targetWorld != null && targetWorld != player.getWorld()) {
            PlayerTickDispatcher.clearPlayer(player);
        }
    }
}

