package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.items.PetsplusItems;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.PetCompendiumDataExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles right-click interactions with the Pet Compendium item.
 * When a player right-clicks a pet while holding the compendium,
 * displays detailed information about that pet in chat.
 * 
 * Also remembers the last viewed pet so players can monitor stats live
 * by right-clicking in air to reopen the compendium.
 */
public class PetCompendiumHandler {
    
    private static final Map<UUID, Long> LAST_USE_TIME = new HashMap<>();
    private static final Map<UUID, UUID> LAST_VIEWED_PET = new HashMap<>(); // Player UUID -> Pet UUID
    private static final long USE_COOLDOWN_TICKS = 20; // 1 second
    
    private PetCompendiumHandler() {}
    
    public static void register() {
        UseEntityCallback.EVENT.register(PetCompendiumHandler::onUseEntity);
        
        // Register right-click in air to reopen last viewed pet
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(
            (player, world, hand) -> {
                if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                    ItemStack stack = player.getStackInHand(hand);
                    if (stack.isOf(PetsplusItems.PET_COMPENDIUM)) {
                        return reopenLastViewedPet(serverPlayer, hand);
                    }
                }
                return ActionResult.PASS;
            }
        );
        
        Petsplus.LOGGER.info("Pet Compendium handler registered");
    }
    
    /**
     * Reopens the compendium for the last viewed pet.
     * Handles edge cases: pet death, dimension changes, unloaded chunks.
     */
    private static ActionResult reopenLastViewedPet(ServerPlayerEntity player, Hand hand) {
        UUID lastPetUuid = LAST_VIEWED_PET.get(player.getUuid());

        if (lastPetUuid == null) {
            player.sendMessage(
                Text.literal("Notice: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("No pet has been viewed yet.").formatted(Formatting.GRAY))
                    .append(Text.literal(" Right-click a pet to start tracking.").formatted(Formatting.DARK_GRAY)),
                true // Action bar
            );
            return ActionResult.PASS;
        }
        
        // Cooldown check
        long currentTime = player.getEntityWorld().getTime();
        Long lastUse = LAST_USE_TIME.get(player.getUuid());
        if (lastUse != null && currentTime - lastUse < USE_COOLDOWN_TICKS) {
            return ActionResult.PASS;
        }
        
        // Try to find the pet entity in the player's world
        Entity entity = player.getEntityWorld().getEntity(lastPetUuid);
        
        if (entity == null) {
            // Pet not in current world (different dimension, unloaded, or dead)
            player.sendMessage(
                Text.literal("Warning: ").formatted(Formatting.GOLD)
                    .append(Text.literal("Last viewed pet is not accessible.").formatted(Formatting.YELLOW))
                    .append(Text.literal(" (Different dimension or unloaded)").formatted(Formatting.DARK_GRAY)),
                true
            );
            return ActionResult.FAIL;
        }
        
        if (!(entity instanceof MobEntity mob)) {
            // Edge case: UUID collision or entity type changed somehow
            LAST_VIEWED_PET.remove(player.getUuid());
            player.sendMessage(
                Text.literal("Warning: ").formatted(Formatting.RED)
                    .append(Text.literal("Last viewed pet is no longer valid.").formatted(Formatting.GRAY)),
                true
            );
            return ActionResult.FAIL;
        }
        
        PetComponent petComponent = PetComponent.get(mob);
        if (petComponent == null) {
            // Pet component was removed or entity is no longer a pet
            LAST_VIEWED_PET.remove(player.getUuid());
            player.sendMessage(
                Text.literal("Warning: ").formatted(Formatting.RED)
                    .append(Text.literal("Entity is no longer a pet.").formatted(Formatting.GRAY)),
                true
            );
            return ActionResult.FAIL;
        }
        
        if (!mob.isAlive()) {
            // Pet is dead
            LAST_VIEWED_PET.remove(player.getUuid());
            player.sendMessage(
                Text.literal("Memorial: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("Your pet has passed away.").formatted(Formatting.GRAY)),
                true
            );
            return ActionResult.FAIL;
        }
        
        // Success! Reopen the compendium
        LAST_USE_TIME.put(player.getUuid(), currentTime);
        openCompendiumScreen(player, mob, petComponent);
        
        // Visual and audio feedback
        Hand swingHand = hand != null ? hand : Hand.MAIN_HAND;
        player.swingHand(swingHand, true);
        player.getEntityWorld().playSound(null, player.getBlockPos(), 
            SoundEvents.ITEM_BOOK_PAGE_TURN, 
            SoundCategory.PLAYERS, 0.8f, 1.2f); // Slightly higher pitch for "refresh"
        
        return ActionResult.SUCCESS;
    }
    
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, 
                                           Entity entity, EntityHitResult hitResult) {
        // Only handle server-side
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        
        // Check if player is holding Pet Compendium
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(PetsplusItems.PET_COMPENDIUM)) {
            return ActionResult.PASS;
        }
        
        // Check if entity is a mob
        if (!(entity instanceof MobEntity mob)) {
            return ActionResult.PASS;
        }
        
        // Check if mob is a pet (has PetComponent)
        PetComponent petComponent = PetComponent.get(mob);
        if (petComponent == null) {
            return ActionResult.PASS;
        }
        
        // Cooldown check to prevent spam
        if (player instanceof ServerPlayerEntity serverPlayer) {
            long currentTime = world.getTime();
            Long lastUse = LAST_USE_TIME.get(player.getUuid());
            
            if (lastUse != null && currentTime - lastUse < USE_COOLDOWN_TICKS) {
                return ActionResult.PASS;
            }
            
            LAST_USE_TIME.put(player.getUuid(), currentTime);
            
            // Remember this pet for future quick access
            LAST_VIEWED_PET.put(player.getUuid(), mob.getUuid());
            
            openCompendiumScreen(serverPlayer, mob, petComponent);
        }
        
        // Visual and audio feedback
        player.swingHand(hand, true);
        world.playSound(null, player.getBlockPos(), 
            SoundEvents.ITEM_BOOK_PAGE_TURN, 
            SoundCategory.PLAYERS, 0.8f, 1.0f);
        
        return ActionResult.SUCCESS;
    }
    
    private static void openCompendiumScreen(ServerPlayerEntity player, MobEntity pet, PetComponent pc) {
        long currentTick = player.getEntityWorld().getTime();
        
        // Open the actual written book GUI
        woflo.petsplus.ui.CompendiumBookBuilder.openCompendium(player, pet, pc, currentTick);
    }
}



