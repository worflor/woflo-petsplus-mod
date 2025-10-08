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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.AdvancementStatKeys;
import woflo.petsplus.advancement.BestFriendTracker;
import woflo.petsplus.api.event.TributeCheckEvent;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.history.HistoryManager;
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
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) {
            return ActionResult.PASS;
        }

        PetRoleType roleType = petComp.getRoleType();
        TributeInfo tributeInfo = getTributeInfo(petComp);

        // Capture level at check time to prevent race conditions
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
            return null;
        }
        
        // First check if this level has a tribute milestone from level rewards
        if (petComp.hasTributeMilestone(level)) {
            if (petComp.isMilestoneUnlocked(level)) {
                return null; // Already paid
            }
            
            Identifier tributeItemId = petComp.getTributeMilestone(level);
            if (tributeItemId == null) {
                Petsplus.LOGGER.error("Tribute milestone at level {} has null item ID", level);
                // Fall through to legacy system
            } else {
                Item item = lookupItem(tributeItemId);
                if (item == null) {
                    Petsplus.LOGGER.error("Tribute item '{}' at level {} does not exist in registry! " +
                        "Check your role JSON file. Falling back to legacy tribute system.", 
                        tributeItemId, level);
                    // Fall through to legacy system
                } else {
                    return new TributeInfo(level, item);
                }
            }
        }

        // Fall back to legacy tribute system
        if (!roleType.xpCurve().tributeMilestones().contains(level)) {
            return null;
        }

        if (petComp.isMilestoneUnlocked(level)) {
            return null;
        }

        Item item = resolveTributeItem(roleType, level);
        return item == null ? null : new TributeInfo(level, item);
    }

    private static Item resolveTributeItem(PetRoleType roleType, int level) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier resolvedId = config.resolveTributeItem(roleType, level);
        if (resolvedId == null) {
            Petsplus.LOGGER.error("No tribute item defined for role {} level {} - check level_rewards in datapack", roleType.id(), level);
            return null;
        }

        Item item = lookupItem(resolvedId);
        if (item == null) {
            Petsplus.LOGGER.error("Unable to resolve tribute item {} for role {} level {}", resolvedId, roleType.id(), level);
            return null;
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

            int previousLevel = petComp.getLevel();

            // Unlock milestone
            petComp.unlockMilestone(milestoneLevel);

            // Get pet name for messages
            String petName = pet.hasCustomName() ?
                pet.getCustomName().getString() :
                pet.getType().getName().getString();

            ItemStack tributeStack = new ItemStack(tributeItem);
            String tributeName = tributeStack.getName().getString();

            // Success feedback
            player.sendMessage(Text.translatable("petsplus.tribute.accept", petName, tributeName), false);
            player.sendMessage(Text.translatable("petsplus.tribute.payment_success", milestoneLevel), true);

            // Enhanced visual and audio feedback with orbital completion effect
            woflo.petsplus.ui.TributeOrbitalEffects.emitTributeCompleteEffect(pet, (ServerWorld) pet.getEntityWorld(), milestoneLevel);

            // Legacy particle effects for compatibility
            ((ServerWorld) pet.getEntityWorld()).spawnParticles(
                net.minecraft.particle.ParticleTypes.END_ROD,
                pet.getX(), pet.getY() + pet.getHeight() * 0.7, pet.getZ(),
                15, 0.4, 0.4, 0.4, 0.08
            );

            // Trigger milestone advancement using the PET_LEVEL criterion
            // The advancement JSON files will check for specific milestone levels
            String roleIdStr = petComp.getRoleId() != null ? petComp.getRoleId().toString() : null;
            AdvancementCriteriaRegistry.PET_LEVEL.trigger(player, milestoneLevel, roleIdStr);

            // Trigger max-rank ability advancement when reaching level 30
            if (milestoneLevel == 30) {
                AdvancementCriteriaRegistry.PET_STAT_THRESHOLD.trigger(
                    player,
                    AdvancementStatKeys.ABILITY_MAX_RANK,
                    AdvancementStatKeys.ABILITY_MAX_RANK_UNLOCKED_VALUE
                );
            }

            if (previousLevel < 30 && petComp.getLevel() >= 30) {
                ServerWorld serverWorld = (ServerWorld) pet.getEntityWorld();
                BestFriendTracker tracker = BestFriendTracker.get(serverWorld);
                if (tracker.registerBestFriend(serverWorld, player.getUuid(), pet.getUuid())) {
                    HistoryManager.recordBestFriendForeverer(pet, player);
                }
            }

            // Start stargaze window for hidden advancement (120s window)
            StargazeMechanic.startStargazeWindow(player);

            return true;

        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing tribute payment", e);
            player.sendMessage(Text.translatable("petsplus.tribute.payment_failed"), true);
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



