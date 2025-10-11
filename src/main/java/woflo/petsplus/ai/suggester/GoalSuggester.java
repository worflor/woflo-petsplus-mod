package woflo.petsplus.ai.suggester;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.SignalBootstrap;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates pet state and suggests behavioral goals with confidence weights.
 */
public class GoalSuggester {

    public record Suggestion(
        GoalDefinition definition,
        float desirability,
        float feasibility,
        String reason,
        Map<String, Object> context
    ) {
        public float score() {
            return desirability * feasibility;
        }
    }

    public static final float MAX_DESIRABILITY = 12.0f;
    public static final float MAX_FEASIBILITY = 1.0f;
    private static final float MIN_MULTIPLIER = 0.0f;

    private final CapabilityAnalyzer capabilityAnalyzer;

    public GoalSuggester() {
        this(mob -> mob != null ? MobCapabilities.analyze(mob) : defaultCapabilities());
    }

    public GoalSuggester(CapabilityAnalyzer capabilityAnalyzer) {
        this.capabilityAnalyzer = Objects.requireNonNull(capabilityAnalyzer);
    }

    private static MobCapabilities.CapabilityProfile defaultCapabilities() {
        return new MobCapabilities.CapabilityProfile(
            true, true, true, true,
            true, true, true, true,
            true, true, true, true,
            true
        );
    }

    @FunctionalInterface
    public interface CapabilityAnalyzer {
        MobCapabilities.CapabilityProfile analyze(MobEntity mob);
    }

    public List<Suggestion> suggest(PetContext ctx) {
        SignalBootstrap.ensureInitialized();

        List<Suggestion> suggestions = new ArrayList<>();
        MobEntity mob = ctx.mob();
        MobCapabilities.CapabilityProfile capabilities = capabilityAnalyzer.analyze(mob);

        for (GoalDefinition definition : GoalRegistry.all()) {
            if (!definition.isCompatible(capabilities)) {
                continue;
            }

            AggregatedSignalResult desirability = aggregateDesirability(ctx, definition);
            if (desirability.appliedValue() <= 0.0f) {
                continue;
            }

            AggregatedSignalResult feasibility = aggregateFeasibility(ctx, definition);
            if (feasibility.appliedValue() <= 0.0f) {
                continue;
            }

            Map<String, Object> context = new HashMap<>();
            context.put("desirabilitySignals", desirability.traceAsMaps());
            context.put("feasibilitySignals", feasibility.traceAsMaps());
            context.put("desirabilitySummary", desirability.summary());
            context.put("feasibilitySummary", feasibility.summary());

            String reason = explainSuggestion(definition, ctx, desirability.appliedValue(), feasibility.appliedValue());
            suggestions.add(new Suggestion(definition, desirability.appliedValue(), feasibility.appliedValue(), reason, context));
        }

        suggestions.sort((a, b) -> Float.compare(b.score(), a.score()));
        return suggestions;
    }

    private AggregatedSignalResult aggregateDesirability(PetContext ctx, GoalDefinition definition) {
        float rawValue = 1.0f;
        float appliedValue = 1.0f;
        List<SignalTraceEntry> trace = new ArrayList<>();
        for (DesirabilitySignal signal : DesirabilitySignalRegistry.all()) {
            SignalResult result = signal.evaluate(definition, ctx);
            rawValue *= result.rawValue();
            appliedValue *= result.appliedValue();
            trace.add(buildTrace(signal.id(), result));
            if (appliedValue <= 0.0f) {
                break;
            }
        }
        float clamped = MathHelper.clamp(appliedValue, MIN_MULTIPLIER, MAX_DESIRABILITY);
        return new AggregatedSignalResult(rawValue, clamped, trace);
    }

    private AggregatedSignalResult aggregateFeasibility(PetContext ctx, GoalDefinition definition) {
        float rawValue = 1.0f;
        float appliedValue = 1.0f;
        List<SignalTraceEntry> trace = new ArrayList<>();
        for (FeasibilitySignal signal : FeasibilitySignalRegistry.all()) {
            SignalResult result = signal.evaluate(definition, ctx);
            rawValue *= result.rawValue();
            appliedValue *= result.appliedValue();
            trace.add(buildTrace(signal.id(), result));
            if (appliedValue <= 0.0f) {
                break;
            }
        }
        float clamped = MathHelper.clamp(appliedValue, MIN_MULTIPLIER, MAX_FEASIBILITY);
        return new AggregatedSignalResult(rawValue, clamped, trace);
    }

    private SignalTraceEntry buildTrace(Identifier id, SignalResult result) {
        return new SignalTraceEntry(id, result.rawValue(), result.appliedValue(), result.trace());
    }

    private record AggregatedSignalResult(float rawValue, float appliedValue, List<SignalTraceEntry> trace) {
        Map<String, Object> summary() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("raw", rawValue);
            summary.put("applied", appliedValue);
            return summary;
        }

        List<Map<String, Object>> traceAsMaps() {
            return trace.stream().map(SignalTraceEntry::toMap).toList();
        }
    }

    private record SignalTraceEntry(Identifier id, float raw, float applied, Map<String, Object> details) {
        Map<String, Object> toMap() {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", id.toString());
            entry.put("raw", raw);
            entry.put("applied", applied);
            if (details != null && !details.isEmpty()) {
                entry.put("details", Map.copyOf(details));
            }
            return entry;
        }
    }

    private String explainSuggestion(GoalDefinition goalType, PetContext ctx, float desirability, float feasibility) {
        StringBuilder reason = new StringBuilder();
        reason.append(goalType.id()).append(" (");

        if (ctx.hasPetsPlusComponent() && ctx.currentMood() != null) {
            reason.append("Mood: ").append(ctx.currentMood().name()).append(", ");
        }

        reason.append("Age: ").append(ctx.getAgeCategory()).append(", ");
        reason.append("Desire: ").append(String.format("%.2f", desirability)).append(", ");
        reason.append("Feasible: ").append(String.format("%.2f", feasibility));
        reason.append(")");

        return reason.toString();
    }
}
