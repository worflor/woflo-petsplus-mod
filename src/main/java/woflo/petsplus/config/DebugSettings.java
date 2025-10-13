package woflo.petsplus.config;

/**
 * Centralized, in-memory debug and telemetry toggles for PetsPlus.
 * Defaults are safe no-ops; consumers may toggle at runtime.
 *
 * Thread-safety: fields are declared volatile for visibility, and all mutator
 * methods are synchronized to ensure safe publication and serialization of writes.
 *
 * No external dependencies; JSON/command wiring will be added in later chunks.
 */
public final class DebugSettings {

    private static volatile boolean debugEnabled = false;
    private static volatile boolean telemetryEnabled = false;
    private static volatile int telemetrySampleRateTicks = 20;
    /** Toggle for owner-centric pipeline dispatch. */
    private static volatile boolean pipelineEnabled = false;

    private DebugSettings() {
        // Prevent instantiation
    }

    /**
     * Enable debug logging/features in-memory.
     */
    public static synchronized void enableDebug() {
        debugEnabled = true;
    }

    /**
     * Disable debug logging/features in-memory.
     */
    public static synchronized void disableDebug() {
        debugEnabled = false;
    }

    /**
     * Read current debug toggle.
     * @return true if debug is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Enable lightweight telemetry in-memory.
     */
    public static synchronized void enableTelemetry() {
        telemetryEnabled = true;
    }

    /**
     * Disable telemetry in-memory.
     */
    public static synchronized void disableTelemetry() {
        telemetryEnabled = false;
    }

    /**
     * Read current telemetry toggle.
     * @return true if telemetry is enabled
     */
    public static boolean isTelemetryEnabled() {
        return telemetryEnabled;
    }

    /**
     * Get the current sample cadence in ticks for telemetry.
     * @return ticks between samples (minimum 1)
     */
    public static int getTelemetrySampleRateTicks() {
        return telemetrySampleRateTicks;
    }

    /**
     * Set the sample cadence in ticks for telemetry (clamped to at least 1).
     * This is a no-op default configuration until future wiring introduces usage.
     *
     * @param ticks desired tick interval
     */
    public static synchronized void setTelemetrySampleRate(final int ticks) {
        telemetrySampleRateTicks = Math.max(1, ticks);
    }

    /** Enable the owner-centric pipeline. */
    public static synchronized void enablePipeline() {
        pipelineEnabled = true;
    }

    /** Disable the owner-centric pipeline. */
    public static synchronized void disablePipeline() {
        pipelineEnabled = false;
    }

    /**
     * Check if the owner-centric pipeline is enabled.
     * @return true if enabled
     */
    public static boolean isPipelineEnabled() {
        return pipelineEnabled;
    }
}
