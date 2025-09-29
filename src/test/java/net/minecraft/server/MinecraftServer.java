package net.minecraft.server;

import java.util.concurrent.CompletableFuture;

/** Minimal server stub providing submit support. */
public class MinecraftServer {
    private int ticks;

    public CompletableFuture<?> submit(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
        return CompletableFuture.completedFuture(null);
    }

    public int getTicks() {
        return ticks;
    }

    public void setTicks(int ticks) {
        this.ticks = ticks;
    }
}
