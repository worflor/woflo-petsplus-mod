package woflo.petsplus.events;

import java.util.Optional;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;

import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.items.AbilityTokenItem;
import woflo.petsplus.items.PetsplusItems;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.ProgressionModule;
import woflo.petsplus.ui.ActionBarUtils;

/**
 * Handles using ability tokens on owned pets to grant new abilities.
 */
public final class AbilityTokenInteractionHandler {
    private AbilityTokenInteractionHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register(AbilityTokenInteractionHandler::onUseEntity);
    }

    private static ActionResult onUseEntity(PlayerEntity player,
                                            net.minecraft.world.World world,
                                            Hand hand,
                                            Entity entity,
                                            EntityHitResult hitResult) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        if (!(entity instanceof MobEntity mob)) {
            return ActionResult.PASS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        ServerWorld serverWorld = (ServerWorld) world;

        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(PetsplusItems.ABILITY_TOKEN)) {
            return ActionResult.PASS;
        }

        if (!player.isSneaking()) {
            return ActionResult.PASS;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null || !component.isOwnedBy(player)) {
            return ActionResult.PASS;
        }

        Optional<Identifier> abilityIdOpt = AbilityTokenItem.getAbilityId(stack);
        if (abilityIdOpt.isEmpty()) {
            serverPlayer.sendMessage(Text.translatable("petsplus.ability_token.missing"), true);
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        Identifier abilityId = abilityIdOpt.get();
        AbilityType abilityType = PetsPlusRegistries.abilityTypeRegistry().get(abilityId);
        MutableText abilityName = AbilityTokenItem.abilityDisplayName(abilityId);

        if (abilityType == null) {
            serverPlayer.sendMessage(Text.translatable("petsplus.ability_token.invalid", abilityId.toString()), true);
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        if (component.isAbilityUnlocked(abilityId)) {
            serverPlayer.sendMessage(
                Text.translatable(
                    "petsplus.ability_token.duplicate",
                    mob.getDisplayName(),
                    abilityName.copy()
                ),
                true
            );
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        ProgressionModule progression = component.getProgressionModule();
        if (progression == null) {
            serverPlayer.sendMessage(
                Text.translatable("petsplus.ability_token.progression_missing", mob.getDisplayName()),
                true
            );
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        if (progression.getAvailableAbilitySlots() <= 0) {
            serverPlayer.sendMessage(
                Text.translatable("petsplus.ability_token.slots_full", mob.getDisplayName()),
                true
            );
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        boolean unlocked = component.unlockAbility(abilityId);
        if (!unlocked) {
            serverPlayer.sendMessage(
                Text.translatable(
                    "petsplus.ability_token.failure",
                    abilityName.copy(),
                    mob.getDisplayName()
                ),
                true
            );
            playFailureFeedback(serverWorld, mob);
            return ActionResult.SUCCESS;
        }

        if (!serverPlayer.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        playSuccessFeedback(serverWorld, mob);
        serverPlayer.sendMessage(
            Text.translatable(
                "petsplus.ability_token.unlock",
                mob.getDisplayName(),
                abilityName.copy()
            ),
            true
        );
        ActionBarUtils.sendActionBar(
            serverPlayer,
            Text.translatable("petsplus.ability_token.slots_remaining", progression.getAvailableAbilitySlots())
        );
        return ActionResult.SUCCESS;
    }

    private static void playFailureFeedback(ServerWorld world, MobEntity mob) {
        world.playSound(
            null,
            mob.getX(),
            mob.getY(),
            mob.getZ(),
            SoundEvents.BLOCK_NOTE_BLOCK_BASS,
            SoundCategory.PLAYERS,
            0.6f,
            0.6f
        );
        world.spawnParticles(
            ParticleTypes.SMOKE,
            mob.getX(),
            mob.getY() + mob.getHeight() * 0.6,
            mob.getZ(),
            6,
            0.2,
            0.2,
            0.2,
            0.01
        );
    }

    private static void playSuccessFeedback(ServerWorld world, MobEntity mob) {
        world.playSound(
            null,
            mob.getX(),
            mob.getY(),
            mob.getZ(),
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS,
            0.75f,
            1.0f
        );
        world.spawnParticles(
            ParticleTypes.HAPPY_VILLAGER,
            mob.getX(),
            mob.getY() + mob.getHeight() * 0.6,
            mob.getZ(),
            12,
            0.3,
            0.3,
            0.3,
            0.02
        );
    }
}
