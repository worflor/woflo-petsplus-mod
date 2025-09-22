package woflo.petsplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.commands.arguments.PetRoleArgumentType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.datagen.SimpleDataGenerator;
import woflo.petsplus.events.ServerEventHandler;

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
		
		// Register component types
		woflo.petsplus.component.PetsplusComponents.register();
		
		// Initialize configuration
		PetsPlusConfig.getInstance();
		
		// Register components first
		woflo.petsplus.component.PetsplusComponents.register();
		
		// Register custom argument types
		PetRoleArgumentType.register();
		
		// Register event handlers
		ServerEventHandler.register();
		
		// Register combat event handlers
		woflo.petsplus.events.CombatEventHandler.register();

		// Register support interaction handler (right-click to feed potions)
		woflo.petsplus.events.SupportInteractionHandler.register();

		// Register tribute handler (sneak-right-click to pay tributes)
		woflo.petsplus.events.TributeHandler.register();
		
		// Register Pets+ item handlers (respec tokens, linked whistles, etc.)
		woflo.petsplus.events.PetsplusItemHandler.register();
		
		// Register enhanced status effects system
		woflo.petsplus.events.PetsplusEffectEventHandler.initialize();
		
		// Register pet detection handlers
		woflo.petsplus.events.PetDetectionHandler.register();
		
		// Register player state tracking
		woflo.petsplus.events.PlayerStateTracker.register();
		
		// Register XP event handler for pet leveling
		woflo.petsplus.events.XpEventHandler.initialize();
		
		// Register pet death handler
		woflo.petsplus.events.PetDeathHandler.initialize();
		
		// Register Guardian Bulwark damage redirection
		woflo.petsplus.mechanics.GuardianBulwark.initialize();
		
		// Register Cursed One auto-resurrection
		woflo.petsplus.mechanics.CursedOneResurrection.initialize();
		
		// Register Eepy Eeper core mechanics (replaces old sacrifice mechanism)
		woflo.petsplus.roles.eepyeeper.EepyEeperCore.initialize();

		// Register sleep event handler for Eepy Eeper
		woflo.petsplus.events.SleepEventHandler.initialize();

		// Register stargaze mechanic for hidden advancement
		woflo.petsplus.mechanics.StargazeMechanic.initialize();
		
		// Register Eclipsed void save mechanism
		woflo.petsplus.mechanics.EclipsedVoidSave.initialize();
		
		// Initialize ability system
		AbilityManager.initialize();

		// Register Enchantment-Bound role mechanics
		woflo.petsplus.roles.enchantmentbound.EnchantmentBoundHandler.initialize();
		
		// Register main pets commands
		woflo.petsplus.commands.PetsCommand.register();
		
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