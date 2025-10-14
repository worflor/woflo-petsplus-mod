package woflo.petsplus.roles.scout;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.ActionBarUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Helper utilities for Scout backpack presentation and routing behaviour.
 */
public final class ScoutBackpack {
    public static final String INVENTORY_KEY = "scout_backpack";
    private static final String ROUTING_KEY = "scout_backpack_route_to_pet";
    private static final int MAX_ROWS = 6;
    private static final double PICKUP_RADIUS_SQ = 1.6 * 1.6;

    private static final ItemStack LOCKED_SLOT_STACK;

    static {
        ItemStack locked = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        locked.set(DataComponentTypes.CUSTOM_NAME,
            Text.translatable("container.petsplus.scout_backpack.locked").formatted(Formatting.DARK_GRAY));
        LOCKED_SLOT_STACK = locked.copy();
    }

    private ScoutBackpack() {
    }

    public static int computeStorageSlots(PetComponent component, MobEntity pet) {
        int baseRows = sizeRows(pet);
        int bonusRows = levelBonusRows(component.getLevel());
        int rows = MathHelper.clamp(baseRows + bonusRows, 1, MAX_ROWS);
        int slots = rows * 9;
        if (slots >= MAX_ROWS * 9) {
            slots = (MAX_ROWS - 1) * 9;
        }
        return slots;
    }

    private static int sizeRows(MobEntity pet) {
        float width = pet.getWidth();
        float height = pet.getHeight();
        float longest = Math.max(width, height);

        if (longest < 0.6f) {
            return 1;
        }
        if (longest < 1.2f) {
            return 2;
        }
        return 3;
    }

    private static int levelBonusRows(int level) {
        if (level >= 30) {
            return 3;
        }
        if (level >= 20) {
            return 2;
        }
        if (level >= 10) {
            return 1;
        }
        return 0;
    }

    public enum RoutingMode {
        PLAYER,
        PET,
        OFF;

        public RoutingMode next() {
            return switch (this) {
                case PLAYER -> PET;
                case PET -> OFF;
                case OFF -> PLAYER;
            };
        }
    }

