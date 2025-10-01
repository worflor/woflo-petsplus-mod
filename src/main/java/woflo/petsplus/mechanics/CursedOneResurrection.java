package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.AfterimageManager;
import woflo.petsplus.ui.UIFeedbackManager;
import woflo.petsplus.roles.cursedone.CursedOneSoulSacrificeManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements Cursed One auto-resurrect and immortality mechanics.
 *
 * Features by level:
 * - Level 15+: Doom Echo ability (JSON-driven) + Pet immortality with 15s cooldown
 * - Level 25+: Auto-resurrect mount buff (applies to both owner and pet resurrections)
 *
 * When owner would die, Cursed One pets can sacrifice themselves to prevent owner death.
 * When Cursed One pets would die, they enter a 15-second reanimation state where they are
 * untargetable and inactive, then resurrect themselves (immortality) unless on cooldown.
 * When owner actually dies, all Cursed One pets die too (cursed bond).
 */
public class CursedOneResurrection {
    
    // Track pets currently in reanimation state (UUID -> reanimation window)
    private static final Map<UUID, ReanimationWindow> reanimatingPets = new ConcurrentHashMap<>();
    
    // Synchronization lock for reanimation state management
    private static final ReentrantLock reanimationLock = new ReentrantLock();

    private record ReanimationWindow(long endTime, int durationTicks) {
        long startTime() {
            return endTime - durationTicks;
        }
    }
    
    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CursedOneResurrection::onEntityDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) ->
            CursedOneResurrection.onOwnerDeath(entity, damageSource, damageAmount));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) ->
            CursedOneResurrection.onPetDeath(entity, damageSource, damageAmount));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) ->
            CursedOneResurrection.onOwnerActualDeath(entity, damageSource));
        
    }
    
    /**
     * Handle damage to entities - prevent damage to reanimating pets
     */
    private static boolean onEntityDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof MobEntity mobEntity)) {
            return true; // Not a mob, allow damage
        }
        
        // Check if this pet is currently reanimating
        if (isReanimating(mobEntity)) {
            // Entity is untargetable during reanimation - no damage allowed
            return false;
        }
        
        return true; // Allow damage normally
    }
    
    public static void handleMobTick(MobEntity mob, ServerWorld world) {
        if (reanimatingPets.isEmpty()) {
            return;
        }

        // Use atomic get operation for thread safety
        ReanimationWindow window = reanimatingPets.get(mob.getUuid());
        if (window == null) {
            return;
        }

        long currentTime = world.getTime();
        if (currentTime >= window.endTime()) {
            // Use synchronized block to prevent race conditions during completion
            reanimationLock.lock();
            try {
                // Double-check that we're still reanimating and haven't been completed by another thread
                ReanimationWindow currentWindow = reanimatingPets.get(mob.getUuid());
                if (currentWindow != null && currentTime >= currentWindow.endTime()) {
                    completeReanimation(world, mob.getUuid(), currentWindow);
                    reanimatingPets.remove(mob.getUuid());
                }
            } finally {
                reanimationLock.unlock();
            }
            return;
        }

        long timeInReanimation = Math.max(0, currentTime - window.startTime());
        createProgressiveReanimationEffects(world, mob.getUuid(), timeInReanimation, window.durationTicks());
    }
    
    /**
     * Check if a pet is currently in reanimation state
     */
    public static boolean isReanimating(MobEntity pet) {
        if (pet == null) return false;
        // Use atomic containsKey operation for thread safety
        return reanimatingPets.containsKey(pet.getUuid());
    }
    
    /**
     * Complete the reanimation process and resurrect the pet
     */
    private static void completeReanimation(ServerWorld world, UUID petUuid, ReanimationWindow window) {
        // Find the pet entity
        MobEntity pet = (MobEntity) world.getEntity(petUuid);

        if (pet == null || !pet.isAlive()) {
            return; // Pet no longer exists or is dead
        }
        
        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null) {
            return; // Pet component missing
        }
        
        // Release the glass afterimage with a final burst
        AfterimageManager.finishEncasement(pet, true);

        // Detonate a lighter burst as the pet reforms
        triggerReanimationBurst(world, pet, petComp);

        // Resurrect with 50% health
        float maxHealth = pet.getMaxHealth();
        float resurrectionHealth = maxHealth * 0.5f;
        pet.setHealth(resurrectionHealth);
        
        // Remove any negative effects that might make the pet appear "dead"
        pet.clearStatusEffects();
        
        // Apply cursed resurrection effects
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0)); // Regen I for 10s
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));   // Resistance I for 5s
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));     // Darkness for 3s (cursed effect)
        
        // Make the pet targetable again and able to move
        pet.setInvulnerable(false);
        pet.setAiDisabled(false);
        
        // Visual and audio feedback for resurrection
        playResurrectionCompleteFeedback(pet, world);
        
        // Fire pet resurrection abilities
        TriggerContext context = new TriggerContext(world, pet, petComp.getOwner(), "on_pet_resurrect");
        AbilityManager.triggerAbilities(pet, context);
        
        // Handle mount buff if owner is mounted and pet is level 25+
        if (petComp.getLevel() >= 25 && petComp.getOwner() != null) {
            handlePetResurrectionMountBuff(petComp.getOwner());
        }
        
        // Notify owner
        if (petComp.getOwner() != null && petComp.getOwner().getWorld() == world) {
            double distance = petComp.getOwner().distanceTo(pet);
            if (distance <= 32) {
                String petName = pet.hasCustomName() ?
                    pet.getCustomName().getString() :
                    pet.getType().getName().getString();
                
                petComp.getOwner().sendMessage(
                    Text.of("§8" + petName + " §5has completed its reanimation! §8Dark forces flow through it once more."),
                    false
                );
                
                petComp.getOwner().sendMessage(
                    Text.of("§8✦ §5Reanimation Complete §8✦"),
                    true
                );
            }
        }
        
        // Brief invulnerability period
        pet.timeUntilRegen = 40; // 2 seconds
    }

    private static void triggerReanimationBurst(ServerWorld world, MobEntity pet, PetComponent component) {
        if (world == null || pet == null || component == null) {
            return;
        }

        TriggerContext context = new TriggerContext(world, pet, component.getOwner(), "on_pet_death")
            .withData("death_burst_reason", "reanimation");
        AbilityManager.triggerAbilities(pet, context);
    }
    
    /**
     * Create progressive visual effects during reanimation that build up over time
     */
    private static void createProgressiveReanimationEffects(ServerWorld world, UUID petUuid,
                                                           long timeInReanimation, int totalDuration) {
        // Find the pet entity
        MobEntity pet = (MobEntity) world.getEntity(petUuid);

        if (pet == null) {
            return; // Pet no longer exists
        }

        double x = pet.getX();
        double y = pet.getY() + 0.5;
        double z = pet.getZ();

        // Calculate progress (0.0 to 1.0) through the 15-second reanimation
        int duration = Math.max(1, totalDuration);
        float progress = Math.min(1.0f, timeInReanimation / (float) duration);

        // Generate a unique seed for this reanimation based on pet UUID and start time
        long reanimationSeed = petUuid.getMostSignificantBits() ^ duration;
        Random random = Random.create(reanimationSeed);

        // Choose a reanimation pattern based on the seed (algorithmic variation)
        int pattern = random.nextInt(4); // 4 different patterns
        
        // Stage 1: Gathering phase (0-40% progress) - particles gather outward
        if (progress < 0.4f) {
            createGatheringPhase(world, random, x, y, z, progress, pattern);
        }
        // Stage 2: Building tension (40-80% progress) - particles swirl and intensify
        else if (progress < 0.8f) {
            createBuildingPhase(world, random, x, y, z, progress, pattern);
        }
        // Stage 3: Pre-explosion (80-95% progress) - particles collapse inward
        else if (progress < 0.95f) {
            createCollapsePhase(world, random, x, y, z, progress, pattern);
        }
        // Stage 4: Near completion (95-100% progress) - dramatic buildup to explosion
        else {
            createPreExplosionPhase(world, random, x, y, z, progress, pattern);
        }
        
        // Add random audio cues at specific progress points
        addProgressiveAudioCues(world, x, y, z, timeInReanimation, pattern);
    }
    
    /**
     * Stage 1: Gathering phase - particles appear and move outward
     */
    private static void createGatheringPhase(ServerWorld world, Random random, double x, double y, double z, float progress, int pattern) {
        // Only create effects every few ticks for performance
        if (world.getTime() % 3 != 0) return;
        
        float intensity = progress * 2.5f; // Intensity grows during this phase
        int particleCount = (int)(2 + intensity * 3);
        
        switch (pattern) {
            case 0: // Spiral pattern
                for (int i = 0; i < particleCount; i++) {
                    double angle = (world.getTime() * 0.1 + i * 0.8) % (Math.PI * 2);
                    double radius = 0.5 + progress * 1.5;
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = (random.nextDouble() - 0.5) * 0.8;
                    
                    world.spawnParticles(ParticleTypes.SMOKE, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, 0.0, 0.05, 0.0, 0.01);
                }
                break;
                
            case 1: // Pulsing rings
                if (world.getTime() % 8 == 0) {
                    for (int i = 0; i < 12; i++) {
                        double angle = i * (Math.PI * 2 / 12);
                        double radius = 1.0 + progress * 1.0;
                        double offsetX = Math.cos(angle) * radius;
                        double offsetZ = Math.sin(angle) * radius;
                        
                        world.spawnParticles(ParticleTypes.SOUL, 
                            x + offsetX, y, z + offsetZ,
                            1, 0.0, 0.1, 0.0, 0.02);
                    }
                }
                break;
                
            case 2: // Chaotic swarm
                for (int i = 0; i < particleCount; i++) {
                    double offsetX = (random.nextDouble() - 0.5) * (2.0 + progress * 2.0);
                    double offsetY = random.nextDouble() * (1.0 + progress * 1.0);
                    double offsetZ = (random.nextDouble() - 0.5) * (2.0 + progress * 2.0);
                    
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.015);
                }
                break;
                
            case 3: // Cross formation
                if (world.getTime() % 5 == 0) {
                    double dist = 0.8 + progress * 1.2;
                    // Four directions
                    double[][] directions = {{dist,0}, {-dist,0}, {0,dist}, {0,-dist}};
                    for (double[] dir : directions) {
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, 
                            x + dir[0], y, z + dir[1],
                            2, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                break;
        }
    }
    
    /**
     * Stage 2: Building phase - particles intensify and swirl faster
     */
    private static void createBuildingPhase(ServerWorld world, Random random, double x, double y, double z, float progress, int pattern) {
        if (world.getTime() % 2 != 0) return;
        
        float localProgress = (progress - 0.4f) / 0.4f; // 0-1 within this phase
        float intensity = 1.0f + localProgress * 2.0f;
        
        switch (pattern) {
            case 0: // Faster spiral with more particles
                for (int i = 0; i < (int)(6 * intensity); i++) {
                    double angle = (world.getTime() * 0.2 + i * 0.5) % (Math.PI * 2);
                    double radius = 2.0 - localProgress * 0.8; // Slightly contracting
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = Math.sin(world.getTime() * 0.1 + i) * 0.5;
                    
                    world.spawnParticles(ParticleTypes.SMOKE, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.02);
                    
                    // Add soul particles for intensity
                    if (i % 2 == 0) {
                        world.spawnParticles(ParticleTypes.SOUL, 
                            x + offsetX * 0.7, y + offsetY, z + offsetZ * 0.7,
                            1, 0.0, 0.05, 0.0, 0.015);
                    }
                }
                break;
                
            case 1: // Multiple pulsing rings at different heights
                if (world.getTime() % 6 == 0) {
                    for (int ring = 0; ring < 3; ring++) {
                        double ringY = y + (ring - 1) * 0.6;
                        double ringRadius = 1.2 + ring * 0.3 - localProgress * 0.4;
                        for (int i = 0; i < 16; i++) {
                            double angle = i * (Math.PI * 2 / 16) + ring * 0.5;
                            double offsetX = Math.cos(angle) * ringRadius;
                            double offsetZ = Math.sin(angle) * ringRadius;
                            
                            world.spawnParticles(ParticleTypes.SOUL, 
                                x + offsetX, ringY, z + offsetZ,
                                1, 0.0, 0.02, 0.0, 0.01);
                        }
                    }
                }
                break;
                
            case 2: // Chaotic vortex
                for (int i = 0; i < (int)(8 * intensity); i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = random.nextDouble() * (2.5 - localProgress * 0.8);
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = (random.nextDouble() - 0.5) * 1.5;

                    // Mix different particle types for chaos
                    if (random.nextFloat() < 0.3f) {
                        world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                            x + offsetX, y + offsetY, z + offsetZ,
                            1, 0.0, 0.0, 0.0, 0.02);
                    } else {
                        world.spawnParticles(ParticleTypes.SMOKE, 
                            x + offsetX, y + offsetY, z + offsetZ,
                            1, 0.0, 0.0, 0.0, 0.025);
                    }
                }
                break;
                
            case 3: // Expanding and contracting cross with vertical elements
                if (world.getTime() % 4 == 0) {
                    double dist = 1.5 + Math.sin(world.getTime() * 0.15) * 0.5 - localProgress * 0.3;
                    // Cross formation with vertical pillars
                    double[][] directions = {{dist,0}, {-dist,0}, {0,dist}, {0,-dist}};
                    for (double[] dir : directions) {
                        // Horizontal particles
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, 
                            x + dir[0], y, z + dir[1],
                            3, 0.1, 0.1, 0.1, 0.02);
                        
                        // Vertical pillar particles
                        for (int h = 0; h < 3; h++) {
                            world.spawnParticles(ParticleTypes.SOUL, 
                                x + dir[0] * 0.7, y + h * 0.4, z + dir[1] * 0.7,
                                1, 0.05, 0.0, 0.05, 0.01);
                        }
                    }
                }
                break;
        }
    }
    
    /**
     * Stage 3: Collapse phase - particles begin moving inward
     */
    private static void createCollapsePhase(ServerWorld world, Random random, double x, double y, double z, float progress, int pattern) {
        float localProgress = (progress - 0.8f) / 0.15f; // 0-1 within this phase
        
        switch (pattern) {
            case 0: // Spiral collapsing inward
                for (int i = 0; i < 10; i++) {
                    double angle = (world.getTime() * 0.3 + i * 0.4) % (Math.PI * 2);
                    double radius = (1.8 - localProgress * 1.5) * (1.0 + Math.sin(world.getTime() * 0.2) * 0.2);
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = (1.0 - localProgress) * 0.8;
                    
                    // Particles moving toward center
                    double velX = -offsetX * 0.05;
                    double velZ = -offsetZ * 0.05;
                    
                    world.spawnParticles(ParticleTypes.SOUL, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, velX, -0.02, velZ, 0.08);
                }
                break;
                
            case 1: // Rings collapsing
                if (world.getTime() % 3 == 0) {
                    double ringRadius = 1.5 - localProgress * 1.3;
                    for (int i = 0; i < 20; i++) {
                        double angle = i * (Math.PI * 2 / 20);
                        double offsetX = Math.cos(angle) * ringRadius;
                        double offsetZ = Math.sin(angle) * ringRadius;
                        
                        // Inward velocity
                        double velX = -offsetX * 0.08;
                        double velZ = -offsetZ * 0.08;
                        
                        world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                            x + offsetX, y, z + offsetZ,
                            1, velX, 0.0, velZ, 0.1);
                    }
                }
                break;
                
            case 2: // Chaotic implosion
                for (int i = 0; i < 12; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = random.nextDouble() * (2.0 - localProgress * 1.8);
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = random.nextDouble() * (1.2 - localProgress * 0.8);

                    // Strong inward pull
                    double velX = -offsetX * (0.06 + localProgress * 0.04);
                    double velZ = -offsetZ * (0.06 + localProgress * 0.04);
                    double velY = -offsetY * 0.03;
                    
                    world.spawnParticles(ParticleTypes.SMOKE, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, velX, velY, velZ, 0.12);
                }
                break;
                
            case 3: // Cross beams converging
                if (world.getTime() % 2 == 0) {
                    double dist = 1.8 - localProgress * 1.6;
                    double[][] directions = {{dist,0}, {-dist,0}, {0,dist}, {0,-dist}, 
                                           {dist*0.7,dist*0.7}, {-dist*0.7,-dist*0.7}, 
                                           {dist*0.7,-dist*0.7}, {-dist*0.7,dist*0.7}};
                    
                    for (double[] dir : directions) {
                        // Particles streaming toward center
                        double velX = -dir[0] * 0.1;
                        double velZ = -dir[1] * 0.1;
                        
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, 
                            x + dir[0], y, z + dir[1],
                            2, velX, 0.0, velZ, 0.15);
                    }
                }
                break;
        }
    }
    
    /**
     * Stage 4: Pre-explosion phase - final dramatic buildup
     */
    private static void createPreExplosionPhase(ServerWorld world, Random random, double x, double y, double z, float progress, int pattern) {
        float localProgress = (progress - 0.95f) / 0.05f; // 0-1 within this phase
        float intensity = 1.0f + localProgress * 4.0f; // Rapidly increasing intensity
        
        // All patterns converge to similar dramatic effect
        if (world.getTime() % 1 == 0) { // Every tick for maximum drama
            // Dense particle core
            for (int i = 0; i < (int)(15 * intensity); i++) {
                double offsetX = (random.nextDouble() - 0.5) * (0.3 - localProgress * 0.2);
                double offsetY = (random.nextDouble() - 0.5) * (0.3 - localProgress * 0.2);
                double offsetZ = (random.nextDouble() - 0.5) * (0.3 - localProgress * 0.2);
                
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, 
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0.0, 0.0, 0.0, 0.01);
                
                if (i % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.005);
                }
            }
            
            // Outer energy rings
            if (world.getTime() % 2 == 0) {
                double ringRadius = 0.8 + Math.sin(world.getTime() * 0.5) * 0.3;
                for (int i = 0; i < 24; i++) {
                    double angle = i * (Math.PI * 2 / 24);
                    double offsetX = Math.cos(angle) * ringRadius;
                    double offsetZ = Math.sin(angle) * ringRadius;
                    
                    world.spawnParticles(ParticleTypes.SOUL, 
                        x + offsetX, y, z + offsetZ,
                        1, 0.0, 0.1, 0.0, 0.02);
                }
            }
        }
    }
    
    /**
     * Add progressive audio cues during reanimation
     */
    private static void addProgressiveAudioCues(ServerWorld world, double x, double y, double z, long timeInReanimation, int pattern) {
        // Play sounds at specific intervals with variation based on pattern
        long interval = 40 + pattern * 10; // Different timing for each pattern
        
        if (timeInReanimation % interval == 0) {
            float progress = Math.min(1.0f, Math.max(0.0f, timeInReanimation / 300.0f));
            
            if (progress < 0.3f) {
                // Early phase - subtle sounds
                world.playSound(null, x, y, z, SoundEvents.BLOCK_SOUL_SAND_STEP, 
                    SoundCategory.NEUTRAL, 0.2f + progress * 0.2f, 0.8f + progress * 0.4f);
            } else if (progress < 0.7f) {
                // Building phase - more intense
                world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_AMBIENT, 
                    SoundCategory.NEUTRAL, 0.3f + progress * 0.3f, 0.6f + progress * 0.6f);
            } else if (progress < 0.9f) {
                // Collapse phase - dramatic
                world.playSound(null, x, y, z, SoundEvents.ENTITY_PHANTOM_FLAP, 
                    SoundCategory.NEUTRAL, 0.4f + progress * 0.4f, 0.4f + progress * 0.8f);
            } else {
                // Pre-explosion - intense buildup
                world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_HURT, 
                    SoundCategory.NEUTRAL, 0.6f + progress * 0.4f, 0.3f + progress * 1.2f);
            }
        }
    }
    
    /**
     * Play resurrection complete feedback with algorithmic variation
     */
    private static void playResurrectionCompleteFeedback(MobEntity pet, ServerWorld world) {
        double x = pet.getX();
        double y = pet.getY() + 0.5;
        double z = pet.getZ();
        
        // Generate variation seed based on pet UUID
        long explosionSeed = pet.getUuid().getMostSignificantBits();
        world.random.setSeed(explosionSeed);
        int explosionPattern = Math.abs((int)(explosionSeed % 3)); // 3 explosion patterns
        
        // Play varied completion sounds
        switch (explosionPattern) {
            case 0: // Deep, rumbling explosion
                world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_SPAWN,
                    SoundCategory.NEUTRAL, 0.8f, 0.8f);
                world.playSound(null, x, y, z, SoundEvents.ITEM_TOTEM_USE,
                    SoundCategory.NEUTRAL, 0.6f, 0.6f);
                break;
            case 1: // Sharp, crackling explosion  
                world.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                    SoundCategory.NEUTRAL, 0.5f, 1.2f);
                world.playSound(null, x, y, z, SoundEvents.ITEM_TOTEM_USE,
                    SoundCategory.NEUTRAL, 0.7f, 1.0f);
                break;
            case 2: // Ethereal, otherworldly explosion
                world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                    SoundCategory.NEUTRAL, 0.4f, 1.8f);
                world.playSound(null, x, y, z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                    SoundCategory.NEUTRAL, 0.6f, 1.4f);
                break;
        }
        
        // Create dramatic explosion effects based on pattern
        createVariedExplosionEffects(world, x, y, z, explosionPattern);
    }
    
    /**
     * Create varied explosion effects based on algorithmic patterns
     */
    private static void createVariedExplosionEffects(ServerWorld world, double x, double y, double z, int pattern) {
        switch (pattern) {
            case 0: // Radial burst explosion
                // Central core explosion
                world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                
                // Radial bursts in 8 directions
                for (int i = 0; i < 8; i++) {
                    double angle = i * (Math.PI * 2 / 8);
                    double distance = 2.5;
                    
                    // Create particle trail
                    for (int j = 0; j < 10; j++) {
                        double progress = j / 10.0;
                        double trailX = x + Math.cos(angle) * distance * progress;
                        double trailZ = z + Math.sin(angle) * distance * progress;
                        double trailY = y + Math.sin(progress * Math.PI) * 0.8;
                        
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            trailX, trailY, trailZ, 2, 0.1, 0.1, 0.1, 0.05);
                    }
                }
                
                // Outer ring of particles
                for (int i = 0; i < 24; i++) {
                    double angle = i * (Math.PI * 2 / 24);
                    double ringX = x + Math.cos(angle) * 3.0;
                    double ringZ = z + Math.sin(angle) * 3.0;
                    
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        ringX, y, ringZ, 3, 0.2, 0.2, 0.2, 0.08);
                }
                break;
                
            case 1: // Spiral explosion
                // Central explosion
                world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                
                // Double spiral outward
                for (int spiral = 0; spiral < 2; spiral++) {
                    for (int i = 0; i < 30; i++) {
                        double progress = i / 30.0;
                        double angle = progress * Math.PI * 4 + spiral * Math.PI; // 2 full rotations
                        double radius = progress * 3.5;
                        double spiralX = x + Math.cos(angle) * radius;
                        double spiralZ = z + Math.sin(angle) * radius;
                        double spiralY = y + progress * 1.5;
                        
                        world.spawnParticles(ParticleTypes.SOUL,
                            spiralX, spiralY, spiralZ, 1, 0.0, 0.1, 0.0, 0.03);
                        
                        if (i % 3 == 0) {
                            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                spiralX, spiralY, spiralZ, 2, 0.1, 0.1, 0.1, 0.04);
                        }
                    }
                }
                
                // Vertical pillar
                for (int i = 0; i < 8; i++) {
                    double pillarY = y + i * 0.4;
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        x, pillarY, z, 4, 0.3, 0.1, 0.3, 0.02);
                }
                break;
                
            case 2: // Layered wave explosion
                // Central explosion
                world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                
                // Three expanding waves at different heights
                for (int wave = 0; wave < 3; wave++) {
                    double waveY = y + wave * 0.7;
                    double waveRadius = 1.5 + wave * 0.8;
                    int particlesInWave = 20 + wave * 8;
                    
                    for (int i = 0; i < particlesInWave; i++) {
                        double angle = i * (Math.PI * 2 / particlesInWave) + wave * 0.5;
                        double waveX = x + Math.cos(angle) * waveRadius;
                        double waveZ = z + Math.sin(angle) * waveRadius;
                        
                        // Inner particles move outward
                        double velX = Math.cos(angle) * 0.15;
                        double velZ = Math.sin(angle) * 0.15;
                        
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            waveX, waveY, waveZ, 1, velX, 0.05, velZ, 0.12);
                    }
                }
                
                // Central updraft
                for (int i = 0; i < 20; i++) {
                    double offsetX = (world.random.nextDouble() - 0.5) * 0.8;
                    double offsetZ = (world.random.nextDouble() - 0.5) * 0.8;
                    double height = Math.max(0.0, Math.min(3.0, world.random.nextDouble() * 3.0));
                    
                    world.spawnParticles(ParticleTypes.SOUL,
                        x + offsetX, y + height, z + offsetZ, 1, 0.0, 0.2, 0.0, 0.1);
                }
                break;
        }
        
        // All patterns get some additional dramatic flair
        // Delayed secondary explosions
        world.getServer().execute(() -> {
            world.getServer().execute(() -> { // Double delay for timing
                // Mini secondary explosions around the area
                for (int i = 0; i < 4; i++) {
                    double secX = x + (world.random.nextDouble() - 0.5) * 4.0;
                    double secZ = z + (world.random.nextDouble() - 0.5) * 4.0;
                    
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        secX, y, secZ, 5, 0.3, 0.3, 0.3, 0.05);
                    
                    if (world.random.nextFloat() < 0.5f) {
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            secX, y + 0.5, secZ, 3, 0.2, 0.2, 0.2, 0.06);
                    }
                }
            });
        });
    }
    
    private static boolean onOwnerDeath(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof ServerPlayerEntity owner)) {
            return true; // Not a player, allow death
        }
        
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return true;
        }
        
        // Find nearby Cursed One pets
        List<MobEntity> cursedPets = findNearbyCursedOnePets(owner, serverWorld);
        if (cursedPets.isEmpty()) {
            return true; // No cursed pets nearby, allow death
        }
        
        // Check if any cursed pet can resurrect the owner
        MobEntity sacrificialPet = findBestResurrectionPet(cursedPets);
        if (sacrificialPet == null) {
            return true; // No suitable pet for resurrection
        }

        // Check resurrection chance
        float resurrectionChance = getResurrectionChance(sacrificialPet);
        if (resurrectionChance <= 0.0f || serverWorld.getRandom().nextFloat() > resurrectionChance) {
            return true; // Resurrection failed - allow death
        }

        // Perform the resurrection
        boolean resurrected = performAutoResurrection(owner, sacrificialPet, damageSource);
        
        if (resurrected) {
            return false; // Prevent death
        }
        
        return true; // Allow death if resurrection failed
    }

    /**
     * Prevent Cursed One pets from dying (immortality mechanic).
     * They enter a 15-second reanimation state instead of dying.
     */
    private static boolean onPetDeath(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (!(entity instanceof MobEntity mobEntity)) {
            return true; // Not a mob, allow death
        }

        PetComponent petComp = PetComponent.get(mobEntity);
        if (petComp == null) {
            return true; // No pet component, allow death
        }
        
        if (!petComp.hasRole(PetRoleType.CURSED_ONE)) {
            return true; // Not a Cursed One pet, allow death
        }

        // Debug logging
        woflo.petsplus.Petsplus.LOGGER.info("Cursed One pet {} is dying - Level: {}, Health: {}/{}", 
            mobEntity.hasCustomName() ? mobEntity.getCustomName().getString() : mobEntity.getType().getName().getString(),
            petComp.getLevel(), mobEntity.getHealth(), mobEntity.getMaxHealth());

        // Require level 15+ for immortality (matches doom echo unlock level)
        if (petComp.getLevel() < 15) {
            woflo.petsplus.Petsplus.LOGGER.info("Cursed One pet level {} is too low for immortality (needs 15+)", petComp.getLevel());
            return true; // Too low level for immortality, allow death
        }

        // Don't prevent death if already reanimating (prevents infinite loop)
        // Use synchronized check to prevent race conditions
        reanimationLock.lock();
        try {
            if (isReanimating(mobEntity)) {
                woflo.petsplus.Petsplus.LOGGER.info("Cursed One pet is already reanimating, allowing death");
                return true; // Allow death while reanimating
            }
        } finally {
            reanimationLock.unlock();
        }

        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            woflo.petsplus.Petsplus.LOGGER.warn("Cursed One pet death occurred in non-server world");
            return true;
        }

        // Check resurrection cooldown (15 seconds) - make atomic with synchronized block
        long currentTime = serverWorld.getTime();
        final boolean[] onCooldown = {false};
        
        reanimationLock.lock();
        try {
            Long lastResurrectTimeObj = petComp.getStateData("last_resurrect_time", Long.class);
            long lastResurrectTime = lastResurrectTimeObj != null ? lastResurrectTimeObj : 0L;
            long resurrectionCooldown = 15 * 20; // 15 seconds in ticks

            if (currentTime - lastResurrectTime < resurrectionCooldown) {
                long cooldownRemaining = resurrectionCooldown - (currentTime - lastResurrectTime);
                woflo.petsplus.Petsplus.LOGGER.info("Cursed One pet resurrection on cooldown for {} more ticks", cooldownRemaining);
                onCooldown[0] = true; // Still on cooldown, allow death this time
            }
        } finally {
            reanimationLock.unlock();
        }
        
        if (onCooldown[0]) {
            return true;
        }

        woflo.petsplus.Petsplus.LOGGER.info("Attempting to enter reanimation state for Cursed One pet");

        // Enter reanimation state instead of dying - use synchronized block to prevent race conditions
        boolean enteredReanimation = false;
        reanimationLock.lock();
        try {
            // Double-check that we're not already reanimating (prevent race condition)
            if (!isReanimating(mobEntity)) {
                enteredReanimation = enterReanimationState(mobEntity, petComp, damageSource, serverWorld);
                
                if (enteredReanimation) {
                    // Update resurrection timestamp atomically
                    petComp.setStateData("last_resurrect_time", currentTime);
                    woflo.petsplus.Petsplus.LOGGER.info("Successfully entered reanimation state - preventing death");
                }
            }
        } finally {
            reanimationLock.unlock();
        }

        if (enteredReanimation) {
            return false; // Prevent death - entering reanimation
        }

        woflo.petsplus.Petsplus.LOGGER.warn("Failed to enter reanimation state - allowing death");
        return true; // Allow death if reanimation failed
    }

    /**
     * Enter the reanimation state for a Cursed One pet.
     * The pet becomes untargetable and inactive for 15 seconds.
     */
    private static boolean enterReanimationState(MobEntity cursedPet, PetComponent petComp, DamageSource damageSource, ServerWorld world) {
        try {
            // Set health to 1 to keep the pet "alive" but very weak
            cursedPet.setHealth(1.0f);
            
            // Make the pet untargetable and unable to move/act
            cursedPet.setInvulnerable(true);
            cursedPet.setAiDisabled(true);
            
            // Determine reanimation window (default 15 seconds, extended if soul sacrifice active)
            int baseDuration = 300;
            int reanimationDuration = CursedOneSoulSacrificeManager.resolveReanimationDuration(cursedPet, baseDuration);

            // Apply visual status effects to show the pet is "dead but not dead"
            cursedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, reanimationDuration, 0));
            cursedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, reanimationDuration, 255));
            cursedPet.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, reanimationDuration, 255));

            long reanimationEndTime = world.getTime() + reanimationDuration;
            // Use atomic put operation for thread safety
            reanimatingPets.put(cursedPet.getUuid(), new ReanimationWindow(reanimationEndTime, reanimationDuration));

            // Visual and audio feedback for entering reanimation
            playReanimationStartFeedback(cursedPet, world);

            // Encased afterimage effect during resurrection buildup
            AfterimageManager.startEncasement(cursedPet, "cursed_reanimation", reanimationDuration);
            
            // Notify owner if nearby
            if (petComp.getOwner() != null && petComp.getOwner().getWorld() == world) {
                double distance = petComp.getOwner().distanceTo(cursedPet);
                if (distance <= 32) {
                    String petName = cursedPet.hasCustomName() ?
                        cursedPet.getCustomName().getString() :
                        cursedPet.getType().getName().getString();

                    petComp.getOwner().sendMessage(
                        Text.of("§8" + petName + " §5enters a reanimation state... §8Dark energies gather around it."),
                        false
                    );

                    petComp.getOwner().sendMessage(
                        Text.of("§8✦ §5Reanimating... §8✦"),
                        true
                    );

                    if (reanimationDuration > baseDuration && petComp.getOwner() instanceof ServerPlayerEntity owner) {
                        UIFeedbackManager.sendCursedReanimationExtended(owner, cursedPet, reanimationDuration / 20);
                    }
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Play reanimation start visual and audio feedback with algorithmic variation.
     */
    private static void playReanimationStartFeedback(MobEntity cursedPet, ServerWorld world) {
        double x = cursedPet.getX();
        double y = cursedPet.getY() + 0.5;
        double z = cursedPet.getZ();
        
        // Generate variation seed based on pet UUID
        long startSeed = cursedPet.getUuid().getLeastSignificantBits();
        world.random.setSeed(startSeed);
        int startPattern = Math.abs((int)(startSeed % 3)); // 3 different start patterns
        
        // Play varied start sounds based on pattern
        switch (startPattern) {
            case 0: // Deep, ominous start
                world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_HURT,
                    SoundCategory.NEUTRAL, 0.8f, 0.4f);
                world.playSound(null, x, y, z, SoundEvents.ENTITY_PHANTOM_DEATH,
                    SoundCategory.NEUTRAL, 0.6f, 0.6f);
                break;
            case 1: // Sharp, crackling start
                world.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_DEATH,
                    SoundCategory.NEUTRAL, 0.7f, 0.5f);
                world.playSound(null, x, y, z, SoundEvents.BLOCK_SOUL_SAND_BREAK,
                    SoundCategory.NEUTRAL, 0.8f, 0.8f);
                break;
            case 2: // Ethereal, otherworldly start
                world.playSound(null, x, y, z, SoundEvents.ENTITY_VEX_DEATH,
                    SoundCategory.NEUTRAL, 0.6f, 0.3f);
                world.playSound(null, x, y, z, SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT,
                    SoundCategory.NEUTRAL, 0.5f, 1.2f);
                break;
        }
        
        // Create varied initial collapse effects
        createVariedCollapseStart(world, x, y, z, startPattern);
    }
    
    /**
     * Create varied initial collapse effects when reanimation begins
     */
    private static void createVariedCollapseStart(ServerWorld world, double x, double y, double z, int pattern) {
        switch (pattern) {
            case 0: // Implosion effect - particles rush inward
                // Outer ring of particles moving inward
                for (int i = 0; i < 20; i++) {
                    double angle = i * (Math.PI * 2 / 20);
                    double startRadius = 3.0;
                    double startX = x + Math.cos(angle) * startRadius;
                    double startZ = z + Math.sin(angle) * startRadius;
                    double startY = y + (world.random.nextDouble() - 0.5) * 1.0;
                    
                    // Velocity toward center
                    double velX = -Math.cos(angle) * 0.2;
                    double velZ = -Math.sin(angle) * 0.2;
                    
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        startX, startY, startZ, 1, velX, -0.05, velZ, 0.15);
                }
                
                // Central void effect
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    x, y, z, 8, 0.8, 0.4, 0.8, 0.05);
                break;
                
            case 1: // Ground eruption - particles burst up from below
                // Multiple eruption points around the pet
                for (int i = 0; i < 6; i++) {
                    double angle = i * (Math.PI * 2 / 6);
                    double eruptX = x + Math.cos(angle) * 1.5;
                    double eruptZ = z + Math.sin(angle) * 1.5;
                    
                    // Particles shooting upward
                    for (int j = 0; j < 8; j++) {
                        double offsetX = (world.random.nextDouble() - 0.5) * 0.6;
                        double offsetZ = (world.random.nextDouble() - 0.5) * 0.6;
                        
                        world.spawnParticles(ParticleTypes.SOUL,
                            eruptX + offsetX, y - 0.5, eruptZ + offsetZ,
                            1, 0.0, 0.3, 0.0, 0.2);
                    }
                }
                
                // Central pillar
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    x, y - 0.3, z, 12, 0.5, 0.2, 0.5, 0.25);
                break;
                
            case 2: // Dimensional tear - reality warping effect
                // Horizontal ring of particles
                for (int ring = 0; ring < 3; ring++) {
                    double ringRadius = 1.0 + ring * 0.8;
                    double ringY = y + ring * 0.3;
                    
                    for (int i = 0; i < 16; i++) {
                        double angle = i * (Math.PI * 2 / 16) + ring * 0.3;
                        double ringX = x + Math.cos(angle) * ringRadius;
                        double ringZ = z + Math.sin(angle) * ringRadius;
                        
                        // Particles with chaotic motion
                        double velX = (world.random.nextDouble() - 0.5) * 0.3;
                        double velY = (world.random.nextDouble() - 0.5) * 0.2;
                        double velZ = (world.random.nextDouble() - 0.5) * 0.3;
                        
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            ringX, ringY, ringZ, 1, velX, velY, velZ, 0.1);
                    }
                }
                
                // Central distortion
                for (int i = 0; i < 15; i++) {
                    double offsetX = (world.random.nextDouble() - 0.5) * 2.0;
                    double offsetY = Math.max(0.0, Math.min(1.5, world.random.nextDouble() * 1.5));
                    double offsetZ = (world.random.nextDouble() - 0.5) * 2.0;
                    
                    world.spawnParticles(ParticleTypes.SOUL,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0.0, 0.0, 0.0, 0.02);
                }
                break;
        }
    }
    
    /**
     * Handle mount buff when pet resurrects itself (level 25+ feature).
     */
    private static void handlePetResurrectionMountBuff(PlayerEntity owner) {
        if (owner == null) return;

        // If owner is mounted, buff the mount with resistance
        if (owner.getVehicle() instanceof LivingEntity mount) {
            mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0)); // Resistance I for 60 ticks (3s)

            owner.sendMessage(
                Text.of("§8Your mount feels the dark protection from your pet's resurrection..."),
                true // Action bar
            );
        }
    }

    private static List<MobEntity> findNearbyCursedOnePets(PlayerEntity owner, ServerWorld world) {
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16), // 16 block radius
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && 
                       petComp.hasRole(PetRoleType.CURSED_ONE) &&
                       petComp.isOwnedBy(owner) &&
                       entity.isAlive() &&
                       petComp.getLevel() >= 15; // Must be at least level 15 to resurrect (doom echo unlock level)
            }
        );
    }
    
    private static MobEntity findBestResurrectionPet(List<MobEntity> cursedPets) {
        if (cursedPets.isEmpty()) {
            return null;
        }
        
        // Prefer higher level pets for resurrection
        MobEntity bestPet = null;
        int highestLevel = 0;
        
        for (MobEntity pet : cursedPets) {
            PetComponent petComp = PetComponent.get(pet);
            if (petComp != null && petComp.getLevel() > highestLevel) {
                bestPet = pet;
                highestLevel = petComp.getLevel();
            }
        }
        
        return bestPet;
    }
    
    private static boolean performAutoResurrection(ServerPlayerEntity owner, MobEntity cursedPet, DamageSource damageSource) {
        try {
            PetComponent petComp = PetComponent.get(cursedPet);
            if (petComp == null) {
                return false;
            }
            
            // Calculate resurrection health based on pet level
            float resurrectionHealth = calculateResurrectionHealth(petComp.getLevel());
            
            // Set owner health to resurrection amount
            owner.setHealth(resurrectionHealth);
            
            // Apply totem-like effects
            applyResurrectionEffects(owner);
            
            // Handle mount buff if owner is mounted and pet is level 25+ (auto-resurrect mount resistance unlock)
            if (petComp.getLevel() >= 25) {
                handleMountBuff(owner);
            }
            
            // Pet sacrifice - the pet dies in place of the owner
            sacrificePet(cursedPet, owner);
            
            // Trigger universal pet sacrifice advancement
            woflo.petsplus.state.PlayerAdvancementState state = woflo.petsplus.state.PlayerAdvancementState.getOrCreate(owner);
            state.incrementPetSacrificeCount();
            int petSacrifices = state.getPetSacrificeCount();
            woflo.petsplus.advancement.AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
                owner,
                woflo.petsplus.advancement.criteria.PetInteractionCriterion.INTERACTION_PET_SACRIFICE,
                petSacrifices
            );
            
            // Visual and audio feedback
            playResurrectionFeedback(owner, cursedPet);

            // Emit resurrection particles
            if (owner.getWorld() instanceof ServerWorld serverWorld) {
                woflo.petsplus.ui.FeedbackManager.emitFeedback("cursed_one_resurrect", owner, serverWorld);
            }

            // Give owner a brief period of invulnerability
            owner.timeUntilRegen = 40; // 2 seconds
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static float calculateResurrectionHealth(int petLevel) {
        // Base 4 hearts (8 health), +0.5 hearts per pet level, max 10 hearts
        float baseHealth = 8.0f;
        float levelBonus = (petLevel - 1) * 1.0f;
        
        return Math.min(20.0f, baseHealth + levelBonus);
    }
    
    private static void applyResurrectionEffects(ServerPlayerEntity owner) {
        // Apply totem-like effects
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1)); // Regen II for 45s
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));   // Absorption II for 5s
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0)); // Fire resistance for 40s
        
        // Cursed specific effects
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0)); // Nausea for 10s (cursed side effect)
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0)); // Darkness for 5s
    }
    
    private static void handleMountBuff(ServerPlayerEntity owner) {
        // If owner is mounted, buff the mount with resistance
        if (owner.getVehicle() instanceof LivingEntity mount) {
            mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1200, 0)); // Resistance I for 60s
            
            owner.sendMessage(
                Text.of("§8Your mount feels the dark protection..."),
                true // Action bar
            );
        }
    }
    
    private static void sacrificePet(MobEntity cursedPet, ServerPlayerEntity owner) {
        String petName = cursedPet.hasCustomName() ? 
            cursedPet.getCustomName().getString() : 
            cursedPet.getType().getName().getString();
        
        // Send farewell message
        owner.sendMessage(
            Text.of("§8" + petName + " §chas sacrificed itself to save you..."),
            false // Chat message
        );
        
        // Create dramatic death effect
        cursedPet.getWorld().playSound(
            null,
            cursedPet.getX(), cursedPet.getY(), cursedPet.getZ(),
            SoundEvents.ENTITY_WITHER_SPAWN,
            SoundCategory.NEUTRAL,
            0.5f,
            2.0f // Higher pitch for more dramatic effect
        );
        
        // Remove the pet component first to prevent death penalty
        PetComponent.remove(cursedPet);
        
        // Kill the pet
        cursedPet.damage((ServerWorld) cursedPet.getWorld(), cursedPet.getDamageSources().magic(), Float.MAX_VALUE);
    }
    
    private static void playResurrectionFeedback(ServerPlayerEntity owner, MobEntity cursedPet) {
        // Play resurrection sound (totem sound but lower pitch)
        owner.getWorld().playSound(
            null,
            owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.ITEM_TOTEM_USE,
            SoundCategory.PLAYERS,
            1.0f,
            0.8f // Lower pitch to sound more ominous
        );
        
        // Send resurrection message
        owner.sendMessage(
            Text.of("§8Dark forces have intervened... §cYou have been saved from death."),
            false // Chat message
        );
        
        // Action bar message
        owner.sendMessage(
            Text.of("§8✦ §cResurrected by cursed magic §8✦"),
            true // Action bar
        );
    }
    
    /**
     * Check if a cursed pet can resurrect its owner.
     */
    public static boolean canPetResurrectOwner(MobEntity cursedPet) {
        PetComponent petComp = PetComponent.get(cursedPet);
        if (petComp == null || !petComp.hasRole(PetRoleType.CURSED_ONE)) {
            return false;
        }
        
        return petComp.getLevel() >= 15 && cursedPet.isAlive();
    }
    
    /**
     * Get the resurrection chance for a cursed pet based on level.
     */
    public static float getResurrectionChance(MobEntity cursedPet) {
        // Input validation
        if (cursedPet == null) {
            return 0.0f;
        }
        
        PetComponent petComp = PetComponent.get(cursedPet);
        if (petComp == null) {
            return 0.0f;
        }
        
        int level = petComp.getLevel();
        if (level < 5) {
            return 0.0f; // Can't resurrect below level 5
        }
        
        // 80% base chance at level 5, +2% per level above 5, max 100%
        float baseChance = 0.8f;
        float levelBonus = Math.max(0, level - 5) * 0.02f;
        
        float chance = Math.min(1.0f, baseChance + levelBonus);
        
        // Bounds checking - ensure chance is between 0.0 and 1.0
        if (chance < 0.0f) {
            woflo.petsplus.Petsplus.LOGGER.warn("Resurrection chance calculated below 0.0: {}, clamping to 0.0", chance);
            return 0.0f;
        }
        if (chance > 1.0f) {
            woflo.petsplus.Petsplus.LOGGER.warn("Resurrection chance calculated above 1.0: {}, clamping to 1.0", chance);
            return 1.0f;
        }
        
        return chance;
    }

    /**
     * Handle bidirectional death: when owner actually dies, Cursed One pets should also die.
     * This represents the cursed bond between owner and pet.
     */
    private static void onOwnerActualDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayerEntity owner)) {
            return; // Not a player
        }

        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // Find all Cursed One pets owned by this player
        List<MobEntity> cursedPets = serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(128), // Large radius to find all pets
            petEntity -> {
                PetComponent petComp = PetComponent.get(petEntity);
                return petComp != null &&
                       petComp.hasRole(PetRoleType.CURSED_ONE) &&
                       petComp.isOwnedBy(owner) &&
                       petEntity.isAlive();
            }
        );

        // Kill all Cursed One pets (they share the cursed fate)
        for (MobEntity cursedPet : cursedPets) {
            killCursedPetWithOwner(cursedPet, owner, damageSource);
        }
    }

    /**
     * Kill a Cursed One pet when their owner dies, representing the cursed bond.
     */
    private static void killCursedPetWithOwner(MobEntity cursedPet, ServerPlayerEntity owner, DamageSource ownerDamageSource) {
        String petName = cursedPet.hasCustomName() ?
            cursedPet.getCustomName().getString() :
            cursedPet.getType().getName().getString();

        // Send message to nearby players about the cursed bond
        ServerWorld world = (ServerWorld) cursedPet.getWorld();
        List<ServerPlayerEntity> nearbyPlayers = world.getEntitiesByClass(
            ServerPlayerEntity.class,
            cursedPet.getBoundingBox().expand(32),
            player -> player.distanceTo(cursedPet) <= 32
        );

        for (ServerPlayerEntity nearbyPlayer : nearbyPlayers) {
            nearbyPlayer.sendMessage(
                Text.of("§8" + petName + " §7collapses as the cursed bond is severed..."),
                false // Chat message
            );
        }

        // Create cursed death effect
        world.playSound(
            null,
            cursedPet.getX(), cursedPet.getY(), cursedPet.getZ(),
            SoundEvents.ENTITY_WITHER_DEATH,
            SoundCategory.NEUTRAL,
            0.3f,
            0.5f // Very low pitch for ominous effect
        );

        // Spawn cursed death particles
        if (world instanceof ServerWorld serverWorld) {
            woflo.petsplus.ui.FeedbackManager.emitFeedback("cursed_one_death_bond", cursedPet, serverWorld);
        }

        // Remove the pet component first to prevent normal death penalty
        PetComponent.remove(cursedPet);

        // Kill the pet with magic damage (representing the cursed bond)
        cursedPet.damage(world, world.getDamageSources().magic(), Float.MAX_VALUE);
    }
}