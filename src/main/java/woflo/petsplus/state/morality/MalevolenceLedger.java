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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-pet morality tracker that accumulates owner "dark deed" telemetry and raises
 * the malevolence emotion when the configured rule thresholds are met.
 */
public final class MalevolenceLedger {
    private static final long DEFAULT_SPREE_WINDOW = MalevolenceRules.SpreeSettings.DEFAULT.windowTicks();

    private final PetComponent parent;

    private final Map<Identifier, MoralityAspectState> viceStates = new LinkedHashMap<>();
    private final Map<Identifier, MoralityAspectState> virtueStates = new LinkedHashMap<>();
    private MoralityPersona persona;
    private long lastPersonaDecayTick = Long.MIN_VALUE;
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
        lastPersonaDecayTick = Long.MIN_VALUE;
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
        Map<Identifier, Float> viceWeights = new LinkedHashMap<>(contribution.viceWeights());
        if (viceWeights.isEmpty()) {
            viceWeights.put(MoralityAspectRegistry.defaultViceAspectId(), 1f);
        }
        Map<Identifier, MalevolenceRules.RequirementRange> virtueRequirements = contribution.virtueRequirements();
        List<String> gateFailures = new ArrayList<>();
        if (!virtueRequirements.isEmpty()) {
            for (Map.Entry<Identifier, MalevolenceRules.RequirementRange> entry : virtueRequirements.entrySet()) {
                Identifier virtueId = entry.getKey();
                MalevolenceRules.RequirementRange requirement = entry.getValue();
                float level = getVirtueLevel(virtueId);
                if (!requirement.isSatisfied(level)) {
                    gateFailures.add(virtueId.toString());
                }
            }
        }

        if (gateFailures.isEmpty()) {
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

        Map<Identifier, Float> virtueSnapshot = snapshotVirtues();

        ThresholdEvaluation evaluation = gateFailures.isEmpty()
            ? evaluateThreshold(thresholds, now)
            : ThresholdEvaluation.suppressedResult(dominantVice, String.join(", ", gateFailures));
        DisharmonyEvaluation disharmonyEvaluation = updateDisharmony(rules, evaluation, now);
        persistState();
        return TriggerOutcome.of(evaluation, disharmonyEvaluation, virtueSnapshot);
    }

    private void decayAspects(long now, MalevolenceRules.Thresholds thresholds) {
        long referenceTick = lastDecayTick != Long.MIN_VALUE ? lastDecayTick : lastDeedTick;
        long elapsed = Math.max(0L, now - referenceTick);
        if (elapsed <= 0L) {
            return;
        }
        double defaultRetentionLn = thresholds.defaultRetentionLnPerTick();
        float defaultPassiveDriftPerTick = thresholds.defaultPassiveDriftPerTick();
        float defaultPersistence = thresholds.defaultPersistencePerDay();
        float defaultPassiveDriftPerDay = thresholds.defaultPassiveDriftPerDay();
        float elapsedDays = elapsed / MoralityAspectDefinition.TICKS_PER_DAY;
        for (Map.Entry<Identifier, MoralityAspectState> entry : viceStates.entrySet()) {
            Identifier id = entry.getKey();
            MoralityAspectState state = entry.getValue();
            MoralityAspectDefinition def = MoralityAspectRegistry.get(id);
            float baseline = def != null ? def.baseline() : 0f;
            double retentionLn = def != null ? def.retentionLnPerTick() : defaultRetentionLn;
            float passiveDriftPerTick = def != null ? def.passiveDriftPerTick() : defaultPassiveDriftPerTick;
            state.stabilize(baseline, retentionLn, passiveDriftPerTick, now);
            float persistence = def != null ? def.persistencePerDay() : defaultPersistence;
            float passiveDriftPerDay = def != null ? def.passiveDriftPerDay() : defaultPassiveDriftPerDay;
            float bleedPerDay = 0.1f + (1f - MathHelper.clamp(persistence, 0f, 1f)) * 0.4f + passiveDriftPerDay * 0.6f;
            float bleed = Math.max(0f, bleedPerDay * elapsedDays);
            state.bleedSuppression(now, bleed);
        }
        for (Map.Entry<Identifier, MoralityAspectState> entry : virtueStates.entrySet()) {
            Identifier id = entry.getKey();
            MoralityAspectState state = entry.getValue();
            MoralityAspectDefinition def = MoralityAspectRegistry.get(id);
            float baseline = def != null ? def.baseline() : 0.5f;
            double retentionLn = def != null ? def.retentionLnPerTick() : defaultRetentionLn;
            float passiveDriftPerTick = def != null ? def.passiveDriftPerTick() : defaultPassiveDriftPerTick;
            state.stabilize(baseline, retentionLn, passiveDriftPerTick, now);
        }
        if (persona != null) {
            persona.relax(now, defaultRetentionLn);
            lastPersonaDecayTick = now;
        }
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

    private ThresholdEvaluation evaluateThreshold(MalevolenceRules.Thresholds thresholds, long now) {
        score = aggregateScore();
        Identifier viceId = dominantVice;
        boolean cooledDown = lastTriggerTick == Long.MIN_VALUE || now - lastTriggerTick >= thresholds.cooldownTicks();
        if (score >= thresholds.triggerScore() && cooledDown) {
            float intensity = thresholds.intensityForScore(score);
            lastTriggerTick = now;
            active = true;
            return ThresholdEvaluation.triggeredResult(intensity, viceId);
        }
        boolean clearable = active && score <= thresholds.remissionScore();
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
        if (!hasPassiveState()) {
            return;
        }
        long effectiveNow = resolveEffectiveNow(now);
        if (effectiveNow <= 0L) {
            return;
        }

        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules == null || rules == MalevolenceRules.EMPTY) {
            return;
        }

        float previousScore = score;
        int previousSpreeCount = spreeCount;
        long previousSpreeAnchor = spreeAnchorTick;
        float previousDisharmony = disharmonyStrength;
        boolean wasActive = active;
        long previousDecayTick = lastDecayTick;

        decayAspects(effectiveNow, rules.thresholds());
        refreshSpreeWindow(effectiveNow, rules.spreeSettings());

        UUID ownerUuid = parent.getOwnerUuid();
        boolean needsHarmonyUpdate = false;
        if (ownerUuid != null) {
            if (active) {
                if (score <= rules.thresholds().remissionScore()) {
                    active = false;
                    disharmonyStrength = 0f;
                    lastDecayTick = effectiveNow;
                    needsHarmonyUpdate = true;
                } else if (disharmonyStrength > 0f) {
                    needsHarmonyUpdate = true;
                }
            } else if (disharmonyStrength > 0f) {
                if (disharmonyStrength <= rules.disharmonySettings().remissionFloor()) {
                    disharmonyStrength = 0f;
                    needsHarmonyUpdate = true;
                } else {
                    needsHarmonyUpdate = true;
                }
            } else if (previousDisharmony > 0f) {
                needsHarmonyUpdate = true;
            }
        } else {
            if (active) {
                active = false;
            }
            if (disharmonyStrength > 0f) {
                disharmonyStrength = 0f;
                needsHarmonyUpdate = true;
            } else if (previousDisharmony > 0f) {
                needsHarmonyUpdate = true;
            }
        }

        if (needsHarmonyUpdate) {
            applyHarmony(effectiveNow);
        }

        if (score != previousScore
            || spreeCount != previousSpreeCount
            || spreeAnchorTick != previousSpreeAnchor
            || disharmonyStrength != previousDisharmony
            || wasActive != active
            || lastDecayTick != previousDecayTick) {
            persistState();
        }
    }

