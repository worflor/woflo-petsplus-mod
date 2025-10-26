package woflo.petsplus.state.morality;

import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable rulebook describing how observed deeds contribute to the malevolence ledger.
 *
 * The rule set is fully data-driven via {@link woflo.petsplus.data.MalevolenceRulesDataLoader}
 * so designers can tune which deeds count as truly evil without touching code.
 */
public final class MalevolenceRules {
    public static final MalevolenceRules EMPTY = new MalevolenceRules(
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        TelemetrySettings.EMPTY,
        Thresholds.DEFAULT,
        SpreeSettings.DEFAULT,
        DisharmonySettings.DEFAULT,
        ForgivenessSettings.DEFAULT,
        Identifier.of("petsplus", "morality/malevolence"),
        false
    );

    private final Map<String, TagRule> tagRules;
    private final Map<Identifier, VictimRule> victimRules;
    private final Map<Identifier, VictimRule> tagVictimRules;
    private final Map<RelationshipType, Float> relationshipWeights;
    private final TelemetrySettings telemetry;
    private final Thresholds thresholds;
    private final SpreeSettings spreeSettings;
    private final DisharmonySettings disharmonySettings;
    private final ForgivenessSettings forgivenessSettings;
    private final Identifier disharmonySetId;
    private final boolean replace;

    public MalevolenceRules(
        Map<String, TagRule> tagRules,
        Map<Identifier, VictimRule> victimRules,
        Map<Identifier, VictimRule> tagVictimRules,
        Map<RelationshipType, Float> relationshipWeights,
        TelemetrySettings telemetry,
        Thresholds thresholds,
        SpreeSettings spreeSettings,
        DisharmonySettings disharmonySettings,
        ForgivenessSettings forgivenessSettings,
        Identifier disharmonySetId,
        boolean replace
    ) {
        this.tagRules = sanitizeTagRules(tagRules);
        this.victimRules = sanitizeVictimRules(victimRules);
        this.tagVictimRules = sanitizeVictimRules(tagVictimRules);
        this.relationshipWeights = sanitizeRelationshipWeights(relationshipWeights);
        this.telemetry = telemetry == null ? TelemetrySettings.EMPTY : telemetry;
        this.thresholds = thresholds == null ? Thresholds.DEFAULT : thresholds;
        this.spreeSettings = spreeSettings == null ? SpreeSettings.DEFAULT : spreeSettings;
        this.disharmonySettings = disharmonySettings == null ? DisharmonySettings.DEFAULT : disharmonySettings;
        this.forgivenessSettings = forgivenessSettings == null ? ForgivenessSettings.DEFAULT : forgivenessSettings;
        this.disharmonySetId = disharmonySetId == null ? Identifier.of("petsplus", "morality/malevolence") : disharmonySetId;
        this.replace = replace;
    }

    public boolean isReplace() {
        return replace;
    }

    public MalevolenceRules overlay(MalevolenceRules other) {
        if (other == null || other == EMPTY) {
            return this;
        }
        if (other.replace) {
            return other;
        }
        Map<String, TagRule> mergedTags = new HashMap<>(this.tagRules);
        mergedTags.putAll(other.tagRules);

        Map<Identifier, VictimRule> mergedVictims = new HashMap<>(this.victimRules);
        mergedVictims.putAll(other.victimRules);

        Map<Identifier, VictimRule> mergedTagVictims = new HashMap<>(this.tagVictimRules);
        mergedTagVictims.putAll(other.tagVictimRules);

        Map<RelationshipType, Float> mergedRelationshipWeights = new EnumMap<>(this.relationshipWeights);
        mergedRelationshipWeights.putAll(other.relationshipWeights);

        TelemetrySettings mergedTelemetry = this.telemetry.overlay(other.telemetry);
        Thresholds mergedThresholds = this.thresholds.overlay(other.thresholds);
        SpreeSettings mergedSpree = this.spreeSettings.overlay(other.spreeSettings);
        DisharmonySettings mergedDisharmony = this.disharmonySettings.overlay(other.disharmonySettings);
        ForgivenessSettings mergedForgiveness = this.forgivenessSettings.overlay(other.forgivenessSettings);
        Identifier resolvedDisharmonySet = other.disharmonySetId != null ? other.disharmonySetId : this.disharmonySetId;

        return new MalevolenceRules(
            mergedTags,
            mergedVictims,
            mergedTagVictims,
            mergedRelationshipWeights,
            mergedTelemetry,
            mergedThresholds,
            mergedSpree,
            mergedDisharmony,
            mergedForgiveness,
            resolvedDisharmonySet,
            false
        );
    }

