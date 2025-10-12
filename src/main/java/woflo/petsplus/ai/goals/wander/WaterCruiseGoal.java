package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Water cruising for aquatic mobs - smooth swimming patterns.
 */
public class WaterCruiseGoal extends AdaptiveGoal {
    private Vec3d cruiseTarget;
    private int cruiseTicks = 0;
    private static final int TARGET_ATTEMPTS = 12;

    public WaterCruiseGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.WATER_CRUISE), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && mob.isSubmergedInWater();
    }

    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater() && mob.isSubmergedInWater();
    }
    
    @Override
    protected void onStartGoal() {
        cruiseTicks = 0;
        pickNewCruiseTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        cruiseTicks++;

        if (!shouldContinueGoal()) {
            stop();
            return;
        }

        if (cruiseTarget == null || mob.getEntityPos().distanceTo(cruiseTarget) < 2.0 || cruiseTicks % 80 == 0) {
            pickNewCruiseTarget();
        }

        if (cruiseTarget != null) {
            mob.getNavigation().startMovingTo(cruiseTarget.x, cruiseTarget.y, cruiseTarget.z, 0.9);
        } else {
            mob.getNavigation().stop();
        }
    }

    private void pickNewCruiseTarget() {
        Vec3d fallback = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
        Vec3d foundTarget = null;

        for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double distance = 4.0 + mob.getRandom().nextDouble() * 6.0;
            double targetX = mob.getX() + Math.cos(angle) * distance;
            double targetZ = mob.getZ() + Math.sin(angle) * distance;

            Vec3d candidate = resolveSubmergedTarget(targetX, targetZ);
            if (candidate != null) {
                foundTarget = candidate;
                break;
            }
        }

        cruiseTarget = foundTarget != null ? foundTarget : fallback;
        if (foundTarget == null) {
            mob.getNavigation().stop();
        }
    }

    private Vec3d resolveSubmergedTarget(double targetX, double targetZ) {
        int columnX = MathHelper.floor(targetX);
        int columnZ = MathHelper.floor(targetZ);
        int baseY = MathHelper.floor(mob.getY());

        BlockPos.Mutable mutable = new BlockPos.Mutable(columnX, baseY, columnZ);
        if (!mob.getEntityWorld().isChunkLoaded(mutable)) {
            return null;
        }

        BlockPos.Mutable waterPos = new BlockPos.Mutable(columnX, baseY, columnZ);
        if (!isWater(waterPos)) {
            boolean located = false;
            for (int dy = 1; dy <= 6 && !located; dy++) {
                waterPos.set(columnX, baseY + dy, columnZ);
                if (isWater(waterPos)) {
                    located = true;
                    break;
                }
                waterPos.set(columnX, baseY - dy, columnZ);
                if (isWater(waterPos)) {
                    located = true;
                    break;
                }
            }

            if (!located) {
                return null;
            }
        }

        BlockPos.Mutable floorPos = new BlockPos.Mutable(waterPos.getX(), waterPos.getY(), waterPos.getZ());
        while (floorPos.getY() > mob.getEntityWorld().getBottomY()) {
            floorPos.move(0, -1, 0);
            if (!isWater(floorPos)) {
                floorPos.move(0, 1, 0);
                break;
            }
        }

        BlockPos.Mutable ceilingPos = new BlockPos.Mutable(waterPos.getX(), waterPos.getY(), waterPos.getZ());
        int topY = mob.getEntityWorld().getTopY(Heightmap.Type.WORLD_SURFACE, columnX, columnZ) + 1;
        while (ceilingPos.getY() < topY) {
            ceilingPos.move(0, 1, 0);
            if (!isWater(ceilingPos)) {
                ceilingPos.move(0, -1, 0);
                break;
            }
        }

        double minY = floorPos.getY() + 0.5;
        double maxY = Math.max(minY, Math.min(ceilingPos.getY() + 0.3, minY + 6.0));
        double desiredY = mob.getY() + (mob.getRandom().nextDouble() * 2.0 - 1.0) * 1.5;
        double targetY = MathHelper.clamp(desiredY, minY, maxY);

        BlockPos targetBlock = BlockPos.ofFloored(targetX, targetY, targetZ);
        if (!isWater(targetBlock)) {
            return null;
        }

        Vec3d candidate = new Vec3d(targetX, targetY, targetZ);
        Box movementBox = mob.getBoundingBox().offset(
            candidate.x - mob.getX(),
            candidate.y - mob.getY(),
            candidate.z - mob.getZ()
        );

        if (!mob.getEntityWorld().isSpaceEmpty(mob, movementBox)) {
            return null;
        }

        return candidate;
    }

    private boolean isWater(BlockPos pos) {
        return mob.getEntityWorld().getFluidState(pos).isIn(FluidTags.WATER);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.12f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.10f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.64f, 1.08f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.12f);
        engagement *= momentumScale;

        float socialBlend = MathHelper.clamp((socialCharge - 0.4f) / 0.3f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.72f, 1.08f);
        engagement *= socialScale;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

