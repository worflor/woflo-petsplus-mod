package woflo.petsplus.ai.goals.special.hideandseek;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Central authority for hide-and-seek sessions. The manager owns every
 * join/leave lifecycle mutation so role goals can interact with the vignette
 * without touching shared maps directly.
 */
public final class HideAndSeekSessionManager {

    public static final int MAX_PARTICIPANTS = 5;
    private static final int MAX_HIDERS = 2;
    private static final double WAITING_QUIRK_CHANCE = 0.035;
    private static final double SEEK_QUIRK_CHANCE = 0.02;
    private static final double CELEBRATE_QUIRK_CHANCE = 0.08;
    private static final long QUIRK_COOLDOWN_TICKS = 80;
    private static final long ANCHOR_STALE_TICKS = 120;
    private static final double ANCHOR_UPDATE_EPSILON_SQ = 0.01;

    private static final HideAndSeekSessionManager INSTANCE = new HideAndSeekSessionManager();
    static final long HINT_STALE_TICKS = 200;
    private static final long HINT_BETRAYAL_COOLDOWN = 200;
    static final double BETRAYAL_REQUIRED_MOTIVATION = 0.6;
    private static final double BETRAYAL_MAX_MOTIVATION = 1.5;
    private static final long BETRAYAL_WINDOW_TICKS = 140;
    private static final double BETRAYAL_DECAY_PER_TICK = Math.log(2.0) / 120.0;
    private static final long SOFT_HINT_MIN_INTERVAL_TICKS = 35;
    private static final double SOFT_HINT_REPEAT_EPSILON_SQ = MathHelper.square(0.8);
    private static final double SOFT_HINT_SMOOTHING_WEIGHT_FLOOR = 0.25;

    public static HideAndSeekSessionManager getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sessionByPet = new ConcurrentHashMap<>();
    private final Map<UUID, CopyOnWriteArrayList<SessionListener>> listeners = new ConcurrentHashMap<>();
    private final Map<RegistryKey<World>, Long> lastMaintenanceByWorld = new ConcurrentHashMap<>();

    private HideAndSeekSessionManager() {
    }

    public enum Phase {
        WAITING,
        FORMATION,
        COUNTDOWN,
        SEEK,
        CELEBRATE
    }

    public enum Role {
        DIRECTOR,
        SEEKER,
        HIDER,
        SUPPORT,
        SPECTATOR
    }

    public interface SessionEvent {
        UUID sessionId();
    }

    public interface SessionListener {
        void onSessionEvent(SessionEvent event);
    }

    public record PhaseChangeEvent(UUID sessionId, Phase previous, Phase current, SessionView view)
        implements SessionEvent {
    }

    public record RoleAssignmentEvent(UUID sessionId, UUID petId, Role role, SessionView view)
        implements SessionEvent {
    }

    public record SessionClosedEvent(UUID sessionId) implements SessionEvent {
    }

    public record SessionMetricsEvent(UUID sessionId, SessionView view) implements SessionEvent {
    }

    public record CaptureEvent(UUID sessionId, UUID seekerId, UUID hiderId, SessionView view)
        implements SessionEvent {
    }

    public record HideHintEvent(UUID sessionId,
                                UUID targetId,
                                UUID reporterId,
                                Vec3d hint,
                                boolean betrayal,
                                SessionView view) implements SessionEvent {
    }

    public enum Quirk {
        TAIL_WIGGLE,
        PEEKABOO,
        SEEKER_POUNCE,
        CELEBRATION_SPIN
    }

    public record QuirkEvent(UUID sessionId, UUID instigator, Quirk quirk, SessionView view)
        implements SessionEvent {
    }

    public enum BetrayalTrigger {
        SIGHTLINE,
        CONTACT,
        TAUNT
    }

    public static final class SessionProfile {
        public final double scanRadius;
        public final double formationToleranceSq;
        public final double formationRadius;
        public final double supportOrbitRadius;
        public final double hideArrivalSq;
        public final double seekerCaptureSq;
        public final int joinerGraceTicks;
        public final int formationTimeoutTicks;
        public final int countdownTicks;
        public final int seekTimeoutTicks;
        public final int celebrateTicks;
        public final int sessionMaxTicks;
        public final double seekerSpeed;
        public final double hiderSpeed;
        public final double supportSpeed;

