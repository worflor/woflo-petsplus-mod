package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.TributeHandler;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;

/**
 * Centralized feedback system for visual and audio effects throughout the mod.
 * Handles both ambient role particles and ability-triggered feedback.
 */
public class FeedbackManager {

    private static final int AMBIENT_PARTICLE_INTERVAL = 80; // 4 seconds
    // Debounce map: last emit tick per source key (debounce)
    private static final ConcurrentHashMap<Object, Long> LAST_EMIT_TICK = new ConcurrentHashMap<>();
    private static final Set<ScheduledFuture<?>> PENDING_TASKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean IS_SHUTTING_DOWN = new AtomicBoolean(false);
    private static final Object EXECUTOR_LOCK = new Object();
    private static final Map<String, GroundTrailDefinition> GROUND_TRAILS_BY_STATE = new ConcurrentHashMap<>();
    private static final Map<String, GroundTrailDefinition> GROUND_TRAILS_BY_EVENT = new ConcurrentHashMap<>();
    private static final long DEFAULT_GROUND_TRAIL_INTERVAL = 6L;
    private static final GroundTrailDefinition LOCH_TRAIL_DEFINITION = new GroundTrailDefinition(
        "loch_n_load_tag",
        "friend_loch_trail",
        "trail_loch_next_tick",
        6L,
        40L
    );
    // Define handler first to avoid any static-init ordering surprises
    private static final Thread.UncaughtExceptionHandler FEEDBACK_EXCEPTION_HANDLER = (thread, throwable) ->
        Petsplus.LOGGER.error("Uncaught exception in feedback executor thread {}", thread.getName(), throwable);
    private static ScheduledThreadPoolExecutor feedbackExecutor = null; // initialized lazily

    private static final class DebounceKey {
        private final Object source;
        private final String eventName;
        private final int hash;

        private DebounceKey(Object source, String eventName) {
            this.source = Objects.requireNonNull(source, "source");
            this.eventName = eventName == null ? "" : eventName;
            this.hash = Objects.hash(this.source, this.eventName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DebounceKey other)) {
                return false;
            }
            return Objects.equals(this.source, other.source) && this.eventName.equals(other.eventName);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    // Static initialization block with shutdown hook registration
    static {
        registerGroundTrail(LOCH_TRAIL_DEFINITION);
        try {
            // Avoid preview Thread Builders to prevent NPEs during class init on server thread
            Thread shutdown = new Thread(FeedbackManager::cleanup, "PetsPlus-FeedbackShutdownHook");
            shutdown.setDaemon(true);
            Runtime.getRuntime().addShutdownHook(shutdown);
        } catch (Throwable t) {
            // Last-resort fallback: log and continue without hook
            Petsplus.LOGGER.warn("Failed to register feedback shutdown hook", t);
        }
    }

    private static ScheduledThreadPoolExecutor createExecutor() {
        // Use basic ThreadFactory to avoid PlatformThreadBuilder initialization issues on some JVMs/contexts
        java.util.concurrent.ThreadFactory factory = r -> {
            Thread t = new Thread(r, "PetsPlus-Feedback-1");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(FEEDBACK_EXCEPTION_HANDLER);
            return t;
        };
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, factory);
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ScheduledThreadPoolExecutor ensureExecutor() {
        ScheduledThreadPoolExecutor executor = feedbackExecutor;
        if (executor == null || executor.isShutdown()) {
            synchronized (EXECUTOR_LOCK) {
                executor = feedbackExecutor;
                if (executor == null || executor.isShutdown()) {
                    feedbackExecutor = createExecutor();
                    executor = feedbackExecutor;
                    IS_SHUTTING_DOWN.set(false);
                }
            }
        }
        return executor;
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
        if (world == null || world.isClient() || position == null) return;
        var effect = FeedbackConfig.getFeedback(eventName);
        if (effect == null) return;

        // Per-source debounce scoped by event so independent feedback can coexist (debounce)
        String normalizedEvent = eventName == null ? "" : eventName;
        Object sourceToken;
        if (sourceEntity != null) {
            sourceToken = sourceEntity.getUuid();
        } else {
            sourceToken = world.getRegistryKey().getValue().toString() + "@" + MathHelper.floor(position.x) + "," + MathHelper.floor(position.y) + "," + MathHelper.floor(position.z);
        }
        DebounceKey key = new DebounceKey(sourceToken, normalizedEvent);
        long now = world.getTime();
        long last = LAST_EMIT_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (now - last < 4L) {
            return;
        }
        LAST_EMIT_TICK.put(key, now);

        if (effect.delayTicks() > 0) {
            scheduleDelayedFeedback(eventName, effect, position, world, sourceEntity);
        } else {
            runImmediateFeedback(eventName, effect, position, world, sourceEntity);
        }
    }

