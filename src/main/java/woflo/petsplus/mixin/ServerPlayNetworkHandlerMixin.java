package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.mechanics.StargazeMechanic;
import woflo.petsplus.api.event.OwnerAbilitySignalEvent;
import woflo.petsplus.state.PetComponent;

/**
 * Captures movement packets so the emotion system can track idle time without
 * scanning every player each tick.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("TAIL"))
    private void petsplus$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        EmotionsEventHandler.handlePlayerMovement(this.player);
        StargazeMechanic.handlePlayerMove(this.player);
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("TAIL"))
    private void petsplus$onPlayerInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity serverPlayer = this.player;
        if (serverPlayer == null || serverPlayer.isSpectator() || !serverPlayer.isSneaking()) {
            return;
        }

        if (!(serverPlayer.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (isAttack(packet)) {
            return;
        }

        Entity target = packet.getEntity(serverWorld);
        if (!(target instanceof MobEntity mob) || mob.isRemoved() || !mob.isAlive()) {
            return;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null || !component.isOwnedBy(serverPlayer) || component.isPerched()) {
            return;
        }

        // Require an active crouch cuddle with the owner before emitting the shift-interact signal
        long now = serverWorld.getTime();
        if (!component.isCrouchCuddleActiveWith(serverPlayer, now)) {
            return;
        }

        if (mob instanceof TameableEntity tameable && !tameable.isInSittingPose()) {
            return;
        }

        OwnerAbilitySignalEvent.fire(OwnerAbilitySignalEvent.Type.SHIFT_INTERACT, serverPlayer, mob);
    }

    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        // Use anonymous class instead of local class to avoid Mixin package restrictions
        final boolean[] isAttack = {false};

        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void attack() {
                isAttack[0] = true;
            }

            @Override
            public void interact(Hand hand) {
            }

            @Override
            public void interactAt(Hand hand, Vec3d pos) {
            }
        });

        return isAttack[0];
    }
}
