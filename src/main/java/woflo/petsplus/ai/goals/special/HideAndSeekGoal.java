package woflo.petsplus.ai.goals.special;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.relationships.InteractionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Coordinates a flexible, pet-agnostic hide-and-seek vignette that adapts to the
 * personalities and energy of each group. The session director corrals up to
 * four friends (five total pets), assigns seekers and hiders based on their
 * affinities, and steers multiple rounds while ensuring everyone has room to
 * play.
 */
public class HideAndSeekGoal extends AdaptiveGoal {

    private static final int MAX_PARTICIPANTS = 5;
    private static final int MAX_HIDERS = 2;

    private static final String QUIRK_COUNTER_KEY = "hide_and_seek_sessions";

    private static final Map<UUID, HideSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> SESSION_BY_PET = new ConcurrentHashMap<>();
    private static final Map<UUID, Invitation> INVITATIONS = new ConcurrentHashMap<>();

    private enum Phase {
        WAITING,
        FORMATION,
        COUNTDOWN,
        SEEK,
        CELEBRATE
    }

    private enum MemberRole {
        SEEKER,
        HIDER,
        SUPPORT,
        SPECTATOR
    }

    private static final class Invitation {
        final UUID sessionId;
        final long expiryTick;
        final RegistryKey<World> worldKey;

        Invitation(UUID sessionId, long expiryTick, RegistryKey<World> worldKey) {
            this.sessionId = sessionId;
            this.expiryTick = expiryTick;
            this.worldKey = worldKey;
        }

        boolean isValid(RegistryKey<World> key, long now) {
            return now <= expiryTick && Objects.equals(worldKey, key);
        }
    }

    private static final class SessionProfile {
        final double scanRadius;
        final double formationToleranceSq;
        final double formationRadius;
        final double supportOrbitRadius;
        final double hideArrivalSq;
        final double seekerCaptureSq;
        final int joinerGraceTicks;
        final int formationTimeoutTicks;
        final int countdownTicks;
        final int seekTimeoutTicks;
        final int celebrateTicks;
        final int sessionMaxTicks;
        final double seekerSpeed;
        final double hiderSpeed;
        final double supportSpeed;

        SessionProfile(double scanRadius,
                       double formationToleranceSq,
                       double formationRadius,
                       double supportOrbitRadius,
                       double hideArrivalSq,
                       double seekerCaptureSq,
                       int joinerGraceTicks,
                       int formationTimeoutTicks,
                       int countdownTicks,
                       int seekTimeoutTicks,
                       int celebrateTicks,
                       int sessionMaxTicks,
                       double seekerSpeed,
                       double hiderSpeed,
                       double supportSpeed) {
            this.scanRadius = scanRadius;
            this.formationToleranceSq = formationToleranceSq;
            this.formationRadius = formationRadius;
            this.supportOrbitRadius = supportOrbitRadius;
            this.hideArrivalSq = hideArrivalSq;
            this.seekerCaptureSq = seekerCaptureSq;
            this.joinerGraceTicks = joinerGraceTicks;
            this.formationTimeoutTicks = formationTimeoutTicks;
            this.countdownTicks = countdownTicks;
            this.seekTimeoutTicks = seekTimeoutTicks;
            this.celebrateTicks = celebrateTicks;
            this.sessionMaxTicks = sessionMaxTicks;
            this.seekerSpeed = seekerSpeed;
            this.hiderSpeed = hiderSpeed;
            this.supportSpeed = supportSpeed;
        }

        static SessionProfile from(List<PetContext> contexts) {
            if (contexts.isEmpty()) {
                return defaultProfile();
            }
            double avgMomentum = contexts.stream()
                .mapToDouble(PetContext::behavioralMomentum)
                .average()
                .orElse(0.55);
            double avgSocial = contexts.stream()
                .mapToDouble(PetContext::socialCharge)
                .average()
                .orElse(0.55);
            double avgStamina = contexts.stream()
                .map(PetContext::behaviouralEnergyProfile)
                .filter(Objects::nonNull)
                .mapToDouble(BehaviouralEnergyProfile::physicalStamina)
                .average()
                .orElse(0.6);

            double scanRadius = MathHelper.clamp(8.0 + avgSocial * 6.0, 7.0, 14.0);
            double formationRadius = MathHelper.clamp(1.6 + avgSocial * 1.8, 1.8, 4.6);
            double formationToleranceSq = MathHelper.square(0.9 + avgMomentum * 1.1);
            double orbitRadius = MathHelper.clamp(2.3 + avgSocial * 2.0, 2.4, 5.2);
            double hideArrival = MathHelper.square(0.7 + avgMomentum * 0.9);
            double captureRadius = MathHelper.square(1.5 + avgMomentum * 1.4);

            int joinerGrace = secondsToTicks((int) MathHelper.clamp(4 + avgSocial * 5, 4, 10));
            int formationTimeout = secondsToTicks((int) MathHelper.clamp(8 + (1.0 - avgMomentum) * 6, 7, 16));
            int countdown = secondsToTicks((int) MathHelper.clamp(3 + (1.0 - avgMomentum) * 3, 3, 8));
            int seekTimeout = secondsToTicks((int) MathHelper.clamp(14 + (1.0 - avgStamina) * 10, 12, 26));
            int celebrate = secondsToTicks((int) MathHelper.clamp(3 + avgSocial * 4, 3, 9));
            int sessionMax = secondsToTicks((int) MathHelper.clamp(70 + avgSocial * 40, 60, 140));

            double seekerSpeed = MathHelper.clamp(1.0 + avgMomentum * 0.6, 0.95, 1.7);
            double hiderSpeed = MathHelper.clamp(0.9 + avgMomentum * 0.5, 0.85, 1.5);
            double supportSpeed = MathHelper.clamp(0.8 + avgSocial * 0.5, 0.75, 1.45);

            return new SessionProfile(
                scanRadius,
                formationToleranceSq,
                formationRadius,
                orbitRadius,
                hideArrival,
                captureRadius,
                joinerGrace,
                formationTimeout,
                countdown,
                seekTimeout,
                celebrate,
                sessionMax,
                seekerSpeed,
                hiderSpeed,
                supportSpeed
            );
        }

        private static SessionProfile defaultProfile() {
            return new SessionProfile(
                10.0,
                MathHelper.square(1.1),
                2.2,
                2.8,
                MathHelper.square(1.0),
                MathHelper.square(1.9),
                secondsToTicks(6),
                secondsToTicks(12),
                secondsToTicks(5),
                secondsToTicks(18),
                secondsToTicks(4),
                secondsToTicks(90),
                1.2,
                1.05,
                0.95
            );
        }
    }

    private static final class RoleAffinity {
        final double seekerBias;
        final double hiderBias;
        final double supportBias;

        RoleAffinity(double seekerBias, double hiderBias, double supportBias) {
            this.seekerBias = seekerBias;
            this.hiderBias = hiderBias;
            this.supportBias = supportBias;
        }
    }

    private static final RoleAffinity BASE_AFFINITY = new RoleAffinity(0.5, 0.5, 0.5);

    private static final class HideSession {
        final UUID id = UUID.randomUUID();
        final RegistryKey<World> worldKey;
        final UUID ownerId;
        final UUID directorId;
        final CopyOnWriteArrayList<UUID> participants;
        final Map<UUID, PetContext> contexts;
        SessionProfile profile;
        final Map<UUID, RoleAffinity> roleAffinities;

        final Set<UUID> ready = ConcurrentHashMap.newKeySet();
        final Map<UUID, MemberRole> roles = new ConcurrentHashMap<>();
        final Map<UUID, Boolean> foundHiders = new ConcurrentHashMap<>();
        final Map<UUID, BlockPos> hideAssignments = new ConcurrentHashMap<>();
        final Map<UUID, Vec3d> formationTargets = new ConcurrentHashMap<>();
        final Map<UUID, List<Vec3d>> seekerRoutes = new ConcurrentHashMap<>();
        final Map<UUID, Integer> seekerRouteIndices = new ConcurrentHashMap<>();
        final List<UUID> currentHiders = new CopyOnWriteArrayList<>();
        final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
        final Set<UUID> queuedSpectators = ConcurrentHashMap.newKeySet();
        final CopyOnWriteArrayList<UUID> seekerRotation = new CopyOnWriteArrayList<>();
        volatile boolean closing;

