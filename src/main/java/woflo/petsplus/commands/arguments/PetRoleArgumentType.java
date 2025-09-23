package woflo.petsplus.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom argument type for pet roles following Fabric documentation patterns.
 * Provides validation and suggestions for role identifiers.
 */
public class PetRoleArgumentType implements ArgumentType<Identifier> {
    private static final DynamicCommandExceptionType UNKNOWN_ROLE = new DynamicCommandExceptionType(
        name -> Text.literal("Unknown pet role: " + name)
    );

    @Override
    public Identifier parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String input = reader.readString();

        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();

        Identifier identifier = PetRoleType.normalizeId(input);
        if (identifier != null && registry.get(identifier) != null) {
            return identifier;
        }

        reader.setCursor(start);
        throw UNKNOWN_ROLE.createWithContext(reader, input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        boolean namespaced = builder.getRemaining().contains(":");

        for (PetRoleType type : registry) {
            Identifier id = type.id();
            Text tooltip = Text.translatable(type.translationKey());
            if (!namespaced) {
                builder.suggest(id.getPath(), tooltip);
            }
            builder.suggest(id.toString(), tooltip);
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of(Petsplus.MOD_ID + ":guardian", "guardian");
    }

    public static PetRoleArgumentType petRole() {
        return new PetRoleArgumentType();
    }

    public static Identifier getRoleId(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, Identifier.class);
    }

    public static void register() {
        net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry.registerArgumentType(
            Identifier.of(Petsplus.MOD_ID, "pet_role"),
            PetRoleArgumentType.class,
            ConstantArgumentSerializer.of(PetRoleArgumentType::new)
        );
    }
}
