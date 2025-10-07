package woflo.petsplus.mood.storm;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds the currently active mood storm definitions loaded from datapacks and
 * exposes convenience lookup helpers for runtime systems.
 */
public final class MoodStormRegistry {
    private static final EnumMap<PetComponent.Mood, MoodStormDefinition> DEFINITIONS =
        new EnumMap<>(PetComponent.Mood.class);
    private static boolean initialized;

    private MoodStormRegistry() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new MoodStormDataLoader());
    }

    static synchronized void reload(Map<PetComponent.Mood, MoodStormDefinition> definitions) {
        DEFINITIONS.clear();
        if (definitions != null && !definitions.isEmpty()) {
            DEFINITIONS.putAll(definitions);
            Petsplus.LOGGER.info("Loaded {} mood storm definitions", DEFINITIONS.size());
        } else {
            Petsplus.LOGGER.info("No mood storm definitions provided; using built-in defaults.");
        }
    }

    @Nullable
    public static MoodStormDefinition definitionFor(PetComponent.Mood mood) {
        return DEFINITIONS.get(mood);
    }

    public static boolean isEligible(PetComponent.Mood mood) {
        MoodStormDefinition definition = definitionFor(mood);
        return definition == null || definition.eligible();
    }
}
