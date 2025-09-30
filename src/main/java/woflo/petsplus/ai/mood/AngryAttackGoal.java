package woflo.petsplus.ai.mood;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;

/**
 * When pets are ANGRY, they become more aggressive and likely to attack nearby threats.
 */
public class AngryAttackGoal extends MoodBasedGoal {
    private static final Method SINGLE_ARG_TRY_ATTACK = resolveTryAttack(Entity.class);
    private static final Method WORLD_AWARE_TRY_ATTACK = resolveWorldAwareTryAttack();

    private LivingEntity target;
    private int aggressionTicks;
    private static final int MAX_AGGRESSION_TICKS = 200; // 10 seconds

    public AngryAttackGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.ANGRY);
        this.setControls(EnumSet.of(Control.MOVE, Control.TARGET));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Look for hostile entities to attack
        LivingEntity nearestThreat = findNearestThreat();
        if (nearestThreat != null) {
            this.target = nearestThreat;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return target != null && target.isAlive() && aggressionTicks < MAX_AGGRESSION_TICKS;
    }

    @Override
    protected boolean shouldBypassCooldown(boolean moodReady) {
        return moodReady && target != null && target.isAlive();
    }

    @Override
    public void start() {
        super.start();
        aggressionTicks = 0;
        mob.setTarget(target);
    }

    @Override
    public void tick() {
        aggressionTicks++;

        if (target == null || !target.isAlive()) {
            stop();
            return;
        }

        // Move toward target aggressively
        double distance = mob.squaredDistanceTo(target);
        if (distance > 4.0) { // If far away, get closer
            mob.getNavigation().startMovingTo(target, 1.3); // Fast approach
        } else if (!mob.getWorld().isClient()) {
            performAttack(target);
        }

        // Update target if it moves too far away
        if (distance > 256) { // 16 blocks
            LivingEntity newTarget = findNearestThreat();
            if (newTarget != null) {
                this.target = newTarget;
                mob.setTarget(newTarget);
            } else {
                stop();
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        target = null;
        aggressionTicks = 0;
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    private LivingEntity findNearestThreat() {
        // Look for hostile entities within reasonable range
        var threats = mob.getWorld().getEntitiesByClass(
            HostileEntity.class,
            mob.getBoundingBox().expand(12),
            entity -> entity.isAlive() && !entity.isRemoved()
        );

        LivingEntity nearest = null;
        double closestDistance = Double.MAX_VALUE;

        for (HostileEntity threat : threats) {
            double distance = mob.squaredDistanceTo(threat);
            if (distance < closestDistance) {
                closestDistance = distance;
                nearest = threat;
            }
        }

        return nearest;
    }

    private void performAttack(LivingEntity target) {
        if (target == null) {
            return;
        }

        if (SINGLE_ARG_TRY_ATTACK != null) {
            try {
                SINGLE_ARG_TRY_ATTACK.invoke(mob, target);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall back to the two-argument form if invocation fails
            }
        }

        if (WORLD_AWARE_TRY_ATTACK != null && mob.getWorld() instanceof ServerWorld serverWorld) {
            try {
                WORLD_AWARE_TRY_ATTACK.invoke(mob, serverWorld, target);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Give up if neither overload can be invoked successfully
            }
        }
    }

    private static Method resolveTryAttack(Class<?>... parameterTypes) {
        try {
            return MobEntity.class.getMethod("tryAttack", parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveWorldAwareTryAttack() {
        Method livingOverload = resolveTryAttack(ServerWorld.class, LivingEntity.class);
        if (livingOverload != null) {
            return livingOverload;
        }
        return resolveTryAttack(ServerWorld.class, Entity.class);
    }
}
