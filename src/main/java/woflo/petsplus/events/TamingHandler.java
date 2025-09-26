package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.taming.CustomTameables;

/**
 * Handles custom taming interactions for entities that aren't vanilla tameables.
 */
public final class TamingHandler {
    private TamingHandler() {}

    public static void register() {
        CustomTameables.ensureDefaultsRegistered();
        UseEntityCallback.EVENT.register(TamingHandler::onUseEntity);
        Petsplus.LOGGER.info("Custom taming handler registered");
    }

    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        if (!(entity instanceof MobEntity mob) || !(mob instanceof PetsplusTameable tameable)) {
            return ActionResult.PASS;
        }

        CustomTameables.Definition definition = CustomTameables.get(mob.getType());
        if (definition == null) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        ServerWorld serverWorld = (ServerWorld) world;

        if (!tameable.petsplus$isTamed()) {
            if (!definition.isTamingItem(stack)) {
                return ActionResult.PASS;
            }

            tameEntity(player, serverWorld, hand, mob, tameable, definition);
            if (definition.consumeItem() && !player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            return ActionResult.SUCCESS;
        }

        return toggleSitting(player, serverWorld, hand, mob, tameable, definition);
    }

    private static void tameEntity(PlayerEntity player, ServerWorld world, Hand hand, MobEntity mob,
                                   PetsplusTameable tameable, CustomTameables.Definition definition) {
        tameable.petsplus$setTamed(true);
        tameable.petsplus$setOwner(player);
        tameable.petsplus$setSitting(false);
        mob.setPersistent();

        PetComponent component = PetComponent.getOrCreate(mob);
        component.setOwner(player);
        component.setStateData("petsplus:tamed", true);
        component.setStateData("petsplus:sitting", false);
        component.setStateData("petsplus:owner_uuid", player.getUuidAsString());

        spawnHearts(world, mob);
        world.playSound(null, mob.getBlockPos(), definition.tameSound(), SoundCategory.NEUTRAL, 0.8f, 1.0f);

        player.swingHand(hand, true);

        if (player instanceof ServerPlayerEntity serverPlayer) {
            PetDetectionHandler.onEntityTamed(mob, serverPlayer);
        } else {
            PetDetectionHandler.onEntityTamed(mob, player);
        }
    }

    private static ActionResult toggleSitting(PlayerEntity player, ServerWorld world, Hand hand, MobEntity mob,
                                              PetsplusTameable tameable, CustomTameables.Definition definition) {
        if (!tameable.petsplus$isOwnedBy(player)) {
            return ActionResult.PASS;
        }

        if (!player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }

        if (player.isSneaking()) {
            // Sneaking with empty hand is reserved for petting interactions.
            return ActionResult.PASS;
        }

        boolean newState = !tameable.petsplus$isSitting();
        tameable.petsplus$setSitting(newState);
        PetComponent.getOrCreate(mob).setStateData("petsplus:sitting", newState);
        world.playSound(null, mob.getBlockPos(), definition.sitSound(), SoundCategory.NEUTRAL, 0.6f, newState ? 0.8f : 1.1f);
        player.swingHand(hand, true);
        return ActionResult.SUCCESS;
    }

    private static void spawnHearts(ServerWorld world, MobEntity mob) {
        world.spawnParticles(ParticleTypes.HEART,
            mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(),
            6, 0.4, 0.4, 0.4, 0.02);
    }
}
