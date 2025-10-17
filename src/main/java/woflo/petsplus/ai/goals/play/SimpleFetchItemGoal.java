package woflo.petsplus.ai.goals.play;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;

/**
 * Simple, robust fetch goal that picks up nearby items and brings them to the owner.
 * No complex behaviors - just find item, pick it up, return to owner, drop it.
 */
public class SimpleFetchItemGoal extends AdaptiveGoal {
    private static final int MAX_FETCH_TIME_TICKS = 300; // 15 seconds max
    private static final double MAX_ITEM_DISTANCE = 12.0; // Max distance to look for items
    private static final double MAX_OWNER_DISTANCE = 20.0; // Max distance from owner to start fetching
    
    // Fetch phases
    private enum FetchPhase {
        SEEKING_ITEM,  // Looking for an item to fetch
        COLLECTING,    // Moving to and picking up the item
        RETURNING,     // Returning to owner with the item
        DELIVERING     // Dropping the item at owner's feet
    }
    
    private FetchPhase currentPhase;
    private ItemEntity targetItem;
    private PlayerEntity owner;
    private int fetchTimer;
    private ItemStack carriedItem;

    public SimpleFetchItemGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FETCH_ITEM), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            return false;
        }
        
        owner = pc.getOwner();
        if (owner == null) {
            return false;
        }
        
        // Check if owner is too far away
        if (mob.squaredDistanceTo(owner) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
            return false;
        }
        
        // Find a nearby item to fetch
        targetItem = findNearestItem();
        if (targetItem == null) {
            return false;
        }
        
        currentPhase = FetchPhase.SEEKING_ITEM;
        fetchTimer = 0;
        carriedItem = ItemStack.EMPTY;
        
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Stop if we've been fetching too long
        if (fetchTimer >= MAX_FETCH_TIME_TICKS) {
            return false;
        }
        
        // Stop if owner is too far away
        if (owner == null || mob.squaredDistanceTo(owner) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
            return false;
        }
        
        return switch (currentPhase) {
            case SEEKING_ITEM -> targetItem != null && targetItem.isAlive();
            case COLLECTING -> targetItem != null && targetItem.isAlive() && carriedItem.isEmpty();
            case RETURNING -> !carriedItem.isEmpty() && owner != null;
            case DELIVERING -> !carriedItem.isEmpty() && owner != null;
        };
    }

    @Override
    protected void onStartGoal() {
        Petsplus.LOGGER.debug("[SimpleFetchItemGoal] Pet {} starting fetch for item at {}", 
            mob.getDisplayName().getString(), targetItem.getPos());
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        
        // Drop carried item if we have one
        if (!carriedItem.isEmpty()) {
            dropCarriedItem();
        }
        
        targetItem = null;
        owner = null;
        currentPhase = null;
        fetchTimer = 0;
        carriedItem = ItemStack.EMPTY;
    }

    @Override
    protected void onTickGoal() {
        fetchTimer++;
        
        switch (currentPhase) {
            case SEEKING_ITEM -> handleSeekingPhase();
            case COLLECTING -> handleCollectingPhase();
            case RETURNING -> handleReturningPhase();
            case DELIVERING -> handleDeliveringPhase();
        }
    }
    
    private void handleSeekingPhase() {
        // This phase is mostly handled in canStartGoal, but we verify the item is still valid
        if (targetItem == null || !targetItem.isAlive()) {
            targetItem = findNearestItem();
            if (targetItem == null) {
                requestStop();
                return;
            }
        }
        
        // Move to the item
        mob.getNavigation().startMovingTo(targetItem, 1.2);
        mob.getLookControl().lookAt(targetItem);
        
        // Check if we're close enough to collect
        if (mob.squaredDistanceTo(targetItem) < 2.0) {
            currentPhase = FetchPhase.COLLECTING;
        }
    }
    
    private void handleCollectingPhase() {
        if (targetItem == null || !targetItem.isAlive()) {
            requestStop();
            return;
        }
        
        // Look at the item
        mob.getLookControl().lookAt(targetItem);
        
        // Check if we're close enough to pick up
        if (mob.squaredDistanceTo(targetItem) < 1.5) {
            // Pick up the item
            carriedItem = targetItem.getStack().copy();
            if (!carriedItem.isEmpty()) {
                // Remove the item from the world
                targetItem.discard();
                targetItem = null;
                currentPhase = FetchPhase.RETURNING;
                Petsplus.LOGGER.debug("[SimpleFetchItemGoal] Pet {} picked up item: {}", 
                    mob.getDisplayName().getString(), carriedItem.getItem().getName().getString());
            }
        }
    }
    
    private void handleReturningPhase() {
        if (owner == null) {
            requestStop();
            return;
        }
        
        // Move to the owner
        mob.getNavigation().startMovingTo(owner, 1.0);
        mob.getLookControl().lookAt(owner);
        
        // Check if we're close enough to deliver
        if (mob.squaredDistanceTo(owner) < 3.0) {
            currentPhase = FetchPhase.DELIVERING;
        }
    }
    
    private void handleDeliveringPhase() {
        if (owner == null) {
            requestStop();
            return;
        }
        
        // Stop moving and look at owner
        mob.getNavigation().stop();
        mob.getLookControl().lookAt(owner);
        
        // Drop the item after a brief pause
        if (fetchTimer % 20 == 0) { // Every second
            dropCarriedItem();
            Petsplus.LOGGER.debug("[SimpleFetchItemGoal] Pet {} delivered item to {}", 
                mob.getDisplayName().getString(), owner.getDisplayName().getString());
            requestStop();
        }
    }
    
    private void dropCarriedItem() {
        if (!carriedItem.isEmpty()) {
            // Create an item entity at the pet's position
            ItemEntity droppedItem = new ItemEntity(
                mob.getWorld(),
                mob.getX(),
                mob.getY(),
                mob.getZ(),
                carriedItem.copy()
            );
            
            // Set the pickup delay to prevent immediate pickup
            droppedItem.setPickupDelay(20);
            
            // Spawn the item in the world
            mob.getWorld().spawnEntity(droppedItem);
            
            // Clear the carried item
            carriedItem = ItemStack.EMPTY;
        }
    }
    
    private ItemEntity findNearestItem() {
        // Search for items in a radius around the pet
        List<ItemEntity> nearbyItems = mob.getWorld().getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(MAX_ITEM_DISTANCE),
            item -> item.isAlive() && !item.getStack().isEmpty()
        );
        
        if (nearbyItems.isEmpty()) {
            return null;
        }
        
        // Find the closest item
        ItemEntity closestItem = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (ItemEntity item : nearbyItems) {
            double distance = mob.squaredDistanceTo(item);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestItem = item;
            }
        }
        
        return closestItem;
    }

    @Override
    protected float calculateEngagement() {
        // Base engagement for fetch
        float engagement = 0.6f;
        
        // Higher engagement when closer to completing the fetch
        if (currentPhase == FetchPhase.DELIVERING) {
            engagement = 0.9f;
        } else if (currentPhase == FetchPhase.RETURNING) {
            engagement = 0.8f;
        } else if (currentPhase == FetchPhase.COLLECTING) {
            engagement = 0.7f;
        }
        
        // Increase engagement based on bond with owner
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            float bondStrength = MathHelper.clamp(pc.getBondStrength() / 10000.0f, 0.0f, 1.0f);
            engagement += bondStrength * 0.2f;
        }
        
        return Math.min(engagement, 1.0f);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.PLAYFULNESS, 0.15f)  // Fetch is a playful activity
            .add(PetComponent.Emotion.LOYALTY, 0.10f)       // Bringing item to owner shows loyalty
            .add(PetComponent.Emotion.CHEERFUL, 0.08f)       // Happy to help
            .withContagion(PetComponent.Emotion.PLAYFULNESS, 0.015f)  // Spread playfulness
            .build();
    }
}