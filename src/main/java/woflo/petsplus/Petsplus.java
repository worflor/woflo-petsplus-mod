package woflo.petsplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import woflo.petsplus.datagen.SimpleDataGenerator;
import woflo.petsplus.initialization.InitializationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Petsplus implements ModInitializer {
	public static final String MOD_ID = "petsplus";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Initializing Pets+ mod...");
		
		// Initialize all systems with organized manager
		InitializationManager.initializeAll();
		
		// Register admin commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			woflo.petsplus.commands.PetsplusAdminCommands.register(dispatcher);
		});
		
		// Generate data files in development environment
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			SimpleDataGenerator.generateAll();
		}
		
		LOGGER.info("Pets+ mod initialized successfully!");
	}
}