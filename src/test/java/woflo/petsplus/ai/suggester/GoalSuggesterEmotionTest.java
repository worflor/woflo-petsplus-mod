package woflo.petsplus.ai.suggester;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.*;

class GoalSuggesterEmotionTest {

    @Test
    void socialEmotionBoostsSuggestionsAndProducesTrace() {
        GoalSuggester suggester = new GoalSuggester(m -> new MobCapabilities.CapabilityProfile(
            true,
            false,
            false,
            false,
            true,
            false,
            false,
            true,
            true,
            true,
            false,
            false,
            false
        ));

        PetContext baseline = emptyContext(false);
        PetContext withEmotion = emptyContext(true);

        GoalSuggester.Suggestion baselineSuggestion = findSuggestion(suggester.suggest(baseline), GoalIds.LEAN_AGAINST_OWNER);
        GoalSuggester.Suggestion emotionSuggestion = findSuggestion(suggester.suggest(withEmotion), GoalIds.LEAN_AGAINST_OWNER);

        assertNotNull(emotionSuggestion, "Emotion suggestion should exist");
        assertNotNull(baselineSuggestion, "Baseline suggestion should exist");

        assertTrue(emotionSuggestion.desirability() > baselineSuggestion.desirability(),
            "Social emotion should increase desirability for leaning against owner");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> desirabilityTrace = (List<Map<String, Object>>) emotionSuggestion.context().get("desirabilitySignals");
        assertNotNull(desirabilityTrace, "Trace should contain desirability entries");
        assertTrue(desirabilityTrace.stream().anyMatch(entry -> "petsplus:desirability/mood_blend".equals(entry.get("id"))),
            "Trace should include the mood blend signal");

        @SuppressWarnings("unchecked")
        Map<String, Object> desirabilitySummary = (Map<String, Object>) emotionSuggestion.context().get("desirabilitySummary");
        assertNotNull(desirabilitySummary, "Summary should be available for desirability");
    }

    @Test
    void natureProfileShapesRelevantGoals() {
        GoalSuggester suggester = new GoalSuggester(m -> new MobCapabilities.CapabilityProfile(
            true,
            false,
            false,
            true,
            true,
            false,
            false,
            true,
            true,
            true,
            false,
            false,
            false
        ));

        PetContext baseline = contextWithNature(PetComponent.NatureEmotionProfile.EMPTY);
        PetContext vigilantNature = contextWithNature(new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.GUARDIAN_VIGIL, 0.9f,
            PetComponent.Emotion.SOBREMESA, 0.4f,
            PetComponent.Emotion.CURIOUS, 0.3f
        ));

        GoalSuggester.Suggestion baselineSuggestion = findSuggestion(suggester.suggest(baseline), GoalIds.PURPOSEFUL_PATROL);
        GoalSuggester.Suggestion natureSuggestion = findSuggestion(suggester.suggest(vigilantNature), GoalIds.PURPOSEFUL_PATROL);

        assertNotNull(baselineSuggestion, "Baseline should include purposeful patrol");
        assertNotNull(natureSuggestion, "Nature-influenced suggestion should exist");

        assertTrue(natureSuggestion.desirability() > baselineSuggestion.desirability(),
            "Guardian vigil nature should raise desirability for purposeful patrol");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> desirabilityTrace = (List<Map<String, Object>>) natureSuggestion.context().get("desirabilitySignals");
        assertNotNull(desirabilityTrace, "Trace should contain desirability entries");
        assertTrue(desirabilityTrace.stream().anyMatch(entry -> "petsplus:desirability/nature".equals(entry.get("id"))),
            "Trace should include the nature signal");
    }

    private static GoalSuggester.Suggestion findSuggestion(List<GoalSuggester.Suggestion> suggestions, Identifier goalId) {
        return suggestions.stream()
            .filter(s -> goalId.equals(s.definition().id()))
            .findFirst()
            .orElse(null);
    }

    private static PetContext emptyContext(boolean withSocialEmotion) {
        Map<PetComponent.Mood, Float> moodBlend = Collections.emptyMap();
        Map<PetComponent.Emotion, Float> emotions = withSocialEmotion
            ? Map.of(PetComponent.Emotion.SOBREMESA, 0.6f)
            : Collections.emptyMap();

        PetComponent component = Mockito.mock(PetComponent.class);

        return new PetContext(
            null,
            component,
            null,
            0,
            moodBlend,
            emotions,
            null,
            null,
            PetComponent.NatureEmotionProfile.EMPTY,
            1,
            0f,
            0L,
            null,
            true,
            2f,
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

    private static PetContext contextWithNature(PetComponent.NatureEmotionProfile profile) {
        Map<PetComponent.Mood, Float> moodBlend = Collections.emptyMap();
        Map<PetComponent.Emotion, Float> emotions = Collections.emptyMap();

        return new PetContext(
            null,
            Mockito.mock(PetComponent.class),
            null,
            0,
            moodBlend,
            emotions,
            null,
            Identifier.of("petsplus", "test_nature"),
            profile,
            1,
            0f,
            0L,
            null,
            true,
            2f,
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
