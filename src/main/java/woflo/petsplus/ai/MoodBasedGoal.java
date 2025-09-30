package woflo.petsplus.ai;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;

/**
 * Base class for mood-driven AI goals.
 * Each mood can have one associated behavior that activates when that mood is dominant.
 */
public abstract class MoodBasedGoal extends Goal {
    protected final MobEntity mob;
    protected final PetComponent petComponent;
    protected final PetComponent.Mood requiredMood;
    private final MoodActionThrottle throttle;
    private final Map<String, Long> actionCooldowns = new HashMap<>();

    public MoodBasedGoal(MobEntity mob, PetComponent.Mood requiredMood) {
        this.mob = mob;
        this.petComponent = PetComponent.get(mob);
        this.requiredMood = requiredMood;
        this.throttle = MoodGoalThrottles.createThrottle(requiredMood);
    }

    @Override
    public boolean canStart() {
        if (petComponent == null) return false;

        // Only activate if this mood is currently dominant
        PetComponent.Mood currentMood = petComponent.getCurrentMood();
        if (currentMood != requiredMood) return false;

        // Check mood intensity - only activate on medium+ intensity (level 1+)
        int moodLevel = petComponent.getMoodLevel();
        if (moodLevel < 1) return false;

        boolean moodReady = canStartMoodBehavior();
        if (!moodReady) {
            return false;
        }

        long now = mob.getWorld().getTime();
        if (throttle != null && !throttle.canStart(
            mob,
            petComponent,
            requiredMood,
            now,
            shouldBypassCooldown(moodReady)
        )) {
            return false;
        }

        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (petComponent == null) return false;

        // Continue if mood is still dominant and has some intensity
        PetComponent.Mood currentMood = petComponent.getCurrentMood();
        if (currentMood != requiredMood) {
            return false;
        }

        long now = mob.getWorld().getTime();
        boolean continueBehavior = shouldContinueMoodBehavior();
        if (!continueBehavior) {
            if (throttle != null && !throttle.hasSatisfiedMinActive(now)) {
                return true;
            }
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (petComponent == null) return;
        long now = mob.getWorld().getTime();
        if (throttle != null) {
            throttle.markStarted(now, petComponent, requiredMood);
            MoodGoalThrottleConfig config = throttle.config();
            if (config != null && config.followHoldTicks() > 0) {
                petComponent.requestMoodFollowHold(now, config.followHoldTicks(), config.followDistanceBonus());
            }
        }
        actionCooldowns.clear();
    }

    @Override
    public void stop() {
        if (petComponent == null) return;
        if (throttle != null) {
            throttle.markStopped(mob.getWorld().getTime(), petComponent, requiredMood);
        }
        actionCooldowns.clear();
    }

    /**
     * Mood-specific start condition. Override this in subclasses.
     */
    protected abstract boolean canStartMoodBehavior();

    /**
     * Mood-specific continue condition. Override this in subclasses.
     */
    protected abstract boolean shouldContinueMoodBehavior();

    protected boolean shouldBypassCooldown(boolean moodReady) {
        return false;
    }

    protected boolean isActionReady(String key, long now) {
        return now >= actionCooldowns.getOrDefault(key, Long.MIN_VALUE);
    }

    protected void scheduleActionCooldown(String key, long now, int minDelayTicks, int maxDelayTicks) {
        if (minDelayTicks < 0) {
            minDelayTicks = 0;
        }
        if (maxDelayTicks < minDelayTicks) {
            maxDelayTicks = minDelayTicks;
        }
        int range = maxDelayTicks - minDelayTicks;
        long delay = minDelayTicks;
        if (range > 0) {
            delay += mob.getRandom().nextInt(range + 1);
        }
        actionCooldowns.put(key, now + delay);
    }

    protected void clearActionCooldown(String key) {
        actionCooldowns.remove(key);
    }

    protected long currentWorldTick() {
        return mob.getWorld().getTime();
    }
}
