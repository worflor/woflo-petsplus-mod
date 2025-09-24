package woflo.petsplus.commands.suggestions;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced suggestion providers following Fabric documentation patterns.
 * Provides context-aware suggestions for commands.
 */
public class PetsSuggestionProviders {
    
    /**
     * Suggests pet roles with intelligent filtering.
     * Shows only available roles or roles compatible with specific pets.
     */
    public static final SuggestionProvider<ServerCommandSource> SMART_ROLE_SUGGESTIONS = 
        (context, builder) -> {
            try {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                
                // Check if we're suggesting for a specific pet
                String petName = getOptionalArgument(context, "pet_name");
                if (petName != null) {
                    MobEntity targetPet = findPetByName(player, petName);
                    if (targetPet != null) {
                        // Suggest all roles for now - could be enhanced later
                        return suggestAllRolesWithDescriptions(builder);
                    }
                }
                
                // Default: suggest all available roles with descriptions
                return suggestAllRolesWithDescriptions(builder);
                
            } catch (CommandSyntaxException e) {
                return suggestAllRolesWithDescriptions(builder);
            }
        };
    
    /**
     * Suggests pets owned by the player, with smart filtering.
     * Prioritizes pets that can receive commands or need attention.
     */
    public static final SuggestionProvider<ServerCommandSource> SMART_PET_SUGGESTIONS = 
        (context, builder) -> {
            try {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                
                // Check command context to filter suggestions
                String command = getCommandName(context);
                
                List<String> petSuggestions = switch (command) {
                    case "role" -> getSuggestionsForRoleCommand(player);
                    case "tribute" -> getSuggestionsForTributeCommand(player);
                    case "info" -> getSuggestionsForInfoCommand(player);
                    default -> getAllPetSuggestions(player);
                };
                
                return CommandSource.suggestMatching(petSuggestions, builder);
                
            } catch (CommandSyntaxException e) {
                return Suggestions.empty();
            }
        };
    
    /**
     * Suggests tribute items based on pet level and current needs.
     */
    public static final SuggestionProvider<ServerCommandSource> TRIBUTE_ITEM_SUGGESTIONS = 
        (context, builder) -> {
            try {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                
                String petName = getOptionalArgument(context, "pet_name");
                if (petName != null) {
                    MobEntity pet = findPetByName(player, petName);
                    if (pet != null) {
                        return suggestTributeItemsForPet(pet, builder);
                    }
                }
                
                // Default: suggest common tribute items
                return suggestCommonTributeItems(builder);
                
            } catch (CommandSyntaxException e) {
                return Suggestions.empty();
            }
        };
    
    /**
     * Suggests ability names for pets with their current role.
     */
    public static final SuggestionProvider<ServerCommandSource> ABILITY_SUGGESTIONS = 
        (context, builder) -> {
            try {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                
                String petName = getOptionalArgument(context, "pet_name");
                if (petName != null) {
                    MobEntity pet = findPetByName(player, petName);
                    if (pet != null) {
                        return suggestAbilitiesForPet(pet, builder);
                    }
                }
                
                // Default: suggest common abilities
                return suggestCommonAbilities(builder);
                
            } catch (CommandSyntaxException e) {
                return Suggestions.empty();
            }
        };
    
    // Helper methods
    
    private static CompletableFuture<Suggestions> suggestAllRolesWithDescriptions(SuggestionsBuilder builder) {
        for (PetRoleType type : PetsPlusRegistries.petRoleTypeRegistry()) {
            String displayName = Text.translatable(type.translationKey()).getString();
            if (displayName.equals(type.translationKey())) {
                displayName = PetRoleType.fallbackName(type.id());
            }
            Text tooltip = Text.literal(displayName);
            // Only suggest the path part (e.g., "guardian") to avoid duplicates
            builder.suggest(type.id().getPath(), tooltip);
        }
        return builder.buildFuture();
    }
    
    private static List<String> getSuggestionsForRoleCommand(ServerPlayerEntity player) {
        // For role commands, prioritize pets without roles or need role changes
        return findNearbyOwnedPets(player, 16.0).stream()
            .filter(pet -> {
                PetComponent petComp = PetComponent.get(pet);
                return petComp != null; // Show all pets for role assignment
            })
            .map(PetsSuggestionProviders::getPetDisplayName)
            .collect(Collectors.toList());
    }
    
