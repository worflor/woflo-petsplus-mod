package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.collection.DefaultedList;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.scout.ScoutBackpack;
import woflo.petsplus.state.PetComponent;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * Effect that magnetizes item drops and experience orbs toward the owner.
 */
public class MagnetizeDropsAndXpEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "magnetize_drops_and_xp");

    private static final Map<ServerWorld, Map<UUID, MagnetizationState>> ACTIVE_MAGNETIZATIONS = new WeakHashMap<>();

    private final double radius;
    private final int durationTicks;
    
    public MagnetizeDropsAndXpEffect(double radius, int durationTicks) {
        this.radius = radius;
        this.durationTicks = durationTicks;
    }
    
    public MagnetizeDropsAndXpEffect(JsonObject config) {
        this.radius = config.has("radius") ? config.get("radius").getAsDouble() : 12.0;
        this.durationTicks = config.has("duration_ticks") ? 
            config.get("duration_ticks").getAsInt() : 
            PetsPlusConfig.getInstance().getRoleInt(PetRoleType.SCOUT.id(), "lootWispDurationTicks", 80);
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (!(context.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        // Start magnetization effect for the specified duration
        scheduleMagnetization(serverWorld, owner, context.getPet(), radius, durationTicks);
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
    
    private void scheduleMagnetization(ServerWorld world, PlayerEntity owner, @Nullable MobEntity pet,
                                       double radius, int duration) {
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.computeIfAbsent(world, w -> new HashMap<>());
        UUID ownerId = owner.getUuid();
        long expiryTick = world.getTime() + duration;
        Vec3d anchor = owner.getPos();
        PetComponent component = pet != null ? PetComponent.get(pet) : null;
        ScoutBackpack.RoutingMode routingMode = ScoutBackpack.RoutingMode.PLAYER;
        boolean routeToBackpack = false;
        UUID petUuid = null;
        if (owner instanceof ServerPlayerEntity && pet != null && component != null && component.isOwnedBy(owner)) {
            routingMode = ScoutBackpack.getRoutingMode(component);
            routeToBackpack = routingMode == ScoutBackpack.RoutingMode.PET
                && ScoutBackpack.canRouteLootToBackpack(component, pet);
            if (routeToBackpack) {
                petUuid = pet.getUuid();
            }
        }

        final ScoutBackpack.RoutingMode finalMode = routingMode;
        final boolean finalRouteToBackpack = routeToBackpack;
        final UUID finalPetUuid = petUuid;

        worldStates.compute(ownerId, (uuid, existing) -> {
            if (existing == null) {
                return new MagnetizationState(anchor, radius, expiryTick, finalPetUuid, finalRouteToBackpack, finalMode);
            }
            existing.refresh(anchor, radius, expiryTick, finalPetUuid, finalRouteToBackpack, finalMode);
            return existing;
        });

        if (owner instanceof ServerPlayerEntity serverOwner) {
            magnetizeNearbyItems(world, serverOwner, worldStates.get(ownerId));
        }
    }

    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.get(world);
        if (worldStates == null || worldStates.isEmpty()) {
            return;
        }

        MagnetizationState state = worldStates.get(player.getUuid());
        if (state == null) {
            return;
        }

        long currentTick = world.getTime();
        if (currentTick >= state.getExpirationTick()
            || player.getPos().squaredDistanceTo(state.getAnchor()) > state.getRadius() * state.getRadius()
            || !player.isAlive()) {
            worldStates.remove(player.getUuid());
            if (worldStates.isEmpty()) {
                ACTIVE_MAGNETIZATIONS.remove(world);
            }
            return;
        }

        magnetizeNearbyItems(world, player, state);
    }

    public static void handleMobTick(MobEntity mob, ServerWorld world) {
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.get(world);
        if (worldStates == null || worldStates.isEmpty()) {
            return;
        }

        UUID mobId = mob.getUuid();
        worldStates.values().removeIf(state -> state.routesToPet(mobId)
            && (!mob.isAlive() || world.getTime() >= state.getExpirationTick()));
        if (worldStates.isEmpty()) {
            ACTIVE_MAGNETIZATIONS.remove(world);
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.get(world);
        if (worldStates != null) {
            worldStates.remove(player.getUuid());
            if (worldStates.isEmpty()) {
                ACTIVE_MAGNETIZATIONS.remove(world);
            }
        }
    }

    private static void magnetizeNearbyItems(ServerWorld world, ServerPlayerEntity owner, MagnetizationState state) {
        Vec3d center = state.getAnchor();
        double radius = state.getRadius();
        Box searchBox = Box.of(center, radius * 2, radius * 2, radius * 2);
        double radiusSquared = radius * radius;

        if (state.getMode() != ScoutBackpack.RoutingMode.OFF) {
            // Magnetize item entities
            List<ItemEntity> items = world.getEntitiesByClass(
                ItemEntity.class,
                searchBox,
                item -> item.squaredDistanceTo(center) <= radiusSquared && !item.cannotPickup()
            );

            for (ItemEntity item : items) {
                if (state.tryHandleItem(world, owner, item)) {
                    continue;
                }
                magnetizeToPlayer(item, owner);
            }
        }

        // Magnetize experience orbs
        List<ExperienceOrbEntity> orbs = world.getEntitiesByClass(
            ExperienceOrbEntity.class,
            searchBox,
            orb -> orb.squaredDistanceTo(center) <= radiusSquared
        );

        for (ExperienceOrbEntity orb : orbs) {
            magnetizeToPlayer(orb, owner);
        }
    }

    private static void magnetizeToPlayer(ItemEntity item, PlayerEntity player) {
        Vec3d toPlayer = player.getPos().subtract(item.getPos());
        if (toPlayer.lengthSquared() == 0) {
            return;
        }
        Vec3d velocity = toPlayer.normalize().multiply(0.1); // Gentle pull
        item.setVelocity(velocity);
    }

    private static void magnetizeToEntity(ItemEntity item, MobEntity entity) {
        Vec3d target = entity.getPos().add(0.0, entity.getStandingEyeHeight() * 0.3, 0.0);
        Vec3d toEntity = target.subtract(item.getPos());
        if (toEntity.lengthSquared() == 0) {
            return;
        }
        Vec3d velocity = toEntity.normalize().multiply(0.1);
        item.setVelocity(velocity);
    }

    private static void magnetizeToPlayer(ExperienceOrbEntity orb, PlayerEntity player) {
        Vec3d toPlayer = player.getPos().subtract(orb.getPos());
        if (toPlayer.lengthSquared() == 0) {
            return;
        }
        Vec3d velocity = toPlayer.normalize().multiply(0.15); // Slightly faster pull for XP
        orb.setVelocity(velocity);
    }

    private static class MagnetizationState {
        private Vec3d anchor;
        private double radius;
        private long expirationTick;
        private @Nullable UUID petUuid;
        private boolean routeToBackpack;
        private ScoutBackpack.RoutingMode mode;

        private MagnetizationState(Vec3d anchor, double radius, long expirationTick, @Nullable UUID petUuid,
                                   boolean routeToBackpack, ScoutBackpack.RoutingMode mode) {
            this.anchor = anchor;
            this.radius = radius;
            this.expirationTick = expirationTick;
            this.petUuid = petUuid;
            this.routeToBackpack = routeToBackpack;
            this.mode = mode;
        }

        private Vec3d getAnchor() {
            return anchor;
        }

        private double getRadius() {
            return radius;
        }

        private long getExpirationTick() {
            return expirationTick;
        }

        private void refresh(Vec3d newAnchor, double newRadius, long newExpiryTick, @Nullable UUID petUuid,
                              boolean routeToBackpack, ScoutBackpack.RoutingMode mode) {
            this.anchor = newAnchor;
            this.radius = Math.max(this.radius, newRadius);
            this.expirationTick = Math.max(this.expirationTick, newExpiryTick);
            this.petUuid = petUuid;
            this.routeToBackpack = routeToBackpack;
            this.mode = mode;
        }

        private ScoutBackpack.RoutingMode getMode() {
            return mode;
        }

        private boolean routesToPet(UUID uuid) {
            return petUuid != null && petUuid.equals(uuid);
        }

        private boolean tryHandleItem(ServerWorld world, ServerPlayerEntity owner, ItemEntity item) {
            if (mode != ScoutBackpack.RoutingMode.PET || !routeToBackpack || petUuid == null) {
                return false;
            }
            var entity = world.getEntity(petUuid);
            if (!(entity instanceof MobEntity pet) || !pet.isAlive()) {
                routeToBackpack = false;
                petUuid = null;
                return false;
            }
            PetComponent component = PetComponent.get(pet);
            if (component == null || !component.isOwnedBy(owner) || !ScoutBackpack.canRouteLootToBackpack(component, pet)) {
                return false;
            }

            int storageSlots = ScoutBackpack.computeStorageSlots(component, pet);
            if (storageSlots <= 0) {
                return false;
            }

            if (!ScoutBackpack.isWithinPickupRange(pet, item)) {
                magnetizeToEntity(item, pet);
                return true;
            }

            DefaultedList<ItemStack> previousBacking = component.getInventoryIfPresent(ScoutBackpack.INVENTORY_KEY);
            if (previousBacking != null) {
                ScoutBackpack.handleOverflow(previousBacking, storageSlots, owner, pet);
            }

            DefaultedList<ItemStack> backing = component.getInventory(ScoutBackpack.INVENTORY_KEY, storageSlots);
            if (!ScoutBackpack.canFullyInsert(backing, item.getStack())) {
                return false;
            }

            return ScoutBackpack.tryStoreItem(world, owner, pet, component, backing, item);
        }
    }
}
