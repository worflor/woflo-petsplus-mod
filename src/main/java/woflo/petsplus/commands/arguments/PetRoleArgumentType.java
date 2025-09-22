package woflo.petsplus.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.PetRole;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Custom argument type for pet roles following Fabric documentation patterns.
 * Provides validation and suggestions for role names.
 */
public class PetRoleArgumentType implements ArgumentType<PetRole> {
    
    /**
     * Parse a role from the command input.
     * Supports both display names and internal keys.
     */
    @Override
    public PetRole parse(StringReader reader) throws CommandSyntaxException {
        String input = reader.readString();
        
        // Try direct key match first
        for (PetRole role : PetRole.values()) {
            if (role.getKey().equalsIgnoreCase(input)) {
                return role;
            }
        }
        
        // Try display name match
        for (PetRole role : PetRole.values()) {
            if (role.getDisplayName().equalsIgnoreCase(input)) {
                return role;
            }
        }
        
        // If no match found, throw exception with helpful message
        String availableRoles = Arrays.stream(PetRole.values())
            .map(PetRole::getKey)
            .collect(Collectors.joining(", "));
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()
            .createWithContext(reader, "Unknown role '" + input + "'. Available: " + availableRoles);
    }
    
    /**
     * Provide suggestions for role names.
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
            Arrays.stream(PetRole.values())
                .map(role -> role.getKey().toLowerCase())
                .collect(Collectors.toList()),
            builder
        );
    }
    
    /**
     * Static factory method for creating instances.
     */
    public static PetRoleArgumentType petRole() {
        return new PetRoleArgumentType();
    }
    
    /**
     * Helper method to get the role from a command context.
     */
    public static PetRole getRole(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, PetRole.class);
    }
    
    /**
     * Register this argument type with Fabric.
     * Call this during mod initialization.
     */
    public static void register() {
        net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry.registerArgumentType(
            Identifier.of("petsplus", "pet_role"),
            PetRoleArgumentType.class,
            ConstantArgumentSerializer.of(PetRoleArgumentType::new)
        );
    }
}