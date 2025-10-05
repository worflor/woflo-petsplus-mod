package woflo.petsplus.behavior.social;

import net.minecraft.text.Text;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.coordination.PetSwarmIndex;

/**
 * Handles pack-scale social observations (group size, age mix, proximity) and
 * mood contagion effects.
 */
public class PackCircleRoutine implements SocialBehaviorRoutine {

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        return true;
    }

    @Override
    public void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick) {
        PetSocialData self = context.petData();
        final long selfAge = self.age();
        SocialContextSnapshot.NeighborSummary summary = context.ensureNeighborSample(swarm);
        PackObservations observations = new PackObservations();
        observations.nearbyCount = summary.packNeighborCount();
        observations.strongestResonance = summary.packStrongestBondResonance();
        observations.closestDistance = summary.closestPackDistance();
        observations.closestData = summary.closestPackNeighbor();

        for (PetSocialData other : summary.packNeighbors()) {
            long otherAge = other.age();
            if (otherAge > selfAge * 2) {
                observations.hasEldest = true;
            } else if (otherAge > selfAge) {
                observations.hasOlder = true;
            } else if (otherAge < Math.max(1L, selfAge) / 2L) {
                observations.hasYounger = true;
            }

            double ageRatio = (double) otherAge / Math.max(selfAge, 1L);
            if (Math.abs(ageRatio - 1.0) <= 0.2) {
                observations.hasSimilarAge = true;
            }
            if (otherAge < 24000) {
                observations.hasNewborn = true;
            }
        }

        context.setPackObservations(observations.nearbyCount, observations.hasOlder, observations.hasYounger,
            observations.hasEldest, observations.hasSimilarAge, observations.hasNewborn,
            observations.strongestResonance, observations.closestData, observations.closestDistance);
        context.setMoodNeighbors(summary.packMoodNeighbors());
    }

    @Override
    public void applyEffects(SocialContextSnapshot context) {
        if (!context.hasPackContext()) {
            return;
        }

        applyMoodContagion(context);
        applyPackEmotions(context);
    }

    private void applyMoodContagion(SocialContextSnapshot context) {
        for (PetSocialData neighbor : context.moodNeighbors()) {
            PetComponent.Mood mood = neighbor.currentMood();
            if (mood == null) {
                continue;
            }
            float strength = calculateContagionStrength(context, context.petData(), neighbor,
                context.hasEldestPet(), context.hasSimilarAge(), context.strongestBondResonance());
            switch (mood) {
                case HAPPY -> context.pushEmotion(PetComponent.Emotion.CHEERFUL, strength);
                case PLAYFUL -> context.pushEmotion(PetComponent.Emotion.GLEE, strength);
                case CURIOUS -> context.pushEmotion(PetComponent.Emotion.CURIOUS, strength);
                case BONDED -> context.pushEmotion(PetComponent.Emotion.UBUNTU, strength);
                case CALM -> context.pushEmotion(PetComponent.Emotion.LAGOM, strength + 0.01f);
                case PASSIONATE -> context.pushEmotion(PetComponent.Emotion.KEFI, strength);
                case YUGEN -> context.pushEmotion(PetComponent.Emotion.YUGEN, strength);
                case FOCUSED -> context.pushEmotion(PetComponent.Emotion.FOCUSED, strength);
                case SISU -> context.pushEmotion(PetComponent.Emotion.SISU, strength);
                case SAUDADE -> context.pushEmotion(PetComponent.Emotion.SAUDADE, strength);
                case PROTECTIVE -> context.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, strength);
                case RESTLESS -> context.pushEmotion(PetComponent.Emotion.RESTLESS, strength);
                case AFRAID -> context.pushEmotion(PetComponent.Emotion.ANGST, strength + 0.01f);
                case ANGRY -> context.pushEmotion(PetComponent.Emotion.FRUSTRATION, strength);
            }
        }
    }

    private float calculateContagionStrength(SocialContextSnapshot context, PetSocialData self, PetSocialData other,
                                             boolean hasEldestPet, boolean hasSimilarAge,
                                             float strongestBondResonance) {
        float strength = 0.02f;
        if (self.age() < 72000) {
            strength += 0.01f;
        }
        if (hasEldestPet && other.age() > self.age() * 2) {
            strength += 0.015f;
        }
        if (hasSimilarAge) {
            strength += 0.005f;
        }
        if (strongestBondResonance > 0.8f) {
            strength += 0.01f;
        }
        double relativeSpeed = self.relativeSpeedTo(other);
        if (relativeSpeed < 0.05) {
            strength += 0.005f;
        } else if (relativeSpeed > 0.3) {
            strength *= 0.75f;
        }
        if (context.areMutuallyFacing(other, 30.0)) {
            strength += 0.004f;
        }
        if (!context.isNeighborCalm(other, 0.4)) {
            strength *= 0.9f;
        }
        return strength;
    }

    private void applyPackEmotions(SocialContextSnapshot context) {
        PetComponent component = context.component();
        PetSocialData self = context.petData();
        long petAge = self.age();
        int nearbyCount = context.nearbyPetCount();
        switch (nearbyCount) {
            case 0 -> {
                PetComponent.Mood currentMood = component.getCurrentMood();
                if (currentMood != PetComponent.Mood.CALM && petAge > 48000) {
                    float lonelinessStrength = petAge > 168000 ? 0.05f : 0.03f;
                    context.pushEmotion(PetComponent.Emotion.FERNWEH, lonelinessStrength);
                    context.pushEmotion(PetComponent.Emotion.SAUDADE, lonelinessStrength * 0.6f);
                    if (context.tryMarkBeat("social_lonely", 300)) {
                        EmotionContextCues.sendCue(context.owner(),
                            "social.lonely." + context.pet().getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.social.lonely", context.pet().getDisplayName()),
                            300);
                    }
                }
            }
            case 1 -> {
                context.pushEmotion(PetComponent.Emotion.UBUNTU, 0.04f);
                context.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f);
                if (context.strongestBondResonance() > 0.7f) {
                    context.pushEmotion(PetComponent.Emotion.HIRAETH, 0.02f);
                }
                if (context.tryMarkBeat("social_pair", 400)) {
                    EmotionContextCues.sendCue(context.owner(),
                        "social.pair." + context.pet().getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.social.pair", context.pet().getDisplayName()),
                        400);
                }
            }
            default -> {
                if (nearbyCount <= 3) {
                    context.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.06f);
                    context.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.04f);
                    if (context.hasSimilarAge()) {
                        context.pushEmotion(PetComponent.Emotion.GLEE, 0.03f);
                    }
                } else {
                    context.pushEmotion(PetComponent.Emotion.KEFI, 0.05f);
                    context.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.07f);
                    context.pushEmotion(PetComponent.Emotion.PRIDE, 0.04f);
                }
            }
        }

        if (context.hasEldestPet() && context.tryMarkBeat("social_eldest", 400)) {
            context.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.05f);
            context.pushEmotion(PetComponent.Emotion.HIRAETH, 0.03f);
            EmotionContextCues.sendCue(context.owner(),
                "social.eldest." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.eldest", context.pet().getDisplayName()),
                400);
        } else if (context.hasOlderPet() && context.tryMarkBeat("social_elder", 350)) {
            context.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.03f);
            context.pushEmotion(PetComponent.Emotion.FOCUSED, 0.02f);
            EmotionContextCues.sendCue(context.owner(),
                "social.elder." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.elder", context.pet().getDisplayName()),
                350);
        }

        PetSocialData closest = context.closestPetData();
        if (closest != null && context.closestDistance() <= 4
            && context.isNeighborCalm(closest, 0.05)
            && context.relativeSpeedTo(closest) < 0.08
            && context.areMutuallyFacing(closest, 25.0)
            && context.tryMarkBeat("social_intimate", 200)) {
            context.pushEmotion(PetComponent.Emotion.UBUNTU, 0.02f);
        }
    }

    private static final class PackObservations {
        int nearbyCount;
        boolean hasOlder;
        boolean hasYounger;
        boolean hasEldest;
        boolean hasSimilarAge;
        boolean hasNewborn;
        float strongestResonance;
        double closestDistance = Double.MAX_VALUE;
        PetSocialData closestData;
    }
}