        public SessionProfile(double scanRadius,
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

        public static SessionProfile fromContexts(List<PetContext> contexts) {
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

        private static int secondsToTicks(int seconds) {
            return Math.max(0, seconds) * 20;
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

    public record HideHint(Vec3d position, UUID reporterId, long tick, boolean betrayal) {
    }

    public static final class SessionView {
        private final UUID sessionId;
        private final RegistryKey<World> worldKey;
        private final UUID ownerId;
        private final UUID directorId;
        private final UUID seekerId;
        private final Phase phase;
        private final SessionProfile profile;
        private final Set<UUID> participants;
        private final Set<UUID> spectators;
        private final Map<UUID, Role> roles;
        private final Vec3d anchor;
        private final int roundsCompleted;
        private final long createdTick;
        private final Quirk lastQuirk;
        private final long lastQuirkTick;
        private final Map<UUID, HideHint> hints;

        private SessionView(UUID sessionId,
                            RegistryKey<World> worldKey,
                            UUID ownerId,
                            UUID directorId,
                            UUID seekerId,
                            Phase phase,
                            SessionProfile profile,
                            Set<UUID> participants,
                            Set<UUID> spectators,
                            Map<UUID, Role> roles,
                            Vec3d anchor,
                            int roundsCompleted,
                            long createdTick,
                            Quirk lastQuirk,
                            long lastQuirkTick,
                            Map<UUID, HideHint> hints) {
            this.sessionId = sessionId;
            this.worldKey = worldKey;
            this.ownerId = ownerId;
            this.directorId = directorId;
            this.seekerId = seekerId;
            this.phase = phase;
            this.profile = profile;
            this.participants = Collections.unmodifiableSet(participants);
            this.spectators = Collections.unmodifiableSet(spectators);
            this.roles = Collections.unmodifiableMap(roles);
            this.anchor = anchor;
            this.roundsCompleted = roundsCompleted;
            this.createdTick = createdTick;
            this.lastQuirk = lastQuirk;
            this.lastQuirkTick = lastQuirkTick;
            this.hints = Collections.unmodifiableMap(hints);
        }

        public UUID sessionId() {
            return sessionId;
        }

        public RegistryKey<World> worldKey() {
            return worldKey;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public UUID directorId() {
            return directorId;
        }

        public UUID seekerId() {
            return seekerId;
        }

        public Phase phase() {
            return phase;
        }

        public SessionProfile profile() {
            return profile;
        }

        public Set<UUID> participants() {
            return participants;
        }

        public Set<UUID> spectators() {
            return spectators;
        }

        public Map<UUID, Role> roles() {
            return roles;
        }

        public Vec3d anchor() {
            return anchor;
        }

        public int roundsCompleted() {
            return roundsCompleted;
        }

        public long createdTick() {
            return createdTick;
        }

        public Optional<Quirk> lastQuirk() {
            return Optional.ofNullable(lastQuirk);
        }

        public long lastQuirkTick() {
            return lastQuirkTick;
        }

        public Map<UUID, HideHint> hints() {
            return hints;
        }

        public Role roleOf(UUID petId) {
            if (Objects.equals(petId, directorId)) {
                return Role.DIRECTOR;
            }
            return roles.getOrDefault(petId, Role.SPECTATOR);
        }

        public boolean includes(UUID petId) {
            return participants.contains(petId) || spectators.contains(petId);
        }
    }

    private static final class Session {
        final UUID id = UUID.randomUUID();
        final RegistryKey<World> worldKey;
        final UUID ownerId;
        final UUID directorId;
        final Map<UUID, PetContext> contexts;
        SessionProfile profile;
        final CopyOnWriteArrayList<UUID> participants;
        final Set<UUID> spectators;
        final Map<UUID, Role> roles;
        final Map<UUID, HideHint> hints;
        final Map<UUID, Long> lastSoftHintTickByReporter;
        final Map<UUID, Vec3d> lastSoftHintPositionByReporter;
        final Set<UUID> poorSports;
        final Map<UUID, Long> lastBetrayalTickByPet;
        final Map<UUID, Map<UUID, BetrayalMemory>> betrayalMotives;
        Phase phase = Phase.WAITING;
        UUID seekerId;
        Vec3d anchor;
        int roundsCompleted;
        final long createdTick;
        long expiryTick;
        boolean closing;
        long lastAnchorTick;
        Quirk lastQuirk;
        long lastQuirkTick = Long.MIN_VALUE;

        Session(RegistryKey<World> worldKey,
                UUID ownerId,
                UUID directorId,
                Map<UUID, PetContext> contexts,
                List<UUID> participants,
                SessionProfile profile,
                long createdTick,
                Vec3d anchor) {
            this.worldKey = worldKey;
            this.ownerId = ownerId;
            this.directorId = directorId;
            this.contexts = new ConcurrentHashMap<>(contexts);
            this.profile = profile;
            this.participants = new CopyOnWriteArrayList<>(participants);
            this.spectators = ConcurrentHashMap.newKeySet();
            this.roles = new ConcurrentHashMap<>();
            this.hints = new ConcurrentHashMap<>();
            this.lastSoftHintTickByReporter = new ConcurrentHashMap<>();
            this.lastSoftHintPositionByReporter = new ConcurrentHashMap<>();
            this.poorSports = ConcurrentHashMap.newKeySet();
            this.lastBetrayalTickByPet = new ConcurrentHashMap<>();
            this.betrayalMotives = new ConcurrentHashMap<>();
            this.anchor = anchor;
            this.createdTick = createdTick;
            this.expiryTick = createdTick + profile.sessionMaxTicks;
            this.lastAnchorTick = createdTick;
        }

        SessionView snapshot() {
            return new SessionView(
                id,
                worldKey,
                ownerId,
                directorId,
                seekerId,
                phase,
                profile,
                Set.copyOf(participants),
                Set.copyOf(spectators),
                Map.copyOf(roles),
                anchor,
                roundsCompleted,
                createdTick,
                lastQuirk,
                lastQuirkTick,
                Map.copyOf(hints)
            );
        }

        boolean canAcceptNewParticipant() {
            return !closing && participants.size() < MAX_PARTICIPANTS;
        }

        boolean contains(UUID uuid) {
            return participants.contains(uuid) || spectators.contains(uuid);
        }

        void refreshProfile() {
            profile = SessionProfile.fromContexts(new ArrayList<>(contexts.values()));
            expiryTick = Math.max(expiryTick, createdTick + profile.sessionMaxTicks);
        }

        void recordQuirk(Quirk quirk, long tick) {
            lastQuirk = quirk;
            lastQuirkTick = tick;
        }
    }

    private static final class BetrayalMemory {
        double motivation;
        long lastTick;
        BetrayalTrigger lastTrigger;

        BetrayalMemory(double motivation, long lastTick, BetrayalTrigger lastTrigger) {
            this.motivation = motivation;
            this.lastTick = lastTick;
            this.lastTrigger = lastTrigger;
        }
    }

    public void registerListener(UUID petId, SessionListener listener) {
        if (listener == null) {
            unregisterAllListeners(petId);
            return;
        }
        listeners.computeIfAbsent(petId, id -> new CopyOnWriteArrayList<>()).addIfAbsent(listener);
    }

    public void unregisterListener(UUID petId, SessionListener listener) {
        if (listener == null) {
            unregisterAllListeners(petId);
            return;
        }
        listeners.computeIfPresent(petId, (id, existing) -> {
            existing.remove(listener);
            return existing.isEmpty() ? null : existing;
        });
    }

    public void unregisterAllListeners(UUID petId) {
        listeners.remove(petId);
    }

    public Optional<SessionView> findSessionFor(UUID petId) {
        UUID sessionId = sessionByPet.get(petId);
        if (sessionId == null) {
            return Optional.empty();
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            sessionByPet.remove(petId);
            return Optional.empty();
        }
        return Optional.of(session.snapshot());
    }

    public Optional<SessionView> findSession(UUID sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    public Optional<SessionView> findJoinableSession(UUID ownerId, RegistryKey<World> worldKey) {
        return sessions.values().stream()
            .filter(session -> Objects.equals(session.ownerId, ownerId))
            .filter(session -> Objects.equals(session.worldKey, worldKey))
            .filter(Session::canAcceptNewParticipant)
            .findFirst()
            .map(Session::snapshot);
    }

    public Optional<SessionView> joinSession(MobEntity mob, SessionView view) {
        Session session = sessions.get(view.sessionId());
        if (session == null || session.closing || !Objects.equals(session.worldKey, view.worldKey())) {
            return Optional.empty();
        }
        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return Optional.empty();
        }
        PetContext context = PetContext.capture(mob, component);
        return Optional.of(joinSession(session, mob, context));
    }

    private SessionView joinSession(Session session, MobEntity mob, PetContext context) {
        UUID mobId = mob.getUuid();
        if (session.contains(mobId)) {
            return session.snapshot();
        }
        boolean safeToJoinAsParticipant = session.phase == Phase.WAITING
            || session.phase == Phase.FORMATION
            || session.phase == Phase.CELEBRATE;
        SessionView snapshot;
        if (safeToJoinAsParticipant && session.canAcceptNewParticipant()) {
            snapshot = addParticipant(session, mobId, context);
        } else {
            snapshot = addSpectator(session, mobId, context);
        }
        return snapshot;
    }

    public boolean isPoorSport(UUID sessionId, UUID petId) {
        Session session = sessions.get(sessionId);
        return session != null && session.poorSports.contains(petId);
    }

    private SessionView addParticipant(Session session, UUID mobId, PetContext context) {
        session.participants.addIfAbsent(mobId);
        session.roles.put(mobId, Role.SUPPORT);
        session.contexts.put(mobId, context);
        maybeMarkPoorSport(session, mobId, context);
        session.refreshProfile();
        sessionByPet.put(mobId, session.id);
        SessionView snapshot = session.snapshot();
        notifySession(session, new SessionMetricsEvent(session.id, snapshot));
        notifyListener(mobId, new RoleAssignmentEvent(session.id, mobId, Role.SUPPORT, snapshot));
        return snapshot;
    }

    private SessionView addSpectator(Session session, UUID mobId, PetContext context) {
        session.spectators.add(mobId);
        session.roles.put(mobId, Role.SPECTATOR);
        if (context != null) {
            session.contexts.put(mobId, context);
            maybeMarkPoorSport(session, mobId, context);
        }
        sessionByPet.put(mobId, session.id);
        SessionView snapshot = session.snapshot();
        notifySession(session, new SessionMetricsEvent(session.id, snapshot));
        notifyListener(mobId, new RoleAssignmentEvent(session.id, mobId, Role.SPECTATOR, snapshot));
        return snapshot;
    }

    public void recordSoftHint(UUID sessionId, UUID targetId, Vec3d position, UUID reporterId, long tick) {
        if (position == null) {
            return;
        }
        Session session = sessions.get(sessionId);
        if (session == null || session.closing) {
            return;
        }
        if (!session.contains(reporterId)) {
            return;
        }
        if (session.roles.get(targetId) != Role.HIDER) {
            return;
        }
        if (!shouldAcceptSoftHint(session, reporterId, position, tick)) {
            return;
        }
        Vec3d sanitized = smoothSoftHint(session, reporterId, position, tick);
        registerHint(session, targetId, sanitized, reporterId, false, tick);
    }

    public double noteBetrayalOpportunity(UUID sessionId,
                                          UUID reporterId,
                                          UUID targetId,
                                          BetrayalTrigger trigger,
                                          double weight,
                                          long tick) {
        Session session = sessions.get(sessionId);
        if (session == null || session.closing) {
            return 0.0;
        }
        if (session.phase != Phase.SEEK) {
            return getBetrayalReadiness(session, reporterId, targetId, tick, true);
        }
        if (!session.contains(reporterId) || session.roles.get(targetId) != Role.HIDER) {
            return 0.0;
        }
        if (!session.poorSports.contains(reporterId)) {
            return 0.0;
        }
        double sanitized = MathHelper.clamp(weight, 0.0, 1.0);
        if (sanitized > 0.0) {
            session.betrayalMotives
                .computeIfAbsent(reporterId, id -> new ConcurrentHashMap<>())
                .compute(targetId, (id, memory) -> {
                    if (memory == null) {
                        return new BetrayalMemory(
                            MathHelper.clamp(sanitized, 0.0, BETRAYAL_MAX_MOTIVATION),
                            tick,
                            trigger
                        );
                    }
                    double decayed = decayMotivation(memory.motivation, tick - memory.lastTick);
                    double updated = MathHelper.clamp(decayed + sanitized, 0.0, BETRAYAL_MAX_MOTIVATION);
                    memory.motivation = updated;
                    memory.lastTick = tick;
                    memory.lastTrigger = trigger;
                    return memory;
                });
        }
        return getBetrayalReadiness(session, reporterId, targetId, tick, true);
    }

    public boolean reportBetrayal(UUID sessionId, UUID reporterId, UUID targetId, Vec3d position, long tick) {
        if (position == null || Objects.equals(reporterId, targetId)) {
            return false;
        }
        Session session = sessions.get(sessionId);
        if (session == null || session.closing) {
            return false;
        }
        if (!session.contains(reporterId) || session.roles.get(targetId) != Role.HIDER) {
            return false;
        }
        if (!session.poorSports.contains(reporterId)) {
            return false;
        }
        if (session.phase != Phase.SEEK) {
            return false;
        }
        Role reporterRole = session.roles.getOrDefault(reporterId, Role.SPECTATOR);
        if (reporterRole != Role.SUPPORT && reporterRole != Role.SPECTATOR) {
            return false;
        }
        long last = session.lastBetrayalTickByPet.getOrDefault(reporterId, Long.MIN_VALUE);
        if (tick - last < HINT_BETRAYAL_COOLDOWN) {
            return false;
        }
        double readiness = getBetrayalReadiness(session, reporterId, targetId, tick, true);
        if (readiness < BETRAYAL_REQUIRED_MOTIVATION) {
            return false;
        }
        session.lastBetrayalTickByPet.put(reporterId, tick);
        registerHint(session, targetId, position, reporterId, true, tick);
        Map<UUID, BetrayalMemory> motives = session.betrayalMotives.get(reporterId);
        if (motives != null) {
            motives.remove(targetId);
            if (motives.isEmpty()) {
                session.betrayalMotives.remove(reporterId);
            }
        }
        return true;
    }

    public void clearHint(UUID sessionId, UUID petId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (session.hints.remove(petId) != null) {
            notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
        }
    }

    public double betrayalReadiness(UUID sessionId, UUID reporterId, UUID targetId, long tick) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return 0.0;
        }
        return getBetrayalReadiness(session, reporterId, targetId, tick, true);
    }

    public SessionView createSession(ServerWorld world,
                                     MobEntity director,
                                     PetComponent directorComponent,
                                     Map<UUID, PetContext> contexts,
                                     List<MobEntity> participants,
                                     long now) {
        UUID ownerId = directorComponent.getOwnerUuid();
        if (ownerId == null) {
            return null;
        }
        List<UUID> all = new ArrayList<>();
        all.add(director.getUuid());
        for (MobEntity entity : participants) {
            all.add(entity.getUuid());
        }
        SessionProfile profile = SessionProfile.fromContexts(new ArrayList<>(contexts.values()));
        Vec3d anchor = new Vec3d(director.getX(), director.getY(), director.getZ());
        Session session = new Session(world.getRegistryKey(), ownerId, director.getUuid(), contexts, all, profile, now, anchor);
        sessions.put(session.id, session);
        session.roles.put(director.getUuid(), Role.DIRECTOR);
        maybeMarkPoorSport(session, director.getUuid(), contexts.get(director.getUuid()));
        sessionByPet.put(director.getUuid(), session.id);
        for (MobEntity entity : participants) {
            UUID id = entity.getUuid();
            session.roles.put(id, Role.SUPPORT);
            sessionByPet.put(id, session.id);
            maybeMarkPoorSport(session, id, contexts.get(id));
        }
        SessionView view = session.snapshot();
        notifySession(session, new SessionMetricsEvent(session.id, view));
        notifyListener(director.getUuid(), new RoleAssignmentEvent(session.id, director.getUuid(), Role.DIRECTOR, view));
        for (MobEntity entity : participants) {
            UUID id = entity.getUuid();
            notifyListener(id, new RoleAssignmentEvent(session.id, id, Role.SUPPORT, view));
        }
        return view;
    }

    public void updateAnchor(UUID sessionId, Vec3d anchor) {
        updateAnchor(sessionId, anchor, -1);
    }

    public void updateAnchor(UUID sessionId, Vec3d anchor, long tick) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        boolean changed = session.anchor == null
            || session.anchor.squaredDistanceTo(anchor) > ANCHOR_UPDATE_EPSILON_SQ;
        session.anchor = anchor;
        if (tick >= 0) {
            session.lastAnchorTick = tick;
        }
        if (changed) {
            notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
        }
    }

    public void setPhase(UUID sessionId, Phase newPhase) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (session.phase == newPhase) {
            return;
        }
        Phase previous = session.phase;
        session.phase = newPhase;
        if (newPhase != Phase.SEEK) {
            session.hints.clear();
            session.lastSoftHintTickByReporter.clear();
            session.lastSoftHintPositionByReporter.clear();
            session.lastBetrayalTickByPet.clear();
            session.betrayalMotives.clear();
        }
        SessionView view = session.snapshot();
        notifySession(session, new PhaseChangeEvent(session.id, previous, newPhase, view));
        if (newPhase == Phase.CELEBRATE) {
            broadcastQuirk(session, Quirk.CELEBRATION_SPIN, session.seekerId, session.lastAnchorTick);
        } else if (newPhase == Phase.WAITING && previous != Phase.WAITING) {
            UUID instigator = session.seekerId != null ? session.seekerId : session.directorId;
            broadcastQuirk(session, Quirk.TAIL_WIGGLE, instigator, session.lastAnchorTick);
        }
    }

