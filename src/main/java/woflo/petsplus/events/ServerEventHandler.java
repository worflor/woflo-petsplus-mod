package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PlayerTickDispatcher;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.ActionBarCueManager;

/**
 * Handles server-wide events and ticking for PetsPlus systems.
 */
public class ServerEventHandler {
    
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerEventHandler::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerEventHandler::onServerStopping);
        ServerWorldEvents.LOAD.register(ServerEventHandler::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(ServerEventHandler::onWorldUnload);
    }
    
    private static void onServerStarting(MinecraftServer server) {
        Petsplus.LOGGER.info("PetsPlus: Server starting - initializing state managers");
        // State managers will be initialized lazily when worlds are accessed
    }
    
    private static void onServerStopping(MinecraftServer server) {
        Petsplus.LOGGER.info("PetsPlus: Server stopping - persisting all pet data");
        
        // Ensure all pet data is saved before shutdown
        for (ServerWorld world : server.getWorlds()) {
            StateManager stateManager = StateManager.forWorld(world);
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

        // Ensure all dispatcher-managed listeners release their per-player state.
        server.getPlayerManager().getPlayerList().forEach(PlayerTickDispatcher::clearPlayer);
        PlayerTickDispatcher.clearAll();

        Petsplus.LOGGER.info("PetsPlus: All pet data persisted successfully");
    }
    
    private static void onWorldLoad(MinecraftServer server, ServerWorld world) {
        Petsplus.LOGGER.info("PetsPlus: World {} loaded - initializing state manager", world.getRegistryKey().getValue());
        // State manager will be initialized when first accessed
    }
    
    private static void onWorldUnload(MinecraftServer server, ServerWorld world) {
        Petsplus.LOGGER.info("PetsPlus: World {} unloading - persisting pet data", world.getRegistryKey().getValue());
        
        StateManager stateManager = StateManager.forWorld(world);
        if (stateManager != null) {
            stateManager.onWorldSave();
        }
    }
}