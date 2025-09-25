package woflo.petsplus.effects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.roles.scout.ScoutBackpack;

/**
 * Provides a persistent backpack inventory tied to the pet.
 */
public class OpenPetBackpackEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "open_pet_backpack");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        MobEntity pet = context.getPet();
        if (!(owner instanceof ServerPlayerEntity serverOwner) || pet == null) {
            return false;
        }

        if (!serverOwner.isAlive() || serverOwner.isRemoved() || serverOwner.isSpectator() || serverOwner.isSleeping()) {
            return false;
        }

        if (!pet.isAlive() || pet.isRemoved()) {
            return false;
        }

        PetComponent component = PetComponent.getOrCreate(pet);
        if (component == null) {
            return false;
        }

        int storageSlots = ScoutBackpack.computeStorageSlots(component, pet);
        if (storageSlots <= 0) {
            return false;
        }

        DefaultedList<ItemStack> previousBacking = component.getInventoryIfPresent(ScoutBackpack.INVENTORY_KEY);
        if (previousBacking != null) {
            ScoutBackpack.handleOverflow(previousBacking, storageSlots, serverOwner, pet);
        }

        DefaultedList<ItemStack> backing = component.getInventory(ScoutBackpack.INVENTORY_KEY, storageSlots);
        component.setInventory(ScoutBackpack.INVENTORY_KEY, backing);

        ScoutBackpack.MenuInventory inventory = new ScoutBackpack.MenuInventory(backing, component, pet);
        int rows = inventory.getRows();

        Text title = Text.translatable("container.petsplus.scout_backpack", pet.getDisplayName());
        ScreenHandlerType<GenericContainerScreenHandler> handlerType = typeForRows(rows);

        serverOwner.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, player) -> new ScoutBackpack.ScoutBackpackScreenHandler(handlerType, syncId, playerInventory, inventory, pet.getUuid()),
            title
        ));

        return true;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeForRows(int rows) {
        return switch (MathHelper.clamp(rows, 1, 6)) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    @Override
    public int getDurationTicks() {
        return 0;
    }
}
