package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.text.Text;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.PetSwarmIndex;

/**
 * Handles pack-scale social observations (group size, age mix, proximity) and
 * mood contagion effects.
 */
public class PackCircleRoutine implements SocialBehaviorRoutine {

    private static final double PACK_SCAN_RADIUS = 8.0;

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        return true;
    }

    @Override
    public void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick) {
        PetSocialData self = context.petData();
        final long selfAge = self.age();
        PackObservations observations = new PackObservations();

        swarm.forEachNeighbor(context.pet(), context.component(), PACK_SCAN_RADIUS, (entry, distance) -> {
            if (distance > 64) {
                return;
            }

            PetSocialData other = context.getOrCreateNeighborData(entry, currentTick);
            observations.nearbyCount++;

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

            float bondDiff = Math.abs(self.bondStrength() - other.bondStrength());
            if (bondDiff < 0.2f) {
                observations.strongestResonance = Math.max(observations.strongestResonance,
                    Math.min(self.bondStrength(), other.bondStrength()));
            }

            if (distance < observations.closestDistance) {
                observations.closestDistance = distance;
                observations.closestData = other;
            }

            if (other.currentMood() != null) {
                observations.moodNeighbors.add(other);
            }
        });

        context.setPackObservations(observations.nearbyCount, observations.hasOlder, observations.hasYounger,
            observations.hasEldest, observations.hasSimilarAge, observations.hasNewborn,
            observations.strongestResonance, observations.closestData, observations.closestDistance);
        context.setMoodNeighbors(observations.moodNeighbors);
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
        PetComponent component = context.component();
        for (PetSocialData neighbor : context.moodNeighbors()) {
            PetComponent.Mood mood = neighbor.currentMood();
            if (mood == null) {
                continue;
            }
            float strength = calculateContagionStrength(context.petData(), neighbor,
                context.hasEldestPet(), context.hasSimilarAge(), context.strongestBondResonance());
            switch (mood) {
                case HAPPY -> component.pushEmotion(PetComponent.Emotion.CHEERFUL, strength);
                case PLAYFUL -> component.pushEmotion(PetComponent.Emotion.GLEE, strength);
                case CURIOUS -> component.pushEmotion(PetComponent.Emotion.CURIOUS, strength);
                case BONDED -> component.pushEmotion(PetComponent.Emotion.UBUNTU, strength);
                case CALM -> component.pushEmotion(PetComponent.Emotion.LAGOM, strength + 0.01f);
                case PASSIONATE -> component.pushEmotion(PetComponent.Emotion.KEFI, strength);
                case YUGEN -> component.pushEmotion(PetComponent.Emotion.YUGEN, strength);
                case FOCUSED -> component.pushEmotion(PetComponent.Emotion.FOCUSED, strength);
                case SISU -> component.pushEmotion(PetComponent.Emotion.SISU, strength);
                case SAUDADE -> component.pushEmotion(PetComponent.Emotion.SAUDADE, strength);
                case PROTECTIVE -> component.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, strength);
                case RESTLESS -> component.pushEmotion(PetComponent.Emotion.RESTLESS, strength);
                case AFRAID -> component.pushEmotion(PetComponent.Emotion.ANGST, strength + 0.01f);
                case ANGRY -> component.pushEmotion(PetComponent.Emotion.FRUSTRATION, strength);
            }
        }
    }

    private float calculateContagionStrength(PetSocialData self, PetSocialData other,
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
                    component.pushEmotion(PetComponent.Emotion.FERNWEH, lonelinessStrength);
                    component.pushEmotion(PetComponent.Emotion.SAUDADE, lonelinessStrength * 0.6f);
                    if (context.tryMarkBeat("social_lonely", 300)) {
                        EmotionContextCues.sendCue(context.owner(),
                            "social.lonely." + context.pet().getUuidAsString(),
                            Text.translatable("petsplus.emotion_cue.social.lonely", context.pet().getDisplayName()),
                            300);
                    }
                }
            }
            case 1 -> {
                component.pushEmotion(PetComponent.Emotion.UBUNTU, 0.04f);
                component.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.03f);
                if (context.strongestBondResonance() > 0.7f) {
                    component.pushEmotion(PetComponent.Emotion.HIRAETH, 0.02f);
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
                    component.pushEmotion(PetComponent.Emotion.SOBREMESA, 0.06f);
                    component.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.04f);
                    if (context.hasSimilarAge()) {
                        component.pushEmotion(PetComponent.Emotion.GLEE, 0.03f);
                    }
                } else {
                    component.pushEmotion(PetComponent.Emotion.KEFI, 0.05f);
                    component.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.07f);
                    component.pushEmotion(PetComponent.Emotion.PRIDE, 0.04f);
                }
            }
        }

        if (context.hasEldestPet() && context.tryMarkBeat("social_eldest", 400)) {
            component.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.05f);
            component.pushEmotion(PetComponent.Emotion.HIRAETH, 0.03f);
            EmotionContextCues.sendCue(context.owner(),
                "social.eldest." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.eldest", context.pet().getDisplayName()),
                400);
        } else if (context.hasOlderPet() && context.tryMarkBeat("social_elder", 350)) {
            component.pushEmotion(PetComponent.Emotion.MONO_NO_AWARE, 0.03f);
            component.pushEmotion(PetComponent.Emotion.FOCUSED, 0.02f);
            EmotionContextCues.sendCue(context.owner(),
                "social.elder." + context.pet().getUuidAsString(),
                Text.translatable("petsplus.emotion_cue.social.elder", context.pet().getDisplayName()),
                350);
        }

        if (context.closestPetData() != null && context.closestDistance() <= 4
            && context.tryMarkBeat("social_intimate", 200)) {
            component.pushEmotion(PetComponent.Emotion.UBUNTU, 0.02f);
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
        final List<PetSocialData> moodNeighbors = new ArrayList<>();
    }
}
