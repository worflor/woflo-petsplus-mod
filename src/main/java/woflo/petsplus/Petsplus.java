package woflo.petsplus;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import woflo.petsplus.initialization.InitializationManager;

/**
 * Main mod class for PetsPlus.
 */
public class Petsplus implements ModInitializer {
    public static final String MOD_ID = "petsplus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PetsPlus");

        InitializationManager.initializeAll();

        LOGGER.info("PetsPlus initialized successfully");
    }
    
    /**
     * Create a PetsPlus identifier.
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