    private static void runImmediateFeedback(String eventName, FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                             ServerWorld world, Entity sourceEntity) {
        if (effect == null || world == null || world.isClient() || position == null) return;
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        FeedbackInvocation invocation = FeedbackInvocation.from(eventName, effect, position, world, sourceEntity);
        if (!server.isOnThread()) {
            server.execute(() -> invocation.dispatch(server));
            return;
        }
        invocation.dispatch(server);
    }

    private static void scheduleDelayedFeedback(String eventName, FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                                ServerWorld world, Entity sourceEntity) {
        if (effect == null || position == null || world == null || world.isClient()) return;
        MinecraftServer server = world.getServer();
        if (server == null || IS_SHUTTING_DOWN.get()) {
            return;
        }

        FeedbackInvocation invocation = FeedbackInvocation.from(eventName, effect, position, world, sourceEntity);
        long delayTicks = Math.max(1, effect.delayTicks());

        ScheduledThreadPoolExecutor executor = ensureExecutor();
        final ScheduledFuture<?>[] handle = new ScheduledFuture<?>[1];
        Runnable task = () -> {
            try {
                if (IS_SHUTTING_DOWN.get() || server.isStopped()) {
                    return;
                }
                server.execute(() -> {
                    if (!IS_SHUTTING_DOWN.get() && !server.isStopped()) {
                        invocation.dispatch(server);
                    }
                });
            } finally {
                ScheduledFuture<?> scheduled = handle[0];
                if (scheduled != null) {
                    PENDING_TASKS.remove(scheduled);
                }
            }
        };

        try {
            handle[0] = executor.schedule(task, delayTicks * 50L, TimeUnit.MILLISECONDS);
            PENDING_TASKS.add(handle[0]);
        } catch (RejectedExecutionException e) {
            // Handle executor shutdown case gracefully
            // Avoid noisy stderr; rely on server log if needed
        }
    }

    /**
     * Register or replace a ground trail definition tied to a component state key.
     */
    public static void registerGroundTrail(GroundTrailDefinition definition) {
        if (definition == null) {
            return;
        }

        GROUND_TRAILS_BY_STATE.put(definition.stateKey(), definition);
        GROUND_TRAILS_BY_EVENT.put(definition.eventName(), definition);
    }

    /**
     * Remove a previously registered ground trail definition.
     */
    public static boolean unregisterGroundTrail(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            return false;
        }

        GroundTrailDefinition removed = GROUND_TRAILS_BY_STATE.remove(stateKey);
        if (removed == null) {
            return false;
        }

        GROUND_TRAILS_BY_EVENT.remove(removed.eventName());
        return true;
    }

