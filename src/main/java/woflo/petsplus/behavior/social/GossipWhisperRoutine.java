package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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

        PetComponent component = context.component();
        List<RumorEntry> eligible = new ArrayList<>(toShare.size());
        for (RumorEntry rumor : toShare) {
            component.ensureGossipSession(rumor.topicId(), currentTick, rumor);
            if (component.hasGossipSessionBudget(rumor.topicId(), currentTick)) {
                eligible.add(rumor);
            } else {
                component.concludeGossipSession(rumor.topicId(), currentTick, computeSessionCooldown(rumor), false);
            }
        }
        if (eligible.isEmpty()) {
            context.setSharedRumors(Collections.emptyList());
            context.setGossipNeighbors(Collections.emptyList());
            return;
        }
        toShare = eligible;

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

        whisperer.ensureGossipSession(rumor.topicId(), context.currentTick(), rumor);
        if (!whisperer.hasGossipSessionBudget(rumor.topicId(), context.currentTick())) {
            whisperer.concludeGossipSession(rumor.topicId(), context.currentTick(), computeSessionCooldown(rumor), false);
            return;
        }

        UUID listenerId = otherPet.getUuid();

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
        boolean isAbstractTopic = GossipTopics.isAbstract(rumor.topicId());
        HarmonyGossipProfile profile = HarmonyGossipBridge.evaluate(whisperer, listener);
        RumorTone tone = RumorTone.classify(rumor, context.currentTick(), profile);

        boolean sharedAny = false;
        boolean newListener = false;
        boolean witnessedAny = false;

        if (witnessed) {
            listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
            float loyaltyBase = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, true);
            float loyalty = profile.adjustPositive(loyaltyBase);
            if (loyalty > 0f) {
                context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
            }
            float guardianBase = GossipSocialHelper.solidarityGuardianDelta(rumor, knowledgeGap, true);
            float guardian = profile.adjustPositive(guardianBase);
            if (guardian > 0f) {
                context.pushEmotion(listener, PetComponent.Emotion.GUARDIAN_VIGIL, guardian);
            }
            float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstractTopic);
            float warmth = profile.adjustPositive(warmthBase);
            if (warmth > 0f) {
                context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
            }
            float irritation = profile.positiveToneIrritation(tone);
            if (irritation > 0f) {
                context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
            }
            sharedAny = true;
            witnessedAny = true;
            if (whisperer.registerGossipListener(rumor.topicId(), listenerId)) {
                newListener = true;
            }
        } else {
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
                return;
            }

            boolean isAbstract = !whispererLedger.hasRumor(rumor.topicId()) && isAbstractTopic;
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
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.6f);
                }
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                if (whisperer.registerGossipListener(rumor.topicId(), listenerId)) {
                    newListener = true;
                }
                sharedAny = true;
            } else if (alreadyKnows) {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), true);
                whisperer.registerGossipListener(rumor.topicId(), listenerId);
                float loyaltyBase = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap, false);
                float loyalty = profile.adjustPositive(loyaltyBase);
                if (loyalty > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.LOYALTY, loyalty);
                }
                float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstractTopic);
                float warmth = profile.adjustPositive(warmthBase);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth * 0.8f);
                }
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                sharedAny = true;
            } else {
                listenerLedger.ingestRumorFromPeer(rumor, context.currentTick(), false);
                float curiosityBase = GossipSocialHelper.curiosityDelta(rumor, knowledgeGap, false, false);
                float curiosity = profile.adjustCuriosity(curiosityBase);
                if (curiosity > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.CURIOUS, curiosity);
                }
                float warmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap, isAbstractTopic);
                float warmth = profile.adjustPositive(warmthBase);
                if (warmth > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.QUERECIA, warmth);
                }
                float irritation = profile.positiveToneIrritation(tone);
                if (irritation > 0f) {
                    context.pushEmotion(listener, PetComponent.Emotion.FRUSTRATION, irritation);
                }
                if (whisperer.registerGossipListener(rumor.topicId(), listenerId)) {
                    newListener = true;
                }
                sharedAny = true;
            }

            if (newListener) {
                whispererLedger.markShared(rumor.topicId(), context.currentTick());
            }
        }

        if (!sharedAny) {
            return;
        }

        float ubuntuBase = GossipSocialHelper.ubuntuDelta(rumor, knowledgeGap, witnessedAny);
        float ubuntu = profile.adjustStorytellerEcho(ubuntuBase);
        if (ubuntu > 0f) {
            context.pushEmotion(whisperer, PetComponent.Emotion.UBUNTU, ubuntu);
        }
        float tellerWarmthBase = GossipSocialHelper.solidarityWarmthDelta(rumor, knowledgeGap * 0.6f, isAbstractTopic);
        float tellerWarmth = profile.adjustStorytellerEcho(tellerWarmthBase);
        if (tellerWarmth > 0f) {
            context.pushEmotion(whisperer, PetComponent.Emotion.SOBREMESA, tellerWarmth * 0.7f);
            context.pushEmotion(whisperer, PetComponent.Emotion.QUERECIA, tellerWarmth * 0.5f);
        }
        float tellerLoyaltyBase = GossipSocialHelper.loyaltyDelta(rumor, knowledgeGap * 0.5f, witnessedAny);
        float tellerLoyalty = profile.adjustStorytellerEcho(tellerLoyaltyBase) * 0.6f;
        if (tellerLoyalty > 0f) {
            context.pushEmotion(whisperer, PetComponent.Emotion.LOYALTY, tellerLoyalty);
        }
        float tellerGuardianBase = GossipSocialHelper.solidarityGuardianDelta(rumor, knowledgeGap * 0.7f, witnessedAny);
        float tellerGuardian = profile.adjustStorytellerEcho(tellerGuardianBase) * 0.5f;
        if (tellerGuardian > 0f) {
            context.pushEmotion(whisperer, PetComponent.Emotion.GUARDIAN_VIGIL, tellerGuardian);
        }
        float admiration = profile.admirationBonus();
        if (admiration > 0f) {
            context.pushEmotion(whisperer, PetComponent.Emotion.PRIDE, admiration);
        }

        float cost = computeWhisperCost(rumor, newListener, witnessedAny);
        boolean depleted = whisperer.consumeGossipBudget(rumor.topicId(), cost, context.currentTick());
        if (depleted) {
            whisperer.concludeGossipSession(rumor.topicId(), context.currentTick(), computeSessionCooldown(rumor), true);
        }

        if (context.tryMarkBeat("gossip_whisper", 1800)) {
            Text cueText = GossipNarration.buildWhisperCue(whisperer, listener, rumor, context.currentTick());
            EmotionContextCues.sendCue(context.owner(),
                "social.gossip.whisper." + context.pet().getUuidAsString(),
                context.pet(),
                cueText,
                1800);
        }
    }

    private float computeWhisperCost(RumorEntry rumor, boolean newListener, boolean witnessed) {
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float base = 0.8f + intensity * 0.5f;
        if (newListener) {
            base += 0.5f;
        }
        if (witnessed) {
            base += 0.25f;
        }
        return MathHelper.clamp(base, 0.45f, 2.5f);
    }

    private long computeSessionCooldown(RumorEntry rumor) {
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float confidence = MathHelper.clamp(rumor.confidence(), 0f, 1f);
        return 140L + Math.round((intensity + confidence) * 70f);
    }
}
