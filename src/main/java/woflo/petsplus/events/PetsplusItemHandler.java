package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import woflo.petsplus.items.PetsplusItemUtils;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.api.PetRole;

/**
 * Handles interactions with special Pets+ items that have data components.
 * Following Fabric 1.21+ data component best practices.
 */
public class PetsplusItemHandler {
    
    public static void register() {
        UseEntityCallback.EVENT.register(PetsplusItemHandler::onUseEntity);
    }
    
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, 
                                          net.minecraft.entity.Entity entity, EntityHitResult hitResult) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        
        if (!(entity instanceof MobEntity mob)) {
            return ActionResult.PASS;
        }
        
        PetComponent petComp = PetComponent.get(mob);
        if (petComp == null || !petComp.isOwnedBy(serverPlayer)) {
            return ActionResult.PASS;
        }
        
        ItemStack heldItem = player.getStackInHand(hand);
        
        // Handle respec token usage
        if (PetsplusItemUtils.isRespecToken(heldItem)) {
            return handleRespecToken(serverPlayer, mob, petComp, heldItem, hand);
        }
        
        // Handle linked whistle creation (when using name tag on pet)
        if (heldItem.isOf(net.minecraft.item.Items.NAME_TAG) && 
            PetsplusItemUtils.hasPetMetadata(heldItem)) {
            return handlePetMetadataTag(serverPlayer, mob, petComp, heldItem, hand);
        }
        
        return ActionResult.PASS;
    }
    
    private static ActionResult handleRespecToken(ServerPlayerEntity player, MobEntity pet, 
                                                PetComponent petComp, ItemStack tokenStack, Hand hand) {
        var respecData = PetsplusItemUtils.getRespecData(tokenStack);
        if (respecData == null || !respecData.enabled()) {
            return ActionResult.PASS;
        }
        
        // Check if pet has a role to respec
        if (petComp.getRole() == null) {
            player.sendMessage(Text.literal("This pet doesn't have a role to respec!")
                .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }
        
        // Reset pet abilities if targeting specific role
        if (respecData.targetRole().isPresent()) {
            String targetRoleKey = respecData.targetRole().get();
            
            try {
                PetRole targetRole = PetRole.valueOf(targetRoleKey.toUpperCase());
                petComp.setRole(targetRole);
                
                player.sendMessage(Text.literal("Pet role changed to " + targetRole.getDisplayName() + "!")
                    .formatted(Formatting.GREEN), false);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Text.literal("Invalid role: " + targetRoleKey)
                    .formatted(Formatting.RED), false);
                return ActionResult.SUCCESS;
            }
        } else {
            // General respec - clear abilities data but keep role
            petComp.resetAbilities();
            
            player.sendMessage(Text.literal("Pet abilities have been reset!")
                .formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("Your pet can now learn new abilities.")
                .formatted(Formatting.YELLOW), false);
        }
        
        // Consume the token
        if (!player.getAbilities().creativeMode) {
            tokenStack.decrement(1);
        }
        
        // Play sound effect
        player.getWorld().playSound(null, pet.getBlockPos(),
            net.minecraft.sound.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        
        // Spawn particles
        if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                pet.getX(), pet.getY() + pet.getHeight() / 2, pet.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);
        }
        
        return ActionResult.SUCCESS;
    }
    
    private static ActionResult handlePetMetadataTag(ServerPlayerEntity player, MobEntity pet, 
                                                   PetComponent petComp, ItemStack tagStack, Hand hand) {
        var metadata = PetsplusItemUtils.getPetMetadata(tagStack);
        if (metadata == null) {
            return ActionResult.PASS;
        }
        
        // Apply the custom display name
        if (metadata.customDisplayName().isPresent()) {
            String displayName = metadata.customDisplayName().get();
            pet.setCustomName(Text.literal(displayName));
            pet.setCustomNameVisible(true);
            
            // Update pet component with metadata
            petComp.setPetMetadata(metadata);
            
            player.sendMessage(Text.literal("Pet renamed to: " + displayName)
                .formatted(Formatting.GREEN), false);
            
            if (metadata.isSpecial()) {
                player.sendMessage(Text.literal("⭐ This pet is now marked as special! ⭐")
                    .formatted(Formatting.GOLD), false);
                
                // Special pets get a small stat boost
                petComp.addBondStrength(100);
            }
        }
        
        // Consume the name tag
        if (!player.getAbilities().creativeMode) {
            tagStack.decrement(1);
        }
        
        // Play sound effect
        player.getWorld().playSound(null, pet.getBlockPos(),
            net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        
        return ActionResult.SUCCESS;
    }
    
    /**
     * Creates a respec token and gives it to the player (for testing/admin commands).
     */
    public static void giveRespecToken(ServerPlayerEntity player) {
        ItemStack token = PetsplusItemUtils.createRespecToken();
        
        if (player.getInventory().insertStack(token)) {
            player.sendMessage(Text.literal("Respec token added to inventory!")
                .formatted(Formatting.GREEN), false);
        } else {
            player.dropItem(token, false);
            player.sendMessage(Text.literal("Respec token dropped at your feet!")
                .formatted(Formatting.YELLOW), false);
        }
    }
    
    /**
     * Creates a linked whistle for a specific pet and gives it to the player.
     */
    public static void giveLinkedWhistle(ServerPlayerEntity player, MobEntity pet) {
        ItemStack whistle = PetsplusItemUtils.createLinkedWhistle(pet);
        
        if (player.getInventory().insertStack(whistle)) {
            player.sendMessage(Text.literal("Linked whistle added to inventory!")
                .formatted(Formatting.GREEN), false);
        } else {
            player.dropItem(whistle, false);
            player.sendMessage(Text.literal("Linked whistle dropped at your feet!")
                .formatted(Formatting.YELLOW), false);
        }
    }
    
    /**
     * Creates a pet metadata tag and gives it to the player.
     */
    public static void givePetMetadataTag(ServerPlayerEntity player, String displayName, boolean isSpecial) {
        ItemStack tag = PetsplusItemUtils.createPetMetadataTag(displayName, isSpecial);
        
        if (player.getInventory().insertStack(tag)) {
            player.sendMessage(Text.literal("Pet metadata tag added to inventory!")
                .formatted(Formatting.GREEN), false);
        } else {
            player.dropItem(tag, false);
            player.sendMessage(Text.literal("Pet metadata tag dropped at your feet!")
                .formatted(Formatting.YELLOW), false);
        }
    }
}