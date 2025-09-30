package woflo.petsplus.roles.enchantmentbound;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Maintains persistent storage for the Enchantment-Bound gear swap ability.
 */
public final class EnchantmentBoundGearSwapManager {
    private static final String ACTIVE_SLOT_KEY = "enchantment_bound_gear_swap_active_slot";
    private static final String SLOT_KEY_PREFIX = "enchantment_bound_gear_swap_slot_";
    public static final int SLOT_COUNT = 6;

    private EnchantmentBoundGearSwapManager() {
    }

    /** Resolves the active slot index, defaulting to {@code 0}. */
    public static int getActiveSlot(PetComponent component) {
        Integer stored = component.getStateData(ACTIVE_SLOT_KEY, Integer.class);
        if (stored == null) {
            component.setStateData(ACTIVE_SLOT_KEY, 0);
            return 0;
        }
        int clamped = MathHelper.clamp(stored, 0, 1);
        if (clamped != stored) {
            component.setStateData(ACTIVE_SLOT_KEY, clamped);
        }
        return clamped;
    }

    /** Updates the active slot index. */
    public static void setActiveSlot(PetComponent component, int slot) {
        int clamped = MathHelper.clamp(slot, 0, 1);
        component.setStateData(ACTIVE_SLOT_KEY, clamped);
    }

    /** Returns a defensive copy of the stored gear for the supplied slot. */
    public static DefaultedList<ItemStack> copySlot(PetComponent component, int slot) {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
        DefaultedList<ItemStack> backing = component.getInventoryIfPresent(keyFor(slot));
        if (backing == null) {
            return copy;
        }
        for (int i = 0; i < Math.min(SLOT_COUNT, backing.size()); i++) {
            ItemStack stack = backing.get(i);
            copy.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    /** Stores the provided gear snapshot into the supplied slot. */
    public static void storeSlot(PetComponent component, int slot, DefaultedList<ItemStack> gear) {
        DefaultedList<ItemStack> stored = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < SLOT_COUNT && i < gear.size(); i++) {
            ItemStack stack = gear.get(i);
            stored.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        component.setInventory(keyFor(slot), stored);
    }

    /** Clears the stored gear for the supplied slot. */
    public static void clearSlot(PetComponent component, int slot) {
        component.setInventory(keyFor(slot), DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY));
    }

    /** Drops all stored gear on the ground, returning {@code true} if any items were emitted. */
    public static boolean dropStoredGear(MobEntity pet, PetComponent component) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        boolean dropped = false;
        for (int slot = 0; slot <= 1; slot++) {
            DefaultedList<ItemStack> backing = component.getInventoryIfPresent(keyFor(slot));
            if (backing == null) {
                continue;
            }
            for (ItemStack stack : backing) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemScatterer.spawn(serverWorld, pet.getX(), pet.getY() + 0.5, pet.getZ(), stack.copy());
                dropped = true;
            }
            clearSlot(component, slot);
        }
        return dropped;
    }

    private static String keyFor(int slot) {
        int clamped = MathHelper.clamp(slot, 0, 1);
        return SLOT_KEY_PREFIX + clamped;
    }
}
