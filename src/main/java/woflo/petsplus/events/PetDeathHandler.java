package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.items.ProofOfExistence;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundGearSwapManager;

/**
 * Handles permanent pet death - pets that die are gone forever with no recovery.
 * Drops a "Proof of Existence" memorial item as a sentimental memento.
 * NOTE: Cursed One pets have special resurrection mechanics handled by CursedOneResurrection.java
 * This handler only processes pets that actually reach the death event (after all prevention mechanics).
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
        
        // All pets that reach this point die permanently
        // (Cursed One resurrection prevention is handled in CursedOneResurrection.java)
        handlePermanentPetDeath(mobEntity, petComp, serverWorld, damageSource);
    }

    /**
     * Handle permanent pet death - the pet is gone forever.
     */
    private static void handlePermanentPetDeath(MobEntity pet, PetComponent petComp, ServerWorld world,
                                                DamageSource damageSource) {
        // Store info before removal
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
        int petLevel = petComp.getLevel();
        var owner = petComp.getOwner();
        ServerPlayerEntity serverOwner = owner instanceof ServerPlayerEntity ? (ServerPlayerEntity) owner : null;

        TriggerContext context = new TriggerContext(world, pet, serverOwner, "on_pet_death")
            .withData("death_burst_reason", "permadeath");
        if (damageSource != null) {
            context.withData("damage_source", damageSource);
        }
        AbilityManager.triggerAbilities(pet, context);

        if (petComp.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
            EnchantmentBoundGearSwapManager.dropStoredGear(pet, petComp);
        }

        // Drop the Proof of Existence memorial item
        ProofOfExistence.dropMemorial(pet, petComp, world);

        // Notify owner if nearby and alive
        if (serverOwner != null && !serverOwner.isDead() && serverOwner.getWorld() == world) {
            double distance = serverOwner.distanceTo(pet);
            if (distance <= 64) { // Within 64 blocks

                // Special message for Cursed One pets
                if (petComp.hasRole(PetRoleType.CURSED_ONE)) {
                    serverOwner.sendMessage(
                        Text.literal("§4" + petName + " §7has been consumed by the curse. ")
                            .append(Text.literal("The dark bond is severed forever. ").formatted(Formatting.DARK_RED))
                            .append(Text.literal("They dropped a ").formatted(Formatting.GRAY))
                            .append(Text.literal("Proof of Existence").formatted(Formatting.YELLOW))
                            .append(Text.literal(" as their final essence.").formatted(Formatting.GRAY)),
                        false
                    );
                } else {
                    serverOwner.sendMessage(
                        Text.literal("§c" + petName + " §7has died permanently. ")
                            .append(Text.literal("All progress lost. ").formatted(Formatting.RED))
                            .append(Text.literal("They dropped a ").formatted(Formatting.GRAY))
                            .append(Text.literal("Proof of Existence").formatted(Formatting.YELLOW))
                            .append(Text.literal(" as a memorial.").formatted(Formatting.GRAY)),
                        false
                    );
                }

                // Trigger advancement criterion for permanent pet death
                AdvancementCriteriaRegistry.PET_DEATH.trigger(serverOwner, petLevel, true);
            }
        }
        
        // Remove the pet component (marks for cleanup)
        PetComponent.remove(pet);
        
        // Remove the pet entity from the world permanently
        pet.discard(); // This removes the entity completely
    }
}