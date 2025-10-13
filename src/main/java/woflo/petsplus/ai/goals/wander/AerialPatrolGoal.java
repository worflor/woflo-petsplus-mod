package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Aerial patrol for flying mobs - circles at various heights.
 */
public class AerialPatrolGoal extends AdaptiveGoal {
    private Vec3d patrolTarget;
    private int patrolTicks = 0;
    private double patrolRadius = 5.0;
    private double patrolHeight;
    private int consecutiveTargetFailures = 0;
    private static final int TARGET_SEARCH_ATTEMPTS = 8;
    private static final int MAX_CONSECUTIVE_TARGET_FAILURES = 4;
    
    public AerialPatrolGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.AERIAL_PATROL), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!(mob.getNavigation() instanceof BirdNavigation)) {
            return false;
        }

        if (mob.isLeashed()) {
            return false;
        }

        if (mob.isTouchingWater() && !MobCapabilities.prefersWater(mob)) {
            return false;
        }

        return hasLaunchClearance();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (!(mob.getNavigation() instanceof BirdNavigation)) {
            return false;
        }

        if (mob.isLeashed()) {
            return false;
        }

        if (mob.isOnGround()) {
            return false;
        }

        if (mob.isTouchingWater() && !MobCapabilities.prefersWater(mob)) {
            return false;
        }

        return true;
    }

    @Override
    protected void onStartGoal() {
        patrolTicks = 0;
        patrolHeight = computeInitialPatrolHeight();
        consecutiveTargetFailures = 0;
        if (!pickNewPatrolTarget()) {
            consecutiveTargetFailures++;
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        consecutiveTargetFailures = 0;
    }
    
    @Override
    protected void onTickGoal() {
        patrolTicks++;
        
        if (patrolTarget == null || mob.getEntityPos().distanceTo(patrolTarget) < 2.0 || patrolTicks % 60 == 0) {
            if (pickNewPatrolTarget()) {
                consecutiveTargetFailures = 0;
            } else {
                consecutiveTargetFailures++;
            }
        }

        if (patrolTarget == null) {
            mob.getNavigation().stop();
            if (consecutiveTargetFailures >= MAX_CONSECUTIVE_TARGET_FAILURES) {
                requestStop();
            }
            return;
        }

        if (!hasLaunchClearance()) {
            requestStop();
            return;
        }

        mob.getNavigation().startMovingTo(patrolTarget.x, patrolTarget.y, patrolTarget.z, 1.0);
    }

    private boolean pickNewPatrolTarget() {
        Vec3d origin = mob.getEntityPos();
        Vec3d bestCandidate = null;

        for (int attempt = 0; attempt < TARGET_SEARCH_ATTEMPTS; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            patrolHeight = ensureClearPatrolHeight(patrolHeight);
            Vec3d candidate = new Vec3d(
                origin.x + Math.cos(angle) * patrolRadius,
                patrolHeight,
                origin.z + Math.sin(angle) * patrolRadius
            );

            if (isValidPatrolTarget(candidate)) {
                bestCandidate = candidate;
                break;
            }
        }

        if (bestCandidate == null) {
            patrolTarget = null;
            return false;
        }

        patrolTarget = bestCandidate;
        return true;
    }

    private boolean hasLaunchClearance() {
        Box baseBox = mob.getBoundingBox().expand(0.1);
        for (double offset = 0.6; offset <= 3.0; offset += 0.4) {
            Box checkBox = baseBox.offset(0.0, offset, 0.0);
            if (mob.getEntityWorld().isSpaceEmpty(mob, checkBox)) {
                return true;
            }
        }
        return false;
    }

    private double computeInitialPatrolHeight() {
        double desired = mob.getY() + 0.75 + mob.getRandom().nextDouble() * 2.5;
        return ensureClearPatrolHeight(desired);
    }

    private double ensureClearPatrolHeight(double desiredY) {
        double minOffset = Math.max(0.6, desiredY - mob.getY());
        double maxOffset = minOffset + 3.0;
        Box baseBox = mob.getBoundingBox().expand(0.1);

        for (double offset = minOffset; offset <= maxOffset; offset += 0.4) {
            Box checkBox = baseBox.offset(0.0, offset, 0.0);
            if (mob.getEntityWorld().isSpaceEmpty(mob, checkBox)) {
                return mob.getY() + offset;
            }
        }

        return mob.getY() + minOffset;
    }

    private boolean isValidPatrolTarget(Vec3d candidate) {
        if (!mob.getEntityWorld().isChunkLoaded(BlockPos.ofFloored(candidate))) {
            return false;
        }

        Box movement = mob.getBoundingBox().offset(
            candidate.x - mob.getX(),
            candidate.y - mob.getY(),
            candidate.z - mob.getZ()
        );

        if (!mob.getEntityWorld().isSpaceEmpty(mob, movement)) {
            return false;
        }

        return candidate.y > mob.getY() - 0.5;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.12f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.65f, 1.12f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.14f);
        engagement *= momentumScale;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

