package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.mood.*;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

/**
 * Modular AI system that adds mood-based behavior goals to pets.
 * Each mood gets one corresponding AI goal that activates when that mood is dominant.
 */
public class MoodBasedAIManager {

    /**
     * Initialize mood-based AI goals for a pet.
     * Call this once when a pet is registered or when the pet's AI needs to be set up.
     */
    public static void initializeMoodAI(MobEntity pet) {
        PetComponent petComponent = PetComponent.get(pet);
        if (petComponent == null) return;

        try {
            // Ensure we don't accumulate duplicate mood goals from repeated initialization
            clearMoodAI(pet);

            // Access the goal selector via mixin
            MobEntityAccessor accessor = (MobEntityAccessor) pet;
            var goalSelector = accessor.getGoalSelector();

            // Add mood-based goals with appropriate priorities
            // Higher number = higher priority, but we want these to be medium priority

            // Defensive/survival goals (higher priority)
            goalSelector.add(5, new FearfulFleeGoal(pet));        // FEARFUL - flee from threats
            goalSelector.add(6, new ProtectiveGuardGoal(pet));    // PROTECTIVE - guard owner

            // Combat/aggressive goals (medium-high priority)
            goalSelector.add(7, new WrathfulAttackGoal(pet));     // WRATHFUL - attack threats

            // Exploration/activity goals (medium priority)
            goalSelector.add(10, new CuriousExploreGoal(pet));    // CURIOUS - investigate objects
            goalSelector.add(11, new JoyfulPlayGoal(pet));        // JOYFUL - playful behavior

            // Social/movement goals (medium priority)
            goalSelector.add(12, new BondedStayCloseGoal(pet));   // BONDED - stay close to owner
            goalSelector.add(13, new RestlessWanderGoal(pet));    // RESTLESS - constant movement

            // Rest/passive goals (lower priority)
            goalSelector.add(15, new ZenRestGoal(pet));           // ZEN - find peaceful rest spots

            // Note: Each mood should have exactly one associated goal
            // Current coverage: FEARFUL, PROTECTIVE, WRATHFUL, CURIOUS, JOYFUL, BONDED, RESTLESS, ZEN

        } catch (Exception e) {
            woflo.petsplus.Petsplus.LOGGER.warn("Failed to initialize mood-based AI for pet {}: {}",
                pet.getType(), e.getMessage());
        }
    }

    /**
     * Remove all mood-based AI goals from a pet.
     * Useful for cleanup or when switching AI systems.
     */
    public static void clearMoodAI(MobEntity pet) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) pet;
            var goalSelector = accessor.getGoalSelector();

            // Remove all mood-based goals
            goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof MoodBasedGoal);

        } catch (Exception e) {
            woflo.petsplus.Petsplus.LOGGER.warn("Failed to clear mood-based AI for pet {}: {}",
                pet.getType(), e.getMessage());
        }
    }

    /**
     * Check if a pet has mood-based AI initialized.
     */
    public static boolean hasMoodAI(MobEntity pet) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) pet;
            var goalSelector = accessor.getGoalSelector();

            return goalSelector.getGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof MoodBasedGoal);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reinitialize mood AI - useful when pet configuration changes.
     */
    public static void reinitializeMoodAI(MobEntity pet) {
        clearMoodAI(pet);
        initializeMoodAI(pet);
    }

    /**
     * Get count of active mood goals for debugging.
     */
    public static int getActiveMoodGoalCount(MobEntity pet) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) pet;
            var goalSelector = accessor.getGoalSelector();

            return (int) goalSelector.getGoals().stream()
                .filter(goal -> goal.getGoal() instanceof MoodBasedGoal)
                .filter(goal -> goal.getGoal().canStart())
                .count();

        } catch (Exception e) {
            return 0;
        }
    }
}