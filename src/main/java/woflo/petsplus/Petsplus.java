package woflo.petsplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import woflo.petsplus.datagen.SimpleDataGenerator;
import woflo.petsplus.initialization.InitializationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Petsplus implements ModInitializer {
	public static final String MOD_ID = "petsplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	// Debug mode flag for enhanced UI displays
	public static boolean DEBUG_MODE = false;

	@Override
	public void onInitialize() {
		LOGGER.info("   /\\\\_/\\\\ ");
		LOGGER.info("   ( o.o ) ");
		LOGGER.info("    > ^ <  ");
		LOGGER.info("Foxy is initializing PetsPlus... refrain from petting!");
		
		// Initialize all systems and components
		InitializationManager.initializeAll();
		
		// Generate data files in development environment
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			SimpleDataGenerator.generateAll();
		}
		
		LOGGER.info("Foxy thanks you, and is glad to see PetsPlus fully initialized!");
	}
}