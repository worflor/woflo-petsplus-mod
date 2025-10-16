package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared scaffolding for role-specific hide-and-seek behaviours.
 */
abstract class AbstractHideAndSeekRoleGoal extends AdaptiveGoal implements HideAndSeekSessionManager.SessionListener {

    protected final HideAndSeekSessionManager manager = HideAndSeekSessionManager.getInstance();
    protected HideAndSeekSessionManager.SessionView currentView;
    protected HideAndSeekSessionManager.Phase activePhase = HideAndSeekSessionManager.Phase.WAITING;
    protected UUID sessionId;

    AbstractHideAndSeekRoleGoal(MobEntity mob, GoalDefinition definition, EnumSet<Control> controls) {
        super(mob, definition, controls);
    }

    @Override
    protected boolean canStartGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }
        Optional<HideAndSeekSessionManager.SessionView> view = manager.findSessionFor(mob.getUuid());
        if (view.isEmpty()) {
            return false;
        }
        HideAndSeekSessionManager.SessionView snapshot = view.get();
        if (snapshot.roleOf(mob.getUuid()) != targetRole()) {
            return false;
        }
        if (!permittedPhases().contains(snapshot.phase())) {
            return false;
        }
        sessionId = snapshot.sessionId();
        currentView = snapshot;
        activePhase = snapshot.phase();
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (sessionId == null) {
            return false;
        }
        Optional<HideAndSeekSessionManager.SessionView> view = manager.findSession(sessionId);
        if (view.isEmpty()) {
            return false;
        }
        HideAndSeekSessionManager.SessionView snapshot = view.get();
        if (snapshot.roleOf(mob.getUuid()) != targetRole()) {
            return false;
        }
        if (!permittedPhases().contains(snapshot.phase())) {
            return false;
        }
        currentView = snapshot;
        activePhase = snapshot.phase();
        return true;
    }

    @Override
    protected void onStartGoal() {
        manager.registerListener(mob.getUuid(), this);
    }

    @Override
    protected void onStopGoal() {
        manager.unregisterListener(mob.getUuid(), this);
        mob.getNavigation().stop();
        currentView = null;
        sessionId = null;
        activePhase = HideAndSeekSessionManager.Phase.WAITING;
    }

    @Override
    protected void onTickGoal() {
        if (currentView == null) {
            return;
        }
        performRoleTick(currentView);
    }

    protected abstract HideAndSeekSessionManager.Role targetRole();

    protected abstract EnumSet<HideAndSeekSessionManager.Phase> permittedPhases();

    protected void performRoleTick(HideAndSeekSessionManager.SessionView view) {
        // Subclasses override for behaviour.
    }

    @Override
    protected float calculateEngagement() {
        return 0.6f;
    }

    @Override
    public void onSessionEvent(HideAndSeekSessionManager.SessionEvent event) {
        if (sessionId == null || !sessionId.equals(event.sessionId())) {
            return;
        }
        if (event instanceof HideAndSeekSessionManager.SessionClosedEvent) {
            requestStop();
            return;
        }
        if (event instanceof HideAndSeekSessionManager.RoleAssignmentEvent assignment
            && assignment.petId().equals(mob.getUuid())) {
            if (assignment.role() != targetRole()) {
                requestStop();
                return;
            }
            currentView = assignment.view();
            activePhase = assignment.view().phase();
        } else if (event instanceof HideAndSeekSessionManager.PhaseChangeEvent phaseChange) {
            if (!permittedPhases().contains(phaseChange.current())) {
                requestStop();
                return;
            }
            currentView = phaseChange.view();
            activePhase = phaseChange.current();
        } else if (event instanceof HideAndSeekSessionManager.SessionMetricsEvent metrics) {
            currentView = metrics.view();
            activePhase = metrics.view().phase();
        } else if (event instanceof HideAndSeekSessionManager.QuirkEvent quirk) {
            currentView = quirk.view();
            activePhase = quirk.view().phase();
            onQuirkEvent(quirk);
        } else if (event instanceof HideAndSeekSessionManager.CaptureEvent captureEvent) {
            currentView = captureEvent.view();
            activePhase = captureEvent.view().phase();
            onCaptureEvent(captureEvent);
        } else if (event instanceof HideAndSeekSessionManager.HideHintEvent hintEvent) {
            currentView = hintEvent.view();
            activePhase = hintEvent.view().phase();
            onHideHintEvent(hintEvent);
        }
        handleSessionEvent(event);
    }

    protected void handleSessionEvent(HideAndSeekSessionManager.SessionEvent event) {
        // subclasses may override
    }

    protected void onQuirkEvent(HideAndSeekSessionManager.QuirkEvent event) {
        // optional hook per role
    }

    protected void onCaptureEvent(HideAndSeekSessionManager.CaptureEvent event) {
        // optional hook per role
    }

    protected void onHideHintEvent(HideAndSeekSessionManager.HideHintEvent event) {
        // optional hook per role
    }
}