    private boolean hasPassiveState() {
        return active || score > 0f || disharmonyStrength > 0f || spreeCount > 0;
    }

    private long resolveEffectiveNow(long candidate) {
        if (candidate > 0L) {
            return candidate;
        }
        if (parent.getPetEntity() != null && parent.getPetEntity().getEntityWorld() != null) {
            return parent.getPetEntity().getEntityWorld().getTime();
        }
        return candidate;
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
        Map<UUID, PetComponent.HarmonyCompatibility> compat = incoming.compatibilities();
        PetComponent.HarmonyCompatibility existing = compat.get(ownerUuid);
        float clampedStrength = MathHelper.clamp(disharmonyStrength, 0f, rules.disharmonySettings().maxStrength());
        if (clampedStrength <= 0f) {
            if (existing == null || existing.disharmonyStrength() <= 0f) {
                return incoming;
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
            for (PetComponent.HarmonyCompatibility value : copy.values()) {
                if (value != null) {
                    disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
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
                incoming.emotionBiases(),
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
            return incoming;
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
        for (PetComponent.HarmonyCompatibility value : copy.values()) {
            if (value != null) {
                disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
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
            incoming.emotionBiases(),
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
        List<Object> encoded = new ArrayList<>(source.size());
        source.forEach((id, state) -> {
            List<Object> tuple = new ArrayList<>(7);
            tuple.add(id.toString());
            tuple.add(state.value());
            tuple.add(state.spreeCount());
            tuple.add(state.spreeAnchorTick());
            tuple.add(state.lastUpdatedTick());
            tuple.add(state.suppressedCharge());
            tuple.add(state.lastSuppressedTick());
            encoded.add(tuple);
        });
        return encoded;
    }

    private MoralityPersona decodePersona(@Nullable List<?> encoded) {
        if (encoded == null || encoded.size() < 3) {
            return null;
        }
        float susceptibility = floatAt(encoded, 0, 1f);
        float resilience = floatAt(encoded, 1, 1f);
        float trauma = floatAt(encoded, 2, 0f);
        return new MoralityPersona(susceptibility, resilience, trauma);
    }

    private List<Object> encodePersona(MoralityPersona persona) {
        if (persona == null) {
            return List.of();
        }
        return List.of(persona.viceSusceptibility(), persona.virtueResilience(), persona.traumaImprint());
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
        float total = 0f;
        for (MoralityAspectState state : viceStates.values()) {
            total += Math.max(0f, state.value());
        }
        return total;
    }

    @Nullable
    private Identifier findDominantVice() {
        Identifier best = null;
        float value = 0f;
        for (Map.Entry<Identifier, MoralityAspectState> entry : viceStates.entrySet()) {
            float current = entry.getValue().value();
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
        Map<Identifier, Float> snapshot = new LinkedHashMap<>();
        virtueStates.forEach((id, state) -> snapshot.put(id, state.value()));
        return snapshot;
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
