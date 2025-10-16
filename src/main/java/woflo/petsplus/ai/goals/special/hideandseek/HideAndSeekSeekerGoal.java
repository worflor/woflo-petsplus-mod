package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class HideAndSeekSeekerGoal extends AbstractHideAndSeekRoleGoal {

    private static final double SEARCH_REACHED_DISTANCE_SQ = 1.5;

    private Vec3d searchTarget;
    private int searchCooldown;
    private long lastRecordedHintTick = Long.MIN_VALUE;

    public HideAndSeekSeekerGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK_SEEKER), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected HideAndSeekSessionManager.Role targetRole() {
        return HideAndSeekSessionManager.Role.SEEKER;
    }

    @Override
    protected EnumSet<HideAndSeekSessionManager.Phase> permittedPhases() {
        return EnumSet.of(
            HideAndSeekSessionManager.Phase.COUNTDOWN,
            HideAndSeekSessionManager.Phase.SEEK,
            HideAndSeekSessionManager.Phase.CELEBRATE
        );
    }

    @Override
    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        switch (activePhase) {
            case COUNTDOWN -> handleCountdown(view);
            case SEEK -> chaseHider(view);
            case CELEBRATE -> mob.getNavigation().stop();
            default -> {
            }
        }
    }

    @Override
    protected void onStartGoal() {
        super.onStartGoal();
        searchTarget = null;
        searchCooldown = 0;
        lastRecordedHintTick = Long.MIN_VALUE;
    }

    private void handleCountdown(HideAndSeekSessionManager.SessionView view) {
        mob.getNavigation().stop();
        Vec3d anchor = view.anchor();
        if (anchor != null) {
            mob.getLookControl().lookAt(anchor.x, anchor.y, anchor.z);
        }
    }

    private void chaseHider(HideAndSeekSessionManager.SessionView view) {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }
        long now = sw.getTime();

        // Check for captures before moving
        Entity visibleTarget = null;
        double visibleDistance = Double.MAX_VALUE;
        for (Map.Entry<UUID, HideAndSeekSessionManager.Role> entry : view.roles().entrySet()) {
            if (entry.getValue() != HideAndSeekSessionManager.Role.HIDER) {
                continue;
            }
            Entity candidate = sw.getEntity(entry.getKey());
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            double distance = mob.squaredDistanceTo(candidate.getX(), candidate.getY(), candidate.getZ());
            if (distance <= view.profile().seekerCaptureSq) {
                if (manager.captureHider(view.sessionId(), mob.getUuid(), candidate.getUuid(), now)) {
                    mob.swingHand(Hand.MAIN_HAND);
                    mob.getNavigation().stop();
                    return;
                }
            }
            if (mob.getVisibilityCache().canSee(candidate) && distance < visibleDistance) {
                visibleDistance = distance;
                visibleTarget = candidate;
            }
        }

        if (visibleTarget != null) {
            Vec3d targetPos = new Vec3d(visibleTarget.getX(), visibleTarget.getY(), visibleTarget.getZ());
            searchTarget = targetPos;
            searchCooldown = 80;
            if (now - lastRecordedHintTick > 20) {
                manager.recordSoftHint(view.sessionId(), visibleTarget.getUuid(), targetPos, mob.getUuid(), now);
                lastRecordedHintTick = now;
            }
            mob.getNavigation().startMovingTo(visibleTarget, view.profile().seekerSpeed);
            return;
        }

        HideAndSeekSessionManager.HideHint hint = selectHint(view, now);
        if (hint != null) {
            searchTarget = resolveHintPosition(hint, view);
            searchCooldown = 80;
        } else if (searchTarget == null
            || mob.squaredDistanceTo(searchTarget.x, searchTarget.y, searchTarget.z) < SEARCH_REACHED_DISTANCE_SQ
            || searchCooldown <= 0) {
            searchTarget = pickSearchPoint(view);
            searchCooldown = 80;
        } else {
            searchCooldown--;
        }

        if (searchTarget == null) {
            mob.getNavigation().stop();
            return;
        }
        mob.getLookControl().lookAt(searchTarget.x, searchTarget.y, searchTarget.z);
        mob.getNavigation().startMovingTo(searchTarget.x, searchTarget.y, searchTarget.z, view.profile().seekerSpeed * 0.95);
    }

    private HideAndSeekSessionManager.HideHint selectHint(HideAndSeekSessionManager.SessionView view, long now) {
        return view.hints().values().stream()
            .filter(hint -> now - hint.tick() <= HideAndSeekSessionManager.HINT_STALE_TICKS)
            .max(Comparator
                .comparingInt((HideAndSeekSessionManager.HideHint hint) -> hint.betrayal() ? 1 : 0)
                .thenComparingLong(HideAndSeekSessionManager.HideHint::tick))
            .orElse(null);
    }

    private Vec3d resolveHintPosition(HideAndSeekSessionManager.HideHint hint,
                                       HideAndSeekSessionManager.SessionView view) {
        Vec3d base = hint.position();
        if (hint.betrayal()) {
            return base;
        }
        double radius = Math.max(1.5, view.profile().supportOrbitRadius);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        double distance = radius * (0.35 + ThreadLocalRandom.current().nextDouble() * 0.45);
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        return base.add(offsetX, 0.0, offsetZ);
    }

    private Vec3d pickSearchPoint(HideAndSeekSessionManager.SessionView view) {
        Vec3d anchor = view.anchor();
        double radius = Math.max(4.0, view.profile().scanRadius * 0.55);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        double distance = radius * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
        double x = (anchor != null ? anchor.x : mob.getX()) + Math.cos(angle) * distance;
        double z = (anchor != null ? anchor.z : mob.getZ()) + Math.sin(angle) * distance;
        double y = anchor != null ? anchor.y : mob.getY();
        return new Vec3d(x, y, z);
    }

    @Override
    protected void onCaptureEvent(HideAndSeekSessionManager.CaptureEvent event) {
        if (!event.view().roles().containsValue(HideAndSeekSessionManager.Role.HIDER)) {
            mob.getNavigation().stop();
        }
    }

    @Override
    protected void onQuirkEvent(HideAndSeekSessionManager.QuirkEvent event) {
        switch (event.quirk()) {
            case SEEKER_POUNCE -> {
                mob.swingHand(Hand.MAIN_HAND);
                mob.getJumpControl().setActive();
            }
            case CELEBRATION_SPIN -> mob.swingHand(Hand.MAIN_HAND);
            default -> {
            }
        }
    }

    @Override
    protected void onHideHintEvent(HideAndSeekSessionManager.HideHintEvent event) {
        if (activePhase != HideAndSeekSessionManager.Phase.SEEK) {
            return;
        }
        HideAndSeekSessionManager.HideHint hint = event.view().hints().get(event.targetId());
        if (hint == null) {
            return;
        }
        searchTarget = resolveHintPosition(hint, event.view());
        searchCooldown = 80;
    }
}
