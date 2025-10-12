package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.TributeHandler;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized feedback system for visual and audio effects throughout the mod.
 * Handles both ambient role particles and ability-triggered feedback.
 */
public class FeedbackManager {

    private static final int AMBIENT_PARTICLE_INTERVAL = 80; // 4 seconds
    // Debounce map: last emit tick per source key (debounce)
    private static final ConcurrentHashMap<Object, Long> LAST_EMIT_TICK = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService FEEDBACK_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "PetsPlus-Feedback");
        thread.setDaemon(true);
        return thread;
    });
    
    // Track pending tasks for proper cleanup
    private static final Map<String, ScheduledFuture<?>> PENDING_TASKS = new ConcurrentHashMap<>();
    private static final AtomicLong TASK_ID_GENERATOR = new AtomicLong(0);
    private static final AtomicBoolean IS_SHUTTING_DOWN = new AtomicBoolean(false);
    
    // Static initialization block with shutdown hook registration
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IS_SHUTTING_DOWN.set(true);
            cleanup();
        }, "PetsPlus-FeedbackShutdownHook"));
    }

    /**
     * Emit feedback for a specific event at an entity's location.
     */
    public static void emitFeedback(String eventName, Entity entity, ServerWorld world) {
        if (entity == null || world == null || world.isClient()) return;
        emitFeedback(eventName, entity.getEntityPos(), world, entity);
    }

    /**
     * Emit feedback for a specific event at a specific location.
     */
    public static void emitFeedback(String eventName, Vec3d position, ServerWorld world, Entity sourceEntity) {
        if (world == null || world.isClient()) return;
        var effect = FeedbackConfig.getFeedback(eventName);
        if (effect == null) return;

        // Per-source debounce using entity UUID if available; else use position+dimension key (debounce)
        Object key;
        if (sourceEntity != null) {
            key = sourceEntity.getUuid();
        } else {
            key = world.getRegistryKey().getValue().toString() + "@" + MathHelper.floor(position.x) + "," + MathHelper.floor(position.y) + "," + MathHelper.floor(position.z);
        }
        long now = world.getTime();
        long last = LAST_EMIT_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (now - last < 4L) {
            return;
        }
        LAST_EMIT_TICK.put(key, now);

        if (effect.delayTicks > 0) {
            scheduleDelayedFeedback(effect, position, world, sourceEntity);
        } else {
            runImmediateFeedback(effect, position, world, sourceEntity);
        }
    }

    private static void runImmediateFeedback(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                             ServerWorld world, Entity sourceEntity) {
        if (effect == null || world == null || world.isClient() || position == null) return;
        var server = world.getServer();
        if (server != null && !server.isOnThread()) {
            UUID sourceUuid = sourceEntity != null ? sourceEntity.getUuid() : null;
            server.execute(() -> executeImmediateFeedback(effect, position, world, resolveEntity(world, sourceUuid)));
            return;
        }

        executeImmediateFeedback(effect, position, world, sourceEntity);
    }

    private static void scheduleDelayedFeedback(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                                ServerWorld world, Entity sourceEntity) {
        if (effect == null || position == null || world == null || world.isClient()) return;
        var server = world.getServer();
        if (server == null || IS_SHUTTING_DOWN.get()) {
            return;
        }

        UUID sourceUuid = sourceEntity != null ? sourceEntity.getUuid() : null;
        long delayTicks = Math.max(1, effect.delayTicks);
        
        // Generate unique task ID for tracking
        String taskId = "feedback_" + TASK_ID_GENERATOR.incrementAndGet();
        
        try {
            ScheduledFuture<?> future = FEEDBACK_EXECUTOR.schedule(() -> {
                // Double-check server state before executing
                if (IS_SHUTTING_DOWN.get() || server.isStopped()) {
                    return;
                }
                
                UUID uuidCopy = sourceUuid;
                server.execute(() -> {
                    // Final check before executing on server thread
                    if (!IS_SHUTTING_DOWN.get() && !server.isStopped()) {
                        Entity resolvedSource = resolveEntity(world, uuidCopy);
                        executeImmediateFeedback(effect, position, world, resolvedSource);
                    }
                });
                
                // Remove task from tracking map after execution
                PENDING_TASKS.remove(taskId);
            }, delayTicks * 50L, TimeUnit.MILLISECONDS);
            
            // Track the task for potential cancellation
            PENDING_TASKS.put(taskId, future);
        } catch (RejectedExecutionException e) {
            // Handle executor shutdown case gracefully
            // Avoid noisy stderr; rely on server log if needed
        }
    }

    private static Entity resolveEntity(ServerWorld world, UUID uuid) {
        return uuid != null ? world.getEntity(uuid) : null;
    }

    /**
     * Emit role-specific ambient particles for a pet.
     */
    public static void emitRoleAmbientParticles(MobEntity pet, ServerWorld world, long currentTick) {
        if (world == null || world.isClient()) return;
        if (currentTick % AMBIENT_PARTICLE_INTERVAL != 0) return;

        var component = PetComponent.get(pet);
        if (component == null) return;

        // Don't emit during combat for cleaner visuals
        if (world.getTime() - component.getLastAttackTick() < 60) return;

        // Check for creator tag (dev crown) first
        Boolean hasCreatorTag = component.getStateData("special_tag_creator", Boolean.class, false);
        if (hasCreatorTag) {
            emitDevCrown(pet, world, currentTick);
            return; // Crown replaces regular ambient particles for extra special feel
        }

        Identifier roleId = component.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        if (roleType == null) {
            return;
        }

        PetRoleType.Visual visual = roleType.visual();
        String eventName = visual.ambientEvent();
        if (eventName == null || eventName.isEmpty()) {
            eventName = roleId.getPath() + "_ambient";
        }
        emitFeedback(eventName, pet, world);
    }

    /**
     * Emit the developer crown particle effect for creator pets (e.g., "woflo").
     * Creates a slowly rotating ring of END_ROD particles above the pet's head.
     */
    public static void emitDevCrown(MobEntity pet, ServerWorld world, long currentTick) {
        if (!pet.isAlive() || pet.isRemoved()) return;

        // Check if dev crown is enabled in config
        var config = woflo.petsplus.config.PetsPlusConfig.getInstance();
        if (!config.isDevCrownEnabled()) return;

        // Route through registry; pattern handles ring + optional sparkles budgeted per ambient cadence
        emitFeedback("dev_crown_ambient", pet, world);
    }

    /**
     * Check if an entity should have ambient particles (used by existing ParticleEffectManager).
     */
    public static boolean shouldEmitAmbientParticles(MobEntity pet, ServerWorld world) {
        if (world == null || world.isClient()) return false;
        var component = PetComponent.get(pet);
        if (component == null) return false;

        // Don't emit if in recent combat
        long lastAttack = component.getLastAttackTick();
        if (world.getTime() - lastAttack < 60) return false;

        return pet.isAlive() && !pet.isRemoved();
    }

    private static void executeImmediateFeedback(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                               ServerWorld world, Entity sourceEntity) {
        if (effect == null || world == null || world.isClient() || position == null) return;
        // Emit particles with per-call total budget clamp (budget)
        final int MAX_BUDGET = 48;
        int totalPlanned = 0;
        if (effect.particles != null) {
            for (var pc : effect.particles) {
                if (pc != null) {
                    totalPlanned += Math.max(0, pc.count);
                }
            }
        }
        float scale = 1.0f;
        if (totalPlanned > MAX_BUDGET && totalPlanned > 0) {
            scale = (float) MAX_BUDGET / (float) totalPlanned;
        }
        int spent = 0;
        if (effect.particles != null) {
            for (var particleConfig : effect.particles) {
                if (particleConfig == null) continue;
                // Scale count proportionally, ensure at least 1 if originally >0 and budget remains
                int scaled = particleConfig.count;
                if (scale < 1.0f) {
                    scaled = Math.max(1, Math.round(particleConfig.count * scale));
                }
                int remaining = MAX_BUDGET - spent;
                if (remaining <= 0) break;
                int capped = Math.min(scaled, remaining);
                if (capped <= 0) continue;
                // Emit using override count without constructing new ParticleConfig
                emitParticlePatternWithCount(particleConfig, capped, position, world, sourceEntity);
                spent += capped;
            }
        }

        // Play audio
        if (effect.audio != null) {
            world.playSound(null, position.x, position.y, position.z,
                          effect.audio.sound, SoundCategory.NEUTRAL,
                          effect.audio.volume, effect.audio.pitch);
        }
    }

    // Internal helper to emit a pattern using an override count without modifying config (budget)
    private static void emitParticlePatternWithCount(FeedbackConfig.ParticleConfig config, int overrideCount,
                                                    Vec3d position, ServerWorld world, Entity sourceEntity) {
        if (config == null || world == null || world.isClient() || position == null) return;
        double entitySizeMultiplier = 1.0;
        if (config.adaptToEntitySize && sourceEntity != null) {
            entitySizeMultiplier = Math.max(0.5, Math.min(2.0, sourceEntity.getWidth()));
        }
        double effectiveRadius = config.radius * entitySizeMultiplier;
        // Dispatch based on pattern, mirroring emitParticlePattern but using overrideCount
        switch (config.pattern.toLowerCase()) {
            case "circle" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = (i / (double) Math.max(1, overrideCount)) * 2 * Math.PI;
                    double x = position.x + Math.cos(angle) * effectiveRadius;
                    double z = position.z + Math.sin(angle) * effectiveRadius;
                    world.spawnParticles(config.type, x, position.y + config.offsetY, z,
                            1, config.offsetX, 0, config.offsetZ, config.speed);
                }
            }
            case "burst" -> {
                world.spawnParticles(config.type, position.x, position.y + config.offsetY, position.z,
                        overrideCount, config.offsetX, config.offsetY, config.offsetZ, config.speed);
            }
            case "line" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double progress = overrideCount == 1 ? 0.5 : i / (double) (overrideCount - 1);
                    double x = position.x + (progress - 0.5) * effectiveRadius;
                    world.spawnParticles(config.type, x, position.y + config.offsetY, position.z,
                            1, config.offsetX, 0, config.offsetZ, config.speed);
                }
            }
            case "area" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * 2 * Math.PI;
                    double r = world.getRandom().nextDouble() * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    world.spawnParticles(config.type, x, position.y + config.offsetY, z,
                            1, config.offsetX, config.offsetY / 2, config.offsetZ, config.speed);
                }
            }
            case "spiral" -> {
                double time = world.getTime() * 0.1;
                for (int i = 0; i < overrideCount; i++) {
                    double angle = time + (i * Math.PI * 2 / Math.max(1, overrideCount));
                    double x = position.x + Math.cos(angle) * effectiveRadius;
                    double z = position.z + Math.sin(angle) * effectiveRadius;
                    double y = position.y + config.offsetY + Math.sin(angle) * 0.1;
                    world.spawnParticles(config.type, x, y, z,
                            1, config.offsetX, 0, config.offsetZ, config.speed);
                }
            }
            case "upward" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double offsetX = (world.getRandom().nextDouble() - 0.5) * config.offsetX * 2;
                    double offsetZ = (world.getRandom().nextDouble() - 0.5) * config.offsetZ * 2;
                    world.spawnParticles(config.type,
                            position.x + offsetX, position.y - 0.2, position.z + offsetZ,
                            1, config.offsetX, 0.0, config.offsetZ, config.speed);
                }
            }
            case "plus" -> {
                double size = effectiveRadius;
                world.spawnParticles(config.type, position.x - size/2, position.y + config.offsetY, position.z,
                        1, config.offsetX, 0, config.offsetZ, config.speed);
                world.spawnParticles(config.type, position.x + size/2, position.y + config.offsetY, position.z,
                        1, config.offsetX, 0, config.offsetZ, config.speed);
                world.spawnParticles(config.type, position.x, position.y + config.offsetY, position.z - size/2,
                        1, config.offsetX, 0, config.offsetZ, config.speed);
                world.spawnParticles(config.type, position.x, position.y + config.offsetY, position.z + size/2,
                        1, config.offsetX, 0, config.offsetZ, config.speed);
            }
            case "z_pattern" -> {
                double time = world.getTime() * 0.05;
                for (int i = 0; i < overrideCount; i++) {
                    double progress = (i / (double) Math.max(1, overrideCount) + time) % 1.0;
                    double x = position.x + (progress - 0.5) * effectiveRadius;
                    double y = position.y + config.offsetY + Math.sin(progress * Math.PI) * 0.2;
                    double z = position.z + Math.cos(progress * Math.PI * 2) * 0.1;
                    world.spawnParticles(config.type, x, y, z,
                            1, config.offsetX, 0, config.offsetZ, config.speed);
                }
            }
            case "random" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * Math.PI * 2;
                    double r = world.getRandom().nextDouble() * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY + world.getRandom().nextDouble() * 0.3;
                    world.spawnParticles(config.type, x, y, z,
                            1, config.offsetX, config.offsetY, config.offsetZ, config.speed);
                }
            }
            case "aura_radius_ground" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(world.getRandom().nextDouble()) * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY + world.getRandom().nextDouble() * 0.1;
                    world.spawnParticles(config.type, x, y, z,
                            1, config.offsetX, 0.02, config.offsetZ, config.speed);
                }
            }
            case "aura_radius_edge" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double baseAngle = (i / (double) Math.max(1, overrideCount)) * Math.PI * 2;
                    double angle = baseAngle + (world.getRandom().nextDouble() - 0.5) * 0.3;
                    double r = effectiveRadius + (world.getRandom().nextDouble() - 0.5) * 0.5;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY + world.getRandom().nextDouble() * 0.2;
                    world.spawnParticles(config.type, x, y, z,
                            1, config.offsetX, config.offsetY, config.offsetZ, config.speed);
                }
            }
            case "orbital_single" -> emitOrbitalSingle(config, position, world, sourceEntity);
            case "orbital_dual" -> emitOrbitalDual(config, position, world, sourceEntity);
            case "orbital_triple" -> emitOrbitalTriple(config, position, world, sourceEntity);
            default -> {
                // fallback to burst
                world.spawnParticles(config.type, position.x, position.y + config.offsetY, position.z,
                        overrideCount, config.offsetX, config.offsetY, config.offsetZ, config.speed);
            }
        }
    }

    private static void emitParticlePattern(FeedbackConfig.ParticleConfig config, Vec3d position,
                                          ServerWorld world, Entity sourceEntity) {
        if (config == null || world == null || world.isClient() || position == null) return;
        double entitySizeMultiplier = 1.0;
        if (config.adaptToEntitySize && sourceEntity != null) {
            entitySizeMultiplier = Math.max(0.5, Math.min(2.0, sourceEntity.getWidth()));
        }

        double effectiveRadius = config.radius * entitySizeMultiplier;

        switch (config.pattern.toLowerCase()) {
            case "circle" -> emitCirclePattern(config, position, world, effectiveRadius);
            case "burst" -> emitBurstPattern(config, position, world);
            case "line" -> emitLinePattern(config, position, world, effectiveRadius);
            case "area" -> emitAreaPattern(config, position, world, effectiveRadius);
            case "spiral" -> emitSpiralPattern(config, position, world, effectiveRadius);
            case "upward" -> emitUpwardPattern(config, position, world);
            case "plus" -> emitPlusPattern(config, position, world, effectiveRadius);
            case "z_pattern" -> emitZPattern(config, position, world, effectiveRadius);
            case "random" -> emitRandomPattern(config, position, world, effectiveRadius);
            case "aura_radius_ground" -> emitAuraRadiusGround(config, position, world, effectiveRadius);
            case "aura_radius_edge" -> emitAuraRadiusEdge(config, position, world, effectiveRadius);
            case "orbital_single" -> emitOrbitalSingle(config, position, world, sourceEntity);
            case "orbital_dual" -> emitOrbitalDual(config, position, world, sourceEntity);
            case "orbital_triple" -> emitOrbitalTriple(config, position, world, sourceEntity);
            default -> emitBurstPattern(config, position, world);
        }
    }

    private static void emitCirclePattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count; i++) {
            double angle = (i / (double) config.count) * 2 * Math.PI;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            world.spawnParticles(config.type, x, pos.y + config.offsetY, z,
                               1, config.offsetX, 0, config.offsetZ, config.speed);
        }
    }

    private static void emitBurstPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world) {
        world.spawnParticles(config.type, pos.x, pos.y + config.offsetY, pos.z,
                           config.count, config.offsetX, config.offsetY, config.offsetZ, config.speed);
    }

    private static void emitLinePattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double length) {
        for (int i = 0; i < config.count; i++) {
            double progress = i / (double) (config.count - 1);
            double x = pos.x + (progress - 0.5) * length;
            world.spawnParticles(config.type, x, pos.y + config.offsetY, pos.z,
                               1, config.offsetX, 0, config.offsetZ, config.speed);
        }
    }

    private static void emitAreaPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count; i++) {
            double angle = world.getRandom().nextDouble() * 2 * Math.PI;
            double r = world.getRandom().nextDouble() * radius;
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            world.spawnParticles(config.type, x, pos.y + config.offsetY, z,
                               1, config.offsetX, config.offsetY / 2, config.offsetZ, config.speed);
        }
    }

    private static void emitSpiralPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        double time = world.getTime() * 0.1;
        for (int i = 0; i < config.count; i++) {
            double angle = time + (i * Math.PI * 2 / config.count);
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + config.offsetY + Math.sin(angle) * 0.1;
            world.spawnParticles(config.type, x, y, z,
                               1, config.offsetX, 0, config.offsetZ, config.speed);
        }
    }

    private static void emitUpwardPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world) {
        for (int i = 0; i < config.count; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * config.offsetX * 2;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * config.offsetZ * 2;
            world.spawnParticles(config.type,
                               pos.x + offsetX, pos.y - 0.2, pos.z + offsetZ,
                               1, config.offsetX, 0.0, config.offsetZ, config.speed);
        }
    }

    private static void emitPlusPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double size) {
        // Horizontal line
        world.spawnParticles(config.type, pos.x - size/2, pos.y + config.offsetY, pos.z,
                           1, config.offsetX, 0, config.offsetZ, config.speed);
        world.spawnParticles(config.type, pos.x + size/2, pos.y + config.offsetY, pos.z,
                           1, config.offsetX, 0, config.offsetZ, config.speed);
        // Vertical line
        world.spawnParticles(config.type, pos.x, pos.y + config.offsetY, pos.z - size/2,
                           1, config.offsetX, 0, config.offsetZ, config.speed);
        world.spawnParticles(config.type, pos.x, pos.y + config.offsetY, pos.z + size/2,
                           1, config.offsetX, 0, config.offsetZ, config.speed);
    }

    private static void emitZPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double size) {
        double time = world.getTime() * 0.05;
        for (int i = 0; i < config.count; i++) {
            double progress = (i / (double) config.count + time) % 1.0;
            double x = pos.x + (progress - 0.5) * size;
            double y = pos.y + config.offsetY + Math.sin(progress * Math.PI) * 0.2;
            double z = pos.z + Math.cos(progress * Math.PI * 2) * 0.1;
            world.spawnParticles(config.type, x, y, z,
                               1, config.offsetX, 0, config.offsetZ, config.speed);
        }
    }

    private static void emitRandomPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double r = world.getRandom().nextDouble() * radius;
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            double y = pos.y + config.offsetY + world.getRandom().nextDouble() * 0.3;
            world.spawnParticles(config.type, x, y, z,
                               1, config.offsetX, config.offsetY, config.offsetZ, config.speed);
        }
    }

    /**
     * Emit particles in a scattered pattern across the ground within the aura radius.
     * Perfect for showing regeneration areas, gathering zones, etc.
     */
    private static void emitAuraRadiusGround(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count; i++) {
            // Random positions within the circular area
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double r = Math.sqrt(world.getRandom().nextDouble()) * radius; // Uniform distribution
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;

            // Ground-level particles with slight rise
            double y = pos.y + config.offsetY + world.getRandom().nextDouble() * 0.1;

            world.spawnParticles(config.type, x, y, z,
                               1, config.offsetX, 0.02, config.offsetZ, config.speed);
        }
    }

    /**
     * Emit particles around the edge/circumference of the aura radius.
     * Perfect for showing ability borders, detection ranges, etc.
     */
    private static void emitAuraRadiusEdge(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count; i++) {
            // Positions around the circumference with slight randomization
            double baseAngle = (i / (double) config.count) * Math.PI * 2;
            double angle = baseAngle + (world.getRandom().nextDouble() - 0.5) * 0.3; // ±0.15 radians variance
            double r = radius + (world.getRandom().nextDouble() - 0.5) * 0.5; // ±0.25 block variance

            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            double y = pos.y + config.offsetY + world.getRandom().nextDouble() * 0.2;

            world.spawnParticles(config.type, x, y, z,
                               1, config.offsetX, config.offsetY, config.offsetZ, config.speed);
        }
    }

    /**
     * Convenience methods for common events
     */
    public static void emitGuardianDamageAbsorbed(MobEntity pet, ServerWorld world) {
        emitFeedback("guardian_damage_absorbed", pet, world);
    }

    public static void emitStrikerExecution(PlayerEntity owner, LivingEntity target, ServerWorld world,
                                            int stacks, float momentumFill) {
        if (target == null || world == null) {
            return;
        }

        Vec3d targetPos = target.getEntityPos();
        double centerY = woflo.petsplus.ui.PetUIHelper.getChestAnchorY(target);
        double spread = 0.12 + 0.04 * Math.min(stacks, 5);
        double verticalSpread = Math.max(0.1, target.getHeight() * 0.35);

        // Budget clamp
        int critCount = Math.min(8, 4 + Math.max(0, stacks) * 2);
        world.spawnParticles(ParticleTypes.CRIT, targetPos.x, centerY, targetPos.z,
                critCount, spread, verticalSpread * 0.6, spread, 0.18);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.25), target.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);

        if (stacks > 0) {
            int emberCount = MathHelper.clamp(2 + stacks * 2, 3, 8);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, targetPos.x, centerY, targetPos.z,
                    emberCount, spread * 0.6, verticalSpread * 0.5, spread * 0.6, 0.01);
        }

        float normalizedFill = MathHelper.clamp(momentumFill, 0.0f, 1.0f);
        float scaledStacks = Math.min(stacks, 6);
        float volumeBase = 0.18f + 0.03f * scaledStacks;
        float volume = Math.min(0.55f, volumeBase * (0.55f + 0.45f * normalizedFill));
        float pitch = 0.95f + 0.08f * scaledStacks + 0.05f * normalizedFill;

        world.playSound(null, targetPos.x, centerY, targetPos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void emitSupportRegenArea(MobEntity pet, ServerWorld world) {
        emitFeedback("support_sitting_regen", pet, world);
    }

    public static void emitPetLevelUp(MobEntity pet, ServerWorld world) {
        emitFeedback("pet_level_up", pet, world);
    }

    public static void emitAbilityReady(MobEntity pet, ServerWorld world) {
        emitFeedback("ability_ready", pet, world);
    }

    // Role-specific ability feedback
    public static void emitRoleAbility(Identifier roleId, String abilityName, Entity source, ServerWorld world) {
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        String prefix = roleType != null ? roleType.visual().abilityEventPrefix() : "";
        if (prefix == null || prefix.isEmpty()) {
            prefix = roleId.getPath();
        }
        String eventName = prefix + "_" + abilityName.toLowerCase();
        emitFeedback(eventName, source, world);
    }

    /**
     * Emit orbital patterns for tribute effects
     */
    private static void emitOrbitalSingle(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, Entity sourceEntity) {
        if (!(sourceEntity instanceof MobEntity pet)) {
            emitBurstPattern(config, pos, world);
            return;
        }
        TributeOrbitalEffects.emitTributeOrbital(pet, world, world.getTime());
    }

    private static void emitOrbitalDual(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, Entity sourceEntity) {
        if (!(sourceEntity instanceof MobEntity pet)) {
            emitBurstPattern(config, pos, world);
            return;
        }
        TributeOrbitalEffects.emitTributeOrbital(pet, world, world.getTime());
    }

    private static void emitOrbitalTriple(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, Entity sourceEntity) {
        if (!(sourceEntity instanceof MobEntity pet)) {
            emitBurstPattern(config, pos, world);
            return;
        }
        TributeOrbitalEffects.emitTributeOrbital(pet, world, world.getTime());
    }

    /**
     * Emit tribute orbital effects via feedback system
     */
    public static void emitTributeOrbitalFeedback(MobEntity pet, ServerWorld world, int tributeLevel) {
        String eventName = "tribute_orbital_" + tributeLevel;
        emitFeedback(eventName, pet, world);
    }

    /**
     * Emit contagion particle effects based on emotion type
     */
    public static void emitContagionFeedback(MobEntity pet, ServerWorld world, PetComponent.Emotion emotion) {
        if (pet == null || world == null || emotion == null) return;
        
        String eventName = switch (emotion) {
            // Positive emotions
            case KEFI, RELIEF, CHEERFUL, CONTENT, PRIDE -> "contagion_positive";
            
            // Negative emotions
            case FOREBODING, ANGST, STARTLE, DISGUST -> "contagion_negative";
            
            // Combat emotions
            case GUARDIAN_VIGIL, PROTECTIVE, FOCUSED -> "contagion_combat";
            
            // Discovery emotions
            case CURIOUS, YUGEN, MONO_NO_AWARE, FERNWEH -> "contagion_discovery";
            
            // Social emotions
            case SOBREMESA, UBUNTU, LAGOM -> "contagion_social";
            
            // Neutral emotions
            case STOIC, VIGILANT, GAMAN, HIRAETH, REGRET -> "contagion_neutral";
            
            default -> null;
        };
        
        if (eventName != null) {
            emitFeedback(eventName, pet, world);
        }
    }

    /**
     * Cancel all pending feedback tasks.
     * Useful for immediate cleanup without full shutdown.
     */
    public static void cancelFeedbackTasks() {
        IS_SHUTTING_DOWN.set(true);
        
        // Cancel all pending tasks
        for (Map.Entry<String, ScheduledFuture<?>> entry : PENDING_TASKS.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isDone()) {
                try { future.cancel(false); } catch (Throwable ignored) {}
            }
        }
        
        // Clear the task tracking map
        PENDING_TASKS.clear();
    }
    
    /**
     * Clean up all delayed tasks and resources.
     * Should be called during server shutdown to prevent watchdog timeouts.
     */
    public static void cleanup() {
        if (IS_SHUTTING_DOWN.compareAndSet(false, true)) {
            // Cancel all pending tasks first
            cancelFeedbackTasks();
            
            // Shutdown executor gracefully
            try { FEEDBACK_EXECUTOR.shutdown(); } catch (Throwable ignored) {}
            try {
                // Wait for tasks to complete with timeout
                if (!FEEDBACK_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't complete in time
                    try { FEEDBACK_EXECUTOR.shutdownNow(); } catch (Throwable ignored) {}
                    // Wait a bit more for forceful shutdown
                    if (!FEEDBACK_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                        // silently ignore; best-effort
                    }
                }
            } catch (InterruptedException e) {
                // Restore interrupted status
                Thread.currentThread().interrupt();
                // Force shutdown on interruption
                try { FEEDBACK_EXECUTOR.shutdownNow(); } catch (Throwable ignored) {}
            }
        }
    }
}
