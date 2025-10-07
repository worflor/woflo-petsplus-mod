package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

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
    private int giftTicks = 0;
    private boolean giftTracked = false;
    
    public GiftBringingGoal(MobEntity mob) {
        super(mob, GoalType.GIFT_BRINGING, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null) return false;
        
        // Only bring gifts when bonded enough
        if (ctx.bondStrength() < 0.3f) return false;
        
        targetItem = findInterestingItem();
        targetPlayer = ctx.owner();
        
        return targetItem != null && targetPlayer != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return giftTicks < MAX_GIFT_TICKS && 
               targetItem != null && 
               targetItem.isAlive() &&
               targetPlayer != null;
    }
    
    @Override
    protected void onStartGoal() {
        giftPhase = 0;
        giftTicks = 0;
        giftTracked = false;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        targetItem = null;
        targetPlayer = null;
        giftPhase = 0;
    }
    
    @Override
    protected void onTickGoal() {
        giftTicks++;
        
        if (giftPhase == 0) {
            // Phase 0: Move to item
            mob.getNavigation().startMovingTo(targetItem, 1.0);
            mob.getLookControl().lookAt(targetItem);
            
            if (mob.squaredDistanceTo(targetItem) < 2.0) {
                targetItem.discard(); // "Pickup"
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
            
            // Gentle tail wag
            if (giftTicks % 10 == 0) {
                mob.bodyYaw += 3;
            }
        }
    }
    
    /**
     * Finds an interesting item to gift.
     */
    private ItemEntity findInterestingItem() {
        return mob.getWorld().getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(15.0),
            item -> item.isAlive() && isInterestingGift(item.getStack())
        ).stream()
        .findFirst()
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
        float engagement = 0.8f;
        
        // Very engaging when bonded
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement = 0.95f;
        }
        
        // Scales with bond strength
        engagement += ctx.bondStrength() * 0.15f;
        
        // Delivery phase is peak engagement
        if (giftPhase == 2) {
            engagement = 1.0f;
        }
        
        return Math.min(1.0f, engagement);
    }
}
