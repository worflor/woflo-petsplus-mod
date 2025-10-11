package woflo.petsplus.ai.context.perception;

/**
 * Identifiers for logical slices of the {@link woflo.petsplus.ai.context.PetContext}
 * snapshot. Signals and perception stimuli can reference these slices to mark
 * cached context data dirty without forcing a full rebuild on every event.
 */
public enum ContextSlice {
    /**
     * Owner proximity, posture, and intent data.
     */
    OWNER,

    /**
     * Crowd and swarm awareness data.
     */
    CROWD,

    /**
     * Weather, biome, and other environmental state.
     */
    ENVIRONMENT,

    /**
     * Social relationship changes and group coordination updates.
     */
    SOCIAL,

    /**
     * Aggregated crowd or perception summaries derived from raw stimuli.
     */
    AGGREGATES,

    /**
     * Current mood blend and dominant mood state.
     */
    MOOD,

    /**
     * Hidden emotion intensities.
     */
    EMOTIONS,

    /**
     * Behavioural energy and momentum readings.
     */
    ENERGY,

    /**
     * Recent goal history, quirk counters, and cooldown data.
     */
    HISTORY,

    /**
     * Rolling window of raw perception stimuli.
     */
    STIMULI,

    /**
     * Arbitrary state-data mutations on the component.
     */
    STATE_DATA,

    /**
     * Time-derived world metadata (time of day, day/night flags).
     */
    WORLD,

    /**
     * Level-of-detail tier changes for dormant/active transitions.
     */
    LOD,

    /**
     * Fallback slice used when a stimulus dirties the entire snapshot.
     */
    ALL
}
