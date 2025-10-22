package woflo.petsplus.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.component.type.TooltipDisplayComponent;

import java.util.function.Consumer;

/**
 * Special book item that displays detailed information about a pet when right-clicked on them.
 * The item itself is reusable and doesn't store pet-specific data - all information is
 * queried from the pet's PetComponent at the time of use.
 */
public class PetCompendium extends Item {
    
    public PetCompendium(Settings settings) {
        super(settings);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, 
                             TooltipDisplayComponent displayComponent,
                             Consumer<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, displayComponent, tooltip, type);
        
        tooltip.accept(Text.empty());
        tooltip.accept(Text.literal("Right-click a pet to view their").formatted(Formatting.GRAY));
        tooltip.accept(Text.literal("journey, stats, and history.").formatted(Formatting.GRAY));
        tooltip.accept(Text.empty());
        tooltip.accept(Text.literal("- Life Statistics").formatted(Formatting.AQUA));
        tooltip.accept(Text.literal("- Role & Nature").formatted(Formatting.AQUA));
        tooltip.accept(Text.literal("- Combat Record").formatted(Formatting.AQUA));
        tooltip.accept(Text.literal("- Recent Journal").formatted(Formatting.AQUA));
        tooltip.accept(Text.empty());
        tooltip.accept(Text.literal("Tip: ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Right-click in air to reopen").formatted(Formatting.DARK_GRAY)));
        tooltip.accept(Text.literal("  last viewed pet").formatted(Formatting.DARK_GRAY));
    }
}