    public static RoutingMode getRoutingMode(PetComponent component) {
        String stored = component.getStateData(ROUTING_KEY, String.class);
        if (stored != null) {
            try {
                return RoutingMode.valueOf(stored.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to legacy handling/default
            }
        }

        Boolean legacy = component.getStateData(ROUTING_KEY, Boolean.class);
        if (legacy != null) {
            RoutingMode mode = legacy ? RoutingMode.PET : RoutingMode.PLAYER;
            setRoutingMode(component, mode);
            return mode;
        }

        return RoutingMode.PLAYER;
    }

    public static boolean isRoutingToPet(PetComponent component) {
        return getRoutingMode(component) == RoutingMode.PET;
    }

    public static void setRoutingMode(PetComponent component, RoutingMode mode) {
        component.setStateData(ROUTING_KEY, mode.name());
    }

    public static boolean canRouteLootToBackpack(PetComponent component, MobEntity pet) {
        if (component == null || pet == null || !pet.isAlive()) {
            return false;
        }
        if (getRoutingMode(component) != RoutingMode.PET) {
            return false;
        }
        if (pet.getTarget() != null) {
            return false;
        }
        PetComponent.Mood mood = component.getCurrentMood();
        if (mood == null) {
            return true;
        }
        return switch (mood) {
            case CALM, BONDED, HAPPY, PLAYFUL, CURIOUS -> true;
            default -> false;
        };
    }

    public static int computeMenuSlots(int storageSlots) {
        int desired = Math.min(storageSlots + 1, MAX_ROWS * 9);
        int rows = MathHelper.clamp(MathHelper.ceilDiv(desired, 9), 1, MAX_ROWS);
        return rows * 9;
    }

    public static ItemStack createToggleStack(RoutingMode mode, MobEntity pet) {
        ItemStack stack = switch (mode) {
            case PET -> new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE);
            case PLAYER -> new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
            case OFF -> new ItemStack(Items.RED_STAINED_GLASS_PANE);
        };
        Text name = switch (mode) {
            case PET -> Text.translatable("container.petsplus.scout_backpack.toggle.pet", pet.getDisplayName())
                .formatted(Formatting.LIGHT_PURPLE);
            case PLAYER -> Text.translatable("container.petsplus.scout_backpack.toggle.player")
                .formatted(Formatting.AQUA);
            case OFF -> Text.translatable("container.petsplus.scout_backpack.toggle.off")
                .formatted(Formatting.RED);
        };
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.translatable("container.petsplus.scout_backpack.toggle.hint")
                .formatted(Formatting.GRAY)
        )));
        return stack;
    }

    public static ItemStack lockedStack() {
        return LOCKED_SLOT_STACK.copy();
    }

    public static boolean canFullyInsert(DefaultedList<ItemStack> backing, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int remaining = stack.getCount();
        for (int i = 0; i < backing.size() && remaining > 0; i++) {
            ItemStack existing = backing.get(i);
            if (existing.isEmpty()) {
                remaining -= Math.min(remaining, stack.getMaxCount());
            } else if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                int space = Math.min(existing.getMaxCount(), backing.get(i).getMaxCount()) - existing.getCount();
                if (space > 0) {
                    remaining -= Math.min(space, remaining);
                }
            }
        }
        return remaining <= 0;
    }

    public static void insertIntoBackpack(DefaultedList<ItemStack> backing, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < backing.size(); i++) {
            ItemStack existing = backing.get(i);
            if (!existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                int space = Math.min(existing.getMaxCount(), backing.get(i).getMaxCount()) - existing.getCount();
                if (space <= 0) {
                    continue;
                }
                int transfer = Math.min(space, stack.getCount());
                if (transfer <= 0) {
                    continue;
                }
                existing.increment(transfer);
                stack.decrement(transfer);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        if (!stack.isEmpty()) {
            for (int i = 0; i < backing.size(); i++) {
                ItemStack existing = backing.get(i);
                if (existing.isEmpty()) {
                    backing.set(i, stack.copy());
                    stack.setCount(0);
                    return;
                }
            }
        }
    }

    public static void handleOverflow(DefaultedList<ItemStack> previousBacking, int slotCount,
                                      @Nullable ServerPlayerEntity owner, MobEntity pet) {
        if (previousBacking == null || slotCount >= previousBacking.size()) {
            return;
        }

        for (int i = slotCount; i < previousBacking.size(); i++) {
            ItemStack stack = previousBacking.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            ItemStack overflow = stack.copy();
            previousBacking.set(i, ItemStack.EMPTY);

            if (owner != null) {
                boolean fullyInserted = owner.getInventory().insertStack(overflow);
                if (!fullyInserted && !overflow.isEmpty()) {
                    owner.dropItem(overflow, false);
                }
            } else if (pet.getEntityWorld() instanceof ServerWorld serverWorld && !overflow.isEmpty()) {
                ItemEntity entity = new ItemEntity(serverWorld, pet.getX(), pet.getBodyY(0.5), pet.getZ(), overflow);
                entity.setToDefaultPickupDelay();
                serverWorld.spawnEntity(entity);
            }
        }
    }

    public static boolean tryStoreItem(ServerWorld world, ServerPlayerEntity owner, MobEntity pet,
                                       PetComponent component, DefaultedList<ItemStack> backing,
                                       ItemEntity item) {
        if (item.isRemoved()) {
            return false;
        }
        ItemStack stack = item.getStack();
        if (!canFullyInsert(backing, stack)) {
            return false;
        }
        ItemStack copy = stack.copy();
        insertIntoBackpack(backing, copy);
        component.setInventory(INVENTORY_KEY, backing);

        Vec3d pos = pet.getEntityPos();
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ITEM_PICKUP,
            SoundCategory.PLAYERS, 0.25f, 1.2f + world.random.nextFloat() * 0.1f);
        if (owner != null) {
            owner.sendPickup(item, stack.getCount());
            owner.increaseStat(Stats.PICKED_UP.getOrCreateStat(stack.getItem()), stack.getCount());
        }
        item.discard();
        refreshOpenBackpack(owner, pet.getUuid());
        return true;
    }

    public static void refreshOpenBackpack(@Nullable ServerPlayerEntity owner, UUID petUuid) {
        if (owner == null) {
            return;
        }
        if (owner.currentScreenHandler instanceof ScoutBackpackScreenHandler handler && handler.isForPet(petUuid)) {
            handler.refreshFromBacking();
        }
    }

    public static boolean isWithinPickupRange(MobEntity pet, ItemEntity item) {
        return item.squaredDistanceTo(pet) <= PICKUP_RADIUS_SQ;
    }

    public static final class MenuInventory implements Inventory {
        private final DefaultedList<ItemStack> backing;
        private final PetComponent component;
        private final MobEntity pet;
        private final int menuSlots;
        private final int toggleSlot;
        private final int[] slotToStorageIndex;
        private ItemStack toggleStack;
        private boolean suppressDirty;

        public MenuInventory(DefaultedList<ItemStack> backing, PetComponent component, MobEntity pet) {
            this.backing = backing;
            this.component = component;
            this.pet = pet;
            int storageSlots = backing.size();
            this.menuSlots = computeMenuSlots(storageSlots);
            this.toggleSlot = Math.min(menuSlots - 1, MAX_ROWS * 9 - 1);
            this.slotToStorageIndex = new int[menuSlots];
            Arrays.fill(this.slotToStorageIndex, -2);
            int storageIndex = 0;
            for (int slot = 0; slot < menuSlots && storageIndex < storageSlots; slot++) {
                if (slot == toggleSlot) {
                    continue;
                }
                slotToStorageIndex[slot] = storageIndex++;
            }
            syncFromBacking();
        }

        public int getRows() {
            return MathHelper.clamp(menuSlots / 9, 1, MAX_ROWS);
        }

        public boolean isToggleSlot(int slot) {
            return slot == toggleSlot;
        }

        public void syncFromBacking() {
            this.toggleStack = createToggleStack(getRoutingMode(component), pet);
        }

        public void refreshToggle() {
            this.toggleStack = createToggleStack(getRoutingMode(component), pet);
        }

        @Override
        public int size() {
            return menuSlots;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : backing) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getStack(int slot) {
            if (slot == toggleSlot) {
                return toggleStack.copy();
            }
            int storageIndex = slotToStorageIndex[slot];
            if (storageIndex >= 0 && storageIndex < backing.size()) {
                return backing.get(storageIndex);
            }
            return lockedStack();
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            int storageIndex = slotToStorageIndex[slot];
            if (storageIndex >= 0) {
                ItemStack result = Inventories.splitStack(backing, storageIndex, amount);
                if (!result.isEmpty()) {
                    markDirty();
                }
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            int storageIndex = slotToStorageIndex[slot];
            if (storageIndex >= 0) {
                ItemStack result = Inventories.removeStack(backing, storageIndex);
                if (!result.isEmpty()) {
                    markDirty();
                }
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            int storageIndex = slotToStorageIndex[slot];
            if (storageIndex >= 0) {
                backing.set(storageIndex, stack);
                markDirty();
            }
        }

        @Override
        public void markDirty() {
            if (suppressDirty) {
                return;
            }
            suppressDirty = true;
            component.setInventory(INVENTORY_KEY, backing);
            suppressDirty = false;
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return component.isOwnedBy(player);
        }

        @Override
        public void clear() {
            for (int i = 0; i < backing.size(); i++) {
                backing.set(i, ItemStack.EMPTY);
            }
            markDirty();
        }

        public RoutingMode toggleRouting(ServerPlayerEntity player) {
            RoutingMode next = getRoutingMode(component).next();
            setRoutingMode(component, next);
            refreshToggle();
            Text feedback = switch (next) {
                case PET -> Text.translatable("message.petsplus.scout_backpack.route.pet", pet.getDisplayName());
                case PLAYER -> Text.translatable("message.petsplus.scout_backpack.route.player");
                case OFF -> Text.translatable("message.petsplus.scout_backpack.route.off");
            };
            ActionBarUtils.sendActionBar(player, feedback);
            return next;
        }
    }

    public static class ScoutBackpackScreenHandler extends GenericContainerScreenHandler {
        private final MenuInventory inventory;
        private final UUID petUuid;

        public ScoutBackpackScreenHandler(ScreenHandlerType<GenericContainerScreenHandler> type, int syncId,
                                          net.minecraft.entity.player.PlayerInventory playerInventory,
                                          MenuInventory inventory, UUID petUuid) {
            super(type, syncId, playerInventory, inventory, inventory.getRows());
            this.inventory = inventory;
            this.petUuid = petUuid;
        }

        @Override
        public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType,
                                PlayerEntity player) {
            if (slot >= 0 && slot < inventory.size() && inventory.isToggleSlot(slot) && player instanceof ServerPlayerEntity serverPlayer) {
                inventory.toggleRouting(serverPlayer);
                inventory.refreshToggle();
                sendContentUpdates();
                return;
            }
            super.onSlotClick(slot, button, actionType, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return inventory.canPlayerUse(player);
        }

        public boolean isForPet(UUID petUuid) {
            return this.petUuid.equals(petUuid);
        }

        public void refreshFromBacking() {
            inventory.syncFromBacking();
            for (Slot slot : slots) {
                slot.markDirty();
            }
            sendContentUpdates();
        }
    }
}


