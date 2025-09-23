package woflo.petsplus.roles.eclipsed;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Implements Eclipsed role mechanics: shadow manipulation and eclipse powers.
 * 
 * Core Features:
 * - Baseline: Shadow stealth, darkness vision, eclipse energy
 * - L7 Umbral Mastery: Eclipse field, shadow stepping, void damage
 * - Darkness and shadow-based abilities
 * 
 * Design Philosophy:
 * - Shadow manipulation archetype
 * - Provides stealth and darkness-based advantages
 * - Eclipse-themed abilities with day/night cycles
 */
public class EclipsedCore {
    
    public static void initialize() {
        // Register damage events for shadow protection
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EclipsedCore::onEntityDamage);
        
        // Register world tick for eclipse effects processing
        ServerTickEvents.END_WORLD_TICK.register(EclipsedCore::onWorldTick);
    }
    
    /**
     * Handle damage events for shadow protection and eclipse abilities.
     */
    private static boolean onEntityDamage(LivingEntity entity, net.minecraft.entity.damage.DamageSource damageSource, float damageAmount) {
        // Handle Eclipsed pet shadow protection
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComp = PetComponent.get(mobEntity);
            if (petComp != null && petComp.hasRole(PetRoleType.ECLIPSED)) {
                // Shadow protection in darkness
                if (isInDarkness(mobEntity)) {
                    return damageAmount <= 2.0f; // Absorb small damage in darkness
                }
                
                // Eclipse energy and void abilities
                if (petComp.getOwner() instanceof ServerPlayerEntity owner &&
                    mobEntity instanceof net.minecraft.entity.passive.TameableEntity tameable) {
                    EclipsedVoid.onServerTick(tameable, owner);
                }
            }
        }
        
        // Handle owner shadow protection from nearby Eclipsed
        if (entity instanceof ServerPlayerEntity player) {
            if (hasNearbyEclipsed(player) && isInDarkness(player)) {
                // Eclipsed provides shadow protection to owner in darkness
                return damageAmount <= 3.0f; // Absorb moderate damage
            }
        }
        
        return true; // Allow damage
    }
    
    /**
     * World tick handler for eclipse effects and shadow abilities.
     */
    private static void onWorldTick(ServerWorld world) {
        // Process eclipse effects for all Eclipsed pets
        processEclipseEffects(world);
    }
    
    /**
     * Process eclipse effects for Eclipsed pets.
     */
    private static void processEclipseEffects(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (hasNearbyEclipsed(player)) {
                // Apply eclipse field and shadow abilities
                world.getEntitiesByClass(
                    MobEntity.class,
                    player.getBoundingBox().expand(16.0),
                    entity -> {
                        PetComponent component = PetComponent.get(entity);
                        return component != null && 
                               component.hasRole(PetRoleType.ECLIPSED) &&
                               entity.isAlive() &&
                               component.isOwnedBy(player);
                    }
                ).forEach(eclipsedPet -> {
                    PetComponent petComp = PetComponent.get(eclipsedPet);
                    if (petComp != null && eclipsedPet instanceof net.minecraft.entity.passive.TameableEntity tameable) {
                        // Process eclipse field using existing void mechanics
                        EclipsedVoid.onServerTick(tameable, player);
                        
                        // Apply advanced abilities for high-level pets
                        if (petComp.getLevel() >= 7) {
                            EclipsedAdvancedAbilities.createEventHorizon(player, eclipsedPet.getPos());
                        }
                    }
                });
            }
        }
    }
    
    /**
     * Check if player has a nearby Eclipsed pet.
     */
    private static boolean hasNearbyEclipsed(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        double searchRadius = 16.0;
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(searchRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= searchRadius * searchRadius;
            }
        ).size() > 0;
    }
    
    /**
     * Check if entity is in darkness (low light level).
     */
    private static boolean isInDarkness(LivingEntity entity) {
        return entity.getWorld().getLightLevel(entity.getBlockPos()) <= 7;
    }
    
    /**
     * Check if it's currently an eclipse (night time).
     */
    private static boolean isEclipseTime(ServerWorld world) {
        long timeOfDay = world.getTimeOfDay() % 24000;
        return timeOfDay >= 13000 && timeOfDay <= 23000; // Night time
    }
    
    /**
     * Check if player has active Eclipse Field (L7+ Eclipsed).
     */
    public static boolean hasActiveEclipseField(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       component.getLevel() >= 7 && // L7+ for Eclipse Field
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).size() > 0;
    }
    
    /**
     * Get shadow damage bonus for nearby Eclipsed pets.
     */
    public static float getShadowDamageBonus(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return 0.0f;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return 0.0f;
        }
        
        // Bonus is higher in darkness and during eclipse
        float baseBonus = 1.0f;
        if (isInDarkness(player)) {
            baseBonus *= 1.5f; // 50% more in darkness
        }
        if (isEclipseTime(world)) {
            baseBonus *= 2.0f; // Double during eclipse
        }
        
        return baseBonus;
    }
    
    /**
     * Apply shadow stealth effects to player.
     */
    public static void applyShadowStealth(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player) || !isInDarkness(player)) {
            return;
        }
        
        // Apply night vision and stealth effects
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            StatusEffects.NIGHT_VISION, 200, 0, true, false)); // 10 seconds
        
        // Additional invisibility during eclipse
        if (player.getWorld() instanceof ServerWorld world && isEclipseTime(world)) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                StatusEffects.INVISIBILITY, 100, 0, true, false)); // 5 seconds
        }
    }
    
    /**
     * Trigger eclipse abilities when entering darkness.
     */
    public static void onEnterDarkness(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        
        // Find nearby Eclipsed pets and trigger shadow abilities
        world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).forEach(eclipsedPet -> {
            PetComponent petComp = PetComponent.get(eclipsedPet);
            if (petComp != null && eclipsedPet instanceof net.minecraft.entity.passive.TameableEntity tameable) {
                // Trigger void abilities when entering darkness
                EclipsedVoid.onServerTick(tameable, player);
                
                // Apply edge step effects for fall damage reduction
                if (EclipsedAdvancedAbilities.shouldTriggerEdgeStep(player, 3.0)) {
                    // Edge step activated
                }
            }
        });
    }
    
    /**
     * Check if player has shadow protection from nearby Eclipsed pets.
     */
    public static boolean hasShadowProtection(ServerPlayerEntity player) {
        return hasNearbyEclipsed(player) && isInDarkness(player);
    }
    
    /**
     * Get eclipse energy level for player based on nearby Eclipsed pets.
     */
    public static int getEclipseEnergy(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return 0;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        
        // Calculate energy based on highest level Eclipsed pet
        int maxLevel = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).stream()
        .mapToInt(entity -> {
            PetComponent component = PetComponent.get(entity);
            return component != null ? component.getLevel() : 0;
        })
        .max()
        .orElse(0);
        
        // Base energy scaling with level and conditions
        int baseEnergy = maxLevel * 10;
        if (isInDarkness(player)) {
            baseEnergy *= 2; // Double in darkness
        }
        if (isEclipseTime(world)) {
            baseEnergy *= 3; // Triple during eclipse
        }
        
        return Math.min(baseEnergy, 300); // Max 300 eclipse energy
    }
}