package woflo.petsplus.initialization;

import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.commands.arguments.PetRoleArgumentType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.events.ServerEventHandler;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.roles.guardian.GuardianCore;
import woflo.petsplus.roles.support.SupportCore;
import woflo.petsplus.roles.scout.ScoutCore;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundCore;
import woflo.petsplus.roles.eclipsed.EclipsedCore;

import static woflo.petsplus.Petsplus.LOGGER;

/**
 * Central initialization manager for all Pets+ systems.
 * 
 * Organizes all initialization logic into logical categories to keep
 * the main mod class clean and maintainable.
 */
public class InitializationManager {
    
    /**
     * Initialize all Pets+ systems in the correct order.
     */
    public static void initializeAll() {
        initializeCore();
        initializeEventHandlers();
        initializeRoleSystems();
        initializeMechanics();
        initializeCommands();
    }
    
    /**
     * Initialize core components and configuration.
     */
    public static void initializeCore() {
        LOGGER.debug("Foxy: Bootstrapping registries...");
        // Bootstrap registry content before consumers run
        PetsPlusRegistries.bootstrap();

        LOGGER.debug("Foxy: Registering core components...");
        // Register component types
        woflo.petsplus.component.PetsplusComponents.register();
        
        // Register items
        woflo.petsplus.items.PetsplusItems.register();

        LOGGER.debug("Foxy: Loading configuration...");
        // Initialize configuration
        PetsPlusConfig.getInstance();

        // Load reusable visual systems
        woflo.petsplus.ui.AfterimageManager.initialize();

        // Register custom argument types
        PetRoleArgumentType.register();

        // Register custom advancement criteria (new event-driven system)
        woflo.petsplus.advancement.AdvancementCriteriaRegistry.register();

        // Register default emotion providers
        var mood = woflo.petsplus.api.mood.MoodAPI.get();
        mood.registerProvider(new woflo.petsplus.mood.providers.EnvironmentComfortProvider());
        mood.registerProvider(new woflo.petsplus.mood.providers.CombatThreatProvider());
        mood.registerProvider(new woflo.petsplus.mood.providers.PackContagionProvider());
        woflo.petsplus.mood.MoodAdvancementTracker.register();
    }
    
    /**
     * Initialize all event handlers and listeners.
     */
    public static void initializeEventHandlers() {
        LOGGER.debug("Foxy: Setting up event handlers...");
        
        // Main event handlers
        ServerEventHandler.register();
        woflo.petsplus.events.CombatEventHandler.register();
        woflo.petsplus.events.EmotionsEventHandler.register();
        
        // Interaction handlers
        woflo.petsplus.events.SupportInteractionHandler.register();
        woflo.petsplus.events.TributeHandler.register();
        woflo.petsplus.events.PetsplusItemHandler.register();
        woflo.petsplus.events.PettingHandler.register();
        woflo.petsplus.events.TamingHandler.register();
        woflo.petsplus.events.PetCompendiumHandler.register();
        // OwnerAbilitySignalTracker is registered via PlayerTickListeners
        woflo.petsplus.interaction.OwnerAbilitySignalAbilityBridge.register();
        
        // Pet trading system - register handler
        woflo.petsplus.handler.PetTradingHandler.register();

        // System event handlers
        woflo.petsplus.events.PetDetectionHandler.register();
        woflo.petsplus.events.PetBreedingHandler.register();
        woflo.petsplus.events.PlayerStateTracker.register();
        woflo.petsplus.events.XpEventHandler.initialize();
        woflo.petsplus.events.PetDeathHandler.initialize();
        woflo.petsplus.events.SleepEventHandler.initialize();

        woflo.petsplus.state.tracking.PlayerTickListeners.registerAll();
    }
    
    /**
     * Initialize all pet role systems.
     */
    public static void initializeRoleSystems() {
        LOGGER.debug("Foxy: Awakening pet abilities...");
        
        // Combat-focused roles
        GuardianCore.initialize();
        
        // Support and utility roles  
        SupportCore.initialize();
        ScoutCore.initialize();
        
        // Specialized movement and magic roles
        EnchantmentBoundCore.initialize();
        EclipsedCore.initialize();
        
        // Special role handlers
        woflo.petsplus.roles.eepyeeper.EepyEeperCore.initialize();
        
        // Initialize ability system
        AbilityManager.initialize();
    }
    
    /**
     * Initialize specialized mechanics and features.
     */
    public static void initializeMechanics() {
        LOGGER.debug("Foxy: Tuning special mechanics...");
        
        // Role-specific mechanics
        woflo.petsplus.mechanics.CursedOneResurrection.initialize();
        
        // Special mechanics
        woflo.petsplus.mechanics.StargazeMechanic.initialize();
    }
    
    /**
     * Initialize command systems.
     */
    public static void initializeCommands() {
        LOGGER.debug("Foxy: Teaching pet commands...");
        
        // Register main pets commands
        woflo.petsplus.commands.PetsCommand.register();
    }
}
