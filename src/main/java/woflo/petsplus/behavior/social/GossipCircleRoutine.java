package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.state.gossip.HarmonyGossipBridge;
import woflo.petsplus.state.gossip.HarmonyGossipBridge.HarmonyGossipProfile;
import woflo.petsplus.state.gossip.PetGossipLedger;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.gossip.RumorTone;

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
    private static final int MAX_STALL_PASSES = 3;

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        if (context.currentTick() % CADENCE != 0) {
            return false;
        }
        if (context.component().isGossipOptedOut(context.currentTick())) {
            return false;
        }
        if (context.component().isGossipSessionCooling(context.currentTick())) {
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
            recordGossipStall(context.component(), currentTick);
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }

        List<RumorEntry> eligibleRumors = new ArrayList<>();
        for (RumorEntry rumor : toShare) {
            PetComponent component = context.component();
            component.ensureGossipSession(rumor.topicId(), currentTick, rumor);
            if (component.hasGossipSessionBudget(rumor.topicId(), currentTick)) {
                eligibleRumors.add(rumor);
            } else {
                component.concludeGossipSession(rumor.topicId(), currentTick, computeSessionCooldown(rumor), false);
            }
        }
        if (eligibleRumors.isEmpty()) {
            recordGossipStall(context.component(), currentTick);
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }
        toShare = eligibleRumors;

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
            recordGossipStall(context.component(), currentTick);
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
            recordGossipStall(context.component(), currentTick);
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            context.setGossipClusters(Collections.emptyList());
            context.setGossipClusterCursor(0);
            return;
        }

        List<List<SocialContextSnapshot.NeighborSample>> clusters = partitionClusters(listeners, currentTick);
        if (clusters.isEmpty()) {
            recordGossipStall(context.component(), currentTick);
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
        resetGossipStall(context.component());
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
        long currentTick = context.currentTick();
        float storytellerKnowledge = storytellerLedger.knowledgeScore(currentTick);
        int clusterCursor = context.gossipClusterCursor();
        int clusterCount = clusters.size();
        if (clusterCount <= 0) {
            return;
        }

        int rumorIndex = 0;
        int visitedClusters = 0;
        List<RumorEntry> sharedRumors = new ArrayList<>();
        boolean sessionEnded = false;

        while (rumorIndex < rumors.size() && visitedClusters < clusterCount) {
            int clusterIndex = (clusterCursor + visitedClusters) % clusterCount;
            List<SocialContextSnapshot.NeighborSample> cluster = clusters.get(clusterIndex);
            if (cluster == null || cluster.isEmpty()) {
                visitedClusters++;
                continue;
            }

            RumorEntry rumor = rumors.get(rumorIndex);
            storyteller.ensureGossipSession(rumor.topicId(), currentTick, rumor);
            if (!storyteller.hasGossipSessionBudget(rumor.topicId(), currentTick)) {
                storyteller.concludeGossipSession(rumor.topicId(), currentTick, computeSessionCooldown(rumor), false);
                rumorIndex++;
                visitedClusters++;
                continue;
            }

            ClusterShareResult result = shareWithCluster(context, storyteller, storytellerLedger, cluster, rumor,
                storytellerKnowledge);
            visitedClusters++;

            if (result.shared()) {
                sharedRumors.add(rumor);
                if (context.tryMarkBeat("gossip_circle_cluster_" + clusterIndex, 2400)) {
                    Text cueText = GossipNarration.buildCircleCue(storyteller, cluster, rumor, currentTick);
                    EmotionContextCues.sendCue(context.owner(),
                        "social.gossip.circle." + context.pet().getUuidAsString() + "." + clusterIndex,
                        context.pet(),
                        cueText,
                        2400);
                }
                float cost = computeClusterCost(rumor, result.totalListeners(), result.newListeners());
                boolean depleted = storyteller.consumeGossipBudget(rumor.topicId(), cost, currentTick);
                if (depleted) {
                    storyteller.concludeGossipSession(rumor.topicId(), currentTick, computeSessionCooldown(rumor), true);
                    sessionEnded = true;
                    rumorIndex++;
                    break;
                }
            } else if (!GossipTopics.isAbstract(rumor.topicId())) {
                storytellerLedger.deferRumor(rumor.topicId());
            }
            rumorIndex++;
        }

        if (!sessionEnded && rumorIndex < rumors.size()) {
            for (int i = rumorIndex; i < rumors.size(); i++) {
                RumorEntry leftover = rumors.get(i);
                if (!GossipTopics.isAbstract(leftover.topicId())) {
                    storytellerLedger.deferRumor(leftover.topicId());
                }
            }
        }

        if (visitedClusters > 0 && clusterCount > 0) {
            int nextCursor = (clusterCursor + Math.max(1, visitedClusters)) % clusterCount;
            storyteller.setStateData(PetComponent.StateKeys.GOSSIP_CLUSTER_CURSOR, nextCursor);
            context.setGossipClusterCursor(nextCursor);
        }

        if (sharedRumors.isEmpty()) {
            recordGossipStall(storyteller, currentTick);
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
        resetGossipStall(storyteller);
    }

    private ClusterShareResult shareWithCluster(SocialContextSnapshot context,
                                                PetComponent storyteller,
                                                PetGossipLedger storytellerLedger,
                                                List<SocialContextSnapshot.NeighborSample> cluster,
                                                RumorEntry rumor,
                                                float storytellerKnowledge) {
        boolean isAbstract = !storytellerLedger.hasRumor(rumor.topicId())
            && GossipTopics.isAbstract(rumor.topicId());
        boolean witnessedAny = false;
        float ubuntuEcho = 0f;
        float loyaltyEcho = 0f;
        float warmthEcho = 0f;
        float guardianEcho = 0f;
        float admirationEcho = 0f;
        boolean sharedAny = false;
        int totalListeners = 0;
        int newListeners = 0;

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
            HarmonyGossipProfile profile = HarmonyGossipBridge.evaluate(storyteller, listener);
            RumorTone tone = RumorTone.classify(rumor, context.currentTick(), profile);

            if (witnessed) {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
                float loyaltyBase = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, true);
                float loyalty = profile.adjustPositive(loyaltyBase);
                if (loyalty > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
                    float loyaltyFeedback = profile.adjustStorytellerEcho(loyaltyBase);
                    loyaltyEcho = Math.max(loyaltyEcho, loyaltyFeedback * 0.6f);
                }
                float guardianBase = GossipSocialHelper.solidarityGuardianDelta(rumor, knowledgeGap, true);
                float guardian = profile.adjustPositive(guardianBase);
                if (guardian > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.GUARDIAN_VIGIL, guardian);
                    float guardianFeedback = profile.adjustStorytellerEcho(guardianBase);
                    guardianEcho = Math.max(guardianEcho, guardianFeedback * 0.6f);
                }
                float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                float warmth = profile.adjustPositive(warmthBase);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
                    float warmthFeedback = profile.adjustStorytellerEcho(warmthBase);
                    warmthEcho = Math.max(warmthEcho, warmthFeedback * 0.7f);
                }
                float ubuntuBase = GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, true);
                float ubuntuFeedback = profile.adjustStorytellerEcho(ubuntuBase);
                ubuntuEcho = Math.max(ubuntuEcho, ubuntuFeedback);
                witnessedAny = true;
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                admirationEcho = Math.max(admirationEcho, profile.admirationBonus());
                sharedAny = true;
                totalListeners++;
                if (storyteller.registerGossipListener(rumor.topicId(), otherPet.getUuid())) {
                    newListeners++;
                }
                continue;
            }

            if (listenerLedger.hasHeardRecently(rumor.topicId(), context.currentTick())) {
                listenerLedger.registerDuplicateHeard(rumor.topicId(), context.currentTick());
                float frustration = profile.adjustFrustration(GossipSocialHelper.frustrationDelta(rumor));
                if (frustration > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, frustration);
                }
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                listener.optOutOfGossip(context.currentTick());
                continue;
            }

            if (isAbstract) {
                listenerLedger.registerAbstractHeard(rumor.topicId(), context.currentTick());
                float curiosityBase = GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, true);
                float curiosity = profile.adjustCuriosity(curiosityBase);
                if (curiosity > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.CURIOUS, curiosity);
                }
                float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, true);
                float warmth = profile.adjustPositive(warmthBase);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.65f);
                    float warmthFeedback = profile.adjustStorytellerEcho(warmthBase);
                    warmthEcho = Math.max(warmthEcho, warmthFeedback * 0.5f);
                }
                float ubuntuBase = GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false);
                float ubuntuFeedback = profile.adjustStorytellerEcho(ubuntuBase);
                ubuntuEcho = Math.max(ubuntuEcho, ubuntuFeedback);
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                admirationEcho = Math.max(admirationEcho, profile.admirationBonus());
                sharedAny = true;
                totalListeners++;
                if (storyteller.registerGossipListener(rumor.topicId(), otherPet.getUuid())) {
                    newListeners++;
                }
            } else {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), alreadyKnows);
                if (alreadyKnows) {
                    float loyaltyBase = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, false);
                    float loyalty = profile.adjustPositive(loyaltyBase);
                    if (loyalty > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
                        float loyaltyFeedback = profile.adjustStorytellerEcho(loyaltyBase);
                        loyaltyEcho = Math.max(loyaltyEcho, loyaltyFeedback * 0.5f);
                    }
                    float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                    float warmth = profile.adjustPositive(warmthBase);
                    if (warmth > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.75f);
                        float warmthFeedback = profile.adjustStorytellerEcho(warmthBase);
                        warmthEcho = Math.max(warmthEcho, warmthFeedback * 0.55f);
                    }
                    float ubuntuBase = GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false);
                    float ubuntuFeedback = profile.adjustStorytellerEcho(ubuntuBase);
                    ubuntuEcho = Math.max(ubuntuEcho, ubuntuFeedback);
                } else {
                    float curiosityBase = GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, false);
                    float curiosity = profile.adjustCuriosity(curiosityBase);
                    if (curiosity > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.CURIOUS, curiosity);
                    }
                    float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstract);
                    float warmth = profile.adjustPositive(warmthBase);
                    if (warmth > 0f) {
                        context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
                        float warmthFeedback = profile.adjustStorytellerEcho(warmthBase);
                        warmthEcho = Math.max(warmthEcho, warmthFeedback * 0.5f);
                    }
                    float ubuntuBase = GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, false);
                    float ubuntuFeedback = profile.adjustStorytellerEcho(ubuntuBase);
                    ubuntuEcho = Math.max(ubuntuEcho, ubuntuFeedback);
                }
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                admirationEcho = Math.max(admirationEcho, profile.admirationBonus());
                sharedAny = true;
                totalListeners++;
                if (storyteller.registerGossipListener(rumor.topicId(), otherPet.getUuid())) {
                    newListeners++;
                }
            }
        }

        if (newListeners > 0) {
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
        if (admirationEcho > 0f) {
            context.pushEmotion(storyteller, PetComponent.Emotion.PRIDE, admirationEcho * 0.4f);
        }
        if (witnessedAny && guardianEcho <= 0f) {
            float guardian = GossipSocialHelper.solidarityGuardianDelta(rumor, 0.2f, true) * 0.4f;
            if (guardian > 0f) {
                context.pushEmotion(storyteller, PetComponent.Emotion.GUARDIAN_VIGIL, guardian);
            }
        }
        return new ClusterShareResult(sharedAny, totalListeners, newListeners, witnessedAny);
    }

    private float computeClusterCost(RumorEntry rumor, int totalListeners, int newListeners) {
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float base = 0.9f + (intensity * 0.6f);
        base += Math.max(0, newListeners) * 0.45f;
        int repeatListeners = Math.max(0, totalListeners - Math.max(0, newListeners));
        base += repeatListeners * 0.2f;
        return MathHelper.clamp(base, 0.5f, 3.5f);
    }

    private long computeSessionCooldown(RumorEntry rumor) {
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float confidence = MathHelper.clamp(rumor.confidence(), 0f, 1f);
        return 120L + Math.round((intensity + confidence) * 80f);
    }

    private void recordGossipStall(PetComponent component, long currentTick) {
        if (component == null) {
            return;
        }
        int streak = component.getStateData(PetComponent.StateKeys.GOSSIP_STALL_COUNT, Integer.class, 0);
        streak = Math.min(16, streak + 1);
        if (streak >= MAX_STALL_PASSES) {
            component.clearStateData(PetComponent.StateKeys.GOSSIP_STALL_COUNT);
            component.optOutOfGossip(currentTick);
        } else {
            component.setStateData(PetComponent.StateKeys.GOSSIP_STALL_COUNT, streak);
        }
    }

    private void resetGossipStall(PetComponent component) {
        if (component != null) {
            component.clearStateData(PetComponent.StateKeys.GOSSIP_STALL_COUNT);
        }
    }

    private record ClusterShareResult(boolean shared, int totalListeners, int newListeners, boolean witnessedAny) {
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
