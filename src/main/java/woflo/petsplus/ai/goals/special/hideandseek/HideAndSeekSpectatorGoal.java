package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class HideAndSeekSpectatorGoal extends AbstractHideAndSeekRoleGoal {

    private int gossipCooldown;
    private UUID observedHider;
    private int observedHiderTicks;

    public HideAndSeekSpectatorGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK_SPECTATOR), EnumSet.of(Control.LOOK));
    }

    @Override
    protected HideAndSeekSessionManager.Role targetRole() {
        return HideAndSeekSessionManager.Role.SPECTATOR;
    }

    @Override
    protected EnumSet<HideAndSeekSessionManager.Phase> permittedPhases() {
        return EnumSet.allOf(HideAndSeekSessionManager.Phase.class);
    }

    @Override
    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        Vec3d anchor = view.anchor();
        if (anchor != null) {
            mob.getLookControl().lookAt(anchor.x, anchor.y, anchor.z);
        }
        if (activePhase != HideAndSeekSessionManager.Phase.SEEK && observedHider != null) {
            resetObservation();
        }
        if (activePhase == HideAndSeekSessionManager.Phase.SEEK) {
            maybeRatOut(view);
        }
    }

    @Override
    protected void onQuirkEvent(HideAndSeekSessionManager.QuirkEvent event) {
        if (event.quirk() == HideAndSeekSessionManager.Quirk.CELEBRATION_SPIN
            || event.quirk() == HideAndSeekSessionManager.Quirk.TAIL_WIGGLE) {
            mob.swingHand(Hand.MAIN_HAND);
        }
    }

    @Override
    protected void onCaptureEvent(HideAndSeekSessionManager.CaptureEvent event) {
        mob.swingHand(Hand.MAIN_HAND);
        resetObservation();
    }

    @Override
    protected void onStartGoal() {
        super.onStartGoal();
        gossipCooldown = 0;
        resetObservation();
    }

    private void maybeRatOut(HideAndSeekSessionManager.SessionView view) {
        if (sessionId == null || !manager.isPoorSport(sessionId, mob.getUuid())) {
            resetObservation();
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            resetObservation();
            return;
        }
        if (gossipCooldown > 0) {
            gossipCooldown--;
        }
        Entity hider = findHiderWithSight(sw, view);
        if (!(hider instanceof MobEntity hiderMob) || !hiderMob.isAlive()) {
            resetObservation();
            return;
        }
        long now = sw.getTime();
        trackObservedHider(hider);
        if (observedHiderTicks < 14) {
            manager.noteBetrayalOpportunity(
                view.sessionId(),
                mob.getUuid(),
                hider.getUuid(),
                HideAndSeekSessionManager.BetrayalTrigger.SIGHTLINE,
                0.0,
                now
            );
            return;
        }
        if (gossipCooldown > 0) {
            return;
        }
        gossipCooldown = 28;
        double closeness = computeCloseness(hider, view.profile().supportOrbitRadius * 3.5);
        double spite = computeBetrayalImpulse(hiderMob);
        double positiveSpite = Math.max(0.0, spite);
        double baseWeight = MathHelper.clamp(positiveSpite * 0.65 + closeness * 0.35, 0.0, 1.0);
        double observationFactor = MathHelper.clamp((observedHiderTicks - 12) / 70.0, 0.0, 1.0);
        double opportunityWeight = MathHelper.clamp(baseWeight * (0.35 + observationFactor * 0.65), 0.0, 1.0);
        if (opportunityWeight < 0.1) {
            manager.noteBetrayalOpportunity(
                view.sessionId(),
                mob.getUuid(),
                hider.getUuid(),
                HideAndSeekSessionManager.BetrayalTrigger.SIGHTLINE,
                0.0,
                now
            );
            return;
        }
        double readiness = manager.noteBetrayalOpportunity(
            view.sessionId(),
            mob.getUuid(),
            hider.getUuid(),
            HideAndSeekSessionManager.BetrayalTrigger.SIGHTLINE,
            opportunityWeight,
            now
        );
        if (readiness < HideAndSeekSessionManager.BETRAYAL_REQUIRED_MOTIVATION) {
            return;
        }
        double baseChance = 0.03 + 0.012 * Math.min(4, view.roundsCompleted());
        double finalChance = MathHelper.clamp(baseChance + readiness * 0.2 + positiveSpite * 0.12, 0.0, 0.28);
        if (ThreadLocalRandom.current().nextDouble() > finalChance) {
            return;
        }
        Vec3d hint = new Vec3d(hider.getX(), hider.getY(), hider.getZ());
        if (!manager.reportBetrayal(view.sessionId(), mob.getUuid(), hider.getUuid(), hint, now)) {
            return;
        }
        resetObservation();
        PetComponent watcher = PetComponent.get(mob);
        if (watcher != null) {
            watcher.pushEmotion(PetComponent.Emotion.RESTLESS, 0.03f);
            watcher.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.02f);
        }
        PetComponent hiderComponent = PetComponent.get(hiderMob);
        if (hiderComponent != null) {
            hiderComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.04f);
            hiderComponent.recordEntityInteraction(mob.getUuid(), InteractionType.THEFT, 1.0f, 0.6f, 1.0f);
        }
        mob.swingHand(Hand.MAIN_HAND);
    }

    private Entity findHiderWithSight(ServerWorld world, HideAndSeekSessionManager.SessionView view) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<UUID, HideAndSeekSessionManager.Role> entry : view.roles().entrySet()) {
            if (entry.getValue() != HideAndSeekSessionManager.Role.HIDER) {
                continue;
            }
            Entity candidate = world.getEntity(entry.getKey());
            if (!(candidate instanceof MobEntity mobEntity) || !mobEntity.isAlive()) {
                continue;
            }
            if (!mob.getVisibilityCache().canSee(candidate) && !mobEntity.getVisibilityCache().canSee(mob)) {
                continue;
            }
            double distance = mob.squaredDistanceTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        double threshold = MathHelper.square(view.profile().supportOrbitRadius * 3.5);
        return best != null && mob.squaredDistanceTo(best) <= threshold ? best : null;
    }

    private double computeCloseness(Entity target, double maxDistance) {
        double distance = Math.sqrt(mob.squaredDistanceTo(target));
        return MathHelper.clamp(1.0 - distance / Math.max(0.01, maxDistance), 0.0, 1.0);
    }

    private double computeBetrayalImpulse(MobEntity hiderMob) {
        PetComponent watcher = PetComponent.get(mob);
        if (watcher == null) {
            return 0.0;
        }
        UUID hiderId = hiderMob.getUuid();
        double spite = 0.0;
        RelationshipType relation = watcher.getRelationshipType(hiderId);
        if (relation != null) {
            spite += switch (relation) {
                case HOSTILE -> 0.5;
                case RIVAL -> 0.32;
                case WARY -> 0.22;
                case NEUTRAL -> 0.08;
                case TRUSTED_AUTHORITY -> -0.06;
                case FUN_ACQUAINTANCE -> -0.1;
                case FRIEND -> -0.22;
                case COMPANION -> -0.32;
                case STRANGER -> 0.06;
            };
        }
        float trust = watcher.getTrustWith(hiderId);
        if (trust < 0.0f) {
            spite += MathHelper.clamp(-trust * 0.4f, 0.0f, 0.26f);
        } else {
            spite -= MathHelper.clamp(trust * 0.15f, 0.0f, 0.12f);
        }
        float respect = watcher.getRespectWith(hiderId);
        spite += MathHelper.clamp(0.22f - respect, 0.0f, 0.1f);
        float affection = watcher.getAffectionWith(hiderId);
        spite -= MathHelper.clamp(affection * 0.28f, 0.0f, 0.18f);
        return MathHelper.clamp(spite, -0.35, 0.75);
    }

    private void trackObservedHider(Entity hider) {
        if (hider == null) {
            resetObservation();
            return;
        }
        UUID id = hider.getUuid();
        if (!id.equals(observedHider)) {
            observedHider = id;
            observedHiderTicks = 0;
        }
        observedHiderTicks++;
    }

    private void resetObservation() {
        observedHider = null;
        observedHiderTicks = 0;
    }
}
