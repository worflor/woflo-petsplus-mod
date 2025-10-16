package woflo.petsplus.state.processing;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.config.MoodEngineConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Unified swarm-backed affective contagion propagation. All contagion writes funnel through
 * PetComponent.addContagionShare(), using neighbor data obtained from the owner-scoped PetSwarmIndex.
 *
 * Goals:
 * - No world scans. Always use swarm neighbor sets.
 * - Balanced default influence that is noticeable but not overpowering.
 * - Cross-owner contagion allowed at a reduced multiplier.
 * - Species-aware weighting and bond/distance falloff.
 * - Optional micro gossip nudges for subtle ambience.
 * - Lightweight telemetry with EWMA clamp to prevent "too much" feel.
 */
public final class AffectiveContagionManager {

    private static final float DEFAULT_BASE_INFLUENCE = 0.011f;
    private static final double DEFAULT_RADIUS = 6.0d;
    private static final int DEFAULT_MAX_NEIGHBORS = 8;
    private static final float DEFAULT_CROSS_OWNER_MULTIPLIER = 0.35f;
    private static final float DEFAULT_GOSSIP_MIN = 0.003f;
    private static final float DEFAULT_GOSSIP_MAX = 0.008f;

    // Telemetry target: keep average applied contagion modest to avoid overbearing feel.
    private static final float TELEMETRY_TARGET_AVG = 0.08f;   // target ceiling over ~5s window
    private static final float TELEMETRY_ALPHA = 0.25f;        // EWMA smoothing for "recent" average

    private static final WeakHashMap<PetComponent, Telemetry> TELEMETRY = new WeakHashMap<>();

    private AffectiveContagionManager() {}

    public static void propagateFor(PetComponent component, long now) {
        if (component == null) return;
        MobEntity pet = component.getPetEntity();
        if (pet == null || pet.isRemoved()) return;
        if (!(pet.getEntityWorld() instanceof ServerWorld world) || world.isClient()) return;
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) return;

        ContagionConfig cfg = ContagionConfig.read();
        if (!cfg.enabled) return;

        // Cadence gating per-pet: avoid running more frequently than configured cadence (debounce/jitter/budget/cadence)
        long lastRun = component.getStateData("contagion_last_run", Long.class, 0L);
        if (now - lastRun < (long) cfg.cadenceTicks) {
            return;
        }
        component.setStateData("contagion_last_run", now);

        // Get owner-scoped swarm neighbors for this pet
        PetSwarmIndex swarm = manager.getSwarmIndex();
        if (swarm == null) return;

        // Collect candidate neighbors (limited by cfg.maxNeighbors)
        final List<NeighborSample> candidates = new ArrayList<>(cfg.maxNeighbors);
        swarm.forEachNeighbor(pet, component, cfg.radius, cfg.maxNeighbors, (entry, distSq) -> {
            if (entry == null || entry.component() == null) return;
            if (entry.pet() == pet) return;
            candidates.add(new NeighborSample(entry, distSq));
        });

        if (candidates.isEmpty()) {
            // Apply only a tiny gossip ambient nudge to avoid "flat" rooms
            maybeApplyGossipNudge(component, cfg, now);
            return;
        }

        // Process each neighbor into small contagion shares with per-call cumulative budget (debounce/jitter/budget/cadence)
        float totalApplied = 0f;
        float remainingBudget = 0.25f; // absolute total budget per call
        for (NeighborSample neighbor : candidates) {
            if (remainingBudget <= 0f) break;
            float applied = applyFromNeighbor(component, neighbor, cfg, now, remainingBudget);
            totalApplied += applied;
            remainingBudget -= Math.abs(applied);
            if (remainingBudget <= 0f) break;
        }

        // Telemetry clamp: if we exceeded a healthy recent avg, scale-down next time
        Telemetry t = TELEMETRY.computeIfAbsent(component, k -> new Telemetry());
        t.update(totalApplied);

