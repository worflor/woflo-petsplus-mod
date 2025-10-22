package woflo.petsplus.interaction;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.event.OwnerAbilitySignalEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Detects owner initiated trigger signals (double crouch and proximity channel)
 * and surfaces them via {@link OwnerAbilitySignalEvent}.
 */
public final class OwnerAbilitySignalTracker implements PlayerTickListener {
    private static final long DOUBLE_CROUCH_WINDOW_TICKS = 12;
    private static final double DOUBLE_CROUCH_MAX_DISTANCE = 16.0;
    private static final double DOUBLE_CROUCH_ALIGNMENT_THRESHOLD = 0.97;
    private static final double DOUBLE_CROUCH_ALIGNMENT_THRESHOLD_SQ = DOUBLE_CROUCH_ALIGNMENT_THRESHOLD * DOUBLE_CROUCH_ALIGNMENT_THRESHOLD;
    private static final double PROXIMITY_RANGE = 3.0;
    private static final double PROXIMITY_RANGE_SQ = PROXIMITY_RANGE * PROXIMITY_RANGE;
    private static final int PROXIMITY_DURATION_TICKS = 30;
    // Legacy approach detection tuning constants (no longer used here) removed.
    private static final int POST_CUDDLE_PET_WINDOW_TICKS = 10; // ~0.5s window to pet after cuddle completes

    private static final Map<ServerPlayerEntity, SneakState> SNEAK_STATES = new WeakHashMap<>();
    // Legacy maps removed; single ACTIVE_CHANNELS structure is used.
    private static final Map<ServerWorld, Map<MobEntity, ProximityChannel>> ACTIVE_CHANNELS = new WeakHashMap<>();
    private static final Map<ServerWorld, Map<MobEntity, PendingProximity>> PENDING_PROXIMITY = new WeakHashMap<>();

    private static final OwnerAbilitySignalTracker INSTANCE = new OwnerAbilitySignalTracker();

    private OwnerAbilitySignalTracker() {
    }

    public static OwnerAbilitySignalTracker getInstance() {
        return INSTANCE;
    }

    // No explicit registration method; PlayerTickListeners registers the singleton.

    /**
     * Invoked from mixin when a player's sneaking state changes.
     */
    public static void handleSneakToggle(ServerPlayerEntity player, boolean sneaking) {
        if (player.isSpectator() || player.isRemoved() || !player.isAlive()) {
            return;
        }

        SneakState state = SNEAK_STATES.computeIfAbsent(player, ignored -> new SneakState());
        if (state.sneaking == sneaking) {
            return;
        }

        state.sneaking = sneaking;
        long now = player.getEntityWorld().getTime();
        long serverTick = player.getEntityWorld().getServer() != null ? player.getEntityWorld().getServer().getTicks() : now;

        if (sneaking) {
            boolean triggeredDouble = false;

            // Double crouch detection (press following a recent release)
            if (state.lastReleaseTick >= 0
                && now - state.lastReleaseTick <= DOUBLE_CROUCH_WINDOW_TICKS
                && state.lastPressTick >= 0
                && state.lastReleaseTick - state.lastPressTick <= DOUBLE_CROUCH_WINDOW_TICKS) {
                triggeredDouble = handleDoubleCrouch(player);
            }

            state.lastPressTick = now;

            if (!triggeredDouble) {
                startProximityChannels(player, state, serverTick);
            }
        } else {
            state.lastReleaseTick = now;
            cancelProximityChannels(player);
            scheduleIdle(state);
        }
    }

    private static boolean handleDoubleCrouch(ServerPlayerEntity player) {
        MobEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }

        OwnerAbilitySignalEvent.fire(OwnerAbilitySignalEvent.Type.DOUBLE_CROUCH, player, target);
        return true;
    }

    private static MobEntity findLookTarget(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }

        StateManager stateManager = StateManager.forWorld(world);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(player.getUuid());
        if (swarm.isEmpty()) {
            return null;
        }

        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d lookVec = player.getRotationVec(1.0f);
        double maxDistanceSq = DOUBLE_CROUCH_MAX_DISTANCE * DOUBLE_CROUCH_MAX_DISTANCE;
        MobEntity closest = null;

        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity mob = entry.pet();
            if (mob == null || !mob.isAlive() || mob.isRemoved() || mob.getEntityWorld() != world) {
                continue;
            }

            double distanceSq = player.squaredDistanceTo(mob);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            Vec3d toEntity = mob.getBoundingBox().getCenter().subtract(eyePos);
            double lengthSq = toEntity.lengthSquared();
            if (lengthSq < 1.0e-4) {
                continue;
            }

            double dot = toEntity.dotProduct(lookVec);
            if (dot <= 0.0) {
                continue;
            }

            double alignmentSq = dot * dot;
            if (alignmentSq < DOUBLE_CROUCH_ALIGNMENT_THRESHOLD_SQ * lengthSq) {
                continue;
            }

            if (!player.canSee(mob)) {
                continue;
            }

            closest = mob;
            maxDistanceSq = distanceSq;
        }

        return closest;
    }

    private static void startProximityChannels(ServerPlayerEntity player, SneakState state, long serverTick) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        StateManager stateManager = StateManager.forWorld(world);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(player.getUuid());
        if (swarm.isEmpty()) {
            return;
        }

        double expandedRangeSq = PROXIMITY_RANGE_SQ;
        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.computeIfAbsent(world, w -> new WeakHashMap<>());
        long completionTick = world.getTime() + PROXIMITY_DURATION_TICKS;
        UUID ownerId = player.getUuid();

        boolean hasChannel = false;
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            if (pet == null || pet.isRemoved() || !pet.isAlive() || pet.getEntityWorld() != world) {
                continue;
            }

            double distanceSq = player.squaredDistanceTo(pet);
            if (distanceSq > expandedRangeSq) {
                continue;
            }

            PetComponent component = entry.component();
            if (component == null || !component.isOwnedBy(player) || component.isPerched()) {
                continue;
            }

            ProximityChannel existing = worldChannels.get(pet);
            if (existing != null && existing.ownerId.equals(ownerId)) {
                existing.refresh(completionTick);
                component.refreshCrouchCuddle(ownerId, completionTick);
                hasChannel = true;
                continue;
            }

            worldChannels.put(pet, new ProximityChannel(ownerId, completionTick));
            component.beginCrouchCuddle(ownerId, completionTick);
            hasChannel = true;
        }

        if (hasChannel) {
            requestRun(player, state, serverTick);
        }
    }

    private static void cancelProximityChannels(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null || worldChannels.isEmpty()) {
            return;
        }

        UUID ownerId = player.getUuid();
        Iterator<Map.Entry<MobEntity, ProximityChannel>> iterator = worldChannels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MobEntity, ProximityChannel> entry = iterator.next();
            if (entry.getValue().ownerId.equals(ownerId)) {
                entry.getValue().clearCrouchApproach();
                iterator.remove();
                PetComponent component = PetComponent.get(entry.getKey());
                if (component != null) {
                    component.endCrouchCuddle(ownerId);
                }
            }
        }

        if (worldChannels.isEmpty()) {
            ACTIVE_CHANNELS.remove(world);
        }
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        SneakState state = SNEAK_STATES.get(player);
        if (state == null) {
            return Long.MAX_VALUE;
        }
        return state.nextTick;
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.getEntityWorld().isClient()) {
            return;
        }

        SneakState state = SNEAK_STATES.get(player);
        if (state == null) {
            return;
        }

        state.nextTick = Long.MAX_VALUE;

        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            cancelProximityChannels(player);
            return;
        }

        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null || worldChannels.isEmpty()) {
            scheduleIdle(state);
            return;
        }

        if (player.isSneaking()) {
            OwnerApproachDetector.trackPlayerMovement(player, currentTick);
        }

        long now = world.getTime();
        UUID ownerId = player.getUuid();
        Iterator<Map.Entry<MobEntity, ProximityChannel>> iterator = worldChannels.entrySet().iterator();
        boolean hasActive = false;

        while (iterator.hasNext()) {
            Map.Entry<MobEntity, ProximityChannel> entry = iterator.next();
            MobEntity pet = entry.getKey();
            ProximityChannel channel = entry.getValue();

            if (!channel.ownerId.equals(ownerId)) {
                continue;
            }

            if (pet == null || !pet.isAlive() || pet.isRemoved()) {
                PetComponent component = pet != null ? PetComponent.get(pet) : null;
                if (component != null) {
                    component.endCrouchCuddle(channel.ownerId);
                }
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            PetComponent component = PetComponent.get(pet);
            if (component == null || component.isPerched()) {
                if (component != null) {
                    component.endCrouchCuddle(channel.ownerId);
                }
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            if (player.isRemoved() || player.isSpectator() || player.getEntityWorld() != world) {
                component.endCrouchCuddle(channel.ownerId);
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            if (!player.isSneaking()) {
                component.endCrouchCuddle(channel.ownerId);
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            if (!component.isOwnedBy(player)) {
                component.endCrouchCuddle(channel.ownerId);
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            if (!component.isCrouchCuddleActiveWith(player, now)) {
                component.endCrouchCuddle(channel.ownerId);
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            if (player.squaredDistanceTo(pet) > PROXIMITY_RANGE_SQ) {
                component.endCrouchCuddle(channel.ownerId);
                channel.clearCrouchApproach();
                iterator.remove();
                continue;
            }

            hasActive = true;

            if (!channel.hasEmittedCrouchApproach()
                && player.isSneaking()
                && OwnerApproachDetector.isApproachingPet(player, pet)
                && isLookingAtPet(player, pet)) {
                OwnerAbilitySignalEvent.fire(OwnerAbilitySignalEvent.Type.CROUCH_APPROACH, player, pet);
                channel.markCrouchApproachEmitted();
            }

            if (now >= channel.completionTick) {
                channel.clearCrouchApproach();
                iterator.remove();
                component.endCrouchCuddle(channel.ownerId);
                markPendingProximity(world, pet, channel.ownerId, now + POST_CUDDLE_PET_WINDOW_TICKS);
                continue;
            }
        }

        if (worldChannels.isEmpty()) {
            ACTIVE_CHANNELS.remove(world);
        }

        if (hasActive) {
            state.nextTick = currentTick + 1L;
        } else {
            scheduleIdle(state);
        }
    }

    private static void markPendingProximity(ServerWorld world, MobEntity pet, UUID ownerId, long expiryTick) {
        if (world == null || pet == null || ownerId == null) {
            return;
        }
        Map<MobEntity, PendingProximity> worldPending = PENDING_PROXIMITY.computeIfAbsent(world, w -> new WeakHashMap<>());
        worldPending.put(pet, new PendingProximity(ownerId, expiryTick));
    }

    /**
     * Called from PettingHandler after a petting interaction. If there is a pending
     * proximity completion for this pet-owner pair, consumes it and fires the ability signal.
     */
    public static void handlePostCuddlePetting(ServerPlayerEntity owner, MobEntity pet) {
        if (owner == null || pet == null) {
            return;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        Map<MobEntity, PendingProximity> worldPending = PENDING_PROXIMITY.get(world);
        if (worldPending == null) {
            return;
        }
        PendingProximity pending = worldPending.get(pet);
        if (pending == null) {
            return;
        }
        long now = world.getTime();
        if (!owner.getUuid().equals(pending.ownerId) || now > pending.expiryTick) {
            // Not the same owner or expired; clear and skip
            if (now > pending.expiryTick) {
                worldPending.remove(pet);
                if (worldPending.isEmpty()) {
                    PENDING_PROXIMITY.remove(world);
                }
            }
            return;
        }

        // Consume and fire
        worldPending.remove(pet);
        if (worldPending.isEmpty()) {
            PENDING_PROXIMITY.remove(world);
        }
        OwnerAbilitySignalEvent.fire(OwnerAbilitySignalEvent.Type.PROXIMITY_CHANNEL, owner, pet);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        SneakState state = SNEAK_STATES.remove(player);
        if (state != null) {
            state.nextTick = Long.MAX_VALUE;
        }
        cancelProximityChannels(player);
        OwnerApproachDetector.clearPlayer(player.getUuid());
    }

    public static void handlePetRemoved(MobEntity pet) {
        if (pet == null) {
            return;
        }

        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null) {
            return;
        }

        ProximityChannel removed = worldChannels.remove(pet);
        if (removed != null) {
            removed.clearCrouchApproach();
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                component.endCrouchCuddle(removed.ownerId);
            }
        }

        if (worldChannels.isEmpty()) {
            ACTIVE_CHANNELS.remove(world);
        }
    }

    private static void requestRun(ServerPlayerEntity player, SneakState state, long tick) {
        if (state == null) {
            return;
        }

        state.nextTick = tick;
        if (player != null && player.getEntityWorld().getServer() != null) {
            if (tick <= player.getEntityWorld().getServer().getTicks()) {
                PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
            }
        }
    }

    private static void scheduleIdle(SneakState state) {
        if (state == null) {
            return;
        }

        long idleTick = Long.MAX_VALUE;
        state.nextTick = idleTick;
    }

    private static boolean isLookingAtPet(ServerPlayerEntity player, MobEntity pet) {
        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d toEntity = pet.getBoundingBox().getCenter().subtract(eyePos);
        double lengthSq = toEntity.lengthSquared();
        if (lengthSq < 1.0e-4) {
            return true;
        }

        double dot = toEntity.dotProduct(lookVec);
        if (dot <= 0.0) {
            return false;
        }

        double alignmentSq = dot * dot;
        if (alignmentSq < DOUBLE_CROUCH_ALIGNMENT_THRESHOLD_SQ * lengthSq) {
            return false;
        }

        return player.canSee(pet);
    }

    private static final class SneakState {
        private boolean sneaking;
        private long lastPressTick = -1;
        private long lastReleaseTick = -1;
        private long nextTick = Long.MAX_VALUE;
    }

    private static final class ProximityChannel {
        private final UUID ownerId;
        private long completionTick;
        private boolean crouchApproachEmitted;

        private ProximityChannel(UUID ownerId, long completionTick) {
            this.ownerId = ownerId;
            this.completionTick = completionTick;
        }

        private void refresh(long newCompletionTick) {
            this.completionTick = Math.max(this.completionTick, newCompletionTick);
        }

        private boolean hasEmittedCrouchApproach() {
            return crouchApproachEmitted;
        }

        private void markCrouchApproachEmitted() {
            this.crouchApproachEmitted = true;
        }

        private void clearCrouchApproach() {
            this.crouchApproachEmitted = false;
        }
    }

    private static final class PendingProximity {
        private final UUID ownerId;
        private final long expiryTick;

        private PendingProximity(UUID ownerId, long expiryTick) {
            this.ownerId = ownerId;
            this.expiryTick = expiryTick;
        }
    }
}




