package woflo.petsplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.commands.suggestions.PetsSuggestionProviders;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.ChatLinks;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main /pets command following Fabric best practices and Brigadier patterns.
 * Replaces the old TestCommands with proper structure and suggestions.
 */
public class PetsCommand {
    
    // Custom suggestion providers following Fabric documentation
    public static final SuggestionProvider<ServerCommandSource> ROLE_SUGGESTIONS = (context, builder) -> {
        return CommandSource.suggestMatching(
            Arrays.stream(PetRole.values())
                .map(role -> role.getKey().toLowerCase())
                .collect(Collectors.toList()),
            builder
        );
    };
    
    public static final SuggestionProvider<ServerCommandSource> PET_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            List<String> petNames = findNearbyOwnedPets(player)
                .stream()
                .map(pet -> pet.hasCustomName() ? 
                    pet.getCustomName().getString() : 
                    pet.getType().getName().getString())
                .collect(Collectors.toList());
            return CommandSource.suggestMatching(petNames, builder);
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    };
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register(PetsCommand::registerCommands);
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, 
                                       CommandRegistryAccess registryAccess, 
                                       CommandManager.RegistrationEnvironment environment) {
        
        dispatcher.register(CommandManager.literal("pets")
            // Base command - shows pet overview
            .executes(PetsCommand::showPetOverview)
            
            // Role assignment with interactive suggestions
            .then(CommandManager.literal("role")
                .executes(PetsCommand::showRoleSelectionMenu)
                .then(CommandManager.argument("role_name", StringArgumentType.string())
                    .suggests(PetsSuggestionProviders.SMART_ROLE_SUGGESTIONS)
                    .executes(PetsCommand::assignRoleToFirstPendingPet)
                    .then(CommandManager.argument("pet_name", StringArgumentType.string())
                        .suggests(PetsSuggestionProviders.SMART_PET_SUGGESTIONS)
                        .executes(PetsCommand::assignRoleToSpecificPet))))
            
            // Pet info and inspection
            .then(CommandManager.literal("info")
                .executes(PetsCommand::showNearbyPetsInfo)
                .then(CommandManager.argument("pet_name", StringArgumentType.string())
                    .suggests(PetsSuggestionProviders.SMART_PET_SUGGESTIONS)
                    .executes(PetsCommand::showSpecificPetInfo)))
            
            // Tribute system
            .then(CommandManager.literal("tribute")
                .executes(PetsCommand::showTributeInfo)
                .then(CommandManager.argument("pet_name", StringArgumentType.string())
                    .suggests(PetsSuggestionProviders.SMART_PET_SUGGESTIONS)
                    .executes(PetsCommand::showSpecificTributeInfo)))
            
            // Admin commands
            .then(CommandManager.literal("admin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload")
                    .executes(PetsCommand::reloadConfig))
                .then(CommandManager.literal("debug")
                    .then(CommandManager.literal("on")
                        .executes(context -> toggleDebug(context, true)))
                    .then(CommandManager.literal("off")
                        .executes(context -> toggleDebug(context, false))))
                .then(CommandManager.literal("setlevel")
                    .then(CommandManager.argument("level", StringArgumentType.string())
                        .then(CommandManager.argument("pet_name", StringArgumentType.string())
                            .suggests(PET_SUGGESTIONS)
                            .executes(PetsCommand::adminSetPetLevel)))))
            
            // Help system
            .then(CommandManager.literal("help")
                .executes(PetsCommand::showHelp)
                .then(CommandManager.literal("roles")
                    .executes(PetsCommand::showRoleHelp))
                .then(CommandManager.literal("commands")
                    .executes(PetsCommand::showCommandHelp))));
    }
    
    // Command implementations
    private static int showPetOverview(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        List<MobEntity> nearbyPets = findNearbyOwnedPets(player);
        List<MobEntity> pendingPets = woflo.petsplus.events.PetDetectionHandler.getPendingPets(player);
        
        // Header
        player.sendMessage(Text.literal("=== Pet Overview ===").formatted(Formatting.GOLD), false);
        
        // Show pending pets first
        if (!pendingPets.isEmpty()) {
            player.sendMessage(Text.literal("🔄 Pets awaiting role assignment:")
                .formatted(Formatting.YELLOW), false);
            
            for (MobEntity pet : pendingPets) {
                String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
                    pet.getType().getName().getString();
                
                player.sendMessage(Text.literal("  • " + petName)
                    .formatted(Formatting.WHITE), false);
            }
            
            // Show role selection buttons
            showRoleButtons(player);
            player.sendMessage(Text.empty(), false);
        }
        
        // Show existing pets
        if (!nearbyPets.isEmpty()) {
            player.sendMessage(Text.literal("🐾 Your nearby pets:")
                .formatted(Formatting.GREEN), false);
            
            for (MobEntity pet : nearbyPets) {
                PetComponent petComp = PetComponent.get(pet);
                if (petComp != null) {
                    String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
                        pet.getType().getName().getString();
                    
                    PetRole role = petComp.getRole();
                    int level = petComp.getLevel();
                    
                    player.sendMessage(Text.literal("  • " + petName + " (" + 
                        (role != null ? role.getDisplayName() : "No Role") + ", Level " + level + ")")
                        .formatted(Formatting.WHITE), false);
                }
            }
        } else if (pendingPets.isEmpty()) {
            player.sendMessage(Text.literal("No pets found nearby. Tame an animal to get started!")
                .formatted(Formatting.YELLOW), false);
        }
        
        // Quick actions
        showQuickActions(player);
        
        return 1;
    }
    
    private static int showRoleSelectionMenu(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.literal("=== Available Pet Roles ===").formatted(Formatting.GOLD), false);
        
        showRoleButtons(player);
        
        return 1;
    }
    
    private static int assignRoleToFirstPendingPet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String roleName = StringArgumentType.getString(context, "role_name");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // Parse role
        PetRole role = parseRoleName(roleName);
        if (role == null) {
            player.sendMessage(Text.literal("Invalid role: " + roleName)
                .formatted(Formatting.RED), false);
            showRoleButtons(player);
            return 0;
        }
        
        // Get pending pets
        List<MobEntity> pendingPets = woflo.petsplus.events.PetDetectionHandler.getPendingPets(player);
        
        if (pendingPets.isEmpty()) {
            player.sendMessage(Text.literal("No pets are waiting for role assignment!")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        // Assign to first pending pet
        MobEntity pet = pendingPets.get(0);
        boolean success = woflo.petsplus.events.PetDetectionHandler.assignPendingRole(player, pet, role);
        
        if (success) {
            String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
                pet.getType().getName().getString();
            
            player.sendMessage(Text.literal("✓ Assigned ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(role.getDisplayName()).formatted(Formatting.AQUA))
                .append(Text.literal(" role to ").formatted(Formatting.GREEN))
                .append(Text.literal(petName).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.GREEN)), false);
            
            // Show remaining pending pets
            if (pendingPets.size() > 1) {
                player.sendMessage(Text.literal("📋 You have " + (pendingPets.size() - 1) + " more pet(s) waiting for assignment.")
                    .formatted(Formatting.GRAY), false);
                showRoleButtons(player);
            }
            
            return 1;
        } else {
            player.sendMessage(Text.literal("Failed to assign role to pet!")
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int assignRoleToSpecificPet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // TODO: Implement specific pet role assignment when needed
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        player.sendMessage(Text.literal("Specific pet role assignment not yet implemented")
            .formatted(Formatting.YELLOW), false);
        return 0;
    }
    
    private static int showNearbyPetsInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        List<MobEntity> pets = findNearbyOwnedPets(player);
        
        if (pets.isEmpty()) {
            player.sendMessage(Text.literal("No pets found nearby!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        player.sendMessage(Text.literal("=== Pet Information ===").formatted(Formatting.GOLD), false);
        
        for (MobEntity pet : pets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null) {
                // Use a simplified pet info display
                showPetInfo(player, pet, petComp);
            }
        }
        
        return 1;
    }
    
    private static int showSpecificPetInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // TODO: Implement specific pet info lookup
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        player.sendMessage(Text.literal("Specific pet info not yet implemented")
            .formatted(Formatting.YELLOW), false);
        return 0;
    }
    
    private static int showTributeInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.literal("=== Tribute System ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("Pets require tributes to break level caps:")
            .formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  • Level 10 → Gold Ingot").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • Level 20 → Diamond").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  • Level 30 → Netherite Ingot").formatted(Formatting.DARK_PURPLE), false);
        player.sendMessage(Text.literal("Sneak + right-click your pet with the required item.")
            .formatted(Formatting.GRAY), false);
        
        return 1;
    }
    
    private static int showSpecificTributeInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // TODO: Implement specific pet tribute info
        return 0;
    }
    
    private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            woflo.petsplus.config.PetsPlusConfig.getInstance().reload();
            player.sendMessage(Text.literal("Configuration reloaded successfully!")
                .formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error reloading config: " + e.getMessage())
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int toggleDebug(CommandContext<ServerCommandSource> context, boolean enable) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // TODO: Implement actual debug toggle
        player.sendMessage(Text.literal("Debug mode " + (enable ? "enabled" : "disabled"))
            .formatted(enable ? Formatting.GREEN : Formatting.RED), false);
        
        return 1;
    }
    
    private static int adminSetPetLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // TODO: Implement admin pet level setting
        return 0;
    }
    
    private static int showHelp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.literal("=== PetsPlus Help ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("/pets").formatted(Formatting.AQUA)
            .append(Text.literal(" - Show pet overview").formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("/pets role [role]").formatted(Formatting.AQUA)
            .append(Text.literal(" - Assign role to pending pet").formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("/pets info").formatted(Formatting.AQUA)
            .append(Text.literal(" - Show detailed pet information").formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("/pets tribute").formatted(Formatting.AQUA)
            .append(Text.literal(" - Show tribute requirements").formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("/pets help roles").formatted(Formatting.AQUA)
            .append(Text.literal(" - Learn about pet roles").formatted(Formatting.WHITE)), false);
        
        return 1;
    }
    
    private static int showRoleHelp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.literal("=== Pet Roles Guide ===").formatted(Formatting.GOLD), false);
        
        for (PetRole role : PetRole.values()) {
            player.sendMessage(Text.literal("• " + role.getDisplayName())
                .formatted(Formatting.AQUA)
                .append(Text.literal(" - " + getRoleDescription(role))
                    .formatted(Formatting.WHITE)), false);
        }
        
        return 1;
    }
    
    private static int showCommandHelp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return showHelp(context);
    }
    
    // Helper methods
    private static List<MobEntity> findNearbyOwnedPets(ServerPlayerEntity player) {
        return player.getWorld().getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(10), 
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && petComp.isOwnedBy(player);
            }
        );
    }
    
    private static PetRole parseRoleName(String name) {
        // Try direct key match first
        for (PetRole role : PetRole.values()) {
            if (role.getKey().equalsIgnoreCase(name)) {
                return role;
            }
        }
        
        // Try display name match
        for (PetRole role : PetRole.values()) {
            if (role.getDisplayName().equalsIgnoreCase(name)) {
                return role;
            }
        }
        
        return null;
    }
    
    private static String getRoleDescription(PetRole role) {
        return switch (role) {
            case GUARDIAN -> "Protective tank that redirects damage and provides stability";
            case STRIKER -> "Aggressive fighter that marks weakened enemies for execution";
            case SUPPORT -> "Healing companion that stores and shares beneficial effects";
            case SCOUT -> "Fast explorer that reveals threats and attracts loot";
            case SKYRIDER -> "Aerial specialist that controls air movement and prevents falls";
            case ENCHANTMENT_BOUND -> "Magic-focused pet that enhances gear enchantments";
            case CURSED_ONE -> "Dark magic wielder with high risk and reward mechanics";
            case EEPY_EEPER -> "Sleep specialist that provides rest-based healing and protection";
            case ECLIPSED -> "Shadow manipulator that brands enemies and provides stealth";
        };
    }
    
    private static void showRoleButtons(ServerPlayerEntity player) {
        ChatLinks.Suggest[] roleButtons = new ChatLinks.Suggest[] {
            new ChatLinks.Suggest("[Guardian]", "/pets role guardian", "Protective tank role", "aqua", true),
            new ChatLinks.Suggest("[Striker]", "/pets role striker", "Aggressive damage dealer", "red", true),
            new ChatLinks.Suggest("[Support]", "/pets role support", "Healing and buffs", "green", true),
            new ChatLinks.Suggest("[Scout]", "/pets role scout", "Fast explorer", "yellow", true)
        };
        
        ChatLinks.Suggest[] roleButtons2 = new ChatLinks.Suggest[] {
            new ChatLinks.Suggest("[Skyrider]", "/pets role skyrider", "Aerial support", "light_purple", true),
            new ChatLinks.Suggest("[Enchantment-Bound]", "/pets role enchantment_bound", "Magic abilities", "blue", true),
            new ChatLinks.Suggest("[Cursed One]", "/pets role cursed_one", "Dark magic", "dark_red", true),
            new ChatLinks.Suggest("[Eepy Eeper]", "/pets role eepy_eeper", "Sleep-based abilities", "dark_green", true)
        };
        
        ChatLinks.Suggest[] roleButtons3 = new ChatLinks.Suggest[] {
            new ChatLinks.Suggest("[Eclipsed]", "/pets role eclipsed", "Shadow magic", "dark_purple", true)
        };
        
        ChatLinks.sendSuggestRow(player, roleButtons, 4);
        ChatLinks.sendSuggestRow(player, roleButtons2, 4);
        ChatLinks.sendSuggestRow(player, roleButtons3, 4);
    }
    
    private static void showQuickActions(ServerPlayerEntity player) {
        player.sendMessage(Text.empty(), false);
        player.sendMessage(Text.literal("Quick Actions:").formatted(Formatting.GRAY), false);
        
        ChatLinks.Suggest[] actions = new ChatLinks.Suggest[] {
            new ChatLinks.Suggest("[Assign Roles]", "/pets role", "Choose a role for pending pets", "green", false),
            new ChatLinks.Suggest("[Pet Info]", "/pets info", "View detailed pet information", "aqua", false),
            new ChatLinks.Suggest("[Help]", "/pets help", "Show help information", "yellow", false)
        };
        
        ChatLinks.sendSuggestRow(player, actions, 3);
    }
    
    private static void showPetInfo(ServerPlayerEntity player, MobEntity pet, PetComponent petComp) {
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
            pet.getType().getName().getString();
        
        PetRole role = petComp.getRole();
        int level = petComp.getLevel();
        int xp = petComp.getExperience();
        float progress = petComp.getXpProgress();
        
        player.sendMessage(Text.literal("• " + petName)
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" (" + (role != null ? role.getDisplayName() : "No Role") + ")")
                .formatted(Formatting.AQUA)), false);
        
        player.sendMessage(Text.literal("  Level " + level + " (XP: " + xp + ", Progress: " + 
            String.format("%.1f", progress * 100) + "%)")
            .formatted(Formatting.WHITE), false);
        
        if (level < 30) {
            int nextLevelXp = PetComponent.getTotalXpForLevel(level + 1);
            int needed = nextLevelXp - xp;
            player.sendMessage(Text.literal("  Next level: " + needed + " XP needed")
                .formatted(Formatting.GRAY), false);
        }
    }
}