package woflo.petsplus.ai.goals.play;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Fetch item - retrieves dropped items and brings them to owner.
 * Creates helpful, playful behavior.
 */
public class FetchItemGoal extends AdaptiveGoal {
    private ItemEntity targetItem;
    private PlayerEntity targetPlayer;
    private int fetchPhase = 0; // 0 = find, 1 = pickup, 2 = return
    private static final int MAX_FETCH_TICKS = 300; // 15 seconds
    private int fetchTicks = 0;
    private ItemStack carriedStack = ItemStack.EMPTY;
    
    public FetchItemGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FETCH_ITEM), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null) return false;
        
        targetItem = findNearbyItem();
        targetPlayer = ctx.owner();
        
        return targetItem != null && targetPlayer != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        if (fetchTicks >= MAX_FETCH_TICKS || targetPlayer == null) {
            return false;
        }

        return switch (fetchPhase) {
            case 0 -> targetItem != null && targetItem.isAlive();
            case 1, 2 -> !carriedStack.isEmpty();
            default -> false;
        };
    }
    
    @Override
    protected void onStartGoal() {
        fetchPhase = 0;
        fetchTicks = 0;
        carriedStack = ItemStack.EMPTY;
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        if (!carriedStack.isEmpty()) {
            if (!mob.getEntityWorld().isClient()) {
                mob.getEntityWorld().spawnEntity(new ItemEntity(
                    mob.getEntityWorld(),
                    mob.getX(),
                    mob.getY(),
                    mob.getZ(),
                    carriedStack.copy()
                ));
            }
            carriedStack = ItemStack.EMPTY;
        }
        targetItem = null;
        targetPlayer = null;
        fetchPhase = 0;
    }
    
    @Override
    protected void onTickGoal() {
        fetchTicks++;
        
        if (fetchPhase == 0) {
            // Phase 0: Move to item
            if (targetItem == null || !targetItem.isAlive()) {
                return;
            }

            mob.getNavigation().startMovingTo(targetItem, 1.2);
            mob.getLookControl().lookAt(targetItem);

            if (mob.squaredDistanceTo(targetItem) < 2.0) {
                ItemStack stack = targetItem.getStack();
                if (!stack.isEmpty()) {
                    carriedStack = stack.copy();
                    targetItem.discard();
                    targetItem = null;
                    fetchPhase = 1;
                } else {
                    // Nothing to carry, abort gracefully
                    fetchTicks = MAX_FETCH_TICKS;
                }
            }
        } else if (fetchPhase == 1) {
            // Phase 1: Return to owner
            mob.getNavigation().startMovingTo(targetPlayer, 1.0);
            mob.getLookControl().lookAt(targetPlayer);

            if (mob.squaredDistanceTo(targetPlayer) < 3.0) {
                fetchPhase = 2;
            }
        } else if (fetchPhase == 2) {
            // Phase 2: "Drop" at owner's feet
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(targetPlayer);

            if (!carriedStack.isEmpty()) {
                ItemStack stackToDeliver = carriedStack.copy();
                carriedStack = ItemStack.EMPTY;

                boolean delivered = targetPlayer.giveItemStack(stackToDeliver);
                if (!delivered || !stackToDeliver.isEmpty()) {
                    targetPlayer.dropItem(stackToDeliver, false);
                }
            }

            // Simulate drop animation
            if (fetchTicks % 10 == 0) {
                mob.setPitch(30); // Look down
            }
        }
        
        // Tail wag during return
        if (fetchPhase == 1 && fetchTicks % 5 == 0) {
            mob.bodyYaw += mob.getRandom().nextFloat() * 10 - 5;
        }
    }
    
    /**
     * Finds a nearby item to fetch.
     */
    private ItemEntity findNearbyItem() {
        List<ItemEntity> items = mob.getEntityWorld().getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(12.0),
            item -> item.isAlive() && !item.getStack().isEmpty()
        );
        
        if (items.isEmpty()) return null;
        
        // Prefer closest item
        return items.stream()
            .min(Comparator.comparingDouble(item -> item.squaredDistanceTo(mob)))
            .orElse(null);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Fetch is a complex achievement behavior combining:
        // - Service/loyalty (bringing gift to owner)
        // - Pride (successful completion of multi-phase task)
        // - Playfulness (game-like structure)
        // - Energy/enthusiasm (KEFI during chase)
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.22f)      // Serving beloved owner
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.18f)        // Task accomplishment
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.15f)         // Energized joy of chase
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.12f)  // Game-like fun
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.020f)  // Spread enthusiasm
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.85f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.65f, 1.16f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.35f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.72f, 1.12f);
        engagement *= momentumScale;

        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
            engagement = 1.0f;
        }
        
        // Higher engagement when bonded
        engagement += ctx.bondStrength() * 0.1f;
        
        // Phase 2 (delivery) is most engaging
        if (fetchPhase == 2) {
            engagement = 1.0f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}



