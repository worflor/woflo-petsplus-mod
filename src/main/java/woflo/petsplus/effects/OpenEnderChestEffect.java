package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Opens the owner's ender chest UI when triggered.
 */
public class OpenEnderChestEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "open_ender_chest");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        if (!serverOwner.isAlive() || serverOwner.isRemoved() || serverOwner.isSpectator()) {
            return false;
        }

        EnderChestInventory enderChest = serverOwner.getEnderChestInventory();
        if (enderChest == null || !enderChest.canPlayerUse(serverOwner)) {
            return false;
        }

        serverOwner.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, player) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, enderChest),
            Text.translatable("container.enderchest")
        ));

        serverOwner.incrementStat(Stats.OPEN_ENDERCHEST);

        return true;
    }

    @Override
    public int getDurationTicks() {
        return 0;
    }
}
