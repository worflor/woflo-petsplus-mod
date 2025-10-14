package woflo.petsplus.ai.goals.special;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.EnumSet;

/**
 * Replaces the vanilla panic goal while the pet is on fire with a nuanced retreat routine.
 * The behaviour scales urgency with hazard severity, blends in the pet's current stamina,
 * and occasionally spins to accelerate extinguishing (with a cooldown so it cannot be spammed).
 */
public final class OnFireScrambleGoal extends Goal {
    private static final double MIN_STEP_SPEED = 0.65d;
    private static final double MAX_STEP_SPEED = 2.40d;
    private static final int BASE_RETARGET_TICKS = 6;
    private static final int MAX_RETARGET_JITTER = 6;
    private static final int SPIN_COOLDOWN_MIN = 20;
    private static final int SPIN_COOLDOWN_MAX = 40;

    private final MobEntity mob;
    private final PetComponent petComponent;

    private BehaviouralEnergyProfile energyProfile = BehaviouralEnergyProfile.neutral();
    private Random nuanceRandom = Random.create();

    private double activeSpeed = 1.0d;
    private float severity = 0.4f;
    private Vec3d targetPos;
    private int retargetCooldown;
    private int ticksRunning;
    private float lastYaw;
    private float spinAccumulator;
    private int spinCooldownTicks;
    private int spinDirection = 1;
    private float nuancePhase;

