package woflo.petsplus.state;

import org.mockito.MockedConstruction;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

public final class PetMoodEngineTestUtil {
    private PetMoodEngineTestUtil() {}

    public static MockedConstruction<PetMoodEngine> mockEngine(
        AtomicReference<EnumMap<PetComponent.Mood, Float>> blendRef,
        Consumer<PetComponent.EmotionDelta> applier
    ) {
        return mockConstruction(PetMoodEngine.class, (engine, context) -> {
            when(engine.getMoodBlend()).thenAnswer(inv -> new EnumMap<>(blendRef.get()));
            doAnswer(invocation -> {
                PetComponent.EmotionDelta delta = invocation.getArgument(0);
                if (applier != null) {
                    applier.accept(delta);
                }
                return null;
            }).when(engine).applyStimulus(any(PetComponent.EmotionDelta.class), anyLong());
        });
    }
}
