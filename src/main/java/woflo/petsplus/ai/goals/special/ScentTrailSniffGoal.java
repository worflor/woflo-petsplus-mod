package woflo.petsplus.ai.goals.special;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
// ... existing code ...
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;

/**
 * ScentTrailSniffGoal - subtle world interaction.
 * Subtle behavior: P0
 * Species tags: multi-tag gating
 *
 * Gating:
 * - Requires scentDriven species profile
 *
 * Heuristic for "owner recent interaction":
 * - Uses PetComponent cached owner stimulus tick and proximity checks as a lightweight proxy
 *   for recent owner block/item interaction (≤ 20s) within ~8 blocks.
 *
 * Behavior:
 * - Move to heuristic target location (owner vicinity or nearest dropped item in range),
 *   then perform brief "sniff/head bob" for 5–8s. No inventory interaction.
 *
 * Cooldown:
 * - 45–90s using PetComponent cooldown key "scent_trail_sniff"
 */
public class ScentTrailSniffGoal extends AdaptiveGoal {
    private static final int MIN_SNIFF_TICKS = 100; // 5s
    private static final int MAX_SNIFF_TICKS = 160; // 8s
    private static final int OWNER_EVENT_WINDOW_TICKS = 20 * 20; // 20s
    private static final double SCAN_RANGE = 8.0;

    private int sniffTicks;
    private int sniffDuration;
    private Vec3d targetPos;

    public ScentTrailSniffGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SCENT_TRAIL_SNIFF), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (cached profile, O(1))
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.scentDriven()) {
            return false;
        }

        // Momentum gating is handled by base AdaptiveGoal (energy compatibility)

        // Heuristic for "owner recent interaction ≤ 20s within 8 blocks"
        PetComponent pc = PetComponent.get(mob);
        long now = mob.getEntityWorld().getTime();
        BlockPos candidate = null;

        if (pc != null) {
            long lastOwnerStimulus = pc.getLastOwnerStimulusTick();
            var owner = pc.getCachedOwnerEntity();
            if (owner != null && lastOwnerStimulus > 0 && (now - lastOwnerStimulus) <= OWNER_EVENT_WINDOW_TICKS) {
                if (owner.squaredDistanceTo(mob) <= (SCAN_RANGE * SCAN_RANGE)) {
                    candidate = owner.getBlockPos();
                }
            }
        }

        // If owner heuristic didn't yield a candidate, fall back to nearest dropped item scent
        if (candidate == null && mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            List<ItemEntity> items = sw.getEntitiesByClass(
                ItemEntity.class,
                mob.getBoundingBox().expand(SCAN_RANGE),
                it -> it != null && it.isAlive() && !it.getStack().isEmpty()
            );
            if (!items.isEmpty()) {
                ItemEntity nearest = null;
                double best = Double.MAX_VALUE;
                for (ItemEntity it : items) {
                    double d = mob.squaredDistanceTo(it);
                    if (d < best) {
                        best = d;
                        nearest = it;
                    }
                }
                if (nearest != null) {
                    candidate = nearest.getBlockPos();
                }
            }
        }

        if (candidate == null) {
            return false;
        }

        // Move towards candidate and prepare to sniff
        targetPos = Vec3d.ofCenter(candidate);
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return sniffTicks < sniffDuration;
    }

    @Override
    protected void onStartGoal() {
        sniffTicks = 0;
        sniffDuration = MIN_SNIFF_TICKS + mob.getRandom().nextInt((MAX_SNIFF_TICKS - MIN_SNIFF_TICKS) + 1);

        // Approach target if we have one
        if (targetPos != null) {
            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.9); // deliberate approach speed
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        // Apply explicit PetComponent cooldown key as specified
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int cooldown = 45 * 20 + mob.getRandom().nextInt((90 - 45 + 1) * 20); // 45–90s
            pc.setCooldown("scent_trail_sniff", cooldown);
        }

        // Reset orientation
        mob.setPitch(0);
        targetPos = null;
    }

    @Override
    protected void onTickGoal() {
        sniffTicks++;

        // Lightweight "sniff/head bob" - gentle head pitch oscillation and micro-yaw
        float phase = (sniffTicks % 20) / 20.0f;
        float bob = (float) Math.sin(phase * MathHelper.TAU) * 10.0f;
        mob.setPitch(bob);

        if (sniffTicks % 15 == 0) {
            mob.setYaw(mob.bodyYaw + mob.getRandom().nextFloat() * 6f - 3f);
        }

        // If far from target, keep moving a bit to arrive
        if (targetPos != null && mob.squaredDistanceTo(targetPos) > 1.0) {
            if (mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.9);
            }
        } else {
            // Stop when close enough
            mob.getNavigation().stop();
        }
    }

    @Override
    protected float calculateEngagement() {
        // Keep it subtle and brief; slight modulation with distance to target
        float engagement = 0.55f;
        if (targetPos != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetPos));
            // Closer targets are a bit more engaging
            engagement += MathHelper.clamp((float) ((SCAN_RANGE - d) / (SCAN_RANGE * 2.0)), 0.0f, 0.1f);
        }
        return MathHelper.clamp(engagement, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Very subtle curiosity pulse
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.08f
        );
    }
}