    public void assignRoles(UUID sessionId, Map<UUID, Role> newAssignments, UUID seekerId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        UUID previousSeeker = session.seekerId;
        session.roles.clear();
        session.roles.put(session.directorId, Role.DIRECTOR);
        for (UUID spectator : session.spectators) {
            session.roles.put(spectator, Role.SPECTATOR);
        }
        for (UUID participant : session.participants) {
            if (Objects.equals(participant, session.directorId)) {
                continue;
            }
            Role role = newAssignments.getOrDefault(participant, Role.SUPPORT);
            session.roles.put(participant, role);
        }
        session.seekerId = seekerId;
        SessionView view = session.snapshot();
        for (Map.Entry<UUID, Role> entry : session.roles.entrySet()) {
            notifyListener(entry.getKey(), new RoleAssignmentEvent(session.id, entry.getKey(), entry.getValue(), view));
        }
        if (seekerId != null && !Objects.equals(previousSeeker, seekerId)) {
            broadcastQuirk(session, Quirk.PEEKABOO, seekerId, session.lastAnchorTick);
        }
    }

    public void markRoundComplete(UUID sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        session.roundsCompleted++;
        notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
    }

    public void leaveSession(UUID petId) {
        UUID sessionId = sessionByPet.remove(petId);
        if (sessionId == null) {
            return;
        }
        unregisterAllListeners(petId);
        Session session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        boolean removedParticipant = session.participants.remove(petId);
        session.spectators.remove(petId);
        session.contexts.remove(petId);
        session.roles.remove(petId);
        session.hints.remove(petId);
        session.lastSoftHintTickByReporter.remove(petId);
        session.lastSoftHintPositionByReporter.remove(petId);
        session.poorSports.remove(petId);
        session.lastBetrayalTickByPet.remove(petId);
        scrubBetrayalMemory(session, petId);
        if (removedParticipant) {
            session.refreshProfile();
        }
        boolean seekerDeparted = Objects.equals(session.seekerId, petId);
        if (session.participants.size() <= 1) {
            if (session.participants.isEmpty()) {
                closeSession(session);
                return;
            }
            setPhase(session.id, Phase.WAITING);
            session.seekerId = null;
        } else if (seekerDeparted) {
            SessionView reassess = session.snapshot();
            Map<UUID, Role> assignments = buildDefaultRoles(reassess, session.directorId);
            UUID newSeeker = assignments.entrySet().stream()
                .filter(entry -> entry.getValue() == Role.SEEKER)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
            assignRoles(session.id, assignments, newSeeker);
        }
        if (session.participants.isEmpty()) {
            closeSession(session);
        } else {
            notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
        }
    }

