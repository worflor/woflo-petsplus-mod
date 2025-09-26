package woflo.petsplus.api.mood;

import woflo.petsplus.mood.EmotionStimulusBus;

/**
 * Marker interface for event-driven emotion providers that subscribe to the
 * {@link EmotionStimulusBus}. Providers should register any callbacks or world
 * listeners inside {@link #register(EmotionStimulusBus)} and push emotion
 * deltas via the supplied bus when relevant changes occur.
 */
public interface ReactiveEmotionProvider {
    String id();

    void register(EmotionStimulusBus bus);

    default void unregister(EmotionStimulusBus bus) {
        // optional cleanup
    }
}
