package woflo.petsplus.debug;

import net.minecraft.server.MinecraftServer;
import woflo.petsplus.config.DebugSettings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates noisy per-tick debug signals into concise, periodic snapshots.
 *
 * Scope: owner-centric. Metrics from multiple pets are combined per owner and
 * flushed at a fixed tick cadence. All methods are static and light-weight.
 */
public final class DebugSnapshotAggregator {

    // Default flush cadence (in ticks). 80 ticks ~= 4 seconds.
    private static final long FLUSH_INTERVAL_TICKS = 80L;
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // Clean up every 60 seconds
    private static final long IDLE_TIMEOUT_TICKS = 600L; // Remove owners not seen for 30 seconds

    private static final Map<UUID, OwnerMetrics> OWNER_METRICS = new ConcurrentHashMap<>();
    private static long lastCleanupTick = Long.MIN_VALUE;

    private DebugSnapshotAggregator() {
    }

    public static void recordFollow(MinecraftServer server,
                                    UUID ownerId,
                                    String petName,
                                    long tick,
                                    long durationNanos,
                                    boolean startedPath,
                                    boolean reusedPath,
                                    Double startDistance,
                                    Double stopDistance) {
        if (!DebugSettings.isDebugEnabled() || server == null || ownerId == null) {
            return;
        }
        OwnerMetrics m = OWNER_METRICS.computeIfAbsent(ownerId, id -> new OwnerMetrics());
        synchronized (m) {
            m.lastTick = tick;
            m.followNanos += Math.max(0L, durationNanos);
            m.followSamples++;
            if (startedPath) m.followStartedPaths++;
            if (reusedPath) m.followReusedPaths++;
            if (startDistance != null && !startDistance.isNaN()) {
                m.followStartSum += startDistance;
                m.followStartCount++;
                m.followStartMin = Math.min(m.followStartMin, startDistance);
                m.followStartMax = Math.max(m.followStartMax, startDistance);
            }
            if (stopDistance != null && !stopDistance.isNaN()) {
                m.followStopSum += stopDistance;
                m.followStopCount++;
                m.followStopMin = Math.min(m.followStopMin, stopDistance);
                m.followStopMax = Math.max(m.followStopMax, stopDistance);
            }
            if (petName != null && !petName.isEmpty()) {
                PetAcc acc = m.petAccs.computeIfAbsent(petName, k -> new PetAcc());
                acc.nanos += Math.max(0L, durationNanos);
                acc.samples++;
                // Limit tracked pets per owner to prevent unbounded growth
                if (m.petAccs.size() > 16) {
                    // Remove entry with lowest sample count
                    m.petAccs.entrySet().stream()
                        .min((a, b) -> Integer.compare(a.getValue().samples, b.getValue().samples))
                        .ifPresent(e -> m.petAccs.remove(e.getKey()));
                }
            }
            maybeFlush(server, ownerId, tick, m);
        }
    }

    public static void recordSpacing(MinecraftServer server,
                                     UUID ownerId,
                                     long tick,
                                     boolean cacheHit,
                                     int neighborCount,
                                     long missComputeNanos) {
        if (!DebugSettings.isDebugEnabled() || server == null || ownerId == null) {
            return;
        }
        OwnerMetrics m = OWNER_METRICS.computeIfAbsent(ownerId, id -> new OwnerMetrics());
        synchronized (m) {
            m.lastTick = tick;
            if (cacheHit) m.spacingHits++; else m.spacingMisses++;
            m.spacingNeighborSamples += Math.max(0, neighborCount);
            if (!cacheHit) m.spacingMissComputeNanos += Math.max(0L, missComputeNanos);
            maybeFlush(server, ownerId, tick, m);
        }
    }

    public static void recordAuraResolve(MinecraftServer server,
                                         UUID ownerId,
                                         long tick,
                                         boolean usedResolver) {
        if (!DebugSettings.isDebugEnabled() || server == null || ownerId == null) {
            return;
        }
        OwnerMetrics m = OWNER_METRICS.computeIfAbsent(ownerId, id -> new OwnerMetrics());
        synchronized (m) {
            m.lastTick = tick;
            if (usedResolver) m.auraResolverHits++; else m.auraFallbackHits++;
            maybeFlush(server, ownerId, tick, m);
        }
    }

    public static void recordAuraApplied(MinecraftServer server,
                                         UUID ownerId,
                                         long tick,
                                         int appliedCount) {
        if (!DebugSettings.isDebugEnabled() || server == null || ownerId == null) {
            return;
        }
        OwnerMetrics m = OWNER_METRICS.computeIfAbsent(ownerId, id -> new OwnerMetrics());
        synchronized (m) {
            m.lastTick = tick;
            m.auraTargetsApplied += Math.max(0, appliedCount);
            maybeFlush(server, ownerId, tick, m);
        }
    }

