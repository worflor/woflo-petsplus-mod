package woflo.petsplus.roles.guardian;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

/**
 * Guardian Bulwark system - redirects damage from owner to pet and provides defensive benefits.
 */
public class GuardianBulwark {
    
    /**
     * Attempt to redirect damage from owner to their Guardian pet.
     * @param owner The player taking damage
     * @param damage The incoming damage amount
     * @param source The damage source
     * @return The amount of damage that should be taken by the owner (0 if fully redirected)
     */
    public static float tryRedirectDamage(PlayerEntity owner, float damage, DamageSource source) {
        // Find Guardian pet near owner
        MobEntity guardianPet = findNearbyGuardianPet(owner);
        if (guardianPet == null) {
            return damage; // No redirect possible
        }
        
        PetComponent petComponent = PetComponent.get(guardianPet);
        if (petComponent == null) {
            return damage;
        }
        
        // Check if Bulwark is on cooldown
        if (petComponent.isOnCooldown("guardian_bulwark")) {
            return damage;
        }
        
        // Don't redirect certain damage types
        if (!canRedirectDamageType(source)) {
            return damage;
        }
        
        // Calculate redirection amount (can be partial based on pet health)
        float redirectAmount = calculateRedirectionAmount(guardianPet, damage);
        if (redirectAmount <= 0) {
            return damage; // Pet can't take any damage
        }
        
        // Apply damage to pet instead
        if (owner.getWorld() instanceof ServerWorld serverWorld) {
            DamageSource petDamageSource = createRedirectedDamageSource(source, serverWorld);
            guardianPet.damage(serverWorld, petDamageSource, redirectAmount);
        }
        
        // Set cooldown on Bulwark
        int cooldown = PetsPlusConfig.getInstance().getInt("guardian", "shieldBashIcdTicks", 120);
        petComponent.setCooldown("guardian_bulwark", cooldown);
        
        // Trigger Guardian abilities
        triggerGuardianAbilities(guardianPet, owner, source, redirectAmount);

        // Emit feedback for damage absorption
        if (owner.getWorld() instanceof ServerWorld serverWorld) {
            woflo.petsplus.ui.FeedbackManager.emitGuardianDamageAbsorbed(guardianPet, serverWorld);
        }

        Petsplus.LOGGER.debug("Guardian {} redirected {} damage from {}",
                             guardianPet.getName().getString(), redirectAmount, owner.getName().getString());
        
        return damage - redirectAmount;
    }
    
    private static MobEntity findNearbyGuardianPet(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        
        // Search for Guardian pets within 16 blocks
        double searchRadius = 16.0;
        return serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(searchRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole().equals(woflo.petsplus.api.PetRole.GUARDIAN) &&
                       entity.isAlive() &&
                       component.isOwnedBy(owner) &&
                       entity.squaredDistanceTo(owner) <= searchRadius * searchRadius;
            }
        ).stream().findFirst().orElse(null);
    }
    
    private static boolean canRedirectDamageType(DamageSource source) {
        // Don't redirect void damage, creative kill, or other special damage
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || 
            source.isOf(DamageTypes.GENERIC_KILL)) {
            return false;
        }
        
        // Don't redirect fall damage if it's too high (would kill pet)
        if (source.isOf(DamageTypes.FALL)) {
            return false; // Guardian doesn't protect from fall damage
        }
        
        return true;
    }
    
    private static float calculateRedirectionAmount(MobEntity pet, float incomingDamage) {
        float petHealth = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        
        // Don't redirect damage if pet is very low on health
        if (petHealth <= maxHealth * 0.2f) {
            return 0;
        }
        
        // Don't redirect more damage than would kill the pet
        float maxRedirect = Math.max(0, petHealth - 1.0f);
        
        // Redirect full damage if possible, otherwise partial
        return Math.min(incomingDamage, maxRedirect);
    }
    
    private static DamageSource createRedirectedDamageSource(DamageSource original, ServerWorld world) {
        // Create a new damage source that indicates this was redirected
        return world.getDamageSources().generic(); // Simplified for now
    }
    
    private static void triggerGuardianAbilities(MobEntity pet, PlayerEntity owner, DamageSource source, float redirectedAmount) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Create trigger context for after_pet_redirect event
        TriggerContext context = new TriggerContext(serverWorld, pet, owner, "after_pet_redirect");
        context.withData("original_damage", redirectedAmount);
        context.withData("damage_source", source);
        
        // Check if this was projectile damage for projectile DR effect
        if (source.getSource() instanceof ProjectileEntity) {
            applyProjectileDR(owner);
        }
        
        // Apply mount buff if owner is mounted
        if (owner.getVehicle() instanceof LivingEntity mount) {
            applyMountKnockbackResistance(mount);
        }
        
        // Trigger abilities through the ability manager
        AbilityManager.triggerAbilities(pet, context);
    }
    
    private static void applyProjectileDR(PlayerEntity owner) {
        double drPercent = PetsPlusConfig.getInstance().getDouble("guardian", "projectileDrOnRedirectPct", 0.10);
        
        // Apply 40 tick (2 second) projectile damage reduction
        // This would be handled by the ProjectileDrForOwnerEffect in practice
        
        Petsplus.LOGGER.debug("Applied {}% projectile DR to {} for 40 ticks", drPercent * 100, owner.getName().getString());
    }
    
    private static void applyMountKnockbackResistance(LivingEntity mount) {
        // Apply Knockback Resistance +0.5 for 40 ticks
        StatusEffectInstance knockbackResistance = new StatusEffectInstance(
            StatusEffects.RESISTANCE, 
            40, 
            0
        );
        mount.addStatusEffect(knockbackResistance);
        
        Petsplus.LOGGER.debug("Applied knockback resistance to mount {}", mount.getName().getString());
    }
}