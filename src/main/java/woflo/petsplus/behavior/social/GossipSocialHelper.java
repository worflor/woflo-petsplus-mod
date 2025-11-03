package woflo.petsplus.behavior.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.state.gossip.RumorEntry;

/**
 * Utility helpers shared by gossip social routines. Keeps downtime checks in a
 * single place so both circle and whisper logic gate on the same definition of
 * "calm enough to chat."
 */
final class GossipSocialHelper {

    private static final float RESTLESS_THRESHOLD = 0.3f;
    private static final float ANGER_THRESHOLD = 0.25f;
    private static final float MIN_DELTA = 0.007f;

    private GossipSocialHelper() {
    }

    static boolean isDowntime(SocialContextSnapshot context, PetComponent component, MobEntity pet) {
        if (component == null || pet == null) {
            return false;
        }

        if (component.isInCombat() || pet.getTarget() != null) {
            return false;
        }

        if (pet.getNavigation().isFollowingPath()) {
            return false;
        }

        if (pet.getVelocity().horizontalLengthSquared() > 0.025f) {
            return false;
        }

        if (component.hasMoodAbove(PetComponent.Mood.RESTLESS, RESTLESS_THRESHOLD)
            || component.hasMoodAbove(PetComponent.Mood.ANGRY, ANGER_THRESHOLD)) {
            return false;
        }

        ServerPlayerEntity owner = context.owner();
        if (owner != null) {
            OwnerCombatState ownerState = StateManager.forWorld(context.world()).getOwnerState(owner);
            long now = context.currentTick();
            if (ownerState != null && (ownerState.isInCombat() || ownerState.recentlyDamaged(now, 60))) {
                return false;
            }
        }

        return true;
    }

    static float curiosityDelta(RumorEntry rumor, float knowledgeGap, boolean alreadyKnows, boolean isAbstract) {
        float strength = rumorStrength(rumor);
        float gapBoost = MathHelper.clamp(knowledgeGap * 0.04f, 0f, 0.06f); // Boosted 2.5x from 0.016f
        float base = 0.012f + (strength * 0.03f) + gapBoost;
        if (alreadyKnows) {
            base *= 0.5f;
        }
        if (isAbstract) {
            base *= 0.8f;
        }
        return clamp(base, 0.008f, 0.05f);
    }

    static float loyaltyDelta(RumorEntry rumor, float knowledgeGap, boolean witnessed) {
        float strength = rumorStrength(rumor);
        float gapBoost = MathHelper.clamp(knowledgeGap * 0.035f, 0f, 0.055f); // Boosted 2.5x from 0.014f
        float confidenceBoost = MathHelper.clamp(rumor.confidence() * 0.01f, 0f, 0.01f);
        float base = 0.01f + (strength * 0.026f) + gapBoost + confidenceBoost;
        if (witnessed) {
            base += 0.008f;
        }
        return clamp(base, 0.007f, 0.046f);
    }

    static float frustrationDelta(RumorEntry rumor) {
        float confidenceDrop = 1f - MathHelper.clamp(rumor.confidence(), 0f, 1f);
        float shareWear = MathHelper.clamp(rumor.shareCount() * 0.005f, 0f, 0.018f);
        float base = 0.01f + (confidenceDrop * 0.032f) + shareWear;
        if (GossipTopics.isAbstract(rumor.topicId())) {
            base *= 0.85f;
        }
        return clamp(base, 0.008f, 0.046f);
    }

    static float storytellerEaseDelta(int sharedCount, float averageStrength) {
        float base = 0.014f + (averageStrength * 0.032f) + (sharedCount * 0.006f);
        return clamp(base, 0.012f, 0.056f);
    }

    static float storytellerPrideDelta(float averageStrength, float storytellerKnowledge) {
        float knowledgeBoost = MathHelper.clamp(storytellerKnowledge * 0.006f, 0f, 0.024f);
        float base = 0.011f + (averageStrength * 0.026f) + knowledgeBoost;
        return clamp(base, 0.009f, 0.048f);
    }

    static float ubuntuDelta(RumorEntry rumor, float knowledgeGap) {
        return ubuntuDelta(rumor, knowledgeGap, false);
    }

    static float ubuntuDelta(RumorEntry rumor, float knowledgeGap, boolean witnessed) {
        float base = 0.011f + (rumorStrength(rumor) * 0.028f)
            + MathHelper.clamp(knowledgeGap * 0.013f, 0f, 0.024f);
        if (witnessed) {
            base += 0.005f + MathHelper.clamp(rumor.confidence() * 0.006f, 0f, 0.006f);
        }
        return clamp(base, 0.009f, 0.05f);
    }

    static float solidarityWarmthDelta(RumorEntry rumor, float knowledgeGap, boolean isAbstract) {
        float confidence = MathHelper.clamp(rumor.confidence(), 0f, 1f);
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float comfortArc = Math.max(0f, 0.55f - Math.abs(0.35f - intensity)) * 0.018f;
        float closenessBoost = MathHelper.clamp((0.35f - knowledgeGap) * 0.012f, -0.01f, 0.012f);
        float base = 0.005f + (confidence * 0.016f) + comfortArc + closenessBoost;
        if (isAbstract) {
            base *= 0.85f;
        }
        return clamp(base, 0f, 0.026f);
    }

    static float solidarityGuardianDelta(RumorEntry rumor, float knowledgeGap, boolean witnessed) {
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float base = Math.max(0f, intensity - 0.55f) * 0.05f
            + MathHelper.clamp(knowledgeGap * 0.01f, 0f, 0.018f);
        if (witnessed) {
            base += 0.006f;
        }
        if (rumor.confidence() > 0.6f) {
            base += 0.004f;
        }
        return clamp(base, 0f, 0.032f);
    }

    static float rumorStrength(RumorEntry rumor) {
        if (rumor == null) {
            return 0f;
        }
        float intensity = MathHelper.clamp(rumor.intensity(), 0f, 1f);
        float confidence = MathHelper.clamp(rumor.confidence(), 0f, 1f);
        if (GossipTopics.isAbstract(rumor.topicId())) {
            intensity *= 0.85f;
        }
        return (intensity * 0.55f) + (confidence * 0.45f);
    }

    private static float clamp(float value, float min, float max) {
        return MathHelper.clamp(value, min, max);
    }
}

