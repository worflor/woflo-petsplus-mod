package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
// Species tags: multi-tag gating
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;

/**
 * Subtle behavior: P1
 * ShowAndDropGoal - path to a small nearby item, "pick" (fake if needed), approach owner, and drop/show it.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): curiousScavenger
 *
 * Preconditions:
 * - Small item entity (stick/seed/feather class via simple predicate) within 6 blocks;
 *   owner within 10 blocks; mouth free preferred (otherwise fake-pick).
 *
 * Behavior:
 * - Path to item, (fake-)pick, walk near owner, drop/show; 6–9s
 *
 * Cooldown:
 * - 180–360s via "show_and_drop"; Variety anti-spam "social_micro" 20–35s
 *
 * Notes:
 * - If actual pickup conflicts exist, perform a virtualized show/drop without modifying inventories.
 */
public class ShowAndDropGoal extends AdaptiveGoal {
    private static final double ITEM_RANGE = 6.0;
    private static final double OWNER_RANGE = 10.0;
    private static final int MIN_TICKS = 120; // 6s
    private static final int MAX_TICKS = 180; // 9s

    private enum Phase { TO_ITEM, TO_OWNER, DROP }
    private Phase phase = Phase.TO_ITEM;

    private int ticks;
    private int duration;

    private ItemEntity targetItem;
    private Vec3d ownerPosSnapshot;
    private boolean fakeCarry;
    private Item carryVisual;

    public ShowAndDropGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SHOW_AND_DROP), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.curiousScavenger()) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc == null) return false;

        // Variety anti-spam (social)
        if (pc.isOnCooldown("social_micro") || pc.isOnCooldown("show_and_drop")) {
            return false;
        }

        // Owner proximity requirement
        var owner = pc.getCachedOwnerEntity();
        if (owner == null || owner.squaredDistanceTo(mob) > (OWNER_RANGE * OWNER_RANGE)) {
            return false;
        }

        // Abort on owner urgency/threat
        OwnerCombatState ocs = OwnerCombatState.get(owner);
        if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
            return false;
        }

        // Momentum gating
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        // Find a nearby small item
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }
        List<ItemEntity> items = sw.getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(ITEM_RANGE),
            it -> it != null && it.isAlive() && !it.getStack().isEmpty() && isSmallShowItem(it.getStack().getItem())
        );

        if (items.isEmpty()) return false;

        // Choose nearest
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity it : items) {
            double d = mob.squaredDistanceTo(it);
            if (d < best) { best = d; nearest = it; }
        }
        if (nearest == null) return false;

        targetItem = nearest;
        ownerPosSnapshot = new Vec3d(owner.getX(), owner.getY(), owner.getZ());

        // Determine if we can actually carry (hand free)
        ItemStack mainHand = mob.getMainHandStack();
        fakeCarry = mainHand != null && !mainHand.isEmpty();
        carryVisual = targetItem.getStack().getItem();

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (targetItem != null && targetItem.isRemoved()) {
            // If the item despawns mid-run, we can continue with a fake-carry if we already started
            if (phase == Phase.TO_ITEM) return false;
        }
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);
        phase = Phase.TO_ITEM;

        if (targetItem != null) {
            if (!orientTowards(targetItem, 32.0f, 26.0f, 18.0f)) {
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().startMovingTo(targetItem.getX(), targetItem.getY(), targetItem.getZ(), 0.8);
            }
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int mainCd = secondsToTicks(180) + mob.getRandom().nextInt(secondsToTicks(181)); // 180–360s
            pc.setCooldown("show_and_drop", mainCd);
            pc.setCooldown("social_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16))); // 20–35s
        }
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetItem = null;
        carryVisual = null;
        ownerPosSnapshot = null;
        phase = Phase.TO_ITEM;
        fakeCarry = false;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        switch (phase) {
            case TO_ITEM -> tickToItem();
            case TO_OWNER -> tickToOwner();
            case DROP -> tickDrop();
        }

        // subtle head motion
        if (ticks % 12 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 6.0f);
        }
    }

    private void tickToItem() {
        if (targetItem == null) {
            // Lost target: fallback to owner to finish gracefully
            phase = Phase.TO_OWNER;
            return;
        }

        // Approach item
        if (mob.squaredDistanceTo(targetItem) > 1.2) {
            if (!orientTowards(targetItem, 32.0f, 26.0f, 18.0f)) {
                mob.getNavigation().stop();
            } else if (mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(targetItem.getX(), targetItem.getY(), targetItem.getZ(), 0.8);
            }
            return;
        }

        // At item: "pick" (virtual if needed)
        if (!(mob.getEntityWorld() instanceof ServerWorld)) {
            phase = Phase.TO_OWNER;
            return;
        }

        // If hand is empty and stack is tiny, we could try to pick legitimately;
        // to avoid inventory complexity, we treat as virtual carry (no item removal).
        // Tiny head dip to signal pick
        mob.setPitch(MathHelper.clamp(mob.getPitch() + 10.0f, -30.0f, 30.0f));

        phase = Phase.TO_OWNER;
    }

    private void tickToOwner() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            phase = Phase.DROP;
            return;
        }
        var owner = pc.getCachedOwnerEntity();
        if (owner != null) ownerPosSnapshot = new Vec3d(owner.getX(), owner.getY(), owner.getZ());

        if (ownerPosSnapshot != null) {
            if (mob.squaredDistanceTo(ownerPosSnapshot) > 4.0) {
                if (!orientTowards(ownerPosSnapshot.x, ownerPosSnapshot.y + 0.5, ownerPosSnapshot.z, 32.0f, 26.0f, 18.0f)) {
                    mob.getNavigation().stop();
                } else if (mob.getNavigation().isIdle()) {
                    mob.getNavigation().startMovingTo(ownerPosSnapshot.x, ownerPosSnapshot.y, ownerPosSnapshot.z, 0.8);
                }
                return;
            }
        }

        // Near owner: proceed to drop/show
        phase = Phase.DROP;
    }

    private void tickDrop() {
        // Small bob as "drop"
        float phase = (ticks % 20) / 20.0f;
        float bob = (float) Math.sin(phase * MathHelper.TAU) * 10.0f;
        mob.setPitch(MathHelper.clamp(bob, -15.0f, 15.0f));

        // No-op for actual item changes; this is purely presentational per spec
        // Finish shortly after
        if (ticks > duration - 10) {
            // gentle stop
            if (mob.getNavigation().isIdle()) {
                // stay put
            } else {
                mob.getNavigation().stop();
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.62f;
        if (targetItem != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetItem));
            e += MathHelper.clamp((float) ((ITEM_RANGE - d) / (ITEM_RANGE * 2.0)), 0.0f, 0.08f);
        }
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Curious and social
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.10f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.06f)
            .build();
    }

    private static boolean isSmallShowItem(Item item) {
        return item == Items.STICK
            || item == Items.FEATHER
            || item == Items.WHEAT_SEEDS
            || item == Items.BEETROOT_SEEDS
            || item == Items.PUMPKIN_SEEDS
            || item == Items.MELON_SEEDS;
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}