package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Director goal owns the phase machine and role assignment for the active
 * session.
 */
public final class HideAndSeekDirectorGoal extends AbstractHideAndSeekRoleGoal {

    private HideAndSeekSessionManager.Phase trackedPhase = HideAndSeekSessionManager.Phase.WAITING;
    private int phaseTicks;

    public HideAndSeekDirectorGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK_DIRECTOR), EnumSet.noneOf(Control.class));
    }

    @Override
    protected void onStartGoal() {
        super.onStartGoal();
        trackedPhase = currentView != null ? currentView.phase() : HideAndSeekSessionManager.Phase.WAITING;
        phaseTicks = 0;
    }

    @Override
    protected HideAndSeekSessionManager.Role targetRole() {
        return HideAndSeekSessionManager.Role.DIRECTOR;
    }

    @Override
    protected EnumSet<HideAndSeekSessionManager.Phase> permittedPhases() {
        return EnumSet.allOf(HideAndSeekSessionManager.Phase.class);
    }

    @Override
    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        if (trackedPhase != view.phase()) {
            trackedPhase = view.phase();
            phaseTicks = 0;
        }

        switch (trackedPhase) {
            case WAITING -> handleWaiting(view);
            case FORMATION -> handleFormation(view);
            case COUNTDOWN -> handleCountdown(view);
            case SEEK -> handleSeek(view);
            case CELEBRATE -> handleCelebrate(view);
        }

        phaseTicks++;
        if (mob.getEntityWorld() instanceof ServerWorld sw) {
            manager.updateAnchor(view.sessionId(), new Vec3d(mob.getX(), mob.getY(), mob.getZ()), sw.getTime());
        } else {
            manager.updateAnchor(view.sessionId(), new Vec3d(mob.getX(), mob.getY(), mob.getZ()));
        }
    }

    private void handleWaiting(HideAndSeekSessionManager.SessionView view) {
        if (view.participants().size() <= 1) {
            return;
        }
        Map<UUID, HideAndSeekSessionManager.Role> assignments = manager.buildDefaultRoles(view, mob.getUuid());
        UUID seeker = assignments.entrySet().stream()
            .filter(entry -> entry.getValue() == HideAndSeekSessionManager.Role.SEEKER)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        manager.assignRoles(view.sessionId(), assignments, seeker);
        manager.setPhase(view.sessionId(), HideAndSeekSessionManager.Phase.FORMATION);
        phaseTicks = 0;
        trackedPhase = HideAndSeekSessionManager.Phase.FORMATION;
    }

    private void handleFormation(HideAndSeekSessionManager.SessionView view) {
        if (!view.roles().containsValue(HideAndSeekSessionManager.Role.SEEKER)) {
            Map<UUID, HideAndSeekSessionManager.Role> assignments = manager.buildDefaultRoles(view, mob.getUuid());
            UUID seeker = assignments.entrySet().stream()
                .filter(entry -> entry.getValue() == HideAndSeekSessionManager.Role.SEEKER)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
            manager.assignRoles(view.sessionId(), assignments, seeker);
            return;
        }
        if (phaseTicks < view.profile().joinerGraceTicks) {
            return;
        }
        manager.setPhase(view.sessionId(), HideAndSeekSessionManager.Phase.COUNTDOWN);
        phaseTicks = 0;
        trackedPhase = HideAndSeekSessionManager.Phase.COUNTDOWN;
    }

    private void handleCountdown(HideAndSeekSessionManager.SessionView view) {
        if (view.seekerId() == null || !view.roles().containsKey(view.seekerId())) {
            Map<UUID, HideAndSeekSessionManager.Role> assignments = manager.buildDefaultRoles(view, mob.getUuid());
            UUID seeker = assignments.entrySet().stream()
                .filter(entry -> entry.getValue() == HideAndSeekSessionManager.Role.SEEKER)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
            manager.assignRoles(view.sessionId(), assignments, seeker);
            return;
        }
        if (phaseTicks < view.profile().countdownTicks) {
            return;
        }
        manager.setPhase(view.sessionId(), HideAndSeekSessionManager.Phase.SEEK);
        phaseTicks = 0;
        trackedPhase = HideAndSeekSessionManager.Phase.SEEK;
    }

    private void handleSeek(HideAndSeekSessionManager.SessionView view) {
        boolean anyHider = view.roles().values().stream()
            .anyMatch(role -> role == HideAndSeekSessionManager.Role.HIDER);
        if (anyHider && phaseTicks < view.profile().seekTimeoutTicks) {
            return;
        }
        manager.setPhase(view.sessionId(), HideAndSeekSessionManager.Phase.CELEBRATE);
        manager.markRoundComplete(view.sessionId());
        phaseTicks = 0;
        trackedPhase = HideAndSeekSessionManager.Phase.CELEBRATE;
    }

    private void handleCelebrate(HideAndSeekSessionManager.SessionView view) {
        if (phaseTicks < view.profile().celebrateTicks) {
            return;
        }
        Map<UUID, HideAndSeekSessionManager.Role> assignments = manager.buildDefaultRoles(view, mob.getUuid());
        UUID seeker = assignments.entrySet().stream()
            .filter(entry -> entry.getValue() == HideAndSeekSessionManager.Role.SEEKER)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        if (!assignments.isEmpty()) {
            manager.assignRoles(view.sessionId(), assignments, seeker);
        }
        manager.setPhase(view.sessionId(), HideAndSeekSessionManager.Phase.WAITING);
        phaseTicks = 0;
        trackedPhase = HideAndSeekSessionManager.Phase.WAITING;
    }
}
