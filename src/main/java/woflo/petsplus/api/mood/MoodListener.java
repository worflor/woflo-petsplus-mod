package woflo.petsplus.api.mood;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;

/** Listener for mood changes at the pet level. */
public interface MoodListener {
    void onMoodChanged(MobEntity pet, PetComponent.Mood oldMood, int oldLevel,
                       PetComponent.Mood newMood, int newLevel);
}
