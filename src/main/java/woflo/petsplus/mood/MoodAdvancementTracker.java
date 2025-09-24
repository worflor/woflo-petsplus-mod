package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.advancement.AdvancementManager;
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

        handleRestlessMilestone(newMood, newLevel, owner, now, tracker);
        handleAngryMilestone(newMood, newLevel, owner, now, tracker);

        if (newLevel >= 3) {
            AdvancementManager.triggerMoodLevelThree(owner, newMood);
        }
    }

    private void handleRestlessMilestone(PetComponent.Mood newMood, int newLevel,
                                         ServerPlayerEntity owner, long now, MoodState tracker) {
        if (newMood == PetComponent.Mood.RESTLESS && newLevel >= 3) {
            tracker.restlessArmed = true;
            tracker.restlessArmedTick = now;
            return;
        }

        if (!tracker.restlessArmed) {
            return;
        }

        if (newMood == PetComponent.Mood.HAPPY && newLevel >= 1) {
            if (now - tracker.restlessArmedTick <= RESTLESS_RELAX_WINDOW_TICKS) {
                AdvancementManager.triggerRestlessRelax(owner);
            }
            tracker.restlessArmed = false;
        } else if (now - tracker.restlessArmedTick > RESTLESS_RELAX_WINDOW_TICKS) {
            tracker.restlessArmed = false;
        }
    }

    private void handleAngryMilestone(PetComponent.Mood newMood, int newLevel,
                                      ServerPlayerEntity owner, long now, MoodState tracker) {
        if (newMood == PetComponent.Mood.ANGRY && newLevel >= 3) {
            tracker.angryArmed = true;
            tracker.angryArmedTick = now;
            return;
        }

        if (!tracker.angryArmed) {
            return;
        }

        if (newMood != PetComponent.Mood.ANGRY && newLevel <= 1) {
            if (now - tracker.angryArmedTick <= ANGRY_SOOTHE_WINDOW_TICKS) {
                AdvancementManager.triggerAngryCooldown(owner);
            }
            tracker.angryArmed = false;
        } else if (now - tracker.angryArmedTick > ANGRY_SOOTHE_WINDOW_TICKS) {
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
