package woflo.petsplus.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern status effects system for pet abilities and support auras.
 * Handles effect application, aura management, and visual feedback.
 */
public class PetsplusEffectManager {
    
    private static final Map<String, Long> lastAuraTick = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastEffectNotification = new ConcurrentHashMap<>();
    
    // Effect durations in ticks (20 ticks = 1 second)
    private static final int SHORT_EFFECT_DURATION = 120; // 6 seconds
    private static final int MEDIUM_EFFECT_DURATION = 200; // 10 seconds
    private static final int LONG_EFFECT_DURATION = 400; // 20 seconds
    
    // Aura pulse intervals
    private static final int SUPPORT_AURA_INTERVAL = 140; // 7 seconds
    private static final int NAP_TIME_AURA_INTERVAL = 120; // 6 seconds
    private static final int GUARDIAN_PULSE_INTERVAL = 160; // 8 seconds
    
    /**
     * Apply role-specific aura effects based on pet configuration and level.
     */
    public static void applyRoleAuraEffects(ServerWorld world, MobEntity pet, PetComponent petComp, PlayerEntity owner) {
        if (owner == null || !owner.isAlive() || !(owner instanceof ServerPlayerEntity serverPlayer)) return;
        
        PetRole role = petComp.getRole();
        String petKey = pet.getUuidAsString();
        long currentTime = world.getTime();
        switch (role) {
            case GUARDIAN -> applyGuardianAura(world, pet, petComp, serverPlayer, currentTime, petKey);
            case SUPPORT -> applySupportAura(world, pet, petComp, serverPlayer, currentTime, petKey);
            case STRIKER -> applyStrikerEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
            case SCOUT -> applyScoutEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
            case SKYRIDER -> applySkyriderEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
            case ENCHANTMENT_BOUND -> applyEnchantmentBoundEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
            case CURSED_ONE -> applyCursedOneEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
            case EEPY_EEPER -> applyEepyEeperAura(world, pet, petComp, serverPlayer, currentTime, petKey);
            case ECLIPSED -> applyEclipsedEffects(world, pet, petComp, serverPlayer, currentTime, petKey);
        }
    }
    
    /**
     * Guardian role aura - Resistance and protection effects.
     */
    private static void applyGuardianAura(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                         ServerPlayerEntity owner, long currentTime, String petKey) {
        String auraKey = petKey + "_guardian_pulse";
        
        if (shouldTriggerAura(auraKey, currentTime, GUARDIAN_PULSE_INTERVAL)) {
            int level = petComp.getLevel();
            
            // Base resistance aura
            if (level >= 3) {
                double distance = owner.distanceTo(pet);
                if (distance <= 8.0) {
                    // Apply Resistance I to owner
                    StatusEffectInstance resistance = new StatusEffectInstance(
                        StatusEffects.RESISTANCE, MEDIUM_EFFECT_DURATION, 0);
                    owner.addStatusEffect(resistance);
                    
                    // Enhanced effects at higher levels
                    if (level >= 12) {
                        // Add brief Absorption
                        StatusEffectInstance absorption = new StatusEffectInstance(
                            StatusEffects.ABSORPTION, SHORT_EFFECT_DURATION, 0);
                        owner.addStatusEffect(absorption);
                    }
                    
                    // Notify owner with action bar
                    notifyOwner(owner, currentTime, petKey + "_guardian", 
                        Text.literal(getPetName(pet) + " holds the line (+Resistance)").formatted(Formatting.BLUE));
                    
                    // Visual effects
                    emitGuardianParticles(world, pet.getPos(), owner.getPos());
                    
                    // Sound feedback
                    world.playSound(null, pet.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK.value(), 
                        SoundCategory.NEUTRAL, 0.3f, 1.2f);
                }
            }
        }
    }
    
