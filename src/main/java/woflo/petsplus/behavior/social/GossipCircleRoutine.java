package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
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
 * Shares pending rumors with nearby pets during loose pack gatherings. Runs on
 * a modest cadence so the gossip queue drains opportunistically while pets are
 * already mingling for other social routines.
 */
public class GossipCircleRoutine implements SocialBehaviorRoutine {

    private static final double GOSSIP_RADIUS = 8.5;
    private static final int MAX_RUMORS_PER_PASS = 3;
    private static final long CADENCE = 40L;
    private static final float KNOWLEDGE_EPSILON = 0.05f;
    private static final float REPEAT_FUZZ = 0.02f;

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        if (context.currentTick() % CADENCE != 0) {
            return false;
        }
        if (!GossipSocialHelper.isDowntime(context, context.component(), context.pet())) {
            return false;
        }
        return context.component().hasShareableRumors(context.currentTick());
    }

    @Override
    public void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick) {
        if (!GossipSocialHelper.isDowntime(context, context.component(), context.pet())) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }

        PetGossipLedger ledger = context.component().getGossipLedger();
        List<RumorEntry> toShare = ledger.peekFreshRumors(MAX_RUMORS_PER_PASS, currentTick);
        if (toShare.isEmpty()) {
            toShare = ledger.peekAbstractRumors(MAX_RUMORS_PER_PASS, currentTick);
        }
        if (toShare.isEmpty()) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }

        List<SocialContextSnapshot.NeighborSample> listeners = new ArrayList<>();
        swarm.forEachNeighbor(context.pet(), context.component(), GOSSIP_RADIUS, (entry, distanceSq) -> {
            if (distanceSq > (GOSSIP_RADIUS * GOSSIP_RADIUS)) {
                return;
            }
            if (entry.pet() == null || entry.pet() == context.pet()) {
                return;
            }
            PetComponent neighborComponent = entry.component();
            if (neighborComponent == null || neighborComponent.getOwnerUuid() == null) {
                return;
            }
            if (!GossipSocialHelper.isDowntime(context, neighborComponent, entry.pet())) {
                return;
            }
            SocialContextSnapshot.NeighborSample sample = new SocialContextSnapshot.NeighborSample(
                entry.pet(), context.getOrCreateNeighborData(entry, currentTick), distanceSq);
            listeners.add(sample);
        });

        float storytellerKnowledge = context.component().getGossipLedger().knowledgeScore(currentTick);
        boolean storytellerIsLeader = true;
        for (SocialContextSnapshot.NeighborSample sample : listeners) {
            PetSocialData data = sample.data();
            PetComponent neighborComponent = data != null ? data.component() : null;
            if (neighborComponent == null) {
                continue;
            }
            float neighborKnowledge = neighborComponent.getGossipLedger().knowledgeScore(currentTick);
            if (neighborKnowledge > storytellerKnowledge + KNOWLEDGE_EPSILON) {
                storytellerIsLeader = false;
                break;
            }
        }

        if (!storytellerIsLeader) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }

        context.setSharedRumors(toShare);
        context.setGossipNeighbors(listeners);
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

        PetComponent storyteller = context.component();
        PetGossipLedger storytellerLedger = storyteller.getGossipLedger();
        List<RumorEntry> actuallyShared = new ArrayList<>(rumors.size());
        for (RumorEntry rumor : rumors) {
            boolean sharedWithAny = false;
            boolean isAbstract = !storytellerLedger.hasRumor(rumor.topicId())
                && GossipTopics.isAbstract(rumor.topicId());
            for (SocialContextSnapshot.NeighborSample sample : neighbors) {
                MobEntity otherPet = sample.pet();
                if (otherPet == null) {
                    continue;
                }
                PetComponent listener = PetComponent.get(otherPet);
                if (listener == null || listener == storyteller) {
                    continue;
                }
                PetGossipLedger listenerLedger = listener.getGossipLedger();
                if (listenerLedger.hasHeardRecently(rumor.topicId(), context.currentTick())) {
                    listenerLedger.registerDuplicateHeard(rumor.topicId(), context.currentTick());
                    listener.pushEmotion(PetComponent.Emotion.FRUSTRATION, REPEAT_FUZZ);
                    continue;
                }
                if (isAbstract) {
                    listenerLedger.registerAbstractHeard(rumor.topicId(), context.currentTick());
                    listener.pushEmotion(PetComponent.Emotion.CURIOUS, 0.015f);
                } else {
                    boolean corroborated = listenerLedger.hasRumor(rumor.topicId());
                    listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), corroborated);
                    if (corroborated) {
                        listener.pushEmotion(PetComponent.Emotion.LOYALTY, 0.015f);
                    } else {
                        listener.pushEmotion(PetComponent.Emotion.CURIOUS, 0.02f);
                    }
                }
                sharedWithAny = true;
            }
            if (sharedWithAny) {
                storytellerLedger.markShared(rumor.topicId(), context.currentTick());
                actuallyShared.add(rumor);
            }
        }

        if (actuallyShared.isEmpty()) {
            return;
        }

        context.setSharedRumors(actuallyShared);
        storyteller.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.025f + actuallyShared.size() * 0.01f);
        storyteller.pushEmotion(PetComponent.Emotion.PRIDE, 0.015f);
        if (context.tryMarkBeat("gossip_circle", 240)) {
            RumorEntry headline = actuallyShared.get(0);
            Text cueText = GossipNarration.buildCircleCue(storyteller, neighbors, headline, context.currentTick());
            EmotionContextCues.sendCue(context.owner(),
                "social.gossip.circle." + context.pet().getUuidAsString(),
                cueText,
                240);
        }
    }
}
