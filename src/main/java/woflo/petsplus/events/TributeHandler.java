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
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.api.event.TributeCheckEvent;
import woflo.petsplus.api.registry.PetRoleType;
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
        if (!player.isSneaking()) {
            Petsplus.LOGGER.info("Player {} not sneaking when interacting with pet", player.getName().getString());
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) {
            Petsplus.LOGGER.info("Player {} has empty hand when interacting with pet", player.getName().getString());
            return ActionResult.PASS;
        }

        Petsplus.LOGGER.info("Player {} interacting with pet level {} with item {}",
            player.getName().getString(), petComp.getLevel(), stack.getItem().toString());

        PetRoleType roleType = petComp.getRoleType();
        TributeInfo tributeInfo = getTributeInfo(petComp);

        Item requiredItem = tributeInfo != null ? tributeInfo.item : null;
        int milestoneLevel = tributeInfo != null ? tributeInfo.level : petComp.getLevel();

        TributeCheckEvent.Context eventContext = new TributeCheckEvent.Context(
            serverPlayer,
            mob,
            petComp,
            roleType,
            stack,
            milestoneLevel,
            requiredItem
        );
        TributeCheckEvent.fire(eventContext);

        if (eventContext.isCancelled()) {
            return ActionResult.PASS;
        }

        if (eventContext.isHandled()) {
            return eventContext.getResult();
        }

        Item finalItem = eventContext.getRequiredItem();
        if (finalItem == null || !isTributeItem(stack, finalItem)) {
            return ActionResult.PASS;
        }

        boolean success = payTribute(
            serverPlayer,
            mob,
            petComp,
            eventContext.getMilestoneLevel(),
            finalItem,
            stack,
            eventContext.shouldConsumeItem()
        );
        return success ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    /**
     * Get tribute information for a pet if it needs tribute
     */
    private static TributeInfo getTributeInfo(PetComponent petComp) {
        int level = petComp.getLevel();
        PetRoleType roleType = petComp.getRoleType();
        if (roleType == null) {
            Petsplus.LOGGER.info("Pet has no role type");
            return null;
        }

        Petsplus.LOGGER.info("Pet level {}, tribute milestones: {}", level, roleType.xpCurve().tributeMilestones());

        if (!roleType.xpCurve().tributeMilestones().contains(level)) {
            Petsplus.LOGGER.info("Pet level {} is not a tribute milestone", level);
            return null;
        }

        if (petComp.isMilestoneUnlocked(level)) {
            Petsplus.LOGGER.info("Pet level {} milestone already unlocked", level);
            return null;
        }

        Item item = resolveTributeItem(roleType, level);
        Petsplus.LOGGER.info("Required tribute item for level {}: {}", level, item);
        return item == null ? null : new TributeInfo(level, item);
    }

    private static Item resolveTributeItem(PetRoleType roleType, int level) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier datapackDefault = roleType.tributeDefaults().itemForLevel(level);
        Identifier roleOverride = config.getRoleTributeOverride(roleType.id(), level);
        Identifier resolvedId = config.resolveTributeItem(roleType, level);
        if (resolvedId == null) {
            return null;
        }

        Item item = lookupItem(resolvedId);
        if (item == null) {
            Petsplus.LOGGER.error("Unable to resolve tribute item {} for role {} level {}", resolvedId, roleType.id(), level);
            return null;
        }

        if (roleOverride != null && !resolvedId.equals(datapackDefault)) {
            Petsplus.LOGGER.warn("Role {} tribute level {} overridden via config to {}", roleType.id(), level, resolvedId);
        } else if (datapackDefault == null) {
            Identifier fallback = config.getFallbackTributeItem(level);
            if (fallback != null && fallback.equals(resolvedId)) {
                Petsplus.LOGGER.warn("Role {} missing tribute metadata for level {}; using fallback item {}", roleType.id(), level, resolvedId);
            }
        }

        return item;
    }

    private static Item lookupItem(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        if (!Registries.ITEM.containsId(id)) {
            return null;
        }
        return Registries.ITEM.get(id);
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
    private static boolean payTribute(ServerPlayerEntity player, MobEntity pet, PetComponent petComp, int milestoneLevel, Item tributeItem, ItemStack stack, boolean consumeItem) {
        try {
            // Consume exactly one item
            if (consumeItem) {
                stack.decrement(1);
            }

            // Unlock milestone
            petComp.unlockMilestone(milestoneLevel);

            // Get pet name for messages
            String petName = pet.hasCustomName() ?
                pet.getCustomName().getString() :
                pet.getType().getName().getString();

            ItemStack tributeStack = new ItemStack(tributeItem);
            String tributeName = tributeStack.getName().getString();

            // Success feedback
            player.sendMessage(Text.of("§6" + petName + " §eaccepts your §6" + tributeName + " §etribute!"), false);
            player.sendMessage(Text.of("§aLevel " + milestoneLevel + " milestone unlocked!"), true);

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
            AdvancementManager.triggerMilestoneUnlock(player, milestoneLevel);

            if (milestoneLevel == 30) {
                AdvancementManager.triggerAbilityMaxRank(player);
            }

            // Start stargaze window for hidden advancement (120s window)
            StargazeMechanic.startStargazeWindow(player);

            return true;

        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing tribute payment", e);
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
