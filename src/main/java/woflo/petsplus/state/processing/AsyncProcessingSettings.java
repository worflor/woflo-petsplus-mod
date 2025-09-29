package woflo.petsplus.state.processing;

/**
 * Simple feature flag holder for experimental asynchronous processing paths.
 * Flags are read once at startup from system properties to avoid repeated
 * string parsing on hot code paths.
 */
public final class AsyncProcessingSettings {
    private static final boolean ENABLE_ASYNC_ABILITIES =
        Boolean.parseBoolean(System.getProperty("petsplus.async.abilities", "false"));
    private static final boolean SHADOW_ASYNC_ABILITIES =
        Boolean.parseBoolean(System.getProperty("petsplus.async.abilities.shadow", "false"));
    private static final boolean ENABLE_ASYNC_SPATIAL =
        Boolean.parseBoolean(System.getProperty("petsplus.async.spatial", "true"));
    private static final boolean ENABLE_ASYNC_OWNER =
        Boolean.parseBoolean(System.getProperty("petsplus.async.owner", "true"));
    private static final boolean ENABLE_ASYNC_PREDICTIVE =
        Boolean.parseBoolean(System.getProperty("petsplus.async.predictive", "true"));
    private static final boolean ENABLE_ASYNC_MOOD =
        Boolean.parseBoolean(System.getProperty("petsplus.async.mood", "true"));
    private static final int TELEMETRY_LOG_INTERVAL_TICKS = Math.max(
        0,
        Integer.parseInt(System.getProperty("petsplus.async.telemetry.interval", "0"))
    );

    private AsyncProcessingSettings() {
    }

    /**
     * @return {@code true} when the asynchronous ability execution pipeline should
     *         replace the synchronous path.
     */
    public static boolean asyncAbilitiesEnabled() {
        return ENABLE_ASYNC_ABILITIES;
    }

    /**
     * @return {@code true} when the asynchronous ability executor should run in
     *         shadow mode (results are computed but not applied).
     */
    public static boolean asyncAbilitiesShadowEnabled() {
        return SHADOW_ASYNC_ABILITIES;
    }

    /**
     * @return {@code true} when spatial analytics should be prepared on the
     *         background worker instead of during the main tick.
     */
    public static boolean asyncSpatialAnalyticsEnabled() {
        return ENABLE_ASYNC_SPATIAL;
    }

    /**
     * @return {@code true} when owner-scoped upkeep should be planned on the
     *         background coordinator before being applied on the main thread.
     */
    public static boolean asyncOwnerProcessingEnabled() {
        return ENABLE_ASYNC_OWNER;
    }

    /**
     * @return {@code true} when predictive scheduling should be computed on the
     *         background worker.
     */
    public static boolean asyncPredictiveSchedulingEnabled() {
        return ENABLE_ASYNC_PREDICTIVE;
    }

    /**
     * @return {@code true} when queued emotion stimuli should be processed on the
     *         background coordinator whenever possible.
     */
    public static boolean asyncMoodAnalyticsEnabled() {
        return ENABLE_ASYNC_MOOD;
    }

    /**
     * @return tick interval used to print async telemetry to the log, or {@code 0}
     *         when telemetry logging is disabled.
     */
    public static int telemetryLogIntervalTicks() {
        return TELEMETRY_LOG_INTERVAL_TICKS;
    }
}
