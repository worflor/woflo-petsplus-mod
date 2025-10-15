package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Owner orbit - maintains distance while circling owner.
 */
public class OwnerOrbitGoal extends AdaptiveGoal {
    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private double orbitAngle;
    private double orbitRadius = 4.0;
    private Vec3d orbitTarget;
    private Vec3d lastIssuedTarget;
    
    public OwnerOrbitGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.OWNER_ORBIT), EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null || !ctx.ownerNearby()) {
            return false;
        }

        // Shelter-hold: if raining and pet is sheltered and recently wet, avoid leaving cover
        if (isShelterHoldActive()) {
            if (ctx.distanceToOwner() > 3.0f) {
                return false;
            }
        }

        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(mob);

        if (!canOrbitInCurrentMedium(capabilities)) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null || !ctx.ownerNearby()) {
            return false;
        }

        // Continue only if not shelter-holding or owner is very close
        if (isShelterHoldActive() && ctx.distanceToOwner() > 3.0f) {
            return false;
        }

        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(mob);
        return canOrbitInCurrentMedium(capabilities);
    }
    
    @Override
    protected void onStartGoal() {
        orbitAngle = mob.getRandom().nextDouble() * Math.PI * 2;
        lastIssuedTarget = null;
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        lastIssuedTarget = null;
    }

    @Override
    protected void onTickGoal() {
        // Don’t step out from under cover during shelter-hold
        if (isShelterHoldActive()) {
            mob.getNavigation().stop();
            return;
        }
        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(mob);

        if (!canOrbitInCurrentMedium(capabilities)) {
            requestStop();
            return;
        }

        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();

        if (owner == null || owner.isRemoved()) {
            return;
        }
        
        // Update orbit angle
        orbitAngle += 0.05; // Slow rotation
        
        // Calculate orbit position
        orbitTarget = owner.getEntityPos().add(
            Math.cos(orbitAngle) * orbitRadius,
            0,
            Math.sin(orbitAngle) * orbitRadius
        );

        if (!mob.getEntityWorld().isChunkLoaded(BlockPos.ofFloored(orbitTarget))) {
            orbitTarget = owner.getEntityPos();
        }

        // Move to orbit position
        issueNavigation(orbitTarget, 0.9);

        // Periodically look at owner
        if (mob.age % 20 < 10) {
            mob.getLookControl().lookAt(owner, 30, 30);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.GUARDIAN_VIGIL, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.10f
        );
    }

    private boolean isShelterHoldActive() {
        var world = this.mob.getEntityWorld();
        if (world == null || (!world.isRaining() && !world.isThundering())) {
            return false;
        }
        if (world.isSkyVisible(this.mob.getBlockPos())) {
            return false;
        }
        var pc = woflo.petsplus.state.PetComponent.get(mob);
        if (pc == null) {
            return false;
        }
        Long lastWetTick = pc.getStateData(LAST_WET_TICK_KEY, Long.class);
        if (lastWetTick == null) {
            return false;
        }
        return world.getTime() - lastWetTick <= 2400L;
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.7f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.64f, 1.08f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.45f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.1f);
        engagement *= momentumScale;

        float socialBlend = MathHelper.clamp((socialCharge - 0.45f) / 0.25f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.72f, 1.12f);
        engagement *= socialScale;

        // Very engaging if bonded
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement += 0.2f;
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }

    private boolean canOrbitInCurrentMedium(MobCapabilities.CapabilityProfile capabilities) {
        boolean hasLandOrbitMovement = capabilities.canWander() || capabilities.canFly();
        boolean strictlyAquatic = capabilities.prefersWater() && !capabilities.prefersLand();
        boolean submerged = mob.isTouchingWater();

        if (!hasLandOrbitMovement) {
            return false;
        }

        if (submerged && strictlyAquatic) {
            return false;
        }

        if (submerged && !capabilities.prefersLand() && !capabilities.canFly()) {
            return false;
        }

        return true;
    }

    private void issueNavigation(Vec3d target, double speed) {
        if (target == null) {
            return;
        }

        if (!mob.getNavigation().isIdle() && lastIssuedTarget != null
            && target.squaredDistanceTo(lastIssuedTarget) <= 0.04) {
            return;
        }
        mob.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
        lastIssuedTarget = target;
    }
}
