package net.minecraft.world;

import net.minecraft.server.MinecraftServer;

/** Minimal world stub for server tests. */
public class World {
    protected final MinecraftServer server;
    private long time;

    public World(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isClient() {
        return false;
    }
}