        Phase phase = Phase.WAITING;
        UUID seekerId;
        Vec3d anchor;
        int phaseTicks;
        int roundsCompleted;
        final long createdTick;
        long expiryTick;
        long lastPhaseTickTime;

        HideSession(RegistryKey<World> worldKey,
                    UUID ownerId,
                    UUID directorId,
                    List<UUID> participants,
                    Map<UUID, PetContext> contexts,
                    SessionProfile profile,
                    Map<UUID, RoleAffinity> roleAffinities,
                    long createdTick) {
            this.worldKey = worldKey;
            this.ownerId = ownerId;
            this.directorId = directorId;
            this.participants = new CopyOnWriteArrayList<>(participants);
            this.contexts = contexts;
            this.profile = profile;
            this.roleAffinities = roleAffinities;
            this.createdTick = createdTick;
            this.expiryTick = createdTick + profile.sessionMaxTicks;
            this.lastPhaseTickTime = createdTick - 1;
        }

        boolean contains(UUID uuid) {
            return participants.contains(uuid);
        }

        boolean everyoneReady() {
            return ready.containsAll(participants);
        }

        boolean tryAdvancePhaseTick(long worldTime) {
            synchronized (this) {
                if (lastPhaseTickTime != worldTime) {
                    phaseTicks++;
                    lastPhaseTickTime = worldTime;
                    return true;
                }
                return false;
            }
        }
    }

    private UUID activeSessionId;
    private MemberRole activeRole = MemberRole.SUPPORT;
    private boolean director;
    private Vec3d lastNavigationTarget;
    private boolean celebrating;

