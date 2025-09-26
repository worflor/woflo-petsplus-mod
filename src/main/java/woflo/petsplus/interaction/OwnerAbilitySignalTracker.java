package woflo.petsplus.interaction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.event.OwnerAbilitySignalEvent;
import woflo.petsplus.state.PetComponent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Detects owner initiated trigger signals (double crouch and proximity channel)
 * and surfaces them via {@link OwnerAbilitySignalEvent}.
 */
public final class OwnerAbilitySignalTracker {
    private static final long DOUBLE_CROUCH_WINDOW_TICKS = 12;
    private static final double DOUBLE_CROUCH_MAX_DISTANCE = 16.0;
    private static final double DOUBLE_CROUCH_ALIGNMENT_THRESHOLD = 0.97;
    private static final double PROXIMITY_RANGE = 3.0;
    private static final double PROXIMITY_RANGE_SQ = PROXIMITY_RANGE * PROXIMITY_RANGE;
    private static final int PROXIMITY_DURATION_TICKS = 30;

    private static final Map<ServerPlayerEntity, SneakState> SNEAK_STATES = new WeakHashMap<>();
    private static final Map<ServerWorld, Map<MobEntity, ProximityChannel>> ACTIVE_CHANNELS = new WeakHashMap<>();

    private OwnerAbilitySignalTracker() {
    }