    public OnFireScrambleGoal(MobEntity mob, PetComponent petComponent) {
        this.mob = mob;
        this.petComponent = petComponent;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!mob.isAlive()) {
            return false;
        }
        if (!(mob.isOnFire() || mob.getFireTicks() > 0)) {
            return false;
        }
        if (mob.hasVehicle()) {
            return false;
        }
        if (mob.isTouchingWaterOrRain()) {
            return false;
        }
        return mob.getNavigation() != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!mob.isAlive()) {
            return false;
        }
        if (mob.isTouchingWaterOrRain()) {
            return false;
        }
        if (mob.isOnFire() || mob.getFireTicks() > 0) {
            return true;
        }
        // linger briefly to finish the scramble once flames go out
        return ticksRunning < 20 && !mob.getNavigation().isIdle();
    }

    @Override
    public void start() {
        ticksRunning = 0;
        spinAccumulator = 0f;
        spinCooldownTicks = 0;
        spinDirection = mob.getRandom().nextBoolean() ? 1 : -1;
        lastYaw = mob.getYaw();
        targetPos = null;
        retargetCooldown = 0;

        energyProfile = resolveEnergyProfile();
        severity = computeSeverity();

        long baseSeed = petComponent != null
            ? petComponent.getStablePerPetSeed()
            : mob.getUuid().getMostSignificantBits() ^ mob.getUuid().getLeastSignificantBits();
        nuanceRandom = Random.create(baseSeed ^ mob.getEntityWorld().getTime());
        nuancePhase = nuanceRandom.nextFloat() * (float) (Math.PI * 2.0);

        activeSpeed = computeSpeed(severity, energyProfile);

        applyStartHop();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetPos = null;
    }

    @Override
    public void tick() {
        ticksRunning++;

        if (ticksRunning % 5 == 0) {
            BehaviouralEnergyProfile updatedProfile = resolveEnergyProfile();
            float newSeverity = computeSeverity();
            severity = MathHelper.clamp(MathHelper.lerp(0.35f, severity, newSeverity), 0.05f, 1f);
            activeSpeed = MathHelper.clamp(MathHelper.lerp(0.4f, activeSpeed,
                computeSpeed(severity, updatedProfile)), MIN_STEP_SPEED, MAX_STEP_SPEED);
            energyProfile = updatedProfile;
        }

        if (retargetCooldown > 0) {
            retargetCooldown--;
        }

        if (shouldPickNewTarget()) {
            pickNewTarget();
        }

        mob.getNavigation().setSpeed(activeSpeed);

        // encourage small hops; higher severity = more frequent jumps
        int hopInterval = Math.max(4, (int) MathHelper.lerp(severity, 14, 5));
        if (ticksRunning % hopInterval == 0) {
            mob.getJumpControl().setActive();
        }

        applySpinBehaviour();
    }

    private boolean shouldPickNewTarget() {
        if (targetPos == null || mob.getNavigation().isIdle()) {
            return true;
        }
        double closeRadius = MathHelper.lerp(severity, 1.2d, 2.8d);
        if (mob.squaredDistanceTo(targetPos) <= closeRadius) {
            return true;
        }
        if (retargetCooldown <= 0) {
            float jitterChance = 0.10f + severity * 0.35f;
            if (mob.getRandom().nextFloat() < jitterChance) {
                return true;
            }
        }
        return false;
    }

    private void pickNewTarget() {
        retargetCooldown = BASE_RETARGET_TICKS + mob.getRandom().nextInt(MAX_RETARGET_JITTER + 1);

        double range = MathHelper.lerp(severity, 1.8d, 6.5d);
        Vec3d pos = null;

        if (severity < 0.35f && mob.getRandom().nextFloat() < 0.7f) {
            Vec3d backwards = mob.getRotationVec(1.0f).normalize().multiply(-range);
            pos = currentPosition().add(backwards);
        } else {
            Vec3d candidate = null;
            if (mob instanceof PathAwareEntity pathAware) {
                candidate = NoPenaltyTargeting.find(pathAware, (int) Math.ceil(range), 2);
            }
            if (candidate != null) {
                pos = candidate;
            }
        }

        if (pos == null) {
            double angle = (ticksRunning * 0.25d) + nuancePhase;
            angle += spinDirection * severity * 0.6d;
            double radius = range * (0.6d + mob.getRandom().nextDouble() * 0.4d);
            Vec3d base = currentPosition();
            pos = base.add(Math.cos(angle) * radius, 0d, Math.sin(angle) * radius);
        }

        targetPos = pos;
        mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, MathHelper.clamp(activeSpeed * speedJitter(),
            MIN_STEP_SPEED, MAX_STEP_SPEED));
    }

    private double speedJitter() {
        double jitter = 0.85d + (mob.getRandom().nextDouble() * 0.3d);
        double momentumPulse = 0.9d + energyProfile.momentum() * 0.2d;
        return MathHelper.clamp(jitter * momentumPulse, 0.75d, 1.3d);
    }

    private void applyStartHop() {
        Vec3d look = mob.getRotationVec(1.0f);
        double retreatStrength = MathHelper.lerp(severity, 0.25d, 0.7d);
        Vec3d lateral = new Vec3d(
            (mob.getRandom().nextDouble() - 0.5d) * severity * 0.4d,
            0d,
            (mob.getRandom().nextDouble() - 0.5d) * severity * 0.4d
        );
        Vec3d impulse = look.multiply(-retreatStrength).add(lateral);
        mob.addVelocity(impulse.x, 0.12d + severity * 0.12d, impulse.z);
        mob.velocityModified = true;
    }

    private BehaviouralEnergyProfile resolveEnergyProfile() {
        if (petComponent == null) {
            return BehaviouralEnergyProfile.neutral();
        }
        var engine = petComponent.getMoodEngine();
        if (engine == null) {
            return BehaviouralEnergyProfile.neutral();
        }
        return engine.getBehaviouralEnergyProfile();
    }

    private float computeSeverity() {
        float hazard = PetMoodEngine.computeStatusHazardSeverity(mob);
        float fireRatio = MathHelper.clamp(mob.getFireTicks() / 160f, 0f, 1f);
        float missingHealth = 1f - MathHelper.clamp(mob.getHealth() / Math.max(1f, mob.getMaxHealth()), 0f, 1f);
        float base = Math.max(hazard, fireRatio);
        return MathHelper.clamp(base * 0.7f + missingHealth * 0.3f + 0.08f, 0.05f, 1f);
    }

    private double computeSpeed(float severity, BehaviouralEnergyProfile profile) {
        double stamina = MathHelper.clamp(profile.physicalStamina(), 0.05f, 1f);
        double momentum = MathHelper.clamp(profile.momentum(), 0f, 1f);
        double severityPulse = 0.95d + severity * 0.85d;
        double staminaPulse = 0.8d + stamina * 0.55d;
        double momentumPulse = 0.85d + momentum * 0.45d;
        double nuance = 0.9d + 0.2d * (0.5d * (Math.sin(nuancePhase) + 1d));
        double base = severityPulse * staminaPulse * momentumPulse * nuance;
        return MathHelper.clamp(base, MIN_STEP_SPEED, MAX_STEP_SPEED);
    }

    private void applySpinBehaviour() {
        float yaw = mob.getYaw();
        float yawDelta = MathHelper.wrapDegrees(yaw - lastYaw);

        if (severity > 0.55f) {
            double swirlBase = 2.5d + severity * 6.5d;
            double sway = Math.sin((ticksRunning * 0.32d) + nuancePhase) * (0.6d + severity * 0.5d);
            float swirl = (float) ((swirlBase * 0.04d * spinDirection) + sway);
            mob.setYaw(yaw + swirl);
            mob.bodyYaw = MathHelper.lerp(0.3f, mob.bodyYaw, mob.getYaw());
            mob.headYaw = MathHelper.lerp(0.45f, mob.headYaw, mob.getYaw());
            yawDelta = MathHelper.wrapDegrees(mob.getYaw() - lastYaw);

            if (ticksRunning % 40 == 0 && mob.getRandom().nextBoolean()) {
                spinDirection *= -1;
            }
        } else {
            mob.headYaw = MathHelper.lerp(0.35f, mob.headYaw, mob.getYaw());
            mob.bodyYaw = MathHelper.lerp(0.25f, mob.bodyYaw, mob.getYaw());
        }

        accumulateSpin(Math.abs(yawDelta));
        lastYaw = mob.getYaw();
    }

    private void accumulateSpin(float delta) {
        if (spinCooldownTicks > 0) {
            spinCooldownTicks--;
            return;
        }

        spinAccumulator += delta;
        if (spinAccumulator >= 360f) {
            spinAccumulator -= 360f;
            if (!mob.getEntityWorld().isClient()) {
                int reduction = MathHelper.floor(MathHelper.lerp(severity, 10f, 40f));
                int remaining = Math.max(0, mob.getFireTicks() - reduction);
                mob.setFireTicks(remaining);
                if (remaining <= 0) {
                    mob.extinguish();
                }
            }
            spinCooldownTicks = SPIN_COOLDOWN_MIN + mob.getRandom().nextInt(SPIN_COOLDOWN_MAX - SPIN_COOLDOWN_MIN + 1);
        }
    }

    private Vec3d currentPosition() {
        return new Vec3d(mob.getX(), mob.getY(), mob.getZ());
    }
}

