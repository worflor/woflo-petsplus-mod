package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.mechanics.StargazeMechanic;
import woflo.petsplus.state.PetComponent;

/**
 * Handles tribute payments for pet milestone unlocks using configurable tribute items.
 */
public class TributeHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(TributeHandler::onUseEntity);
    }

    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(entity instanceof MobEntity mob)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        PetComponent petComp = PetComponent.get(mob);
        if (petComp == null) return ActionResult.PASS;

        // Must be owner
        if (!petComp.isOwnedBy(player)) return ActionResult.PASS;

        // Must be sneaking for tribute payment
        if (!player.isSneaking()) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return ActionResult.PASS;

        // Check if pet needs tribute and if item is correct
        TributeInfo tributeInfo = getTributeInfo(petComp);
        if (tributeInfo == null || tributeInfo.item == null) return ActionResult.PASS;

        if (!isTributeItem(stack, tributeInfo.item)) return ActionResult.PASS;

        // Pay tribute
        boolean success = payTribute(serverPlayer, mob, petComp, tributeInfo, stack);
        return success ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    /**
     * Get tribute information for a pet if it needs tribute
     */
    private static TributeInfo getTributeInfo(PetComponent petComp) {
        int level = petComp.getLevel();
        PetsPlusConfig config = PetsPlusConfig.getInstance();

        // Check if this level requires tribute and is not yet unlocked
        if (config.hasTributeLevel(level) && !petComp.isMilestoneUnlocked(level)) {
            return createTributeInfo(level);
        }

        return null;
    }

    private static TributeInfo createTributeInfo(int level) {
        Item item = resolveTributeItem(level);
        return item == null ? null : new TributeInfo(level, item);
    }

    private static Item resolveTributeItem(int level) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        String configuredId = config.getTributeItemId(level);
        Item item = parseItemId(configuredId);

        if (item == null) {
            String fallbackId = config.getDefaultTributeItemId(level);
            if (!fallbackId.equals(configuredId)) {
                item = parseItemId(fallbackId);
                if (item != null) {
                    woflo.petsplus.Petsplus.LOGGER.warn("Invalid tribute item '{}' for level {}. Falling back to default '{}'", configuredId, level, fallbackId);
                }
            }
        }

        if (item == null) {
            woflo.petsplus.Petsplus.LOGGER.error("Unable to resolve tribute item '{}' for level {}. Tribute payments for this level are disabled until the config is fixed.", configuredId, level);
        }

        return item;
    }

    private static Item parseItemId(String idString) {
        if (idString == null || idString.isBlank()) {
            return null;
        }

        Identifier identifier = Identifier.tryParse(idString);
        if (identifier == null) {
            return null;
        }

        return Registries.ITEM.get(identifier);
    }

    /**
     * Check if item is a valid tribute for the given level
     */
    private static boolean isTributeItem(ItemStack stack, Item tributeItem) {
        return tributeItem != null && stack.isOf(tributeItem);
    }

    /**
     * Process tribute payment
     */
    private static boolean payTribute(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, TributeInfo tributeInfo, ItemStack stack) {
        try {
            // Consume exactly one item
            stack.decrement(1);

            // Unlock milestone
            petComp.unlockMilestone(tributeInfo.level);

            // Get pet name for messages
            String petName = pet.hasCustomName() ?
                pet.getCustomName().getString() :
                pet.getType().getName().getString();

            ItemStack tributeStack = new ItemStack(tributeInfo.item);
            String tributeName = tributeStack.getName().getString();

            // Success feedback
            player.sendMessage(Text.of("§6" + petName + " §eaccepts your §6" + tributeName + " §etribute!"), false);
            player.sendMessage(Text.of("§aLevel " + tributeInfo.level + " milestone unlocked!"), true);

            // Visual and audio feedback
            pet.getWorld().playSound(null, pet.getX(), pet.getY(), pet.getZ(),
                SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.8f, 1.4f);

            // Particle effects - golden particles for tribute
            ((ServerWorld) pet.getWorld()).spawnParticles(
                net.minecraft.particle.ParticleTypes.END_ROD,
                pet.getX(), pet.getY() + pet.getHeight() * 0.7, pet.getZ(),
                15, 0.4, 0.4, 0.4, 0.08
            );

            // Trigger milestone advancement
            AdvancementManager.triggerMilestoneUnlock(player, tributeInfo.level);

            if (tributeInfo.level == 30) {
                AdvancementManager.triggerAbilityMaxRank(player);
            }

            // Start stargaze window for hidden advancement (120s window)
            StargazeMechanic.startStargazeWindow(player);

            return true;

        } catch (Exception e) {
            woflo.petsplus.Petsplus.LOGGER.error("Error processing tribute payment", e);
            player.sendMessage(Text.of("A\u0015cTribute payment failed"), true);
            return false;
        }
    }

    /**
     * Check if a pet is waiting for tribute
     */
    public static boolean isPetWaitingForTribute(MobEntity pet) {
        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null) return false;

        return getTributeInfo(petComp) != null;
    }

    /**
     * Get required tribute item for a pet's current level
     */
    public static String getRequiredTribute(MobEntity pet) {
        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null) return null;

        TributeInfo info = getTributeInfo(petComp);
        if (info == null || info.item == null) {
            return null;
        }

        return new ItemStack(info.item).getName().getString();
    }

    private static class TributeInfo {
        final int level;
        final Item item;

        TributeInfo(int level, Item item) {
            this.level = level;
            this.item = item;
        }
    }
}
