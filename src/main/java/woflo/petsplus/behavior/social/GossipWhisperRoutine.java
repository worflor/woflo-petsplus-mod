package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;

import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.state.gossip.PetGossipLedger;
import woflo.petsplus.state.gossip.RumorEntry;

/**
 * Handles intimate one-on-one rumor exchanges, typically when two pets cross
 * paths away from the pack. Runs on the same cadence as whisper routines so the
 * storytelling feels like a private aside.
 */
public class GossipWhisperRoutine implements SocialBehaviorRoutine {

    private static final double WHISPER_RADIUS = 6.0;
    private static final long CADENCE = 80L;
    private static final float KNOWLEDGE_EPSILON = 0.05f;

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        if (context.currentTick() % CADENCE != 0) {
            return false;
        }
        if (context.component().isGossipOptedOut(context.currentTick())) {
            return false;
        }
        if (!GossipSocialHelper.isDowntime(context, context.component(), context.pet())) {
            return false;
        }
        return context.component().hasShareableRumors(context.currentTick());
    }

    @Override
    public void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick) {
        if (context.component().isGossipOptedOut(currentTick)
            || !GossipSocialHelper.isDowntime(context, context.component(), context.pet())) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }

        PetGossipLedger ledger = context.component().getGossipLedger();
        List<RumorEntry> toShare = ledger.peekFreshRumors(1, currentTick);
        if (toShare.isEmpty()) {
            toShare = ledger.peekAbstractRumors(1, currentTick);
        }
        if (toShare.isEmpty()) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }

        List<SocialContextSnapshot.NeighborSample> potential = new ArrayList<>();
        swarm.forEachNeighbor(context.pet(), context.component(), WHISPER_RADIUS, (entry, distanceSq) -> {
            if (distanceSq > (WHISPER_RADIUS * WHISPER_RADIUS)) {
                return;
            }
            if (entry.pet() == null || entry.pet() == context.pet()) {
                return;
            }
            PetComponent neighborComponent = entry.component();
            if (neighborComponent == null) {
                return;
            }
            if (neighborComponent.isGossipOptedOut(currentTick)) {
                return;
            }
            if (neighborComponent.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
                || neighborComponent.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.35f)) {
                neighborComponent.optOutOfGossip(currentTick);
                return;
            }
            if (!GossipSocialHelper.isDowntime(context, neighborComponent, entry.pet())) {
                return;
            }
            potential.add(new SocialContextSnapshot.NeighborSample(
                entry.pet(), context.getOrCreateNeighborData(entry, currentTick), distanceSq));
        });

        potential.sort(Comparator.comparingDouble(SocialContextSnapshot.NeighborSample::squaredDistance));
        List<SocialContextSnapshot.NeighborSample> nearest = potential.isEmpty()
            ? Collections.emptyList()
            : List.of(potential.get(0));

        if (!nearest.isEmpty()) {
            SocialContextSnapshot.NeighborSample candidate = nearest.get(0);
            PetSocialData data = candidate.data();
            PetComponent neighborComponent = data != null ? data.component() : null;
            if (neighborComponent != null) {
                if (neighborComponent.isGossipOptedOut(currentTick)) {
                    context.setSharedRumors(Collections.emptyList());
                    context.setGossipNeighbors(Collections.emptyList());
                    return;
                }
                if (neighborComponent.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
                    || neighborComponent.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.35f)) {
                    neighborComponent.optOutOfGossip(currentTick);
                    context.setSharedRumors(Collections.emptyList());
                    context.setGossipNeighbors(Collections.emptyList());
                    return;
                }
                if (!GossipSocialHelper.isDowntime(context, neighborComponent, candidate.pet())) {
                    context.setSharedRumors(Collections.emptyList());
                    context.setGossipNeighbors(Collections.emptyList());
                    return;
                }
                float whispererKnowledge = context.component().getGossipLedger().knowledgeScore(currentTick);
                float neighborKnowledge = neighborComponent.getGossipLedger().knowledgeScore(currentTick);
                if (neighborKnowledge > whispererKnowledge + KNOWLEDGE_EPSILON) {
                    context.setSharedRumors(Collections.emptyList());
                    context.setGossipNeighbors(Collections.emptyList());
                    return;
                }
            }
        }

        context.setSharedRumors(toShare);
        context.setGossipNeighbors(nearest);
    }

    @Override
    public void applyEffects(SocialContextSnapshot context) {
        List<RumorEntry> rumors = context.sharedRumors();
        if (rumors.isEmpty()) {
            return;
        }
        List<SocialContextSnapshot.NeighborSample> neighbors = context.gossipNeighbors();
        if (neighbors.isEmpty()) {
            return;
        }

        RumorEntry rumor = rumors.get(0);
        SocialContextSnapshot.NeighborSample targetSample = neighbors.get(0);
        MobEntity otherPet = targetSample.pet();
        if (otherPet == null) {
            return;
        }

        PetComponent whisperer = context.component();
        PetComponent listener = PetComponent.get(otherPet);
        if (listener == null || listener == whisperer) {
            return;
        }

        if (listener.isGossipOptedOut(context.currentTick())) {
            return;
        }
        if (listener.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
            || listener.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.35f)) {
            listener.optOutOfGossip(context.currentTick());
            return;
        }
        if (!GossipSocialHelper.isDowntime(context, listener, otherPet)) {
            return;
        }

        PetGossipLedger whispererLedger = whisperer.getGossipLedger();
        PetGossipLedger listenerLedger = listener.getGossipLedger();
        float whispererKnowledge = whispererLedger.knowledgeScore(context.currentTick());
        float listenerKnowledge = listenerLedger.knowledgeScore(context.currentTick());
        float knowledgeGap = Math.max(0f, whispererKnowledge - listenerKnowledge);
        boolean alreadyKnows = listenerLedger.hasRumor(rumor.topicId());
        boolean witnessed = alreadyKnows && listenerLedger.witnessedRecently(rumor.topicId(), context.currentTick());

        boolean sharedAny = false;
        boolean newListener = false;

        if (witnessed) {
            listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
            context.pushEmotion(listener, PetComponent.Emotion.LOYALTY,
                GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, true));
            sharedAny = true;
        } else {
            if (listenerLedger.hasHeardRecently(rumor.topicId(), context.currentTick())) {
                listenerLedger.registerDuplicateHeard(rumor.topicId(), context.currentTick());
                context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION,
                    GossipSocialHelper.frustrationDelta(rumor));
                listener.optOutOfGossip(context.currentTick());
                return;
            }

            boolean isAbstract = !whispererLedger.hasRumor(rumor.topicId())
                && GossipTopics.isAbstract(rumor.topicId());
            if (isAbstract) {
                listenerLedger.registerAbstractHeard(rumor.topicId(), context.currentTick());
                context.pushEmotion(listener, PetComponent.Emotion.CURIOUS,
                    GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, true));
                newListener = true;
                sharedAny = true;
            } else if (alreadyKnows) {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
                context.pushEmotion(listener, PetComponent.Emotion.LOYALTY,
                    GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, false));
                sharedAny = true;
            } else {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), false);
                context.pushEmotion(listener, PetComponent.Emotion.CURIOUS,
                    GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, false));
                newListener = true;
                sharedAny = true;
            }

            if (newListener) {
                whispererLedger.markShared(rumor.topicId(), context.currentTick());
            }
        }

        if (!sharedAny) {
            return;
        }

        context.pushEmotion(whisperer, PetComponent.Emotion.EMPATHY,
            GossipSocialHelper.empathyDelta(rumor, knowledgeGap));

        if (context.tryMarkBeat("gossip_whisper", 1800)) {
            Text cueText = GossipNarration.buildWhisperCue(whisperer, listener, rumor, context.currentTick());
            EmotionContextCues.sendCue(context.owner(),
                "social.gossip.whisper." + context.pet().getUuidAsString(),
                cueText,
                1800);
        }
    }
}
