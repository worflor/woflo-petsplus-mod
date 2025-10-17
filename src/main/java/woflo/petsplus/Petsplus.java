package woflo.petsplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import woflo.petsplus.ai.behaviors.BehaviorManager;

/**
 * Main mod class for PetsPlus.
 */
public class Petsplus implements ModInitializer {
    public static final String MOD_ID = "petsplus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PetsPlus");
        
        // Register resource reload listeners
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(BehaviorManager.getInstance());
        
        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("PetsPlus server starting");
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("PetsPlus server stopping");
        });
        
        LOGGER.info("PetsPlus initialized successfully");
    }
    
    /**
     * Create a PetsPlus identifier.
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}