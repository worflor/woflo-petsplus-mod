package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.StateManager;

/**
 * Handles server-wide events and ticking for PetsPlus systems.
 */
public class ServerEventHandler {
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ServerEventHandler::onServerTick);
        ServerLifecycleEvents.SERVER_STARTING.register(ServerEventHandler::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerEventHandler::onServerStopping);
        ServerWorldEvents.LOAD.register(ServerEventHandler::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(ServerEventHandler::onWorldUnload);
    }
    
    private static void onServerTick(MinecraftServer server) {
        // Tick state managers for all worlds
        for (ServerWorld world : server.getWorlds()) {
            StateManager stateManager = StateManager.forWorld(world);
            stateManager.tick();
        }

        // Update pet inspection boss bars
        woflo.petsplus.ui.PetInspectionManager.tick(server);
        
        // Tick boss bars for cleanup and updates
        woflo.petsplus.ui.BossBarManager.tickBossBars();
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