    /**
     * Registers tracker state. All runtime callbacks are driven by mixins.
     */
    public static void register() {
        Petsplus.LOGGER.info("Owner ability signal tracker registered");
    }

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
        long now = player.getWorld().getTime();

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
                startProximityChannels(player);
            }
        } else {
            state.lastReleaseTick = now;
            cancelProximityChannels(player);
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
        World world = player.getWorld();
        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Box searchBox = player.getBoundingBox().stretch(lookVec.multiply(DOUBLE_CROUCH_MAX_DISTANCE)).expand(1.0);
        double closestDistanceSq = DOUBLE_CROUCH_MAX_DISTANCE * DOUBLE_CROUCH_MAX_DISTANCE;
        MobEntity closest = null;

        for (Entity entity : world.getOtherEntities(player, searchBox, e -> isOwnedPet(player, e))) {
            if (!(entity instanceof MobEntity mob)) {
                continue;
            }

            double distanceSq = player.squaredDistanceTo(mob);
            if (distanceSq > closestDistanceSq) {
                continue;
            }

            Vec3d toEntity = mob.getBoundingBox().getCenter().subtract(eyePos);
            double lengthSq = toEntity.lengthSquared();
            if (lengthSq < 1.0e-4) {
                continue;
            }

            double alignment = toEntity.normalize().dotProduct(lookVec);
            if (alignment < DOUBLE_CROUCH_ALIGNMENT_THRESHOLD) {
                continue;
            }

            if (!player.canSee(mob)) {
                continue;
            }

            closest = mob;
            closestDistanceSq = distanceSq;
        }

        return closest;
    }

    private static boolean isOwnedPet(ServerPlayerEntity owner, Entity entity) {
        if (!(entity instanceof MobEntity mob) || entity.isRemoved() || !entity.isAlive()) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null || !component.isOwnedBy(owner)) {
            return false;
        }
        return true;
    }

    private static void startProximityChannels(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Box search = player.getBoundingBox().expand(PROXIMITY_RANGE, PROXIMITY_RANGE / 2.0, PROXIMITY_RANGE);
        java.util.List<MobEntity> nearbyPets = world.getEntitiesByClass(
            MobEntity.class,
            search,
            entity -> isOwnedPet(player, entity)
        );

        if (nearbyPets.isEmpty()) {
            return;
        }

        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.computeIfAbsent(world, w -> new WeakHashMap<>());
        long completionTick = world.getTime() + PROXIMITY_DURATION_TICKS;
        UUID ownerId = player.getUuid();

        for (MobEntity pet : nearbyPets) {
            PetComponent component = PetComponent.get(pet);
            if (component == null || component.isPerched()) {
                continue;
            }

            ProximityChannel existing = worldChannels.get(pet);
            if (existing != null && existing.ownerId.equals(ownerId)) {
                existing.refresh(completionTick);
                component.refreshCrouchCuddle(ownerId, completionTick);
                continue;
            }

            worldChannels.put(pet, new ProximityChannel(ownerId, completionTick));
            component.beginCrouchCuddle(ownerId, completionTick);
        }
    }

    private static void cancelProximityChannels(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null || worldChannels.isEmpty()) {
            return;
        }

        UUID ownerId = player.getUuid();
        Iterator<Map.Entry<MobEntity, ProximityChannel>> iterator = worldChannels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MobEntity, ProximityChannel> entry = iterator.next();
            if (entry.getValue().ownerId.equals(ownerId)) {
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

    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null || worldChannels.isEmpty()) {
            return;
        }

        long now = world.getTime();
        UUID ownerId = player.getUuid();
        Iterator<Map.Entry<MobEntity, ProximityChannel>> iterator = worldChannels.entrySet().iterator();
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
                iterator.remove();
                continue;
            }

            PetComponent component = PetComponent.get(pet);
            if (component == null || component.isPerched()) {
                if (component != null) {
                    component.endCrouchCuddle(channel.ownerId);
                }
                iterator.remove();
                continue;
            }

            if (player.isRemoved() || player.isSpectator() || player.getWorld() != world) {
                component.endCrouchCuddle(channel.ownerId);
                iterator.remove();
                continue;
            }

            if (!player.isSneaking()) {
                component.endCrouchCuddle(channel.ownerId);
                iterator.remove();
                continue;
            }

            if (!component.isOwnedBy(player)) {
                component.endCrouchCuddle(channel.ownerId);
                iterator.remove();
                continue;
            }

            if (!component.isCrouchCuddleActiveWith(player, now)) {
                component.endCrouchCuddle(channel.ownerId);
                iterator.remove();
                continue;
            }

            if (player.squaredDistanceTo(pet) > PROXIMITY_RANGE_SQ) {
                component.endCrouchCuddle(channel.ownerId);
                iterator.remove();
                continue;
            }

            if (now >= channel.completionTick) {
                iterator.remove();
                component.endCrouchCuddle(channel.ownerId);
                OwnerAbilitySignalEvent.fire(OwnerAbilitySignalEvent.Type.PROXIMITY_CHANNEL, player, pet);
            }
        }

        if (worldChannels.isEmpty()) {
            ACTIVE_CHANNELS.remove(world);
        }
    }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        SNEAK_STATES.remove(player);
        cancelProximityChannels(player);
    }

    public static void handlePetRemoved(MobEntity pet) {
        if (pet == null) {
            return;
        }

        if (!(pet.getWorld() instanceof ServerWorld world)) {
            return;
        }

        Map<MobEntity, ProximityChannel> worldChannels = ACTIVE_CHANNELS.get(world);
        if (worldChannels == null) {
            return;
        }

        ProximityChannel removed = worldChannels.remove(pet);
        if (removed != null) {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                component.endCrouchCuddle(removed.ownerId);
            }
        }

        if (worldChannels.isEmpty()) {
            ACTIVE_CHANNELS.remove(world);
        }
    }

    private static final class SneakState {
        private boolean sneaking;
        private long lastPressTick = -1;
        private long lastReleaseTick = -1;
    }

    private static final class ProximityChannel {
        private final UUID ownerId;
        private long completionTick;

        private ProximityChannel(UUID ownerId, long completionTick) {
            this.ownerId = ownerId;
            this.completionTick = completionTick;
        }

        private void refresh(long newCompletionTick) {
            this.completionTick = Math.max(this.completionTick, newCompletionTick);
        }
    }
}
