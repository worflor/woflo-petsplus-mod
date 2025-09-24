package woflo.petsplus.ai;

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

    public MoodBasedGoal(MobEntity mob, PetComponent.Mood requiredMood) {
        this.mob = mob;
        this.petComponent = PetComponent.get(mob);
        this.requiredMood = requiredMood;
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

        return canStartMoodBehavior();
    }

    @Override
    public boolean shouldContinue() {
        if (petComponent == null) return false;

        // Continue if mood is still dominant and has some intensity
        PetComponent.Mood currentMood = petComponent.getCurrentMood();
        return currentMood == requiredMood && shouldContinueMoodBehavior();
    }

    /**
     * Mood-specific start condition. Override this in subclasses.
     */
    protected abstract boolean canStartMoodBehavior();

    /**
     * Mood-specific continue condition. Override this in subclasses.
     */
    protected abstract boolean shouldContinueMoodBehavior();
}