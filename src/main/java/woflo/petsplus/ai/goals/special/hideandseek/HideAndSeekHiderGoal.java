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

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public final class HideAndSeekHiderGoal extends AbstractHideAndSeekRoleGoal {

    private Vec3d hideTarget;
    private Vec3d homeSpot;
    private int lineOfSightCooldown;
    private int shuffleCooldown;

    public HideAndSeekHiderGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK_HIDER), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected HideAndSeekSessionManager.Role targetRole() {
        return HideAndSeekSessionManager.Role.HIDER;
    }

    @Override
    protected EnumSet<HideAndSeekSessionManager.Phase> permittedPhases() {
        return EnumSet.of(
            HideAndSeekSessionManager.Phase.FORMATION,
            HideAndSeekSessionManager.Phase.COUNTDOWN,
            HideAndSeekSessionManager.Phase.SEEK,
            HideAndSeekSessionManager.Phase.CELEBRATE
        );
    }

    @Override
    protected void onStartGoal() {
        super.onStartGoal();
        hideTarget = null;
        homeSpot = null;
        lineOfSightCooldown = 0;
        shuffleCooldown = 0;
    }

    @Override
    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        switch (activePhase) {
            case FORMATION -> moveToAnchor(view);
            case COUNTDOWN, SEEK -> moveToHideSpot(view);
            case CELEBRATE -> mob.getNavigation().stop();
            default -> {
            }
        }
    }

    private void moveToAnchor(HideAndSeekSessionManager.SessionView view) {
        Vec3d anchor = view.anchor();
        if (anchor == null) {
            return;
        }
        double radius = view.profile().formationRadius * 0.85;
        if (homeSpot == null) {
            homeSpot = anchor.add(randomOffset(radius));
        }
        if (hideTarget == null || hideTarget.squaredDistanceTo(homeSpot) > MathHelper.square(radius * 1.5)) {
            hideTarget = homeSpot;
        }
        mob.getNavigation().startMovingTo(hideTarget.x, hideTarget.y, hideTarget.z, view.profile().hiderSpeed);
    }

    private void moveToHideSpot(HideAndSeekSessionManager.SessionView view) {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }
        Vec3d anchor = view.anchor();
        if (anchor == null) {
            return;
        }
        if (homeSpot == null) {
            homeSpot = anchor.add(randomOffset(view.profile().supportOrbitRadius * 0.75));
        }
        if (lineOfSightCooldown > 0) {
            lineOfSightCooldown--;
        }
        if (shuffleCooldown > 0) {
            shuffleCooldown--;
        }

        Entity seeker = view.seekerId() != null ? sw.getEntity(view.seekerId()) : null;
        if (seeker != null && seeker.isAlive()) {
            boolean canSee = mob.getVisibilityCache().canSee(seeker)
                || (seeker instanceof MobEntity seekerMob && seekerMob.getVisibilityCache().canSee(mob));
            double distanceSq = mob.squaredDistanceTo(seeker);
            double senseRadiusSq = MathHelper.square(view.profile().supportOrbitRadius * 2.0);
            if ((canSee || distanceSq < senseRadiusSq * 0.5) && lineOfSightCooldown <= 0) {
                hideTarget = clampToHome(computeEvadeTarget(anchor, homeSpot, seeker, view), homeSpot, view.profile().supportOrbitRadius);
                lineOfSightCooldown = 50 + ThreadLocalRandom.current().nextInt(40);
                shuffleCooldown = 40 + ThreadLocalRandom.current().nextInt(30);
            }
        }

        if (hideTarget == null
            || mob.squaredDistanceTo(hideTarget.x, hideTarget.y, hideTarget.z) < 0.6
            || shuffleCooldown <= 0) {
            Vec3d candidate = homeSpot.add(randomOffset(view.profile().supportOrbitRadius * 0.6));
            hideTarget = clampToHome(candidate, homeSpot, view.profile().supportOrbitRadius);
            shuffleCooldown = 60 + ThreadLocalRandom.current().nextInt(40);
        }

        mob.getNavigation().startMovingTo(hideTarget.x, hideTarget.y, hideTarget.z, view.profile().hiderSpeed);
    }

    @Override
    protected void onQuirkEvent(HideAndSeekSessionManager.QuirkEvent event) {
        switch (event.quirk()) {
            case PEEKABOO -> {
                Vec3d anchor = event.view().anchor();
                if (anchor != null) {
                    hideTarget = anchor.add(randomOffset(event.view().profile().formationRadius * 0.6));
                    mob.getLookControl().lookAt(anchor.x, anchor.y, anchor.z);
                }
                mob.swingHand(Hand.MAIN_HAND);
            }
            case CELEBRATION_SPIN -> mob.swingHand(Hand.MAIN_HAND);
            case SEEKER_POUNCE -> hideTarget = null;
            default -> {
            }
        }
    }

    @Override
    protected void onCaptureEvent(HideAndSeekSessionManager.CaptureEvent event) {
        if (event.hiderId().equals(mob.getUuid())) {
            hideTarget = null;
        } else if (activePhase == HideAndSeekSessionManager.Phase.SEEK) {
            hideTarget = null;
        }
    }

    private Vec3d randomOffset(double radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = radius * (0.5 + random.nextDouble() * 0.5);
        double x = Math.cos(angle) * distance;
        double z = Math.sin(angle) * distance;
        return new Vec3d(x, 0.0, z);
    }

    private Vec3d computeEvadeTarget(Vec3d anchor,
                                     Vec3d home,
                                     Entity seeker,
                                     HideAndSeekSessionManager.SessionView view) {
        Vec3d origin = home != null ? home : anchor != null ? anchor : new Vec3d(mob.getX(), mob.getY(), mob.getZ());
        Vec3d seekerPlane = new Vec3d(seeker.getX(), origin.y, seeker.getZ());
        Vec3d away = origin.subtract(seekerPlane);
        if (away.lengthSquared() < 0.0001) {
            away = new Vec3d(1.0, 0.0, 0.0);
        }
        away = away.normalize();
        Vec3d perpendicular = new Vec3d(-away.z, 0.0, away.x);
        if (perpendicular.lengthSquared() < 0.0001) {
            perpendicular = new Vec3d(0.0, 0.0, 1.0);
        } else {
            perpendicular = perpendicular.normalize();
        }
        double radius = view.profile().supportOrbitRadius * 0.75;
        double forward = radius * (0.45 + ThreadLocalRandom.current().nextDouble() * 0.35);
        double lateral = radius * ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
        Vec3d candidate = origin.add(away.multiply(forward)).add(perpendicular.multiply(lateral));
        return new Vec3d(candidate.x, origin.y, candidate.z);
    }

    private Vec3d clampToHome(Vec3d candidate, Vec3d home, double maxRadius) {
        if (home == null) {
            return candidate;
        }
        Vec3d offset = candidate.subtract(home);
        double distanceSq = offset.lengthSquared();
        double maxRadiusSq = MathHelper.square(maxRadius);
        if (distanceSq <= maxRadiusSq) {
            return candidate;
        }
        if (distanceSq < 0.0001) {
            return home;
        }
        Vec3d clamped = offset.normalize().multiply(maxRadius * 0.95);
        return new Vec3d(home.x + clamped.x, candidate.y, home.z + clamped.z);
    }

    @Override
    protected void onHideHintEvent(HideAndSeekSessionManager.HideHintEvent event) {
        if (!event.targetId().equals(mob.getUuid())) {
            return;
        }
        double supportRadius = event.view().profile().supportOrbitRadius;
        ServerWorld sw = mob.getEntityWorld() instanceof ServerWorld server ? server : null;
        if (event.betrayal()) {
            lineOfSightCooldown = 0;
            shuffleCooldown = 30;
            Vec3d anchor = event.view().anchor();
            Entity seeker = null;
            if (sw != null && event.view().seekerId() != null) {
                seeker = sw.getEntity(event.view().seekerId());
            }
            if (seeker != null) {
                hideTarget = clampToHome(computeEvadeTarget(anchor, homeSpot, seeker, event.view()), homeSpot, supportRadius);
            } else {
                hideTarget = clampToHome(event.hint(), homeSpot, supportRadius);
            }
            PetComponent component = PetComponent.get(mob);
            if (component != null) {
                component.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.07f);
                component.pushEmotion(PetComponent.Emotion.ANGST, 0.05f);
                if (event.reporterId() != null) {
                    component.recordEntityInteraction(event.reporterId(), InteractionType.THREAT, 1.0f, 0.8f, 1.0f);
                }
            }
            mob.swingHand(Hand.MAIN_HAND);
        } else {
            if (shuffleCooldown <= 0) {
                Vec3d candidate = event.hint().add(randomOffset(supportRadius * 0.4));
                hideTarget = clampToHome(candidate, homeSpot, supportRadius);
                shuffleCooldown = 45;
            }
        }
    }
}
