package woflo.petsplus.effects;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.event.GameEvent;
import net.minecraft.particle.ParticleTypes;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundGearSwapManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Swaps the owner's current equipment with the stored alternate gear set.
 */
public class GearSwapEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "gear_swap");
    private static final int SLOT_MAIN_HAND = 0;
    private static final int SLOT_OFF_HAND = 1;
    private static final int SLOT_HELMET = 2;
    private static final int SLOT_CHEST = 3;
    private static final int SLOT_LEGS = 4;
    private static final int SLOT_BOOTS = 5;

    private final SoundEvent storeSound;
    private final SoundEvent swapSound;

    public GearSwapEffect(SoundEvent storeSound, SoundEvent swapSound) {
        this.storeSound = storeSound;
        this.swapSound = swapSound;
    }

    public static GearSwapEffect fromConfig(String storeSoundId, String swapSoundId) {
        return new GearSwapEffect(resolveSound(storeSoundId, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value()),
            resolveSound(swapSoundId, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND.value()));
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getWorld() instanceof ServerWorld)) {
            return false;
        }

        MobEntity pet = context.getPet();
        PlayerEntity ownerEntity = context.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity owner) || pet == null) {
            return false;
        }

        if (!owner.isAlive() || owner.isRemoved()) {
            return false;
        }

        if (!(owner.getWorld() instanceof ServerWorld ownerWorld) || ownerWorld != context.getWorld()) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            return false;
        }

        DefaultedList<ItemStack> currentGear = captureCurrentGear(owner);
        int activeSlot = EnchantmentBoundGearSwapManager.getActiveSlot(component);
        EnchantmentBoundGearSwapManager.storeSlot(component, activeSlot, currentGear);

        int alternateSlot = 1 - activeSlot;
        DefaultedList<ItemStack> alternateGear = EnchantmentBoundGearSwapManager.copySlot(component, alternateSlot);
        boolean hasAlternate = hasAny(alternateGear);

        EnchantmentBoundGearSwapManager.setActiveSlot(component, alternateSlot);

        if (!hasAlternate) {
            UIFeedbackManager.sendEnchantmentGearSwapStored(owner, pet);
            playSound(ownerWorld, pet, storeSound);
            spawnParticles(ownerWorld, owner, pet, false);
            owner.swingHand(Hand.MAIN_HAND);
            return true;
        }

        applyGear(owner, alternateGear);
        owner.getInventory().markDirty();
        owner.currentScreenHandler.sendContentUpdates();
        UIFeedbackManager.sendEnchantmentGearSwapSwapped(owner, pet);
        playSound(ownerWorld, pet, swapSound);
        spawnParticles(ownerWorld, owner, pet, true);
        owner.swingHand(Hand.MAIN_HAND);
        owner.emitGameEvent(GameEvent.EQUIP);
        return true;
    }

    private static DefaultedList<ItemStack> captureCurrentGear(ServerPlayerEntity owner) {
        DefaultedList<ItemStack> gear = DefaultedList.ofSize(EnchantmentBoundGearSwapManager.SLOT_COUNT, ItemStack.EMPTY);
        gear.set(SLOT_MAIN_HAND, owner.getMainHandStack().copy());
        gear.set(SLOT_OFF_HAND, owner.getOffHandStack().copy());
        gear.set(SLOT_HELMET, owner.getEquippedStack(EquipmentSlot.HEAD).copy());
        gear.set(SLOT_CHEST, owner.getEquippedStack(EquipmentSlot.CHEST).copy());
        gear.set(SLOT_LEGS, owner.getEquippedStack(EquipmentSlot.LEGS).copy());
        gear.set(SLOT_BOOTS, owner.getEquippedStack(EquipmentSlot.FEET).copy());
        return gear;
    }

    private static void applyGear(ServerPlayerEntity owner, DefaultedList<ItemStack> gear) {
        owner.setStackInHand(Hand.MAIN_HAND, copyOrEmpty(gear, SLOT_MAIN_HAND));
        owner.setStackInHand(Hand.OFF_HAND, copyOrEmpty(gear, SLOT_OFF_HAND));
        owner.equipStack(EquipmentSlot.HEAD, copyOrEmpty(gear, SLOT_HELMET));
        owner.equipStack(EquipmentSlot.CHEST, copyOrEmpty(gear, SLOT_CHEST));
        owner.equipStack(EquipmentSlot.LEGS, copyOrEmpty(gear, SLOT_LEGS));
        owner.equipStack(EquipmentSlot.FEET, copyOrEmpty(gear, SLOT_BOOTS));
    }

    private static ItemStack copyOrEmpty(DefaultedList<ItemStack> gear, int index) {
        if (index < 0 || index >= gear.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = gear.get(index);
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static boolean hasAny(DefaultedList<ItemStack> gear) {
        for (ItemStack stack : gear) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void playSound(ServerWorld world, MobEntity pet, SoundEvent sound) {
        if (sound == null) {
            return;
        }
        world.playSound(null, pet.getBlockPos(), sound, SoundCategory.PLAYERS, 0.9f, 1.0f);
    }

    private static void spawnParticles(ServerWorld world, ServerPlayerEntity owner, MobEntity pet, boolean swapped) {
        if (world == null) {
            return;
        }

        int count = swapped ? 32 : 20;
        double spread = swapped ? 0.75 : 0.5;
        world.spawnParticles(swapped ? ParticleTypes.END_ROD : ParticleTypes.CLOUD,
            owner.getX(), owner.getBodyY(0.5), owner.getZ(),
            count, spread, 0.6, spread, swapped ? 0.02 : 0.01);

        if (pet != null) {
            world.spawnParticles(swapped ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SOUL_FIRE_FLAME,
                pet.getX(), pet.getBodyY(0.5), pet.getZ(),
                12, 0.4, 0.3, 0.4, swapped ? 0.02 : 0.01);
        }
    }

    private static SoundEvent resolveSound(String id, SoundEvent fallback) {
        if (id == null || id.isEmpty()) {
            return fallback;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return fallback;
        }
        RegistryKey<SoundEvent> key = RegistryKey.of(RegistryKeys.SOUND_EVENT, parsed);
        return Registries.SOUND_EVENT.getOptionalValue(key).orElse(fallback);
    }
}