    private static void maybeFlush(MinecraftServer server, UUID ownerId, long tick, OwnerMetrics m) {
        if (m.lastFlushTick == Long.MIN_VALUE) {
            m.lastFlushTick = tick;
            return;
        }
        if (tick - m.lastFlushTick < FLUSH_INTERVAL_TICKS) {
            return;
        }

        // Periodic cleanup of offline owners
        if (lastCleanupTick == Long.MIN_VALUE || tick - lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
            lastCleanupTick = tick;
            OWNER_METRICS.entrySet().removeIf(e -> {
                OwnerMetrics metrics = e.getValue();
                return metrics.lastTick != Long.MIN_VALUE && tick - metrics.lastTick > IDLE_TIMEOUT_TICKS;
            });
        }

        // Build follow summary
        if (m.followSamples > 0) {
            double avgMs = (m.followNanos / (double) m.followSamples) / 1_000_000.0;
            double avgStart = m.followStartCount > 0 ? (m.followStartSum / m.followStartCount) : Double.NaN;
            double avgStop = m.followStopCount > 0 ? (m.followStopSum / m.followStopCount) : Double.NaN;

            String topPet = null;
            double topAvgMs = -1.0;
            for (Map.Entry<String, PetAcc> e : m.petAccs.entrySet()) {
                PetAcc acc = e.getValue();
                if (acc.samples <= 0) continue;
                double pAvg = (acc.nanos / (double) acc.samples) / 1_000_000.0;
                if (pAvg > topAvgMs) {
                    topAvgMs = pAvg;
                    topPet = e.getKey();
                }
            }

            StringBuilder sb = new StringBuilder(160);
            sb.append("[FollowSummary] owner=").append(ownerId)
              .append(" pets=").append(m.petAccs.size())
              .append(" avgTick=").append(String.format(Locale.ROOT, "%.3fms", avgMs))
              .append(" startPaths=").append(m.followStartedPaths)
              .append(" reusePaths=").append(m.followReusedPaths);
            if (!Double.isNaN(avgStart)) {
                sb.append(" avgStart=").append(String.format(Locale.ROOT, "%.2f", avgStart))
                  .append(" minMaxStart=").append(String.format(Locale.ROOT, "%.2f/%.2f", m.followStartMin, m.followStartMax));
            }
            if (!Double.isNaN(avgStop)) {
                sb.append(" avgStop=").append(String.format(Locale.ROOT, "%.2f", avgStop))
                  .append(" minMaxStop=").append(String.format(Locale.ROOT, "%.2f/%.2f", m.followStopMin, m.followStopMax));
            }
            if (topPet != null) {
                sb.append(" topPet=").append(topPet).append("@").append(String.format(Locale.ROOT, "%.3fms", topAvgMs));
            }
            DebugSettings.broadcastDebug(server, net.minecraft.text.Text.literal(sb.toString()));
        }

        // Build spacing summary
        int totalSpacing = m.spacingHits + m.spacingMisses;
        if (totalSpacing > 0) {
            double hitRate = (double) m.spacingHits / totalSpacing * 100.0;
            double avgNeighbors = (double) m.spacingNeighborSamples / totalSpacing;
            double avgMissMs = m.spacingMisses == 0 ? 0.0
                : (m.spacingMissComputeNanos / (double) m.spacingMisses) / 1_000_000.0;
            String line = String.format(Locale.ROOT,
                "[SpacingSummary] owner=%s hitRate=%.2f avgNeighbors=%.2f avgMissCompute=%.3fms",
                ownerId,
                hitRate,
                avgNeighbors,
                avgMissMs);
            DebugSettings.broadcastDebug(server, net.minecraft.text.Text.literal(line));
        }

        // Build aura summary
        int totalAura = m.auraResolverHits + m.auraFallbackHits;
        if (totalAura > 0 || m.auraTargetsApplied > 0) {
            double resolverRate = totalAura == 0 ? 0.0 : (double) m.auraResolverHits / totalAura * 100.0;
            String line = String.format(Locale.ROOT,
                "[AuraSummary] owner=%s resolverHitRate=%.2f applied=%d",
                ownerId,
                resolverRate,
                m.auraTargetsApplied);
            DebugSettings.broadcastDebug(server, net.minecraft.text.Text.literal(line));
        }

        // Reset window and carry forward tick
        m.resetForNextWindow();
        m.lastFlushTick = tick;
    }

    private static final class OwnerMetrics {
        long lastTick = Long.MIN_VALUE;
        long lastFlushTick = Long.MIN_VALUE;

        long followNanos = 0L;
        int followSamples = 0;
        int followStartedPaths = 0;
        int followReusedPaths = 0;
        double followStartSum = 0.0;
        int followStartCount = 0;
        double followStartMin = Double.POSITIVE_INFINITY;
        double followStartMax = Double.NEGATIVE_INFINITY;
        double followStopSum = 0.0;
        int followStopCount = 0;
        double followStopMin = Double.POSITIVE_INFINITY;
        double followStopMax = Double.NEGATIVE_INFINITY;
        final Map<String, PetAcc> petAccs = new HashMap<>();

        int spacingHits = 0;
        int spacingMisses = 0;
        int spacingNeighborSamples = 0;
        long spacingMissComputeNanos = 0L;

        int auraResolverHits = 0;
        int auraFallbackHits = 0;
        int auraTargetsApplied = 0;

        void resetForNextWindow() {
            followNanos = 0L;
            followSamples = 0;
            followStartedPaths = 0;
            followReusedPaths = 0;
            followStartSum = 0.0;
            followStartCount = 0;
            followStartMin = Double.POSITIVE_INFINITY;
            followStartMax = Double.NEGATIVE_INFINITY;
            followStopSum = 0.0;
            followStopCount = 0;
            followStopMin = Double.POSITIVE_INFINITY;
            followStopMax = Double.NEGATIVE_INFINITY;
            petAccs.clear();

            spacingHits = 0;
            spacingMisses = 0;
            spacingNeighborSamples = 0;
            spacingMissComputeNanos = 0L;

            auraResolverHits = 0;
            auraFallbackHits = 0;
            auraTargetsApplied = 0;
        }
    }

    private static final class PetAcc {
        long nanos = 0L;
        int samples = 0;
    }
}