    public boolean captureHider(UUID sessionId, UUID seekerId, UUID hiderId, long tick) {
        Session session = sessions.get(sessionId);
        if (session == null || session.closing) {
            return false;
        }
        if (!session.participants.contains(hiderId)) {
            return false;
        }
        if (session.roles.get(hiderId) != Role.HIDER) {
            return false;
        }
        if (seekerId != null) {
            Role seekerRole = session.roles.get(seekerId);
            if (seekerRole != Role.SEEKER) {
                return false;
            }
        }
        if (seekerId != null) {
            session.seekerId = seekerId;
        }
        session.roles.put(hiderId, Role.SUPPORT);
        session.hints.remove(hiderId);
        scrubBetrayalMemory(session, hiderId);
        SessionView view = session.snapshot();
        notifyListener(hiderId, new RoleAssignmentEvent(session.id, hiderId, Role.SUPPORT, view));
        notifySession(session, new SessionMetricsEvent(session.id, view));
        notifySession(session, new CaptureEvent(session.id, seekerId, hiderId, view));
        broadcastQuirk(session, Quirk.SEEKER_POUNCE, seekerId, tick);
        return true;
    }

    public void closeSession(SessionView view) {
        Session session = sessions.get(view.sessionId());
        if (session == null) {
            return;
        }
        closeSession(session);
    }