    private static Map<String, TagRule> sanitizeTagRules(Map<String, TagRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Map<String, TagRule> sanitized = new HashMap<>();
        for (Map.Entry<String, TagRule> entry : rules.entrySet()) {
            String key = normalizeTag(entry.getKey());
            TagRule value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            sanitized.put(key, value);
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Map<Identifier, VictimRule> sanitizeVictimRules(Map<Identifier, VictimRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, VictimRule> sanitized = new HashMap<>();
        for (Map.Entry<Identifier, VictimRule> entry : rules.entrySet()) {
            Identifier id = entry.getKey();
            VictimRule value = entry.getValue();
            if (id == null || value == null) {
                continue;
            }
            sanitized.put(id, value);
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Map<RelationshipType, Float> sanitizeRelationshipWeights(Map<RelationshipType, Float> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        EnumMap<RelationshipType, Float> weights = new EnumMap<>(RelationshipType.class);
        for (Map.Entry<RelationshipType, Float> entry : raw.entrySet()) {
            RelationshipType type = entry.getKey();
            Float value = entry.getValue();
            if (type == null || value == null || Float.isNaN(value) || Float.isInfinite(value)) {
                continue;
            }
            weights.put(type, Math.max(0f, value));
        }
        return weights.isEmpty() ? Map.of() : Map.copyOf(weights);
    }

    @Nullable
    public TagRule tagRule(String tag) {
        if (tag == null) {
            return null;
        }
        return tagRules.get(normalizeTag(tag));
    }

    public Set<String> resolveTags(EntityType<?> victimType, Set<Identifier> registryTags, Set<String> dynamicTags) {
        Set<String> resolved = new HashSet<>();
        if (dynamicTags != null) {
            dynamicTags.stream()
                .map(MalevolenceRules::normalizeTag)
                .filter(Objects::nonNull)
                .forEach(resolved::add);
        }
        if (victimType != null) {
            Identifier typeId = EntityType.getId(victimType);
            VictimRule directRule = victimRules.get(typeId);
            if (directRule != null) {
                resolved.addAll(directRule.tags());
            }
        }
        if (registryTags != null && !registryTags.isEmpty()) {
            for (Identifier tagId : registryTags) {
                VictimRule rule = tagVictimRules.get(tagId);
                if (rule != null) {
                    resolved.addAll(rule.tags());
                }
            }
        }
        return resolved;
    }

    public float baseWeight(EntityType<?> victimType, Set<String> deedTags) {
        float total = 0f;
        if (victimType != null) {
            Identifier id = EntityType.getId(victimType);
            VictimRule victimRule = victimRules.get(id);
            if (victimRule != null) {
                total += victimRule.baseWeight();
            }
        }
        if (deedTags == null || deedTags.isEmpty()) {
            return Math.max(0f, total);
        }
        for (String tag : deedTags) {
            TagRule rule = tagRules.get(normalizeTag(tag));
            if (rule != null) {
                total += rule.baseWeight();
            }
        }
        return Math.max(0f, total);
    }

    public float relationshipMultiplier(RelationshipType type, Set<String> deedTags) {
        float base = relationshipWeights.getOrDefault(type, 1.0f);
        if (deedTags == null || deedTags.isEmpty()) {
            return Math.max(0f, base);
        }
        float modifier = 0f;
        for (String tag : deedTags) {
            TagRule rule = tagRules.get(normalizeTag(tag));
            if (rule != null) {
                modifier = Math.max(modifier, rule.relationshipMultiplier(type));
            }
        }
        if (modifier > 0f) {
            base *= modifier;
        }
        return Math.max(0f, base);
    }

    public float telemetryMultiplier(@Nullable RelationshipProfile profile,
                                     @Nullable InteractionVector interactionVector,
                                     float friendlyFireSeverity,
                                     Set<String> deedTags) {
        float multiplier = telemetry.compute(profile, interactionVector, friendlyFireSeverity);
        if (deedTags != null) {
            for (String tag : deedTags) {
                TagRule rule = tagRules.get(normalizeTag(tag));
                if (rule != null && rule.telemetryBias() > 0f) {
                    multiplier += rule.telemetryBias();
                }
            }
        }
        return MathHelper.clamp(multiplier, telemetry.minFactor, telemetry.maxFactor);
    }

    public float spreeMultiplier(int currentCount, boolean lowHealthFinish, Set<String> deedTags) {
        float multiplier = spreeSettings.baseMultiplier;
        if (currentCount > 1) {
            multiplier += Math.min(spreeSettings.maxBonus,
                (currentCount - 1) * spreeSettings.stepBonus);
        }
        if (lowHealthFinish) {
            multiplier += spreeSettings.clutchBonus;
        }
        if (deedTags != null) {
            for (String tag : deedTags) {
                TagRule rule = tagRules.get(normalizeTag(tag));
                if (rule != null && rule.spreeBonus() > 0f) {
                    multiplier += rule.spreeBonus();
                }
            }
        }
        return Math.max(spreeSettings.baseMultiplier, multiplier);
    }

    public Thresholds thresholds() {
        return thresholds;
    }

    public SpreeSettings spreeSettings() {
        return spreeSettings;
    }

    public DisharmonySettings disharmonySettings() {
        return disharmonySettings;
    }

    public ForgivenessSettings forgivenessSettings() {
        return forgivenessSettings;
    }

    public Identifier disharmonySetId() {
        return disharmonySetId;
    }

    public TelemetrySettings telemetry() {
        return telemetry;
    }

    public static String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        return tag.trim().toLowerCase();
    }

    public record VictimRule(float baseWeight, Set<String> tags) {
        public VictimRule(float baseWeight, Set<String> tags) {
            this.baseWeight = Math.max(0f, baseWeight);
            this.tags = tags == null || tags.isEmpty() ? Set.of() : Set.copyOf(tags);
        }

        public VictimRule overlay(VictimRule other) {
            if (other == null) {
                return this;
            }
            float weight = other.baseWeight > 0f ? other.baseWeight : this.baseWeight;
            Set<String> merged = new HashSet<>(this.tags);
            merged.addAll(other.tags);
            return new VictimRule(weight, merged);
        }
    }

    public record TagRule(float baseWeight,
                          Map<RelationshipType, Float> relationshipMultipliers,
                          float telemetryBias,
                          float spreeBonus) {
        public TagRule(float baseWeight,
                       Map<RelationshipType, Float> relationshipMultipliers,
                       float telemetryBias,
                       float spreeBonus) {
            this.baseWeight = baseWeight;
            if (relationshipMultipliers == null || relationshipMultipliers.isEmpty()) {
                this.relationshipMultipliers = Map.of();
            } else {
                EnumMap<RelationshipType, Float> copy = new EnumMap<>(RelationshipType.class);
                for (Map.Entry<RelationshipType, Float> entry : relationshipMultipliers.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    float clamped = Math.max(0f, entry.getValue());
                    copy.put(entry.getKey(), clamped);
                }
                this.relationshipMultipliers = copy.isEmpty() ? Map.of() : Map.copyOf(copy);
            }
            this.telemetryBias = telemetryBias;
            this.spreeBonus = Math.max(0f, spreeBonus);
        }

        public float baseWeight() {
            return Math.max(0f, baseWeight);
        }

        public float relationshipMultiplier(RelationshipType type) {
            if (relationshipMultipliers.isEmpty()) {
                return 0f;
            }
            return relationshipMultipliers.getOrDefault(type, 0f);
        }

        public float telemetryBias() {
            return Math.max(0f, telemetryBias);
        }

        public float spreeBonus() {
            return spreeBonus;
        }
    }

    public record ForgivenessSettings(
        float friendlyFireFloor,
        int spreeGrace,
        float magnitudeFloor,
        float highTrustThreshold,
        float highTrustSeverityLimit,
        float magnitudeTrustWeight,
        float magnitudeAffectionWeight,
        float magnitudeRespectWeight
    ) {
        public static final ForgivenessSettings DEFAULT = new ForgivenessSettings(0.25f, 1, 0.28f, 0.75f, 0.35f, 1.0f, 1.1f, 0.6f);

        public ForgivenessSettings overlay(ForgivenessSettings other) {
            if (other == null || other == DEFAULT) {
                return this;
            }
            float friendlyFloor = other.friendlyFireFloor > 0f ? other.friendlyFireFloor : this.friendlyFireFloor;
            int grace = other.spreeGrace > 0 ? other.spreeGrace : this.spreeGrace;
            float magnitude = other.magnitudeFloor > 0f ? other.magnitudeFloor : this.magnitudeFloor;
            float trustThreshold = other.highTrustThreshold > 0f ? other.highTrustThreshold : this.highTrustThreshold;
            float severityLimit = other.highTrustSeverityLimit > 0f ? other.highTrustSeverityLimit : this.highTrustSeverityLimit;
            float trustWeight = other.magnitudeTrustWeight != 0f ? other.magnitudeTrustWeight : this.magnitudeTrustWeight;
            float affectionWeight = other.magnitudeAffectionWeight != 0f ? other.magnitudeAffectionWeight : this.magnitudeAffectionWeight;
            float respectWeight = other.magnitudeRespectWeight != 0f ? other.magnitudeRespectWeight : this.magnitudeRespectWeight;
            return new ForgivenessSettings(friendlyFloor, grace, magnitude, trustThreshold, severityLimit, trustWeight, affectionWeight, respectWeight);
        }

        public boolean shouldForgive(
            @Nullable RelationshipProfile profile,
            @Nullable InteractionVector interactionVector,
            float friendlyFireSeverity,
            int candidateSpreeCount
        ) {
            if (friendlyFireSeverity > 0f) {
                if (friendlyFireSeverity <= friendlyFireFloor && candidateSpreeCount <= spreeGrace) {
                    return true;
                }
                if (profile != null && profile.trust() >= highTrustThreshold && friendlyFireSeverity <= highTrustSeverityLimit && candidateSpreeCount <= spreeGrace + 1) {
                    return true;
                }
            }
            if (interactionVector != null) {
                float magnitude = interactionVector.magnitude(magnitudeTrustWeight, magnitudeAffectionWeight, magnitudeRespectWeight);
                if (magnitude <= magnitudeFloor) {
                    if (friendlyFireSeverity <= friendlyFireFloor && candidateSpreeCount <= spreeGrace + 1) {
                        return true;
                    }
                    if (friendlyFireSeverity == 0f && candidateSpreeCount <= spreeGrace) {
                        return true;
                    }
                    if (profile != null && profile.trust() >= highTrustThreshold && friendlyFireSeverity <= highTrustSeverityLimit) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public record InteractionVector(float trustDelta, float affectionDelta, float respectDelta) {
        public InteractionVector(float trustDelta, float affectionDelta, float respectDelta) {
            this.trustDelta = trustDelta;
            this.affectionDelta = affectionDelta;
            this.respectDelta = respectDelta;
        }

        public static InteractionVector of(@Nullable woflo.petsplus.state.relationships.InteractionType.DimensionalResult result) {
            if (result == null) {
                return null;
            }
            return new InteractionVector(result.trustDelta(), result.affectionDelta(), result.respectDelta());
        }

        public float magnitude(float trustWeight, float affectionWeight, float respectWeight) {
            return Math.abs(trustDelta) * trustWeight
                + Math.abs(affectionDelta) * affectionWeight
                + Math.abs(respectDelta) * respectWeight;
        }
    }

    public record TelemetrySettings(float baseFactor,
                                    float trustWeight,
                                    float affectionWeight,
                                    float respectWeight,
                                    float deltaWeight,
                                    float friendlyFireWeight,
                                    float minFactor,
                                    float maxFactor) {
        public static final TelemetrySettings EMPTY = new TelemetrySettings(1.0f, 0f, 0f, 0f, 0f, 0f, 0.2f, 4.0f);

        public TelemetrySettings overlay(TelemetrySettings other) {
            if (other == null || other == EMPTY) {
                return this;
            }
            float base = other.baseFactor > 0f ? other.baseFactor : this.baseFactor;
            float trust = other.trustWeight != 0f ? other.trustWeight : this.trustWeight;
            float affection = other.affectionWeight != 0f ? other.affectionWeight : this.affectionWeight;
            float respect = other.respectWeight != 0f ? other.respectWeight : this.respectWeight;
            float delta = other.deltaWeight != 0f ? other.deltaWeight : this.deltaWeight;
            float ff = other.friendlyFireWeight != 0f ? other.friendlyFireWeight : this.friendlyFireWeight;
            float min = other.minFactor > 0f ? other.minFactor : this.minFactor;
            float max = other.maxFactor > 0f ? other.maxFactor : this.maxFactor;
            return new TelemetrySettings(base, trust, affection, respect, delta, ff, min, max);
        }

        public float compute(@Nullable RelationshipProfile profile,
                             @Nullable InteractionVector vector,
                             float friendlyFireSeverity) {
            float result = Math.max(minFactor, baseFactor);
            if (profile != null) {
                float trustNorm = (profile.trust() + 1.0f) * 0.5f;
                result += trustNorm * trustWeight;
                result += profile.affection() * affectionWeight;
                result += profile.respect() * respectWeight;
            }
            if (vector != null) {
                result += vector.magnitude(deltaWeight, deltaWeight, deltaWeight);
            }
            if (friendlyFireSeverity > 0f && friendlyFireWeight != 0f) {
                result += friendlyFireSeverity * friendlyFireWeight;
            }
            return MathHelper.clamp(result, minFactor, maxFactor);
        }
    }

    public record Thresholds(float triggerScore,
                             float remissionScore,
                             long cooldownTicks,
                             float decayHalfLifeTicks,
                             float intensityScale,
                             float minIntensity,
                             float maxIntensity) {
        public static final Thresholds DEFAULT = new Thresholds(32f, 12f, 72000L, 48000f, 0.04f, 0.25f, 1.0f);

        public Thresholds overlay(Thresholds other) {
            if (other == null || other == DEFAULT) {
                return this;
            }
            float trigger = other.triggerScore > 0f ? other.triggerScore : this.triggerScore;
            float remission = other.remissionScore > 0f ? other.remissionScore : this.remissionScore;
            long cooldown = other.cooldownTicks > 0 ? other.cooldownTicks : this.cooldownTicks;
            float halfLife = other.decayHalfLifeTicks > 0f ? other.decayHalfLifeTicks : this.decayHalfLifeTicks;
            float scale = other.intensityScale > 0f ? other.intensityScale : this.intensityScale;
            float min = other.minIntensity > 0f ? other.minIntensity : this.minIntensity;
            float max = other.maxIntensity > 0f ? other.maxIntensity : this.maxIntensity;
            return new Thresholds(trigger, remission, cooldown, halfLife, scale, min, max);
        }

        public float intensityForScore(float score) {
            float scaled = score * intensityScale;
            return MathHelper.clamp(scaled, minIntensity, maxIntensity);
        }
    }

    public record SpreeSettings(long windowTicks,
                                float baseMultiplier,
                                float stepBonus,
                                float maxBonus,
                                float clutchBonus) {
        public static final SpreeSettings DEFAULT = new SpreeSettings(1200L, 1.0f, 0.3f, 1.2f, 0.35f);

        public SpreeSettings overlay(SpreeSettings other) {
            if (other == null || other == DEFAULT) {
                return this;
            }
            long window = other.windowTicks > 0 ? other.windowTicks : this.windowTicks;
            float base = other.baseMultiplier > 0f ? other.baseMultiplier : this.baseMultiplier;
            float step = other.stepBonus > 0f ? other.stepBonus : this.stepBonus;
            float max = other.maxBonus > 0f ? other.maxBonus : this.maxBonus;
            float clutch = other.clutchBonus > 0f ? other.clutchBonus : this.clutchBonus;
            return new SpreeSettings(window, base, step, max, clutch);
        }
    }

    public record DisharmonySettings(float baseStrength,
                                     float intensityScale,
                                     float maxStrength,
                                     float remissionFloor) {
        public static final DisharmonySettings DEFAULT = new DisharmonySettings(0.3f, 0.45f, 2.5f, 0.2f);

        public DisharmonySettings overlay(DisharmonySettings other) {
            if (other == null || other == DEFAULT) {
                return this;
            }
            float base = other.baseStrength >= 0f ? other.baseStrength : this.baseStrength;
            float scale = other.intensityScale > 0f ? other.intensityScale : this.intensityScale;
            float max = other.maxStrength > 0f ? other.maxStrength : this.maxStrength;
            float floor = other.remissionFloor >= 0f ? other.remissionFloor : this.remissionFloor;
            return new DisharmonySettings(base, scale, max, floor);
        }

        public float resolveStrength(float intensity) {
            float value = baseStrength + intensity * intensityScale;
            return MathHelper.clamp(value, 0f, maxStrength);
        }
    }
}
