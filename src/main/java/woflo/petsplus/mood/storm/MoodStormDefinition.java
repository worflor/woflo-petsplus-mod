package woflo.petsplus.mood.storm;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Datapack-driven configuration describing how a particular mood storm should
 * behave. Definitions may override the default emotion push, particles, and the
 * command functions that should run when the storm is triggered.
 */
public record MoodStormDefinition(Identifier id,
                                  PetComponent.Mood mood,
                                  boolean eligible,
                                  List<Identifier> rewardFunctions,
                                  List<Identifier> penaltyFunctions,
                                  @Nullable PetComponent.Emotion emotionOverride,
                                  @Nullable Identifier ambientSoundId,
                                  @Nullable Identifier particleId) {

    public MoodStormDefinition {
        rewardFunctions = rewardFunctions == null ? List.of() : List.copyOf(rewardFunctions);
        penaltyFunctions = penaltyFunctions == null ? List.of() : List.copyOf(penaltyFunctions);
    }
}
