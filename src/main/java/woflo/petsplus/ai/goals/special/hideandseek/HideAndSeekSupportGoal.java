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

public final class HideAndSeekSupportGoal extends AbstractHideAndSeekRoleGoal {

    private double orbitAngle;
    private int betrayalCheckCooldown;
    private UUID observedHider;
    private int observedHiderTicks;

    public HideAndSeekSupportGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK_SUPPORT), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected HideAndSeekSessionManager.Role targetRole() {
        return HideAndSeekSessionManager.Role.SUPPORT;
    }

    @Override
    protected EnumSet<HideAndSeekSessionManager.Phase> permittedPhases() {
        return EnumSet.of(
            HideAndSeekSessionManager.Phase.FORMATION,
            HideAndSeekSessionManager.Phase.SEEK,
            HideAndSeekSessionManager.Phase.CELEBRATE
        );
    }

    @Override
    protected void onStartGoal() {
        super.onStartGoal();
        orbitAngle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        betrayalCheckCooldown = 0;
        resetObservation();
    }

    @Override
    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        if (activePhase != HideAndSeekSessionManager.Phase.SEEK && observedHider != null) {
            resetObservation();
        }
        switch (activePhase) {
            case FORMATION, SEEK -> orbitAnchor(view);
            case CELEBRATE -> mob.getNavigation().stop();
            default -> {
            }
        }
        if (activePhase == HideAndSeekSessionManager.Phase.SEEK) {
            maybeRatOut(view);
        }
    }

    private void orbitAnchor(HideAndSeekSessionManager.SessionView view) {
        Vec3d anchor = view.anchor();
        if (anchor == null) {
            return;
        }
        orbitAngle += 0.12;
        double radius = view.profile().supportOrbitRadius;
        double x = anchor.x + Math.cos(orbitAngle) * radius;
        double z = anchor.z + Math.sin(orbitAngle) * radius;
        mob.getNavigation().startMovingTo(x, anchor.y, z, view.profile().supportSpeed);
    }

    @Override
    protected void onQuirkEvent(HideAndSeekSessionManager.QuirkEvent event) {
        switch (event.quirk()) {
            case TAIL_WIGGLE -> mob.swingHand(Hand.MAIN_HAND);
            case CELEBRATION_SPIN -> {
                orbitAngle += Math.PI / 2.0;
                mob.swingHand(Hand.MAIN_HAND);
            }
            default -> {
            }
        }
    }

    @Override
    protected void onCaptureEvent(HideAndSeekSessionManager.CaptureEvent event) {
        if (activePhase == HideAndSeekSessionManager.Phase.SEEK) {
            orbitAngle += Math.PI / 3.0;
            mob.swingHand(Hand.MAIN_HAND);
        }
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
        if (betrayalCheckCooldown > 0) {
            betrayalCheckCooldown--;
        }
        Entity seeker = view.seekerId() != null ? sw.getEntity(view.seekerId()) : null;
        if (seeker == null || !seeker.isAlive()) {
            resetObservation();
            return;
        }
        Entity hider = findHiderWithSight(sw, view);
        if (!(hider instanceof MobEntity hiderMob) || !hiderMob.isAlive()) {
            resetObservation();
            return;
        }
        long now = sw.getTime();
        trackObservedHider(hider);
        if (observedHiderTicks < 12) {
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
        if (betrayalCheckCooldown > 0) {
            return;
        }
        betrayalCheckCooldown = 24;
        double closeness = computeCloseness(hider, view.profile().supportOrbitRadius * 3.0);
        double spite = computeBetrayalImpulse(hiderMob);
        double positiveSpite = Math.max(0.0, spite);
        double baseWeight = MathHelper.clamp(positiveSpite * 0.7 + closeness * 0.3, 0.0, 1.0);
        double observationFactor = MathHelper.clamp((observedHiderTicks - 10) / 60.0, 0.0, 1.0);
        double opportunityWeight = MathHelper.clamp(baseWeight * (0.4 + observationFactor * 0.6), 0.0, 1.0);
        if (opportunityWeight < 0.12) {
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
        double baseChance = 0.045 + 0.015 * Math.min(4, view.roundsCompleted());
        double finalChance = MathHelper.clamp(baseChance + readiness * 0.22 + positiveSpite * 0.15, 0.0, 0.32);
        if (ThreadLocalRandom.current().nextDouble() > finalChance) {
            return;
        }
        Vec3d hint = new Vec3d(hider.getX(), hider.getY(), hider.getZ());
        if (!manager.reportBetrayal(view.sessionId(), mob.getUuid(), hider.getUuid(), hint, now)) {
            return;
        }
        resetObservation();
        PetComponent reporter = PetComponent.get(mob);
        if (reporter != null) {
            reporter.pushEmotion(PetComponent.Emotion.RESTLESS, 0.04f);
            reporter.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.03f);
        }
        PetComponent hiderComponent = PetComponent.get(hiderMob);
        if (hiderComponent != null) {
            hiderComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.05f);
            hiderComponent.recordEntityInteraction(mob.getUuid(), InteractionType.THEFT, 1.0f, 0.7f, 1.0f);
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
        double threshold = MathHelper.square(view.profile().supportOrbitRadius * 3.0);
        return best != null && mob.squaredDistanceTo(best) <= threshold ? best : null;
    }

    private double computeCloseness(Entity target, double maxDistance) {
        double distance = Math.sqrt(mob.squaredDistanceTo(target));
        return MathHelper.clamp(1.0 - distance / Math.max(0.01, maxDistance), 0.0, 1.0);
    }

    private double computeBetrayalImpulse(MobEntity hiderMob) {
        PetComponent reporter = PetComponent.get(mob);
        if (reporter == null) {
            return 0.0;
        }
        UUID hiderId = hiderMob.getUuid();
        double spite = 0.0;
        RelationshipType relation = reporter.getRelationshipType(hiderId);
        if (relation != null) {
            spite += switch (relation) {
                case HOSTILE -> 0.55;
                case RIVAL -> 0.4;
                case WARY -> 0.28;
                case NEUTRAL -> 0.12;
                case TRUSTED_AUTHORITY -> -0.08;
                case FUN_ACQUAINTANCE -> -0.12;
                case FRIEND -> -0.25;
                case COMPANION -> -0.35;
                case STRANGER -> 0.08;
            };
        }
        float trust = reporter.getTrustWith(hiderId);
        if (trust < 0.0f) {
            spite += MathHelper.clamp(-trust * 0.45f, 0.0f, 0.3f);
        } else {
            spite -= MathHelper.clamp(trust * 0.18f, 0.0f, 0.15f);
        }
        float respect = reporter.getRespectWith(hiderId);
        spite += MathHelper.clamp(0.25f - respect, 0.0f, 0.12f);
        float affection = reporter.getAffectionWith(hiderId);
        spite -= MathHelper.clamp(affection * 0.3f, 0.0f, 0.2f);
        return MathHelper.clamp(spite, -0.4, 0.9);
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
