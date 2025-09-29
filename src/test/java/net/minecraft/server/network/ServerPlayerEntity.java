package net.minecraft.server.network;

import java.util.UUID;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Minimal server player stub for tests. */
public class ServerPlayerEntity extends PlayerEntity {
    private final UUID uuid = UUID.randomUUID();

    public ServerPlayerEntity(ServerWorld world) {
        super(world);
    }

    @Override
    public ServerWorld getWorld() {
        return (ServerWorld) super.getWorld();
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public MinecraftServer getServer() {
        return getWorld().getServer();
    }
}
