package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Gift bringing - finds and brings interesting items to owner.
 * Creates generous, thoughtful behavior.
 */
public class GiftBringingGoal extends AdaptiveGoal {
    private ItemEntity targetItem;
    private PlayerEntity targetPlayer;
    private int giftPhase = 0; // 0 = search, 1 = pickup, 2 = deliver
    private static final int MAX_GIFT_TICKS = 400; // 20 seconds
    private static final double MAX_OWNER_DISTANCE = 14.0;
    private static final double MAX_OWNER_DISTANCE_SQ = MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE;
    private int giftTicks = 0;
    private boolean giftTracked = false;
    private ItemStack carriedStack = ItemStack.EMPTY;
    private float neutralPitch = 0.0f;
    
    public GiftBringingGoal(MobEntity mob) {
        // GoalIds.GIFT_BRINGING removed; map to a nearby social goal id to retain runtime wiring
        super(mob, GoalRegistry.require(GoalIds.SHOW_AND_DROP), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null) return false;

        if (!ctx.ownerNearby()) {
            return false;
        }

        // Only bring gifts when bonded enough
        if (ctx.bondStrength() < 0.3f) return false;

        targetPlayer = ctx.owner();

        if (!isOwnerWithinRange(targetPlayer)) {
            return false;
        }

        targetItem = findInterestingItem(targetPlayer);

        return targetItem != null && targetPlayer != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        if (giftTicks >= MAX_GIFT_TICKS || targetPlayer == null || targetPlayer.isRemoved()) {
            return false;
        }

        if (!isOwnerWithinRange(targetPlayer)) {
            return false;
        }

        if (giftPhase == 0) {
            return targetItem != null && targetItem.isAlive();
        }

        return !carriedStack.isEmpty();
    }
    
    @Override
    protected void onStartGoal() {
        giftPhase = 0;
        giftTicks = 0;
        giftTracked = false;
        carriedStack = ItemStack.EMPTY;
        neutralPitch = mob.getPitch();
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(neutralPitch);
        targetItem = null;
        targetPlayer = null;
        giftPhase = 0;
        carriedStack = ItemStack.EMPTY;
        giftTracked = false;
    }

    @Override
    protected void onTickGoal() {
        giftTicks++;

        if (!shouldContinueGoal()) {
            requestStop();
            return;
        }

        if (giftPhase == 0) {
            // Phase 0: Move to item
            if (targetItem == null || !targetItem.isAlive()) {
                requestStop();
                return;
            }
            mob.getNavigation().startMovingTo(targetItem, 1.0);
            mob.getLookControl().lookAt(targetItem);

            if (mob.squaredDistanceTo(targetItem) < 2.0) {
                carriedStack = targetItem.getStack().copy();
                if (!mob.getEntityWorld().isClient()) {
                    targetItem.discard(); // "Pickup"
                }
                targetItem = null;
                giftPhase = 1;
            }
        } else if (giftPhase == 1) {
            // Phase 1: Return to owner
            mob.getNavigation().startMovingTo(targetPlayer, 0.9);
            mob.getLookControl().lookAt(targetPlayer);

            // Proud carrying posture
            mob.setPitch(-10);

            if (mob.squaredDistanceTo(targetPlayer) < 4.0) {
                giftPhase = 2;
                // Track gift giving for relationship system (once per gift)
                // Using GIFT interaction (pet bringing gift is bonding moment)
                if (!giftTracked) {
                    woflo.petsplus.events.RelationshipEventHandler.onGiftGiven(mob, targetPlayer, 1.0f);
                    giftTracked = true;
                }
            }
        } else if (giftPhase == 2) {
            // Phase 2: Present gift
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(targetPlayer);

            // Sit/stop and look at owner expectantly
            mob.setPitch(15); // Look up at owner

            if (!carriedStack.isEmpty() && targetPlayer != null) {
                ItemStack stackToGive = carriedStack.copy();
                carriedStack = ItemStack.EMPTY;

                if (!mob.getEntityWorld().isClient()) {
                    if (!targetPlayer.giveItemStack(stackToGive)) {
                        ItemEntity dropped = targetPlayer.dropItem(stackToGive, false);
                        if (dropped != null) {
                            dropped.setPickupDelay(0);
                            dropped.setOwner(targetPlayer.getUuid());
                        }
                    }
                }
            }

            // Gentle tail wag
            if (giftTicks % 10 == 0) {
                mob.bodyYaw += 3;
            }

            requestStop();
            return;
        }
    }
    
    /**
     * Finds an interesting item to gift.
     */
    private ItemEntity findInterestingItem(PlayerEntity owner) {
        return mob.getEntityWorld().getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(MAX_OWNER_DISTANCE),
            item -> item.isAlive()
                && isInterestingGift(item.getStack())
                && item.squaredDistanceTo(owner) <= MAX_OWNER_DISTANCE_SQ
                && item.squaredDistanceTo(mob) <= MAX_OWNER_DISTANCE_SQ
        ).stream()
        .min(Comparator.comparingDouble(item -> item.squaredDistanceTo(owner)))
        .orElse(null);
    }
    
    /**
     * Determines if an item is gift-worthy.
     */
    private boolean isInterestingGift(ItemStack stack) {
        // Prefer flowers, food, shiny items
        return stack.isOf(Items.POPPY) ||
               stack.isOf(Items.DANDELION) ||
               stack.isOf(Items.BLUE_ORCHID) ||
               stack.isOf(Items.ALLIUM) ||
               stack.isOf(Items.AZURE_BLUET) ||
               stack.isOf(Items.RED_TULIP) ||
               stack.isOf(Items.ORANGE_TULIP) ||
               stack.isOf(Items.WHITE_TULIP) ||
               stack.isOf(Items.PINK_TULIP) ||
               stack.isOf(Items.OXEYE_DAISY) ||
               stack.isOf(Items.CORNFLOWER) ||
               stack.isOf(Items.LILY_OF_THE_VALLEY) ||
               stack.isOf(Items.GOLD_NUGGET) ||
               stack.isOf(Items.EMERALD) ||
               stack.isOf(Items.DIAMOND) ||
               stack.isOf(Items.FEATHER) ||
               stack.isOf(Items.RABBIT_FOOT) ||
               stack.isOf(Items.BONE);
    }

    private boolean isOwnerWithinRange(PlayerEntity owner) {
        return owner != null && !owner.isRemoved() && mob.squaredDistanceTo(owner) <= MAX_OWNER_DISTANCE_SQ;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.28f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.020f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float mentalFocus = MathHelper.clamp(ctx.mentalFocus(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.48f, 0.9f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.78f, 1.1f);
        engagement *= staminaScale;

        float focusBlend = MathHelper.clamp((mentalFocus - 0.45f) / 0.35f, -1.0f, 1.0f);
        float focusScale = MathHelper.lerp((focusBlend + 1.0f) * 0.5f, 0.8f, 1.08f);
        engagement *= focusScale;

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement = Math.max(engagement, 0.95f);
        }

        engagement += ctx.bondStrength() * 0.15f;

        if (giftPhase == 2) {
            engagement = Math.max(engagement, 1.0f);
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

