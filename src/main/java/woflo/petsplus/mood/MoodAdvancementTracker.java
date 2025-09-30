package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.api.mood.MoodListener;
import woflo.petsplus.state.PetComponent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Listens for notable mood milestones and routes them into advancements.
 */
public final class MoodAdvancementTracker implements MoodListener {
    private static final int RESTLESS_RELAX_WINDOW_TICKS = 2000; // 100 seconds
    private static final int ANGRY_SOOTHE_WINDOW_TICKS = 400; // 20 seconds

    private static final MoodAdvancementTracker INSTANCE = new MoodAdvancementTracker();

    private final Map<MobEntity, MoodState> state = new WeakHashMap<>();

    private MoodAdvancementTracker() {}

    public static void register() {
        MoodAPI.get().registerListener(INSTANCE);
    }

    @Override
    public void onMoodChanged(MobEntity pet, PetComponent.Mood oldMood, int oldLevel,
                              PetComponent.Mood newMood, int newLevel) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return;
        }

        if (!(component.getOwner() instanceof ServerPlayerEntity owner)) {
            return;
        }

        long now = serverWorld.getTime();
        MoodState tracker = state.computeIfAbsent(pet, ignored -> new MoodState());

        // Handle transition-based milestones (RESTLESS→HAPPY, ANGRY→CALM)
        handleRestlessMilestone(oldMood, oldLevel, newMood, newLevel, owner, now, tracker);
        handleAngryMilestone(oldMood, oldLevel, newMood, newLevel, owner, now, tracker);

        // Trigger mood level criterion for all mood changes
        AdvancementCriteriaRegistry.PET_MOOD_LEVEL.trigger(owner, newMood, newLevel);
    }

    private void handleRestlessMilestone(PetComponent.Mood oldMood, int oldLevel,
                                         PetComponent.Mood newMood, int newLevel,
                                         ServerPlayerEntity owner, long now, MoodState tracker) {
        // Track when RESTLESS level 3+ is reached
        if (newMood == PetComponent.Mood.RESTLESS && newLevel >= 3) {
            tracker.restlessArmed = true;
            tracker.restlessArmedTick = now;
            return;
        }

        // Check if transitioning from RESTLESS to HAPPY within time window
        if (tracker.restlessArmed && oldMood == PetComponent.Mood.RESTLESS &&
            newMood == PetComponent.Mood.HAPPY && newLevel >= 1) {
            long ticksElapsed = now - tracker.restlessArmedTick;
            // Fire the transition criterion (JSON validates the time window)
            AdvancementCriteriaRegistry.PET_MOOD_TRANSITION.trigger(owner,
                oldMood, oldLevel, newMood, newLevel, ticksElapsed);
            tracker.restlessArmed = false;
        } else if (tracker.restlessArmed && now - tracker.restlessArmedTick > RESTLESS_RELAX_WINDOW_TICKS) {
            // Window expired
            tracker.restlessArmed = false;
        }
    }

    private void handleAngryMilestone(PetComponent.Mood oldMood, int oldLevel,
                                      PetComponent.Mood newMood, int newLevel,
                                      ServerPlayerEntity owner, long now, MoodState tracker) {
        // Track when ANGRY level 3+ is reached
        if (newMood == PetComponent.Mood.ANGRY && newLevel >= 3) {
            tracker.angryArmed = true;
            tracker.angryArmedTick = now;
            return;
        }

        // Check if transitioning from ANGRY to non-ANGRY with low level within time window
        if (tracker.angryArmed && oldMood == PetComponent.Mood.ANGRY &&
            newMood != PetComponent.Mood.ANGRY && newLevel <= 1) {
            long ticksElapsed = now - tracker.angryArmedTick;
            // Fire the transition criterion (JSON validates the time window)
            AdvancementCriteriaRegistry.PET_MOOD_TRANSITION.trigger(owner,
                oldMood, oldLevel, newMood, newLevel, ticksElapsed);
            tracker.angryArmed = false;
        } else if (tracker.angryArmed && now - tracker.angryArmedTick > ANGRY_SOOTHE_WINDOW_TICKS) {
            // Window expired
            tracker.angryArmed = false;
        }
    }

    private static final class MoodState {
        boolean restlessArmed;
        long restlessArmedTick;
        boolean angryArmed;
        long angryArmedTick;
    }
}
