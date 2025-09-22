package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.advancement.AdvancementManager;

/**
 * Handles pet death events, including level/XP reset and item dropping.
 */
public class PetDeathHandler {
    
    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register(PetDeathHandler::onEntityDeath);
    }
    
    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof MobEntity mobEntity)) return;
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) return;
        
        PetComponent petComp = PetComponent.get(mobEntity);
        if (petComp == null) return; // Not a pet
        
        // Handle pet death
        handlePetDeath(mobEntity, petComp, serverWorld);
    }
    
    private static void handlePetDeath(MobEntity pet, PetComponent petComp, ServerWorld world) {
        // Store info before reset
        int lostLevel = petComp.getLevel();
        int lostXp = petComp.getExperience();
        
        // Reset pet's level and XP
        petComp.resetLevel();
        
        // Drop any held items (this would need to be implemented based on your pet inventory system)
        dropPetItems(pet, world);
        
        // Notify owner if nearby
        if (petComp.getOwner() != null && petComp.getOwner().getWorld() == world) {
            double distance = petComp.getOwner().distanceTo(pet);
            if (distance <= 64) { // Within 64 blocks
                String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
                petComp.getOwner().sendMessage(
                    net.minecraft.text.Text.of("§c" + petName + " §7died and lost §c" + (lostLevel - 1) + " levels §7and §c" + lostXp + " XP§7!"),
                    false // Chat message
                );

                // Trigger advancement for permanent pet death
                if (petComp.getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                    AdvancementManager.triggerPetPermanentDeath(serverPlayer, pet);
                }
            }
        }
        
        // Remove the pet component since the pet is dead
        PetComponent.remove(pet);
    }
    
    /**
     * Drop any items the pet was carrying.
     * This is a placeholder - the actual implementation would depend on your pet inventory system.
     */
    private static void dropPetItems(MobEntity pet, ServerWorld world) {
        // TODO: Implement pet inventory system and drop items here
        // For now, this is a placeholder that could drop items from a theoretical pet inventory
        
        // Example of what this might look like:
        // PetInventory inventory = PetInventory.get(pet);
        // if (inventory != null) {
        //     for (ItemStack stack : inventory.getStacks()) {
        //         if (!stack.isEmpty()) {
        //             dropItemStack(world, pet.getPos(), stack);
        //         }
        //     }
        //     inventory.clear();
        // }
    }
    
    /**
     * Helper method to drop an item stack at a location.
     */
    @SuppressWarnings("unused") // Will be used when pet inventory system is implemented
    private static void dropItemStack(ServerWorld world, Vec3d pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        
        ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        itemEntity.setPickupDelay(40); // Standard pickup delay
        itemEntity.setVelocity(
            world.random.nextGaussian() * 0.05,
            world.random.nextGaussian() * 0.05 + 0.2,
            world.random.nextGaussian() * 0.05
        );
        world.spawnEntity(itemEntity);
    }
}