    private void closeSession(Session session) {
        if (session.closing) {
            return;
        }
        session.closing = true;
        sessions.remove(session.id);
        session.betrayalMotives.clear();
        session.lastSoftHintTickByReporter.clear();
        session.lastSoftHintPositionByReporter.clear();
        notifySession(session, new SessionClosedEvent(session.id));
        for (UUID participant : session.participants) {
            sessionByPet.remove(participant, session.id);
            unregisterAllListeners(participant);
        }
        for (UUID spectator : session.spectators) {
            sessionByPet.remove(spectator, session.id);
            unregisterAllListeners(spectator);
        }
        boolean hasWorldSessions = sessions.values().stream()
            .anyMatch(remaining -> Objects.equals(remaining.worldKey, session.worldKey));
        if (!hasWorldSessions) {
            lastMaintenanceByWorld.remove(session.worldKey);
        }
    }

    private void notifySession(Session session, SessionEvent event) {
        for (UUID participant : session.participants) {
            notifyListener(participant, event);
        }
        for (UUID spectator : session.spectators) {
            notifyListener(spectator, event);
        }
    }

    private void notifyListener(UUID petId, SessionEvent event) {
        CopyOnWriteArrayList<SessionListener> registered = listeners.get(petId);
        if (registered == null || registered.isEmpty()) {
            return;
        }
        for (SessionListener listener : registered) {
            listener.onSessionEvent(event);
        }
    }

