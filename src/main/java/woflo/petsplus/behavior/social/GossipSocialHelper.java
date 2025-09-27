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
    private static final float MIN_DELTA = 0.0035f;

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
        float gapBoost = MathHelper.clamp(knowledgeGap * 0.0065f, 0f, 0.01f);
        float base = 0.0045f + (strength * 0.0125f) + gapBoost;
        if (alreadyKnows) {
            base *= 0.4f;
        }
        if (isAbstract) {
            base *= 0.75f;
        }
        return clamp(base, MIN_DELTA, 0.024f);
    }

    static float loyaltyDelta(RumorEntry rumor, float knowledgeGap, boolean witnessed) {
        float strength = rumorStrength(rumor);
        float gapBoost = MathHelper.clamp(knowledgeGap * 0.0055f, 0f, 0.009f);
        float base = 0.0038f + (strength * 0.0105f) + gapBoost;
        if (witnessed) {
            base += 0.0035f;
        }
        return clamp(base, MIN_DELTA, 0.02f);
    }

    static float frustrationDelta(RumorEntry rumor) {
        float confidenceDrop = 1f - MathHelper.clamp(rumor.confidence(), 0f, 1f);
        float shareWear = MathHelper.clamp(rumor.shareCount() * 0.0022f, 0f, 0.008f);
        float base = 0.0042f + (confidenceDrop * 0.014f) + shareWear;
        if (GossipTopics.isAbstract(rumor.topicId())) {
            base *= 0.8f;
        }
        return clamp(base, 0.004f, 0.02f);
    }

    static float storytellerEaseDelta(int sharedCount, float averageStrength) {
        float base = 0.0062f + (averageStrength * 0.0135f) + (sharedCount * 0.0025f);
        return clamp(base, 0.0055f, 0.028f);
    }

    static float storytellerPrideDelta(float averageStrength, float storytellerKnowledge) {
        float knowledgeBoost = MathHelper.clamp(storytellerKnowledge * 0.0025f, 0f, 0.01f);
        float base = 0.0045f + (averageStrength * 0.0105f) + knowledgeBoost;
        return clamp(base, 0.004f, 0.02f);
    }

    static float empathyDelta(RumorEntry rumor, float knowledgeGap) {
        float base = 0.0045f + (rumorStrength(rumor) * 0.0105f)
            + MathHelper.clamp(knowledgeGap * 0.006f, 0f, 0.01f);
        return clamp(base, 0.004f, 0.02f);
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

