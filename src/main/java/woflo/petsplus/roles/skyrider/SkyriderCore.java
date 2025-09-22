package woflo.petsplus.roles.skyrider;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

/**
 * Implements Skyrider role mechanics: fall safety and wind-based abilities.
 * 
 * Core Features:
 * - Baseline: Reduced fall damage for pet, gust dodge on hit
 * - L7 Windlash Rider: Jump boost on owner fall, knockup attacks
 * - Air control and vertical mobility enhancement
 * 
 * Design Philosophy:
 * - Air control and fall mastery archetype
 * - Enhances vertical mobility and aerial combat
 * - Provides fall safety and positioning advantages
 */
public class SkyriderCore {
    
    public static void initialize() {
        // Register damage events for fall damage reduction
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SkyriderCore::onEntityDamage);
        
        // Register world tick for wind effects processing
        ServerTickEvents.END_WORLD_TICK.register(SkyriderCore::onWorldTick);
    }
    
    /**
     * Handle damage events for fall damage reduction and gust dodge.
     */
    private static boolean onEntityDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        // Handle Skyrider pet fall damage reduction
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComp = PetComponent.get(mobEntity);
            if (petComp != null && petComp.getRole() == PetRole.SKYRIDER) {
                // Reduce fall damage for Skyrider pets
                if (damageSource.isOf(DamageTypes.FALL)) {
                    return damageAmount <= 2.0f; // Absorb small fall damage completely
                }
                
                // Check for wind abilities  
                if (petComp.getOwner() instanceof ServerPlayerEntity owner &&
                    mobEntity instanceof TameableEntity tameable) {
                    // Use wind abilities for fall reduction checks
                    boolean shouldApplyFallReduction = SkyriderWinds.shouldApplyFallReductionToMount(tameable, owner);
                    if (shouldApplyFallReduction && SkyriderWinds.isOwnerFallingMinDistance(owner, 3.0)) {
                        // Apply levitation effect if configured
                        if (SkyriderWinds.shouldTriggerProjLevitation(tameable, owner)) {
                            // Wind effects triggered
                        }
                    }
                }
            }
        }
        
        // Handle owner fall damage reduction from nearby Skyrider
        if (entity instanceof ServerPlayerEntity player && damageSource.isOf(DamageTypes.FALL)) {
            if (hasNearbySkyrider(player)) {
                // Skyrider provides fall damage reduction to owner
                return damageAmount <= 4.0f; // Absorb moderate fall damage
            }
        }
        
        return true; // Allow damage
    }
    
    /**
     * World tick handler for wind effects and passive abilities.
     */
    private static void onWorldTick(ServerWorld world) {
        // Process wind effects for all Skyrider pets
        processSkyriderWindEffects(world);
    }
    
    /**
     * Process wind effects for Skyrider pets.
     */
    private static void processSkyriderWindEffects(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (hasNearbySkyrider(player)) {
                // Check if owner is falling and should trigger wind effects
                world.getEntitiesByClass(
                    MobEntity.class,
                    player.getBoundingBox().expand(16.0),
                    entity -> {
                        PetComponent component = PetComponent.get(entity);
                        return component != null && 
                               component.getRole() == PetRole.SKYRIDER &&
                               entity.isAlive() &&
                               component.isOwnedBy(player) &&
                               entity instanceof TameableEntity;
                    }
                ).forEach(skyriderPet -> {
                    if (skyriderPet instanceof TameableEntity tameable) {
                        SkyriderWinds.onServerTick(tameable, player);
                    }
                });
            }
        }
    }
    
    /**
     * Check if player has a nearby Skyrider pet.
     */
    private static boolean hasNearbySkyrider(ServerPlayerEntity player) {
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
                       component.getRole() == PetRole.SKYRIDER &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= searchRadius * searchRadius;
            }
        ).size() > 0;
    }
    
    /**
     * Check if a player has active Skyrider wind protection.
     */
    public static boolean hasActiveSkyriderProtection(ServerPlayerEntity player) {
        return hasNearbySkyrider(player);
    }
    
    /**
     * Get the fall damage reduction from nearby Skyrider pets.
     */
    public static float getSkyriderFallReduction(ServerPlayerEntity player) {
        if (!hasNearbySkyrider(player)) {
            return 0.0f;
        }
        
        // Skyrider provides significant fall damage reduction
        return 0.5f; // 50% fall damage reduction
    }
    
    /**
     * Trigger Windlash Rider effects when owner begins falling.
     */
    public static void onOwnerStartFalling(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        
        // Find nearby Skyrider pets with Windlash Rider (L7+)
        double minFallBlocks = SkyriderWinds.getWindlashMinFallBlocks();
        if (!SkyriderWinds.isOwnerFallingMinDistance(player, minFallBlocks)) {
            return;
        }

        if (!SkyriderWinds.isWindlashOffCooldown(player)) {
            long remaining = SkyriderWinds.getWindlashCooldownRemaining(player);
            Petsplus.LOGGER.debug(
                "Windlash Rider on cooldown for {} ({} ticks remaining)",
                player.getName().getString(),
                remaining
            );
            return;
        }

        var skyriderPets = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.getRole() == PetRole.SKYRIDER &&
                       component.getLevel() >= 7 &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity instanceof TameableEntity;
            }
        );

        boolean triggered = false;

        for (MobEntity skyriderPet : skyriderPets) {
            if (!(skyriderPet instanceof TameableEntity tameable)) {
                continue;
            }

            if (!SkyriderWinds.isOwnerFallingMinDistance(player, minFallBlocks)) {
                continue;
            }

            boolean applyToMount = SkyriderWinds.shouldApplyFallReductionToMount(tameable, player);
            SkyriderWinds.onServerTick(tameable, player);
            triggered = true;

            Petsplus.LOGGER.debug(
                "Skyrider Windlash primed by pet {} for {} ({})",
                skyriderPet.getDisplayName().getString(),
                player.getName().getString(),
                applyToMount ? "mounted" : "on foot"
            );
        }

        if (triggered) {
            SkyriderWinds.markWindlashTriggered(player);
        }
    }
}