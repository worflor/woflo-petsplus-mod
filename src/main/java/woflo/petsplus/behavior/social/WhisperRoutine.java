package woflo.petsplus.behavior.social;

import java.util.List;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.state.PetSwarmIndex;

/**
 * Handles intimate one-on-one social moments such as greetings and empathy
 * checks. Runs on a slower cadence to match the original throttling logic.
 */
public class WhisperRoutine implements SocialBehaviorRoutine {

    private static final double WHISPER_RADIUS = 12.0;
    private static final long ADVANCED_TICK_INTERVAL = 120L;
    private static final long REUNION_THRESHOLD = 24000L;
    private static final String SOCIAL_MEMORY_PREFIX = "social_memory_";

    @Override
    public boolean shouldRun(SocialContextSnapshot context) {
        return context.currentTick() % ADVANCED_TICK_INTERVAL == 0;
    }

    @Override
    public void gatherContext(SocialContextSnapshot context, PetSwarmIndex swarm, long currentTick) {
        SocialContextSnapshot.NeighborSummary summary = context.ensureNeighborSample(swarm);
        double maxDistanceSq = WHISPER_RADIUS * WHISPER_RADIUS;
        List<SocialContextSnapshot.NeighborSample> nearest = summary.nearestWithin(maxDistanceSq, 3);
        context.setNearestNeighbors(nearest);
    }

    @Override
    public void applyEffects(SocialContextSnapshot context) {
        PetComponent component = context.component();
        for (SocialContextSnapshot.NeighborSample sample : context.nearestNeighbors()) {
            MobEntity otherPet = sample.pet();
            if (otherPet == null) {
                continue;
            }
            PetSocialData otherData = sample.data();
            String otherPetId = otherPet.getUuidAsString();
            String socialMemoryKey = SOCIAL_MEMORY_PREFIX + otherPetId;

            Long lastInteraction = component.getStateData(socialMemoryKey, Long.class);
            boolean isFirstMeeting = lastInteraction == null;
            boolean isReunion = lastInteraction != null
                && (context.currentTick() - lastInteraction) > REUNION_THRESHOLD;

            if (isFirstMeeting) {
                component.pushEmotion(PetComponent.Emotion.CURIOUS, 0.06f);
                if (context.petData().age() < 72000) {
                    component.pushEmotion(PetComponent.Emotion.GLEE, 0.04f);
                    component.pushEmotion(PetComponent.Emotion.PLAYFULNESS, 0.05f);
                } else {
                    component.pushEmotion(PetComponent.Emotion.VIGILANT, 0.03f);
                }
                component.setStateData(socialMemoryKey, context.currentTick());

                if (context.tryMarkBeat("first_meeting_" + otherPetId, 800)) {
                    EmotionContextCues.sendCue(context.owner(),
                        "social.first_meeting." + context.pet().getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.social.first_meeting",
                            context.pet().getDisplayName(), otherPet.getDisplayName()),
                        400);
                }
            } else if (isReunion) {
                long separationTime = context.currentTick() - lastInteraction;
                float reunionStrength = Math.min(0.08f, separationTime / 120000f);

                component.pushEmotion(PetComponent.Emotion.GLEE, reunionStrength);
                component.pushEmotion(PetComponent.Emotion.LOYALTY, reunionStrength * 0.7f);
                component.setStateData(socialMemoryKey, context.currentTick());
            }

            PetComponent.Mood mood = otherData.currentMood();
            if (mood == PetComponent.Mood.AFRAID || mood == PetComponent.Mood.ANGRY) {
                float empathyStrength = context.petData().bondStrength() * 0.04f;
                long sinceStress = context.currentTick() - otherData.lastThreatRecoveryTick();
                if (sinceStress >= 0 && sinceStress < 200) {
                    empathyStrength *= 0.5f;
                }
                component.pushEmotion(PetComponent.Emotion.EMPATHY, empathyStrength);
                if (context.tryMarkBeat("empathy_" + otherPetId, 600)) {
                    EmotionContextCues.sendCue(context.owner(),
                        "social.empathy." + context.pet().getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.social.empathy",
                            context.pet().getDisplayName(), otherPet.getDisplayName()),
                        300);
                }
            }
        }
    }
}
