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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Implements Eclipsed void save mechanic.
 * When owner falls into the void, Eclipsed pets can teleport them to safety.
 */
public class EclipsedVoidSave {
    
    private static final int VOID_Y_THRESHOLD = -64; // Below this Y level triggers void save
    private static final int SAVE_COOLDOWN_TICKS = 6000; // 5 minutes cooldown
    
    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EclipsedVoidSave::onVoidDamage);
    }
    
    private static boolean onVoidDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof ServerPlayerEntity owner)) {
            return true; // Not a player, allow damage
        }
        
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return true;
        }
        
        // Check if this is void damage
        if (!isVoidDamage(damageSource)) {
            return true; // Not void damage, allow damage
        }
        
        // Check if player is in the void
        if (owner.getY() > VOID_Y_THRESHOLD) {
            return true; // Not deep enough in void, allow damage
        }
        
        // Find nearby Eclipsed pets
        List<MobEntity> eclipsedPets = findNearbyEclipsedPets(owner, serverWorld);
        if (eclipsedPets.isEmpty()) {
            return true; // No eclipsed pets nearby, allow damage
        }
        
        // Check if any eclipsed pet can save the owner
        MobEntity savingPet = findBestVoidSavePet(eclipsedPets);
        if (savingPet == null) {
            return true; // No suitable pet for void save
        }
        
        // Check cooldown
        if (isOnVoidSaveCooldown(owner)) {
            return true; // On cooldown, allow damage
        }
        
        // Perform the void save
        boolean saved = performVoidSave(owner, savingPet, serverWorld);
        
        if (saved) {
            return false; // Prevent damage
        }
        
        return true; // Allow damage if void save failed
    }
    
    private static boolean isVoidDamage(DamageSource damageSource) {
        return damageSource.getName().equals("outOfWorld") || 
               damageSource.getName().equals("fell.out.of.world") ||
               damageSource.getName().equals("generic.kill");
    }
    
    private static List<MobEntity> findNearbyEclipsedPets(PlayerEntity owner, ServerWorld world) {
        // Eclipsed pets can save from any distance (shadow magic)
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(256), // Large radius for void save
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && 
                       petComp.hasRole(PetRoleType.ECLIPSED) &&
                       petComp.isOwnedBy(owner) &&
                       entity.isAlive() &&
                       petComp.getLevel() >= 4; // Must be at least level 4 to void save
            }
        );
    }
    
    private static MobEntity findBestVoidSavePet(List<MobEntity> eclipsedPets) {
        if (eclipsedPets.isEmpty()) {
            return null;
        }
        
        // Prefer higher level pets for void save (better teleportation)
        MobEntity bestPet = null;
        int highestLevel = 0;
        
        for (MobEntity pet : eclipsedPets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null && petComp.getLevel() > highestLevel) {
                bestPet = pet;
                highestLevel = petComp.getLevel();
            }
        }
        
        return bestPet;
    }
    
    private static boolean isOnVoidSaveCooldown(ServerPlayerEntity owner) {
        // Check if player has recently been void-saved
        return owner.hasStatusEffect(StatusEffects.WEAKNESS) && 
               owner.getStatusEffect(StatusEffects.WEAKNESS).getDuration() > 5800; // Custom cooldown marker
    }
    
    private static boolean performVoidSave(ServerPlayerEntity owner, MobEntity eclipsedPet, ServerWorld world) {
        try {
            PetComponent petComp = PetComponent.get(eclipsedPet);
            if (petComp == null) {
                return false;
            }
            
            // Find safe teleport location
            BlockPos safeLocation = findSafeTeleportLocation(owner, world, petComp.getLevel());
            if (safeLocation == null) {
                return false; // Couldn't find safe location
            }
            
            // Teleport owner to safety
            teleportToSafety(owner, safeLocation, world);
            
            // Apply void save effects
            applyVoidSaveEffects(owner, petComp.getLevel());
            
            // Apply cooldown
            applyVoidSaveCooldown(owner);
            
            // Pet exhaustion - higher level pets handle void save better
            applyPetExhaustion(eclipsedPet, petComp);
            
            // Visual and audio feedback
            playVoidSaveFeedback(owner, eclipsedPet, safeLocation);
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static BlockPos findSafeTeleportLocation(ServerPlayerEntity owner, ServerWorld world, int petLevel) {
        // Try world spawn
        BlockPos worldSpawn = world.getSpawnPos();
        if (isSafeLocation(world, worldSpawn)) {
            return worldSpawn;
        }
        
        // Search for safe location near world spawn (higher level pets find better spots)
        int searchRadius = Math.min(50, 10 + (petLevel * 5));
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = worldSpawn.getX() + (world.getRandom().nextInt(searchRadius * 2) - searchRadius);
            int z = worldSpawn.getZ() + (world.getRandom().nextInt(searchRadius * 2) - searchRadius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeLocation(world, candidate)) {
                return candidate;
            }
        }
        
        // Last resort: just use world spawn (even if not perfectly safe)
        return worldSpawn;
    }
    
    private static boolean isSafeLocation(ServerWorld world, BlockPos pos) {
        // Check if location is safe for teleportation
        if (pos.getY() < world.getBottomY() || pos.getY() > world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos)) {
            return false;
        }
        
        // Check if there's solid ground and air space
        BlockPos groundPos = pos.down();
        BlockPos airPos1 = pos;
        BlockPos airPos2 = pos.up();
        
        return world.getBlockState(groundPos).isSolidBlock(world, groundPos) &&
               world.getBlockState(airPos1).isAir() &&
               world.getBlockState(airPos2).isAir();
    }
    
    private static void teleportToSafety(ServerPlayerEntity owner, BlockPos safeLocation, ServerWorld world) {
        // Teleport with slight upward offset to ensure safe landing
        Vec3d teleportPos = Vec3d.ofBottomCenter(safeLocation).add(0, 0.1, 0);
        owner.teleport(teleportPos.x, teleportPos.y, teleportPos.z, true);
        
        // Reset fall distance
        owner.fallDistance = 0;
        
        // Cancel any momentum
        owner.setVelocity(Vec3d.ZERO);
    }
    
    private static void applyVoidSaveEffects(ServerPlayerEntity owner, int petLevel) {
        // Apply shadow magic effects
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0)); // Night vision for 60s
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0)); // Resistance I for 20s
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0)); // Slow falling for 30s
        
        // Shadow sickness from void exposure
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0)); // Nausea for 10s
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0)); // Blindness for 5s
        
        // Healing based on pet level
        if (petLevel >= 6) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 300, 0)); // Regen I for 15s
        }
    }
    
    private static void applyVoidSaveCooldown(ServerPlayerEntity owner) {
        // Apply weakness as cooldown marker
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, SAVE_COOLDOWN_TICKS, 0)); // 5 minutes
        
        owner.sendMessage(
            Text.of("§8You cannot be void-saved again for 5 minutes."),
            false
        );
    }
    
    private static void applyPetExhaustion(MobEntity eclipsedPet, PetComponent petComp) {
        // Lower level pets get more exhausted
        int exhaustionLevel = Math.max(0, 7 - petComp.getLevel());
        
        if (exhaustionLevel > 0) {
            eclipsedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1200, exhaustionLevel - 1));
            eclipsedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 800, 0));
        }
        
        // All pets get some fatigue
        eclipsedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0)); // Glowing for 30s (shadow magic residue)
    }
    
    private static void playVoidSaveFeedback(ServerPlayerEntity owner, MobEntity eclipsedPet, BlockPos safeLocation) {
        String petName = eclipsedPet.hasCustomName() ? 
            eclipsedPet.getCustomName().getString() : 
            eclipsedPet.getType().getName().getString();
        
        // Play void save sound (enderman teleport but lower pitch)
        owner.getWorld().playSound(
            null,
            owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.ENTITY_SHULKER_TELEPORT,
            SoundCategory.PLAYERS,
            1.0f,
            0.6f // Lower pitch for ominous effect
        );
        
        // Send void save message
        owner.sendMessage(
            Text.of("§8" + petName + " §5pulled you from the void through shadow magic!"),
            false // Chat message
        );
        
        // Action bar message
        owner.sendMessage(
            Text.of("§8✦ §5Saved from the void §8✦"),
            true // Action bar
        );
        
        // Coordinates message
        owner.sendMessage(
            Text.of("§8Teleported to safety at: §7" + safeLocation.getX() + ", " + safeLocation.getY() + ", " + safeLocation.getZ()),
            false
        );
    }
    
    /**
     * Check if an eclipsed pet can void save its owner.
     */
    public static boolean canPetVoidSave(MobEntity eclipsedPet) {
        PetComponent petComp = PetComponent.get(eclipsedPet);
        if (petComp == null || !petComp.hasRole(PetRoleType.ECLIPSED)) {
            return false;
        }
        
        return petComp.getLevel() >= 4 && eclipsedPet.isAlive();
    }
    
    /**
     * Get the void save range for an eclipsed pet based on level.
     */
    public static double getVoidSaveRange(MobEntity eclipsedPet) {
        PetComponent petComp = PetComponent.get(eclipsedPet);
        if (petComp == null) {
            return 0.0;
        }
        
        // Higher level pets can save from greater distances
        return 128.0 + (petComp.getLevel() * 32.0); // Base 128 + 32 per level
    }
    
    /**
     * Check if a player is currently in the void danger zone.
     */
    public static boolean isInVoidDangerZone(PlayerEntity player) {
        return player.getY() <= VOID_Y_THRESHOLD;
    }
}