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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        this.relationshipWeights = sanitizeRelationshipMap(relationshipWeights);
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

    private static Map<RelationshipType, Float> sanitizeRelationshipMap(Map<RelationshipType, Float> raw) {
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

    private static Map<Identifier, Float> sanitizeAspectWeights(Map<Identifier, Float> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, Float> sanitized = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Float> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            Float value = entry.getValue();
            if (id == null || value == null || !Float.isFinite(value) || value <= 0f) {
                continue;
            }
            sanitized.merge(id, value, Float::sum);
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Map<Identifier, Float> sanitizeSignedAspectWeights(Map<Identifier, Float> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, Float> sanitized = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Float> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            Float value = entry.getValue();
            if (id == null || value == null || !Float.isFinite(value) || value == 0f) {
                continue;
            }
            sanitized.merge(id, value, Float::sum);
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Map<Identifier, RequirementRange> sanitizeRequirementMap(Map<Identifier, RequirementRange> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, RequirementRange> sanitized = new LinkedHashMap<>();
        for (Map.Entry<Identifier, RequirementRange> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            RequirementRange range = entry.getValue();
            if (id == null || range == null) {
                continue;
            }
            sanitized.merge(id, range, RequirementRange::tighten);
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Map<Identifier, Float> mergeAspectWeights(Map<Identifier, Float> base,
                                                             Map<Identifier, Float> overlay) {
        if (base.isEmpty()) {
            return overlay.isEmpty() ? Map.of() : Map.copyOf(overlay);
        }
        Map<Identifier, Float> merged = new LinkedHashMap<>(base);
        overlay.forEach((id, value) -> merged.merge(id, value, Float::sum));
        return merged;
    }

    private static Map<Identifier, RequirementRange> mergeRequirementMaps(
        Map<Identifier, RequirementRange> base,
        Map<Identifier, RequirementRange> overlay
    ) {
        if (base.isEmpty()) {
            return overlay.isEmpty() ? Map.of() : Map.copyOf(overlay);
        }
        Map<Identifier, RequirementRange> merged = new LinkedHashMap<>(base);
        overlay.forEach((id, range) -> merged.merge(id, range, RequirementRange::tighten));
        return merged;
    }

    private static float sanitizePositive(float value) {
        if (Float.isNaN(value) || value <= 0f) {
            return 0f;
        }
        return value;
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

    public TagEvaluation evaluateDeedTags(Set<String> deedTags) {
        if (deedTags == null || deedTags.isEmpty()) {
            return TagEvaluation.empty();
        }
        float totalBaseWeight = 0f;
        float totalTelemetryBias = 0f;
        float totalSpreeBonus = 0f;
        EnumMap<RelationshipType, Float> relationshipMax = null;
        Map<Identifier, Float> viceWeights = new LinkedHashMap<>();
        Map<Identifier, Float> virtueWeights = new LinkedHashMap<>();
        Map<Identifier, RequirementRange> virtueRequirements = new LinkedHashMap<>();
        for (String tag : deedTags) {
            String normalized = normalizeTag(tag);
            if (normalized == null) {
                continue;
            }
            TagRule rule = tagRules.get(normalized);
            if (rule == null) {
                continue;
            }
            totalBaseWeight += rule.baseWeight();
            if (rule.telemetryBias() > 0f) {
                totalTelemetryBias += rule.telemetryBias();
            }
            if (rule.spreeBonus() > 0f) {
                totalSpreeBonus += rule.spreeBonus();
            }
            if (!rule.relationshipMultipliers().isEmpty()) {
                if (relationshipMax == null) {
                    relationshipMax = new EnumMap<>(RelationshipType.class);
                }
                for (Map.Entry<RelationshipType, Float> entry : rule.relationshipMultipliers().entrySet()) {
                    RelationshipType relationshipType = entry.getKey();
                    Float value = entry.getValue();
                    if (relationshipType == null || value == null || value <= 0f) {
                        continue;
                    }
                    relationshipMax.merge(relationshipType, value, Math::max);
                }
            }
            if (!rule.viceWeights().isEmpty()) {
                rule.viceWeights().forEach((id, value) -> viceWeights.merge(id, value, Float::sum));
            }
            if (!rule.virtueWeights().isEmpty()) {
                rule.virtueWeights().forEach((id, value) -> virtueWeights.merge(id, value, Float::sum));
            }
            if (!rule.virtueRequirements().isEmpty()) {
                rule.virtueRequirements().forEach((id, range) ->
                    virtueRequirements.merge(id, range, RequirementRange::tighten));
            }
        }
        if ((totalBaseWeight <= 0f)
            && (relationshipMax == null || relationshipMax.isEmpty())
            && totalTelemetryBias <= 0f
            && totalSpreeBonus <= 0f
            && viceWeights.isEmpty()
            && virtueWeights.isEmpty()
            && virtueRequirements.isEmpty()) {
            return TagEvaluation.empty();
        }
        Map<RelationshipType, Float> relationshipView = relationshipMax == null || relationshipMax.isEmpty()
            ? Map.of()
            : Map.copyOf(relationshipMax);
        Map<Identifier, Float> viceView = viceWeights.isEmpty() ? Map.of() : Map.copyOf(viceWeights);
        Map<Identifier, Float> virtueView = virtueWeights.isEmpty() ? Map.of() : Map.copyOf(virtueWeights);
        Map<Identifier, RequirementRange> requirementView = virtueRequirements.isEmpty()
            ? Map.of()
            : Map.copyOf(virtueRequirements);
        return new TagEvaluation(totalBaseWeight, relationshipView, totalTelemetryBias, totalSpreeBonus,
            viceView, virtueView, requirementView);
    }

    public float baseWeight(EntityType<?> victimType, Set<String> deedTags) {
        return baseWeight(victimType, evaluateDeedTags(deedTags));
    }

    public float baseWeight(EntityType<?> victimType, TagEvaluation tagEvaluation) {
        float total = 0f;
        if (victimType != null) {
            Identifier id = EntityType.getId(victimType);
            VictimRule victimRule = victimRules.get(id);
            if (victimRule != null) {
                total += victimRule.baseWeight();
            }
        }
        TagEvaluation evaluation = tagEvaluation == null ? TagEvaluation.empty() : tagEvaluation;
        total += evaluation.baseWeight();
        return sanitizePositive(total);
    }

    public float relationshipMultiplier(RelationshipType type, Set<String> deedTags) {
        return relationshipMultiplier(type, evaluateDeedTags(deedTags));
    }

    public float relationshipMultiplier(RelationshipType type, TagEvaluation tagEvaluation) {
        float base = relationshipWeights.getOrDefault(type, 1.0f);
        TagEvaluation evaluation = tagEvaluation == null ? TagEvaluation.empty() : tagEvaluation;
        float modifier = evaluation.relationshipMultiplier(type);
        if (modifier > 0f) {
            base *= modifier;
        }
        return sanitizePositive(base);
    }

    public float telemetryMultiplier(@Nullable RelationshipProfile profile,
                                     @Nullable InteractionVector interactionVector,
                                     float friendlyFireSeverity,
                                     Set<String> deedTags) {
        return telemetryMultiplier(profile, interactionVector, friendlyFireSeverity, evaluateDeedTags(deedTags));
    }

    public float telemetryMultiplier(@Nullable RelationshipProfile profile,
                                     @Nullable InteractionVector interactionVector,
                                     float friendlyFireSeverity,
                                     TagEvaluation tagEvaluation) {
        float multiplier = telemetry.compute(profile, interactionVector, friendlyFireSeverity);
        TagEvaluation evaluation = tagEvaluation == null ? TagEvaluation.empty() : tagEvaluation;
        multiplier += evaluation.telemetryBias();
        return MathHelper.clamp(multiplier, telemetry.minFactor, telemetry.maxFactor);
    }

    public float spreeMultiplier(int currentCount, boolean lowHealthFinish, Set<String> deedTags) {
        return spreeMultiplier(currentCount, lowHealthFinish, evaluateDeedTags(deedTags));
    }

    public float spreeMultiplier(int currentCount, boolean lowHealthFinish, TagEvaluation tagEvaluation) {
        float multiplier = spreeSettings.baseMultiplier;
        if (currentCount > 1) {
            multiplier += Math.min(spreeSettings.maxBonus,
                (currentCount - 1) * spreeSettings.stepBonus);
        }
        if (lowHealthFinish) {
            multiplier += spreeSettings.clutchBonus;
        }
        TagEvaluation evaluation = tagEvaluation == null ? TagEvaluation.empty() : tagEvaluation;
        multiplier += evaluation.spreeBonus();
        return Math.max(spreeSettings.baseMultiplier, multiplier);
    }

    public AspectContribution aspectContribution(EntityType<?> victimType, TagEvaluation tagEvaluation) {
        TagEvaluation evaluation = tagEvaluation == null ? TagEvaluation.empty() : tagEvaluation;
        Map<Identifier, Float> vice = new LinkedHashMap<>(evaluation.viceWeights());
        Map<Identifier, Float> virtue = new LinkedHashMap<>(evaluation.virtueWeights());
        Map<Identifier, RequirementRange> requirements = new LinkedHashMap<>(evaluation.virtueRequirements());
        if (victimType != null) {
            Identifier id = EntityType.getId(victimType);
            VictimRule victimRule = victimRules.get(id);
            if (victimRule != null) {
                victimRule.viceWeights().forEach((key, value) -> vice.merge(key, value, Float::sum));
                victimRule.virtueWeights().forEach((key, value) -> virtue.merge(key, value, Float::sum));
                victimRule.virtueRequirements().forEach((key, range) ->
                    requirements.merge(key, range, RequirementRange::tighten));
            }
        }
        return new AspectContribution(
            vice.isEmpty() ? Map.of() : Map.copyOf(vice),
            virtue.isEmpty() ? Map.of() : Map.copyOf(virtue),
            requirements.isEmpty() ? Map.of() : Map.copyOf(requirements)
        );
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
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    public record VictimRule(float baseWeight,
                             Set<String> tags,
                             Map<Identifier, Float> viceWeights,
                             Map<Identifier, Float> virtueWeights,
                             Map<Identifier, RequirementRange> virtueRequirements) {
        public VictimRule(float baseWeight,
                          Set<String> tags,
                          Map<Identifier, Float> viceWeights,
                          Map<Identifier, Float> virtueWeights,
                          Map<Identifier, RequirementRange> virtueRequirements) {
            this.baseWeight = Math.max(0f, baseWeight);
            this.tags = tags == null || tags.isEmpty() ? Set.of() : Set.copyOf(tags);
            this.viceWeights = sanitizeAspectWeights(viceWeights);
            this.virtueWeights = sanitizeSignedAspectWeights(virtueWeights);
            this.virtueRequirements = sanitizeRequirementMap(virtueRequirements);
        }

        public VictimRule overlay(VictimRule other) {
            if (other == null) {
                return this;
            }
            float weight = other.baseWeight > 0f ? other.baseWeight : this.baseWeight;
            Set<String> merged = new HashSet<>(this.tags);
            merged.addAll(other.tags);
            Map<Identifier, Float> mergedVice = mergeAspectWeights(this.viceWeights, other.viceWeights);
            Map<Identifier, Float> mergedVirtue = mergeAspectWeights(this.virtueWeights, other.virtueWeights);
            Map<Identifier, RequirementRange> mergedRequirements = mergeRequirementMaps(this.virtueRequirements,
                other.virtueRequirements);
            return new VictimRule(weight, merged, mergedVice, mergedVirtue, mergedRequirements);
        }
    }

    public record TagEvaluation(float baseWeight,
                                 Map<RelationshipType, Float> relationshipMultipliers,
                                 float telemetryBias,
                                 float spreeBonus,
                                 Map<Identifier, Float> viceWeights,
                                 Map<Identifier, Float> virtueWeights,
                                 Map<Identifier, RequirementRange> virtueRequirements) {
        private static final TagEvaluation EMPTY = new TagEvaluation(0f, Map.of(), 0f, 0f, Map.of(), Map.of(), Map.of());

        public TagEvaluation {
            baseWeight = sanitizePositive(baseWeight);
            telemetryBias = sanitizePositive(telemetryBias);
            spreeBonus = sanitizePositive(spreeBonus);
            relationshipMultipliers = sanitizeRelationshipMap(relationshipMultipliers);
            viceWeights = sanitizeAspectWeights(viceWeights);
            virtueWeights = sanitizeSignedAspectWeights(virtueWeights);
            virtueRequirements = sanitizeRequirementMap(virtueRequirements);
        }

        public static TagEvaluation empty() {
            return EMPTY;
        }

        public float relationshipMultiplier(RelationshipType type) {
            if (relationshipMultipliers.isEmpty()) {
                return 0f;
            }
            Float value = relationshipMultipliers.get(type);
            if (value == null) {
                return 0f;
            }
            return value > 0f ? value : 0f;
        }
    }

    public record TagRule(float baseWeight,
                          Map<RelationshipType, Float> relationshipMultipliers,
                          float telemetryBias,
                          float spreeBonus,
                          Map<Identifier, Float> viceWeights,
                          Map<Identifier, Float> virtueWeights,
                          Map<Identifier, RequirementRange> virtueRequirements) {
        public TagRule {
            baseWeight = sanitizePositive(baseWeight);
            relationshipMultipliers = sanitizeRelationshipMap(relationshipMultipliers);
            telemetryBias = sanitizePositive(telemetryBias);
            spreeBonus = sanitizePositive(spreeBonus);
            viceWeights = sanitizeAspectWeights(viceWeights);
            virtueWeights = sanitizeSignedAspectWeights(virtueWeights);
            virtueRequirements = sanitizeRequirementMap(virtueRequirements);
        }

        public float relationshipMultiplier(RelationshipType type) {
            if (relationshipMultipliers.isEmpty()) {
                return 0f;
            }
            return relationshipMultipliers.getOrDefault(type, 0f);
        }
    }

    public record RequirementRange(float min, float max) {
        public RequirementRange {
            float clampedMin = Float.isFinite(min) ? min : 0f;
            float clampedMax = Float.isFinite(max) ? max : 1f;
            if (clampedMax < clampedMin) {
                float tmp = clampedMax;
                clampedMax = clampedMin;
                clampedMin = tmp;
            }
            min = clampedMin;
            max = clampedMax;
        }

        public boolean isSatisfied(float value) {
            return value >= min && value <= max;
        }

        public RequirementRange tighten(RequirementRange other) {
            if (other == null) {
                return this;
            }
            float newMin = Math.max(this.min, other.min);
            float newMax = Math.min(this.max, other.max);
            if (newMax < newMin) {
                newMax = newMin;
            }
            return new RequirementRange(newMin, newMax);
        }
    }

    public record AspectContribution(Map<Identifier, Float> viceWeights,
                                     Map<Identifier, Float> virtueWeights,
                                     Map<Identifier, RequirementRange> virtueRequirements) {
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
                             float defaultPersistencePerDay,
                             float defaultPassiveDriftPerDay,
                             float defaultImpressionability,
                             float intensityScale,
                             float minIntensity,
                             float maxIntensity) {
        public static final Thresholds DEFAULT = new Thresholds(32f, 12f, 72000L, 1.0f, 0f, 1.0f, 0.04f, 0.25f, 1.0f);

        public Thresholds overlay(Thresholds other) {
            if (other == null || other == DEFAULT) {
                return this;
            }
            float trigger = other.triggerScore > 0f ? other.triggerScore : this.triggerScore;
            float remission = other.remissionScore > 0f ? other.remissionScore : this.remissionScore;
            long cooldown = other.cooldownTicks > 0 ? other.cooldownTicks : this.cooldownTicks;
            float persistence = other.defaultPersistencePerDay >= 0f
                ? other.defaultPersistencePerDay
                : this.defaultPersistencePerDay;
            float passiveDrift = other.defaultPassiveDriftPerDay >= 0f
                ? other.defaultPassiveDriftPerDay
                : this.defaultPassiveDriftPerDay;
            float impressionability = other.defaultImpressionability > 0f
                ? other.defaultImpressionability
                : this.defaultImpressionability;
            float scale = other.intensityScale > 0f ? other.intensityScale : this.intensityScale;
            float min = other.minIntensity > 0f ? other.minIntensity : this.minIntensity;
            float max = other.maxIntensity > 0f ? other.maxIntensity : this.maxIntensity;
            return new Thresholds(trigger, remission, cooldown, persistence, passiveDrift, impressionability, scale, min, max);
        }

        public float intensityForScore(float score) {
            float scaled = score * intensityScale;
            return MathHelper.clamp(scaled, minIntensity, maxIntensity);
        }

        public double defaultRetentionLnPerTick() {
            return MoralityAspectDefinition.retentionLnPerTick(defaultPersistencePerDay);
        }

        public float defaultPassiveDriftPerTick() {
            return MoralityAspectDefinition.passiveDriftPerTick(defaultPassiveDriftPerDay);
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