        // Subtle gossip infusion after neighbor processing
        maybeApplyGossipNudge(component, cfg, now);
    }

    private static float applyFromNeighbor(PetComponent self, NeighborSample neighbor, ContagionConfig cfg, long now, float remainingBudget) {
        PetComponent otherComp = neighbor.entry.component();
        if (otherComp == null) return 0f;

        // Distance falloff (0.4 to 1.0 across [radius]) to keep close contagion stronger
        double radiusSq = cfg.radius * cfg.radius;
        double norm = MathHelper.clamp(1.0 - (neighbor.distSq / Math.max(1.0, radiusSq)), 0.0, 1.0);
        float distanceFalloff = (float)(0.4 + 0.6 * norm);

        // Bond factor: minimum of both resilience to reflect mutual receptivity
        float selfBond = MathHelper.clamp(self.computeBondResilience(now), 0.25f, 1.0f);
        float otherBond = MathHelper.clamp(otherComp.computeBondResilience(now), 0.25f, 1.0f);
        float bondFactor = Math.min(selfBond, otherBond);

        // Species weighting
        float speciesWeight = resolveSpeciesWeight(self, otherComp, cfg);

        // Cross-owner multiplier
        boolean sameOwner = sameOwner(self, otherComp);
        float crossOwnerFactor = sameOwner ? 1.0f : cfg.crossOwnerMultiplier;

        // Telemetry-based adaptive scale (if recent avg is too high, gently scale down)
        float telemetryScale = telemetryScaleFor(self);

        // Use neighbor's dominant 1-2 emotions with their weights as a guide
        Map<PetComponent.Emotion, Float> active = otherComp.getActiveEmotions();
        if (active == null || active.isEmpty()) return 0f;

        // Sort emotions by weight descending
        List<Map.Entry<PetComponent.Emotion, Float>> top = new ArrayList<>(active.entrySet());
        top.sort(Comparator.comparingDouble((Map.Entry<PetComponent.Emotion, Float> e) -> e.getValue()).reversed());

        int processed = 0;
        float applied = 0f;
        for (Map.Entry<PetComponent.Emotion, Float> e : top) {
            if (processed >= 2) break; // only the top 2
            PetComponent.Emotion emotion = e.getKey();
            float neighborWeight = MathHelper.clamp(e.getValue(), 0f, 1f);

            float amount = cfg.baseInfluence
                * distanceFalloff
                * bondFactor
                * speciesWeight
                * crossOwnerFactor
                * neighborWeight
                * telemetryScale
                * self.getHarmonyState().contagionMultiplier();

            // Safety clamp (in case config is set extreme)
            amount = MathHelper.clamp(amount, -0.2f, 0.2f);

            // Cap to remaining per-call budget; break when exhausted (debounce/jitter/budget/cadence)
            float cap = Math.min(Math.abs(amount), Math.max(0f, remainingBudget));
            if (cap <= 0f) break;
            amount = Math.copySign(cap, amount);

            if (Math.abs(amount) > 0.0001f) {
                self.addContagionShare(emotion, amount);
                applied += Math.abs(amount);
            }
            processed++;
        }
        return applied;
    }

    private static boolean sameOwner(PetComponent a, PetComponent b) {
        @Nullable var au = a.getOwnerUuid();
        @Nullable var bu = b.getOwnerUuid();
        return au != null && au.equals(bu);
    }

    private static float resolveSpeciesWeight(PetComponent a, PetComponent b, ContagionConfig cfg) {
        // Same species descriptor: weight = 1.0
        boolean sameSpecies = false;
        try {
            sameSpecies = a.getSpeciesDescriptor() != null
                && a.getSpeciesDescriptor().equals(b.getSpeciesDescriptor());
        } catch (Throwable ignored) {}

        if (sameSpecies) return 1.0f;

        // If both are social_affiliative, treat nearly like same species
        boolean aAff = false;
        boolean bAff = false;
        try {
            aAff = a.hasSpeciesTag(PetsplusEntityTypeTags.SOCIAL_AFFILIATIVE);
            bAff = b.hasSpeciesTag(PetsplusEntityTypeTags.SOCIAL_AFFILIATIVE);
        } catch (Throwable ignored) {}

        if (aAff && bAff) return Math.max(0.95f, cfg.defaultCrossSpeciesWeight);

        // Default cross-species weight
        return MathHelper.clamp(cfg.defaultCrossSpeciesWeight, 0.5f, 1.25f);
    }

    private static float telemetryScaleFor(PetComponent c) {
        Telemetry t = TELEMETRY.get(c);
        if (t == null) return 1.0f;
        float recent = t.recentAvg;
        if (recent <= TELEMETRY_TARGET_AVG) return 1.0f;
        // Scale down progressively when above target
        // Example: at 0.12 vs 0.08 target, return ~0.8
        float over = MathHelper.clamp((recent - TELEMETRY_TARGET_AVG) / Math.max(0.01f, TELEMETRY_TARGET_AVG), 0f, 1.5f);
        float scale = 1.0f - 0.25f * over;
        return MathHelper.clamp(scale, 0.6f, 1.0f);
    }

    private static void maybeApplyGossipNudge(PetComponent component, ContagionConfig cfg, long now) {
        // Gate via tag and optional owner proximity feel (already represented in bond/distance)
        boolean socialAff = false;
        try {
            socialAff = component.hasSpeciesTag(PetsplusEntityTypeTags.SOCIAL_AFFILIATIVE);
        } catch (Throwable ignored) {}

        if (!socialAff) return; // keep it subtle and species-appropriate

        // Per-component micro cooldown to avoid frequent nudges (debounce)
        long lastTick = component.getStateData("gossip_last_tick", Long.class, 0L);
        if (now - lastTick < 120L) {
            return;
        }
        component.setStateData("gossip_last_tick", now);

        // Pick micro influence based on current mood context
        PetComponent.Mood dominant = component.getDominantMood();
        float afraid = component.getMoodStrength(PetComponent.Mood.AFRAID);
        float restless = component.getMoodStrength(PetComponent.Mood.RESTLESS);
        float bonded = component.getMoodStrength(PetComponent.Mood.BONDED);
        float calm = component.getMoodStrength(PetComponent.Mood.CALM);

        float micro = MathHelper.clamp((cfg.gossipMin + cfg.gossipMax) * 0.5f, 0.0005f, 0.02f);

        if (afraid >= 0.25f || restless >= 0.35f || dominant == PetComponent.Mood.RESTLESS || dominant == PetComponent.Mood.AFRAID) {
            // Soothing counter or resonance based on current state; keep it tiny
            component.addContagionShare(PetComponent.Emotion.RELIEF, micro * 0.5f);
            // Tone down contradictory ANGST nudge when RELIEF is applied (stability clamp)
            component.addContagionShare(PetComponent.Emotion.ANGST, micro * 0.15f);
            return;
        }

        if (bonded >= 0.3f || calm >= 0.4f || dominant == PetComponent.Mood.CALM || dominant == PetComponent.Mood.BONDED) {
            component.addContagionShare(PetComponent.Emotion.UBUNTU, micro);
            component.addContagionShare(PetComponent.Emotion.CONTENT, micro * 0.6f);
            return;
        }

        // Neutral fallback
        component.addContagionShare(PetComponent.Emotion.UBUNTU, micro * 0.6f);
    }

    // ------------------------------------------------------------------------------------------------

    private static final class NeighborSample {
        final PetSwarmIndex.SwarmEntry entry;
        final double distSq;

        NeighborSample(PetSwarmIndex.SwarmEntry entry, double distSq) {
            this.entry = entry;
            this.distSq = distSq;
        }

        Vec3d pos() {
            return new Vec3d(entry.x(), entry.y(), entry.z());
        }
    }

    private static final class Telemetry {
        float recentAvg = 0f;

        void update(float appliedThisCall) {
            recentAvg = MathHelper.lerp(TELEMETRY_ALPHA, recentAvg, Math.max(0f, appliedThisCall));
        }
    }

    private record ContagionConfig(boolean enabled,
                                   double radius,
                                   int cadenceTicks,
                                   int budgetPerTick,
                                   int maxNeighbors,
                                   float baseInfluence,
                                   float crossOwnerMultiplier,
                                   float gossipMin,
                                   float gossipMax,
                                   float defaultCrossSpeciesWeight) {

        static ContagionConfig read() {
            MoodEngineConfig cfg = MoodEngineConfig.get();
            // Safe fallback defaults when section is absent or partially defined
            var root = cfg.getRoot();
            var contagion = root != null && root.has("contagion") && root.get("contagion").isJsonObject()
                ? root.getAsJsonObject("contagion")
                : null;

            boolean enabled = getBool(contagion, "enabled", true);
            double radius = getDouble(contagion, "radius", DEFAULT_RADIUS);
            int cadence = getInt(contagion, "cadence_ticks", 60);
            int budget = getInt(contagion, "budget_per_tick", 16);
            int maxN = getInt(contagion, "max_neighbors", DEFAULT_MAX_NEIGHBORS);
            float base = (float) getDouble(contagion, "base_influence", DEFAULT_BASE_INFLUENCE);
            float cross = (float) getDouble(contagion, "cross_owner_multiplier", DEFAULT_CROSS_OWNER_MULTIPLIER);
            float gMin = (float) getDouble(contagion, "gossip_min", DEFAULT_GOSSIP_MIN);
            float gMax = (float) getDouble(contagion, "gossip_max", DEFAULT_GOSSIP_MAX);

            float crossSpecies = (float) getDouble(contagion, "default_cross_species_weight", 0.85d);

            // Sanitize
            radius = Math.max(1.0d, radius);
            cadence = Math.max(10, cadence);
            budget = Math.max(1, budget);
            maxN = Math.max(1, maxN);
            base = MathHelper.clamp(base, 0.0005f, 0.05f);
            cross = MathHelper.clamp(cross, 0.05f, 1.0f);
            gMin = MathHelper.clamp(gMin, 0f, 0.05f);
            gMax = MathHelper.clamp(gMax, gMin, 0.05f);
            crossSpecies = MathHelper.clamp(crossSpecies, 0.5f, 1.25f);

            return new ContagionConfig(enabled, radius, cadence, budget, maxN, base, cross, gMin, gMax, crossSpecies);
        }

        private static boolean getBool(@Nullable com.google.gson.JsonObject obj, String key, boolean def) {
            if (obj == null) return def;
            var el = obj.get(key);
            return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) ? el.getAsBoolean() : def;
        }

        private static int getInt(@Nullable com.google.gson.JsonObject obj, String key, int def) {
            if (obj == null) return def;
            var el = obj.get(key);
            return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) ? el.getAsInt() : def;
        }

        private static double getDouble(@Nullable com.google.gson.JsonObject obj, String key, double def) {
            if (obj == null) return def;
            var el = obj.get(key);
            return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) ? el.getAsDouble() : def;
        }
    }
}