    public HideAndSeekGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HIDE_AND_SEEK), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }

        PetComponent selfComponent = PetComponent.get(mob);
        if (selfComponent == null || selfComponent.isInCombat() || selfComponent.isDormant()) {
            return false;
        }

        PetContext selfContext = PetContext.capture(mob, selfComponent);

        long now = sw.getTime();
        cleanupSessions(sw, now);

        UUID existing = SESSION_BY_PET.get(mob.getUuid());
        if (existing != null) {
            HideSession session = SESSIONS.get(existing);
            if (session != null && Objects.equals(session.worldKey, sw.getRegistryKey())
                && (session.contains(mob.getUuid()) || session.spectators.contains(mob.getUuid()))) {
                activeSessionId = session.id;
                return true;
            }
            SESSION_BY_PET.remove(mob.getUuid());
        }

        Invitation invitation = INVITATIONS.get(mob.getUuid());
        if (invitation != null && invitation.isValid(sw.getRegistryKey(), now)) {
            activeSessionId = invitation.sessionId;
            return true;
        }

        HideSession spectatorSession = findSpectatorSession(sw, selfComponent, now);
        if (spectatorSession != null) {
            activeSessionId = spectatorSession.id;
            SESSION_BY_PET.put(mob.getUuid(), spectatorSession.id);
            registerSpectator(spectatorSession, selfComponent, selfContext);
            return true;
        }

        UUID ownerId = selfComponent.getOwnerUuid();
        if (ownerId == null) {
            return false;
        }

        double scanRadius = MathHelper.clamp(9.0 + selfContext.socialCharge() * 6.0, 8.0, 14.0);
        List<MobEntity> neighbours = sw.getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(scanRadius), entity ->
            entity != mob && entity.isAlive() && !entity.isRemoved());

        List<MobEntity> eligible = new ArrayList<>();
        Map<UUID, PetContext> contextSnapshot = new HashMap<>();
        Map<UUID, RoleAffinity> roleBiases = new HashMap<>();
        for (MobEntity candidate : neighbours) {
            PetComponent candidateComponent = PetComponent.get(candidate);
            if (candidateComponent == null || !Objects.equals(candidateComponent.getOwnerUuid(), ownerId)) {
                continue;
            }
            if (candidateComponent.isInCombat() || candidateComponent.isDormant()) {
                continue;
            }
            if (SESSION_BY_PET.containsKey(candidate.getUuid())) {
                continue;
            }
            PetContext ctx = PetContext.capture(candidate, candidateComponent);
            contextSnapshot.put(candidate.getUuid(), ctx);
            roleBiases.put(candidate.getUuid(), computeRoleAffinity(candidate, ctx));
            eligible.add(candidate);
        }

        if (eligible.isEmpty()) {
            return false;
        }

        contextSnapshot.put(mob.getUuid(), selfContext);
        roleBiases.put(mob.getUuid(), computeRoleAffinity(mob, selfContext));

        eligible.sort((a, b) -> Double.compare(scoreCandidate(selfComponent, selfContext, mob, contextSnapshot.get(b.getUuid()), b),
            scoreCandidate(selfComponent, selfContext, mob, contextSnapshot.get(a.getUuid()), a)));

        List<MobEntity> selected = new ArrayList<>();
        for (MobEntity candidate : eligible) {
            if (selected.size() >= MAX_PARTICIPANTS - 1) {
                break;
            }
            selected.add(candidate);
        }

        if (selected.isEmpty()) {
            return false;
        }

        List<UUID> participants = new ArrayList<>();
        participants.add(mob.getUuid());
        for (MobEntity candidate : selected) {
            participants.add(candidate.getUuid());
        }
        participants.sort(UUID::compareTo);

        if (!participants.get(0).equals(mob.getUuid())) {
            return false;
        }

        for (MobEntity candidate : selected) {
            PetContext ctx = contextSnapshot.get(candidate.getUuid());
            if (ctx == null) {
                ctx = PetContext.capture(candidate, PetComponent.get(candidate));
                contextSnapshot.put(candidate.getUuid(), ctx);
            }
        }

        SessionProfile profile = SessionProfile.from(new ArrayList<>(contextSnapshot.values()));

        HideSession session = new HideSession(
            sw.getRegistryKey(),
            ownerId,
            mob.getUuid(),
            participants,
            contextSnapshot,
            profile,
            roleBiases,
            now
        );
        session.anchor = computeAnchor(sw, participants);
        assignFormationSpots(session, sw);
        assignInitialRoles(session, sw);

        SESSIONS.put(session.id, session);
        SESSION_BY_PET.put(mob.getUuid(), session.id);
        activeSessionId = session.id;
        director = true;

        long inviteExpiry = now + session.profile.joinerGraceTicks;
        for (MobEntity candidate : selected) {
            SESSION_BY_PET.put(candidate.getUuid(), session.id);
            INVITATIONS.put(candidate.getUuid(), new Invitation(session.id, inviteExpiry, sw.getRegistryKey()));
        }

        selfComponent.incrementQuirkCounter(QUIRK_COUNTER_KEY);
        session.ready.add(mob.getUuid());
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }
        HideSession session = getActiveSession();
        if (session == null) {
            return false;
        }
        if (!Objects.equals(session.worldKey, sw.getRegistryKey())) {
            return false;
        }
        long now = sw.getTime();
        if (now >= session.expiryTick) {
            return false;
        }
        boolean participant = session.contains(mob.getUuid());
        boolean spectator = session.spectators.contains(mob.getUuid());
        if (!participant && !spectator) {
            return false;
        }
        if (participant && session.phase == Phase.WAITING && now - session.createdTick > session.profile.joinerGraceTicks) {
            int committed = 0;
            for (UUID readyId : session.ready) {
                if (session.participants.contains(readyId) && ++committed >= 2) {
                    break;
                }
            }
            if (committed < 2) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onStartGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            requestStop();
            return;
        }
        HideSession session = getActiveSession();
        if (session == null) {
            Invitation invitation = INVITATIONS.remove(mob.getUuid());
            if (invitation == null) {
                requestStop();
                return;
            }
            session = SESSIONS.get(invitation.sessionId);
            if (session == null) {
                requestStop();
                return;
            }
            activeSessionId = session.id;
            SESSION_BY_PET.put(mob.getUuid(), session.id);
        }

        director = session.directorId.equals(mob.getUuid());
        boolean participant = session.participants.contains(mob.getUuid());
        if (!session.roleAffinities.containsKey(mob.getUuid()) && (participant || session.spectators.contains(mob.getUuid()))) {
            PetComponent component = PetComponent.get(mob);
            PetContext ctx = PetContext.capture(mob, component);
            session.contexts.put(mob.getUuid(), ctx);
            session.roleAffinities.put(mob.getUuid(), computeRoleAffinity(mob, ctx));
        }
        if (participant) {
            session.ready.add(mob.getUuid());
        } else {
            session.spectators.add(mob.getUuid());
            session.roles.put(mob.getUuid(), MemberRole.SPECTATOR);
        }
        INVITATIONS.remove(mob.getUuid());
        updateLocalRole();

        lastNavigationTarget = null;
        celebrating = false;

        if (participant && director && session.phase == Phase.WAITING && session.everyoneReady()) {
            session.phase = Phase.FORMATION;
            session.phaseTicks = 0;
        }

        PetComponent self = PetComponent.get(mob);
        if (participant && director && self != null) {
            self.setCooldown(pairCooldownKey(session.participants), secondsToTicks(90));
        }
    }

    @Override
    protected void onStopGoal() {
        HideSession session = getActiveSession();
        if (session != null) {
            session.ready.remove(mob.getUuid());
            session.spectators.remove(mob.getUuid());
            session.queuedSpectators.remove(mob.getUuid());
            session.roles.remove(mob.getUuid());
            if (!session.participants.contains(mob.getUuid())) {
                session.contexts.remove(mob.getUuid());
                session.roleAffinities.remove(mob.getUuid());
            }
            if (mob.getNavigation() != null) {
                mob.getNavigation().stop();
            }
            if (shouldTearDown(session)) {
                tearDownSession(session);
            }
        }
        SESSION_BY_PET.remove(mob.getUuid());
        INVITATIONS.remove(mob.getUuid());
        activeSessionId = null;
        lastNavigationTarget = null;
        celebrating = false;
    }

    @Override
    protected void onTickGoal() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            requestStop();
            return;
        }
        HideSession session = getActiveSession();
        if (session == null) {
            requestStop();
            return;
        }

        session.tryAdvancePhaseTick(sw.getTime());
        if (director) {
            tickDirector(sw, session);
        }

        updateLocalRole();
        executeRoleBehaviour(sw, session);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.PLAYFULNESS, 0.34f)
            .add(PetComponent.Emotion.CHEERFUL, 0.28f)
            .add(PetComponent.Emotion.CURIOUS, 0.22f)
            .add(PetComponent.Emotion.UBUNTU, 0.18f)
            .add(PetComponent.Emotion.FOCUSED, 0.12f)
            .withContagion(PetComponent.Emotion.PLAYFULNESS, 0.02f)
            .build();
    }

    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.86f;
        float stamina = MathHelper.clamp(ctx.behaviouralEnergyProfile() != null ? ctx.behaviouralEnergyProfile().physicalStamina() : 0.6f, 0.0f, 1.0f);
        float social = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float focus = MathHelper.clamp(ctx.behaviouralEnergyProfile() != null ? ctx.behaviouralEnergyProfile().mentalFocus() : 0.6f, 0.0f, 1.0f);

        engagement *= MathHelper.lerp(stamina, 0.7f, 1.12f);
        engagement *= MathHelper.lerp(social, 0.72f, 1.2f);
        engagement *= MathHelper.lerp(focus, 0.76f, 1.08f);

        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement = MathHelper.clamp(engagement + 0.08f, 0.0f, 1.0f);
        }
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }

    private void tickDirector(ServerWorld world, HideSession session) {
        synchronized (session) {
            switch (session.phase) {
                case WAITING -> handleWaitingPhase(world, session);
                case FORMATION -> handleFormationPhase(world, session);
                case COUNTDOWN -> handleCountdownPhase(world, session);
                case SEEK -> handleSeekPhase(world, session);
                case CELEBRATE -> handleCelebratePhase(world, session);
            }
        }
    }

    private void pruneUnresponsiveInvitees(ServerWorld world, HideSession session) {
        List<UUID> removed = new ArrayList<>();
        long now = world.getTime();
        for (UUID uuid : new ArrayList<>(session.participants)) {
            if (uuid.equals(session.directorId)) {
                continue;
            }
            if (session.ready.contains(uuid)) {
                continue;
            }
            Invitation invitation = INVITATIONS.get(uuid);
            if (invitation != null && Objects.equals(invitation.sessionId, session.id) && invitation.expiryTick > now) {
                continue;
            }
            if (session.participants.remove(uuid)) {
                removed.add(uuid);
            }
        }

        if (removed.isEmpty()) {
            return;
        }

        for (UUID uuid : removed) {
            SESSION_BY_PET.remove(uuid, session.id);
            INVITATIONS.remove(uuid);
            session.ready.remove(uuid);
            session.roles.remove(uuid);
            session.currentHiders.remove(uuid);
            session.foundHiders.remove(uuid);
            session.hideAssignments.remove(uuid);
            session.formationTargets.remove(uuid);
            session.seekerRoutes.remove(uuid);
            session.seekerRouteIndices.remove(uuid);
            session.contexts.remove(uuid);
            session.roleAffinities.remove(uuid);
            session.queuedSpectators.remove(uuid);
        }
        session.currentHiders.removeIf(id -> !session.participants.contains(id));

        if (session.participants.size() < 2) {
            session.expiryTick = Math.min(session.expiryTick, now + 1);
            return;
        }

        session.anchor = computeAnchor(world, session.participants);
        assignFormationSpots(session, world);
        ensureSeekerRotation(session);
        if (session.seekerId != null && !session.participants.contains(session.seekerId)) {
            session.seekerId = null;
        }
    }

    private void admitSpectatorsForNextRound(ServerWorld world, HideSession session) {
        if (session.queuedSpectators.isEmpty()) {
            return;
        }

        List<UUID> promoted = new ArrayList<>();
        for (UUID queued : new ArrayList<>(session.queuedSpectators)) {
            if (session.participants.size() >= MAX_PARTICIPANTS) {
                break;
            }
            if (session.participants.contains(queued)) {
                session.queuedSpectators.remove(queued);
                continue;
            }
            MobEntity entity = getMob(world, queued);
            if (entity == null) {
                continue;
            }
            PetComponent component = PetComponent.get(entity);
            if (component == null || component.isInCombat() || component.isDormant()) {
                continue;
            }
            PetContext ctx = session.contexts.get(queued);
            if (ctx == null) {
                ctx = PetContext.capture(entity, component);
                session.contexts.put(queued, ctx);
            }
            session.roleAffinities.put(queued, computeRoleAffinity(entity, ctx));
            session.participants.add(queued);
            session.ready.add(queued);
            session.roles.put(queued, MemberRole.SUPPORT);
            SESSION_BY_PET.put(queued, session.id);
            promoted.add(queued);
        }

        if (promoted.isEmpty()) {
            return;
        }

        session.participants.sort(UUID::compareTo);
        for (UUID uuid : promoted) {
            session.queuedSpectators.remove(uuid);
            session.spectators.remove(uuid);
        }
        session.anchor = computeAnchor(world, session.participants);
        assignFormationSpots(session, world);
        ensureSeekerRotation(session);
    }

    private void refreshParticipantSnapshots(ServerWorld world, HideSession session) {
        boolean updated = false;
        for (UUID uuid : session.participants) {
            if (captureContextSnapshot(world, session, uuid)) {
                updated = true;
            }
        }
        for (UUID spectatorId : session.spectators) {
            captureContextSnapshot(world, session, spectatorId);
        }
        if (!updated) {
            return;
        }
        List<PetContext> contexts = session.participants.stream()
            .map(session.contexts::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (contexts.isEmpty()) {
            return;
        }
        SessionProfile recalculated = SessionProfile.from(contexts);
        session.profile = blendProfiles(session.profile, recalculated);
    }

    private boolean captureContextSnapshot(ServerWorld world, HideSession session, UUID uuid) {
        MobEntity entity = getMob(world, uuid);
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        PetComponent component = PetComponent.get(entity);
        if (component == null || component.isDormant()) {
            return false;
        }
        PetContext snapshot = PetContext.capture(entity, component);
        session.contexts.put(uuid, snapshot);
        session.roleAffinities.put(uuid, computeRoleAffinity(entity, snapshot));
        return true;
    }

    private PetContext ensureContext(ServerWorld world, HideSession session, UUID uuid) {
        PetContext context = session.contexts.get(uuid);
        if (context != null) {
            return context;
        }
        captureContextSnapshot(world, session, uuid);
        return session.contexts.get(uuid);
    }

    private static List<UUID> snapshotHiders(HideSession session) {
        return new ArrayList<>(session.currentHiders);
    }

    private void handleWaitingPhase(ServerWorld world, HideSession session) {
        if (session.phaseTicks == 1 || session.phaseTicks % 40 == 0) {
            pruneUnresponsiveInvitees(world, session);
        }

        if (session.phaseTicks == 1 || session.phaseTicks % 60 == 0) {
            refreshParticipantSnapshots(world, session);
        }

        if (session.closing) {
            session.queuedSpectators.clear();
            return;
        }

        admitSpectatorsForNextRound(world, session);

        if (session.participants.size() < 2) {
            session.expiryTick = Math.min(session.expiryTick, world.getTime() + secondsToTicks(2));
            return;
        }

        if (session.everyoneReady() || session.phaseTicks > session.profile.joinerGraceTicks) {
            pruneUnresponsiveInvitees(world, session);
            if (session.participants.size() < 2) {
                session.expiryTick = Math.min(session.expiryTick, world.getTime() + secondsToTicks(2));
                return;
            }
            session.phase = Phase.FORMATION;
            session.phaseTicks = 0;
            refreshParticipantSnapshots(world, session);
            session.anchor = computeAnchor(world, session.participants);
            assignFormationSpots(session, world);
            assignInitialRoles(session, world);
        }
    }

    private void handleFormationPhase(ServerWorld world, HideSession session) {
        if (session.phaseTicks % 40 == 0) {
            session.anchor = computeAnchor(world, session.participants);
            assignFormationSpots(session, world);
        }

        boolean allArrived = true;
        for (UUID uuid : session.participants) {
            MobEntity entity = getMob(world, uuid);
            if (entity == null) {
                continue;
            }
            Vec3d target = session.formationTargets.get(uuid);
            if (target == null) {
                continue;
            }
            if (entity.squaredDistanceTo(target) > session.profile.formationToleranceSq) {
                allArrived = false;
                break;
            }
        }

        if (allArrived || session.phaseTicks > session.profile.formationTimeoutTicks) {
            session.phase = Phase.COUNTDOWN;
            session.phaseTicks = 0;
            pushGroupEmotion(world, session.participants, PetComponent.Emotion.CURIOUS, 0.18f);
            for (UUID spectatorId : session.spectators) {
                MobEntity spectator = getMob(world, spectatorId);
                if (spectator != null) {
                    pushEmotion(spectator, PetComponent.Emotion.CURIOUS, 0.12f);
                }
            }
        }
    }

    private void handleCountdownPhase(ServerWorld world, HideSession session) {
        if (session.phaseTicks == 1) {
            prepareNextRound(world, session);
        }
        if (session.phaseTicks >= session.profile.countdownTicks) {
            session.phase = Phase.SEEK;
            session.phaseTicks = 0;
        }
    }

    private void handleSeekPhase(ServerWorld world, HideSession session) {
        ensureActiveSeeker(world, session);
        checkSeekerFinds(world, session);
        boolean allFound = snapshotHiders(session).stream().allMatch(id -> session.foundHiders.getOrDefault(id, Boolean.FALSE));
        if (allFound || session.phaseTicks >= session.profile.seekTimeoutTicks) {
            session.phase = Phase.CELEBRATE;
            session.phaseTicks = 0;
            session.roundsCompleted++;
            pushCelebrationEmotions(world, session, allFound);
            reinforceRoundRelationships(world, session, allFound);
        }
    }

    private void handleCelebratePhase(ServerWorld world, HideSession session) {
        if (session.phaseTicks >= session.profile.celebrateTicks) {
            if (session.roundsCompleted >= 3) {
                session.closing = true;
                session.phase = Phase.WAITING;
                session.phaseTicks = 0;
                session.queuedSpectators.clear();
                session.expiryTick = Math.min(session.expiryTick, world.getTime() + secondsToTicks(2));
            } else {
                admitSpectatorsForNextRound(world, session);
                session.phase = Phase.FORMATION;
                session.phaseTicks = 0;
                refreshParticipantSnapshots(world, session);
                session.anchor = computeAnchor(world, session.participants);
                assignFormationSpots(session, world);
                rotateRoles(session, world);
            }
        }
    }

    private void executeRoleBehaviour(ServerWorld world, HideSession session) {
        if (celebrating && session.phase != Phase.CELEBRATE) {
            celebrating = false;
            mob.setPitch(0.0f);
        }

        switch (session.phase) {
            case WAITING -> idleAroundAnchor(world, session);
            case FORMATION -> moveToFormation(world, session);
            case COUNTDOWN -> performCountdownPose(world, session);
            case SEEK -> performRoleDuringSeek(world, session);
            case CELEBRATE -> performCelebration(world, session);
        }
    }

    private void idleAroundAnchor(ServerWorld world, HideSession session) {
        if (activeRole == MemberRole.SPECTATOR) {
            observeFromSidelines(world, session);
            return;
        }
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
        Vec3d wander = anchor.add(randomOffset(session.profile.supportOrbitRadius * 0.7));
        driveEntityTowards(mob, wander, session.profile.supportSpeed);
    }

    private void moveToFormation(ServerWorld world, HideSession session) {
        if (activeRole == MemberRole.SPECTATOR) {
            observeFromSidelines(world, session);
            return;
        }
        Vec3d target = session.formationTargets.get(mob.getUuid());
        if (target == null) {
            return;
        }
        driveEntityTowards(mob, target, session.profile.supportSpeed);
        if (mob.squaredDistanceTo(target) < session.profile.formationToleranceSq) {
            mob.getLookControl().lookAt(target.x, target.y + mob.getStandingEyeHeight(), target.z);
        }
    }

    private void performCountdownPose(ServerWorld world, HideSession session) {
        if (activeRole == MemberRole.SPECTATOR) {
            observeFromSidelines(world, session);
            return;
        }
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
        mob.getLookControl().lookAt(anchor.x, anchor.y + 0.6, anchor.z);
        if (session.phaseTicks % 20 == 0) {
            pushEmotion(mob, PetComponent.Emotion.CURIOUS, 0.08f);
        }
    }

    private void performRoleDuringSeek(ServerWorld world, HideSession session) {
        switch (activeRole) {
            case SEEKER -> followSeekerRoute(world, session);
            case HIDER -> moveToHideSpot(world, session);
            case SUPPORT -> orbitPlaySpace(world, session);
            case SPECTATOR -> observeFromSidelines(world, session);
        }
    }

    private void performCelebration(ServerWorld world, HideSession session) {
        if (activeRole == MemberRole.SPECTATOR) {
            observeFromSidelines(world, session);
            if (session.phaseTicks % 20 == 1) {
                pushEmotion(mob, PetComponent.Emotion.CHEERFUL, 0.18f);
            }
            return;
        }
        celebrating = true;
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        float spin = (session.phaseTicks % 20) / 20.0f * MathHelper.TAU;
        mob.setYaw(mob.bodyYaw + MathHelper.sin(spin) * 10.0f);
        mob.setPitch(MathHelper.sin(session.phaseTicks * 0.4f) * 9.0f);
        if (session.phaseTicks == 1) {
            pushEmotion(mob, PetComponent.Emotion.CHEERFUL, 0.3f);
        }
    }

    private void followSeekerRoute(ServerWorld world, HideSession session) {
        List<Vec3d> route = session.seekerRoutes.get(session.seekerId);
        if (route == null || route.isEmpty()) {
            Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
            driveEntityTowards(mob, anchor, session.profile.seekerSpeed);
            return;
        }
        int index = session.seekerRouteIndices.getOrDefault(session.seekerId, 0);
        Vec3d target = route.get(Math.min(index, route.size() - 1));
        driveEntityTowards(mob, target, session.profile.seekerSpeed);
        if (mob.squaredDistanceTo(target) < session.profile.hideArrivalSq * 1.2) {
            session.seekerRouteIndices.put(session.seekerId, Math.min(route.size() - 1, index + 1));
        }
    }

    private void moveToHideSpot(ServerWorld world, HideSession session) {
        BlockPos hide = session.hideAssignments.get(mob.getUuid());
        if (hide == null) {
            orbitPlaySpace(world, session);
            return;
        }
        Vec3d target = Vec3d.ofCenter(hide);
        driveEntityTowards(mob, target, session.profile.hiderSpeed);
        if (mob.squaredDistanceTo(target) < session.profile.hideArrivalSq) {
            mob.getLookControl().lookAt(target.x, target.y + mob.getStandingEyeHeight(), target.z);
            pushEmotion(mob, PetComponent.Emotion.FOCUSED, 0.06f);
        }
    }

    private void orbitPlaySpace(ServerWorld world, HideSession session) {
        if (activeRole == MemberRole.SPECTATOR) {
            observeFromSidelines(world, session);
            return;
        }
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
        double period = 80.0;
        double theta = (world.getTime() + mob.getUuid().hashCode() % 20) / period * MathHelper.TAU;
        Vec3d offset = new Vec3d(Math.cos(theta), 0.0, Math.sin(theta))
            .multiply(session.profile.supportOrbitRadius + 0.35 * session.participants.size());
        Vec3d target = anchor.add(offset);
        driveEntityTowards(mob, target, session.profile.supportSpeed);
    }

    private void observeFromSidelines(ServerWorld world, HideSession session) {
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
        double baseRadius = session.profile.supportOrbitRadius + 1.6 + Math.min(session.participants.size(), MAX_PARTICIPANTS) * 0.35;
        double wobble = MathHelper.sin((world.getTime() + mob.getUuid().hashCode() % 40) * 0.1f) * 0.4;
        double hashPortion = (mob.getUuid().hashCode() & 0xFFFF) / 65536.0;
        double baseAngle = MathHelper.TAU * hashPortion;
        Vec3d offset = new Vec3d(Math.cos(baseAngle + wobble), 0.0, Math.sin(baseAngle + wobble)).multiply(baseRadius);
        Vec3d target = snapToGround(world, anchor.add(offset));
        driveEntityTowards(mob, target, session.profile.supportSpeed * 0.85);
        mob.getLookControl().lookAt(anchor.x, anchor.y + mob.getStandingEyeHeight() * 0.6, anchor.z);
    }

    private HideSession findSpectatorSession(ServerWorld world, PetComponent component, long now) {
        UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) {
            return null;
        }
        Vec3d selfPos = entityPosition(mob);
        HideSession closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (HideSession session : SESSIONS.values()) {
            if (session == null || now >= session.expiryTick) {
                continue;
            }
            if (!Objects.equals(session.worldKey, world.getRegistryKey()) || !Objects.equals(session.ownerId, ownerId)) {
                continue;
            }
            if (session.contains(mob.getUuid())) {
                continue;
            }
            Vec3d anchor = session.anchor != null ? session.anchor : computeAnchor(world, session.participants);
            double distSq = anchor.squaredDistanceTo(selfPos);
            double maxRadiusSq = session.profile != null ? MathHelper.square(session.profile.scanRadius + 3.0) : MathHelper.square(12.0);
            if (distSq > maxRadiusSq) {
                continue;
            }
            if (closest == null || distSq < bestDistSq) {
                closest = session;
                bestDistSq = distSq;
            }
        }
        return closest;
    }

    private void registerSpectator(HideSession session, PetComponent component, PetContext context) {
        UUID uuid = mob.getUuid();
        session.spectators.add(uuid);
        session.roles.put(uuid, MemberRole.SPECTATOR);
        if (context != null) {
            session.contexts.put(uuid, context);
            session.roleAffinities.put(uuid, computeRoleAffinity(mob, context));
        }

        if (session.participants.contains(uuid)) {
            session.queuedSpectators.remove(uuid);
            return;
        }
        if (session.participants.size() >= MAX_PARTICIPANTS) {
            return;
        }
        if (component == null || component.isInCombat() || component.isDormant()) {
            return;
        }
        session.queuedSpectators.add(uuid);
    }

    private void prepareNextRound(ServerWorld world, HideSession session) {
        session.closing = false;
        session.currentHiders.clear();
        session.hideAssignments.clear();
        session.seekerRoutes.clear();
        session.seekerRouteIndices.clear();
        session.foundHiders.clear();

        refreshParticipantSnapshots(world, session);
        rotateRoles(session, world);
        assignHideSpots(session, world);
        prepareSeekerRoute(session, world);
    }

    private void rotateRoles(HideSession session, ServerWorld world) {
        UUID nextSeeker = nextSeekerForRound(session);
        if (nextSeeker == null) {
            nextSeeker = session.seekerId != null ? session.seekerId : session.directorId;
        }
        session.seekerId = nextSeeker;
        session.roles.put(nextSeeker, MemberRole.SEEKER);

        session.currentHiders.clear();
        List<UUID> hiderPool = new ArrayList<>(session.participants);
        hiderPool.remove(session.seekerId);
        hiderPool.sort((a, b) -> Double.compare(
            affinityFor(session, b).hiderBias,
            affinityFor(session, a).hiderBias
        ));

        int desiredHiders = session.participants.size() <= 2 ? 1 : Math.min(MAX_HIDERS, session.participants.size() - 1);
        for (UUID candidate : hiderPool) {
            if (session.currentHiders.size() >= desiredHiders) {
                break;
            }
            session.currentHiders.add(candidate);
            session.roles.put(candidate, MemberRole.HIDER);
        }

        for (UUID participant : session.participants) {
            if (participant.equals(session.seekerId)) {
                continue;
            }
            if (!session.currentHiders.contains(participant)) {
                session.roles.put(participant, MemberRole.SUPPORT);
            }
        }
    }

    private void assignHideSpots(HideSession session, ServerWorld world) {
        List<UUID> hiders = snapshotHiders(session);
        if (hiders.isEmpty()) {
            return;
        }
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(mob);
        UUID primaryHider = hiders.get(0);
        MobEntity primaryEntity = getMob(world, primaryHider);
        BlockPos primarySpot = pickHideSpot(world, primaryEntity, anchor);
        session.hideAssignments.put(primaryHider, primarySpot);

        if (hiders.size() > 1) {
            UUID secondary = hiders.get(1);
            BlockPos buddySpot = pickSecondaryHideSpot(world, primarySpot, world.random);
            session.hideAssignments.put(secondary, buddySpot);
        }
    }

    private void prepareSeekerRoute(HideSession session, ServerWorld world) {
        if (session.seekerId == null) {
            return;
        }
        MobEntity seeker = getMob(world, session.seekerId);
        if (seeker == null) {
            return;
        }
        List<Vec3d> route = new ArrayList<>();
        Vec3d anchor = session.anchor != null ? session.anchor : entityPosition(seeker);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int orbitSamples = 3 + random.nextInt(2);
        for (int i = 0; i < orbitSamples; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double radius = session.profile.supportOrbitRadius + random.nextDouble() * 2.5;
            Vec3d candidate = snapToGround(world, anchor.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
            route.add(candidate);
        }
        for (UUID hiderId : snapshotHiders(session)) {
            BlockPos hide = session.hideAssignments.get(hiderId);
            if (hide != null) {
                route.add(Vec3d.ofCenter(hide));
            }
        }
        session.seekerRoutes.put(session.seekerId, route);
        session.seekerRouteIndices.put(session.seekerId, 0);
    }

    private void ensureActiveSeeker(ServerWorld world, HideSession session) {
        UUID seekerId = session.seekerId;
        MobEntity seeker = seekerId != null ? getMob(world, seekerId) : null;
        if (seeker != null && seeker.isAlive()) {
            return;
        }

        UUID previous = session.seekerId;
        UUID replacement = session.participants.stream()
            .filter(uuid -> !Objects.equals(uuid, previous))
            .filter(uuid -> getMob(world, uuid) != null)
            .max(Comparator.comparingDouble(uuid -> affinityFor(session, uuid).seekerBias))
            .orElse(null);

        if (replacement == null) {
            session.expiryTick = Math.min(session.expiryTick, world.getTime() + secondsToTicks(2));
            return;
        }

        session.seekerId = replacement;
        session.roles.put(replacement, MemberRole.SEEKER);
        if (previous != null) {
            session.roles.put(previous, MemberRole.SUPPORT);
            session.seekerRoutes.remove(previous);
            session.seekerRouteIndices.remove(previous);
        }

        if (session.currentHiders.remove(replacement)) {
            session.hideAssignments.remove(replacement);
            session.foundHiders.remove(replacement);
        }

        ensureHiderCoverage(world, session);
        prepareSeekerRoute(session, world);
        ensureSeekerRotation(session);
        session.seekerRotation.remove(replacement);
        session.seekerRotation.add(replacement);

        MobEntity replacementEntity = getMob(world, replacement);
        if (replacementEntity != null) {
            pushEmotion(replacementEntity, PetComponent.Emotion.FOCUSED, 0.16f);
        }
    }

    private void ensureHiderCoverage(ServerWorld world, HideSession session) {
        session.currentHiders.removeIf(uuid -> !session.participants.contains(uuid));
        session.currentHiders.removeIf(uuid -> {
            MobEntity entity = getMob(world, uuid);
            return entity == null || !entity.isAlive();
        });
        session.hideAssignments.keySet().removeIf(uuid -> !session.currentHiders.contains(uuid));
        session.foundHiders.keySet().removeIf(uuid -> !session.currentHiders.contains(uuid));

        int desired = session.participants.size() <= 2 ? 1 : Math.min(MAX_HIDERS, session.participants.size() - 1);
        while (session.currentHiders.size() < desired) {
            UUID candidate = session.participants.stream()
                .filter(uuid -> !uuid.equals(session.seekerId))
                .filter(uuid -> !session.currentHiders.contains(uuid))
                .filter(uuid -> getMob(world, uuid) != null)
                .max(Comparator.comparingDouble(uuid -> affinityFor(session, uuid).hiderBias))
                .orElse(null);
            if (candidate == null) {
                break;
            }
            session.currentHiders.add(candidate);
            session.roles.put(candidate, MemberRole.HIDER);

            if (!session.hideAssignments.containsKey(candidate)) {
                MobEntity entity = getMob(world, candidate);
                Vec3d anchor = session.anchor != null ? session.anchor : computeAnchor(world, session.participants);
                session.hideAssignments.put(candidate, pickHideSpot(world, entity, anchor));
            }
        }

        for (UUID participant : session.participants) {
            if (participant.equals(session.seekerId)) {
                continue;
            }
            if (!session.currentHiders.contains(participant)) {
                session.roles.put(participant, MemberRole.SUPPORT);
            }
        }
    }

    private void checkSeekerFinds(ServerWorld world, HideSession session) {
        MobEntity seeker = getMob(world, session.seekerId);
        if (seeker == null) {
            return;
        }
        List<UUID> hiders = snapshotHiders(session);
        for (UUID hiderId : hiders) {
            if (session.foundHiders.getOrDefault(hiderId, Boolean.FALSE)) {
                continue;
            }
            MobEntity hider = getMob(world, hiderId);
            if (hider == null) {
                session.foundHiders.put(hiderId, Boolean.TRUE);
                continue;
            }
            if (seeker.squaredDistanceTo(hider) <= session.profile.seekerCaptureSq) {
                session.foundHiders.put(hiderId, Boolean.TRUE);
                pushEmotion(seeker, PetComponent.Emotion.PRIDE, 0.32f);
                pushEmotion(hider, PetComponent.Emotion.STARTLE, 0.26f);
                pushEmotion(hider, PetComponent.Emotion.RELIEF, 0.24f);
                handleCatchRelationship(world, session, hiderId);
                if (hiders.size() > 1) {
                    for (UUID buddy : hiders) {
                        MobEntity buddyEntity = getMob(world, buddy);
                        if (buddyEntity != null) {
                            pushEmotion(buddyEntity, PetComponent.Emotion.UBUNTU, 0.2f);
                        }
                    }
                }
            }
        }
    }

    private void pushCelebrationEmotions(ServerWorld world, HideSession session, boolean success) {
        List<UUID> hiders = snapshotHiders(session);
        for (UUID uuid : session.participants) {
            MobEntity entity = getMob(world, uuid);
            if (entity == null) {
                continue;
            }
            if (uuid.equals(session.seekerId) && success) {
                pushEmotion(entity, PetComponent.Emotion.PRIDE, 0.28f);
            } else if (hiders.contains(uuid)) {
                pushEmotion(entity, success ? PetComponent.Emotion.RELIEF : PetComponent.Emotion.STARTLE, 0.22f);
            } else {
                pushEmotion(entity, PetComponent.Emotion.CHEERFUL, 0.2f);
            }
        }
    }

    private void handleCatchRelationship(ServerWorld world, HideSession session, UUID hiderId) {
        if (session.seekerId != null) {
            float seekerScale = 1.05f + (session.currentHiders.size() > 1 ? 0.05f : 0.0f);
            recordMutualInteraction(world, session, session.seekerId, hiderId, InteractionType.PLAY, seekerScale);
        }
        for (UUID buddy : snapshotHiders(session)) {
            if (Objects.equals(buddy, hiderId)) {
                continue;
            }
            recordMutualInteraction(world, session, buddy, hiderId, InteractionType.PLAY, 1.04f);
        }
        for (UUID participant : session.participants) {
            if (Objects.equals(participant, hiderId) || Objects.equals(participant, session.seekerId)) {
                continue;
            }
            MemberRole role = session.roles.getOrDefault(participant, MemberRole.SUPPORT);
            if (role != MemberRole.SUPPORT) {
                continue;
            }
            recordMutualInteraction(world, session, participant, hiderId, InteractionType.PLAY, 0.94f);
        }
    }

    private void reinforceRoundRelationships(ServerWorld world, HideSession session, boolean success) {
        List<UUID> participants = new ArrayList<>(session.participants);
        int size = participants.size();
        for (int i = 0; i < size; i++) {
            UUID first = participants.get(i);
            MemberRole firstRole = session.roles.getOrDefault(first, MemberRole.SUPPORT);
            for (int j = i + 1; j < size; j++) {
                UUID second = participants.get(j);
                MemberRole secondRole = session.roles.getOrDefault(second, MemberRole.SUPPORT);
                float baseScale = bondScaleFor(firstRole, secondRole, success, session);
                recordMutualInteraction(world, session, first, second, InteractionType.PLAY, baseScale);
            }
        }

        if (session.spectators.isEmpty()) {
            return;
        }
        float observationScale = spectatorObservationScale(session);
        for (UUID spectator : session.spectators) {
            for (UUID participant : participants) {
                recordInteraction(world, session, spectator, participant, InteractionType.OBSERVATION, observationScale);
            }
        }
    }

    private float bondScaleFor(MemberRole first, MemberRole second, boolean success, HideSession session) {
        if ((first == MemberRole.SEEKER && second == MemberRole.HIDER)
            || (first == MemberRole.HIDER && second == MemberRole.SEEKER)) {
            return success ? 1.08f : 0.96f;
        }
        if (first == MemberRole.HIDER && second == MemberRole.HIDER) {
            return session.currentHiders.size() > 1 ? 1.12f : 1.02f;
        }
        if (first == MemberRole.SUPPORT && second == MemberRole.SUPPORT) {
            return 0.95f;
        }
        if ((first == MemberRole.SUPPORT && second == MemberRole.HIDER)
            || (first == MemberRole.HIDER && second == MemberRole.SUPPORT)) {
            return 1.03f;
        }
        if ((first == MemberRole.SUPPORT && second == MemberRole.SEEKER)
            || (first == MemberRole.SEEKER && second == MemberRole.SUPPORT)) {
            return 1.02f;
        }
        return 0.92f;
    }

    private float spectatorObservationScale(HideSession session) {
        return 0.78f + Math.min(0.12f, session.participants.size() * 0.02f);
    }

    private void recordMutualInteraction(ServerWorld world, HideSession session, UUID first, UUID second, InteractionType type, float baseScale) {
        if (Objects.equals(first, second)) {
            return;
        }
        recordInteraction(world, session, first, second, type, baseScale);
        recordInteraction(world, session, second, first, type, baseScale);
    }

    private void recordInteraction(ServerWorld world, HideSession session, UUID actorId, UUID partnerId, InteractionType type, float baseScale) {
        if (actorId == null || partnerId == null || Objects.equals(actorId, partnerId)) {
            return;
        }
        MobEntity actor = getMob(world, actorId);
        MobEntity partner = getMob(world, partnerId);
        if (actor == null || partner == null) {
            return;
        }
        PetComponent component = PetComponent.get(actor);
        if (component == null || component.isDormant()) {
            return;
        }
        PetContext context = ensureContext(world, session, actorId);
        BehaviouralEnergyProfile energyProfile = context != null && context.behaviouralEnergyProfile() != null
            ? context.behaviouralEnergyProfile()
            : component.getMoodEngine() != null
                ? component.getMoodEngine().getBehaviouralEnergyProfile()
                : BehaviouralEnergyProfile.neutral();

        double social = context != null ? context.socialCharge() : energyProfile.socialCharge();
        double momentum = context != null ? context.behavioralMomentum() : energyProfile.momentum();
        double stamina = energyProfile.physicalStamina();
        double focus = energyProfile.mentalFocus();

        float trustScale = (float) MathHelper.clamp(baseScale + social * 0.2 + momentum * 0.1, 0.55, 1.6);
        float affectionScale = (float) MathHelper.clamp(baseScale + social * 0.35 + stamina * 0.15, 0.55, 1.7);
        float respectScale = (float) MathHelper.clamp(0.85 + focus * 0.25 + baseScale * 0.1, 0.5, 1.6);

        component.recordEntityInteraction(partnerId, type, trustScale, affectionScale, respectScale);
    }

    private void pushGroupEmotion(ServerWorld world, List<UUID> participants, PetComponent.Emotion emotion, float amount) {
        for (UUID uuid : participants) {
            MobEntity entity = getMob(world, uuid);
            if (entity != null) {
                pushEmotion(entity, emotion, amount);
            }
        }
    }

    private void updateLocalRole() {
        HideSession session = getActiveSession();
        if (session == null) {
            activeRole = MemberRole.SUPPORT;
            return;
        }
        activeRole = session.roles.getOrDefault(mob.getUuid(), MemberRole.SUPPORT);
    }

    private HideSession getActiveSession() {
        if (activeSessionId == null) {
            return null;
        }
        return SESSIONS.get(activeSessionId);
    }

    private void driveEntityTowards(MobEntity entity, Vec3d target, double speed) {
        if (entity == null || target == null || entity.getNavigation() == null) {
            return;
        }
        if (entity == mob) {
            if (lastNavigationTarget != null && lastNavigationTarget.squaredDistanceTo(target) <= 0.5 && !entity.getNavigation().isIdle()) {
                return;
            }
            lastNavigationTarget = target;
        }
        entity.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
    }

    private static Vec3d computeAnchor(ServerWorld world, List<UUID> participants) {
        if (participants.isEmpty()) {
            return fallbackAnchor(world);
        }
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        int count = 0;
        for (UUID uuid : participants) {
            MobEntity entity = getMob(world, uuid);
            if (entity == null) {
                continue;
            }
            sumX += entity.getX();
            sumY += entity.getY();
            sumZ += entity.getZ();
            count++;
        }
        if (count == 0) {
            return fallbackAnchor(world);
        }
        Vec3d center = new Vec3d(sumX / count, sumY / count, sumZ / count);
        return snapToGround(world, center);
    }

    private static Vec3d fallbackAnchor(ServerWorld world) {
        double x = world.getWorldBorder().getCenterX();
        double z = world.getWorldBorder().getCenterZ();
        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        return new Vec3d(x, y, z);
    }

    private static void assignFormationSpots(HideSession session, ServerWorld world) {
        Vec3d base = session.anchor != null ? session.anchor : fallbackAnchor(world);
        int count = session.participants.size();
        double radius = Math.max(1.6, session.profile.formationRadius + 0.25 * count);
        for (int i = 0; i < count; i++) {
            UUID participant = session.participants.get(i);
            double angle = (MathHelper.TAU / Math.max(1, count)) * i;
            Vec3d offset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            session.formationTargets.put(participant, snapToGround(world, base.add(offset)));
        }
    }

    private static void assignInitialRoles(HideSession session, ServerWorld world) {
        session.closing = false;
        session.currentHiders.clear();
        session.roles.clear();
        session.seekerId = session.directorId;
        session.roles.put(session.directorId, MemberRole.SEEKER);
        List<UUID> others = new ArrayList<>(session.participants);
        others.remove(session.directorId);
        others.sort((a, b) -> Double.compare(
            affinityFor(session, b).hiderBias,
            affinityFor(session, a).hiderBias
        ));
        int desiredHiders = others.isEmpty() ? 0 : Math.min(MAX_HIDERS, Math.max(1, others.size()));
        for (int i = 0; i < others.size(); i++) {
            UUID candidate = others.get(i);
            if (i < desiredHiders) {
                session.roles.put(candidate, MemberRole.HIDER);
                session.currentHiders.add(candidate);
            } else {
                session.roles.put(candidate, MemberRole.SUPPORT);
            }
        }
        seedSeekerRotation(session);
    }

    private static void seedSeekerRotation(HideSession session) {
        session.seekerRotation.clear();
        List<UUID> ordered = new ArrayList<>(session.participants);
        ordered.sort((a, b) -> Double.compare(
            affinityFor(session, b).seekerBias,
            affinityFor(session, a).seekerBias
        ));
        session.seekerRotation.addAll(ordered);
    }

    private static void ensureSeekerRotation(HideSession session) {
        session.seekerRotation.removeIf(uuid -> !session.participants.contains(uuid));
        for (UUID participant : session.participants) {
            if (session.seekerRotation.contains(participant)) {
                continue;
            }
            double bias = affinityFor(session, participant).seekerBias;
            int insertIndex = 0;
            while (insertIndex < session.seekerRotation.size()) {
                UUID existing = session.seekerRotation.get(insertIndex);
                if (affinityFor(session, existing).seekerBias < bias) {
                    break;
                }
                insertIndex++;
            }
            session.seekerRotation.add(insertIndex, participant);
        }
        if (session.seekerRotation.isEmpty() && !session.participants.isEmpty()) {
            session.seekerRotation.addAll(session.participants);
        }
    }

    private static UUID nextSeekerForRound(HideSession session) {
        ensureSeekerRotation(session);
        if (session.seekerRotation.isEmpty()) {
            if (session.seekerId != null) {
                return session.seekerId;
            }
            return session.participants.isEmpty() ? session.directorId : session.participants.get(0);
        }

        UUID previous = session.seekerId;
        UUID chosen = null;
        int attempts = session.seekerRotation.size();
        for (int i = 0; i < attempts; i++) {
            UUID candidate = session.seekerRotation.remove(0);
            if (!session.participants.contains(candidate)) {
                continue;
            }
            session.seekerRotation.add(candidate);
            if (Objects.equals(candidate, previous)
                && session.participants.size() > 1
                && attempts > 1) {
                continue;
            }
            chosen = candidate;
            break;
        }

        if (chosen == null) {
            if (previous != null && session.participants.contains(previous)) {
                return previous;
            }
            return session.participants.isEmpty() ? session.directorId : session.participants.get(0);
        }

        return chosen;
    }

    private static RoleAffinity affinityFor(HideSession session, UUID uuid) {
        return session.roleAffinities.getOrDefault(uuid, BASE_AFFINITY);
    }

    private static RoleAffinity computeRoleAffinity(MobEntity entity, PetContext context) {
        double seeker = 0.55;
        double hider = 0.55;
        double support = 0.5;

        BehaviouralEnergyProfile energy = context.behaviouralEnergyProfile();
        double momentum = context.behavioralMomentum();
        double social = context.socialCharge();
        double stamina = energy != null ? energy.physicalStamina() : 0.6;
        double focus = energy != null ? energy.mentalFocus() : 0.6;

        seeker += momentum * 0.6 + focus * 0.25;
        hider += (1.0 - momentum) * 0.4 + stamina * 0.2;
        support += social * 0.5;

        if (MobCapabilities.canFly(entity)) {
            seeker += 0.6;
        }
        if (MobCapabilities.isSmallSize(entity)) {
            hider += 0.6;
        }
        if (MobCapabilities.prefersWater(entity)) {
            hider += 0.4;
        }
        if (!MobCapabilities.canWander(entity)) {
            support += 0.3;
            hider += 0.2;
            seeker -= 0.2;
        }
        if (MobCapabilities.prefersLand(entity)) {
            seeker += 0.2;
        }

        Map<String, Integer> quirks = context.quirkCounters();
        if (quirks != null) {
            int stealthGames = quirks.getOrDefault("stealth_games", 0);
            int trackingWins = quirks.getOrDefault("tracking_success", 0);
            hider += Math.min(0.5, stealthGames * 0.08);
            seeker += Math.min(0.6, trackingWins * 0.1);
        }

        double norm = seeker + hider + support;
        if (norm <= 0) {
            return new RoleAffinity(0.33, 0.33, 0.34);
        }
        return new RoleAffinity(seeker / norm, hider / norm, support / norm);
    }

    private static double scoreCandidate(PetComponent selfComponent,
                                         PetContext selfContext,
                                         MobEntity self,
                                         PetContext candidateContext,
                                         MobEntity candidate) {
        PetComponent candidateComponent = PetComponent.get(candidate);
        if (candidateComponent == null) {
            return Double.NEGATIVE_INFINITY;
        }
        float comfort = (selfComponent.getComfortWith(candidate.getUuid()) + candidateComponent.getComfortWith(self.getUuid())) * 0.5f;
        double distanceScore = -self.squaredDistanceTo(candidate) * 0.08;
        double socialScore = candidateContext != null ? candidateContext.socialCharge() * 1.4 : 0.6;
        double stamina = candidateContext != null && candidateContext.behaviouralEnergyProfile() != null
            ? candidateContext.behaviouralEnergyProfile().physicalStamina()
            : 0.6;
        return comfort * 3.0 + distanceScore + stamina * 0.8 + socialScore;
    }

    private static BlockPos pickHideSpot(ServerWorld world, MobEntity hider, Vec3d reference) {
        Vec3d base = reference != null ? reference : (hider != null ? entityPosition(hider) : fallbackAnchor(world));
        BlockPos origin = BlockPos.ofFloored(base);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = 12;
        BlockPos best = origin;
        double bestCover = -1;
        while (attempts-- > 0) {
            int dx = random.nextInt(-4, 5);
            int dz = random.nextInt(-4, 5);
            BlockPos candidate = origin.add(dx, 0, dz);
            candidate = descendToGround(world, candidate);
            BlockState state = world.getBlockState(candidate.down());
            if (state.isAir()) {
                continue;
            }
            double cover = evaluateCover(world, candidate);
            if (cover > bestCover) {
                bestCover = cover;
                best = candidate;
            }
        }
        return best;
    }

    private static BlockPos pickSecondaryHideSpot(ServerWorld world, BlockPos primary, Random rng) {
        if (primary == null) {
            return null;
        }
        for (int i = 0; i < 6; i++) {
            Direction dir = Direction.Type.HORIZONTAL.random(rng);
            BlockPos candidate = descendToGround(world, primary.offset(dir));
            if (!world.isAir(candidate.down())) {
                return candidate;
            }
        }
        return primary;
    }

    private static double evaluateCover(ServerWorld world, BlockPos pos) {
        double cover = 0;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (!world.isAir(pos.offset(dir))) {
                cover += 1.0;
            }
        }
        return cover;
    }

    private static BlockPos descendToGround(World world, BlockPos pos) {
        BlockPos.Mutable mutable = pos.mutableCopy();
        int safety = 6;
        while (safety-- > 0 && world.isAir(mutable) && mutable.getY() > world.getBottomY()) {
            mutable.move(Direction.DOWN);
        }
        return mutable.getY() < world.getBottomY() ? pos : mutable.up();
    }

    private static Vec3d snapToGround(World world, Vec3d pos) {
        BlockPos.Mutable mutable = BlockPos.ofFloored(pos).mutableCopy();
        int maxDrop = 6;
        while (maxDrop-- > 0 && world.isAir(mutable) && mutable.getY() > world.getBottomY()) {
            mutable.move(Direction.DOWN);
        }
        if (world.isAir(mutable)) {
            return new Vec3d(pos.x, mutable.getY(), pos.z);
        }
        double y = mutable.getY() + 1.0;
        return new Vec3d(pos.x, y, pos.z);
    }

    private static MobEntity getMob(ServerWorld world, UUID uuid) {
        return uuid == null ? null : world.getEntity(uuid) instanceof MobEntity mob ? mob : null;
    }

    private static Vec3d entityPosition(MobEntity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private static Vec3d randomOffset(double radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * MathHelper.TAU;
        double distance = random.nextDouble() * radius;
        return new Vec3d(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private static void pushEmotion(Entity entity, PetComponent.Emotion emotion, float amount) {
        if (!(entity instanceof MobEntity mobEntity)) {
            return;
        }
        PetComponent component = PetComponent.get(mobEntity);
        if (component != null) {
            component.pushEmotion(emotion, amount);
        }
    }

    private static boolean shouldTearDown(HideSession session) {
        return session.ready.isEmpty();
    }

    private static void tearDownSession(HideSession session) {
        for (UUID uuid : session.participants) {
            SESSION_BY_PET.remove(uuid, session.id);
            INVITATIONS.remove(uuid);
        }
        for (UUID uuid : session.spectators) {
            SESSION_BY_PET.remove(uuid, session.id);
            INVITATIONS.remove(uuid);
        }
        SESSIONS.remove(session.id);
    }

    private static void cleanupSessions(ServerWorld world, long now) {
        Iterator<Map.Entry<UUID, HideSession>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HideSession> entry = iterator.next();
            HideSession session = entry.getValue();
            if (session == null) {
                iterator.remove();
                continue;
            }
            if (now >= session.expiryTick) {
                tearDownSession(session);
                iterator.remove();
                continue;
            }
            if (!Objects.equals(session.worldKey, world.getRegistryKey())) {
                continue;
            }
        }
    }

    private static String pairCooldownKey(List<UUID> participants) {
        List<UUID> copy = new ArrayList<>(participants);
        copy.sort(UUID::compareTo);
        return "hide_and_seek_group:" + copy.stream().map(UUID::toString).collect(Collectors.joining(":"));
    }

    private static SessionProfile blendProfiles(SessionProfile current, SessionProfile target) {
        if (target == null) {
            return current;
        }
        if (current == null) {
            return target;
        }
        float factor = 0.35f;
        return new SessionProfile(
            MathHelper.lerp(factor, current.scanRadius, target.scanRadius),
            MathHelper.lerp(factor, current.formationToleranceSq, target.formationToleranceSq),
            MathHelper.lerp(factor, current.formationRadius, target.formationRadius),
            MathHelper.lerp(factor, current.supportOrbitRadius, target.supportOrbitRadius),
            MathHelper.lerp(factor, current.hideArrivalSq, target.hideArrivalSq),
            MathHelper.lerp(factor, current.seekerCaptureSq, target.seekerCaptureSq),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.joinerGraceTicks, target.joinerGraceTicks))),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.formationTimeoutTicks, target.formationTimeoutTicks))),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.countdownTicks, target.countdownTicks))),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.seekTimeoutTicks, target.seekTimeoutTicks))),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.celebrateTicks, target.celebrateTicks))),
            Math.max(1, MathHelper.floor(MathHelper.lerp(factor, current.sessionMaxTicks, target.sessionMaxTicks))),
            MathHelper.lerp(factor, current.seekerSpeed, target.seekerSpeed),
            MathHelper.lerp(factor, current.hiderSpeed, target.hiderSpeed),
            MathHelper.lerp(factor, current.supportSpeed, target.supportSpeed)
        );
    }

    private static int secondsToTicks(int seconds) {
        return seconds * 20;
    }
}
