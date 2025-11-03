package woflo.petsplus.state.morality;

import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-pet morality tracker and behavioral influence system.
 * 
 * Accumulates owner "dark deed" telemetry and adjusts the pet's behavioral axes
 * (aggression, empathy, courage, social, resource attitudes) to create emergent
 * personality drift based on experiential history.
 * 
 * Triggers malevolence emotion when either:
 * 1. Vice score exceeds configured threshold, OR
 * 2. Identity dissonance (persona drift from Nature baseline) exceeds ~35%
 * 
 * Personality axes decay toward 0.5 (neutral) over time with same retention
 * mechanics as the legacy virtue/vice system.
 */
public final class MalevolenceLedger {
    private static final long DEFAULT_SPREE_WINDOW = MalevolenceRules.SpreeSettings.DEFAULT.windowTicks();

    private final PetComponent parent;

    private final Map<Identifier, MoralityAspectState> viceStates = new LinkedHashMap<>();
    private final Map<Identifier, MoralityAspectState> virtueStates = new LinkedHashMap<>();
    private MoralityPersona persona;
    @Nullable
    private Identifier dominantVice;

    private float score;
    private int spreeCount;
    private long spreeAnchorTick = Long.MIN_VALUE;
    private long lastDeedTick = Long.MIN_VALUE;
    private long lastTriggerTick = Long.MIN_VALUE;
    private long lastDecayTick = Long.MIN_VALUE;
    private float disharmonyStrength;
    private boolean active;
    private long lastContextTick = Long.MIN_VALUE;
    @Nullable
    private DarkDeedFingerprint lastContextFingerprint;

    public MalevolenceLedger(PetComponent parent) {
        this.parent = parent;
        loadSnapshotFromStateStore();
    }

