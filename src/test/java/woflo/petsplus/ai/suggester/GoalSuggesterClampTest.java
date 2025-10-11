package woflo.petsplus.ai.suggester;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.SignalBootstrap;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.*;

class GoalSuggesterClampTest {

    @AfterEach
    void resetBootstrap() {
        SignalBootstrap.resetForTesting();
        SignalBootstrap.ensureInitialized();
    }

    @Test
    void clampsAggregateMultiplierAndProvidesSummary() {
        SignalBootstrap.resetForTesting();

        DesirabilitySignalRegistry.register(new DesirabilitySignal() {
            private final Identifier id = Identifier.of("test", "extreme");

            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
                return new SignalResult(50.0f, 50.0f, Map.of());
            }
        });

        FeasibilitySignalRegistry.register(new FeasibilitySignal() {
            private final Identifier id = Identifier.of("test", "identity");

            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
                return SignalResult.identity();
            }
        });

        SignalBootstrap.markInitializedForTesting();

        GoalSuggester suggester = new GoalSuggester(mob -> new MobCapabilities.CapabilityProfile(
            true, true, true, true,
            true, true, true, true,
            true, true, true, true,
            true
        ));

        GoalSuggester.Suggestion suggestion = findSuggestion(
            suggester.suggest(emptyContext()),
            GoalIds.CASUAL_WANDER
        );

        assertNotNull(suggestion);
        assertEquals(GoalSuggester.MAX_DESIRABILITY, suggestion.desirability());

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) suggestion.context().get("desirabilitySummary");
        assertNotNull(summary, "Summary map should be present");
        assertTrue((Float) summary.get("raw") > (Float) summary.get("applied"),
            "Raw desirability should exceed the clamped applied value");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trace = (List<Map<String, Object>>) suggestion.context().get("desirabilitySignals");
        assertNotNull(trace);
        assertTrue(trace.stream().anyMatch(entry -> "test:extreme".equals(entry.get("id"))));
    }

    private static GoalSuggester.Suggestion findSuggestion(List<GoalSuggester.Suggestion> suggestions, Identifier goalId) {
        return suggestions.stream()
            .filter(s -> goalId.equals(s.definition().id()))
            .findFirst()
            .orElse(null);
    }

    private static PetContext emptyContext() {
        return new PetContext(
            null,
            null,
            null,
            0,
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            1,
            0f,
            0L,
            null,
            false,
            Float.MAX_VALUE,
            List.<Entity>of(),
            PetContextCrowdSummary.empty(),
            BlockPos.ORIGIN,
            0L,
            true,
            StimulusSnapshot.empty(),
            SocialSnapshot.empty(),
            false,
            new ArrayDeque<>(),
            new HashMap<>(),
            new HashMap<>(),
            0.5f,
            BehaviouralEnergyProfile.neutral()
        );
    }
}
