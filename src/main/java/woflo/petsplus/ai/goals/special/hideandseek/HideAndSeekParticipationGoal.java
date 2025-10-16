package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight dispatcher that ensures each mob is registered with the
 * hide-and-seek session manager and listens for role updates. The dispatcher
 * itself does not claim movement controls so the specialised role goals can run
 * concurrently.
 */
public final class HideAndSeekParticipationGoal extends AdaptiveGoal implements HideAndSeekSessionManager.SessionListener {

    private final HideAndSeekSessionManager manager = HideAndSeekSessionManager.getInstance();

    private HideAndSeekSessionManager.SessionView currentView;
    private HideAndSeekSessionManager.Role activeRole = HideAndSeekSessionManager.Role.SPECTATOR;
    private HideAndSeekSessionManager.Phase activePhase = HideAndSeekSessionManager.Phase.WAITING;
    private UUID sessionId;

    public HideAndSeekParticipationGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK), EnumSet.noneOf(Control.class));
    }

    @Override
    protected boolean canStartGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }

        PetComponent selfComponent = PetComponent.get(mob);
        if (selfComponent == null || selfComponent.isDormant() || selfComponent.isInCombat()) {
            return false;
        }

        long now = sw.getTime();
        manager.tickMaintenance(sw, now);

        Optional<HideAndSeekSessionManager.SessionView> existing = manager.findSessionFor(mob.getUuid());
        if (existing.isPresent()) {
            setSession(existing.get());
            return true;
        }

        UUID ownerId = selfComponent.getOwnerUuid();
        if (ownerId == null) {
            return false;
        }

        Optional<HideAndSeekSessionManager.SessionView> joinable = manager.findJoinableSession(ownerId, sw.getRegistryKey())
            .flatMap(view -> manager.joinSession(mob, view));
        if (joinable.isPresent()) {
            setSession(joinable.get());
            return true;
        }

        Optional<HideAndSeekSessionManager.SessionView> nearby = manager.findClosestSession(sw, mob, ownerId)
            .flatMap(view -> manager.joinSession(mob, view));
        if (nearby.isPresent()) {
            setSession(nearby.get());
            return true;
        }

        PetContext context = PetContext.capture(mob, selfComponent);
        double scanRadius = Math.max(6.0, 8.0 + context.socialCharge() * 4.0);
        Box scanBox = mob.getBoundingBox().expand(scanRadius);

        List<MobEntity> neighbours = sw.getEntitiesByClass(MobEntity.class, scanBox, entity ->
            entity != mob && entity.isAlive() && !entity.isRemoved());
        List<MobEntity> eligible = new ArrayList<>();
        Map<UUID, PetContext> contexts = new HashMap<>();
        contexts.put(mob.getUuid(), context);
        for (MobEntity candidate : neighbours) {
            PetComponent component = PetComponent.get(candidate);
            if (component == null || !Objects.equals(component.getOwnerUuid(), ownerId)) {
                continue;
            }
            if (component.isDormant() || component.isInCombat()) {
                continue;
            }
            if (manager.findSessionFor(candidate.getUuid()).isPresent()) {
                continue;
            }
            contexts.put(candidate.getUuid(), PetContext.capture(candidate, component));
            eligible.add(candidate);
            if (eligible.size() >= HideAndSeekSessionManager.MAX_PARTICIPANTS - 1) {
                break;
            }
        }

        if (eligible.isEmpty()) {
            return false;
        }

        HideAndSeekSessionManager.SessionView created = manager.createSession(sw, mob, selfComponent, contexts, eligible, now);
        if (created == null) {
            return false;
        }
        setSession(created);
        return true;
    }

    private void setSession(HideAndSeekSessionManager.SessionView view) {
        this.currentView = view;
        this.sessionId = view.sessionId();
        this.activePhase = view.phase();
        this.activeRole = view.roleOf(mob.getUuid());
    }

    @Override
    protected void onStartGoal() {
        manager.registerListener(mob.getUuid(), this);
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
        currentView = view.get();
        activeRole = currentView.roleOf(mob.getUuid());
        activePhase = currentView.phase();
        return currentView.includes(mob.getUuid());
    }

    @Override
    protected void onTickGoal() {
        if (mob.getEntityWorld() instanceof ServerWorld sw) {
            manager.tickMaintenance(sw, sw.getTime());
        }
        if (currentView == null) {
            return;
        }
        // Keep the anchor updated for the director; other roles follow their own goals
        if (activeRole == HideAndSeekSessionManager.Role.DIRECTOR) {
            if (mob.getEntityWorld() instanceof ServerWorld sw) {
                manager.updateAnchor(sessionId, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), sw.getTime());
            } else {
                manager.updateAnchor(sessionId, new Vec3d(mob.getX(), mob.getY(), mob.getZ()));
            }
        }
    }

    @Override
    protected void onStopGoal() {
        manager.unregisterListener(mob.getUuid(), this);
        if (sessionId != null) {
            manager.leaveSession(mob.getUuid());
        }
        currentView = null;
        sessionId = null;
        activeRole = HideAndSeekSessionManager.Role.SPECTATOR;
        activePhase = HideAndSeekSessionManager.Phase.WAITING;
    }

    @Override
    public void onSessionEvent(HideAndSeekSessionManager.SessionEvent event) {
        if (sessionId == null || !sessionId.equals(event.sessionId())) {
            return;
        }
        if (event instanceof HideAndSeekSessionManager.SessionClosedEvent) {
            currentView = null;
            sessionId = null;
            requestStop();
            return;
        }
        if (event instanceof HideAndSeekSessionManager.SessionMetricsEvent metrics) {
            currentView = metrics.view();
            activePhase = metrics.view().phase();
            activeRole = metrics.view().roleOf(mob.getUuid());
        } else if (event instanceof HideAndSeekSessionManager.RoleAssignmentEvent assignment
                   && assignment.petId().equals(mob.getUuid())) {
            currentView = assignment.view();
            activeRole = assignment.role();
            activePhase = assignment.view().phase();
        } else if (event instanceof HideAndSeekSessionManager.PhaseChangeEvent phaseChange) {
            currentView = phaseChange.view();
            activePhase = phaseChange.current();
            activeRole = phaseChange.view().roleOf(mob.getUuid());
        } else if (event instanceof HideAndSeekSessionManager.QuirkEvent quirkEvent) {
            currentView = quirkEvent.view();
            activePhase = quirkEvent.view().phase();
            activeRole = quirkEvent.view().roleOf(mob.getUuid());
        } else if (event instanceof HideAndSeekSessionManager.CaptureEvent captureEvent) {
            currentView = captureEvent.view();
            activePhase = captureEvent.view().phase();
            activeRole = captureEvent.view().roleOf(mob.getUuid());
        }
    }

    public Optional<HideAndSeekSessionManager.SessionView> getCurrentView() {
        return Optional.ofNullable(currentView);
    }

    public HideAndSeekSessionManager.Role getActiveRole() {
        return activeRole;
    }

    public HideAndSeekSessionManager.Phase getActivePhase() {
        return activePhase;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    protected float calculateEngagement() {
        return 0.65f;
    }
}