    private static List<String> getSuggestionsForTributeCommand(ServerPlayerEntity player) {
        // For tribute commands, prioritize pets that can accept tributes
        return findNearbyOwnedPets(player, 16.0).stream()
            .filter(pet -> {
                PetComponent petComp = PetComponent.get(pet);
                if (petComp == null) {
                    return false;
                }
                Identifier roleId = petComp.getRoleId();
                return PetsPlusRegistries.petRoleTypeRegistry().get(roleId) != null
                    && petComp.getLevel() < 100;
            })
            .map(PetsSuggestionProviders::getPetDisplayName)
            .collect(Collectors.toList());
    }
    
    private static List<String> getSuggestionsForInfoCommand(ServerPlayerEntity player) {
        // For info commands, show all pets
        return getAllPetSuggestions(player);
    }
    
    private static List<String> getAllPetSuggestions(ServerPlayerEntity player) {
        return findNearbyOwnedPets(player, 16.0).stream()
            .map(PetsSuggestionProviders::getPetDisplayName)
            .collect(Collectors.toList());
    }
    
    private static CompletableFuture<Suggestions> suggestTributeItemsForPet(MobEntity pet, SuggestionsBuilder builder) {
        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null) return Suggestions.empty();
        
        int nextLevel = petComp.getLevel() + 1;
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        PetRoleType roleType = petComp.getRoleType();
        if (roleType == null) {
            return builder.buildFuture();
        }

        if (!roleType.xpCurve().tributeMilestones().contains(nextLevel)) {
            return builder.buildFuture();
        }

        Identifier tributeId = config.resolveTributeItem(roleType, nextLevel);
        if (tributeId != null) {
            builder.suggest(tributeId.toString());
        }

        return builder.buildFuture();
    }
    
    private static CompletableFuture<Suggestions> suggestAbilitiesForPet(MobEntity pet, SuggestionsBuilder builder) {
        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null) {
            return suggestCommonAbilities(builder);
        }

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(petComp.getRoleId());
        if (roleType == null) {
            return suggestCommonAbilities(builder);
        }

        for (Identifier abilityId : roleType.defaultAbilities()) {
            builder.suggest(abilityId.toString());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCommonAbilities(SuggestionsBuilder builder) {
        PetsPlusRegistries.abilityTypeRegistry().getIds()
            .forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    }
    
    private static CompletableFuture<Suggestions> suggestCommonTributeItems(SuggestionsBuilder builder) {
        // Suggest common tribute items
        builder.suggest("minecraft:diamond");
        builder.suggest("minecraft:emerald");
        builder.suggest("minecraft:gold_ingot");
        builder.suggest("minecraft:iron_ingot");
        
        return builder.buildFuture();
    }
    
    private static String getPetDisplayName(MobEntity pet) {
        if (pet.hasCustomName()) {
            return pet.getCustomName().getString();
        }
        return pet.getType().getName().getString();
    }
    
    private static List<MobEntity> findNearbyOwnedPets(ServerPlayerEntity owner, double radius) {
        ServerWorld world = owner.getWorld();
        Box searchArea = owner.getBoundingBox().expand(radius);
        
        return world.getEntitiesByClass(MobEntity.class, searchArea, entity -> {
            PetComponent petComp = PetComponent.get(entity);
            return petComp != null && petComp.isOwnedBy(owner) && entity.isAlive();
        });
    }
    
    private static MobEntity findPetByName(ServerPlayerEntity owner, String name) {
        return findNearbyOwnedPets(owner, 16.0).stream()
            .filter(pet -> {
                String petName = getPetDisplayName(pet);
                return petName.equalsIgnoreCase(name);
            })
            .findFirst()
            .orElse(null);
    }
    
    private static String getOptionalArgument(CommandContext<ServerCommandSource> context, String name) {
        try {
            return context.getArgument(name, String.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private static String getCommandName(CommandContext<ServerCommandSource> context) {
        // Extract the command name from the input
        String input = context.getInput();
        String[] parts = input.split(" ");
        if (parts.length > 1) {
            return parts[1]; // Return the subcommand (role, tribute, info, etc.)
        }
        return "";
    }
}