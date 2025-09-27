package woflo.petsplus.behavior.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;

/**
 * Utility helpers shared by gossip social routines. Keeps downtime checks in a
 * single place so both circle and whisper logic gate on the same definition of
 * "calm enough to chat."
 */
final class GossipSocialHelper {

    private static final float RESTLESS_THRESHOLD = 0.3f;
    private static final float ANGER_THRESHOLD = 0.25f;

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
}

