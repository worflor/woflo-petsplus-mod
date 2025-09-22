package woflo.petsplus.items;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.component.PetsplusComponents;

/**
 * Utilities for creating and managing special Pets+ items with data components.
 * Following Fabric 1.21+ data component best practices.
 */
public class PetsplusItemUtils {
    
    /**
     * Creates a respec token that allows ability respecialization.
     */
    public static ItemStack createRespecToken() {
        ItemStack stack = new ItemStack(Items.PAPER);
        
        // Set custom name
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
            Text.literal("Pet Respec Token")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Add lore
        stack.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("Allows your pet to reset their abilities")
                    .formatted(Formatting.GRAY),
                Text.literal("Right-click on a pet to use")
                    .formatted(Formatting.YELLOW),
                Text.empty(),
                Text.literal("Single use only")
                    .formatted(Formatting.RED)
            )));
        
        // Set enchantment glint
        stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        
        // Add our custom data component
        stack.set(PetsplusComponents.RESPEC_TOKEN, 
            PetsplusComponents.RespecData.create());
        
        return stack;
    }
    
    /**
     * Creates a respec token for a specific role.
     */
    public static ItemStack createRoleRespecToken(String roleKey) {
        ItemStack stack = createRespecToken();
        
        // Update name to be role-specific
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
            Text.literal("Pet Respec Token (" + roleKey + ")")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Update component data
        stack.set(PetsplusComponents.RESPEC_TOKEN, 
            PetsplusComponents.RespecData.forRole(roleKey));
        
        return stack;
    }
    
    /**
     * Creates a linked whistle for pet recall.
     */
    public static ItemStack createLinkedWhistle(MobEntity pet) {
        ItemStack stack = new ItemStack(Items.GOAT_HORN);
        
        String petName = pet.hasCustomName() ? 
            pet.getCustomName().getString() : 
            pet.getType().getName().getString();
        
        // Set custom name
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
            Text.literal("Linked Whistle: " + petName)
                .formatted(Formatting.AQUA, Formatting.BOLD));
        
        // Add lore
        stack.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("Recalls your bonded pet from anywhere")
                    .formatted(Formatting.GRAY),
                Text.literal("Right-click to summon " + petName)
                    .formatted(Formatting.YELLOW),
                Text.empty(),
                Text.literal("Uses remaining: 10")
                    .formatted(Formatting.GREEN),
                Text.literal("Cooldown: 5 minutes")
                    .formatted(Formatting.RED)
            )));
        
        // Set enchantment glint
        stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        
        // Add our custom data component
        stack.set(PetsplusComponents.LINKED_WHISTLE, 
            PetsplusComponents.LinkedWhistleData.create(pet.getUuid(), petName));
        
        return stack;
    }
    
    /**
     * Creates an enhanced pet name tag with metadata.
     */
    public static ItemStack createPetMetadataTag(String displayName, boolean isSpecial) {
        ItemStack stack = new ItemStack(Items.NAME_TAG);
        
        // Set custom name
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
            Text.literal(isSpecial ? "Legendary Pet Tag" : "Enhanced Pet Tag")
                .formatted(isSpecial ? Formatting.LIGHT_PURPLE : Formatting.BLUE, Formatting.BOLD));
        
        // Add lore
        java.util.List<Text> loreLines = new java.util.ArrayList<>();
        loreLines.add(Text.literal("Enhanced pet naming system").formatted(Formatting.GRAY));
        loreLines.add(Text.literal("Right-click on a pet to apply").formatted(Formatting.YELLOW));
        loreLines.add(Text.empty());
        loreLines.add(Text.literal("Display Name: " + displayName).formatted(Formatting.WHITE));
        
        if (isSpecial) {
            loreLines.add(Text.literal("⭐ Special Pet ⭐").formatted(Formatting.GOLD));
        }
        
        stack.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(loreLines));
        
        // Set enchantment glint for special pets
        if (isSpecial) {
            stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        
        // Add our custom data component
        stack.set(PetsplusComponents.PET_METADATA, 
            PetsplusComponents.PetMetadata.create()
                .withDisplayName(displayName)
                .withSpecial(isSpecial));
        
        return stack;
    }
    
    /**
     * Checks if an item stack is a valid respec token.
     */
    public static boolean isRespecToken(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.PAPER)) {
            return false;
        }
        
        var respecData = stack.get(PetsplusComponents.RESPEC_TOKEN);
        return respecData != null && respecData.enabled();
    }
    
    /**
     * Checks if an item stack is a linked whistle.
     */
    public static boolean isLinkedWhistle(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.GOAT_HORN)) {
            return false;
        }
        
        var whistleData = stack.get(PetsplusComponents.LINKED_WHISTLE);
        return whistleData != null && whistleData.linkedPetUuid().isPresent();
    }
    
    /**
     * Checks if an item stack has pet metadata.
     */
    public static boolean hasPetMetadata(ItemStack stack) {
        return stack.get(PetsplusComponents.PET_METADATA) != null;
    }
    
    /**
     * Gets the respec data from an item stack.
     */
    public static PetsplusComponents.RespecData getRespecData(ItemStack stack) {
        return stack.get(PetsplusComponents.RESPEC_TOKEN);
    }
    
    /**
     * Gets the linked whistle data from an item stack.
     */
    public static PetsplusComponents.LinkedWhistleData getWhistleData(ItemStack stack) {
        return stack.get(PetsplusComponents.LINKED_WHISTLE);
    }
    
    /**
     * Gets the pet metadata from an item stack.
     */
    public static PetsplusComponents.PetMetadata getPetMetadata(ItemStack stack) {
        return stack.get(PetsplusComponents.PET_METADATA);
    }
    
    /**
     * Updates the whistle's remaining uses and cooldown.
     */
    public static ItemStack updateWhistleUses(ItemStack stack, long cooldownExpiry) {
        var whistleData = getWhistleData(stack);
        if (whistleData == null) {
            return stack;
        }
        
        var newData = whistleData.useOnce().withCooldown(cooldownExpiry);
        stack.set(PetsplusComponents.LINKED_WHISTLE, newData);
        
        // Update lore to reflect new uses count
        if (newData.usesRemaining() <= 0) {
            stack.set(net.minecraft.component.DataComponentTypes.LORE, 
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                    Text.literal("This whistle has been depleted")
                        .formatted(Formatting.RED),
                    Text.literal("No uses remaining")
                        .formatted(Formatting.DARK_RED)
                )));
        } else {
            // Update the uses remaining line in lore
            var petName = whistleData.petName().orElse("Unknown Pet");
            stack.set(net.minecraft.component.DataComponentTypes.LORE, 
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                    Text.literal("Recalls your bonded pet from anywhere")
                        .formatted(Formatting.GRAY),
                    Text.literal("Right-click to summon " + petName)
                        .formatted(Formatting.YELLOW),
                    Text.empty(),
                    Text.literal("Uses remaining: " + newData.usesRemaining())
                        .formatted(Formatting.GREEN),
                    Text.literal("Cooldown: 5 minutes")
                        .formatted(Formatting.RED)
                )));
        }
        
        return stack;
    }
    
    /**
     * Creates an item stack with debugging information for development.
     */
    public static ItemStack createDebugInfoStick() {
        ItemStack stack = new ItemStack(Items.STICK);
        
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
            Text.literal("Pet Debug Stick")
                .formatted(Formatting.DARK_RED, Formatting.BOLD));
        
        stack.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("Development tool")
                    .formatted(Formatting.GRAY),
                Text.literal("Right-click pet for debug info")
                    .formatted(Formatting.YELLOW)
            )));
        
        stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        
        return stack;
    }
}