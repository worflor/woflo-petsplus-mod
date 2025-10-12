package woflo.petsplus.ai.group;

import woflo.petsplus.state.PetComponent;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GroupCoordinator {

    /**
     * Open invite coordination for short-lived, opt-in duo/group play behaviors.
     * Lists remain small because join windows are tiny (≤3s); operations are O(1)/amortized.
     */
    public static final class OpenInvite {
        public final UUID initiatorUuid;
        public final Identifier behaviorId;
        public final int maxFollowers;
        private int joinedCount;
        public final long createdTick;
        public final int joinWindowTicks;
        public final double radius;
        public final boolean allowOwnerSneakBoost;
        // Store dimension for stable world equality checks
        public final net.minecraft.registry.RegistryKey<World> worldKey; // GroupTuning: central constants

        // Follower uniqueness guarded by synchronized block on this
        private final Set<UUID> followers = new HashSet<>();

        OpenInvite(UUID initiatorUuid,
                   Identifier behaviorId,
                   int maxFollowers,
                   long createdTick,
                   int joinWindowTicks,
                   double radius,
                   boolean allowOwnerSneakBoost,
                   net.minecraft.registry.RegistryKey<World> worldKey) {
            this.initiatorUuid = initiatorUuid;
            this.behaviorId = behaviorId;
            this.maxFollowers = Math.max(0, maxFollowers);
            this.joinedCount = 0;
            this.createdTick = Math.max(0L, createdTick);
            this.joinWindowTicks = Math.max(0, joinWindowTicks);
            this.radius = Math.max(0.0, radius);
            this.allowOwnerSneakBoost = allowOwnerSneakBoost;
            this.worldKey = worldKey;
        }

        public boolean isExpired(long now) {
            return now - createdTick > joinWindowTicks;
        }

        public boolean hasCapacity() {
            synchronized (this) {
                return joinedCount < maxFollowers;
            }
        }

        public int getJoinedCount() {
            synchronized (this) {
                return joinedCount;
            }
        }

        public boolean addFollower(UUID followerUuid) {
            if (followerUuid == null) return false;
            synchronized (this) { // OpenInvite: atomic capacity check
                if (joinedCount >= maxFollowers || followers.contains(followerUuid) || followerUuid.equals(initiatorUuid)) {
                    return false;
                }
                followers.add(followerUuid);
                joinedCount++;
                return true;
            }
        }

        public boolean alreadyJoined(UUID followerUuid) {
            if (followerUuid == null) return false;
            synchronized (this) {
                return followers.contains(followerUuid);
            }
        }
    }

    // Active invites keyed by behavior id; lists are short and ephemeral
    private static final Map<Identifier, List<OpenInvite>> OPEN_INVITES = new ConcurrentHashMap<>();

    /**
     * Publish an open invite for nearby pets to optionally join within a short window.
     * Species tags: open invite join
     */
    public static void publishOpenInvite(MobEntity initiator,
                                         Identifier behaviorId,
                                         double radius,
                                         int maxFollowers,
                                         int joinWindowTicks,
                                         boolean allowOwnerSneakBoost) {
        if (initiator == null || behaviorId == null) {
            return;
        }
        // GroupTuning: central constants
        double r = radius > 0.0 ? radius : GroupTuning.GROUP_RADIUS_DEFAULT;
        int maxF = maxFollowers > 0 ? maxFollowers : GroupTuning.MAX_FOLLOWERS_DEFAULT;
        int window = joinWindowTicks > 0 ? joinWindowTicks : GroupTuning.INVITE_EXPIRY_TICKS_DEFAULT;

        World w = initiator.getEntityWorld();
        if (!(w instanceof ServerWorld sw)) {
            return;
        }
        long now = sw.getTime();

        // Prevent duplicate invites from same initiator for same behavior within active window
        List<OpenInvite> bucket = OPEN_INVITES.computeIfAbsent(behaviorId, k -> new CopyOnWriteArrayList<>());
        for (OpenInvite existing : bucket) {
            if (existing != null
                && initiator.getUuid().equals(existing.initiatorUuid)
                && java.util.Objects.equals(existing.worldKey, sw.getRegistryKey())
                && !existing.isExpired(now)) {
                return; // prevent duplicate within window
            }
        }

        OpenInvite invite = new OpenInvite(
            initiator.getUuid(),
            behaviorId,
            maxF,
            now,
            window,
            r,
            allowOwnerSneakBoost,
            sw.getRegistryKey() // store dimension for comparison
        );

        // Keep only one active from same initiator for this behavior (replace older)
        bucket.removeIf(existing -> existing != null && existing.initiatorUuid.equals(initiator.getUuid()));
        bucket.add(invite);

        // Opportunistic cleanup
        cleanupExpiredInvites(sw);
    }

    /**
     * Locate a nearby open invite for this behavior within the provided search radius.
     * Species tags: open invite join
     */
    public static Optional<OpenInvite> findNearbyOpenInvite(Identifier behaviorId,
                                                              MobEntity candidate,
                                                              double searchRadius) {
        if (behaviorId == null || candidate == null || searchRadius <= 0.0) {
            return Optional.empty();
        }
        World world = candidate.getEntityWorld();
        if (!(world instanceof ServerWorld sw)) {
            return Optional.empty();
        }
        long now = sw.getTime();
        cleanupExpiredInvites(sw);

        List<OpenInvite> bucket = OPEN_INVITES.get(behaviorId);
        if (bucket == null || bucket.isEmpty()) {
            return Optional.empty();
        }

        double searchR = Math.max(0.0, searchRadius);
        UUID selfId = candidate.getUuid();
        var candidateWorldKey = sw.getRegistryKey();

        for (OpenInvite invite : bucket) {
            if (invite == null) continue;
            if (invite.isExpired(now)) continue;
            if (!invite.hasCapacity()) continue;
            if (invite.alreadyJoined(selfId)) continue;
            if (selfId.equals(invite.initiatorUuid)) continue;

            // Ensure same dimension/world
            if (!java.util.Objects.equals(invite.worldKey, candidateWorldKey)) continue; // Owner dimension guard for sneak boost

            var initiator = sw.getEntity(invite.initiatorUuid);
            if (!(initiator instanceof MobEntity initMob)) continue;
            if (!initMob.isAlive() || initMob.isRemoved()) continue;

            double maxR = Math.min(invite.radius, searchR);
            if (maxR <= 0.0) continue;
            if (candidate.squaredDistanceTo(initMob) > (maxR * maxR)) continue; // squared distance check

            return Optional.of(invite);
        }

        return Optional.empty();
    }

    /**
     * Attempt to join the provided invite. Validates capacity, range, and window.
     * Species tags: open invite join
     */
    public static boolean tryJoinOpenInvite(MobEntity candidate, OpenInvite invite) {
        if (candidate == null || invite == null) {
            return false;
        }
        World world = candidate.getEntityWorld();
        if (!(world instanceof ServerWorld sw)) {
            return false;
        }
        // Reject if candidate is the initiator
        if (candidate.getUuid().equals(invite.initiatorUuid)) {
            return false;
        }

        long now = sw.getTime();
        if (invite.isExpired(now)) {
            return false;
        }
        // Enforce same dimension/world
        if (!java.util.Objects.equals(invite.worldKey, sw.getRegistryKey())) {
            return false;
        }

        var initiator = sw.getEntity(invite.initiatorUuid);
        if (!(initiator instanceof MobEntity initMob)) {
            return false;
        }
        if (!initMob.isAlive() || initMob.isRemoved()) {
            return false;
        }
        if (candidate.squaredDistanceTo(initMob) > (invite.radius * invite.radius)) {
            return false;
        }

        // Atomic capacity + uniqueness guarded inside addFollower
        synchronized (invite) { // OpenInvite: atomic capacity check
            if (invite.getJoinedCount() >= invite.maxFollowers) return false;
            if (invite.alreadyJoined(candidate.getUuid())) return false;
            return invite.addFollower(candidate.getUuid());
        }
    }

    /**
     * Remove expired invites or those with invalid initiators or dimension mismatch at cleanup time.
     */
    public static void cleanupExpiredInvites(World world) {
        if (world == null) return;
        long now = world.getTime();
        net.minecraft.registry.RegistryKey<World> wk = null;
        if (world instanceof ServerWorld swDim) {
            wk = swDim.getRegistryKey();
        }
        for (Map.Entry<Identifier, List<OpenInvite>> entry : OPEN_INVITES.entrySet()) {
            List<OpenInvite> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            final var dimKey = wk;
            list.removeIf(invite -> {
                if (invite == null) return true;
                if (invite.isExpired(now)) return true;
                if (dimKey != null && !java.util.Objects.equals(invite.worldKey, dimKey)) return false; // keep; we'll validate against its own world during join
                if (world instanceof ServerWorld sw) {
                    var entity = sw.getEntity(invite.initiatorUuid);
                    if (!(entity instanceof MobEntity mob) || !mob.isAlive() || mob.isRemoved()) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    public Optional<GroupContext> formOwnerGroup(List<PetComponent> components) {
        if (components == null || components.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, List<PetComponent>> byOwner = new HashMap<>();
        for (PetComponent component : components) {
            if (component == null) {
                continue;
            }
            UUID owner = component.getOwnerUuid();
            if (owner == null) {
                continue;
            }
            byOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(component);
        }
        return byOwner.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> new GroupContext(entry.getKey(), entry.getValue()))
            .findFirst();
    }
}

