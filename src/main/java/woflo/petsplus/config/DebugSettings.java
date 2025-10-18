package woflo.petsplus.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import woflo.petsplus.Petsplus;

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
    private static volatile Level originalPetsplusLogLevel = null;

    private DebugSettings() {
        // Prevent instantiation
    }

    /**
     * Enable debug logging/features in-memory.
     */
    public static synchronized void enableDebug() {
        debugEnabled = true;
        ensureOriginalLogLevelRecorded();
        setPetsplusLoggerLevel(Level.DEBUG);
    }

    /**
     * Disable debug logging/features in-memory.
     */
    public static synchronized void disableDebug() {
        debugEnabled = false;
        Level target = originalPetsplusLogLevel != null ? originalPetsplusLogLevel : Level.INFO;
        setPetsplusLoggerLevel(target);
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

    public static void broadcastDebug(MinecraftServer server, Text text) {
        if (!debugEnabled || server == null || text == null) {
            return;
        }
        var playerManager = server.getPlayerManager();
        if (playerManager == null) {
            return;
        }
        for (ServerPlayerEntity player : playerManager.getPlayerList()) {
            if (player.hasPermissionLevel(2)) {
                player.sendMessage(text, false);
            }
        }
    }

    private static void ensureOriginalLogLevelRecorded() {
        if (originalPetsplusLogLevel != null) {
            return;
        }
        Level current = resolveCurrentPetsplusLogLevel();
        if (current != null) {
            originalPetsplusLogLevel = current;
        }
    }

    private static Level resolveCurrentPetsplusLogLevel() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            if (context == null) {
                return null;
            }
            var configuration = context.getConfiguration();
            if (configuration == null) {
                return null;
            }
            LoggerConfig loggerConfig = configuration.getLoggerConfig(Petsplus.MOD_ID);
            return loggerConfig != null ? loggerConfig.getLevel() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setPetsplusLoggerLevel(Level level) {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            if (context != null) {
                var configuration = context.getConfiguration();
                if (configuration != null) {
                    ensureLoggerConfigured(configuration, Petsplus.MOD_ID, level);
                    ensureLoggerConfigured(configuration, Petsplus.class.getPackageName(), level);
                    context.updateLoggers();
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to Configurator fallback.
        }

        try {
            Configurator.setLevel(Petsplus.MOD_ID, level);
            Configurator.setLevel(Petsplus.class.getPackageName(), level);
        } catch (Throwable ignored) {
            // Swallow to keep debug toggles best-effort only.
        }
    }

    private static void ensureLoggerConfigured(org.apache.logging.log4j.core.config.Configuration configuration,
                                               String loggerName,
                                               Level level) {
        LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
        if (!loggerConfig.getName().equals(loggerName)) {
            LoggerConfig specific = new LoggerConfig(loggerName, level, true);
            specific.setAdditive(loggerConfig.isAdditive());
            configuration.addLogger(loggerName, specific);
            loggerConfig = specific;
        }
        loggerConfig.setLevel(level);
    }
}
