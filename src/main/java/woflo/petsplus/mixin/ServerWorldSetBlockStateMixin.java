package woflo.petsplus.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.state.StateManager;

/**
 * Hooks world block mutations so arcane ambient caches invalidate even when
 * blocks change outside the standard player interaction paths (e.g. pistons,
 * explosions, block updates).
 */
@Mixin(World.class)
public abstract class ServerWorldSetBlockStateMixin {

    @Unique
    private static final ThreadLocal<BlockState> PETSPLUS$PREVIOUS_ARCANE_STATE = ThreadLocal.withInitial(() -> null);

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void petsplus$capturePreviousState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerWorld serverWorld)) {
            PETSPLUS$PREVIOUS_ARCANE_STATE.remove();
            return;
        }

        PETSPLUS$PREVIOUS_ARCANE_STATE.set(serverWorld.getBlockState(pos));
    }

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void petsplus$invalidateArcaneAmbientOnChange(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth,
                                                          CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue()) {
                return;
            }

            BlockState previous = PETSPLUS$PREVIOUS_ARCANE_STATE.get();
            if (!EmotionsEventHandler.isArcaneAmbientContributor(previous)
                && !EmotionsEventHandler.isArcaneAmbientContributor(newState)) {
                return;
            }

            if (!((Object) this instanceof ServerWorld serverWorld)) {
                return;
            }

            StateManager.invalidateArcaneAmbientAt(serverWorld, pos);
        } finally {
            PETSPLUS$PREVIOUS_ARCANE_STATE.remove();
        }
    }
}
