package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemUsage;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import net.minecraft.text.Text;

/**
 * Handles right-click interactions to give potions to Support pets.
 */
public class SupportInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(SupportInteractionHandler::onUseEntity);
        Petsplus.LOGGER.info("Support interaction handler registered");
    }

    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(entity instanceof MobEntity mob)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        PetComponent comp = PetComponent.get(mob);
        if (comp == null || !comp.hasRole(PetRoleType.SUPPORT)) return ActionResult.PASS;

        // Must be owner
        if (!comp.isOwnedBy(player)) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(Items.MILK_BUCKET)) {
            boolean hadStoredPotion = woflo.petsplus.roles.support.SupportPotionUtils.hasStoredPotion(comp);
            if (!hadStoredPotion) {
                return ActionResult.PASS;
            }

            woflo.petsplus.roles.support.SupportPotionUtils.clearStoredPotion(comp);

            ItemStack emptied = ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET));
            player.setStackInHand(hand, emptied);
            if (player.getAbilities().creativeMode) {
                player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            }

            ServerWorld serverWorld = (ServerWorld) world;
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(),
                12, 0.2, 0.3, 0.2, 0.01);
            serverWorld.playSound(null, mob.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                0.8f, 1.3f);
            EmotionContextCues.sendCue(serverPlayer,
                "support.potion.ready." + mob.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.support.potion_ready", mob.getDisplayName()),
                200);

            return ActionResult.SUCCESS;
        }

        // Must be holding a potion (regular, splash, or lingering)
        if (!(stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION))) {
            return ActionResult.PASS;
        }

        // Enforce single-slot: if already has a stored potion, reject
        if (woflo.petsplus.roles.support.SupportPotionUtils.hasStoredPotion(comp)) {
            EmotionContextCues.sendCue(serverPlayer,
                "support.potion.full." + mob.getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.support.potion_full", mob.getDisplayName()),
                200);
            return ActionResult.SUCCESS; // handled
        }

        var storedState = woflo.petsplus.roles.support.SupportPotionUtils.createStateFromStack(stack, comp);
        if (!storedState.isValid()) {
            return ActionResult.PASS; // Not a potion with effects (e.g., water)
        }
        woflo.petsplus.roles.support.SupportPotionUtils.writeStoredState(comp, storedState);

        // Consume exactly one from the player stack
        stack.decrement(1);

        // Feedback
        ((ServerWorld) world).spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
            mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(),
            7, 0.2, 0.3, 0.2, 0.02);
        EmotionContextCues.sendCue(serverPlayer,
            "support.potion.stored." + mob.getUuidAsString(),
            Text.translatable("petsplus.emotion_cue.support.potion_stored", mob.getDisplayName()),
            200);

        return ActionResult.SUCCESS;
    }
}