    private void loadSnapshotFromStateStore() {
        float legacyScore = sanitizeNonNegative(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SCORE, Float.class, 0f));
        this.spreeCount = Math.max(0, parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_COUNT, Integer.class, 0));
        this.spreeAnchorTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_ANCHOR_TICK, Long.class, Long.MIN_VALUE);
        this.lastDeedTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DEED_TICK, Long.class, Long.MIN_VALUE);
        this.lastTriggerTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_TRIGGER_TICK, Long.class, Long.MIN_VALUE);
        long storedDecayTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DECAY_TICK, Long.class, Long.MIN_VALUE);
        if (storedDecayTick == Long.MIN_VALUE) {
            this.lastDecayTick = lastDeedTick;
        } else if (lastDeedTick == Long.MIN_VALUE) {
            this.lastDecayTick = storedDecayTick;
        } else {
            this.lastDecayTick = Math.max(storedDecayTick, lastDeedTick);
        }
        this.disharmonyStrength = sanitizeNonNegative(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_DISHARMONY, Float.class, 0f));
        this.active = Boolean.TRUE.equals(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_ACTIVE, Boolean.class, Boolean.FALSE));
        this.lastContextTick = Long.MIN_VALUE;
        this.lastContextFingerprint = null;

        viceStates.clear();
        virtueStates.clear();
        decodeAspectStates(castList(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_VICE_STATE, List.class)), viceStates);
        decodeAspectStates(castList(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_VIRTUE_STATE, List.class)), virtueStates);
        persona = decodePersona(castList(parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, List.class)));
        if (persona == null) {
            persona = MoralityPersona.seeded(resolvePersonaSeed());
        }
        if (viceStates.isEmpty() && legacyScore > 0f) {
            Identifier defaultVice = MoralityAspectRegistry.defaultViceAspectId();
            MoralityAspectState state = new MoralityAspectState();
            state.setValue(legacyScore);
            state.setSpree(spreeCount, spreeAnchorTick);
            viceStates.put(defaultVice, state);
        }
        ensureBaselineStates();
        score = aggregateScore();
        dominantVice = findDominantVice();
    }

    private static float sanitizeNonNegative(float candidate) {
        if (!Float.isFinite(candidate) || candidate < 0f) {
            return 0f;
        }
        return candidate;
    }

    public synchronized void onStateDataRehydrated(long now) {
        loadSnapshotFromStateStore();
        disharmonyStrength = Math.max(0f, disharmonyStrength);
        if (lastDecayTick != Long.MIN_VALUE && now > 0L && lastDecayTick > now) {
            lastDecayTick = now;
        }
        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules != null && rules != MalevolenceRules.EMPTY) {
            float maxStrength = Math.max(0f, rules.disharmonySettings().maxStrength());
            if (maxStrength > 0f) {
                disharmonyStrength = MathHelper.clamp(disharmonyStrength, 0f, maxStrength);
            } else {
                disharmonyStrength = 0f;
            }
        }
        PetComponent.HarmonyState current = parent.getHarmonyState();
        PetComponent.HarmonyState overlay = overlayHarmonyState(current, now);
        if (overlay != current) {
            parent.applyHarmonyState(overlay);
        }
    }

    public synchronized void onStateDataCleared(long now) {
        score = 0f;
        spreeCount = 0;
        spreeAnchorTick = Long.MIN_VALUE;
        lastDeedTick = Long.MIN_VALUE;
        lastTriggerTick = Long.MIN_VALUE;
        lastDecayTick = Long.MIN_VALUE;
        disharmonyStrength = 0f;
        active = false;
        viceStates.clear();
        virtueStates.clear();
        persona = null;
        dominantVice = null;
        lastContextTick = Long.MIN_VALUE;
        lastContextFingerprint = null;
        PetComponent.HarmonyState current = parent.getHarmonyState();
        PetComponent.HarmonyState overlay = overlayHarmonyState(current, now);
        if (overlay != current) {
            parent.applyHarmonyState(overlay);
        }
    }

    public synchronized TriggerOutcome recordDarkDeed(DarkDeedContext context, long now) {
        UUID ownerUuid = parent.getOwnerUuid();
        if (ownerUuid == null) {
            return TriggerOutcome.none();
        }

        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules == null || rules == MalevolenceRules.EMPTY) {
            return TriggerOutcome.none();
        }
        MalevolenceRules.Thresholds thresholds = rules.thresholds();

        DarkDeedFingerprint fingerprint = DarkDeedFingerprint.from(context);
        if (now == lastContextTick
            && lastContextFingerprint != null
            && lastContextFingerprint.matches(fingerprint)) {
            return TriggerOutcome.none();
        }

        decayAspects(now, thresholds);
        refreshSpreeWindow(now, rules.spreeSettings());
        SpreeSnapshot spreeSnapshot = computeSpreeSnapshot(now, rules.spreeSettings());

        Set<String> resolvedTags = rules.resolveTags(context.victimType(), context.registryTags(), context.dynamicTags());
        MalevolenceRules.TagEvaluation tagEvaluation = rules.evaluateDeedTags(resolvedTags);
        float baseWeight = rules.baseWeight(context.victimType(), tagEvaluation);
        if (baseWeight <= 0f) {
            lastContextTick = now;
            lastContextFingerprint = fingerprint;
            maybeClear(rules, now, true);
            persistState();
            return TriggerOutcome.none();
        }

        RelationshipModule relationshipModule = parent.getRelationshipModule();
        RelationshipProfile relationshipProfile = null;
        if (relationshipModule != null && context.victimUuid() != null) {
            relationshipProfile = relationshipModule.getRelationship(context.victimUuid());
        }
        RelationshipType relationshipType = relationshipProfile != null
            ? relationshipProfile.getType()
            : context.relationshipHint();

        float relationshipMultiplier = rules.relationshipMultiplier(relationshipType, tagEvaluation);
        MalevolenceRules.InteractionVector interactionVector = MalevolenceRules.InteractionVector.of(context.interactionVector());
        if (rules.forgivenessSettings().shouldForgive(relationshipProfile, interactionVector,
            context.friendlyFireSeverity(), spreeSnapshot.count())) {
            if (context.friendlyFireSeverity() > 0f) {
                applySpreeSnapshot(spreeSnapshot);
            }
            lastContextTick = now;
            lastContextFingerprint = fingerprint;
            maybeClear(rules, now, true);
            persistState();
            return TriggerOutcome.none();
        }
        float telemetryMultiplier = rules.telemetryMultiplier(relationshipProfile, interactionVector,
            context.friendlyFireSeverity(), tagEvaluation);
        float spreeMultiplier = rules.spreeMultiplier(spreeSnapshot.count(), context.lowHealthFinish(), tagEvaluation);

        float addedScore = baseWeight * relationshipMultiplier * telemetryMultiplier * spreeMultiplier;
        if (addedScore <= 0f || Float.isNaN(addedScore) || Float.isInfinite(addedScore)) {
            lastContextTick = now;
            lastContextFingerprint = fingerprint;
            maybeClear(rules, now, true);
            persistState();
            return TriggerOutcome.none();
        }

        MalevolenceRules.AspectContribution contribution = rules.aspectContribution(context.victimType(), tagEvaluation);
        Map<Identifier, Float> viceWeights = contribution.viceWeights();
        if (viceWeights.isEmpty()) {
            viceWeights = new LinkedHashMap<>(2);
            viceWeights.put(MoralityAspectRegistry.defaultViceAspectId(), 1f);
        } else {
            // Only copy if we need to modify
            viceWeights = new LinkedHashMap<>(viceWeights);
        }
        Map<Identifier, MalevolenceRules.RequirementRange> virtueRequirements = contribution.virtueRequirements();
        List<String> gateFailures = null;
        if (!virtueRequirements.isEmpty()) {
            gateFailures = new ArrayList<>();
            for (Map.Entry<Identifier, MalevolenceRules.RequirementRange> entry : virtueRequirements.entrySet()) {
                Identifier virtueId = entry.getKey();
                MalevolenceRules.RequirementRange requirement = entry.getValue();
                float level = getVirtueLevel(virtueId);
                if (!requirement.isSatisfied(level)) {
                    gateFailures.add(virtueId.toString());
                }
            }
        }

        if (gateFailures == null || gateFailures.isEmpty()) {
            applySpreeSnapshot(spreeSnapshot);
            applyViceAdjustments(viceWeights, addedScore, now, rules.spreeSettings(), thresholds);
        } else {
            for (Map.Entry<Identifier, Float> entry : viceWeights.entrySet()) {
                Identifier id = entry.getKey();
                float weight = entry.getValue();
                if (!Float.isFinite(weight) || weight == 0f) {
                    continue;
                }
                float impressionability = resolveImpressionability(id, thresholds);
                float suppressedDelta = addedScore * weight * impressionability;
                recordSuppressedCharge(id, suppressedDelta, now);
            }
        }

        applyVirtueAdjustments(contribution.virtueWeights(), addedScore, now, rules.spreeSettings(), thresholds);

        lastDeedTick = now;
        lastContextTick = now;
        lastContextFingerprint = fingerprint;
        lastDecayTick = now;
        score = aggregateScore();
        dominantVice = findDominantVice();
        
        // NEW: Adjust persona axes based on deed context
        adjustPersonaFromDeed(context, addedScore, now);

        Map<Identifier, Float> virtueSnapshot = snapshotVirtues();

        ThresholdEvaluation evaluation = gateFailures.isEmpty()
            ? evaluateThreshold(thresholds, now)
            : ThresholdEvaluation.suppressedResult(dominantVice, String.join(", ", gateFailures));
        DisharmonyEvaluation disharmonyEvaluation = updateDisharmony(rules, evaluation, now);
        persistState();
        return TriggerOutcome.of(evaluation, disharmonyEvaluation, virtueSnapshot);
    }

    private void decayAspects(long now, MalevolenceRules.Thresholds thresholds) {
        // Morality decay disabled - aspects only change through deeds and events
        score = aggregateScore();
        dominantVice = findDominantVice();
        lastDecayTick = now;
    }

    private void refreshSpreeWindow(long now, MalevolenceRules.SpreeSettings spreeSettings) {
        if (spreeSettings == null) {
            return;
        }
        if (spreeAnchorTick != Long.MIN_VALUE && now - spreeAnchorTick > spreeSettings.windowTicks()) {
            spreeCount = 0;
            spreeAnchorTick = Long.MIN_VALUE;
        }
    }

    private SpreeSnapshot computeSpreeSnapshot(long now, MalevolenceRules.SpreeSettings spreeSettings) {
        int candidate;
        long anchor;
        if (spreeSettings == null) {
            candidate = Math.min(Integer.MAX_VALUE - 1, spreeCount + 1);
            if (candidate <= 0) {
                candidate = 1;
            }
            anchor = spreeAnchorTick == Long.MIN_VALUE ? now : spreeAnchorTick;
            return new SpreeSnapshot(candidate, anchor);
        }
        boolean reset = spreeAnchorTick == Long.MIN_VALUE || now - spreeAnchorTick > spreeSettings.windowTicks();
        anchor = reset ? now : spreeAnchorTick;
        int base = reset ? 0 : spreeCount;
        candidate = Math.min(Integer.MAX_VALUE - 1, base + 1);
        if (candidate <= 0) {
            candidate = 1;
        }
        return new SpreeSnapshot(candidate, anchor);
    }

    private void applySpreeSnapshot(SpreeSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        spreeCount = snapshot.count();
        spreeAnchorTick = snapshot.anchor();
    }

    private long resolveSpreeWindow(@Nullable MalevolenceRules.SpreeSettings spreeSettings) {
        return spreeSettings != null ? spreeSettings.windowTicks() : DEFAULT_SPREE_WINDOW;
    }

    private float resolveImpressionability(Identifier id, MalevolenceRules.Thresholds thresholds) {
        MoralityAspectDefinition definition = MoralityAspectRegistry.get(id);
        return definition != null ? definition.impressionability() : thresholds.defaultImpressionability();
    }

    private float applyViceDelta(Identifier id,
                                 float delta,
                                 long now,
                                 long spreeWindow,
                                 float experienceScale) {
        if (!Float.isFinite(delta) || delta == 0f) {
            return 0f;
        }
        MoralityAspectState state = ensureViceState(id);
        float adjusted = delta;
        if (delta > 0f && persona != null) {
            adjusted = persona.adjustViceDelta(id, delta, state);
        }
        state.applyDelta(adjusted, now, spreeWindow);
        if (persona != null && adjusted > 0f && experienceScale > 0f) {
            persona.recordViceExperience(adjusted * experienceScale, now);
        }
        return adjusted;
    }

    private float applyVirtueDelta(Identifier id,
                                   float delta,
                                   long now,
                                   long spreeWindow,
                                   float positiveExperienceScale,
                                   float negativeExperienceScale) {
        if (!Float.isFinite(delta) || delta == 0f) {
            return 0f;
        }
        MoralityAspectState state = ensureVirtueState(id);
        float adjusted = persona != null ? persona.adjustVirtueDelta(delta) : delta;
        state.applyDelta(adjusted, now, spreeWindow);
        if (persona != null) {
            if (adjusted > 0f && positiveExperienceScale > 0f) {
                persona.recordVirtueExperience(adjusted * positiveExperienceScale, now);
            } else if (adjusted < 0f && negativeExperienceScale > 0f) {
                persona.recordViceExperience(Math.abs(adjusted) * negativeExperienceScale, now);
            }
        }
        return adjusted;
    }

    private void recordSuppressedCharge(Identifier id,
                                        float suppressedDelta,
                                        long now) {
        if (!Float.isFinite(suppressedDelta) || suppressedDelta <= 0f) {
            return;
        }
        MoralityAspectState state = ensureViceState(id);
        state.recordSuppressed(suppressedDelta, now);
    }

    private void applyViceAdjustments(Map<Identifier, Float> viceWeights,
                                      float severity,
                                      long now,
                                      MalevolenceRules.SpreeSettings spreeSettings,
                                      MalevolenceRules.Thresholds thresholds) {
        if (viceWeights.isEmpty()) {
            return;
        }
        long spreeWindow = resolveSpreeWindow(spreeSettings);
        for (Map.Entry<Identifier, Float> entry : viceWeights.entrySet()) {
            Identifier id = entry.getKey();
            float weight = entry.getValue();
            if (!Float.isFinite(weight) || weight == 0f) {
                continue;
            }
            float impressionability = resolveImpressionability(id, thresholds);
            float delta = severity * weight * impressionability;
            float applied = applyViceDelta(id, delta, now, spreeWindow, 1f);
            applySynergy(id, applied, now, spreeWindow, thresholds);
        }
        score = aggregateScore();
        dominantVice = findDominantVice();
    }

    private void applyVirtueAdjustments(Map<Identifier, Float> virtueWeights,
                                        float severity,
                                        long now,
                                        MalevolenceRules.SpreeSettings spreeSettings,
                                        MalevolenceRules.Thresholds thresholds) {
        if (virtueWeights.isEmpty()) {
            return;
        }
        long spreeWindow = resolveSpreeWindow(spreeSettings);
        for (Map.Entry<Identifier, Float> entry : virtueWeights.entrySet()) {
            Identifier id = entry.getKey();
            float weight = entry.getValue();
            if (!Float.isFinite(weight) || weight == 0f) {
                continue;
            }
            float impressionability = resolveImpressionability(id, thresholds);
            float delta = severity * weight * impressionability;
            applyVirtueDelta(id, delta, now, spreeWindow, 1f, 0.5f);
        }
    }

    private void applySynergy(Identifier sourceId,
                              float delta,
                              long now,
                              long spreeWindow,
                              MalevolenceRules.Thresholds thresholds) {
        MoralityAspectDefinition definition = MoralityAspectRegistry.get(sourceId);
        if (definition == null || definition.synergy().isEmpty() || delta == 0f) {
            return;
        }
        for (Map.Entry<Identifier, Float> entry : definition.synergy().entrySet()) {
            Identifier targetId = entry.getKey();
            float factor = entry.getValue();
            if (!Float.isFinite(factor) || factor == 0f) {
                continue;
            }
            float impressionability = resolveImpressionability(targetId, thresholds);
            float contribution = delta * factor * impressionability;
            if (MoralityAspectRegistry.isVice(targetId)) {
                applyViceDelta(targetId, contribution, now, spreeWindow, 0.5f);
            } else {
                applyVirtueDelta(targetId, contribution, now, spreeWindow, 0.5f, 0f);
            }
        }
    }
    
    /**
     * Adjust persona axes based on deed context.
     * This is the integration point that makes deeds affect personality directly.
     * Tags come from CombatEventHandler, EmotionsEventHandler, etc.
     */
    /**
     * Derives DeedKind from deed context.
     * Maps victim type, tags, and context to structured deed classification.
     * NOTE: Only matches deeds that are actually triggered in the system.
     */
    private static DeedKind deriveDeedKind(DarkDeedContext context) {
        Set<String> tags = context.dynamicTags();
        
        // Check for specific deed patterns (based on actual tags from CombatEventHandler/EmotionsEventHandler)
        if (tags.contains("passive_baby")) return DeedKind.KILL_BABY_MOB;
        if (tags.contains("owned_pet")) return DeedKind.KILL_OWN_PET;
        if (tags.contains("villager")) return DeedKind.KILL_VILLAGER;
        if (tags.contains("passive_species")) return DeedKind.KILL_PASSIVE_MOB;
        if (tags.contains("friendly_fire")) return DeedKind.FRIENDLY_FIRE;
        if (tags.contains("team_betrayal")) return DeedKind.TEAM_BETRAYAL;
        if (context.lowHealthFinish()) return DeedKind.CLUTCH_FINISH;
        if (tags.contains("boss_target")) return DeedKind.KILL_BOSS;
        if (tags.contains("hostile_target")) return DeedKind.KILL_HOSTILE;
        
        // Default to passive mob kill if no other match
        return DeedKind.KILL_PASSIVE_MOB;
    }

    private void adjustPersonaFromDeed(DarkDeedContext context, float severity, long now) {
        if (persona == null || severity <= 0f) {
            return;
        }
        
        DeedKind deed = deriveDeedKind(context);
        float scale = Math.min(1.0f, severity * 0.1f);
        
        // Apply structured persona adjustments based on deed kind
        switch (deed) {
            case KILL_BABY_MOB:
                persona.adjustEmpathy(-0.35f * scale);
                persona.adjustAggression(0.25f * scale);
                break;
            case KILL_OWN_PET:
                persona.adjustEmpathy(-0.40f * scale);
                persona.adjustAggression(0.20f * scale);
                persona.recordViceExperience(Math.abs(severity) * scale, now);
                break;
            case KILL_VILLAGER:
                persona.adjustEmpathy(-0.30f * scale);
                break;
            case KILL_PASSIVE_MOB:
                persona.adjustEmpathy(-0.25f * scale);
                persona.adjustAggression(0.15f * scale);
                break;
            case FRIENDLY_FIRE:
                persona.adjustCourage(-0.20f * scale);
                persona.recordViceExperience(Math.abs(severity) * scale, now);
                break;
            case TEAM_BETRAYAL:
                persona.adjustCourage(-0.25f * scale);
                persona.adjustEmpathy(-0.15f * scale);
                persona.recordViceExperience(Math.abs(severity) * scale, now);
                break;
            case CLUTCH_FINISH:
                persona.adjustAggression(0.20f * scale);
                break;
            case KILL_SPREE:
                // Not used in current system, reserved for future
                break;
            case PROTECT_ALLY:
                // Not used in current system, reserved for future virtue deeds
                break;
            case GUARD_DUTY:
                // Not used in current system, reserved for future virtue deeds
                break;
            case KILL_HOSTILE:
                persona.adjustCourage(0.10f * scale);
                break;
            case KILL_BOSS:
                persona.adjustCourage(0.15f * scale);
                persona.adjustAggression(0.05f * scale);
                break;
        }
    }

    private ThresholdEvaluation evaluateThreshold(MalevolenceRules.Thresholds thresholds, long now) {
        score = aggregateScore();
        Identifier viceId = dominantVice;
        boolean cooledDown = lastTriggerTick == Long.MIN_VALUE || now - lastTriggerTick >= thresholds.cooldownTicks();
        
        // NEW: Use identity dissonance (persona drift) as trigger in addition to vice score
        float identityDissonance = 0f;
        if (persona != null) {
            PersonaBehavioralProfile profile = new PersonaBehavioralProfile(persona);
            identityDissonance = profile.calculateIdentityDissonance();
        }
        
        // Trigger if EITHER high vice score OR significant personality drift
        boolean shouldTrigger = (score >= thresholds.triggerScore() || identityDissonance > 0.35f) && cooledDown;
        
        if (shouldTrigger) {
            float intensity = thresholds.intensityForScore(score);
            // Boost intensity if personality drift is extreme
            if (identityDissonance > 0.4f) {
                intensity = Math.max(intensity, 0.6f);
            }
            lastTriggerTick = now;
            active = true;
            return ThresholdEvaluation.triggeredResult(intensity, viceId);
        }
        
        boolean clearable = active && score <= thresholds.remissionScore() && identityDissonance < 0.25f;
        if (clearable) {
            active = false;
            return ThresholdEvaluation.clearedResult(viceId);
        }
        return ThresholdEvaluation.none(viceId);
    }


    private DisharmonyEvaluation updateDisharmony(MalevolenceRules rules,
                                                  ThresholdEvaluation evaluation, long now) {
        MalevolenceRules.DisharmonySettings settings = rules.disharmonySettings();
        float previous = disharmonyStrength;
        if (evaluation.suppressed()) {
            applyHarmony(now);
            return new DisharmonyEvaluation(previous, disharmonyStrength, false, evaluation.viceId());
        }
        if (evaluation.triggered()) {
            float candidate = settings.resolveStrength(evaluation.intensity());
            disharmonyStrength = Math.max(disharmonyStrength, candidate);
        } else if (evaluation.cleared()) {
            disharmonyStrength = 0f;
        }
        if (!evaluation.triggered() && !evaluation.cleared() && !active) {
            if (disharmonyStrength > 0f && disharmonyStrength <= settings.remissionFloor()) {
                disharmonyStrength = 0f;
            }
        }
        applyHarmony(now);
        return new DisharmonyEvaluation(previous, disharmonyStrength, evaluation.cleared(), evaluation.viceId());
    }

    private void maybeClear(MalevolenceRules rules, long now, boolean decayFirst) {
        if (!active) {
            return;
        }
        MalevolenceRules.Thresholds thresholds = rules.thresholds();
        if (decayFirst) {
            decayAspects(now, thresholds);
        }
        score = aggregateScore();
        dominantVice = findDominantVice();
        if (score <= thresholds.remissionScore()) {
            active = false;
            disharmonyStrength = 0f;
            lastDecayTick = now;
            applyHarmony(now);
        }
    }

    private void applyHarmony(long tick) {
        PetComponent.HarmonyState current = parent.getHarmonyState();
        PetComponent.HarmonyState overlay = overlayHarmonyState(current, tick);
        if (overlay != current) {
            parent.applyHarmonyState(overlay);
        }
    }

    public synchronized void onWorldTimeSegmentTick(long now) {
        // Morality system is event-driven only - no per-tick passive updates
        // State changes occur through deeds, not time passage
    }

    public synchronized PetComponent.HarmonyState overlayHarmonyState(PetComponent.HarmonyState incoming, long now) {
        if (incoming == null) {
            return PetComponent.HarmonyState.empty();
        }
        UUID ownerUuid = parent.getOwnerUuid();
        if (ownerUuid == null) {
            return incoming;
        }
        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules == null || rules == MalevolenceRules.EMPTY) {
            return incoming;
        }
        
        // Derive emotion biases from persona
        Map<PetComponent.Emotion, Float> emotionBiases = deriveEmotionBiasesFromPersona();
        
        Map<UUID, PetComponent.HarmonyCompatibility> compat = incoming.compatibilities();
        PetComponent.HarmonyCompatibility existing = compat.get(ownerUuid);
        float clampedStrength = MathHelper.clamp(disharmonyStrength, 0f, rules.disharmonySettings().maxStrength());
        if (clampedStrength <= 0f) {
            if (existing == null || existing.disharmonyStrength() <= 0f) {
                // Return incoming with updated emotion biases
                return new PetComponent.HarmonyState(
                    incoming.harmonySetIds(),
                    incoming.disharmonySetIds(),
                    incoming.moodMultiplier(),
                    incoming.contagionMultiplier(),
                    incoming.volatilityMultiplier(),
                    incoming.resilienceMultiplier(),
                    incoming.guardMultiplier(),
                    incoming.harmonyStrength(),
                    incoming.disharmonyStrength(),
                    emotionBiases,
                    incoming.compatibilities(),
                    Math.max(incoming.lastUpdatedTick(), now)
                );
            }
            Map<UUID, PetComponent.HarmonyCompatibility> copy = new LinkedHashMap<>(compat);
            copy.remove(ownerUuid);
            List<Identifier> disharmonySets = new ArrayList<>(incoming.disharmonySetIds());
            Identifier marker = rules.disharmonySetId();
            boolean removedMarker = disharmonySets.remove(marker);
            List<Identifier> sanitizedDisharmony = removedMarker
                ? (disharmonySets.isEmpty() ? List.of() : List.copyOf(disharmonySets))
                : incoming.disharmonySetIds();
            float disharmonyTotal = 0f;
            if (!copy.isEmpty()) {
                for (PetComponent.HarmonyCompatibility value : copy.values()) {
                    if (value != null) {
                        disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
                    }
                }
            }
            if (!sanitizedDisharmony.isEmpty()) {
                disharmonyTotal = Math.max(disharmonyTotal, incoming.disharmonyStrength());
            }
            return new PetComponent.HarmonyState(
                incoming.harmonySetIds(),
                sanitizedDisharmony,
                incoming.moodMultiplier(),
                incoming.contagionMultiplier(),
                incoming.volatilityMultiplier(),
                incoming.resilienceMultiplier(),
                incoming.guardMultiplier(),
                incoming.harmonyStrength(),
                disharmonyTotal,
                emotionBiases,
                copy,
                Math.max(incoming.lastUpdatedTick(), now)
            );
        }
        float maxStrength = rules.disharmonySettings().maxStrength();
        float resolvedStrength = MathHelper.clamp(existing != null
            ? Math.max(existing.disharmonyStrength(), clampedStrength)
            : clampedStrength, 0f, maxStrength);
        Identifier marker = rules.disharmonySetId();
        boolean compatSatisfied = existing != null
            && existing.disharmonyStrength() >= resolvedStrength - 0.0001f
            && existing.disharmonySetIds().contains(marker);
        boolean globalSatisfied = incoming.disharmonyStrength() >= resolvedStrength - 0.0001f
            && incoming.disharmonySetIds().contains(marker);
        if (compatSatisfied && globalSatisfied) {
            // Still update emotion biases even if other state is satisfied
            return new PetComponent.HarmonyState(
                incoming.harmonySetIds(),
                incoming.disharmonySetIds(),
                incoming.moodMultiplier(),
                incoming.contagionMultiplier(),
                incoming.volatilityMultiplier(),
                incoming.resilienceMultiplier(),
                incoming.guardMultiplier(),
                incoming.harmonyStrength(),
                incoming.disharmonyStrength(),
                emotionBiases,
                incoming.compatibilities(),
                Math.max(incoming.lastUpdatedTick(), now)
            );
        }
        Map<UUID, PetComponent.HarmonyCompatibility> copy = new LinkedHashMap<>(compat);
        List<Identifier> harmonySets = existing != null ? existing.harmonySetIds() : List.of();
        List<Identifier> disharmonySets = new ArrayList<>(existing != null ? existing.disharmonySetIds() : List.of());
        if (!disharmonySets.contains(marker)) {
            disharmonySets.add(marker);
        }
        PetComponent.HarmonyCompatibility updatedCompat = new PetComponent.HarmonyCompatibility(
            ownerUuid,
            harmonySets,
            disharmonySets,
            existing != null ? existing.harmonyStrength() : 0f,
            resolvedStrength,
            Math.max(incoming.lastUpdatedTick(), now)
        );
        copy.put(ownerUuid, updatedCompat);
        List<Identifier> globalSets = new ArrayList<>(incoming.disharmonySetIds());
        if (!globalSets.contains(marker)) {
            globalSets.add(marker);
        }
        float disharmonyTotal = Math.max(incoming.disharmonyStrength(), resolvedStrength);
        if (!copy.isEmpty()) {
            for (PetComponent.HarmonyCompatibility value : copy.values()) {
                if (value != null) {
                    disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
                }
            }
        }
        return new PetComponent.HarmonyState(
            incoming.harmonySetIds(),
            globalSets,
            incoming.moodMultiplier(),
            incoming.contagionMultiplier(),
            incoming.volatilityMultiplier(),
            incoming.resilienceMultiplier(),
            incoming.guardMultiplier(),
            incoming.harmonyStrength(),
            disharmonyTotal,
            emotionBiases,
            copy,
            Math.max(incoming.lastUpdatedTick(), now)
        );
    }

    private record DarkDeedFingerprint(
        @Nullable Identifier victimTypeId,
        @Nullable UUID victimUuid,
        Set<Identifier> registryTags,
        Set<String> dynamicTags,
        int friendlyFireKey,
        boolean lowHealthFinish,
        RelationshipType relationshipHint
    ) {
        private static final DarkDeedFingerprint EMPTY = new DarkDeedFingerprint(null, null, Set.of(), Set.of(), 0, false,
            RelationshipType.NEUTRAL);

        private static DarkDeedFingerprint from(DarkDeedContext context) {
            if (context == null) {
                return EMPTY;
            }
            Identifier victimTypeId = context.victimType() != null ? EntityType.getId(context.victimType()) : null;
            Set<Identifier> registryTags = context.registryTags().isEmpty() ? Set.of() : context.registryTags();
            Set<String> dynamicTags = context.dynamicTags().isEmpty() ? Set.of() : context.dynamicTags();
            int friendlyFireKey = MathHelper.floor(context.friendlyFireSeverity() * 100f);
            return new DarkDeedFingerprint(
                victimTypeId,
                context.victimUuid(),
                registryTags,
                dynamicTags,
                friendlyFireKey,
                context.lowHealthFinish(),
                context.relationshipHint()
            );
        }

        private boolean matches(DarkDeedFingerprint other) {
            if (other == null) {
                return false;
            }
            return Objects.equals(victimTypeId, other.victimTypeId)
                && Objects.equals(victimUuid, other.victimUuid)
                && Objects.equals(registryTags, other.registryTags)
                && Objects.equals(dynamicTags, other.dynamicTags)
                && friendlyFireKey == other.friendlyFireKey
                && lowHealthFinish == other.lowHealthFinish
                && relationshipHint == other.relationshipHint;
        }

        private boolean matches(DarkDeedContext context) {
            if (context == null) {
                return false;
            }
            return matches(from(context));
        }
    }

    private static List<?> castList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return null;
    }

    private void decodeAspectStates(@Nullable List<?> encoded, Map<Identifier, MoralityAspectState> into) {
        if (encoded == null) {
            return;
        }
        for (Object element : encoded) {
            if (!(element instanceof List<?> tuple) || tuple.isEmpty()) {
                continue;
            }
            Identifier id = identifierAt(tuple, 0);
            if (id == null) {
                continue;
            }
            MoralityAspectState state = new MoralityAspectState();
            state.setValue(floatAt(tuple, 1, 0f));
            state.setSpree(intAt(tuple, 2, 0), longAt(tuple, 3, Long.MIN_VALUE));
            long lastTick = longAt(tuple, 4, Long.MIN_VALUE);
            if (lastTick != Long.MIN_VALUE) {
                state.applyDelta(0f, lastTick, 0L);
            }
            state.setSuppression(floatAt(tuple, 5, 0f), longAt(tuple, 6, Long.MIN_VALUE));
            into.put(id, state);
        }
    }

    private List<Object> encodeAspectStates(Map<Identifier, MoralityAspectState> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> encoded = new ArrayList<>(source.size());
        for (Map.Entry<Identifier, MoralityAspectState> entry : source.entrySet()) {
            MoralityAspectState state = entry.getValue();
            if (state == null) {
                continue;
            }
            List<Object> tuple = new ArrayList<>(7);
            tuple.add(entry.getKey().toString());
            tuple.add(state.value());
            tuple.add(state.spreeCount());
            tuple.add(state.spreeAnchorTick());
            tuple.add(state.lastUpdatedTick());
            tuple.add(state.suppressedCharge());
            tuple.add(state.lastSuppressedTick());
            encoded.add(tuple);
        }
        return encoded;
    }

    private MoralityPersona decodePersona(@Nullable List<?> encoded) {
        if (encoded == null || encoded.size() < 3) {
            return null;
        }
        float susceptibility = floatAt(encoded, 0, 1f);
        float resilience = floatAt(encoded, 1, 1f);
        float trauma = floatAt(encoded, 2, 0f);
        MoralityPersona persona = new MoralityPersona(susceptibility, resilience, trauma);
        
        // NEW: Decode behavioral axes if present (indices 3-7)
        if (encoded.size() > 3) {
            persona.adjustAggression(floatAt(encoded, 3, 0.5f) - 0.5f);
            persona.adjustEmpathy(floatAt(encoded, 4, 0.5f) - 0.5f);
            persona.adjustCourage(floatAt(encoded, 5, 0.5f) - 0.5f);
            persona.adjustSocial(floatAt(encoded, 6, 0.5f) - 0.5f);
            persona.adjustResourceAttitude(floatAt(encoded, 7, 0.5f) - 0.5f);
        }
        
        return persona;
    }

    private List<Object> encodePersona(MoralityPersona persona) {
        if (persona == null) {
            return List.of();
        }
        return List.of(
            persona.viceSusceptibility(),
            persona.virtueResilience(),
            persona.traumaImprint(),
            persona.aggressionTendency(),
            persona.empathyLevel(),
            persona.courageBaseline(),
            persona.socialOrientation(),
            persona.resourceAttitude()
        );
    }

    private MoralityAspectState ensureViceState(Identifier id) {
        return viceStates.computeIfAbsent(id, key -> {
            MoralityAspectState state = new MoralityAspectState();
            MoralityAspectDefinition def = MoralityAspectRegistry.get(key);
            state.setValue(def != null ? def.baseline() : 0f);
            return state;
        });
    }

    private MoralityAspectState ensureVirtueState(Identifier id) {
        return virtueStates.computeIfAbsent(id, key -> {
            MoralityAspectState state = new MoralityAspectState();
            MoralityAspectDefinition def = MoralityAspectRegistry.get(key);
            state.setValue(def != null ? def.baseline() : 0.5f);
            return state;
        });
    }

    private void ensureBaselineStates() {
        for (MoralityAspectDefinition definition : MoralityAspectRegistry.all()) {
            Map<Identifier, MoralityAspectState> target = definition.isVice() ? viceStates : virtueStates;
            target.computeIfAbsent(definition.id(), id -> {
                MoralityAspectState state = new MoralityAspectState();
                state.setValue(definition.baseline());
                return state;
            });
        }
    }

    private float aggregateScore() {
        if (viceStates.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        for (MoralityAspectState state : viceStates.values()) {
            if (state != null) {
                total += Math.max(0f, state.value());
            }
        }
        return total;
    }

    @Nullable
    private Identifier findDominantVice() {
        if (viceStates.isEmpty()) {
            return null;
        }
        Identifier best = null;
        float value = 0f;
        for (Map.Entry<Identifier, MoralityAspectState> entry : viceStates.entrySet()) {
            MoralityAspectState state = entry.getValue();
            if (state == null) {
                continue;
            }
            float current = state.value();
            if (current > value + 0.0001f) {
                value = current;
                best = entry.getKey();
            }
        }
        return best;
    }

    private float getVirtueLevel(Identifier id) {
        MoralityAspectState state = virtueStates.get(id);
        if (state != null) {
            return state.value();
        }
        MoralityAspectDefinition def = MoralityAspectRegistry.get(id);
        return def != null ? def.baseline() : 0.5f;
    }

    private Map<Identifier, Float> snapshotVirtues() {
        if (virtueStates.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Identifier, Float> snapshot = new LinkedHashMap<>(virtueStates.size());
        for (Map.Entry<Identifier, MoralityAspectState> entry : virtueStates.entrySet()) {
            MoralityAspectState state = entry.getValue();
            if (state != null) {
                snapshot.put(entry.getKey(), state.value());
            }
        }
        return snapshot;
    }

    public synchronized MoralitySnapshot describe() {
        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        long spreeWindow = resolveSpreeWindow(rules != null ? rules.spreeSettings() : null);
        List<AspectSnapshot> viceHighlights = collectTopAspects(viceStates, 3);
        List<AspectSnapshot> virtueHighlights = collectTopAspects(virtueStates, 3);
        Identifier viceId = dominantVice;
        String viceName = viceId != null ? humanizeAspectIdentifier(viceId) : null;

        return new MoralitySnapshot(
            Math.max(0f, score),
            Math.max(0f, disharmonyStrength),
            active,
            Math.max(0, spreeCount),
            spreeWindow,
            spreeAnchorTick,
            lastDeedTick,
            lastTriggerTick,
            lastDecayTick,
            viceId,
            viceName,
            viceHighlights,
            virtueHighlights
        );
    }
    
    /**
     * Get the current behavioral profile derived from persona.
     * Can be used by AI systems to gate behaviors based on tags.
     */
    public synchronized PersonaBehavioralProfile getBehavioralProfile() {
        if (persona == null) {
            // Fallback to neutral persona if not initialized
            return new PersonaBehavioralProfile(new MoralityPersona(1f, 1f, 0f));
        }
        return new PersonaBehavioralProfile(persona);
    }

    /**
     * Get the underlying persona for direct access to behavioral axes.
     */
    public synchronized MoralityPersona getPersona() {
        return persona;
    }

    private List<AspectSnapshot> collectTopAspects(Map<Identifier, MoralityAspectState> source, int limit) {
        if (source.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Map.Entry<Identifier, MoralityAspectState>> entries = new ArrayList<>(source.entrySet());
        entries.removeIf(entry -> entry.getValue() == null || entry.getValue().value() <= 0f);
        if (entries.isEmpty()) {
            return List.of();
        }
        entries.sort((left, right) -> Float.compare(right.getValue().value(), left.getValue().value()));
        List<AspectSnapshot> highlights = new ArrayList<>(Math.min(limit, entries.size()));
        for (int index = 0; index < entries.size() && index < limit; index++) {
            Map.Entry<Identifier, MoralityAspectState> entry = entries.get(index);
            MoralityAspectState state = entry.getValue();
            highlights.add(new AspectSnapshot(
                entry.getKey(),
                humanizeAspectIdentifier(entry.getKey()),
                Math.max(0f, state.value()),
                Math.max(0, state.spreeCount()),
                Math.max(0f, state.suppressedCharge())
            ));
        }
        return highlights.isEmpty() ? List.of() : Collections.unmodifiableList(highlights);
    }

    public static String humanizeAspectIdentifier(@Nullable Identifier identifier) {
        if (identifier == null) {
            return "Unknown";
        }
        String path = identifier.getPath();
        if (path == null || path.isEmpty()) {
            return identifier.toString();
        }
        String[] pieces = path.split("[_\\-]+");
        StringBuilder builder = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(piece.substring(0, 1).toUpperCase()).append(piece.substring(1).toLowerCase());
        }
        return builder.length() > 0 ? builder.toString() : identifier.toString();
    }

    private long resolvePersonaSeed() {
        if (parent.getPetEntity() != null) {
            UUID uuid = parent.getPetEntity().getUuid();
            return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        }
        UUID owner = parent.getOwnerUuid();
        if (owner != null) {
            return owner.getMostSignificantBits() ^ owner.getLeastSignificantBits();
        }
        return parent.hashCode();
    }

    private static Identifier identifierAt(List<?> tuple, int index) {
        if (tuple.size() <= index) {
            return null;
        }
        Object value = tuple.get(index);
        if (value instanceof Identifier identifier) {
            return identifier;
        }
        if (value instanceof String string) {
            return Identifier.tryParse(string);
        }
        return null;
    }

    private static float floatAt(List<?> tuple, int index, float defaultValue) {
        if (tuple.size() <= index) {
            return defaultValue;
        }
        Object value = tuple.get(index);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String string) {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int intAt(List<?> tuple, int index, int defaultValue) {
        if (tuple.size() <= index) {
            return defaultValue;
        }
        Object value = tuple.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static long longAt(List<?> tuple, int index, long defaultValue) {
        if (tuple.size() <= index) {
            return defaultValue;
        }
        Object value = tuple.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private void persistState() {
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SCORE, score);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DEED_TICK, lastDeedTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_TRIGGER_TICK, lastTriggerTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DECAY_TICK, lastDecayTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_COUNT, spreeCount);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_ANCHOR_TICK, spreeAnchorTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_DISHARMONY, disharmonyStrength);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_ACTIVE, active);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_VICE_STATE, encodeAspectStates(viceStates));
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_VIRTUE_STATE, encodeAspectStates(virtueStates));
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, encodePersona(persona));
    }

    public record TriggerOutcome(boolean triggered, float intensity, boolean cleared, boolean suppressed,
                                     float disharmonyStrength, @Nullable Identifier dominantVice,
                                     Map<Identifier, Float> virtueLevels, @Nullable String reason) {
        private static final TriggerOutcome NONE = new TriggerOutcome(false, 0f, false, false, 0f, null, Map.of(), null);

        public static TriggerOutcome none() {
            return NONE;
        }

        private static TriggerOutcome of(ThresholdEvaluation evaluation, DisharmonyEvaluation disharmony,
                                         Map<Identifier, Float> virtueSnapshot) {
            if (evaluation.triggered()) {
                return new TriggerOutcome(true, evaluation.intensity(), false, false, disharmony.current(),
                    evaluation.viceId(), virtueSnapshot, evaluation.reason());
            }
            if (evaluation.cleared()) {
                return new TriggerOutcome(false, 0f, true, false, disharmony.current(), evaluation.viceId(), virtueSnapshot,
                    evaluation.reason());
            }
            if (evaluation.suppressed()) {
                return new TriggerOutcome(false, 0f, false, true, disharmony.current(), evaluation.viceId(), virtueSnapshot,
                    evaluation.reason());
            }
            return none();
        }
    }

    public record MoralitySnapshot(
        float score,
        float disharmony,
        boolean active,
        int spreeCount,
        long spreeWindowTicks,
        long spreeAnchorTick,
        long lastDeedTick,
        long lastTriggerTick,
        long lastDecayTick,
        @Nullable Identifier dominantViceId,
        @Nullable String dominantViceName,
        List<AspectSnapshot> topVices,
        List<AspectSnapshot> topVirtues
    ) {
        public MoralitySnapshot {
            topVices = topVices == null || topVices.isEmpty() ? List.of() : List.copyOf(topVices);
            topVirtues = topVirtues == null || topVirtues.isEmpty() ? List.of() : List.copyOf(topVirtues);
        }
    }

    public record AspectSnapshot(
        Identifier id,
        String name,
        float value,
        int spreeCount,
        float suppressedCharge
    ) {
    }

    private record ThresholdEvaluation(boolean triggered, float intensity, boolean cleared, boolean suppressed,
                                       @Nullable Identifier viceId, @Nullable String reason) {
        private static final ThresholdEvaluation NONE = new ThresholdEvaluation(false, 0f, false, false, null, null);

        static ThresholdEvaluation triggeredResult(float intensity, @Nullable Identifier viceId) {
            return new ThresholdEvaluation(true, intensity, false, false, viceId, null);
        }

        static ThresholdEvaluation clearedResult(@Nullable Identifier viceId) {
            return new ThresholdEvaluation(false, 0f, true, false, viceId, null);
        }

        static ThresholdEvaluation suppressedResult(@Nullable Identifier viceId, @Nullable String reason) {
            return new ThresholdEvaluation(false, 0f, false, true, viceId, reason);
        }

        static ThresholdEvaluation none(@Nullable Identifier viceId) {
            return new ThresholdEvaluation(false, 0f, false, false, viceId, null);
        }
    }

    private record DisharmonyEvaluation(float previous, float current, boolean cleared, @Nullable Identifier viceId) {}

    /**
     * Derive emotion bias modifiers from persona axes.
     * These biases affect which emotions the pet feels more intensely.
     */
    private Map<PetComponent.Emotion, Float> deriveEmotionBiasesFromPersona() {
        EnumMap<PetComponent.Emotion, Float> biases = new EnumMap<>(PetComponent.Emotion.class);
        
        if (persona == null) {
            return biases;
        }
        
        float empathy = persona.empathyLevel();
        float aggression = persona.aggressionTendency();
        float courage = persona.courageBaseline();
        float social = persona.socialOrientation();
        float resources = persona.resourceAttitude();
        
        // High empathy → amplify Worried, mute Pride from kills
        if (empathy > 0.6f) {
            biases.put(PetComponent.Emotion.WORRIED, 0.6f);
            biases.put(PetComponent.Emotion.PRIDE, 0.25f);
        }
        
        // Low empathy → mute Regret, amplify Kefi during combat
        if (empathy < 0.4f) {
            biases.put(PetComponent.Emotion.REGRET, 0.2f);
            biases.put(PetComponent.Emotion.KEFI, 0.6f);
        }
        
        // High courage → mute Startle, amplify Protective
        if (courage > 0.6f) {
            biases.put(PetComponent.Emotion.STARTLE, 0.25f);
            biases.put(PetComponent.Emotion.PROTECTIVE, 0.55f);
        }
        
        // Low courage → amplify Foreboding, mute Sisu
        if (courage < 0.4f) {
            biases.put(PetComponent.Emotion.FOREBODING, 0.6f);
            biases.put(PetComponent.Emotion.SISU, 0.25f);
        }
        
        // High social → amplify bonded-related emotions, amplify contagion
        if (social > 0.6f) {
            biases.put(PetComponent.Emotion.PACK_SPIRIT, 0.6f);
        }
        
        // Low social → suppress bonded, amplify solitary emotions
        if (social < 0.4f) {
            biases.put(PetComponent.Emotion.UBUNTU, 0.25f);
            biases.put(PetComponent.Emotion.STOIC, 0.5f);
        }
        
        // High aggression + Low empathy = ruthless, amplify Kefi
        if (aggression > 0.7f && empathy < 0.3f) {
            biases.put(PetComponent.Emotion.KEFI, 0.7f);
            biases.put(PetComponent.Emotion.REGRET, 0.1f);
        }
        
        // High resources share → amplify content
        if (resources > 0.6f) {
            biases.put(PetComponent.Emotion.CONTENT, 0.55f);
        }
        
        return biases;
    }

    private record SpreeSnapshot(int count, long anchor) {}

    public record DarkDeedContext(
        @Nullable net.minecraft.entity.EntityType<?> victimType,
        @Nullable UUID victimUuid,
        Set<Identifier> registryTags,
        Set<String> dynamicTags,
        @Nullable woflo.petsplus.state.relationships.InteractionType.DimensionalResult interactionVector,
        float friendlyFireSeverity,
        boolean lowHealthFinish,
        RelationshipType relationshipHint
    ) {
        public DarkDeedContext {
            registryTags = registryTags == null ? Set.of() : Set.copyOf(registryTags);
            dynamicTags = dynamicTags == null ? Set.of() : Set.copyOf(dynamicTags);
            friendlyFireSeverity = MathHelper.clamp(friendlyFireSeverity, 0f, 4f);
            relationshipHint = relationshipHint == null ? RelationshipType.NEUTRAL : relationshipHint;
        }
    }
}
