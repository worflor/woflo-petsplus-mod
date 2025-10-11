package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;

/**
 * Well-known perception stimulus identifiers used by built-in systems.
 */
public final class PerceptionStimulusType {
    public static final Identifier OWNER_NEARBY = Identifier.of("petsplus", "perception/owner_nearby");
    public static final Identifier CROWD_SUMMARY = Identifier.of("petsplus", "perception/crowd_summary");
    public static final Identifier ENVIRONMENTAL_SNAPSHOT = Identifier.of("petsplus", "perception/environmental_snapshot");
    public static final Identifier MOOD_BLEND = Identifier.of("petsplus", "perception/mood_blend");
    public static final Identifier EMOTION_SAMPLE = Identifier.of("petsplus", "perception/emotion_sample");
    public static final Identifier ENERGY_PROFILE = Identifier.of("petsplus", "perception/energy_profile");
    public static final Identifier GOAL_HISTORY = Identifier.of("petsplus", "perception/goal_history");
    public static final Identifier SOCIAL_GRAPH = Identifier.of("petsplus", "perception/social_graph");
    public static final Identifier STATE_DATA = Identifier.of("petsplus", "perception/state_data");
    public static final Identifier WORLD_TICK = Identifier.of("petsplus", "perception/world_tick");

    private PerceptionStimulusType() {
    }
}
