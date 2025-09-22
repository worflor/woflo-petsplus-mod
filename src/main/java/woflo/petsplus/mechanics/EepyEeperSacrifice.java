package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Implements Eepy Eeper sacrifice mechanic.
 * When owner would take massive damage (>50% health), Eepy Eeper can sacrifice itself to negate the damage.
 */
public class EepyEeperSacrifice {
    
    private static final float SACRIFICE_DAMAGE_THRESHOLD = 0.5f; // 50% of max health
    
    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EepyEeperSacrifice::onOwnerDamage);
    }
    
    private static boolean onOwnerDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof ServerPlayerEntity owner)) {
            return true; // Not a player, allow damage
        }
        
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return true;
        }
        
        // Check if damage is significant enough to trigger sacrifice
        float damageThreshold = owner.getMaxHealth() * SACRIFICE_DAMAGE_THRESHOLD;
        if (damageAmount < damageThreshold) {
            return true; // Damage too low for sacrifice
        }
        
        // Find nearby Eepy Eeper pets
        List<MobEntity> eepyPets = findNearbyEepyEeperPets(owner, serverWorld);
        if (eepyPets.isEmpty()) {
            return true; // No eepy pets nearby, allow damage
        }
        
        // Check if any eepy pet can sacrifice for the owner
        MobEntity sacrificialPet = findBestSacrificePet(eepyPets, damageAmount);
        if (sacrificialPet == null) {
            return true; // No suitable pet for sacrifice
        }
        
        // Perform the sacrifice
        boolean sacrificed = performSacrifice(owner, sacrificialPet, damageSource, damageAmount);
        
        if (sacrificed) {
            return false; // Prevent damage
        }
        
        return true; // Allow damage if sacrifice failed
    }
    
    private static List<MobEntity> findNearbyEepyEeperPets(PlayerEntity owner, ServerWorld world) {
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(12), // 12 block radius
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && 
                       petComp.getRole() == PetRole.EEPY_EEPER &&
                       petComp.isOwnedBy(owner) &&
                       entity.isAlive() &&
                       petComp.getLevel() >= 3; // Must be at least level 3 to sacrifice
            }
        );
    }
    
    private static MobEntity findBestSacrificePet(List<MobEntity> eepyPets, float damageAmount) {
        if (eepyPets.isEmpty()) {
            return null;
        }
        
        // Prefer pets that can handle the damage amount based on their level
        MobEntity bestPet = null;
        float bestSacrificeCapacity = 0;
        
        for (MobEntity pet : eepyPets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null) {
                float sacrificeCapacity = calculateSacrificeCapacity(petComp.getLevel());
                if (sacrificeCapacity >= damageAmount && sacrificeCapacity > bestSacrificeCapacity) {
                    bestPet = pet;
                    bestSacrificeCapacity = sacrificeCapacity;
                }
            }
        }
        
        // If no pet can handle the full damage, use the highest level one
        if (bestPet == null) {
            int highestLevel = 0;
            for (MobEntity pet : eepyPets) {
                PetComponent petComp = PetComponent.get(pet);
                if (petComp != null && petComp.getLevel() > highestLevel) {
                    bestPet = pet;
                    highestLevel = petComp.getLevel();
                }
            }
        }
        
        return bestPet;
    }
    
    private static float calculateSacrificeCapacity(int petLevel) {
        // Base capacity of 10 damage at level 3, +5 damage per level, max 50 damage
        float baseCapacity = 10.0f;
        float levelBonus = Math.max(0, petLevel - 3) * 5.0f;
        
        return Math.min(50.0f, baseCapacity + levelBonus);
    }
    
    private static boolean performSacrifice(ServerPlayerEntity owner, MobEntity eepyPet, DamageSource damageSource, float damageAmount) {
        try {
            PetComponent petComp = PetComponent.get(eepyPet);
            if (petComp == null) {
                return false;
            }
            
            // Calculate how much damage the pet can absorb
            float sacrificeCapacity = calculateSacrificeCapacity(petComp.getLevel());
            float absorbedDamage = Math.min(damageAmount, sacrificeCapacity);
            
            // Apply sacrifice effects to owner
            applySacrificeEffects(owner, absorbedDamage, damageAmount);
            
            // Handle remaining damage if any
            if (damageAmount > sacrificeCapacity) {
                float remainingDamage = damageAmount - sacrificeCapacity;
                // Apply reduced damage to owner
                owner.damage((ServerWorld) owner.getWorld(), damageSource, remainingDamage * 0.5f); // 50% damage reduction
                
                owner.sendMessage(
                    Text.of("§6Your Eepy Eeper absorbed most of the damage!"),
                    true // Action bar
                );
            } else {
                owner.sendMessage(
                    Text.of("§6Your Eepy Eeper completely absorbed the damage!"),
                    true // Action bar
                );
            }
            
            // Sacrifice the pet
            sacrificePet(eepyPet, owner, absorbedDamage);
            
            // Visual and audio feedback
            playSacrificeFeedback(owner, eepyPet);
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void applySacrificeEffects(ServerPlayerEntity owner, float absorbedDamage, float totalDamage) {
        // Give owner absorption hearts based on absorbed damage
        int absorptionLevel = Math.min(4, (int) (absorbedDamage / 5)); // 1 level per 5 damage absorbed, max 4
        if (absorptionLevel > 0) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, absorptionLevel - 1)); // 30s
        }
        
        // Give resistance briefly
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0)); // Resistance I for 5s
        
        // Give speed boost (eepy energy transfer)
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1)); // Speed II for 20s
        
        // Apply drowsiness effect (from eepy sacrifice)
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 0)); // Slowness I for 10s
    }
    
    private static void sacrificePet(MobEntity eepyPet, ServerPlayerEntity owner, float absorbedDamage) {
        String petName = eepyPet.hasCustomName() ? 
            eepyPet.getCustomName().getString() : 
            eepyPet.getType().getName().getString();
        
        // Send sacrifice message
        owner.sendMessage(
            Text.of("§6" + petName + " §esacrificed itself to protect you from §c" + String.format("%.1f", absorbedDamage) + " §edamage!"),
            false // Chat message
        );
        
        // Create peaceful death effect (different from cursed one)
        eepyPet.getWorld().playSound(
            null,
            eepyPet.getX(), eepyPet.getY(), eepyPet.getZ(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
            SoundCategory.NEUTRAL,
            1.0f,
            1.5f // Higher pitch for peaceful effect
        );
        
        // Remove the pet component first to prevent death penalty
        PetComponent.remove(eepyPet);
        
        // Kill the pet peacefully
        eepyPet.damage((ServerWorld) eepyPet.getWorld(), eepyPet.getDamageSources().magic(), Float.MAX_VALUE);
    }
    
    private static void playSacrificeFeedback(ServerPlayerEntity owner, MobEntity eepyPet) {
        // Play sacrifice sound (bell sound for peaceful sacrifice)
        owner.getWorld().playSound(
            null,
            owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
            SoundCategory.PLAYERS,
            0.8f,
            1.2f // Higher pitch for positive effect
        );
        
        // Send sacrifice message
        owner.sendMessage(
            Text.of("§6A loyal friend has given their life for yours..."),
            false // Chat message
        );
    }
    
    /**
     * Check if an eepy pet can sacrifice for its owner.
     */
    public static boolean canPetSacrifice(MobEntity eepyPet, float damageAmount) {
        PetComponent petComp = PetComponent.get(eepyPet);
        if (petComp == null || petComp.getRole() != PetRole.EEPY_EEPER) {
            return false;
        }
        
        if (petComp.getLevel() < 3 || !eepyPet.isAlive()) {
            return false;
        }
        
        // Check if damage is significant enough
        return damageAmount >= 5.0f; // Minimum 2.5 hearts of damage to trigger sacrifice
    }
    
    /**
     * Get the sacrifice capacity for an eepy pet based on level.
     */
    public static float getSacrificeCapacity(MobEntity eepyPet) {
        PetComponent petComp = PetComponent.get(eepyPet);
        if (petComp == null) {
            return 0.0f;
        }
        
        return calculateSacrificeCapacity(petComp.getLevel());
    }
    
    /**
     * Check if damage amount triggers sacrifice threshold for a player.
     */
    public static boolean exceedsSacrificeThreshold(PlayerEntity player, float damageAmount) {
        return damageAmount >= (player.getMaxHealth() * SACRIFICE_DAMAGE_THRESHOLD);
    }
}