    /**
     * Support role aura - Sitting AoE regeneration and potion effects.
     */
    private static void applySupportAura(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                        ServerPlayerEntity owner, long currentTime, String petKey) {
        String auraKey = petKey + "_support_pulse";
        
        if (shouldTriggerAura(auraKey, currentTime, SUPPORT_AURA_INTERVAL)) {
            int level = petComp.getLevel();
            double distance = owner.distanceTo(pet);
            double auraRadius = PetsPlusConfig.getInstance().getDouble("support", "auraRadius", 6.0);
            int minLevel = PetsPlusConfig.getInstance().getInt("support", "minLevel", 5);
            
            // Support AoE healing requires sitting pet
            if (distance <= auraRadius && level >= minLevel && isPetSitting(pet)) {
                // Check if pet has stored potion data (simulate potion carrier)
                String storedPotion = petComp.getStateData("storedPotion", String.class, "regeneration");
                
                // Find nearby living entities for AoE healing
                List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
                    LivingEntity.class,
                    pet.getBoundingBox().expand(auraRadius),
                    entity -> entity != pet && 
                             (entity instanceof PlayerEntity || 
                              (entity instanceof MobEntity mob && PetComponent.get(mob) != null))
                );
                
                // Apply diluted potion effect to all nearby entities
                RegistryEntry<StatusEffect> effect = getStatusEffectFromString(storedPotion);
                if (effect != null && !nearbyEntities.isEmpty()) {
                    for (LivingEntity entity : nearbyEntities) {
                        StatusEffectInstance dilutedEffect = new StatusEffectInstance(
                            effect, SHORT_EFFECT_DURATION, 0);
                        entity.addStatusEffect(dilutedEffect);
                    }
                    
                    // Also apply to pet if beneficial
                    if (isBeneficialEffect(effect)) {
                        pet.addStatusEffect(new StatusEffectInstance(
                            effect, SHORT_EFFECT_DURATION, 0));
                    }
                    
                    // Consume "sip" from stored potion
                    int sipsRemaining = petComp.getStateData("potionSips", Integer.class, 8);
                    if (sipsRemaining > 0) {
                        petComp.setStateData("potionSips", sipsRemaining - 1);
                    }
                    
                    // Notify and provide feedback
                    notifyOwner(owner, currentTime, petKey + "_support",
                        Text.literal(getPetName(pet) + " spreads " + capitalizeFirst(storedPotion) + " aura").formatted(Formatting.GREEN));
                    
                    // Scattered healing heart particles for AoE effect
                    emitSupportAoEParticles(world, pet.getPos(), auraRadius, nearbyEntities);
                    
                    // Soft healing sound
                    world.playSound(null, pet.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 
                        SoundCategory.NEUTRAL, 0.4f, 1.5f);
                }
            }
        }
    }
    
    /**
     * Eepy Eeper Nap Time aura - Regeneration for nearby entities.
     */
    private static void applyEepyEeperAura(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                          ServerPlayerEntity owner, long currentTime, String petKey) {
        String auraKey = petKey + "_nap_time";
        
        if (shouldTriggerAura(auraKey, currentTime, NAP_TIME_AURA_INTERVAL)) {
            int level = petComp.getLevel();
            
            // Nap Time aura at level 10+
            if (level >= 10 && isPetSitting(pet)) {
                double radius = PetsPlusConfig.getInstance().getDouble("eepy_eeper", "napRegenRadius", 4.0);
                
                // Find nearby living entities
                List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
                    LivingEntity.class,
                    pet.getBoundingBox().expand(radius),
                    entity -> entity != pet && 
                             (entity instanceof PlayerEntity || 
                              (entity instanceof MobEntity mob && PetComponent.get(mob) != null))
                );
                
                // Apply Regeneration I to all nearby entities
                for (LivingEntity entity : nearbyEntities) {
                    entity.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, SHORT_EFFECT_DURATION, 0));
                }
                
                if (!nearbyEntities.isEmpty()) {
                    notifyOwner(owner, currentTime, petKey + "_nap",
                        Text.literal(getPetName(pet) + "'s cozy presence spreads").formatted(Formatting.AQUA));
                    
                    // Sleepy particle effects
                    emitNapTimeParticles(world, pet.getPos(), radius);
                    
                    // Peaceful ambient sound
                    world.playSound(null, pet.getBlockPos(), SoundEvents.BLOCK_WOOL_PLACE, 
                        SoundCategory.NEUTRAL, 0.3f, 0.8f);
                }
            }
        }
    }
    
    /**
     * Apply combat enhancement effects for Striker pets.
     */
    private static void applyStrikerEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                           ServerPlayerEntity owner, long currentTime, String petKey) {
        // Striker effects are typically triggered by combat events rather than passive auras
        // This could be expanded to include momentum buffs or combat readiness indicators
        
        int level = petComp.getLevel();
        if (level >= 7) {
            // Check if owner is in combat recently
            long lastCombat = petComp.getStateData("lastCombatTime", Long.class, 0L);
            if (currentTime - lastCombat < 200) { // Within 10 seconds of combat
                // Apply brief Speed boost during active combat
                if (owner.distanceTo(pet) <= 10.0) {
                    StatusEffectInstance speed = new StatusEffectInstance(
                        StatusEffects.SPEED, 100, 0);
                    owner.addStatusEffect(speed);
                    
                    // Combat readiness particles
                    if (currentTime % 40 == 0) { // Every 2 seconds
                        emitCombatReadinessParticles(world, pet.getPos());
                    }
                }
            }
        }
    }
    
    /**
     * Apply utility effects for other pet roles.
     */
    private static void applyScoutEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                         ServerPlayerEntity owner, long currentTime, String petKey) {
        // Scout provides vision and movement benefits
        int level = petComp.getLevel();
        if (level >= 5 && owner.distanceTo(pet) <= 12.0) {
            // Night Vision during night time
            if (world.isNight()) {
                StatusEffectInstance nightVision = new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, LONG_EFFECT_DURATION, 0);
                owner.addStatusEffect(nightVision);
            }
        }
    }
    
    private static void applySkyriderEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                            ServerPlayerEntity owner, long currentTime, String petKey) {
        // Skyrider provides flight and mobility assistance - handled by specific triggers
    }
    
    private static void applyEnchantmentBoundEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                                    ServerPlayerEntity owner, long currentTime, String petKey) {
        // Enchantment Bound provides mining and durability benefits - handled by specific events
    }
    
    private static void applyCursedOneEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                             ServerPlayerEntity owner, long currentTime, String petKey) {
        // Cursed One provides risk/reward mechanics - handled by damage and death events
    }
    
    private static void applyEclipsedEffects(ServerWorld world, MobEntity pet, PetComponent petComp, 
                                           ServerPlayerEntity owner, long currentTime, String petKey) {
        // Eclipsed provides void-themed utility - handled by specific triggers
    }
    
    // ============ UTILITY METHODS ============
    
    private static boolean shouldTriggerAura(String auraKey, long currentTime, int interval) {
        Long lastTick = lastAuraTick.get(auraKey);
        if (lastTick == null || currentTime - lastTick >= interval) {
            lastAuraTick.put(auraKey, currentTime);
            return true;
        }
        return false;
    }
    
    private static void notifyOwner(ServerPlayerEntity owner, long currentTime, String notificationKey, Text message) {
        Long lastNotif = lastEffectNotification.get(notificationKey);
        if (lastNotif == null || currentTime - lastNotif >= 200) { // Max once per 10 seconds
            owner.sendMessage(message, true); // Send to action bar
            lastEffectNotification.put(notificationKey, currentTime);
        }
    }
    
    private static String getPetName(MobEntity pet) {
        return pet.hasCustomName() ? pet.getCustomName().getString() : "Pet";
    }
    
    private static boolean isPetSitting(MobEntity pet) {
        if (pet instanceof net.minecraft.entity.passive.TameableEntity tameable) {
            return tameable.isSitting();
        }
        return false;
    }
    
    private static RegistryEntry<StatusEffect> getStatusEffectFromString(String effectName) {
        return switch (effectName.toLowerCase()) {
            case "regeneration" -> StatusEffects.REGENERATION;
            case "speed" -> StatusEffects.SPEED;
            case "strength" -> StatusEffects.STRENGTH;
            case "resistance" -> StatusEffects.RESISTANCE;
            case "fire_resistance" -> StatusEffects.FIRE_RESISTANCE;
            case "water_breathing" -> StatusEffects.WATER_BREATHING;
            case "night_vision" -> StatusEffects.NIGHT_VISION;
            case "invisibility" -> StatusEffects.INVISIBILITY;
            case "absorption" -> StatusEffects.ABSORPTION;
            default -> StatusEffects.REGENERATION; // Safe fallback
        };
    }
    
    private static boolean isBeneficialEffect(RegistryEntry<StatusEffect> effect) {
        return effect == StatusEffects.REGENERATION ||
               effect == StatusEffects.SPEED ||
               effect == StatusEffects.STRENGTH ||
               effect == StatusEffects.RESISTANCE ||
               effect == StatusEffects.FIRE_RESISTANCE ||
               effect == StatusEffects.WATER_BREATHING ||
               effect == StatusEffects.NIGHT_VISION ||
               effect == StatusEffects.INVISIBILITY ||
               effect == StatusEffects.ABSORPTION;
    }
    
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    // ============ PARTICLE EFFECTS ============
    
    private static void emitGuardianParticles(ServerWorld world, Vec3d petPos, Vec3d ownerPos) {
        // Blue protective particles around pet
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double x = petPos.x + Math.cos(angle) * 1.5;
            double z = petPos.z + Math.sin(angle) * 1.5;
            world.spawnParticles(ParticleTypes.ENCHANT, x, petPos.y + 1, z, 1, 0, 0, 0, 0.02);
        }
    }
    
    private static void emitSupportAoEParticles(ServerWorld world, Vec3d petPos, double radius, List<LivingEntity> nearbyEntities) {
        // Configuration-driven particle parameters
        double particleDensity = PetsPlusConfig.getInstance().getDouble("support", "particleDensity", 0.4);
        double particleHeight = PetsPlusConfig.getInstance().getDouble("support", "particleHeight", 2.5);
        double particleSpeed = PetsPlusConfig.getInstance().getDouble("support", "particleSpeed", 0.025);
        int minParticles = PetsPlusConfig.getInstance().getInt("support", "minParticles", 4);
        int maxParticles = PetsPlusConfig.getInstance().getInt("support", "maxParticles", 16);
        double swirlfactor = PetsPlusConfig.getInstance().getDouble("support", "swirlFactor", 0.8);
        double companionChance = PetsPlusConfig.getInstance().getDouble("support", "companionChance", 0.3);
        
        // === SOPHISTICATED DETERMINISTIC SEEDING ===
        // Create seeds based on world time, position, and pet identity for natural variation
        long worldTime = world.getTime();
        long positionSeed = (long)(petPos.x * 1000) ^ (long)(petPos.z * 1000) ^ (long)(petPos.y * 100);
        long timeSeed = (worldTime / 20) ^ ((worldTime % 20) << 8); // Slow cycle + fast variation
        long masterSeed = positionSeed ^ timeSeed;
        
        // Create deterministic but organic time variation
        double timePhase = (worldTime % 200) / 200.0; // 10-second cycle
        double timeOffset = Math.sin(timePhase * Math.PI * 2) * 0.3 + 0.7; // 0.4 to 1.0 range
        
        // Calculate sophisticated particle count with seeded variation
        int baseParticleCount = Math.max(minParticles, (int)(radius * radius * particleDensity * timeOffset));
        int totalParticles = Math.min(maxParticles, baseParticleCount + nearbyEntities.size());
        
        // === MAIN GROUND-RISING SWIRLING HEARTS ===
        for (int i = 0; i < totalParticles; i++) {
            // Create per-particle deterministic seed
            long particleSeed = masterSeed ^ (i * 0x9E3779B9L); // Golden ratio multiplier for distribution
            java.util.Random particleRandom = new java.util.Random(particleSeed);
            
            // Golden ratio spiral with deterministic variation
            double goldenAngle = 2.3999632297286533; // Precise golden angle
            double angle = i * goldenAngle + timePhase * 0.5;
            double radiusRatio = Math.sqrt((i + timePhase) / totalParticles);
            double currentRadius = radius * 0.75 * radiusRatio;
            
            // Position with deterministic but natural-feeling randomization
            double groundX = petPos.x + Math.cos(angle) * currentRadius + 
                           (particleRandom.nextDouble() - 0.5) * 0.4;
            double groundZ = petPos.z + Math.sin(angle) * currentRadius + 
                           (particleRandom.nextDouble() - 0.5) * 0.4;
            double groundY = petPos.y - 0.2 + particleRandom.nextDouble() * 0.1;
            
            // Deterministic spiral motion parameters
            double spiralTightness = swirlfactor * (0.6 + particleRandom.nextDouble() * 0.8);
            double riseSpeed = particleSpeed * (1.2 + particleRandom.nextDouble() * 0.6);
            double maxRiseHeight = particleHeight * (0.7 + particleRandom.nextDouble() * 0.6);
            
            // Deterministic stage count with consistent seeding
            int stages = 3 + (int)(particleRandom.nextDouble() * 3); // 3-5 stages per particle
            
            for (int stage = 0; stage < stages; stage++) {
                double stageProgress = stage / (double)stages;
                double heightProgress = Math.pow(stageProgress, 0.7); // Ease-out curve
                
                // Spiral position calculation with time-based phase shift
                double stageHeight = groundY + heightProgress * maxRiseHeight;
                double spiralAngle = angle + stageProgress * spiralTightness * Math.PI * 2 + timePhase * 0.2;
                double spiralRadius = currentRadius * (1.0 - stageProgress * 0.3);
                
                double x = groundX + Math.cos(spiralAngle) * spiralRadius * 0.2;
                double z = groundZ + Math.sin(spiralAngle) * spiralRadius * 0.2;
                
                // Velocity with deterministic variation
                double velX = Math.cos(spiralAngle + Math.PI/2) * particleSpeed * 0.4;
                double velY = riseSpeed * (1.0 - stageProgress * 0.5);
                double velZ = Math.sin(spiralAngle + Math.PI/2) * particleSpeed * 0.4;
                
                // Spawn main heart with spiral motion
                world.spawnParticles(ParticleTypes.HEART, x, stageHeight, z, 1, 
                                   velX, velY, velZ, particleSpeed * 0.8);
                
                // === COMPANION PARTICLE SYSTEM (DETERMINISTIC) ===
                if (particleRandom.nextDouble() < companionChance && stage < stages - 1) {
                    double companionAngle = spiralAngle + Math.PI + particleRandom.nextDouble() * Math.PI;
                    double companionDistance = 0.3 + particleRandom.nextDouble() * 0.2;
                    
                    double compX = x + Math.cos(companionAngle) * companionDistance;
                    double compZ = z + Math.sin(companionAngle) * companionDistance;
                    double compY = stageHeight + (particleRandom.nextDouble() - 0.5) * 0.2;
                    
                    // Orbital motion around the main particle
                    double orbitalVelX = Math.cos(companionAngle + Math.PI/2) * particleSpeed * 0.6;
                    double orbitalVelZ = Math.sin(companionAngle + Math.PI/2) * particleSpeed * 0.6;
                    
                    world.spawnParticles(ParticleTypes.HEART, compX, compY, compZ, 1,
                                       orbitalVelX, riseSpeed * 0.8, orbitalVelZ, particleSpeed * 0.5);
                }
            }
        }
        
        // === ENTITY-SPECIFIC DETERMINISTIC EFFECTS ===
        for (LivingEntity entity : nearbyEntities) {
            Vec3d entityPos = entity.getPos();
            double entityHeight = entity.getBoundingBox().getLengthY();
            double entityWidth = Math.max(0.4, entity.getBoundingBox().getAverageSideLength() * 0.5);
            
            // Create deterministic seed based on entity UUID and world time
            long entitySeed = entity.getUuid().getMostSignificantBits() ^ 
                             entity.getUuid().getLeastSignificantBits() ^ 
                             (worldTime / 10); // Change every half second
            
            int playerParticles = PetsPlusConfig.getInstance().getInt("support", "particlesPerEntity", 3);
            double subtleIntensity = PetsPlusConfig.getInstance().getDouble("support", "subtleIntensity", 0.7);
            
            for (int i = 0; i < playerParticles; i++) {
                // Deterministic particle type selection
                long particleTypeSeed = entitySeed ^ (i * 0x517CC1B727220A95L);
                java.util.Random typeRandom = new java.util.Random(particleTypeSeed);
                double particleType = typeRandom.nextDouble();
                
                if (particleType < 0.4) {
                    // === FEET EFFECT: Gentle hearts at ground level ===
                    double footAngle = typeRandom.nextDouble() * Math.PI * 2;
                    double footRadius = entityWidth * (0.8 + typeRandom.nextDouble() * 0.4);
                    double footX = entityPos.x + Math.cos(footAngle) * footRadius;
                    double footZ = entityPos.z + Math.sin(footAngle) * footRadius;
                    double footY = entityPos.y + 0.05 + typeRandom.nextDouble() * 0.1;
                    
                    world.spawnParticles(ParticleTypes.HEART, footX, footY, footZ, 1,
                                       0, particleSpeed * subtleIntensity, 0, particleSpeed * 0.3);
                    
                } else if (particleType < 0.7) {
                    // === AROUND BODY EFFECT: Hearts orbiting the entity ===
                    double bodyAngle = typeRandom.nextDouble() * Math.PI * 2 + timePhase * Math.PI;
                    double bodyHeight = entityPos.y + entityHeight * (0.3 + typeRandom.nextDouble() * 0.4);
                    double bodyRadius = entityWidth * (1.2 + typeRandom.nextDouble() * 0.3);
                    
                    double bodyX = entityPos.x + Math.cos(bodyAngle) * bodyRadius;
                    double bodyZ = entityPos.z + Math.sin(bodyAngle) * bodyRadius;
                    
                    // Circular orbital motion with time-based phase
                    double orbitalVelX = Math.cos(bodyAngle + Math.PI/2) * particleSpeed * subtleIntensity * 0.8;
                    double orbitalVelZ = Math.sin(bodyAngle + Math.PI/2) * particleSpeed * subtleIntensity * 0.8;
                    
                    world.spawnParticles(ParticleTypes.HEART, bodyX, bodyHeight, bodyZ, 1,
                                       orbitalVelX, particleSpeed * 0.2, orbitalVelZ, particleSpeed * 0.4);
                    
                } else {
                    // === ABOVE HEAD EFFECT: Gentle hearts floating down ===
                    double headX = entityPos.x + (typeRandom.nextDouble() - 0.5) * entityWidth;
                    double headZ = entityPos.z + (typeRandom.nextDouble() - 0.5) * entityWidth;
                    double headY = entityPos.y + entityHeight + 0.3 + typeRandom.nextDouble() * 0.4;
                    
                    // Gentle downward and sideways drift with deterministic variation
                    double driftX = (typeRandom.nextDouble() - 0.5) * particleSpeed * subtleIntensity;
                    double driftZ = (typeRandom.nextDouble() - 0.5) * particleSpeed * subtleIntensity;
                    
                    world.spawnParticles(ParticleTypes.HEART, headX, headY, headZ, 1,
                                       driftX, -particleSpeed * subtleIntensity * 0.6, driftZ, particleSpeed * 0.3);
                }
            }
            
            // === SPECIAL EFFECT: Deterministic healing pulse for injured entities ===
            if (entity instanceof LivingEntity living && living.getHealth() < living.getMaxHealth() * 0.4) {
                // Use health-based seed for consistency
                long healthSeed = entitySeed ^ (long)(living.getHealth() * 1000);
                java.util.Random healthRandom = new java.util.Random(healthSeed);
                
                if (healthRandom.nextDouble() < 0.15) { // 15% chance for injured entities
                    int pulseParticles = 6 + healthRandom.nextInt(4);
                    for (int p = 0; p < pulseParticles; p++) {
                        double pulseAngle = (p / (double)pulseParticles) * Math.PI * 2 + timePhase * 0.3;
                        double pulseRadius = entityWidth * 1.5;
                        double pulseX = entityPos.x + Math.cos(pulseAngle) * pulseRadius;
                        double pulseZ = entityPos.z + Math.sin(pulseAngle) * pulseRadius;
                        double pulseY = entityPos.y + entityHeight * 0.5;
                        
                        // Expanding ring motion with time-synchronized phase
                        double expandVelX = Math.cos(pulseAngle) * particleSpeed * 1.2;
                        double expandVelZ = Math.sin(pulseAngle) * particleSpeed * 1.2;
                        
                        world.spawnParticles(ParticleTypes.HEART, pulseX, pulseY, pulseZ, 1,
                                           expandVelX, 0, expandVelZ, particleSpeed * 0.6);
                    }
                }
            }
        }
    }
    
    private static void emitNapTimeParticles(ServerWorld world, Vec3d petPos, double radius) {
        // Sleepy Z particles in a circle
        int particleCount = (int)(radius * 3);
        for (int i = 0; i < particleCount; i++) {
            double angle = i * 2 * Math.PI / particleCount;
            double x = petPos.x + Math.cos(angle) * radius * 0.8;
            double z = petPos.z + Math.sin(angle) * radius * 0.8;
            world.spawnParticles(ParticleTypes.CLOUD, x, petPos.y + 0.5, z, 1, 0, 0.1, 0, 0.01);
        }
    }
    
    private static void emitCombatReadinessParticles(ServerWorld world, Vec3d petPos) {
        // Red sparks indicating combat readiness
        for (int i = 0; i < 4; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 1.0;
            double offsetZ = (world.random.nextDouble() - 0.5) * 1.0;
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, 
                petPos.x + offsetX, petPos.y + 1, petPos.z + offsetZ, 1, 0, 0, 0, 0.01);
        }
    }
    
    /**
     * Clean up old tracking data to prevent memory leaks.
     */
    public static void cleanup() {
        long currentTime = System.currentTimeMillis();
        
        // Remove entries older than 5 minutes
        lastAuraTick.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 300000);
        lastEffectNotification.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 300000);
    }
}