package woflo.petsplus.mood.providers;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors the emotional state of nearby bonded companions and the owner so pets feel like a pack.
 */
public class PackContagionProvider implements EmotionProvider {
    private static final double SCAN_RADIUS = 10.0;

    @Override
    public String id() {
        return "pack_contagion";
    }

    @Override
    public int periodHintTicks() {
        return 120;
    }

    @Override
    public void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api) {
        if (comp == null || pet == null || !pet.isAlive()) {
            return;
        }

        UUID ownerId = comp.getOwnerUuid();
        float selfBond = MathHelper.clamp(comp.computeBondResilience(time), 0.25f, 1.0f);

        Box scan = pet.getBoundingBox().expand(SCAN_RADIUS);
        List<MobEntity> neighbours = world.getEntitiesByClass(MobEntity.class, scan,
                other -> other != pet && other.isAlive());

        for (MobEntity other : neighbours) {
            PetComponent otherComp = PetComponent.get(other);
            if (otherComp == null) {
                continue;
            }

            UUID otherOwner = otherComp.getOwnerUuid();
            boolean shareOwner = ownerId != null && ownerId.equals(otherOwner);
            double distSq = pet.squaredDistanceTo(other);
            applyAllyMirror(comp, otherComp, distSq, selfBond, time, shareOwner);
        }

        PlayerEntity owner = comp.getOwner();
        if (owner instanceof ServerPlayerEntity serverOwner) {
            OwnerCombatState state = OwnerCombatState.getOrCreate(serverOwner);
            applyOwnerMirror(comp, state, selfBond, time);
        }
    }

    void applyAllyMirror(PetComponent comp, PetComponent allyComp, double distanceSq, float selfBond, long time, boolean shareOwner) {
        if (allyComp == null) {
            return;
        }
        float otherBond = MathHelper.clamp(allyComp.computeBondResilience(time), 0.25f, 1.0f);
        if (!shareOwner) {
            float trust = Math.min(selfBond, otherBond);
            if (trust < 0.55f) {
                return;
            }
        }

        PetComponent.Emotion dominant = allyComp.getDominantEmotion();
        if (dominant == null) {
            return;
        }

        double maxDistSq = SCAN_RADIUS * SCAN_RADIUS;
        float distanceFactor = (float) MathHelper.clamp(1.0 - distanceSq / Math.max(1.0, maxDistSq), 0.25, 1.0);
        float bondFactor = shareOwner ? 1.0f : Math.min(selfBond, otherBond);
        float contagion = 0.018f * distanceFactor * bondFactor;
        if (contagion > 0f) {
            comp.addContagionShare(dominant, contagion);
        }
    }

    void applyOwnerMirror(PetComponent comp, OwnerCombatState state, float selfBond, long time) {
        float base = 0.015f * MathHelper.clamp(selfBond, 0.25f, 1.0f);
        if (state.isInCombat() || state.recentlyDamaged(time, 80)) {
            comp.addContagionShare(PetComponent.Emotion.PROTECTIVENESS, base * 1.2f);
            comp.addContagionShare(PetComponent.Emotion.ANGST, base * 0.75f);
        } else {
            comp.addContagionShare(PetComponent.Emotion.UBUNTU, base * 0.8f);
            comp.addContagionShare(PetComponent.Emotion.RELIEF, base * 0.5f);
        }
    }
}