    static GroundTrailDefinition getGroundTrailByState(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            return null;
        }
        return GROUND_TRAILS_BY_STATE.get(stateKey);
    }

    static GroundTrailDefinition getGroundTrailByEvent(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return null;
        }
        return GROUND_TRAILS_BY_EVENT.get(eventName);
    }

    /**
     * Emit any registered ground trails driven by component state flags.
     */
    public static boolean emitGroundTrails(MobEntity pet, PetComponent component, ServerWorld world, long currentTick) {
        if (pet == null || component == null || world == null || world.isClient()) {
            return false;
        }
        if (!pet.isAlive() || pet.isRemoved()) {
            for (GroundTrailDefinition definition : GROUND_TRAILS_BY_STATE.values()) {
                component.clearStateData(definition.nextTickStateKey());
            }
            return false;
        }

        boolean emitted = false;
        long timeline = Math.max(world.getTime(), currentTick);
        for (GroundTrailDefinition definition : GROUND_TRAILS_BY_STATE.values()) {
            if (!prepareTrailEmission(component, definition, timeline)) {
                continue;
            }
            emitFeedback(definition.eventName(), pet, world);
            emitted = true;
        }
        return emitted;
    }

    static boolean prepareTrailEmission(PetComponent component, GroundTrailDefinition definition, long timeline) {
        if (component == null || definition == null) {
            return false;
        }

        boolean hasTag = Boolean.TRUE.equals(
            component.getStateData(definition.stateKey(), Boolean.class, Boolean.FALSE)
        );
        if (!hasTag) {
            component.clearStateData(definition.nextTickStateKey());
            return false;
        }

        long lastAttack = component.getLastAttackTick();
        if (timeline - lastAttack < definition.combatCooldownTicks()) {
            return false;
        }

        long nextAllowed = component.getStateData(definition.nextTickStateKey(), Long.class, 0L);
        if (nextAllowed > timeline) {
            return false;
        }

        component.setStateData(definition.nextTickStateKey(), timeline + definition.intervalTicks());
        return true;
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

    private static void executeImmediateFeedback(String eventName, FeedbackConfig.FeedbackEffect effect, Vec3d position,
                                               ServerWorld world, Entity sourceEntity) {
        if (effect == null || world == null || world.isClient() || position == null) return;
        // Emit particles with per-call total budget clamp (budget)
        final int MAX_BUDGET = 48;
        int totalPlanned = 0;
        if (effect.particles() != null) {
            for (var pc : effect.particles()) {
                if (pc != null) {
                    totalPlanned += Math.max(0, pc.count());
                }
            }
        }
        float scale = 1.0f;
        if (totalPlanned > MAX_BUDGET && totalPlanned > 0) {
            scale = (float) MAX_BUDGET / (float) totalPlanned;
        }
        int spent = 0;
        if (effect.particles() != null) {
            for (var particleConfig : effect.particles()) {
                if (particleConfig == null) continue;
                // Scale count proportionally, ensure at least 1 if originally >0 and budget remains
                int scaled = particleConfig.count();
                if (scale < 1.0f) {
                    scaled = Math.max(1, Math.round(particleConfig.count() * scale));
                }
                int remaining = MAX_BUDGET - spent;
                if (remaining <= 0) break;
                int capped = Math.min(scaled, remaining);
                if (capped <= 0) continue;
                // Emit using override count without constructing new ParticleConfig
                emitParticlePatternWithCount(particleConfig, capped, position, world, sourceEntity, eventName);
                spent += capped;
            }
        }

        // Play audio
        if (effect.audio() != null) {
            world.playSound(null, position.x, position.y, position.z,
                          effect.audio().sound(), SoundCategory.NEUTRAL,
                          effect.audio().volume(), effect.audio().pitch());
        }
    }

    // Internal helper to emit a pattern using an override count without modifying config (budget)
    private static void emitParticlePatternWithCount(FeedbackConfig.ParticleConfig config, int overrideCount,
                                                    Vec3d position, ServerWorld world, Entity sourceEntity,
                                                    @Nullable String eventName) {
        if (config == null || world == null || world.isClient() || position == null) return;
        double entitySizeMultiplier = 1.0;
        if (config.adaptToEntitySize() && sourceEntity != null) {
            entitySizeMultiplier = Math.max(0.5, Math.min(2.0, sourceEntity.getWidth()));
        }
        double effectiveRadius = config.radius() * entitySizeMultiplier;
        // Dispatch based on pattern, mirroring emitParticlePattern but using overrideCount
        switch (config.pattern().toLowerCase()) {
            case "circle" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = (i / (double) Math.max(1, overrideCount)) * 2 * Math.PI;
                    double x = position.x + Math.cos(angle) * effectiveRadius;
                    double z = position.z + Math.sin(angle) * effectiveRadius;
                    world.spawnParticles(config.type(), x, position.y + config.offsetY(), z,
                            1, config.offsetX(), 0, config.offsetZ(), config.speed());
                }
            }
            case "burst" -> {
                world.spawnParticles(config.type(), position.x, position.y + config.offsetY(), position.z,
                        overrideCount, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
            }
            case "line" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double progress = overrideCount == 1 ? 0.5 : i / (double) (overrideCount - 1);
                    double x = position.x + (progress - 0.5) * effectiveRadius;
                    world.spawnParticles(config.type(), x, position.y + config.offsetY(), position.z,
                            1, config.offsetX(), 0, config.offsetZ(), config.speed());
                }
            }
            case "area" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * 2 * Math.PI;
                    double r = world.getRandom().nextDouble() * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    world.spawnParticles(config.type(), x, position.y + config.offsetY(), z,
                            1, config.offsetX(), config.offsetY() / 2, config.offsetZ(), config.speed());
                }
            }
            case "spiral" -> {
                double time = world.getTime() * 0.1;
                for (int i = 0; i < overrideCount; i++) {
                    double angle = time + (i * Math.PI * 2 / Math.max(1, overrideCount));
                    double x = position.x + Math.cos(angle) * effectiveRadius;
                    double z = position.z + Math.sin(angle) * effectiveRadius;
                    double y = position.y + config.offsetY() + Math.sin(angle) * 0.1;
                    world.spawnParticles(config.type(), x, y, z,
                            1, config.offsetX(), 0, config.offsetZ(), config.speed());
                }
            }
            case "upward" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double offsetX = (world.getRandom().nextDouble() - 0.5) * config.offsetX() * 2;
                    double offsetZ = (world.getRandom().nextDouble() - 0.5) * config.offsetZ() * 2;
                    world.spawnParticles(config.type(),
                            position.x + offsetX, position.y - 0.2, position.z + offsetZ,
                            1, config.offsetX(), 0.0, config.offsetZ(), config.speed());
                }
            }
            case "plus" -> {
                double size = effectiveRadius;
                world.spawnParticles(config.type(), position.x - size/2, position.y + config.offsetY(), position.z,
                        1, config.offsetX(), 0, config.offsetZ(), config.speed());
                world.spawnParticles(config.type(), position.x + size/2, position.y + config.offsetY(), position.z,
                        1, config.offsetX(), 0, config.offsetZ(), config.speed());
                world.spawnParticles(config.type(), position.x, position.y + config.offsetY(), position.z - size/2,
                        1, config.offsetX(), 0, config.offsetZ(), config.speed());
                world.spawnParticles(config.type(), position.x, position.y + config.offsetY(), position.z + size/2,
                        1, config.offsetX(), 0, config.offsetZ(), config.speed());
            }
            case "z_pattern" -> {
                double time = world.getTime() * 0.05;
                for (int i = 0; i < overrideCount; i++) {
                    double progress = (i / (double) Math.max(1, overrideCount) + time) % 1.0;
                    double x = position.x + (progress - 0.5) * effectiveRadius;
                    double y = position.y + config.offsetY() + Math.sin(progress * Math.PI) * 0.2;
                    double z = position.z + Math.cos(progress * Math.PI * 2) * 0.1;
                    world.spawnParticles(config.type(), x, y, z,
                            1, config.offsetX(), 0, config.offsetZ(), config.speed());
                }
            }
            case "random" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * Math.PI * 2;
                    double r = world.getRandom().nextDouble() * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY() + world.getRandom().nextDouble() * 0.3;
                    world.spawnParticles(config.type(), x, y, z,
                            1, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
                }
            }
            case "aura_radius_ground" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double angle = world.getRandom().nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(world.getRandom().nextDouble()) * effectiveRadius;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY() + world.getRandom().nextDouble() * 0.1;
                    world.spawnParticles(config.type(), x, y, z,
                            1, config.offsetX(), 0.02, config.offsetZ(), config.speed());
                }
            }
            case "aura_radius_edge" -> {
                for (int i = 0; i < overrideCount; i++) {
                    double baseAngle = (i / (double) Math.max(1, overrideCount)) * Math.PI * 2;
                    double angle = baseAngle + (world.getRandom().nextDouble() - 0.5) * 0.3;
                    double r = effectiveRadius + (world.getRandom().nextDouble() - 0.5) * 0.5;
                    double x = position.x + Math.cos(angle) * r;
                    double z = position.z + Math.sin(angle) * r;
                    double y = position.y + config.offsetY() + world.getRandom().nextDouble() * 0.2;
                    world.spawnParticles(config.type(), x, y, z,
                            1, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
                }
            }
            case "orbital_single" -> emitOrbitalSingle(config, position, world, sourceEntity);
            case "orbital_dual" -> emitOrbitalDual(config, position, world, sourceEntity);
            case "orbital_triple" -> emitOrbitalTriple(config, position, world, sourceEntity);
            case "ground_trail" -> emitGroundTrailPattern(config, position, world, sourceEntity, overrideCount, eventName);
            default -> {
                // fallback to burst
                world.spawnParticles(config.type(), position.x, position.y + config.offsetY(), position.z,
                        overrideCount, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
            }
        }
    }

    private static void emitParticlePattern(FeedbackConfig.ParticleConfig config, Vec3d position,
                                          ServerWorld world, Entity sourceEntity, @Nullable String eventName) {
        if (config == null || world == null || world.isClient() || position == null) return;
        double entitySizeMultiplier = 1.0;
        if (config.adaptToEntitySize() && sourceEntity != null) {
            entitySizeMultiplier = Math.max(0.5, Math.min(2.0, sourceEntity.getWidth()));
        }

        double effectiveRadius = config.radius() * entitySizeMultiplier;

        switch (config.pattern().toLowerCase()) {
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
            case "ground_trail" -> emitGroundTrailPattern(config, position, world, sourceEntity, config.count(), eventName);
            default -> emitBurstPattern(config, position, world);
        }
    }

    private static void emitCirclePattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count(); i++) {
            double angle = (i / (double) config.count()) * 2 * Math.PI;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            world.spawnParticles(config.type(), x, pos.y + config.offsetY(), z,
                               1, config.offsetX(), 0, config.offsetZ(), config.speed());
        }
    }

    private static void emitBurstPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world) {
        world.spawnParticles(config.type(), pos.x, pos.y + config.offsetY(), pos.z,
                           config.count(), config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
    }

    private static void emitLinePattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double length) {
        for (int i = 0; i < config.count(); i++) {
            double progress = i / (double) (config.count() - 1);
            double x = pos.x + (progress - 0.5) * length;
            world.spawnParticles(config.type(), x, pos.y + config.offsetY(), pos.z,
                               1, config.offsetX(), 0, config.offsetZ(), config.speed());
        }
    }

    private static void emitAreaPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count(); i++) {
            double angle = world.getRandom().nextDouble() * 2 * Math.PI;
            double r = world.getRandom().nextDouble() * radius;
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            world.spawnParticles(config.type(), x, pos.y + config.offsetY(), z,
                               1, config.offsetX(), config.offsetY() / 2, config.offsetZ(), config.speed());
        }
    }

    private static void emitSpiralPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        double time = world.getTime() * 0.1;
        for (int i = 0; i < config.count(); i++) {
            double angle = time + (i * Math.PI * 2 / config.count());
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + config.offsetY() + Math.sin(angle) * 0.1;
            world.spawnParticles(config.type(), x, y, z,
                               1, config.offsetX(), 0, config.offsetZ(), config.speed());
        }
    }

    private static void emitUpwardPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world) {
        for (int i = 0; i < config.count(); i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * config.offsetX() * 2;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * config.offsetZ() * 2;
            world.spawnParticles(config.type(),
                               pos.x + offsetX, pos.y - 0.2, pos.z + offsetZ,
                               1, config.offsetX(), 0.0, config.offsetZ(), config.speed());
        }
    }

    private static void emitGroundTrailPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world,
                                               @Nullable Entity sourceEntity, int count,
                                               @Nullable String eventName) {
        if (config == null || world == null || world.isClient() || sourceEntity == null || count <= 0) {
            return;
        }

        Vec3d velocity = sourceEntity.getVelocity();
        Vec3d forward = new Vec3d(velocity.x, 0.0, velocity.z);
        if (forward.lengthSquared() < 1.0E-4) {
            float yaw = sourceEntity.getYaw();
            double yawRadians = yaw * MathHelper.RADIANS_PER_DEGREE;
            forward = new Vec3d(-MathHelper.sin((float) yawRadians), 0.0, MathHelper.cos((float) yawRadians));
        }

        if (forward.lengthSquared() < 1.0E-6) {
            forward = new Vec3d(0.0, 0.0, 1.0);
        } else {
            forward = forward.normalize();
        }

        Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        double radius = Math.max(0.25, config.radius());
        Vec3d basePos = new Vec3d(pos.x, pos.y + config.offsetY(), pos.z);

        GroundTrailDefinition definition = getGroundTrailByEvent(eventName);
        long interval = definition != null ? definition.intervalTicks() : DEFAULT_GROUND_TRAIL_INTERVAL;
        long timeSlice = Math.max(0L, world.getTime()) / Math.max(1L, interval);
        long seed = timeSlice ^ 0x9E3779B97F4A7C15L;
        UUID uuid = sourceEntity.getUuid();
        if (uuid != null) {
            seed ^= uuid.getMostSignificantBits();
            seed ^= uuid.getLeastSignificantBits();
        }
        Random random = new Random(seed);

        double lateralScale = Math.max(0.2, radius * 0.45);
        double spreadBase = Math.max(0.01, (config.offsetX() + config.offsetZ()) * 0.25);

        for (int i = 0; i < count; i++) {
            double progress = (i + 1.0) / (double) (count + 1);
            double backFactor = 0.3 + progress * 0.7 + random.nextDouble() * 0.2;
            Vec3d backOffset = forward.multiply(-backFactor * radius);

            double lateral = (random.nextDouble() - 0.5) * lateralScale * 2.0;
            Vec3d sideOffset = right.multiply(lateral);

            double verticalOffset = -0.05 + random.nextDouble() * 0.08;
            Vec3d spawnPos = basePos.add(backOffset).add(sideOffset).add(0.0, verticalOffset, 0.0);

            double horizontalSpread = spreadBase * (0.6 + random.nextDouble() * 0.6);
            double verticalSpread = 0.003 + random.nextDouble() * 0.004;
            double speed = Math.max(0.0005, config.speed());

            world.spawnParticles(config.type(),
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    1, horizontalSpread, verticalSpread, horizontalSpread, speed);
        }
    }

    private static void emitPlusPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double size) {
        // Horizontal line
        world.spawnParticles(config.type(), pos.x - size/2, pos.y + config.offsetY(), pos.z,
                           1, config.offsetX(), 0, config.offsetZ(), config.speed());
        world.spawnParticles(config.type(), pos.x + size/2, pos.y + config.offsetY(), pos.z,
                           1, config.offsetX(), 0, config.offsetZ(), config.speed());
        // Vertical line
        world.spawnParticles(config.type(), pos.x, pos.y + config.offsetY(), pos.z - size/2,
                           1, config.offsetX(), 0, config.offsetZ(), config.speed());
        world.spawnParticles(config.type(), pos.x, pos.y + config.offsetY(), pos.z + size/2,
                           1, config.offsetX(), 0, config.offsetZ(), config.speed());
    }

    private static void emitZPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double size) {
        double time = world.getTime() * 0.05;
        for (int i = 0; i < config.count(); i++) {
            double progress = (i / (double) config.count() + time) % 1.0;
            double x = pos.x + (progress - 0.5) * size;
            double y = pos.y + config.offsetY() + Math.sin(progress * Math.PI) * 0.2;
            double z = pos.z + Math.cos(progress * Math.PI * 2) * 0.1;
            world.spawnParticles(config.type(), x, y, z,
                               1, config.offsetX(), 0, config.offsetZ(), config.speed());
        }
    }

    private static void emitRandomPattern(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count(); i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double r = world.getRandom().nextDouble() * radius;
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            double y = pos.y + config.offsetY() + world.getRandom().nextDouble() * 0.3;
            world.spawnParticles(config.type(), x, y, z,
                               1, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
        }
    }

    /**
     * Emit particles in a scattered pattern across the ground within the aura radius.
     * Perfect for showing regeneration areas, gathering zones, etc.
     */
    private static void emitAuraRadiusGround(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count(); i++) {
            // Random positions within the circular area
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double r = Math.sqrt(world.getRandom().nextDouble()) * radius; // Uniform distribution
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;

            // Ground-level particles with slight rise
            double y = pos.y + config.offsetY() + world.getRandom().nextDouble() * 0.1;

            world.spawnParticles(config.type(), x, y, z,
                               1, config.offsetX(), 0.02, config.offsetZ(), config.speed());
        }
    }

    /**
     * Emit particles around the edge/circumference of the aura radius.
     * Perfect for showing ability borders, detection ranges, etc.
     */
    private static void emitAuraRadiusEdge(FeedbackConfig.ParticleConfig config, Vec3d pos, ServerWorld world, double radius) {
        for (int i = 0; i < config.count(); i++) {
            // Positions around the circumference with slight randomization
            double baseAngle = (i / (double) config.count()) * Math.PI * 2;
            double angle = baseAngle + (world.getRandom().nextDouble() - 0.5) * 0.3; // +/-0.15 radians variance
            double r = radius + (world.getRandom().nextDouble() - 0.5) * 0.5; // +/-0.25 block variance

            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            double y = pos.y + config.offsetY() + world.getRandom().nextDouble() * 0.2;

            world.spawnParticles(config.type(), x, y, z,
                               1, config.offsetX(), config.offsetY(), config.offsetZ(), config.speed());
        }
    }

    public record GroundTrailDefinition(String stateKey,
                                        String eventName,
                                        String nextTickStateKey,
                                        long intervalTicks,
                                        long combatCooldownTicks) {
        public GroundTrailDefinition {
            Objects.requireNonNull(stateKey, "stateKey");
            Objects.requireNonNull(eventName, "eventName");
            if (nextTickStateKey == null || nextTickStateKey.isBlank()) {
                nextTickStateKey = "trail_" + stateKey + "_next_tick";
            }
            intervalTicks = Math.max(1L, intervalTicks);
            combatCooldownTicks = Math.max(0L, combatCooldownTicks);
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
        for (ScheduledFuture<?> future : PENDING_TASKS) {
            if (future == null) {
                continue;
            }
            try {
                if (!future.isDone()) {
                    future.cancel(false);
                }
            } catch (Throwable ignored) {
            }
        }

        PENDING_TASKS.clear();
    }
    
    /**
     * Clean up all delayed tasks and resources.
     * Should be called during server shutdown to prevent watchdog timeouts.
     */
    public static void cleanup() {
        boolean alreadyShuttingDown = IS_SHUTTING_DOWN.getAndSet(true);

        cancelFeedbackTasks();

        ScheduledThreadPoolExecutor executor;
        synchronized (EXECUTOR_LOCK) {
            executor = feedbackExecutor;
            feedbackExecutor = null;
        }

        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (Throwable ignored) {
            }

            if (!alreadyShuttingDown) {
                try {
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        LAST_EMIT_TICK.clear();
    }

    private record FeedbackInvocation(String eventName,
                                      FeedbackConfig.FeedbackEffect effect,
                                      Vec3d position,
                                      RegistryKey<World> worldKey,
                                      @Nullable UUID sourceUuid) {

        private FeedbackInvocation {
            Objects.requireNonNull(eventName, "eventName");
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(worldKey, "worldKey");
        }

        private static FeedbackInvocation from(String eventName,
                                               FeedbackConfig.FeedbackEffect effect,
                                               Vec3d position,
                                               ServerWorld world,
                                               @Nullable Entity source) {
            Objects.requireNonNull(world, "world");
            UUID uuid = source != null ? source.getUuid() : null;
            return new FeedbackInvocation(eventName == null ? "" : eventName,
                effect, position, world.getRegistryKey(), uuid);
        }

        private void dispatch(MinecraftServer server) {
            if (server == null || server.isStopped()) {
                return;
            }
            ServerWorld targetWorld = server.getWorld(worldKey);
            if (targetWorld == null || targetWorld.isClient()) {
                return;
            }
            Entity sourceEntity = sourceUuid != null ? targetWorld.getEntity(sourceUuid) : null;
            executeImmediateFeedback(eventName, effect, position, targetWorld, sourceEntity);
        }
    }
}
