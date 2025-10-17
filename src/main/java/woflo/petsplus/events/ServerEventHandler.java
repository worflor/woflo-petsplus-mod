package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListeners;
import woflo.petsplus.ui.ActionBarCueManager;
import woflo.petsplus.ui.BossBarManager;
import woflo.petsplus.ui.PetInspectionManager;

/**
 * Handles server-wide events and ticking for PetsPlus systems.
 */
public class ServerEventHandler {
    
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerEventHandler::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerEventHandler::onServerStopping);
        ServerWorldEvents.LOAD.register(ServerEventHandler::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(ServerEventHandler::onWorldUnload);
        ServerPlayConnectionEvents.JOIN.register(ServerEventHandler::onPlayerJoin);
    }
    
    private static void onServerStarting(MinecraftServer server) {
        Petsplus.LOGGER.info("PetsPlus: Server starting - initializing state managers");
        StateManager.onServerStarting();
        // State managers will be initialized lazily when worlds are accessed
        PlayerTickListeners.registerAll();
    }

    private static void onServerStopping(MinecraftServer server) {
        Petsplus.LOGGER.info("PetsPlus: Server stopping - persisting all pet data");
        StateManager.beginServerStopping();

        // Ensure all pet data is saved before shutdown
        for (ServerWorld world : server.getWorlds()) {
            StateManager stateManager = StateManager.getIfLoaded(world);
            if (stateManager != null) {
                stateManager.onWorldSave();
            }
        }
        
        // Clean up all background tasks and resources to prevent watchdog timeouts
        woflo.petsplus.ui.FeedbackManager.cleanup();
        woflo.petsplus.effects.PetsplusEffectManager.shutdown();
        woflo.petsplus.ui.BossBarManager.shutdown();
        woflo.petsplus.ui.PetInspectionManager.shutdown();
        ActionBarCueManager.shutdown();
        woflo.petsplus.ui.CooldownParticleManager.shutdown();
        woflo.petsplus.util.EntityTagUtil.shutdown();
        
        // Cancel pending idle emotion tasks and shutdown executor
        woflo.petsplus.mood.MoodService.getInstance().getStimulusBus().cancelPendingIdleTasks();
        woflo.petsplus.mood.EmotionStimulusBus.shutdownIdleExecutor();

        // Ensure all dispatcher-managed listeners release their per-player state.
        server.getPlayerManager().getPlayerList().forEach(PlayerTickDispatcher::clearPlayer);
        PlayerTickDispatcher.clearAll();
        
        // Properly shutdown all state managers to close async coordinators
        StateManager.unloadAll();

        Petsplus.LOGGER.info("PetsPlus: All pet data persisted successfully");
    }

    private static void onWorldLoad(MinecraftServer server, ServerWorld world) {
        Petsplus.LOGGER.info("PetsPlus: World {} loaded - initializing state manager", world.getRegistryKey().getValue());
        StateManager.onWorldLoaded(world);
        // State manager will be initialized when first accessed
    }
    
    private static void onWorldUnload(MinecraftServer server, ServerWorld world) {
        Petsplus.LOGGER.info("PetsPlus: World {} unloading - persisting pet data", world.getRegistryKey().getValue());

        StateManager stateManager = StateManager.getIfLoaded(world);
        if (stateManager != null) {
            stateManager.onWorldSave();
            // Properly shutdown the state manager to close async coordinators
            StateManager.unloadWorld(world);
        }
    }

    private static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        if (handler == null) {
            return;
        }

        ServerPlayerEntity player = handler.player;
        if (player == null) {
            return;
        }

        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        StateManager stateManager = StateManager.getIfLoaded(serverWorld);
        if (stateManager == null) {
            return;
        }

        PetInspectionManager.onPlayerJoin(player);
        BossBarManager.onPlayerJoin(player);
        ActionBarCueManager.onPlayerJoin(player);
    }
}
