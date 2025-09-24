package woflo.petsplus.mood.providers;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Threat/Combat provider:
 * - Nearby hostile entities → ANGST
 * - Owner recently damaged → PROTECTIVENESS
 * - Pet recently damaged → STARTLE/FRUSTRATION
 */
public class CombatThreatProvider implements EmotionProvider {
    @Override public String id() { return "combat_threat"; }
    @Override public int periodHintTicks() { return 20; } // ~1s

    @Override
    public void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api) {
        // Nearby hostiles (cheap AABB query)
        Box box = pet.getBoundingBox().expand(8.0);
        var hostiles = world.getEntitiesByClass(LivingEntity.class, box, e -> e.getType().isIn(EntityTypeTags.RAIDERS) || e.getType().isIn(EntityTypeTags.SKELETONS) || e.getType().isIn(EntityTypeTags.ZOMBIES));
        if (!hostiles.isEmpty()) {
            api.pushEmotion(pet, PetComponent.Emotion.ANGST, Math.min(0.05f * hostiles.size(), 0.15f));
        }

        // Owner recent damage?
        var owner = comp.getOwner();
        if (owner != null) {
            OwnerCombatState ocs = OwnerCombatState.get(owner);
            if (ocs != null && ocs.recentlyDamaged(time, 60)) {
                api.pushEmotion(pet, PetComponent.Emotion.PROTECTIVENESS, 0.06f);
            }
        }

        // Pet hurt recently check via entity's hurt time (simple signal)
        if (pet.hurtTime > 0) {
            api.pushEmotion(pet, PetComponent.Emotion.STARTLE, 0.04f);
            api.pushEmotion(pet, PetComponent.Emotion.FRUSTRATION, 0.02f);
        }
    }
}
