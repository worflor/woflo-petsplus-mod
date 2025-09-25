package woflo.petsplus.ui;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized feedback system for visual and audio effects throughout the mod.
 * Handles both ambient role particles and ability-triggered feedback.
 */
public class FeedbackManager {

    private static final int AMBIENT_PARTICLE_INTERVAL = 80; // 4 seconds
    private static final Map<ServerWorld, List<DelayedFeedbackTask>> DELAYED_TASKS = new HashMap<>();
    private static boolean tickHandlerRegistered;

    /**
     * Emit feedback for a specific event at an entity's location.
     */
    public static void emitFeedback(String eventName, Entity entity, ServerWorld world) {
        if (entity == null || world == null) return;
        emitFeedback(eventName, entity.getPos(), world, entity);
    }

    /**
     * Emit feedback for a specific event at a specific location.
     */
    public static void emitFeedback(String eventName, Vec3d position, ServerWorld world, Entity sourceEntity) {
        var effect = FeedbackConfig.getFeedback(eventName);
        if (effect == null) return;

        if (effect.delayTicks > 0) {
            scheduleDelayedFeedback(effect, position, world, sourceEntity);
        } else {
            runImmediateFeedback(effect, position, world, sourceEntity);
        }
    }

    private static void runImmediateFeedback(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                             ServerWorld world, Entity sourceEntity) {
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
        var server = world.getServer();
        if (server == null) {
            return;
        }

        ensureTickHandlerRegistered();

        UUID sourceUuid = sourceEntity != null ? sourceEntity.getUuid() : null;
        long executeTick = server.getTicks() + Math.max(1, effect.delayTicks);

        if (server.isOnThread()) {
            addDelayedTask(world, effect, position, sourceUuid, executeTick);
        } else {
            server.execute(() -> addDelayedTask(world, effect, position, sourceUuid, executeTick));
        }
    }

    private static void addDelayedTask(ServerWorld world, FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                       UUID sourceUuid, long executeTick) {
        var tasks = DELAYED_TASKS.computeIfAbsent(world, key -> new ArrayList<>());
        tasks.add(new DelayedFeedbackTask(effect, position, sourceUuid, executeTick));
    }

    private static void ensureTickHandlerRegistered() {
        if (tickHandlerRegistered) {
            return;
        }

        synchronized (FeedbackManager.class) {
            if (tickHandlerRegistered) {
                return;
            }
            ServerTickEvents.END_WORLD_TICK.register(FeedbackManager::onWorldTick);
            tickHandlerRegistered = true;
        }
    }

    private static void onWorldTick(ServerWorld world) {
        var tasks = DELAYED_TASKS.get(world);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        long currentTick = world.getServer().getTicks();
        List<DelayedFeedbackTask> dueTasks = new ArrayList<>();
        for (var task : new ArrayList<>(tasks)) {
            if (task.executeTick <= currentTick) {
                dueTasks.add(task);
            }
        }

        if (dueTasks.isEmpty()) {
            return;
        }

        for (var task : dueTasks) {
            Entity resolvedSource = resolveEntity(world, task.sourceEntityUuid);
            executeImmediateFeedback(task.effect, task.position, world, resolvedSource);
        }

        tasks.removeAll(dueTasks);
        if (tasks.isEmpty()) {
            DELAYED_TASKS.remove(world);
        }
    }

    private static Entity resolveEntity(ServerWorld world, UUID uuid) {
        return uuid != null ? world.getEntity(uuid) : null;
    }

    private static final class DelayedFeedbackTask {
        private final FeedbackConfig.FeedbackEffect effect;
        private final Vec3d position;
        private final UUID sourceEntityUuid;
        private final long executeTick;

        private DelayedFeedbackTask(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                    UUID sourceEntityUuid, long executeTick) {
            this.effect = effect;
            this.position = position;
            this.sourceEntityUuid = sourceEntityUuid;
            this.executeTick = executeTick;
        }
    }

    /**
     * Emit role-specific ambient particles for a pet.
     */
    public static void emitRoleAmbientParticles(MobEntity pet, ServerWorld world, long currentTick) {
        if (currentTick % AMBIENT_PARTICLE_INTERVAL != 0) return;

        var component = PetComponent.get(pet);
        if (component == null) return;

        // Don't emit during combat for cleaner visuals
        if (world.getTime() - component.getLastAttackTick() < 60) return;

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
     * Check if an entity should have ambient particles (used by existing ParticleEffectManager).
     */
    public static boolean shouldEmitAmbientParticles(MobEntity pet, ServerWorld world) {
        var component = PetComponent.get(pet);
        if (component == null) return false;

        // Don't emit if in recent combat
        long lastAttack = component.getLastAttackTick();
        if (world.getTime() - lastAttack < 60) return false;

        return pet.isAlive() && !pet.isRemoved();
    }

    private static void executeImmediateFeedback(FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                               ServerWorld world, Entity sourceEntity) {
        // Emit particles
        for (var particleConfig : effect.particles) {
            emitParticlePattern(particleConfig, position, world, sourceEntity);
        }

        // Play audio
        if (effect.audio != null) {
            world.playSound(null, position.x, position.y, position.z,
                          effect.audio.sound, SoundCategory.NEUTRAL,
                          effect.audio.volume, effect.audio.pitch);
        }
    }

    private static void emitParticlePattern(FeedbackConfig.ParticleConfig config, Vec3d position,
                                          ServerWorld world, Entity sourceEntity) {
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

        Vec3d targetPos = target.getPos();
        double centerY = targetPos.y + target.getHeight() * 0.5;
        double spread = 0.12 + 0.04 * Math.min(stacks, 5);
        double verticalSpread = Math.max(0.1, target.getHeight() * 0.35);

        int critCount = 4 + Math.max(0, stacks) * 2;
        world.spawnParticles(ParticleTypes.CRIT, targetPos.x, centerY, targetPos.z,
                critCount, spread, verticalSpread * 0.6, spread, 0.18);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.25), target.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);

        if (stacks > 0) {
            int emberCount = MathHelper.clamp(2 + stacks * 2, 3, 12);
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
     * Clean up all delayed tasks and resources.
     * Should be called during server shutdown to prevent watchdog timeouts.
     */
    public static void cleanup() {
        synchronized (FeedbackManager.class) {
            DELAYED_TASKS.clear();
            tickHandlerRegistered = false;
        }
    }
}