    private void broadcastQuirk(Session session, Quirk quirk, UUID instigator, long tick) {
        if (session == null || session.closing) {
            return;
        }
        long appliedTick = tick >= 0 ? tick : session.lastAnchorTick;
        session.recordQuirk(quirk, appliedTick);
        SessionView view = session.snapshot();
        notifySession(session, new QuirkEvent(session.id, instigator, quirk, view));
    }

    public void tickMaintenance(ServerWorld world, long now) {
        RegistryKey<World> key = world.getRegistryKey();
        Long previous = lastMaintenanceByWorld.get(key);
        if (previous != null && previous == now) {
            return;
        }
        lastMaintenanceByWorld.put(key, now);
        List<Session> worldSessions = sessions.values().stream()
            .filter(session -> Objects.equals(session.worldKey, world.getRegistryKey()))
            .collect(Collectors.toList());
        for (Session session : worldSessions) {
            if (session.closing) {
                continue;
            }
            if (pruneInvalidParticipants(world, session)) {
                continue;
            }
            if (session.expiryTick <= now) {
                closeSession(session);
                continue;
            }
            if (session.participants.isEmpty()) {
                closeSession(session);
                continue;
            }
            if (!session.participants.contains(session.directorId)) {
                closeSession(session);
                continue;
            }
            promoteSpectators(world, session);
            if (session.closing) {
                continue;
            }
            ensureAnchorFresh(world, session, now);
            ensurePhaseViable(session);
            ensureSeekerPresent(session);
            maybeTriggerAmbientQuirk(world, session, now);
            pruneStaleHints(session, now);
            decayBetrayalMotives(session, now);
        }
    }

