package net.minecraft.server.world;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Minimal ServerWorld stub for unit tests. */
public class ServerWorld extends World {
    private final RegistryKey<World> key = RegistryKey.of(Identifier.of("petsplus", "test_world"));

    public ServerWorld(MinecraftServer server) {
        super(server);
    }

    public RegistryKey<World> getRegistryKey() {
        return key;
    }

    public <T extends Entity> java.util.List<T> getEntitiesByClass(Class<T> type, Box box, java.util.function.Predicate<T> predicate) {
        return java.util.Collections.emptyList();
    }
}
