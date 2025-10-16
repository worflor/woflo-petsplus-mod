package woflo.petsplus.ai.goals.play;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.tags.PetsplusItemTags;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Fetch item - retrieves dropped items and brings them to owner.
 * Creates helpful, playful behavior.
 */
public class FetchItemGoal extends AdaptiveGoal {
    private static final int MAX_FETCH_TICKS = 300; // 15 seconds
    private static final double BASE_FETCH_DISTANCE = 16.0;
    private static final double BASE_FETCH_DISTANCE_SQ = BASE_FETCH_DISTANCE * BASE_FETCH_DISTANCE;

    private ItemEntity targetItem;
    private PlayerEntity targetPlayer;
    private int fetchPhase = 0; // 0 = find, 1 = pickup, 2 = return
    private int fetchTicks = 0;
    private ItemStack carriedStack = ItemStack.EMPTY;

    public FetchItemGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FETCH_ITEM), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) {
            traceSkip("no_owner");
            return false;
        }

        if (!ctx.ownerNearby()) {
            traceSkip("owner_not_nearby");
            return false;
        }

        if (!isWithinFetchRange(owner)) {
            traceSkip("owner_out_of_range");
            return false;
        }

        targetItem = findNearbyItem(owner);
        targetPlayer = owner;

        if (targetItem == null) {
            traceSkip("no_item_in_range");
        }

        return targetItem != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (fetchTicks >= MAX_FETCH_TICKS || targetPlayer == null) {
            return false;
        }

        if (!isWithinFetchRange(targetPlayer)) {
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
        mob.setPitch(0.0f);
        fetchTicks = 0;
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

        if (!shouldContinueGoal()) {
            requestStop();
            return;
        }

        if (fetchPhase == 0) {
            // Phase 0: Move to item
            if (targetItem == null || !targetItem.isAlive()) {
                requestStop();
                return;
            }

            if (!orientTowards(targetItem, 34.0f, 28.0f, 20.0f)) {
                mob.getNavigation().stop();
                return;
            }

            mob.getNavigation().startMovingTo(targetItem, 1.2);

            if (mob.squaredDistanceTo(targetItem) < 2.0) {
                ItemStack stack = targetItem.getStack();
                if (!stack.isEmpty()) {
                    carriedStack = stack.copy();
                    if (!mob.getEntityWorld().isClient()) {
                        targetItem.discard();
                    }
                    targetItem = null;
                    fetchPhase = 1;
                } else {
                    // Nothing to carry, abort gracefully
                    fetchTicks = MAX_FETCH_TICKS;
                }
            }
        } else if (fetchPhase == 1) {
            // Phase 1: Return to owner
            if (!orientTowards(targetPlayer, 34.0f, 28.0f, 20.0f)) {
                mob.getNavigation().stop();
                return;
            }

            mob.getNavigation().startMovingTo(targetPlayer, 1.0);

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

                if (!mob.getEntityWorld().isClient()) {
                    boolean delivered = targetPlayer.giveItemStack(stackToDeliver);
                    if (!delivered || !stackToDeliver.isEmpty()) {
                        targetPlayer.dropItem(stackToDeliver, false);
                    }
                }
            }

            // Simulate drop animation
            if (fetchTicks % 10 == 0) {
                mob.setPitch(30); // Look down
            }

            requestStop();
            return;
        }

        // Tail wag during return
        if (fetchPhase == 1 && fetchTicks % 5 == 0) {
            mob.bodyYaw += mob.getRandom().nextFloat() * 10 - 5;
        }
    }

    /**
     * Finds a nearby item to fetch.
     */
    private ItemEntity findNearbyItem(PlayerEntity owner) {
        List<ItemEntity> items = mob.getEntityWorld().getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(getFetchDistance()),
            item -> item.isAlive()
                && !item.getStack().isEmpty()
                && !item.getStack().isIn(PetsplusItemTags.FETCH_BLACKLIST)
                && item.squaredDistanceTo(mob) <= getFetchDistanceSq()
                && item.squaredDistanceTo(owner) <= getFetchDistanceSq()
        );

        if (items.isEmpty()) {
            return null;
        }

        // Prefer closest item
        return items.stream()
            .min(Comparator.comparingDouble(item -> item.squaredDistanceTo(mob)))
            .orElse(null);
    }

    private boolean isWithinFetchRange(PlayerEntity owner) {
        if (owner == null || owner.isRemoved()) {
            return false;
        }
        double distanceSq = mob.squaredDistanceTo(owner);
        if (distanceSq > getFetchDistanceSq()) {
            return false;
        }
        return true;
    }

    private double getFetchDistance() {
        return BASE_FETCH_DISTANCE;
    }

    private double getFetchDistanceSq() {
        return BASE_FETCH_DISTANCE_SQ;
    }

    private void traceSkip(String reason) {
        Petsplus.LOGGER.debug("[FetchItemGoal] Skipping fetch for {} because {}", mob.getDisplayName().getString(), reason);
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
