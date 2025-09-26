package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.events.PlayerStateTracker;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;
import woflo.petsplus.mechanics.StargazeMechanic;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.ActionBarCueManager;
import woflo.petsplus.ui.BossBarManager;
import woflo.petsplus.ui.PetInspectionManager;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.roles.eclipsed.EclipsedCore;
import woflo.petsplus.roles.guardian.GuardianCore;
import woflo.petsplus.roles.skyrider.SkyriderCore;
import woflo.petsplus.roles.eepyeeper.EepyEeperCore;
import woflo.petsplus.roles.scout.ScoutCore;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundCore;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundHandler;

/**
 * Runs lightweight, player-local upkeep without needing global world tick scans.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        StateManager.forWorld((ServerWorld) player.getWorld()).handleOwnerTick(player);
        EmotionContextCues.handlePlayerTick(player);
        EmotionsEventHandler.handlePlayerTick(player);
        PlayerStateTracker.handlePlayerTick(player);
        OwnerAbilitySignalTracker.handlePlayerTick(player);
        StargazeMechanic.handlePlayerTick(player);
        ActionBarCueManager.handlePlayerTick(player);
        PetInspectionManager.handlePlayerTick(player);
        BossBarManager.handlePlayerTick(player);
        MagnetizeDropsAndXpEffect.handlePlayerTick(player);
        EclipsedCore.handlePlayerTick(player);
        GuardianCore.handlePlayerTick(player);
        SkyriderCore.handlePlayerTick(player);
        EepyEeperCore.handlePlayerTick(player);
        ScoutCore.handlePlayerTick(player);
        EnchantmentBoundCore.handlePlayerTick(player);
        EnchantmentBoundHandler.handlePlayerTick(player);
    }
}
