package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Effect that magnetizes item drops and experience orbs toward the owner.
 */
public class MagnetizeDropsAndXpEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "magnetize_drops_and_xp");

    private static final Map<ServerWorld, Map<UUID, MagnetizationState>> ACTIVE_MAGNETIZATIONS = new WeakHashMap<>();

    static {
        ServerTickEvents.END_WORLD_TICK.register(MagnetizeDropsAndXpEffect::tickWorld);
    }

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
        scheduleMagnetization(serverWorld, owner, radius, durationTicks);
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
    
    private void scheduleMagnetization(ServerWorld world, PlayerEntity owner, double radius, int duration) {
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.computeIfAbsent(world, w -> new HashMap<>());
        UUID ownerId = owner.getUuid();
        long expiryTick = world.getTime() + duration;
        Vec3d anchor = owner.getPos();

        worldStates.compute(ownerId, (uuid, existing) -> {
            if (existing == null) {
                return new MagnetizationState(anchor, radius, expiryTick);
            }
            existing.refresh(anchor, radius, expiryTick);
            return existing;
        });

        magnetizeNearbyItems(world, owner, anchor, radius);
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, MagnetizationState> worldStates = ACTIVE_MAGNETIZATIONS.get(world);
        if (worldStates == null || worldStates.isEmpty()) {
            return;
        }

        long currentTick = world.getTime();
        Iterator<Map.Entry<UUID, MagnetizationState>> iterator = worldStates.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, MagnetizationState> entry = iterator.next();
            MagnetizationState state = entry.getValue();

            if (currentTick >= state.getExpirationTick()) {
                iterator.remove();
                continue;
            }

            PlayerEntity owner = world.getPlayerByUuid(entry.getKey());
            if (owner == null || !owner.isAlive()) {
                iterator.remove();
                continue;
            }

            if (owner.getPos().squaredDistanceTo(state.getAnchor()) > state.getRadius() * state.getRadius()) {
                iterator.remove();
                continue;
            }

            magnetizeNearbyItems(world, owner, state.getAnchor(), state.getRadius());
        }

        if (worldStates.isEmpty()) {
            ACTIVE_MAGNETIZATIONS.remove(world);
        }
    }

    private static void magnetizeNearbyItems(ServerWorld world, PlayerEntity owner, Vec3d center, double radius) {
        Box searchBox = Box.of(center, radius * 2, radius * 2, radius * 2);
        double radiusSquared = radius * radius;

        // Magnetize item entities
        List<ItemEntity> items = world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            item -> item.squaredDistanceTo(center) <= radiusSquared && !item.cannotPickup()
        );

        for (ItemEntity item : items) {
            magnetizeToPlayer(item, owner);
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

        private MagnetizationState(Vec3d anchor, double radius, long expirationTick) {
            this.anchor = anchor;
            this.radius = radius;
            this.expirationTick = expirationTick;
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

        private void refresh(Vec3d newAnchor, double newRadius, long newExpiryTick) {
            this.anchor = newAnchor;
            this.radius = Math.max(this.radius, newRadius);
            this.expirationTick = Math.max(this.expirationTick, newExpiryTick);
        }
    }
}
