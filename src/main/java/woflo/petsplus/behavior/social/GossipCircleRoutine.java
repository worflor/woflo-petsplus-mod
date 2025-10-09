package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;

import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
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
    private static final int MAX_LISTENERS_PER_CLUSTER = 4;
    private static final long CADENCE = 40L;
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
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
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
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
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
            SocialContextSnapshot.NeighborSample sample = new SocialContextSnapshot.NeighborSample(
                entry.pet(), context.getOrCreateNeighborData(entry, currentTick), distanceSq);
            listeners.add(sample);
        });

        if (listeners.isEmpty()) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }

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
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }

        List<List<SocialContextSnapshot.NeighborSample>> clusters = partitionClusters(listeners, currentTick);
        if (clusters.isEmpty()) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }

        int cursor = context.component().getStateData(PetComponent.StateKeys.GOSSIP_CLUSTER_CURSOR, Integer.class, 0);
        if (cursor < 0) {
            cursor = 0;
        }
        cursor = clusters.isEmpty() ? 0 : (cursor % clusters.size());

        context.setSharedRumors(toShare);
        context.setGossipNeighbors(listeners);
        context.setGossipClusters(clusters);
        context.setGossipClusterCursor(cursor);
    }

    @Override
    public void applyEffects(SocialContextSnapshot context) {
        List<RumorEntry> rumors = context.sharedRumors();
        if (rumors.isEmpty()) {
            return;
        }

        List<List<SocialContextSnapshot.NeighborSample>> clusters = context.gossipClusters();
        if (clusters.isEmpty()) {
            return;
        }

        PetComponent storyteller = context.component();
        PetGossipLedger storytellerLedger = storyteller.getGossipLedger();
        float storytellerKnowledge = storytellerLedger.knowledgeScore(context.currentTick());
        int clusterCursor = context.gossipClusterCursor();
        int clusterCount = clusters.size();
        if (clusterCount <= 0) {
            return;
        }

        int rumorIndex = 0;
        int visitedClusters = 0;
        List<RumorEntry> sharedRumors = new ArrayList<>();

        for (int step = 0; step < clusterCount && rumorIndex < rumors.size(); step++) {
            int clusterIndex = (clusterCursor + step) % clusterCount;
            List<SocialContextSnapshot.NeighborSample> cluster = clusters.get(clusterIndex);
            if (cluster == null || cluster.isEmpty()) {
                visitedClusters++;
                continue;
            }
            RumorEntry rumor = rumors.get(rumorIndex);
            boolean shared = shareWithCluster(context, storyteller, storytellerLedger, cluster, rumor,
                storytellerKnowledge);
            visitedClusters++;
            rumorIndex++;
            if (shared) {
                sharedRumors.add(rumor);
                if (context.tryMarkBeat("gossip_circle_cluster_" + clusterIndex, 2400)) {
                    Text cueText = GossipNarration.buildCircleCue(storyteller, cluster, rumor, context.currentTick());
                    EmotionContextCues.sendCue(context.owner(),
                        "social.gossip.circle." + context.pet().getUuidAsString() + "." + clusterIndex,
                        cueText,
                        2400);
                }
            } else if (!GossipTopics.isAbstract(rumor.topicId())) {
                storytellerLedger.deferRumor(rumor.topicId());
            }
        }

        if (rumorIndex < rumors.size()) {
            for (int i = rumorIndex; i < rumors.size(); i++) {
                RumorEntry leftover = rumors.get(i);
                if (!GossipTopics.isAbstract(leftover.topicId())) {
                    storytellerLedger.deferRumor(leftover.topicId());
                }
            }
        }

        if (visitedClusters > 0) {
            int nextCursor = (clusterCursor + Math.max(1, visitedClusters)) % clusterCount;
            storyteller.setStateData(PetComponent.StateKeys.GOSSIP_CLUSTER_CURSOR, nextCursor);
            context.setGossipClusterCursor(nextCursor);
        }

        if (sharedRumors.isEmpty()) {
            return;
        }

        context.setSharedRumors(sharedRumors);

        float combinedStrength = 0f;
        for (RumorEntry rumor : sharedRumors) {
            combinedStrength += GossipSocialHelper.rumorStrength(rumor);
        }
        float averageStrength = combinedStrength / sharedRumors.size();
        float sobremesaDelta = GossipSocialHelper.storytellerEaseDelta(sharedRumors.size(), averageStrength);
        float prideDelta = GossipSocialHelper.storytellerPrideDelta(averageStrength, storytellerKnowledge);
        context.pushEmotion(storyteller, PetComponent.Emotion.SOBREMESA, sobremesaDelta);
        context.pushEmotion(storyteller, PetComponent.Emotion.PRIDE, prideDelta);
    }

    private boolean shareWithCluster(SocialContextSnapshot context,
                                     PetComponent storyteller,
                                     PetGossipLedger storytellerLedger,
                                     List<SocialContextSnapshot.NeighborSample> cluster,
                                     RumorEntry rumor,
                                     float storytellerKnowledge) {
        boolean sharedWithNewListener = false;
        boolean isAbstract = !storytellerLedger.hasRumor(rumor.topicId())
            && GossipTopics.isAbstract(rumor.topicId());
        boolean witnessedAny = false;
        float ubuntuEcho = 0f;
        float loyaltyEcho = 0f;
        float warmthEcho = 0f;
        float guardianEcho = 0f;
        for (SocialContextSnapshot.NeighborSample sample : cluster) {
            MobEntity otherPet = sample.pet();
            if (otherPet == null) {
                continue;
            }
            PetComponent listener = PetComponent.get(otherPet);
            if (listener == null || listener == storyteller) {
                continue;
            }
            if (listener.isGossipOptedOut(context.currentTick())) {
                continue;
            }
            if (listener.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
                || listener.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.35f)) {
                listener.optOutOfGossip(context.currentTick());
                continue;
            }
            if (!GossipSocialHelper.isDowntime(context, listener, otherPet)) {
                continue;
            }
            PetGossipLedger listenerLedger = listener.getGossipLedger();
            boolean alreadyKnows = listenerLedger.hasRumor(rumor.topicId());
            boolean witnessed = alreadyKnows && listenerLedger.witnessedRecently(rumor.topicId(), context.currentTick());
            float listenerKnowledge = listenerLedger.knowledgeScore(context.currentTick());
            float knowledgeGap = Math.max(0f, storytellerKnowledge - listenerKnowledge);
            if (witnessed) {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
                float loyalty = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, true);
                context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
                float guardian = GossipSocialHelper.solidarityGuardianDelta(rumor, knowledgeGap, true);
                if (guardian > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.GUARDIAN_VIGIL, guardian);
                }
                float warmth = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
                }
                witnessedAny = true;
                ubuntuEcho = Math.max(ubuntuEcho, GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, true));
                loyaltyEcho = Math.max(loyaltyEcho, loyalty * 0.6f);
                guardianEcho = Math.max(guardianEcho, guardian * 0.6f);
                warmthEcho = Math.max(warmthEcho, warmth * 0.7f);
                continue;
            }
            if (listenerLedger.hasHeardRecently(rumor.topicId(), context.currentTick())) {
                listenerLedger.registerDuplicateHeard(rumor.topicId(), context.currentTick());
                context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION,
                    GossipSocialHelper.frustrationDelta(rumor));
                listener.optOutOfGossip(context.currentTick());
                continue;
            }
            if (isAbstract) {
                listenerLedger.registerAbstractHeard(rumor.topicId(), context.currentTick());
                float curiosity = GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, true);
                context.pushEmotion(listener, PetComponent.Emotion.CURIOUS, curiosity);
                float warmth = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, true);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.65f);
                    warmthEcho = Math.max(warmthEcho, warmth * 0.5f);
                }
                ubuntuEcho = Math.max(ubuntuEcho, GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false));
                sharedWithNewListener = true;
            } else {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), alreadyKnows);
                if (alreadyKnows) {
                    float loyalty = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, false);
                    context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
                    float warmth = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                    if (warmth > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.75f);
                        warmthEcho = Math.max(warmthEcho, warmth * 0.55f);
                    }
                    loyaltyEcho = Math.max(loyaltyEcho, loyalty * 0.5f);
                    ubuntuEcho = Math.max(ubuntuEcho, GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false));
                } else {
                    float curiosity = GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, false);
                    context.pushEmotion(listener, PetComponent.Emotion.CURIOUS, curiosity);
                    float warmth = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                    if (warmth > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
                        warmthEcho = Math.max(warmthEcho, warmth * 0.5f);
                    }
                    ubuntuEcho = Math.max(ubuntuEcho, GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false));
                    sharedWithNewListener = true;
                }
            }
        }
        if (sharedWithNewListener) {
            storytellerLedger.markShared(rumor.topicId(), context.currentTick());
        }
        if (ubuntuEcho > 0f) {
            context.pushEmotion(storyteller, PetComponent.Emotion.UBUNTU, ubuntuEcho * 0.75f);
        }
        if (loyaltyEcho > 0f) {
            context.pushEmotion(storyteller, PetComponent.Emotion.LOYALTY, loyaltyEcho);
        }
        if (warmthEcho > 0f) {
            context.pushEmotion(storyteller, PetComponent.Emotion.SOBREMESA, warmthEcho);
            context.pushEmotion(storyteller, PetComponent.Emotion.QUERECIA, warmthEcho * 0.8f);
        }
        if (guardianEcho > 0f) {
            context.pushEmotion(storyteller, PetComponent.Emotion.GUARDIAN_VIGIL, guardianEcho * 0.7f);
        }
        if (witnessedAny && guardianEcho <= 0f) {
            float guardian = GossipSocialHelper.solidarityGuardianDelta(rumor, 0.2f, true) * 0.4f;
            if (guardian > 0f) {
                context.pushEmotion(storyteller, PetComponent.Emotion.GUARDIAN_VIGIL, guardian);
            }
        }
        return sharedWithNewListener;
    }

    private List<List<SocialContextSnapshot.NeighborSample>> partitionClusters(
        List<SocialContextSnapshot.NeighborSample> listeners, long currentTick) {
        if (listeners.isEmpty()) {
            return List.of();
        }
        List<SocialContextSnapshot.NeighborSample> sorted = new ArrayList<>(listeners);
        sorted.sort(Comparator
            .comparingDouble((SocialContextSnapshot.NeighborSample sample) -> -neighborKnowledge(sample, currentTick))
            .thenComparingDouble(SocialContextSnapshot.NeighborSample::squaredDistance));

        List<List<SocialContextSnapshot.NeighborSample>> clusters = new ArrayList<>();
        List<SocialContextSnapshot.NeighborSample> bucket = new ArrayList<>(MAX_LISTENERS_PER_CLUSTER);
        for (SocialContextSnapshot.NeighborSample sample : sorted) {
            if (bucket.size() >= MAX_LISTENERS_PER_CLUSTER) {
                clusters.add(List.copyOf(bucket));
                bucket = new ArrayList<>(MAX_LISTENERS_PER_CLUSTER);
            }
            bucket.add(sample);
        }
        if (!bucket.isEmpty()) {
            clusters.add(List.copyOf(bucket));
        }
        return clusters;
    }

    private double neighborKnowledge(SocialContextSnapshot.NeighborSample sample, long currentTick) {
        PetSocialData data = sample.data();
        PetComponent component = data != null ? data.component() : PetComponent.get(sample.pet());
        if (component == null) {
            return 0.0D;
        }
        return component.getGossipLedger().knowledgeScore(currentTick);
    }
}
