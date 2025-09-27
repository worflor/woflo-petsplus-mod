package woflo.petsplus.behavior.social;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.gossip.RumorTone;

import java.util.List;

/**
 * Formats localized cue text for gossip exchanges so action bar prompts echo
 * the actual story being told instead of a generic placeholder.
 */
final class GossipNarration {

    private GossipNarration() {
    }

    static Text buildCircleCue(PetComponent storyteller,
                               List<SocialContextSnapshot.NeighborSample> neighbors,
                               RumorEntry rumor,
                               long currentTick) {
        PetComponent listener = null;
        if (neighbors != null && !neighbors.isEmpty()) {
            SocialContextSnapshot.NeighborSample sample = neighbors.get(0);
            if (sample != null && sample.data() != null) {
                listener = sample.data().component();
            }
        }
        return buildCue("petsplus.emotion_cue.social.gossip.circle", storyteller, listener, rumor, currentTick);
    }

    static Text buildWhisperCue(PetComponent storyteller, PetComponent listener,
                               RumorEntry rumor, long currentTick) {
        return buildCue("petsplus.emotion_cue.social.gossip.whisper", storyteller, listener, rumor, currentTick);
    }

    private static Text buildCue(String translationKey,
                                 @Nullable PetComponent storyteller,
                                 @Nullable PetComponent listener,
                                 RumorEntry rumor,
                                 long currentTick) {
        Text storytellerName = storyteller != null && storyteller.getPet() != null
            ? storyteller.getPet().getDisplayName()
            : Text.translatable("petsplus.gossip.unknown_pet");
        Text listenerName = listener != null && listener.getPet() != null
            ? listener.getPet().getDisplayName()
            : Text.translatable("petsplus.gossip.unknown_pet");
        Text story = buildStoryText(rumor, currentTick);
        if (translationKey.endsWith("whisper")) {
            return Text.translatable(translationKey, storytellerName, listenerName, story);
        }
        return Text.translatable(translationKey, storytellerName, story);
    }

    private static Text buildStoryText(RumorEntry rumor, long currentTick) {
        Text base = rumor.paraphrasedCopy();
        if (base == null) {
            base = GossipTopics.findAbstract(rumor.topicId())
                .map(topic -> Text.translatable(topic.translationKey()))
                .orElseGet(() -> Text.translatable("petsplus.gossip.topic.generic"));
        }
        RumorTone tone = RumorTone.classify(rumor, currentTick);
        Text toneDescriptor = Text.translatable("petsplus.gossip.tone." + tone.key());
        String templateKey = selectTemplateKey(tone, rumor, currentTick);
        return Text.translatable(templateKey, base, toneDescriptor);
    }

    private static String selectTemplateKey(RumorTone tone, RumorEntry rumor, long currentTick) {
        List<String> templates = tone.templateKeys();
        if (templates.isEmpty()) {
            return "petsplus.gossip.story_with_tone";
        }
        long seed = rumor.topicId() ^ (rumor.shareCount() * 31L) ^ currentTick;
        int index = Math.floorMod(seed, templates.size());
        return templates.get(index);
    }
}
