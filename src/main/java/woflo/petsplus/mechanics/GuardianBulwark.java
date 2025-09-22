package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.advancement.AdvancementManager;

import java.util.List;

/**
 * Implements Guardian Bulwark damage redirection mechanic.
 * When owner takes damage, Guardian pets can redirect some/all damage to themselves.
 */
public class GuardianBulwark {
    
    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GuardianBulwark::onOwnerTakeDamage);
    }
    
    private static boolean onOwnerTakeDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof PlayerEntity owner)) {
            return true; // Not a player, allow damage
        }
        
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return true;
        }
        
        // Find nearby Guardian pets
        List<MobEntity> guardianPets = findNearbyGuardianPets(owner, serverWorld);
        if (guardianPets.isEmpty()) {
            return true; // No guardians nearby, allow normal damage
        }
        
        // Check if this damage should be redirected
        if (!shouldRedirectDamage(damageSource)) {
            return true;
        }
        
        // Try to redirect damage to a guardian
        MobEntity chosenGuardian = chooseGuardianForRedirection(guardianPets);
        if (chosenGuardian == null) {
            return true;
        }
        
        // Redirect the damage
        boolean redirected = redirectDamageToGuardian(owner, chosenGuardian, damageSource, damageAmount);
        
        if (redirected) {
            // Fire after_pet_redirect trigger for abilities
            firePetRedirectTrigger(owner, chosenGuardian, damageAmount);
            
            // Visual/audio feedback
            playRedirectionFeedback(owner, chosenGuardian);
            
            return false; // Cancel original damage to owner
        }
        
        return true; // Allow original damage if redirection failed
    }
    
    private static List<MobEntity> findNearbyGuardianPets(PlayerEntity owner, ServerWorld world) {
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16), // 16 block radius
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && 
                       petComp.getRole() == PetRole.GUARDIAN &&
                       petComp.isOwnedBy(owner) &&
                       entity.isAlive() &&
                       !entity.isInvulnerable();
            }
        );
    }
    
    private static boolean shouldRedirectDamage(DamageSource damageSource) {
        // Don't redirect certain damage types
        if (damageSource.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY) ||
            damageSource.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_EFFECTS) ||
            damageSource.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_DROWNING) ||
            damageSource.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) {
            return false;
        }
        
        return true;
    }
    
    private static MobEntity chooseGuardianForRedirection(List<MobEntity> guardians) {
        if (guardians.isEmpty()) {
            return null;
        }
        
        // Choose the guardian with highest health percentage
        MobEntity bestGuardian = null;
        float bestHealthRatio = 0.0f;
        
        for (MobEntity guardian : guardians) {
            float healthRatio = guardian.getHealth() / guardian.getMaxHealth();
            if (healthRatio > bestHealthRatio && healthRatio > 0.2f) { // Don't use guardians below 20% health
                bestGuardian = guardian;
                bestHealthRatio = healthRatio;
            }
        }
        
        return bestGuardian;
    }
    
    private static boolean redirectDamageToGuardian(PlayerEntity owner, MobEntity guardian, 
                                                  DamageSource damageSource, float damageAmount) {
        try {
            // Calculate damage reduction for owner based on guardian's health
            float redirectionPercent = calculateRedirectionPercent(guardian);
            float redirectedDamage = damageAmount * redirectionPercent;
            float ownerDamage = damageAmount * (1.0f - redirectionPercent);
            
            // Apply damage to guardian
            boolean guardianTookDamage = guardian.damage((ServerWorld) guardian.getWorld(), damageSource, redirectedDamage);
            
            if (guardianTookDamage && ownerDamage > 0) {
                // Apply reduced damage to owner
                owner.damage((ServerWorld) owner.getWorld(), damageSource, ownerDamage);
            }
            
            return guardianTookDamage;
            
        } catch (Exception e) {
            // If anything goes wrong, don't redirect
            return false;
        }
    }
    
    private static float calculateRedirectionPercent(MobEntity guardian) {
        // Base redirection of 70%, increased by guardian's health
        float baseRedirection = 0.7f;
        float healthBonus = (guardian.getHealth() / guardian.getMaxHealth()) * 0.3f;
        
        return Math.min(0.95f, baseRedirection + healthBonus); // Max 95% redirection
    }
    
    private static void firePetRedirectTrigger(PlayerEntity owner, MobEntity guardian, float damageAmount) {
        try {
            // TODO: Fire the after_pet_redirect trigger for Guardian abilities
            // This will trigger abilities like Shield Bash Rider
            // TriggerDispatcher.fireTrigger("after_pet_redirect", guardian, owner, null);

            // Track damage for advancement
            if (owner instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                AdvancementManager.triggerGuardianTankDamage(serverPlayer, guardian, damageAmount);
            }
        } catch (Exception e) {
            // Ignore trigger errors to prevent blocking redirection
        }
    }
    
    private static void playRedirectionFeedback(PlayerEntity owner, MobEntity guardian) {
        // Play shield sound
        owner.getWorld().playSound(
            null,
            guardian.getX(), guardian.getY(), guardian.getZ(),
            SoundEvents.ITEM_SHIELD_BLOCK,
            SoundCategory.NEUTRAL,
            0.8f,
            1.0f + (owner.getWorld().random.nextFloat() - 0.5f) * 0.2f
        );
        
        // Send action bar message
        String guardianName = guardian.hasCustomName() ? 
            guardian.getCustomName().getString() : 
            guardian.getType().getName().getString();
            
        owner.sendMessage(
            Text.of("§6" + guardianName + " §eguards you!"),
            true // Action bar
        );
    }
    
    /**
     * Get the redirection chance for a guardian pet.
     * Higher level guardians have better redirection rates.
     */
    public static float getRedirectionChance(MobEntity guardian) {
        PetComponent petComp = PetComponent.get(guardian);
        if (petComp == null) {
            return 0.0f;
        }
        
        int level = petComp.getLevel();
        
        // Base 60% chance, +2% per level, max 90%
        float baseChance = 0.6f;
        float levelBonus = (level - 1) * 0.02f;
        
        return Math.min(0.9f, baseChance + levelBonus);
    }
}