









package woflo.petsplus.commands;

import woflo.petsplus.api.registry.RoleIdentifierUtil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.commands.suggestions.PetsSuggestionProviders;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.naming.NameParser;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.PetNatureSelector;
import woflo.petsplus.ui.ChatLinks;
import woflo.petsplus.util.PetTargetingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main /pets command following Fabric best practices and Brigadier patterns.
 * Uses intelligent raycast targeting for pet selection.
 */
public class PetsCommand {
    
    // Custom suggestion providers following Fabric documentation
    public static final SuggestionProvider<ServerCommandSource> ROLE_SUGGESTIONS = (context, builder) -> {
        List<String> keys = PetsPlusRegistries.petRoleTypeRegistry().stream()
            .map(type -> type.id().getPath().toLowerCase())
            .collect(Collectors.toList());
        return CommandSource.suggestMatching(keys, builder);
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

        // Register both /pets and /petsplus commands
        var petsPlusCommand = CommandManager.literal("petsplus")
            // Base command - shows pet overview
            .executes(PetsCommand::showPetOverview)

            // Role assignment with interactive suggestions
            .then(CommandManager.literal("role")
                .executes(PetsCommand::showRoleSelectionMenu)
                .then(CommandManager.argument("role", StringArgumentType.string())
                    .suggests(PetsSuggestionProviders.SMART_ROLE_SUGGESTIONS)
                    .executes(PetsCommand::assignRoleToFirstPendingPetWithValidation)
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
                .then(CommandManager.literal("config")
                    .then(CommandManager.literal("regen")
                        .then(CommandManager.argument("role", StringArgumentType.string())
                            .suggests(PetsSuggestionProviders.SMART_ROLE_SUGGESTIONS)
                            .executes(PetsCommand::regenerateRoleConfigWithValidation))))
                .then(CommandManager.literal("debug")
                    .then(CommandManager.literal("on")
                        .executes(context -> toggleDebug(context, true)))
                    .then(CommandManager.literal("off")
                        .executes(context -> toggleDebug(context, false))))
                .then(CommandManager.literal("nature")
                    .then(CommandManager.literal("info")
                        .executes(PetsplusAdminCommands::showPetNature))
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("nature", IdentifierArgumentType.identifier())
                            .suggests((context, builder) -> {
                                for (Identifier id : PetNatureSelector.getRegisteredNatureIds()) {
                                    builder.suggest(id.toString());
                                }
                                builder.suggest("petsplus:custom");
                                return builder.buildFuture();
                            })
                            .executes(PetsplusAdminCommands::setPetNature)))
                    .then(CommandManager.literal("clear")
                        .executes(PetsplusAdminCommands::clearPetNature)))
                .then(CommandManager.literal("setlevel")
                    .then(CommandManager.argument("level", StringArgumentType.string())
                        .executes(PetsCommand::adminSetPetLevel)))
                .then(CommandManager.literal("testname")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(PetsCommand::adminTestNameParsing))))

            .then(CommandManager.literal("journal")
                .executes(PetsCommand::showCueJournal))

            // Emotion and mood debugging
            .then(CommandManager.literal("emotions")
                .executes(PetsCommand::showEmotionInfo)
                .then(CommandManager.literal("info")
                    .executes(PetsCommand::showEmotionInfo))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("emotion", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            // Add all emotion suggestions
                            for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
                                builder.suggest(emotion.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("weight", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                builder.suggest("0.2");
                                builder.suggest("0.5");
                                builder.suggest("1.0");
                                builder.suggest("2.0");
                                builder.suggest("5.0");
                                return builder.buildFuture();
                            })
                            .executes(PetsCommand::setEmotion))))
                .then(CommandManager.literal("clear")
                    .executes(PetsCommand::clearEmotions))
                .then(CommandManager.literal("preset")
                    .then(CommandManager.literal("happy")
                        .executes(PetsCommand::presetHappy))
                    .then(CommandManager.literal("afraid")
                        .executes(PetsCommand::presetAfraid))
                    .then(CommandManager.literal("angry")
                        .executes(PetsCommand::presetAngry))
                    .then(CommandManager.literal("calm")
                        .executes(PetsCommand::presetCalm))
                    .then(CommandManager.literal("playful")
                        .executes(PetsCommand::presetPlayful))
                    .then(CommandManager.literal("protective")
                        .executes(PetsCommand::presetProtective))
                    .then(CommandManager.literal("melancholy")
                        .executes(PetsCommand::presetMelancholy))))

            // Help system
            .then(CommandManager.literal("help")
                .executes(PetsCommand::showHelp)
                .then(CommandManager.literal("roles")
                    .executes(PetsCommand::showRoleHelp))
                .then(CommandManager.literal("commands")
                    .executes(PetsCommand::showCommandHelp)));

        dispatcher.register(petsPlusCommand);

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
                    
                    Identifier roleId = petComp.getRoleId();
                    PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
                    Text roleLabel = resolveRoleLabel(roleId, roleType);
                    int level = petComp.getLevel();

                    player.sendMessage(Text.literal("  • " + petName + " (")
                        .append(roleLabel.copy())
                        .append(Text.literal(", Level " + level + ")"))
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
    
    private static int assignRoleToFirstPendingPetWithValidation(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String roleInput = StringArgumentType.getString(context, "role");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        // Direct role validation using static constants to avoid registry sync issues
        PetRoleType roleType = getBuiltinRoleByName(roleInput);
        Identifier roleId = roleType != null ? roleType.id() : null;

        if (roleType == null) {
            player.sendMessage(Text.literal("Unknown pet role: " + roleInput + ". Available roles: guardian, striker, support, scout, skyrider, enchantment_bound, cursed_one, eepy_eeper, eclipsed").formatted(Formatting.RED), false);
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
        boolean success = woflo.petsplus.events.PetDetectionHandler.assignPendingRole(player, pet, roleId);

        if (success) {
            String petName = pet.hasCustomName() ? pet.getCustomName().getString() :
                pet.getType().getName().getString();

            player.sendMessage(Text.literal("✓ Assigned ")
                .formatted(Formatting.GREEN)
                .append(resolveRoleLabel(roleId, roleType).copy().formatted(Formatting.AQUA))
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

        Map<Integer, Identifier> tributeItems = PetsPlusConfig.getInstance().getResolvedGlobalTributeItems();
        tributeItems.forEach((level, itemId) -> player.sendMessage(formatTributeLine(level, itemId), false));

        player.sendMessage(Text.literal("Sneak + right-click your pet with the required item.")
            .formatted(Formatting.GRAY), false);

        return 1;
    }

    private static MutableText formatTributeLine(int level, Identifier itemId) {
        Formatting itemColor = tributeFormattingForLevel(level);
        MutableText line = Text.literal("  • Level " + level + " → ").formatted(Formatting.WHITE);
        Item item = Registries.ITEM.get(itemId);
        if (Registries.ITEM.getId(item).equals(itemId)) {
            line.append(Text.translatable(item.getTranslationKey()).copy().formatted(itemColor));
        } else {
            line.append(Text.literal(itemId.toString()).formatted(Formatting.RED));
        }
        return line;
    }

    private static Formatting tributeFormattingForLevel(int level) {
        return switch (level) {
            case 10 -> Formatting.YELLOW;
            case 20 -> Formatting.AQUA;
            case 30 -> Formatting.DARK_PURPLE;
            default -> Formatting.GOLD;
        };
    }
    
    private static int showSpecificTributeInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // TODO: Implement specific pet tribute info
        return 0;
    }
    
    private static int showCueJournal(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        EmotionContextCues.dumpJournal(player);
        return 1;
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        try {
            PetsPlusConfig.getInstance().reload();
            context.getSource().sendFeedback(
                () -> Text.literal("Configuration reloaded successfully!").formatted(Formatting.GREEN),
                false
            );
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error reloading config: " + e.getMessage())
                .formatted(Formatting.RED));
            return 0;
        }
    }

    private static int regenerateRoleConfigWithValidation(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String roleInput = StringArgumentType.getString(context, "role");

        // Direct role validation using static constants
        PetRoleType roleType = getBuiltinRoleByName(roleInput);
        if (roleType == null) {
            context.getSource().sendError(Text.literal("Unknown pet role: " + roleInput).formatted(Formatting.RED));
            return 0;
        }

        Identifier roleId = roleType.id();
        PetsPlusConfig.getInstance().regenerateRoleConfig(roleId);
        context.getSource().sendFeedback(
            () -> Text.literal("Regenerated config stub for role " + roleId + ".").formatted(Formatting.GREEN),
            false
        );
        return 1;
    }
    
    private static int toggleDebug(CommandContext<ServerCommandSource> context, boolean enable) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        // Set debug mode flag
        woflo.petsplus.Petsplus.DEBUG_MODE = enable;

        player.sendMessage(Text.literal("Debug mode " + (enable ? "enabled" : "disabled") + " - mood power levels will " + (enable ? "show" : "be hidden"))
            .formatted(enable ? Formatting.GREEN : Formatting.RED), false);

        return 1;
    }
    
    private static int adminSetPetLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String levelStr = StringArgumentType.getString(context, "level");
        
        // Parse level
        int level;
        try {
            level = Integer.parseInt(levelStr);
            if (level < 1 || level > 30) {
                player.sendMessage(Text.literal("Level must be between 1 and 30!").formatted(Formatting.RED), false);
                return 0;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("Invalid level: " + levelStr).formatted(Formatting.RED), false);
            return 0;
        }
        
        // Find the target pet using raycast/proximity
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby and try again.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Set the level
        petComp.setLevel(level);
        player.sendMessage(Text.literal("Pet level set to " + level + "!").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int adminTestNameParsing(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String name = StringArgumentType.getString(context, "name");

        if (name.trim().isEmpty()) {
            player.sendMessage(Text.literal("Name cannot be empty.").formatted(Formatting.RED), false);
            return 0;
        }

        try {
            // Parse the name for attributes
            List<AttributeKey> attributes = NameParser.parse(name);

            // Send result to player
            player.sendMessage(Text.literal("📝 Testing name parsing for: \"")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(name).formatted(Formatting.WHITE))
                .append(Text.literal("\"").formatted(Formatting.YELLOW)), false);

            if (attributes.isEmpty()) {
                player.sendMessage(Text.literal("   No attributes found.").formatted(Formatting.GRAY), false);
            } else {
                player.sendMessage(Text.literal("   Found " + attributes.size() + " attribute(s):").formatted(Formatting.GREEN), false);

                for (int i = 0; i < attributes.size(); i++) {
                    AttributeKey attr = attributes.get(i);
                    MutableText attributeText = Text.literal("   " + (i + 1) + ". ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(attr.type()).formatted(Formatting.AQUA));

                    if (attr.hasValue()) {
                        attributeText.append(Text.literal(": ").formatted(Formatting.GRAY))
                            .append(Text.literal(attr.value()).formatted(Formatting.WHITE));
                    }

                    if (attr.priority() > 0) {
                        attributeText.append(Text.literal(" (priority: " + attr.priority() + ")").formatted(Formatting.DARK_GRAY));
                    }

                    player.sendMessage(attributeText, false);
                }

                // Show configuration status
                PetsPlusConfig config = PetsPlusConfig.getInstance();
                player.sendMessage(Text.literal("Config status:")
                    .formatted(Formatting.YELLOW), false);
                player.sendMessage(Text.literal("  Enabled: " + config.isNamedAttributesEnabled())
                    .formatted(config.isNamedAttributesEnabled() ? Formatting.GREEN : Formatting.RED), false);
                player.sendMessage(Text.literal("  Max attributes: " + config.getMaxNamedAttributes())
                    .formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("  Case sensitive: " + config.isNamedAttributesCaseSensitive())
                    .formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("  Patterns - Exact: " + config.isExactPatternsEnabled() +
                    ", Prefix: " + config.isPrefixPatternsEnabled() +
                    ", Regex: " + config.isRegexPatternsEnabled())
                    .formatted(Formatting.GRAY), false);
            }

            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error parsing name: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }


    
    /**
     * Find the target pet using intelligent raycast targeting.
     * Primary: Pet player is looking at
     * Fallback: Nearest pet within range
     */
    private static MobEntity findTargetPet(ServerPlayerEntity player) {
        return PetTargetingUtil.findTargetPet(player);
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

        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        for (PetRoleType type : registry) {
            Identifier id = type.id();
            Text label = resolveRoleLabel(id, type);
            String description = getRoleDescription(id, type);
            player.sendMessage(Text.literal("• ")
                .formatted(Formatting.DARK_GRAY)
                .append(label.copy().formatted(Formatting.AQUA))
                .append(Text.literal(" - " + description).formatted(Formatting.WHITE)), false);
        }
        
        return 1;
    }
    
    private static int showCommandHelp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return showHelp(context);
    }
    
    // Helper methods

    /**
     * Get builtin role by name using static constants to avoid registry sync issues
     */
    private static PetRoleType getBuiltinRoleByName(String input) {
        if (input == null) return null;

        String normalized = input.toLowerCase().trim();
        return switch (normalized) {
            case "guardian" -> PetRoleType.GUARDIAN;
            case "striker" -> PetRoleType.STRIKER;
            case "support" -> PetRoleType.SUPPORT;
            case "scout" -> PetRoleType.SCOUT;
            case "skyrider" -> PetRoleType.SKYRIDER;
            case "enchantment_bound", "enchantmentbound" -> PetRoleType.ENCHANTMENT_BOUND;
            case "cursed_one", "cursedone" -> PetRoleType.CURSED_ONE;
            case "eepy_eeper", "eepyeeper" -> PetRoleType.EEPY_EEPER;
            case "eclipsed" -> PetRoleType.ECLIPSED;
            default -> null;
        };
    }

    private static List<MobEntity> findNearbyOwnedPets(ServerPlayerEntity player) {
        return player.getEntityWorld().getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(10),
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && petComp.isOwnedBy(player);
            }
        );
    }

    private static Text resolveRoleLabel(Identifier roleId, @Nullable PetRoleType roleType) {
        if (roleType != null) {
            Text translated = Text.translatable(roleType.translationKey());
            if (!translated.getString().equals(roleType.translationKey())) {
                return translated;
            }
        }

        return Text.literal(RoleIdentifierUtil.formatName(roleId));
    }

    private static String getRoleDescription(Identifier roleId, @Nullable PetRoleType roleType) {
        return roleType != null ? Text.translatable(roleType.translationKey()).getString() : RoleIdentifierUtil.formatName(roleId);
    }
    
    private static void showRoleButtons(ServerPlayerEntity player) {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        List<ChatLinks.Suggest> commands = new ArrayList<>();

        for (PetRoleType type : registry) {
            Identifier id = type.id();
            Text label = resolveRoleLabel(id, type);
            String hover = getRoleDescription(id, type);
            commands.add(new ChatLinks.Suggest(
                "[" + label.getString() + "]",
                "/petsplus role " + id.getPath(),
                hover,
                "aqua",
                true
            ));
        }

        if (commands.isEmpty()) {
            return;
        }

        ChatLinks.sendSuggestRow(player, commands.toArray(new ChatLinks.Suggest[0]), 4);
    }
    
    private static void showQuickActions(ServerPlayerEntity player) {
        player.sendMessage(Text.empty(), false);
        player.sendMessage(Text.literal("Quick Actions:").formatted(Formatting.GRAY), false);

        ChatLinks.Suggest[] actions = new ChatLinks.Suggest[] {
            new ChatLinks.Suggest("[Assign Roles]", "/petsplus role ", "Click to prefill then type a role or pick a button", "green", false),
            new ChatLinks.Suggest("[Pet Info]", "/petsplus info", "View detailed pet information", "aqua", false),
            new ChatLinks.Suggest("[Help]", "/petsplus help", "Show help information", "yellow", false)
        };

        ChatLinks.sendSuggestRow(player, actions, 3);
    }
    
    private static void showPetInfo(ServerPlayerEntity player, MobEntity pet, PetComponent petComp) {
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : 
            pet.getType().getName().getString();
        
        Identifier roleId = petComp.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        int level = petComp.getLevel();
        int xp = petComp.getExperience();
        float progress = petComp.getXpProgress();

        Text roleText = Text.literal(" (")
            .append(resolveRoleLabel(roleId, roleType).copy().formatted(Formatting.AQUA))
            .append(Text.literal(")"));

        player.sendMessage(Text.literal("• " + petName)
            .formatted(Formatting.YELLOW)
            .append(roleText), false);
        
        player.sendMessage(Text.literal("  Level " + level + " (XP: " + xp + ", Progress: " + 
            String.format("%.1f", progress * 100) + "%)")
            .formatted(Formatting.WHITE), false);
        
        if (level < petComp.getRoleType().xpCurve().maxLevel()) {
            int nextLevelXp = petComp.getXpNeededForNextLevel();
            int needed = Math.max(0, nextLevelXp - xp);
            player.sendMessage(Text.literal("  Next level: " + needed + " XP needed")
                .formatted(Formatting.GRAY), false);
        }
    }

    // ============ EMOTION COMMANDS ============

    private static int setEmotion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String emotionName = StringArgumentType.getString(context, "emotion").toUpperCase();
        String weightStr = StringArgumentType.getString(context, "weight");

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        try {
            PetComponent.Emotion emotion = PetComponent.Emotion.valueOf(emotionName);
            float weight = Float.parseFloat(weightStr);

            petComp.pushEmotion(emotion, weight);
            petComp.updateMood();

            player.sendMessage(Text.literal("Set emotion ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(emotion.name().toLowerCase()).formatted(Formatting.AQUA))
                .append(Text.literal(" to weight ").formatted(Formatting.GREEN))
                .append(Text.literal(String.valueOf(weight)).formatted(Formatting.YELLOW)), false);

            return 1;
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Invalid emotion name or weight! Use tab completion to see valid emotions.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static int clearEmotions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        // Clear emotions by setting them to very low values - there's no direct clear method
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            petComp.pushEmotion(emotion, 0.0f);
        }
        petComp.updateMood();

        player.sendMessage(Text.literal("Cleared all emotions from pet!").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int showEmotionInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        player.sendMessage(Text.literal("=== Pet Emotion & Mood Info ===").formatted(Formatting.GOLD), false);

        // Show current mood
        player.sendMessage(Text.literal("Current Mood: ")
            .formatted(Formatting.GRAY)
            .append(petComp.getMoodTextWithDebug()), false);

        // Show dominant emotion
        PetComponent.Emotion dominantEmotion = petComp.getDominantEmotion();
        if (dominantEmotion != null) {
            player.sendMessage(Text.literal("Dominant Emotion: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(dominantEmotion.name().toLowerCase()).formatted(Formatting.AQUA)), false);
        } else {
            player.sendMessage(Text.literal("Dominant Emotion: None").formatted(Formatting.GRAY), false);
        }

        // Show mood blend
        player.sendMessage(Text.literal("Mood Strengths:").formatted(Formatting.YELLOW), false);
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            float strength = petComp.getMoodBlend().getOrDefault(mood, 0f);
            if (strength > 0.1f) {
                player.sendMessage(Text.literal("  " + mood.name().toLowerCase() + ": ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.format("%.2f", strength)).formatted(Formatting.WHITE)), false);
            }
        }

        return 1;
    }

    // Emotion presets for common moods
    private static int presetHappy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Happy", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.RELIEF, 2.5f),
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 1.5f),
            new EmotionWeight(PetComponent.Emotion.CONTENT, 1.0f)
        });
    }

    private static int presetAfraid(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Afraid", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.ANGST, 3.0f),
            new EmotionWeight(PetComponent.Emotion.FOREBODING, 2.5f),
            new EmotionWeight(PetComponent.Emotion.STARTLE, 2.0f)
        });
    }

    private static int presetAngry(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Angry", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.FRUSTRATION, 3.0f),
            new EmotionWeight(PetComponent.Emotion.DISGUST, 2.0f)
        });
    }

    private static int presetCalm(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Calm", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CONTENT, 3.0f),
            new EmotionWeight(PetComponent.Emotion.LAGOM, 2.5f),
            new EmotionWeight(PetComponent.Emotion.WABI_SABI, 1.8f),
            new EmotionWeight(PetComponent.Emotion.RELIEF, 1.2f)
        });
    }

    private static int presetPlayful(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Playful", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.KEFI, 2.5f),
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 2.0f),
            new EmotionWeight(PetComponent.Emotion.SOBREMESA, 1.5f)
        });
    }

    private static int presetProtective(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Protective", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.GUARDIAN_VIGIL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.QUERECIA, 2.2f),
            new EmotionWeight(PetComponent.Emotion.UBUNTU, 1.8f),
            new EmotionWeight(PetComponent.Emotion.SOBREMESA, 1.4f)
        });
    }

    private static int presetMelancholy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Melancholy", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.SAUDADE, 3.0f),
            new EmotionWeight(PetComponent.Emotion.HIRAETH, 2.5f),
            new EmotionWeight(PetComponent.Emotion.MONO_NO_AWARE, 2.0f),
            new EmotionWeight(PetComponent.Emotion.FERNWEH, 1.5f),
            new EmotionWeight(PetComponent.Emotion.ENNUI, 1.0f)
        });
    }

    private static int applyEmotionPreset(CommandContext<ServerCommandSource> context, String presetName, EmotionWeight[] emotions) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        // Clear existing emotions first
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            petComp.pushEmotion(emotion, 0.0f);
        }

        // Apply preset emotions
        for (EmotionWeight ew : emotions) {
            petComp.pushEmotion(ew.emotion, ew.weight);
        }
        petComp.updateMood();

        player.sendMessage(Text.literal("Applied ")
            .formatted(Formatting.GREEN)
            .append(Text.literal(presetName).formatted(Formatting.AQUA))
            .append(Text.literal(" emotion preset to pet!").formatted(Formatting.GREEN)), false);

        return 1;
    }

    private static class EmotionWeight {
        final PetComponent.Emotion emotion;
        final float weight;

        EmotionWeight(PetComponent.Emotion emotion, float weight) {
            this.emotion = emotion;
            this.weight = weight;
        }
    }
}
