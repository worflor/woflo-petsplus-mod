package woflo.petsplus.items;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.component.PetsplusComponents;

/**
 * Item representation of a specific ability token.
 *
 * <p>The token stores an ability identifier in NBT and exposes helper methods
 * for creating consistent stacks across commands, loot tables, and gameplay.
 */
public class AbilityTokenItem extends Item {

    public AbilityTokenItem(Settings settings) {
        super(settings);
    }

    /**
     * Create a new ability token stack for the supplied ability type.
     */
    public static ItemStack create(AbilityType type) {
        if (type == null) {
            return new ItemStack(PetsplusItems.ABILITY_TOKEN);
        }
        return create(type.id());
    }

    /**
     * Create a new ability token stack for the supplied ability identifier.
     */
    public static ItemStack create(Identifier abilityId) {
        ItemStack stack = new ItemStack(PetsplusItems.ABILITY_TOKEN);
        setAbility(stack, abilityId);
        return stack;
    }

    /**
     * Persist the ability identifier onto the stack.
     */
    public static void setAbility(ItemStack stack, Identifier abilityId) {
        if (stack.isEmpty() || abilityId == null) {
            return;
        }
        stack.set(PetsplusComponents.ABILITY_TOKEN, abilityId);
    }

    /**
     * Read the stored ability identifier, if present.
     */
    public static Optional<Identifier> getAbilityId(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Identifier id = stack.get(PetsplusComponents.ABILITY_TOKEN);
        return Optional.ofNullable(id);
    }

    /**
     * Resolve a display name for the ability represented by the provided identifier.
     */
    public static MutableText abilityDisplayName(@Nullable Identifier abilityId) {
        if (abilityId == null) {
            return Text.literal("Unknown Ability");
        }
        return Text.literal(formatAbilityName(abilityId));
    }

    private static String formatAbilityName(Identifier abilityId) {
        String path = abilityId.getPath();
        if (path == null || path.isEmpty()) {
            return abilityId.toString();
        }
        return Arrays.stream(path.split("[/_]"))
            .flatMap(segment -> Stream.of(segment.replace('-', ' ').split("\\s+")))
            .filter(token -> !token.isEmpty())
            .map(token -> token.length() == 1
                ? token.toUpperCase(Locale.ROOT)
                : Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(" "));
    }

    @Override
    public Text getName(ItemStack stack) {
        Optional<Identifier> abilityId = getAbilityId(stack);
        if (abilityId.isEmpty()) {
            return super.getName(stack);
        }
        Identifier id = abilityId.get();
        AbilityType type = PetsPlusRegistries.abilityTypeRegistry().get(id);
        if (type == null) {
            return Text.translatable("item." + Petsplus.MOD_ID + ".ability_token.unknown", id.toString());
        }
        return Text.translatable(
            "item." + Petsplus.MOD_ID + ".ability_token.named",
            abilityDisplayName(id)
        );
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return getAbilityId(stack).isPresent();
    }

    @Override
    public void appendTooltip(ItemStack stack,
                              Item.TooltipContext context,
                              TooltipDisplayComponent displayComponent,
                              Consumer<Text> tooltip,
                              TooltipType type) {
        super.appendTooltip(stack, context, displayComponent, tooltip, type);

        Optional<Identifier> abilityIdOpt = getAbilityId(stack);
        if (abilityIdOpt.isEmpty()) {
            tooltip.accept(Text.translatable("item." + Petsplus.MOD_ID + ".ability_token.tooltip.unassigned")
                .formatted(Formatting.RED));
            return;
        }

        Identifier abilityId = abilityIdOpt.get();
        AbilityType abilityType = PetsPlusRegistries.abilityTypeRegistry().get(abilityId);

        MutableText abilityName = abilityDisplayName(abilityId).copy().formatted(Formatting.GOLD);
        tooltip.accept(Text.translatable(
            "item." + Petsplus.MOD_ID + ".ability_token.tooltip.ability",
            abilityName
        ).formatted(Formatting.GRAY));

        if (abilityType != null) {
            String description = abilityType.description();
            if (description != null) {
                String sanitized = description.trim();
                if (!sanitized.isEmpty()
                    && !PetsPlusRegistries.PLACEHOLDER_ABILITY_DESCRIPTION.equals(sanitized)) {
                    tooltip.accept(Text.literal(sanitized).formatted(Formatting.DARK_GREEN, Formatting.ITALIC));
                }
            }
        } else {
            tooltip.accept(Text.translatable(
                "item." + Petsplus.MOD_ID + ".ability_token.tooltip.unregistered",
                abilityId.toString()
            ).formatted(Formatting.RED));
        }

        tooltip.accept(Text.translatable(
            "item." + Petsplus.MOD_ID + ".ability_token.tooltip.id",
            abilityId.toString()
        ).formatted(Formatting.DARK_GRAY));
    }
}