    private boolean pruneInvalidParticipants(ServerWorld world, Session session) {
        boolean changed = false;
        for (UUID participant : new ArrayList<>(session.participants)) {
            Entity entity = world.getEntity(participant);
            if (!(entity instanceof MobEntity mobEntity) || !mobEntity.isAlive()) {
                session.participants.remove(participant);
                session.roles.remove(participant);
                session.contexts.remove(participant);
                sessionByPet.remove(participant, session.id);
                unregisterAllListeners(participant);
                session.hints.remove(participant);
                session.lastSoftHintTickByReporter.remove(participant);
                session.lastSoftHintPositionByReporter.remove(participant);
                session.poorSports.remove(participant);
                session.lastBetrayalTickByPet.remove(participant);
                scrubBetrayalMemory(session, participant);
                if (Objects.equals(session.seekerId, participant)) {
                    session.seekerId = null;
                }
                changed = true;
            }
        }
        for (UUID spectator : new ArrayList<>(session.spectators)) {
            Entity entity = world.getEntity(spectator);
            if (!(entity instanceof MobEntity mobEntity) || !mobEntity.isAlive()) {
                session.spectators.remove(spectator);
                session.roles.remove(spectator);
                session.contexts.remove(spectator);
                sessionByPet.remove(spectator, session.id);
                unregisterAllListeners(spectator);
                session.hints.remove(spectator);
                session.lastSoftHintTickByReporter.remove(spectator);
                session.lastSoftHintPositionByReporter.remove(spectator);
                session.poorSports.remove(spectator);
                session.lastBetrayalTickByPet.remove(spectator);
                scrubBetrayalMemory(session, spectator);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        if (session.participants.isEmpty()) {
            closeSession(session);
            return true;
        }
        session.refreshProfile();
        notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
        return session.closing;
    }

    private void promoteSpectators(ServerWorld world, Session session) {
        if (!session.canAcceptNewParticipant() || session.spectators.isEmpty()) {
            return;
        }
        if (session.phase == Phase.COUNTDOWN || session.phase == Phase.SEEK) {
            return;
        }
        List<UUID> promoted = new ArrayList<>();
        for (UUID spectator : new ArrayList<>(session.spectators)) {
            if (!session.canAcceptNewParticipant()) {
                break;
            }
            if (!session.spectators.remove(spectator)) {
                continue;
            }
            session.participants.addIfAbsent(spectator);
            session.roles.put(spectator, Role.SUPPORT);
            sessionByPet.put(spectator, session.id);
            promoted.add(spectator);
            if (!session.contexts.containsKey(spectator)) {
                Entity entity = world.getEntity(spectator);
                if (entity instanceof MobEntity mobEntity) {
                    PetComponent component = PetComponent.get(mobEntity);
                    if (component != null) {
                        session.contexts.put(spectator, PetContext.capture(mobEntity, component));
                    }
                }
            }
            PetContext snapshot = session.contexts.get(spectator);
            if (snapshot != null) {
                maybeMarkPoorSport(session, spectator, snapshot);
            }
        }
        if (promoted.isEmpty()) {
            return;
        }
        session.refreshProfile();
        SessionView view = session.snapshot();
        notifySession(session, new SessionMetricsEvent(session.id, view));
        for (UUID spectator : promoted) {
            notifyListener(spectator, new RoleAssignmentEvent(session.id, spectator, Role.SUPPORT, view));
        }
    }

    private void ensureAnchorFresh(ServerWorld world, Session session, long now) {
        if (session.anchor != null && now - session.lastAnchorTick <= ANCHOR_STALE_TICKS) {
            return;
        }
        Vec3d candidate = resolveAnchorCandidate(world, session);
        if (candidate == null) {
            closeSession(session);
            return;
        }
        updateAnchor(session.id, candidate, now);
    }

    private Vec3d resolveAnchorCandidate(ServerWorld world, Session session) {
        Entity director = world.getEntity(session.directorId);
        if (director != null && director.isAlive()) {
            return entityPosition(director);
        }
        for (UUID participant : session.participants) {
            if (Objects.equals(participant, session.directorId)) {
                continue;
            }
            Entity entity = world.getEntity(participant);
            if (entity != null && entity.isAlive()) {
                return entityPosition(entity);
            }
        }
        for (UUID spectator : session.spectators) {
            Entity entity = world.getEntity(spectator);
            if (entity != null && entity.isAlive()) {
                return entityPosition(entity);
            }
        }
        return null;
    }

    private void ensurePhaseViable(Session session) {
        if (session.participants.size() <= 1) {
            if (session.phase != Phase.WAITING) {
                setPhase(session.id, Phase.WAITING);
            }
            session.seekerId = null;
        }
    }

    private void ensureSeekerPresent(Session session) {
        if (session.participants.size() <= 1) {
            return;
        }
        if (session.seekerId != null && session.participants.contains(session.seekerId)) {
            return;
        }
        SessionView view = session.snapshot();
        Map<UUID, Role> assignments = buildDefaultRoles(view, session.directorId);
        UUID seeker = assignments.entrySet().stream()
            .filter(entry -> entry.getValue() == Role.SEEKER)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        if (assignments.isEmpty()) {
            return;
        }
        assignRoles(session.id, assignments, seeker);
    }

    private void maybeTriggerAmbientQuirk(ServerWorld world, Session session, long now) {
        if (session.lastQuirkTick != Long.MIN_VALUE && now - session.lastQuirkTick < QUIRK_COOLDOWN_TICKS) {
            return;
        }
        double chance;
        Quirk quirk;
        switch (session.phase) {
            case WAITING -> {
                chance = WAITING_QUIRK_CHANCE;
                quirk = Quirk.TAIL_WIGGLE;
            }
            case SEEK -> {
                chance = SEEK_QUIRK_CHANCE;
                quirk = Quirk.SEEKER_POUNCE;
            }
            case CELEBRATE -> {
                chance = CELEBRATE_QUIRK_CHANCE;
                quirk = Quirk.CELEBRATION_SPIN;
            }
            default -> {
                chance = 0.0;
                quirk = null;
            }
        }
        if (chance <= 0.0 || quirk == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        UUID instigator = pickLivingParticipant(world, session);
        broadcastQuirk(session, quirk, instigator, now);
    }

    private UUID pickLivingParticipant(ServerWorld world, Session session) {
        List<UUID> live = new ArrayList<>();
        for (UUID participant : session.participants) {
            Entity entity = world.getEntity(participant);
            if (entity != null && entity.isAlive()) {
                live.add(participant);
            }
        }
        if (live.isEmpty()) {
            for (UUID spectator : session.spectators) {
                Entity entity = world.getEntity(spectator);
                if (entity != null && entity.isAlive()) {
                    live.add(spectator);
                }
            }
        }
        if (live.isEmpty()) {
            return null;
        }
        return live.get(ThreadLocalRandom.current().nextInt(live.size()));
    }

    private static Vec3d entityPosition(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private void maybeMarkPoorSport(Session session, UUID mobId, PetContext context) {
        if (session.poorSports.contains(mobId)) {
            return;
        }
        double chance = 0.08;
        if (context != null) {
            BehaviouralEnergyProfile energyProfile = context.behaviouralEnergyProfile();
            if (energyProfile != null) {
                if (energyProfile.socialCharge() < 0.35f) {
                    chance += 0.12;
                }
                if (energyProfile.momentum() > 0.65f) {
                    chance += 0.08;
                }
                if (energyProfile.rawSocialActivity() < 0.1f) {
                    chance += 0.04;
                }
            }
            Map<PetComponent.Emotion, Float> activeEmotions = context.activeEmotions();
            if (activeEmotions != null && !activeEmotions.isEmpty()) {
                float frustration = activeEmotions.getOrDefault(PetComponent.Emotion.FRUSTRATION, 0.0f);
                float restlessness = activeEmotions.getOrDefault(PetComponent.Emotion.RESTLESS, 0.0f);
                chance += MathHelper.clamp(frustration * 0.4f, 0.0f, 0.12f);
                chance += MathHelper.clamp(restlessness * 0.25f, 0.0f, 0.08f);
            }
        } else {
            chance += 0.05;
        }
        chance = MathHelper.clamp(chance, 0.05, 0.35);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            session.poorSports.add(mobId);
        }
    }

    private boolean shouldAcceptSoftHint(Session session, UUID reporterId, Vec3d position, long tick) {
        if (reporterId == null) {
            return true;
        }
        Long lastTick = session.lastSoftHintTickByReporter.get(reporterId);
        Vec3d lastPosition = session.lastSoftHintPositionByReporter.get(reporterId);
        if (lastTick == null) {
            return true;
        }
        long elapsed = tick - lastTick;
        if (elapsed >= SOFT_HINT_MIN_INTERVAL_TICKS) {
            return true;
        }
        if (lastPosition == null) {
            return true;
        }
        return lastPosition.squaredDistanceTo(position) >= SOFT_HINT_REPEAT_EPSILON_SQ;
    }

    private Vec3d smoothSoftHint(Session session, UUID reporterId, Vec3d hint, long tick) {
        if (reporterId == null) {
            return hint;
        }
        Vec3d previous = session.lastSoftHintPositionByReporter.get(reporterId);
        Long lastTick = session.lastSoftHintTickByReporter.get(reporterId);
        if (previous == null || lastTick == null) {
            return hint;
        }
        long elapsed = Math.max(0L, tick - lastTick);
        double weight = MathHelper.clamp(
            (double) elapsed / Math.max(1.0, SOFT_HINT_MIN_INTERVAL_TICKS),
            SOFT_HINT_SMOOTHING_WEIGHT_FLOOR,
            1.0
        );
        return previous.lerp(hint, weight);
    }

    private void registerHint(Session session,
                              UUID targetId,
                              Vec3d hint,
                              UUID reporterId,
                              boolean betrayal,
                              long tick) {
        if (session.closing) {
            return;
        }
        HideHint existing = session.hints.get(targetId);
        if (existing != null) {
            if (!betrayal && existing.betrayal() && existing.tick() >= tick) {
                return;
            }
            if (!betrayal && existing.tick() > tick) {
                return;
            }
            if (existing.tick() == tick
                && existing.betrayal() == betrayal
                && Objects.equals(existing.reporterId(), reporterId)
                && existing.position().squaredDistanceTo(hint) < 0.25) {
                return;
            }
        }
        session.hints.put(targetId, new HideHint(hint, reporterId, tick, betrayal));
        if (reporterId != null) {
            session.lastSoftHintTickByReporter.put(reporterId, tick);
            session.lastSoftHintPositionByReporter.put(reporterId, hint);
        }
        SessionView view = session.snapshot();
        notifySession(session, new HideHintEvent(session.id, targetId, reporterId, hint, betrayal, view));
    }

    private void pruneStaleHints(Session session, long now) {
        if (session.hints.isEmpty()) {
            return;
        }
        boolean removed = session.hints.entrySet().removeIf(entry -> now - entry.getValue().tick() > HINT_STALE_TICKS);
        if (removed) {
            notifySession(session, new SessionMetricsEvent(session.id, session.snapshot()));
        }
    }

    private double getBetrayalReadiness(Session session,
                                        UUID reporterId,
                                        UUID targetId,
                                        long tick,
                                        boolean prune) {
        Map<UUID, BetrayalMemory> motives = session.betrayalMotives.get(reporterId);
        if (motives == null) {
            return 0.0;
        }
        BetrayalMemory memory = motives.get(targetId);
        if (memory == null) {
            return 0.0;
        }
        long delta = Math.max(0L, tick - memory.lastTick);
        double decayed = decayMotivation(memory.motivation, delta);
        if (delta > BETRAYAL_WINDOW_TICKS) {
            decayed *= 0.5;
        }
        if (decayed <= 0.01 || delta > BETRAYAL_WINDOW_TICKS * 2) {
            if (prune) {
                motives.remove(targetId);
                if (motives.isEmpty()) {
                    session.betrayalMotives.remove(reporterId);
                }
            }
            return 0.0;
        }
        if (prune) {
            memory.motivation = decayed;
        }
        return decayed;
    }

    private void decayBetrayalMotives(Session session, long now) {
        if (session.betrayalMotives.isEmpty()) {
            return;
        }
        session.betrayalMotives.entrySet().removeIf(entry -> {
            Map<UUID, BetrayalMemory> motives = entry.getValue();
            motives.entrySet().removeIf(inner -> {
                BetrayalMemory memory = inner.getValue();
                long delta = Math.max(0L, now - memory.lastTick);
                double decayed = decayMotivation(memory.motivation, delta);
                if (delta > BETRAYAL_WINDOW_TICKS) {
                    decayed *= 0.5;
                }
                if (decayed <= 0.01 || delta > BETRAYAL_WINDOW_TICKS * 2) {
                    return true;
                }
                memory.motivation = decayed;
                return false;
            });
            return motives.isEmpty();
        });
    }

    private void scrubBetrayalMemory(Session session, UUID mobId) {
        session.betrayalMotives.remove(mobId);
        session.betrayalMotives.values().forEach(map -> map.remove(mobId));
    }

    private static double decayMotivation(double motivation, long delta) {
        if (motivation <= 0.0 || delta <= 0L) {
            return motivation;
        }
        double factor = Math.exp(-BETRAYAL_DECAY_PER_TICK * delta);
        return motivation * factor;
    }

    public Optional<SessionView> findClosestSession(ServerWorld world, MobEntity mob, UUID ownerId) {
        return sessions.values().stream()
            .filter(session -> Objects.equals(session.ownerId, ownerId))
            .filter(session -> Objects.equals(session.worldKey, world.getRegistryKey()))
            .sorted((a, b) -> Double.compare(distanceSquared(a.anchor, mob), distanceSquared(b.anchor, mob)))
            .findFirst()
            .map(Session::snapshot);
    }

    private static double distanceSquared(Vec3d anchor, Entity entity) {
        if (anchor == null) {
            return Double.MAX_VALUE;
        }
        return anchor.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
    }

    public Map<UUID, Role> buildDefaultRoles(SessionView view, UUID directorId) {
        List<UUID> pool = view.participants().stream()
            .filter(id -> !Objects.equals(id, directorId))
            .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        Map<UUID, Role> roles = new ConcurrentHashMap<>();
        if (!pool.isEmpty()) {
            UUID seeker = pool.remove(0);
            roles.put(seeker, Role.SEEKER);
            int assignedHiders = 0;
            for (UUID id : pool) {
                if (assignedHiders < MAX_HIDERS) {
                    roles.put(id, Role.HIDER);
                    assignedHiders++;
                } else {
                    roles.put(id, Role.SUPPORT);
                }
            }
        }
        return roles;
    }
}
