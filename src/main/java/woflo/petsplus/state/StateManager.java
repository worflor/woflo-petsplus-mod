package woflo.petsplus.state;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.abilities.AbilityTriggerPayload;
import woflo.petsplus.abilities.AbilityTriggerResult;
import woflo.petsplus.abilities.OwnerAbilityEventBridge;
import woflo.petsplus.effects.AuraTargetResolver;
import woflo.petsplus.effects.PetsplusEffectManager;
import woflo.petsplus.effects.ProjectileDrForOwnerEffect;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.roles.support.SupportPotionVacuumManager;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.mood.EmotionStimulusBus;
import woflo.petsplus.ui.CooldownParticleManager;
import woflo.petsplus.util.EntityTagUtil;
import woflo.petsplus.state.processing.AsyncProcessingSettings;
import woflo.petsplus.state.processing.AsyncMigrationProgressTracker;
import woflo.petsplus.state.processing.AsyncProcessingTelemetry;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.AsyncJobPriority;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;
import woflo.petsplus.state.processing.OwnerEventDispatcher;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventType;
import woflo.petsplus.state.processing.OwnerBatchPlan;
import woflo.petsplus.state.processing.OwnerBatchPlanner;
import woflo.petsplus.state.processing.OwnerProcessingManager;
import woflo.petsplus.state.processing.OwnerTaskBatch;
import woflo.petsplus.state.processing.OwnerSpatialResult;
import woflo.petsplus.state.processing.OwnerSchedulingPrediction;
import woflo.petsplus.state.processing.AbilityCooldownPlan;
import woflo.petsplus.state.processing.AbilityCooldownPlanner;
import woflo.petsplus.state.processing.GossipPropagationPlanner.GossipPropagationPlan;
import woflo.petsplus.state.processing.GossipPropagationPlanner.Share;
import woflo.petsplus.events.XpEventHandler;
import woflo.petsplus.state.gossip.GossipTopics;
import woflo.petsplus.state.gossip.PetGossipLedger;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.processing.AsyncWorkerBudget;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

/**
 * Manages state for all pets and owners in the game using components for persistence.
 */
public class StateManager {
    private static final Map<ServerWorld, StateManager> WORLD_MANAGERS = new WeakHashMap<>();
    private static final long INTERVAL_TICK_SPACING = 20L;
    private static final long SUPPORT_POTION_ACTIVE_RECHECK = 5L;
    private static final long SUPPORT_POTION_IDLE_RECHECK = 40L;
    private static final long PARTICLE_RECHECK_INTERVAL = 20L;
    private static final long DEFAULT_AURA_RECHECK = 40L;
    private static final int MAX_SPATIAL_DEFERRALS = 3;
    private static final long MAX_SPATIAL_WAIT_TICKS = 20L;
    
    private final ServerWorld world;
    private final Map<MobEntity, PetComponent> petComponents = new WeakHashMap<>();
    private final Map<PlayerEntity, OwnerCombatState> ownerStates = new WeakHashMap<>();
    private long nextMaintenanceTick;
    private long lastTelemetryLogTick;
    private final PetSwarmIndex swarmIndex = new PetSwarmIndex();
    private final AuraTargetResolver auraTargetResolver = new AuraTargetResolver(swarmIndex);
    private final PetWorkScheduler workScheduler = new PetWorkScheduler();
    private final OwnerProcessingManager ownerProcessingManager = new OwnerProcessingManager();
    private final OwnerBatchProcessor ownerBatchProcessor = new OwnerBatchProcessor();
    private final OwnerEventDispatcher ownerEventDispatcher = new OwnerEventDispatcher();
    private final AdaptiveTickScaler adaptiveTickScaler = new AdaptiveTickScaler();
    private final AsyncWorkCoordinator asyncWorkCoordinator;
    private final AsyncWorkerBudget.Registration asyncWorkerRegistration;
    private final OwnerEventDispatcher.PresenceListener ownerPresenceListener;
    private final EmotionStimulusBus.QueueListener stimulusQueueListener;
    private final EmotionStimulusBus.IdleListener stimulusIdleListener;
    private final Map<UUID, EnumMap<OwnerEventType, OwnerSpatialResult>> pendingSpatialResults = new HashMap<>();
    private final Map<UUID, EnumMap<OwnerEventType, SpatialJobState>> spatialJobStates = new HashMap<>();
    private boolean disposed;

    private StateManager(ServerWorld world) {
        this.world = world;
        MinecraftServer server = world.getServer();
        if (server == null) {
            throw new IllegalStateException("Server world is missing server reference");
        }
        AsyncWorkerBudget.Registration registration = AsyncWorkerBudget.global().registerCoordinator();
        this.asyncWorkCoordinator = new AsyncWorkCoordinator(server, adaptiveTickScaler::loadFactor, registration);
        this.asyncWorkerRegistration = registration;
        this.ownerPresenceListener = ownerProcessingManager::onListenerPresenceChanged;
        this.stimulusQueueListener = this::handleStimulusQueued;
        this.stimulusIdleListener = this::handleStimulusIdleScheduled;
        registerOwnerEventListeners();
        registerEmotionStimulusBridge();
    }
    
    public static StateManager forWorld(ServerWorld world) {
        synchronized (WORLD_MANAGERS) {
            return WORLD_MANAGERS.computeIfAbsent(world, StateManager::new);
        }
    }

    public static void unloadWorld(ServerWorld world) {
        if (world == null) {
            return;
        }
        StateManager manager;
        synchronized (WORLD_MANAGERS) {
            manager = WORLD_MANAGERS.remove(world);
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    public static void unloadAll() {
        java.util.List<StateManager> managers;
        synchronized (WORLD_MANAGERS) {
            managers = new java.util.ArrayList<>(WORLD_MANAGERS.values());
            WORLD_MANAGERS.clear();
        }
        for (StateManager manager : managers) {
            manager.shutdown();
        }
    }
    
    public PetComponent getPetComponent(MobEntity pet) {
        PetComponent component = petComponents.computeIfAbsent(pet, entity -> {
            // Try to get existing component from entity
            PetComponent existing = PetComponent.get(entity);
            if (existing != null) {
                return existing;
            }

            // Create new component and attach to entity
            PetComponent created = new PetComponent(entity);
            PetComponent.set(entity, created);
            return created;
        });

        component.attachStateManager(this);
        if (world instanceof ServerWorld serverWorld) {
            MoodService.getInstance().trackPet(serverWorld, pet);
        }

        component.ensureSpeciesDescriptorInitialized();
        swarmIndex.trackPet(pet, component);
        ownerProcessingManager.trackPet(component);
        return component;
    }

    public AsyncWorkCoordinator getAsyncWorkCoordinator() {
        return asyncWorkCoordinator;
    }

    public OwnerProcessingManager getOwnerProcessingManager() {
        return ownerProcessingManager;
    }

    @Nullable
    public PetComponent peekPetComponent(MobEntity pet) {
        return petComponents.get(pet);
    }

    public OwnerCombatState getOwnerState(PlayerEntity owner) {
        return ownerStates.computeIfAbsent(owner, entity -> {
            // Try to get existing component from entity
            OwnerCombatState existing = OwnerCombatState.get(entity);
            if (existing != null) {
                return existing;
            }
            
            // Create new component and attach to entity
            OwnerCombatState state = new OwnerCombatState(entity);
            OwnerCombatState.set(entity, state);
            return state;
        });
    }
    
    /**
     * Assign a role to a pet for testing purposes.
     */
    public boolean assignRole(MobEntity pet, String roleName) {
        if (pet == null || roleName == null) {
            return false;
        }

        Identifier roleId = PetRoleType.normalizeId(roleName);
        if (roleId == null) {
            Petsplus.LOGGER.warn("Attempted to assign invalid role '{}' to pet {}", roleName, pet.getUuid());
            return false;
        }

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        if (roleType == null) {
            Petsplus.LOGGER.warn("Attempted to assign unknown role '{}' to pet {}", roleId, pet.getUuid());
            return false;
        }

        PetComponent component = getPetComponent(pet);
        component.setRoleId(roleId);
        component.ensureCharacteristics();

        Petsplus.LOGGER.debug("Assigned role {} ({}) to pet {}", roleId, roleType.translationKey(), pet.getUuid());
        return true;
    }
    
    /**
     * Get the count of active owner states.
     */
    public int getOwnerStateCount() {
        return ownerStates.size();
    }
    
    /**
     * Cleanup invalid states and return count.
     */
    public int cleanupInvalidStates() {
        int cleaned = 0;
        
        // Remove invalid pet components
        var petIterator = petComponents.entrySet().iterator();
        while (petIterator.hasNext()) {
            var entry = petIterator.next();
            if (entry.getKey() == null || entry.getKey().isRemoved()) {
                petIterator.remove();
                cleaned++;
            }
        }
        
        // Remove invalid owner states
        var ownerIterator = ownerStates.entrySet().iterator();
        while (ownerIterator.hasNext()) {
            var entry = ownerIterator.next();
            if (entry.getKey() == null || entry.getKey().isRemoved()) {
                ownerIterator.remove();
                cleaned++;
            }
        }
        
        return cleaned;
    }
    
    public void removePet(MobEntity pet) {
        PetComponent component = petComponents.remove(pet);
        if (component != null) {
            workScheduler.unscheduleAll(component);
            component.markSchedulingUninitialized();
            ownerProcessingManager.onTasksCleared(component);
            ownerProcessingManager.untrackPet(component);
        }
        if (world instanceof ServerWorld) {
            MoodService.getInstance().untrackPet(pet);
        }
        swarmIndex.untrackPet(pet);
    }

    public void removeOwner(PlayerEntity owner) {
        ownerStates.remove(owner);
        UUID ownerId = owner.getUuid();
        swarmIndex.removeOwner(ownerId);
        auraTargetResolver.handleOwnerRemoval(ownerId);
        ownerProcessingManager.removeOwner(ownerId);
    }

    public void handleOwnerTick(ServerPlayerEntity player) {
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.OWNER_PROCESSING);
        long worldTime = world.getTime();
        OwnerTaskBatch ownerTickBatch = ownerProcessingManager.snapshotOwnerTick(player, worldTime);
        EnumSet<OwnerEventType> spatialListeners = collectSpatialListeners();
        OwnerBatchSnapshot snapshot = null;

        if (ownerTickBatch != null) {
            boolean hasDueEvents = !ownerTickBatch.dueEventsView().isEmpty();
            boolean wantsPrediction = AsyncProcessingSettings.asyncPredictiveSchedulingEnabled();

            if (!spatialListeners.isEmpty() && AsyncProcessingSettings.asyncSpatialAnalyticsEnabled()) {
                long captureStart = System.nanoTime();
                snapshot = OwnerBatchSnapshot.capture(ownerTickBatch);
                asyncWorkCoordinator.telemetry().recordCaptureDuration(System.nanoTime() - captureStart);
                primeSpatialAnalysis(snapshot, spatialListeners, worldTime);
            }

            if (snapshot != null) {
                ownerBatchProcessor.processBatchWithSnapshot(ownerTickBatch, player, worldTime, true, null, snapshot);
            } else if (hasDueEvents || wantsPrediction) {
                ownerBatchProcessor.processBatch(ownerTickBatch, player, worldTime);
            } else {
                ownerTickBatch.close();
            }
        }

        auraTargetResolver.handleOwnerTick(player);
        OwnerCombatState state = ownerStates.get(player);
        if (state != null) {
            state.tick();
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PetsplusEffectManager.maybeCleanup(server);

        long currentServerTick = server.getTicks();
        if (nextMaintenanceTick == 0L) {
            nextMaintenanceTick = currentServerTick + 20;
        }

        if (currentServerTick >= nextMaintenanceTick) {
            long worldTick = world.getTime();

            TagTargetEffect.cleanupExpiredTags(worldTick);
            EntityTagUtil.cleanupExpiredTags(worldTick);
            ProjectileDrForOwnerEffect.cleanupExpired(worldTick);
            CooldownParticleManager.maybeCleanup(worldTick);

            petComponents.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isRemoved());
            ownerStates.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isRemoved());

            nextMaintenanceTick = currentServerTick + 200;
        }
    }

    public void handlePetTick(MobEntity pet) {
        PetComponent component = petComponents.get(pet);
        if (component == null) {
            return;
        }
        component.attachStateManager(this);

        if (!pet.isAlive()) {
            cancelScheduledWork(component, pet);
            return;
        }

        PlayerEntity owner = component.getOwner();
        if (!(owner instanceof ServerPlayerEntity) || owner.isRemoved()) {
            cancelScheduledWork(component, pet);
            return;
        }

        long time = world.getTime();
        component.ensureSchedulingInitialized(time);

        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.PET_STATE);

        PetComponent.SwarmStateSnapshot swarmSnapshot = component.snapshotSwarmState();
        double x = pet.getX();
        double y = pet.getY();
        double z = pet.getZ();

        asyncWorkCoordinator.submitStandalone(
            "pet-tick-" + pet.getUuid(),
            () -> computePetTickPlan(x, y, z, swarmSnapshot),
            plan -> applyPetTickPlan(component, pet, plan, time),
            AsyncJobPriority.HIGH
        ).exceptionally(error -> {
            Throwable cause = unwrapAsyncError(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Async pet tick rejected for pet {}", pet.getUuid());
                runSynchronousPetTick(component, pet, time);
            } else {
                Petsplus.LOGGER.error("Async pet tick failed for pet {}", pet.getUuid(), cause);
                runSynchronousPetTick(component, pet, time);
            }
            return null;
        });
    }

    private static PetTickPlan computePetTickPlan(double x, double y, double z,
                                                  PetComponent.SwarmStateSnapshot snapshot) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || snapshot == null) {
            return PetTickPlan.noop();
        }
        long cellKey = ChunkSectionPos.asLong(
            ChunkSectionPos.getSectionCoord(MathHelper.floor(x)),
            ChunkSectionPos.getSectionCoord(MathHelper.floor(y)),
            ChunkSectionPos.getSectionCoord(MathHelper.floor(z))
        );

        double dx = x - snapshot.x();
        double dy = y - snapshot.y();
        double dz = z - snapshot.z();
        double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
        final double movementThresholdSq = 0.0625D;

        if (!snapshot.initialized() || snapshot.cellKey() != cellKey || distanceSq >= movementThresholdSq) {
            return new PetTickPlan(true, cellKey, x, y, z);
        }
        return PetTickPlan.noop();
    }

    private void applyPetTickPlan(PetComponent component, MobEntity pet, PetTickPlan plan, long time) {
        if (component == null || pet == null || plan == null) {
            runSynchronousPetTick(component, pet, time);
            return;
        }

        boolean swarmUpdated = false;
        if (plan.swarmUpdate()) {
            component.applySwarmUpdate(swarmIndex, plan.cellKey(), plan.x(), plan.y(), plan.z());
            swarmUpdated = true;
        }

        ownerProcessingManager.markPetChanged(component, time);
        if (swarmUpdated) {
            if (hasOwnerEventListeners(OwnerEventType.AURA)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.AURA, time);
            }
            if (hasOwnerEventListeners(OwnerEventType.SUPPORT)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.SUPPORT, time);
            }
            if (hasOwnerEventListeners(OwnerEventType.MOVEMENT)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.MOVEMENT, time);
            }
        }
    }

    private void runSynchronousPetTick(PetComponent component, MobEntity pet, long time) {
        if (component == null || pet == null) {
            return;
        }
        boolean swarmUpdated = component.updateSwarmTrackingIfMoved(swarmIndex);
        ownerProcessingManager.markPetChanged(component, time);
        if (swarmUpdated) {
            if (hasOwnerEventListeners(OwnerEventType.AURA)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.AURA, time);
            }
            if (hasOwnerEventListeners(OwnerEventType.SUPPORT)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.SUPPORT, time);
            }
            if (hasOwnerEventListeners(OwnerEventType.MOVEMENT)) {
                ownerProcessingManager.signalEvent(component, OwnerEventType.MOVEMENT, time);
            }
        }
    }

    private record PetTickPlan(boolean swarmUpdate, long cellKey, double x, double y, double z) {
        static PetTickPlan noop() {
            return new PetTickPlan(false, Long.MIN_VALUE, 0.0D, 0.0D, 0.0D);
        }
    }

    public void schedulePetTask(PetComponent component, PetWorkScheduler.TaskType type, long tick) {
        workScheduler.schedule(component, type, tick);
        ownerProcessingManager.onTaskScheduled(component, type, tick);
    }

    public void unscheduleAllTasks(PetComponent component) {
        workScheduler.unscheduleAll(component);
        ownerProcessingManager.onTasksCleared(component);
    }

    public void processScheduledPetTasks(long currentTick) {
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.ADVANCED_SYSTEMS);
        asyncWorkCoordinator.drainMainThreadTasks();
        adaptiveTickScaler.recordTick();
        ownerProcessingManager.prepareForTick(currentTick);
        workScheduler.processDue(currentTick, ownerProcessingManager::enqueueTask);
        int pendingGroups = ownerProcessingManager.preparePendingGroups(currentTick);
        int budget = adaptiveTickScaler.ownerBatchBudget(pendingGroups);
        ownerProcessingManager.flushBatches((ownerId, batch) ->
            ownerBatchProcessor.processBatch(batch, currentTick)
        , currentTick, budget);
        maybeLogAsyncTelemetry(currentTick);
    }

    public long scaleInterval(long baseTicks) {
        if (baseTicks == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return adaptiveTickScaler.scaleInterval(baseTicks);
    }

    public AsyncWorkCoordinator asyncWorkCoordinator() {
        return asyncWorkCoordinator;
    }

    private void maybeLogAsyncTelemetry(long currentTick) {
        int interval = AsyncProcessingSettings.telemetryLogIntervalTicks();
        if (interval <= 0) {
            return;
        }
        if (currentTick - lastTelemetryLogTick < interval) {
            return;
        }
        lastTelemetryLogTick = currentTick;
        AsyncProcessingTelemetry telemetry = asyncWorkCoordinator.telemetry();
        AsyncProcessingTelemetry.TelemetrySnapshot snapshot = telemetry.snapshotAndReset();
        if (!snapshot.hasSamples()) {
            return;
        }
        Petsplus.LOGGER.info("Async owner pipeline telemetry: {}", snapshot.toSummaryString());
    }

    private long scaleIntervalForDistance(long baseTicks, MobEntity pet, @Nullable ServerPlayerEntity owner) {
        if (baseTicks <= 0L) {
            return 1L;
        }
        if (baseTicks == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        long scaled = scaleInterval(baseTicks);
        double factor = distanceQualityFactor(pet, owner);
        if (factor <= 1.0D) {
            return scaled;
        }
        long adjusted = Math.round(scaled * factor);
        if (adjusted <= 0L) {
            return 1L;
        }
        if (adjusted >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return adjusted;
    }

    private double distanceQualityFactor(@Nullable MobEntity pet, @Nullable ServerPlayerEntity owner) {
        if (pet == null || owner == null) {
            return 1.0D;
        }
        double distanceSq = owner.squaredDistanceTo(pet);
        if (distanceSq <= 32.0D * 32.0D) {
            return 1.0D;
        }
        if (distanceSq <= 64.0D * 64.0D) {
            return 1.5D;
        }
        if (distanceSq <= 96.0D * 96.0D) {
            return 2.0D;
        }
        return 3.0D;
    }

    private boolean prepareSpatialPayload(OwnerBatchContext context,
                                          OwnerEventType eventType,
                                          long currentTick) {
        if (!AsyncProcessingSettings.asyncSpatialAnalyticsEnabled()) {
            return true;
        }
        UUID ownerId = context.batch().ownerId();
        if (ownerId == null) {
            return true;
        }

        OwnerSpatialResult cached = consumeSpatialResult(ownerId, eventType);
        if (cached != null) {
            context.attachPayload(eventType, cached);
            clearSpatialJobState(ownerId, eventType);
            return true;
        }

        SpatialJobState state = spatialJobState(ownerId, eventType);
        if (state.pending) {
            if (currentTick - state.lastRequestedTick > MAX_SPATIAL_WAIT_TICKS) {
                state.markFailed();
                return true;
            }
            return false;
        }

        if (state.attempts >= MAX_SPATIAL_DEFERRALS) {
            return true;
        }

        state.markRequested(currentTick);
        OwnerBatchSnapshot snapshot = context.snapshot();
        asyncWorkCoordinator.submitOwnerBatch(
            snapshot,
            OwnerSpatialResult::analyze,
            result -> storeSpatialResult(ownerId, eventType, result)
        ).exceptionally(error -> {
            Throwable cause = unwrapAsyncError(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Async spatial analysis rejected for owner {}", ownerId);
            } else {
                Petsplus.LOGGER.error("Async spatial analysis failed for owner {}", ownerId, cause);
            }
            markSpatialJobFailed(ownerId, eventType);
            return null;
        });
        return false;
    }

    private EnumSet<OwnerEventType> collectSpatialListeners() {
        EnumSet<OwnerEventType> listeners = EnumSet.noneOf(OwnerEventType.class);
        for (OwnerEventType type : OwnerEventType.values()) {
            if (type != null && type.requiresSwarmSnapshot() && hasOwnerEventListeners(type)) {
                listeners.add(type);
            }
        }
        return listeners;
    }

    private void primeSpatialAnalysis(OwnerBatchSnapshot snapshot,
                                      EnumSet<OwnerEventType> eventTypes,
                                      long currentTick) {
        if (snapshot == null || eventTypes == null || eventTypes.isEmpty()) {
            return;
        }
        UUID ownerId = snapshot.ownerId();
        if (ownerId == null) {
            return;
        }

        EnumSet<OwnerEventType> scheduledTypes = EnumSet.noneOf(OwnerEventType.class);
        for (OwnerEventType type : eventTypes) {
            if (type == null || !type.requiresSwarmSnapshot()) {
                continue;
            }
            if (hasSpatialResult(ownerId, type)) {
                continue;
            }
            SpatialJobState state = spatialJobState(ownerId, type);
            if (state.pending || state.attempts >= MAX_SPATIAL_DEFERRALS) {
                continue;
            }
            state.markRequested(currentTick);
            scheduledTypes.add(type);
        }

        if (scheduledTypes.isEmpty()) {
            return;
        }

        asyncWorkCoordinator.submitOwnerBatch(
            snapshot,
            OwnerSpatialResult::analyze,
            result -> {
                if (result == null || result.isEmpty()) {
                    for (OwnerEventType type : scheduledTypes) {
                        markSpatialJobFailed(ownerId, type);
                    }
                    return;
                }
                for (OwnerEventType type : scheduledTypes) {
                    storeSpatialResult(ownerId, type, result);
                }
            }
        ).exceptionally(error -> {
            Throwable cause = unwrapAsyncError(error);
            if (cause instanceof RejectedExecutionException) {
                Petsplus.LOGGER.debug("Preemptive spatial analysis rejected for owner {}", ownerId);
            } else {
                Petsplus.LOGGER.error("Preemptive spatial analysis failed for owner {}", ownerId, cause);
            }
            for (OwnerEventType type : scheduledTypes) {
                markSpatialJobFailed(ownerId, type);
            }
            return null;
        });
    }

    private boolean hasSpatialResult(UUID ownerId, OwnerEventType type) {
        if (ownerId == null || type == null) {
            return false;
        }
        EnumMap<OwnerEventType, OwnerSpatialResult> results = pendingSpatialResults.get(ownerId);
        return results != null && results.containsKey(type);
    }

    private void storeSpatialResult(UUID ownerId,
                                    OwnerEventType type,
                                    OwnerSpatialResult result) {
        if (ownerId == null || type == null || result == null) {
            return;
        }
        pendingSpatialResults
            .computeIfAbsent(ownerId, ignored -> new EnumMap<>(OwnerEventType.class))
            .put(type, result);
        spatialJobState(ownerId, type).markCompleted();
    }

    @Nullable
    private OwnerSpatialResult consumeSpatialResult(UUID ownerId, OwnerEventType type) {
        if (ownerId == null || type == null) {
            return null;
        }
        EnumMap<OwnerEventType, OwnerSpatialResult> ownerResults = pendingSpatialResults.get(ownerId);
        if (ownerResults == null) {
            return null;
        }
        OwnerSpatialResult result = ownerResults.remove(type);
        if (ownerResults.isEmpty()) {
            pendingSpatialResults.remove(ownerId);
        }
        return result;
    }

    private void markSpatialJobFailed(UUID ownerId, OwnerEventType type) {
        if (ownerId == null || type == null) {
            return;
        }
        spatialJobState(ownerId, type).markFailed();
    }

    private void clearSpatialJobState(UUID ownerId, OwnerEventType type) {
        if (ownerId == null || type == null) {
            return;
        }
        EnumMap<OwnerEventType, SpatialJobState> states = spatialJobStates.get(ownerId);
        if (states == null) {
            return;
        }
        states.remove(type);
        if (states.isEmpty()) {
            spatialJobStates.remove(ownerId);
        }
    }

    private SpatialJobState spatialJobState(UUID ownerId, OwnerEventType type) {
        EnumMap<OwnerEventType, SpatialJobState> states = spatialJobStates.computeIfAbsent(
            ownerId,
            ignored -> new EnumMap<>(OwnerEventType.class)
        );
        return states.computeIfAbsent(type, ignored -> new SpatialJobState());
    }

    private static Throwable unwrapAsyncError(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }

    private void registerOwnerEventListeners() {
        ownerEventDispatcher.addPresenceListener(ownerPresenceListener);
        ownerEventDispatcher.register(OwnerEventType.INTERVAL, this::flushPendingMoodStimuli);
        ownerEventDispatcher.register(OwnerEventType.XP_GAIN, XpEventHandler::handleOwnerXpGainEvent);
        ownerEventDispatcher.register(OwnerEventType.GOSSIP, this::processGossipDecayEvent);
        ownerEventDispatcher.register(OwnerEventType.SUPPORT, this::processSupportPotionEvent);
        ownerEventDispatcher.register(OwnerEventType.EMOTION, EmotionsEventHandler::onOwnerEmotionEvent);
        ownerEventDispatcher.register(OwnerEventType.ABILITY_TRIGGER, OwnerAbilityEventBridge.INSTANCE);
    }

    private boolean hasOwnerEventListeners(OwnerEventType type) {
        return type != null && ownerEventDispatcher.hasListeners(type);
    }

    private void registerEmotionStimulusBridge() {
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
        bus.addQueueListener(stimulusQueueListener);
        bus.addIdleListener(stimulusIdleListener);
    }

    private void unregisterEmotionStimulusBridge() {
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
        bus.removeQueueListener(stimulusQueueListener);
        bus.removeIdleListener(stimulusIdleListener);
    }

    private void handleStimulusQueued(ServerWorld queuedWorld, MobEntity pet, long queuedTick) {
        if (queuedWorld != this.world) {
            return;
        }
        PetComponent component = getPetComponent(pet);
        if (component.getOwnerUuid() == null) {
            MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            return;
        }

        if (!hasOwnerEventListeners(OwnerEventType.INTERVAL)) {
            MoodService.getInstance().getStimulusBus().dispatchStimuli(pet);
            return;
        }

        long signalTick = Math.max(queuedTick, world.getTime());
        ownerProcessingManager.signalEvent(component, OwnerEventType.INTERVAL, signalTick);
    }

    private boolean handleStimulusIdleScheduled(ServerWorld scheduledWorld, MobEntity pet, long scheduledTick) {
        if (scheduledWorld != this.world) {
            return false;
        }
        if (pet == null || pet.isRemoved()) {
            return false;
        }
        PetComponent component = getPetComponent(pet);
        if (component.getOwnerUuid() == null) {
            return false;
        }

        if (!hasOwnerEventListeners(OwnerEventType.INTERVAL)) {
            return false;
        }

        long signalTick = Math.max(scheduledTick, world.getTime() + 1L);
        ownerProcessingManager.signalEvent(component, OwnerEventType.INTERVAL, signalTick);
        return true;
    }

    private void flushPendingMoodStimuli(OwnerEventFrame frame) {
        if (frame.pets().isEmpty()) {
            return;
        }

        EmotionStimulusBus stimulusBus = MoodService.getInstance().getStimulusBus();
        AsyncWorkCoordinator moodCoordinator = AsyncProcessingSettings.asyncMoodAnalyticsEnabled()
            ? asyncWorkCoordinator
            : null;
        for (PetComponent component : frame.pets()) {
            if (component == null) {
                continue;
            }
            MobEntity pet = component.getPetEntity();
            if (pet == null || pet.isRemoved()) {
                continue;
            }
            stimulusBus.dispatchStimuli(pet, moodCoordinator);
        }
    }

    private void processGossipDecayEvent(OwnerEventFrame frame) {
        List<PetWorkScheduler.ScheduledTask> dueTasks = frame.batch().tasksFor(PetWorkScheduler.TaskType.GOSSIP_DECAY);
        if (dueTasks.isEmpty()) {
            return;
        }

        long currentTick = frame.currentTick();
        GossipPropagationPlan plan = frame.payload(GossipPropagationPlan.class);
        Set<UUID> handled = Collections.emptySet();
        if (plan != null && !plan.isEmpty()) {
            handled = applyGossipPlan(frame, plan, currentTick);
            if (handled.size() >= dueTasks.size()) {
                return;
            }
        }

        List<PetWorkScheduler.ScheduledTask> fallbackTasks;
        if (handled.isEmpty()) {
            fallbackTasks = dueTasks;
        } else {
            fallbackTasks = new ArrayList<>();
            for (PetWorkScheduler.ScheduledTask task : dueTasks) {
                MobEntity pet = task.pet();
                if (pet == null) {
                    continue;
                }
                if (!handled.contains(pet.getUuid())) {
                    fallbackTasks.add(task);
                }
            }
        }

        if (fallbackTasks.isEmpty()) {
            return;
        }

        processGossipDecayFallback(frame, fallbackTasks, currentTick);
    }

    private void processGossipDecayFallback(OwnerEventFrame frame,
                                            List<PetWorkScheduler.ScheduledTask> tasks,
                                            long currentTick) {
        List<PetSwarmIndex.SwarmEntry> swarmSnapshot = frame.swarmSnapshot();
        if (swarmSnapshot.isEmpty()) {
            UUID ownerId = frame.ownerId();
            if (ownerId != null) {
                swarmSnapshot = swarmIndex.snapshotOwner(ownerId);
            }
        }
        if (swarmSnapshot.isEmpty()) {
            return;
        }

        OwnerSpatialResult spatialResult = frame.payload(OwnerSpatialResult.class);
        Map<UUID, PetSwarmIndex.SwarmEntry> swarmById = null;

        for (PetWorkScheduler.ScheduledTask task : tasks) {
            PetComponent component = task.component();
            MobEntity pet = task.pet();
            if (component == null || pet == null || pet.isRemoved()) {
                continue;
            }
            if (component.isGossipOptedOut(currentTick)) {
                continue;
            }
            component.attachStateManager(this);
            PetGossipLedger ledger = component.getGossipLedger();
            if (!ledger.hasShareableRumors(currentTick)) {
                continue;
            }
            List<PetSwarmIndex.SwarmEntry> neighbors;
            if (spatialResult != null) {
                if (swarmById == null) {
                    swarmById = new HashMap<>();
                    for (PetSwarmIndex.SwarmEntry entry : swarmSnapshot) {
                        MobEntity swarmPet = entry.pet();
                        if (swarmPet != null) {
                            swarmById.put(swarmPet.getUuid(), entry);
                        }
                    }
                }
                UUID storytellerId = pet.getUuid();
                List<UUID> neighborIds = spatialResult.neighborsWithin(storytellerId, 12.0D, 4);
                if (!neighborIds.isEmpty()) {
                    neighbors = new ArrayList<>(neighborIds.size());
                    for (UUID id : neighborIds) {
                        PetSwarmIndex.SwarmEntry entry = swarmById.get(id);
                        if (entry == null) {
                            continue;
                        }
                        PetComponent neighborComponent = entry.component();
                        if (neighborComponent == null || neighborComponent.isGossipOptedOut(currentTick)) {
                            continue;
                        }
                        neighbors.add(entry);
                        if (neighbors.size() >= 4) {
                            break;
                        }
                    }
                } else {
                    neighbors = List.of();
                }
            } else {
                neighbors = List.of();
            }

            if (neighbors.isEmpty()) {
                neighbors = collectGossipNeighbors(pet, swarmSnapshot, currentTick);
            }
            if (neighbors.isEmpty()) {
                continue;
            }
            shareRumorsWithNeighbors(component, neighbors, currentTick);
        }
    }

    private void processSupportPotionEvent(OwnerEventFrame frame) {
        List<PetWorkScheduler.ScheduledTask> dueTasks = frame.batch().tasksFor(PetWorkScheduler.TaskType.SUPPORT_POTION);
        if (dueTasks.isEmpty()) {
            return;
        }

        long currentTick = frame.currentTick();
        ServerPlayerEntity frameOwner = frame.owner();

        List<SupportScanRequest> requests = new ArrayList<>(dueTasks.size());
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;

        for (PetWorkScheduler.ScheduledTask task : dueTasks) {
            PetComponent component = task.component();
            MobEntity pet = task.pet();
            if (component == null || pet == null) {
                continue;
            }

            PetComponent tracked = petComponents.get(pet);
            if (tracked == null) {
                continue;
            }
            if (tracked != component) {
                cancelScheduledWork(component, pet);
                continue;
            }
            if (!pet.isAlive()) {
                cancelScheduledWork(component, pet);
                continue;
            }

            ServerPlayerEntity owner = resolveSupportOwner(component, frameOwner);
            if (owner == null) {
                cancelScheduledWork(component, pet);
                continue;
            }

            PetRoleType roleType = component.getRoleType(false);
            if (roleType == null) {
                component.scheduleNextSupportPotionScan(Long.MAX_VALUE);
                ownerProcessingManager.onTaskExecuted(component, PetWorkScheduler.TaskType.SUPPORT_POTION, currentTick);
                continue;
            }

            PetRoleType.SupportPotionBehavior behavior = roleType.supportPotionBehavior();
            if (behavior == null) {
                component.scheduleNextSupportPotionScan(Long.MAX_VALUE);
                ownerProcessingManager.onTaskExecuted(component, PetWorkScheduler.TaskType.SUPPORT_POTION, currentTick);
                continue;
            }

            component.attachStateManager(this);
            component.ensureSchedulingInitialized(currentTick);

            double radius = SupportPotionUtils.getAutoPickupRadius(component, behavior);
            requests.add(new SupportScanRequest(pet, component, owner, behavior, radius));
            sumX += pet.getX();
            sumY += pet.getY();
            sumZ += pet.getZ();
        }

        if (requests.isEmpty()) {
            return;
        }

        int requestCount = requests.size();
        double centerX = sumX / requestCount;
        double centerY = sumY / requestCount;
        double centerZ = sumZ / requestCount;

        double maxReach = 0.0D;
        double maxRadius = 0.0D;
        for (SupportScanRequest request : requests) {
            double dx = request.pet().getX() - centerX;
            double dy = request.pet().getY() - centerY;
            double dz = request.pet().getZ() - centerZ;
            double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            double reach = distance + request.radius();
            if (reach > maxReach) {
                maxReach = reach;
            }
            if (request.radius() > maxRadius) {
                maxRadius = request.radius();
            }
        }
        if (maxReach <= 0.0D) {
            maxReach = maxRadius;
        }

        List<ItemEntity> pooled = SupportPotionVacuumManager.getInstance()
            .collectPotionsNearby(world, new Vec3d(centerX, centerY, centerZ), maxReach);
        Set<ItemEntity> consumed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (SupportScanRequest request : requests) {
            MobEntity pet = request.pet();
            PetComponent component = request.component();
            if (!pet.isAlive()) {
                cancelScheduledWork(component, pet);
                continue;
            }

            List<ItemEntity> available = new ArrayList<>();
            for (ItemEntity item : pooled) {
                if (!consumed.contains(item)) {
                    available.add(item);
                }
            }

            ItemEntity consumedItem = attemptSupportPotionPickup(
                pet,
                request.owner(),
                component,
                request.behavior(),
                available,
                request.radius()
            );

            long delayBase = consumedItem != null ? SUPPORT_POTION_ACTIVE_RECHECK : SUPPORT_POTION_IDLE_RECHECK;
            long delay = scaleIntervalForDistance(delayBase, pet, request.owner());
            component.scheduleNextSupportPotionScan(currentTick + delay);
            ownerProcessingManager.onTaskExecuted(component, PetWorkScheduler.TaskType.SUPPORT_POTION, currentTick);

            if (consumedItem != null) {
                consumed.add(consumedItem);
            }
        }
    }

    private Set<UUID> applyGossipPlan(OwnerEventFrame frame,
                                      GossipPropagationPlan plan,
                                      long currentTick) {
        Map<UUID, PetComponent> componentsById = new HashMap<>();
        for (PetComponent component : frame.pets()) {
            if (component == null) {
                continue;
            }
            MobEntity pet = component.getPetEntity();
            if (pet == null) {
                continue;
            }
            componentsById.put(pet.getUuid(), component);
        }
        for (PetSwarmIndex.SwarmEntry entry : frame.swarmSnapshot()) {
            if (entry == null) {
                continue;
            }
            MobEntity pet = entry.pet();
            PetComponent component = entry.component();
            if (pet == null || component == null) {
                continue;
            }
            componentsById.putIfAbsent(pet.getUuid(), component);
        }

        UUID ownerId = frame.ownerId();
        Set<UUID> handled = new HashSet<>();

        for (UUID storytellerId : plan.storytellers()) {
            if (storytellerId == null) {
                continue;
            }
            PetComponent storyteller = resolveGossipComponent(ownerId, storytellerId, componentsById);
            if (storyteller == null) {
                continue;
            }
            MobEntity storytellerPet = storyteller.getPetEntity();
            if (storytellerPet == null || storytellerPet.isRemoved()) {
                continue;
            }
            if (storyteller.isGossipOptedOut(currentTick)) {
                continue;
            }
            List<Share> shares = plan.sharesFor(storytellerId);
            if (shares.isEmpty()) {
                continue;
            }
            storyteller.attachStateManager(this);
            PetGossipLedger storytellerLedger = storyteller.getGossipLedger();
            boolean anyShared = false;

            for (Share share : shares) {
                if (share == null) {
                    continue;
                }
                PetComponent neighbor = resolveGossipComponent(ownerId, share.listenerId(), componentsById);
                if (neighbor == null) {
                    continue;
                }
                MobEntity neighborPet = neighbor.getPetEntity();
                if (neighborPet == null || neighborPet.isRemoved()) {
                    continue;
                }
                if (neighbor.isGossipOptedOut(currentTick)) {
                    continue;
                }
                RumorEntry rumor = share.rumor();
                if (rumor == null) {
                    continue;
                }
                PetGossipLedger neighborLedger = neighbor.getGossipLedger();
                boolean alreadyHeard = neighborLedger.hasRumor(rumor.topicId());
                neighborLedger.ingestRumorFromPeer(rumor, currentTick, alreadyHeard);
                if (alreadyHeard) {
                    neighborLedger.registerDuplicateHeard(rumor.topicId(), currentTick);
                } else if (GossipTopics.isAbstract(rumor.topicId())) {
                    neighborLedger.registerAbstractHeard(rumor.topicId(), currentTick);
                }
                storytellerLedger.markShared(rumor.topicId(), currentTick);
                anyShared = true;
            }

            if (anyShared) {
                handled.add(storytellerId);
            }
        }

        return handled;
    }

    private PetComponent resolveGossipComponent(@Nullable UUID ownerId,
                                                UUID petId,
                                                Map<UUID, PetComponent> cache) {
        if (petId == null) {
            return null;
        }
        PetComponent component = cache.get(petId);
        if (component != null) {
            return component;
        }
        PetSwarmIndex.SwarmEntry entry = swarmIndex.findEntry(ownerId, petId);
        if (entry == null) {
            return null;
        }
        component = entry.component();
        if (component != null) {
            cache.put(petId, component);
        }
        return component;
    }

    @Nullable
    private ServerPlayerEntity resolveSupportOwner(PetComponent component, @Nullable ServerPlayerEntity frameOwner) {
        if (frameOwner != null && !frameOwner.isRemoved()) {
            UUID overrideId = frameOwner.getUuid();
            UUID componentOwnerId = component.getOwnerUuid();
            if (componentOwnerId != null && componentOwnerId.equals(overrideId)) {
                return frameOwner;
            }
        }

        PlayerEntity owner = component.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner) || owner.isRemoved()) {
            return null;
        }
        return serverOwner;
    }

    private record SupportScanRequest(MobEntity pet,
                                      PetComponent component,
                                      ServerPlayerEntity owner,
                                      PetRoleType.SupportPotionBehavior behavior,
                                      double radius) {
    }

    private List<PetSwarmIndex.SwarmEntry> collectGossipNeighbors(MobEntity storyteller,
                                                                  List<PetSwarmIndex.SwarmEntry> swarm,
                                                                  long currentTick) {
        if (storyteller == null) {
            return List.of();
        }
        double originX = storyteller.getX();
        double originY = storyteller.getY();
        double originZ = storyteller.getZ();
        double radius = 12.0;
        double radiusSq = radius * radius;
        List<PetSwarmIndex.SwarmEntry> matches = new ArrayList<>();
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            if (entry == null) {
                continue;
            }
            if (entry.pet() == storyteller) {
                continue;
            }
            PetComponent neighborComponent = entry.component();
            if (neighborComponent == null || neighborComponent.isGossipOptedOut(currentTick)) {
                continue;
            }
            double dx = entry.x() - originX;
            double dy = entry.y() - originY;
            double dz = entry.z() - originZ;
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq > radiusSq) {
                continue;
            }
            matches.add(entry);
        }
        if (matches.isEmpty()) {
            return List.of();
        }
        matches.sort((a, b) -> Double.compare(distanceSquared(a, originX, originY, originZ),
            distanceSquared(b, originX, originY, originZ)));
        int limit = Math.min(matches.size(), 4);
        return new ArrayList<>(matches.subList(0, limit));
    }

    private double distanceSquared(PetSwarmIndex.SwarmEntry entry, double x, double y, double z) {
        double dx = entry.x() - x;
        double dy = entry.y() - y;
        double dz = entry.z() - z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private void shareRumorsWithNeighbors(PetComponent storyteller,
                                          List<PetSwarmIndex.SwarmEntry> neighbors,
                                          long currentTick) {
        if (neighbors.isEmpty()) {
            return;
        }
        PetGossipLedger storytellerLedger = storyteller.getGossipLedger();
        List<RumorEntry> freshRumors = storytellerLedger.peekFreshRumors(neighbors.size(), currentTick);
        List<RumorEntry> rumors = freshRumors;
        if (rumors.isEmpty()) {
            rumors = storytellerLedger.peekAbstractRumors(Math.min(1, neighbors.size()), currentTick);
        } else if (rumors.size() < neighbors.size()) {
            List<RumorEntry> supplemental = storytellerLedger.peekAbstractRumors(neighbors.size() - rumors.size(), currentTick);
            if (!supplemental.isEmpty()) {
                rumors = new ArrayList<>(rumors);
                rumors.addAll(supplemental);
            }
        }
        if (rumors.isEmpty()) {
            return;
        }
        Set<PetComponent> recipients = new HashSet<>();
        int rumorIndex = 0;
        for (PetSwarmIndex.SwarmEntry neighborEntry : neighbors) {
            if (rumorIndex >= rumors.size()) {
                break;
            }
            PetComponent neighborComponent = neighborEntry.component();
            if (neighborComponent == null || !recipients.add(neighborComponent)) {
                continue;
            }
            RumorEntry rumor = rumors.get(rumorIndex++);
            PetGossipLedger neighborLedger = neighborComponent.getGossipLedger();
            boolean alreadyHeard = neighborLedger.hasRumor(rumor.topicId());
            neighborLedger.ingestRumorFromPeer(rumor, currentTick, alreadyHeard);
            if (alreadyHeard) {
                neighborLedger.registerDuplicateHeard(rumor.topicId(), currentTick);
            } else if (GossipTopics.isAbstract(rumor.topicId())) {
                neighborLedger.registerAbstractHeard(rumor.topicId(), currentTick);
            }
            storytellerLedger.markShared(rumor.topicId(), currentTick);
        }
    }

    public OwnerEventDispatcher getOwnerEventDispatcher() {
        return ownerEventDispatcher;
    }

    public void dispatchOwnerEvent(ServerPlayerEntity owner,
                                   OwnerEventType eventType,
                                   @Nullable Object payload) {
        if (eventType == null) {
            return;
        }
        EnumSet<OwnerEventType> events = EnumSet.of(eventType);
        EnumMap<OwnerEventType, Object> payloads = null;
        if (payload != null) {
            payloads = new EnumMap<>(OwnerEventType.class);
            payloads.put(eventType, payload);
        }
        dispatchOwnerEvents(owner, events, payloads);
    }

    public void dispatchOwnerEvents(@Nullable ServerPlayerEntity owner,
                                    EnumSet<OwnerEventType> eventTypes,
                                    @Nullable Map<OwnerEventType, Object> payloads) {
        UUID ownerId = owner != null ? owner.getUuid() : null;
        dispatchOwnerEvents(owner, ownerId, eventTypes, payloads);
    }

    public void dispatchOwnerEvents(@Nullable ServerPlayerEntity owner,
                                    @Nullable UUID ownerId,
                                    EnumSet<OwnerEventType> eventTypes,
                                    @Nullable Map<OwnerEventType, Object> payloads) {
        if ((owner == null || owner.isRemoved()) && ownerId == null) {
            return;
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            return;
        }

        EnumSet<OwnerEventType> interestedEvents = EnumSet.copyOf(eventTypes);
        interestedEvents.removeIf(type -> type == null || !hasOwnerEventListeners(type));
        if (interestedEvents.isEmpty()) {
            return;
        }

        long currentTick = world.getTime();
        UUID effectiveOwnerId = owner != null ? owner.getUuid() : ownerId;
        ServerPlayerEntity sanitizedOwner = owner != null && !owner.isRemoved() ? owner : null;

        EnumSet<OwnerEventType> immutableEvents = EnumSet.copyOf(interestedEvents);
        EnumMap<OwnerEventType, Object> payloadCopy = null;
        if (payloads != null && !payloads.isEmpty()) {
            payloadCopy = new EnumMap<>(OwnerEventType.class);
            for (Map.Entry<OwnerEventType, Object> entry : payloads.entrySet()) {
                OwnerEventType type = entry.getKey();
                Object value = entry.getValue();
                if (type != null && value != null && immutableEvents.contains(type)) {
                    payloadCopy.put(type, value);
                }
            }
            if (payloadCopy.isEmpty()) {
                payloadCopy = null;
            }
        }

        OwnerTaskBatch batch = ownerProcessingManager.createAdHocBatch(effectiveOwnerId, immutableEvents, currentTick);
        if (batch == null) {
            List<PetSwarmIndex.SwarmEntry> swarmSnapshot = swarmIndex.snapshotOwner(effectiveOwnerId);
            if (swarmSnapshot.isEmpty()) {
                return;
            }
            List<PetComponent> pets = new ArrayList<>(swarmSnapshot.size());
            for (PetSwarmIndex.SwarmEntry entry : swarmSnapshot) {
                PetComponent component = entry.component();
                if (component != null) {
                    pets.add(component);
                }
            }
            if (pets.isEmpty()) {
                return;
            }
            batch = OwnerTaskBatch.adHoc(effectiveOwnerId, pets, immutableEvents, currentTick, sanitizedOwner);
        }

        try (OwnerTaskBatch closable = batch) {
            ownerBatchProcessor.processBatch(closable, sanitizedOwner, currentTick, false, payloadCopy);
        }
    }

    public void fireAbilityTrigger(ServerPlayerEntity owner,
                                   String triggerId,
                                   @Nullable Map<String, Object> eventData) {
        if (owner == null || owner.isRemoved()) {
            return;
        }
        if (triggerId == null || triggerId.isEmpty()) {
            return;
        }
        if (!hasOwnerEventListeners(OwnerEventType.ABILITY_TRIGGER)) {
            return;
        }

        EnumSet<OwnerEventType> events = EnumSet.of(OwnerEventType.ABILITY_TRIGGER);
        EnumMap<OwnerEventType, Object> payload = new EnumMap<>(OwnerEventType.class);
        payload.put(OwnerEventType.ABILITY_TRIGGER, AbilityTriggerPayload.of(triggerId, eventData));
        dispatchOwnerEvents(owner, events, payload);
    }

    public AbilityTriggerResult dispatchAbilityTrigger(ServerPlayerEntity owner,
                                                       String triggerId,
                                                       @Nullable Map<String, Object> eventData) {
        if (owner == null || owner.isRemoved()) {
            return AbilityTriggerResult.empty();
        }
        if (triggerId == null || triggerId.isEmpty()) {
            return AbilityTriggerResult.empty();
        }
        if (!hasOwnerEventListeners(OwnerEventType.ABILITY_TRIGGER)) {
            return AbilityTriggerResult.empty();
        }
        EnumSet<OwnerEventType> events = EnumSet.of(OwnerEventType.ABILITY_TRIGGER);
        EnumMap<OwnerEventType, Object> payload = new EnumMap<>(OwnerEventType.class);
        CompletableFuture<AbilityTriggerResult> resultFuture = new CompletableFuture<>();
        payload.put(OwnerEventType.ABILITY_TRIGGER, AbilityTriggerPayload.of(triggerId, eventData, resultFuture));
        dispatchOwnerEvents(owner, events, payload);
        if (!resultFuture.isDone()) {
            resultFuture.complete(AbilityTriggerResult.empty());
        }
        try {
            return resultFuture.join();
        } catch (CompletionException exception) {
            Petsplus.LOGGER.error("Ability trigger {} failed for owner {}", triggerId, owner.getName().getString(), exception);
            return AbilityTriggerResult.empty();
        }
    }

    public void requestEmotionEvent(ServerPlayerEntity owner,
                                    @Nullable Object payload) {
        if (owner == null || owner.isRemoved()) {
            return;
        }
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION);
        EnumSet<OwnerEventType> events = EnumSet.of(OwnerEventType.EMOTION);
        EnumMap<OwnerEventType, Object> payloads = null;
        if (payload != null) {
            payloads = new EnumMap<>(OwnerEventType.class);
            payloads.put(OwnerEventType.EMOTION, payload);
        }
        dispatchOwnerEvents(owner, events, payloads);
    }

    private void cancelScheduledWork(PetComponent component, MobEntity pet) {
        workScheduler.unscheduleAll(component);
        component.markSchedulingUninitialized();
        component.invalidateSwarmTracking();
        swarmIndex.untrackPet(pet);
        ownerProcessingManager.onTasksCleared(component);
        ownerProcessingManager.untrackPet(component);
    }

    private void runScheduledTask(PetWorkScheduler.ScheduledTask task, long currentTick) {
        runScheduledTask(task, null, null, currentTick);
    }

    private void runScheduledTask(PetWorkScheduler.ScheduledTask task,
                                  @Nullable ServerPlayerEntity ownerOverride,
                                  long currentTick) {
        runScheduledTask(task, ownerOverride, null, currentTick);
    }

    private void runScheduledTask(PetWorkScheduler.ScheduledTask task,
                                  @Nullable ServerPlayerEntity ownerOverride,
                                  @Nullable OwnerBatchContext context,
                                  long currentTick) {
        PetComponent component = task.component();
        MobEntity pet = task.pet();
        if (component == null || pet == null) {
            return;
        }

        PetComponent tracked = petComponents.get(pet);
        if (tracked == null) {
            return;
        }

        if (tracked != component) {
            cancelScheduledWork(component, pet);
            return;
        }

        if (!pet.isAlive()) {
            cancelScheduledWork(component, pet);
            return;
        }

        ServerPlayerEntity serverOwner = context != null ? context.owner() : null;
        if (serverOwner == null && ownerOverride != null && !ownerOverride.isRemoved()) {
            UUID overrideId = ownerOverride.getUuid();
            UUID componentOwnerId = component.getOwnerUuid();
            if (componentOwnerId != null && componentOwnerId.equals(overrideId)) {
                serverOwner = ownerOverride;
            }
        }

        if (serverOwner == null) {
            PlayerEntity owner = component.getOwner();
            if (!(owner instanceof ServerPlayerEntity resolvedOwner) || owner.isRemoved()) {
                cancelScheduledWork(component, pet);
                return;
            }
            serverOwner = resolvedOwner;
        }

        if (serverOwner.isRemoved()) {
            cancelScheduledWork(component, pet);
            return;
        }

        component.attachStateManager(this);

        switch (task.type()) {
            case INTERVAL -> runIntervalAbility(pet, component, serverOwner, context, currentTick);
            case AURA -> runAuraPulse(pet, component, serverOwner, context, currentTick);
            case SUPPORT_POTION -> runSupportScan(pet, component, serverOwner, currentTick);
            case PARTICLE -> runParticlePass(pet, component, serverOwner, currentTick);
            case GOSSIP_DECAY -> component.tickGossipLedger(currentTick);
        }

        ownerProcessingManager.onTaskExecuted(component, task.type(), currentTick);
    }

    private void runIntervalAbility(MobEntity pet,
                                    PetComponent component,
                                    ServerPlayerEntity owner,
                                    @Nullable OwnerBatchContext context,
                                    long currentTick) {
        boolean handledCooldowns = context != null && context.applyAbilityCooldownPlan(component, currentTick);
        if (!handledCooldowns) {
            component.updateCooldowns();
        }
        TriggerContext ctx = new TriggerContext(world, pet, owner, "interval_tick");
        woflo.petsplus.abilities.AbilityManager.triggerAbilities(pet, ctx);
        long delay = scaleIntervalForDistance(INTERVAL_TICK_SPACING, pet, owner);
        component.scheduleNextIntervalTick(currentTick + delay);
    }

    private void runAuraPulse(MobEntity pet, PetComponent component, ServerPlayerEntity owner,
                              @Nullable OwnerBatchContext context, long currentTick) {
        PetRoleType roleType = component.getRoleType(false);
        if (roleType == null) {
            component.scheduleNextAuraCheck(Long.MAX_VALUE);
            return;
        }

        boolean hasAuraContent = !roleType.passiveAuras().isEmpty() || roleType.supportPotionBehavior() != null;
        if (!hasAuraContent) {
            component.scheduleNextAuraCheck(Long.MAX_VALUE);
            return;
        }

        OwnerSpatialResult spatialResult = null;
        if (context != null) {
            Object payload = context.payload(OwnerEventType.AURA);
            if (payload instanceof OwnerSpatialResult result) {
                spatialResult = result;
            }
        }

        List<PetSwarmIndex.SwarmEntry> swarmSnapshot = null;
        if (spatialResult != null) {
            swarmSnapshot = gatherSwarmFromSpatial(component.getOwnerUuid(), spatialResult);
        }
        if ((swarmSnapshot == null || swarmSnapshot.isEmpty()) && context != null) {
            swarmSnapshot = context.swarmSnapshot();
        }

        try {
            long nextTick = PetsplusEffectManager.applyRoleAuraEffects(
                world,
                pet,
                component,
                owner,
                auraTargetResolver,
                currentTick,
                swarmSnapshot,
                spatialResult
            );
            long baseDelay;
            if (nextTick == Long.MAX_VALUE) {
                baseDelay = DEFAULT_AURA_RECHECK;
            } else {
                long scheduledTick = Math.max(currentTick + 1, nextTick);
                baseDelay = Math.max(1L, scheduledTick - currentTick);
            }
            long delay = scaleIntervalForDistance(baseDelay, pet, owner);
            component.scheduleNextAuraCheck(currentTick + delay);
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to apply aura effects for pet {}", pet.getUuid(), e);
            long delay = scaleIntervalForDistance(DEFAULT_AURA_RECHECK, pet, owner);
            component.scheduleNextAuraCheck(currentTick + delay);
        }
    }

    private List<PetSwarmIndex.SwarmEntry> gatherSwarmFromSpatial(@Nullable UUID ownerId,
                                                                  @Nullable OwnerSpatialResult spatialResult) {
        if (ownerId == null || spatialResult == null || spatialResult.isEmpty()) {
            return List.of();
        }
        List<PetSwarmIndex.SwarmEntry> entries = new ArrayList<>();
        for (UUID petId : spatialResult.petIds()) {
            PetSwarmIndex.SwarmEntry entry = swarmIndex.findEntry(ownerId, petId);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private void runSupportScan(MobEntity pet, PetComponent component, ServerPlayerEntity owner, long currentTick) {
        PetRoleType roleType = component.getRoleType(false);
        if (roleType == null) {
            component.scheduleNextSupportPotionScan(Long.MAX_VALUE);
            return;
        }

        PetRoleType.SupportPotionBehavior behavior = roleType.supportPotionBehavior();
        if (behavior == null) {
            component.scheduleNextSupportPotionScan(Long.MAX_VALUE);
            return;
        }

        boolean processed = pickupNearbyPotionsForSupport(pet, owner, component, behavior);
        long delay = processed ? SUPPORT_POTION_ACTIVE_RECHECK : SUPPORT_POTION_IDLE_RECHECK;
        delay = scaleIntervalForDistance(delay, pet, owner);
        component.scheduleNextSupportPotionScan(currentTick + delay);
    }

    private void runParticlePass(MobEntity pet, PetComponent component, ServerPlayerEntity owner, long currentTick) {
        boolean emitted = false;
        if (woflo.petsplus.ui.ParticleEffectManager.shouldEmitParticles(pet, world)) {
            woflo.petsplus.ui.ParticleEffectManager.emitRoleParticles(pet, world, currentTick);
            emitted = true;
        }

        // Check for tribute orbital effects
        if (woflo.petsplus.events.TributeHandler.isPetWaitingForTribute(pet)) {
            woflo.petsplus.ui.TributeOrbitalEffects.emitTributeOrbital(pet, world, currentTick);
            emitted = true;
        }

        component.updateCooldowns();
        long delay = emitted ? PARTICLE_RECHECK_INTERVAL : PARTICLE_RECHECK_INTERVAL * 2;
        delay = scaleIntervalForDistance(delay, pet, owner);
        component.scheduleNextParticleCheck(currentTick + delay);
    }

    @Nullable
    private ServerPlayerEntity resolveOnlineOwner(@Nullable UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return null;
        }
        PlayerManager manager = server.getPlayerManager();
        if (manager == null) {
            return null;
        }
        ServerPlayerEntity player = manager.getPlayer(ownerId);
        if (player == null || player.isRemoved()) {
            return null;
        }
        return player;
    }

    private final class OwnerBatchProcessor {

        void processBatch(OwnerTaskBatch batch, long currentTick) {
            processBatch(batch, null, currentTick, true, null);
        }

        void processBatch(OwnerTaskBatch batch,
                          @Nullable ServerPlayerEntity ownerOverride,
                          long currentTick) {
            processBatch(batch, ownerOverride, currentTick, true, null);
        }

        void processBatch(OwnerTaskBatch batch,
                          @Nullable ServerPlayerEntity ownerOverride,
                          long currentTick,
                          boolean markEvents,
                          @Nullable Map<OwnerEventType, Object> eventPayloads) {
            if (batch == null) {
                return;
            }
            if (batch.isEmpty() && batch.dueEventsView().isEmpty()) {
                batch.close();
                return;
            }

            boolean asyncEligible = markEvents && AsyncProcessingSettings.asyncOwnerProcessingEnabled();
            if (asyncEligible && tryProcessBatchAsync(batch, ownerOverride, currentTick, eventPayloads)) {
                return;
            }

            processBatchSynchronously(batch, ownerOverride, currentTick, markEvents, eventPayloads, null);
        }

        void processBatchWithSnapshot(OwnerTaskBatch batch,
                                      @Nullable ServerPlayerEntity ownerOverride,
                                      long currentTick,
                                      boolean markEvents,
                                      @Nullable Map<OwnerEventType, Object> eventPayloads,
                                      OwnerBatchSnapshot snapshot) {
            if (batch == null) {
                return;
            }
            if (batch.isEmpty() && batch.dueEventsView().isEmpty()) {
                batch.close();
                return;
            }

            boolean asyncEligible = markEvents && AsyncProcessingSettings.asyncOwnerProcessingEnabled();
            if (asyncEligible && tryProcessBatchAsyncWithSnapshot(batch, ownerOverride, currentTick, eventPayloads, snapshot)) {
                return;
            }

            processBatchSynchronously(batch, ownerOverride, currentTick, markEvents, eventPayloads, snapshot);
        }

        private boolean tryProcessBatchAsync(OwnerTaskBatch batch,
                                             @Nullable ServerPlayerEntity ownerOverride,
                                             long currentTick,
                                             @Nullable Map<OwnerEventType, Object> eventPayloads) {
            long captureStart = System.nanoTime();
            OwnerBatchSnapshot snapshot = OwnerBatchSnapshot.capture(batch);
            asyncWorkCoordinator.telemetry().recordCaptureDuration(System.nanoTime() - captureStart);

            return tryProcessBatchAsyncWithSnapshot(batch, ownerOverride, currentTick, eventPayloads, snapshot);
        }

        private boolean tryProcessBatchAsyncWithSnapshot(OwnerTaskBatch batch,
                                                         @Nullable ServerPlayerEntity ownerOverride,
                                                         long currentTick,
                                                         @Nullable Map<OwnerEventType, Object> eventPayloads,
                                                         OwnerBatchSnapshot snapshot) {
            CompletableFuture<OwnerBatchPlan> future = asyncWorkCoordinator.submitOwnerBatch(
                snapshot,
                OwnerBatchPlanner::plan,
                plan -> applyAsyncPlan(batch, ownerOverride, currentTick, eventPayloads, snapshot, plan)
            );

            future.whenCompleteAsync((ignored, error) -> {
                if (error == null) {
                    return;
                }
                Throwable cause = unwrapAsyncError(error);
                if (cause instanceof RejectedExecutionException) {
                    Petsplus.LOGGER.debug("Async owner batch rejected for owner {}", snapshot.ownerId());
                } else {
                    Petsplus.LOGGER.error("Async owner batch failed for owner {}", snapshot.ownerId(), cause);
                }
                processBatchSynchronously(batch, ownerOverride, currentTick, true, eventPayloads, snapshot);
            }, runnable -> {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    server.submit(runnable);
                } else {
                    runnable.run();
                }
            });

            return true;
        }

        private void applyAsyncPlan(OwnerTaskBatch batch,
                                    @Nullable ServerPlayerEntity ownerOverride,
                                    long currentTick,
                                    @Nullable Map<OwnerEventType, Object> eventPayloads,
                                    OwnerBatchSnapshot snapshot,
                                    OwnerBatchPlan plan) {
            try {
                ServerPlayerEntity owner = resolveOwner(ownerOverride, batch, snapshot);
                Map<OwnerEventType, Object> payloads = mergePayloads(eventPayloads, plan);
                AbilityCooldownPlan cooldownPlan = plan == null ? AbilityCooldownPlan.empty() : plan.abilityCooldownPlan();
                OwnerBatchContext context = new OwnerBatchContext(batch, owner, payloads, snapshot, cooldownPlan);
                runBatch(context, currentTick, true);
                applyPlanPrediction(snapshot, plan, currentTick);
            } finally {
                batch.close();
            }
        }

        private void processBatchSynchronously(OwnerTaskBatch batch,
                                               @Nullable ServerPlayerEntity ownerOverride,
                                               long currentTick,
                                               boolean markEvents,
                                               @Nullable Map<OwnerEventType, Object> eventPayloads,
                                               @Nullable OwnerBatchSnapshot snapshot) {
            try {
                ServerPlayerEntity owner = resolveOwner(ownerOverride, batch, snapshot);
                OwnerBatchSnapshot effectiveSnapshot = snapshot;
                if (effectiveSnapshot == null) {
                    long captureStart = System.nanoTime();
                    effectiveSnapshot = OwnerBatchSnapshot.capture(batch);
                    asyncWorkCoordinator.telemetry().recordCaptureDuration(System.nanoTime() - captureStart);
                }
                AbilityCooldownPlan cooldownPlan = AbilityCooldownPlanner.plan(effectiveSnapshot);
                OwnerBatchContext context = new OwnerBatchContext(batch, owner, eventPayloads, effectiveSnapshot, cooldownPlan);
                runBatch(context, currentTick, markEvents);
                maybeSchedulePredictiveJob(context);
            } finally {
                batch.close();
            }
        }

        private ServerPlayerEntity resolveOwner(@Nullable ServerPlayerEntity ownerOverride,
                                                OwnerTaskBatch batch,
                                                @Nullable OwnerBatchSnapshot snapshot) {
            ServerPlayerEntity owner = sanitizeOwner(ownerOverride);
            if (owner != null) {
                return owner;
            }
            owner = sanitizeOwner(batch.lastKnownOwner());
            if (owner != null) {
                return owner;
            }
            UUID lastKnownId = snapshot != null ? snapshot.lastKnownOwnerId() : null;
            if (lastKnownId != null) {
                owner = resolveOnlineOwner(lastKnownId);
                if (owner != null) {
                    return owner;
                }
            }
            return resolveOnlineOwner(snapshot != null ? snapshot.ownerId() : batch.ownerId());
        }

        private ServerPlayerEntity sanitizeOwner(@Nullable ServerPlayerEntity candidate) {
            if (candidate == null || candidate.isRemoved()) {
                return null;
            }
            return candidate;
        }

        private Map<OwnerEventType, Object> mergePayloads(@Nullable Map<OwnerEventType, Object> base,
                                                          OwnerBatchPlan plan) {
            if ((base == null || base.isEmpty()) && (plan == null || plan.eventPayloads().isEmpty())) {
                return Map.of();
            }
            EnumMap<OwnerEventType, Object> merged = new EnumMap<>(OwnerEventType.class);
            if (base != null) {
                for (Map.Entry<OwnerEventType, Object> entry : base.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        merged.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (plan != null) {
                merged.putAll(plan.eventPayloads());
            }
            return merged;
        }

        private void applyPlanPrediction(OwnerBatchSnapshot snapshot,
                                         OwnerBatchPlan plan,
                                         long currentTick) {
            if (plan == null) {
                return;
            }
            UUID ownerId = snapshot.ownerId();
            if (ownerId == null) {
                return;
            }
            OwnerSchedulingPrediction prediction = plan.schedulingPrediction();
            if (prediction != null && !prediction.isEmpty()) {
                ownerProcessingManager.applySchedulingPrediction(ownerId, prediction, currentTick);
            }

            Map<OwnerEventType, Long> windows = plan.eventWindowPredictions();
            if (!windows.isEmpty()) {
                ownerProcessingManager.applyEventWindowPredictions(ownerId, windows, currentTick);
            }
        }

        private void runBatch(OwnerBatchContext context,
                              long currentTick,
                              boolean markEvents) {
            context.batch().forEachBucket((type, tasks) -> executeBucket(type, tasks, context, currentTick));
            handleDueEvents(context, context.dueEvents(), currentTick, markEvents);
        }

        private void executeBucket(PetWorkScheduler.TaskType type,
                                   List<PetWorkScheduler.ScheduledTask> tasks,
                                   OwnerBatchContext context,
                                   long currentTick) {
            if (tasks == null || tasks.isEmpty()) {
                return;
            }

            OwnerEventType eventType = OwnerEventType.fromTaskType(type);
            boolean handledByEvent = eventType == OwnerEventType.SUPPORT
                && context.dueEvents().contains(OwnerEventType.SUPPORT)
                && ownerEventDispatcher.hasListeners(OwnerEventType.SUPPORT);

            if (handledByEvent) {
                return;
            }

            if (context.hasOwner()) {
                switch (type) {
                    case AURA, SUPPORT_POTION, GOSSIP_DECAY -> context.ensureSwarmSnapshot();
                    case INTERVAL -> context.ensurePetsPrimed(currentTick);
                    default -> {
                    }
                }
            }

            runTasks(tasks, context, currentTick);
        }

        private void runTasks(List<PetWorkScheduler.ScheduledTask> tasks,
                              OwnerBatchContext context,
                              long currentTick) {
            ServerPlayerEntity owner = context.owner();
            if (owner == null) {
                for (PetWorkScheduler.ScheduledTask task : tasks) {
                    StateManager.this.runScheduledTask(task, null, context, currentTick);
                }
                return;
            }

            for (PetWorkScheduler.ScheduledTask task : tasks) {
                StateManager.this.runScheduledTask(task, owner, context, currentTick);
            }
        }

        private void handleDueEvents(OwnerBatchContext context,
                                     Set<OwnerEventType> dueEvents,
                                     long currentTick,
                                     boolean markEvents) {
            if (dueEvents.isEmpty()) {
                return;
            }

            EnumSet<OwnerEventType> activeEvents = EnumSet.noneOf(OwnerEventType.class);
            for (OwnerEventType eventType : dueEvents) {
                if (ownerEventDispatcher.hasListeners(eventType)) {
                    activeEvents.add(eventType);
                } else if (markEvents) {
                    ownerProcessingManager.markOwnerEventExecuted(context.batch().ownerId(), eventType, currentTick);
                }
            }

            if (activeEvents.isEmpty()) {
                return;
            }

            EnumSet<OwnerEventType> deferred = context.prepareAsyncPayloads(activeEvents, currentTick);
            if (!deferred.isEmpty()) {
                UUID ownerId = context.batch().ownerId();
                for (OwnerEventType type : deferred) {
                    if (ownerId != null) {
                        ownerProcessingManager.signalEvent(ownerId, type, currentTick + 1L);
                    }
                }
                activeEvents.removeAll(deferred);
            }

            if (activeEvents.isEmpty()) {
                return;
            }

            boolean requiresSwarm = false;
            boolean primesScheduling = false;
            for (OwnerEventType eventType : activeEvents) {
                if (eventType.requiresSwarmSnapshot()) {
                    requiresSwarm = true;
                }
                if (eventType.primesScheduling()) {
                    primesScheduling = true;
                }
            }

            if (requiresSwarm) {
                context.ensureSwarmSnapshot();
            }

            if (primesScheduling) {
                context.ensurePetsPrimed(currentTick);
            }

            dispatchOwnerEvents(context, activeEvents, currentTick, markEvents, requiresSwarm);
        }

        private void dispatchOwnerEvents(OwnerBatchContext context,
                                         EnumSet<OwnerEventType> dueEvents,
                                         long currentTick,
                                         boolean markEvents,
                                         boolean requiresSwarmSnapshot) {
            if (dueEvents.isEmpty()) {
                return;
            }

            List<PetSwarmIndex.SwarmEntry> swarmSnapshot = requiresSwarmSnapshot
                ? context.swarmSnapshot()
                : context.swarmSnapshotIfPrimed();

            for (OwnerEventType eventType : dueEvents) {
                if (!ownerEventDispatcher.hasListeners(eventType)) {
                    if (markEvents) {
                        ownerProcessingManager.markOwnerEventExecuted(context.batch().ownerId(), eventType, currentTick);
                    }
                    continue;
                }
                try (OwnerEventFrame frame = OwnerEventFrame.obtain(
                    eventType,
                    world,
                    context.owner(),
                    context.batch().ownerId(),
                    context.pets(),
                    swarmSnapshot,
                    dueEvents,
                    currentTick,
                    context.batch(),
                    context.snapshot(),
                    context.payload(eventType)
                )) {
                    ownerEventDispatcher.dispatch(frame);
                }
                if (markEvents) {
                    ownerProcessingManager.markOwnerEventExecuted(context.batch().ownerId(), eventType, currentTick);
                }
            }
        }

        private void maybeSchedulePredictiveJob(OwnerBatchContext context) {
            if (!AsyncProcessingSettings.asyncPredictiveSchedulingEnabled()) {
                return;
            }
            OwnerBatchSnapshot snapshot = context.snapshot();
            UUID ownerId = snapshot.ownerId();
            if (ownerId == null) {
                return;
            }
            asyncWorkCoordinator.submitOwnerBatch(
                snapshot,
                OwnerSchedulingPrediction::predict,
                prediction -> {
                    if (prediction == null || prediction.isEmpty()) {
                        return;
                    }
                    ownerProcessingManager.applySchedulingPrediction(ownerId, prediction, world.getTime());
                }
            ).exceptionally(error -> {
                Throwable cause = unwrapAsyncError(error);
                if (cause instanceof RejectedExecutionException) {
                    Petsplus.LOGGER.debug("Async predictive scheduling skipped for owner {} due to throttling", ownerId);
                } else {
                    Petsplus.LOGGER.error("Async predictive scheduling failed for owner {}", ownerId, cause);
                }
                return null;
            });
        }
    }
    private final class OwnerBatchContext {
        private final OwnerTaskBatch batch;
        private final OwnerBatchSnapshot snapshot;
        private final ServerPlayerEntity owner;
        private final Set<OwnerEventType> dueEvents;
        private final EnumMap<OwnerEventType, Object> payloads;
        private final AbilityCooldownPlan abilityCooldownPlan;
        private List<PetSwarmIndex.SwarmEntry> swarmSnapshot = List.of();
        private boolean swarmPrimed;

        private OwnerBatchContext(OwnerTaskBatch batch,
                                  @Nullable ServerPlayerEntity owner,
                                  @Nullable Map<OwnerEventType, Object> eventPayloads,
                                  OwnerBatchSnapshot snapshot,
                                  AbilityCooldownPlan abilityCooldownPlan) {
            this.batch = batch;
            this.snapshot = snapshot;
            this.owner = owner != null && !owner.isRemoved() ? owner : null;
            Set<OwnerEventType> events = batch.dueEventsView();
            this.dueEvents = events.isEmpty()
                ? Collections.emptySet()
                : events;
            if (eventPayloads == null || eventPayloads.isEmpty()) {
                this.payloads = new EnumMap<>(OwnerEventType.class);
            } else {
                this.payloads = new EnumMap<>(OwnerEventType.class);
                for (Map.Entry<OwnerEventType, Object> entry : eventPayloads.entrySet()) {
                    OwnerEventType type = entry.getKey();
                    Object value = entry.getValue();
                    if (type != null && value != null) {
                        this.payloads.put(type, value);
                    }
                }
            }
            this.abilityCooldownPlan = abilityCooldownPlan == null ? AbilityCooldownPlan.empty() : abilityCooldownPlan;
        }

        EnumSet<OwnerEventType> prepareAsyncPayloads(EnumSet<OwnerEventType> candidates, long currentTick) {
            if (candidates.isEmpty()) {
                return EnumSet.noneOf(OwnerEventType.class);
            }
            EnumSet<OwnerEventType> deferred = EnumSet.noneOf(OwnerEventType.class);
            for (OwnerEventType type : candidates) {
                if (type == null || !type.requiresSwarmSnapshot()) {
                    continue;
                }
                if (payloads.containsKey(type)) {
                    continue;
                }
                boolean ready = StateManager.this.prepareSpatialPayload(this, type, currentTick);
                if (!ready) {
                    deferred.add(type);
                }
            }
            return deferred;
        }

        void attachPayload(OwnerEventType type, Object payload) {
            if (type == null || payload == null) {
                return;
            }
            payloads.put(type, payload);
        }

        OwnerBatchSnapshot snapshot() {
            return snapshot;
        }

        Set<OwnerEventType> dueEvents() {
            return dueEvents;
        }

        boolean hasOwner() {
            return owner != null;
        }

        @Nullable
        ServerPlayerEntity owner() {
            return owner;
        }

        List<PetComponent> pets() {
            return batch.pets();
        }

        OwnerTaskBatch batch() {
            return batch;
        }

        boolean applyAbilityCooldownPlan(PetComponent component, long currentTick) {
            return abilityCooldownPlan.applyTo(component, currentTick);
        }

        void ensurePetsPrimed(long currentTick) {
            for (PetComponent component : pets()) {
                if (component != null) {
                    component.ensureSchedulingInitialized(currentTick);
                }
            }
        }

        void ensureSwarmSnapshot() {
            if (swarmPrimed) {
                return;
            }
            UUID ownerId = batch.ownerId();
            if (ownerId == null) {
                swarmSnapshot = List.of();
                swarmPrimed = true;
                return;
            }
            swarmSnapshot = swarmIndex.snapshotOwner(ownerId);
            swarmPrimed = true;
        }

        List<PetSwarmIndex.SwarmEntry> swarmSnapshot() {
            ensureSwarmSnapshot();
            return swarmSnapshot;
        }

        List<PetSwarmIndex.SwarmEntry> swarmSnapshotIfPrimed() {
            return swarmPrimed ? swarmSnapshot : List.of();
        }

        @Nullable
        Object payload(OwnerEventType type) {
            return payloads.get(type);
        }
    }

    /**
     * Called when the world is being saved or unloaded to ensure all pet data is persisted.
     * The actual persistence happens through the MobEntityDataMixin, but this method
     * can be used for any additional cleanup or state synchronization.
     */
    public void onWorldSave() {
        // Currently, the MobEntityDataMixin handles automatic persistence
        // This method is available for future enhancements or explicit saves
        int petCount = petComponents.size();
        int ownerCount = ownerStates.size();

        if (petCount > 0 || ownerCount > 0) {
            woflo.petsplus.Petsplus.LOGGER.debug("PetsPlus: Preparing to save world {} with {} pets and {} owners",
                world.getRegistryKey().getValue(), petCount, ownerCount);
        }
    }

    private void shutdown() {
        if (disposed) {
            return;
        }
        disposed = true;
        unregisterEmotionStimulusBridge();
        ownerEventDispatcher.removePresenceListener(ownerPresenceListener);
        ownerEventDispatcher.clear();
        workScheduler.clear();
        ownerProcessingManager.shutdown();
        swarmIndex.clear();
        pendingSpatialResults.clear();
        spatialJobStates.clear();
        petComponents.clear();
        ownerStates.clear();
        asyncWorkCoordinator.drainMainThreadTasks();
        asyncWorkCoordinator.close();
        asyncWorkerRegistration.close();
    }
    
    /**
     * Get all pet components for debugging/admin purposes.
     */
    public Map<MobEntity, PetComponent> getAllPetComponents() {
        return new HashMap<>(petComponents);
    }
    
    /**
     * Get all owner states for debugging/admin purposes.
     */
    public Map<PlayerEntity, OwnerCombatState> getAllOwnerStates() {
        return new HashMap<>(ownerStates);
    }

    public PetSwarmIndex getSwarmIndex() {
        return swarmIndex;
    }

    public AuraTargetResolver getAuraTargetResolver() {
        return auraTargetResolver;
    }

    public ServerWorld world() {
        return world;
    }

    @Nullable
    public ServerPlayerEntity findOnlineOwner(@Nullable UUID ownerId) {
        return resolveOnlineOwner(ownerId);
    }

    private boolean pickupNearbyPotionsForSupport(MobEntity pet, PlayerEntity owner, PetComponent component,
                                                  PetRoleType.SupportPotionBehavior behavior) {
        double pickupRadius = SupportPotionUtils.getAutoPickupRadius(component, behavior);
        List<ItemEntity> items = SupportPotionVacuumManager.getInstance()
            .collectPotionsNearby(pet, pickupRadius);
        return attemptSupportPotionPickup(pet, owner, component, behavior, items, pickupRadius) != null;
    }

    @Nullable
    private ItemEntity attemptSupportPotionPickup(MobEntity pet,
                                                  @Nullable PlayerEntity owner,
                                                  PetComponent component,
                                                  PetRoleType.SupportPotionBehavior behavior,
                                                  Iterable<ItemEntity> candidates,
                                                  double pickupRadius) {
        if (pet == null || component == null || behavior == null) {
            return null;
        }
        if (!pet.isAlive()) {
            return null;
        }
        if (component.getLevel() < behavior.minLevel()) {
            return null;
        }
        if (pickupRadius <= 0.0D) {
            return null;
        }

        double radiusSq = pickupRadius * pickupRadius;
        var currentState = SupportPotionUtils.getStoredState(component);

        for (ItemEntity picked : candidates) {
            if (picked == null || picked.isRemoved()) {
                continue;
            }
            if (pet.squaredDistanceTo(picked) > radiusSq) {
                continue;
            }

            var stack = picked.getStack();
            var incoming = SupportPotionUtils.createStateFromStack(stack, component);
            if (!incoming.isValid()) {
                continue;
            }

            var outcome = SupportPotionUtils.mergePotionStates(
                component,
                currentState,
                incoming,
                false
            );
            if (!outcome.accepted()) {
                if (owner instanceof ServerPlayerEntity serverOwner) {
                    SupportPotionUtils.RejectionReason reason = outcome.rejectionReason();
                    String cueId = "support.potion.reject." + reason.name().toLowerCase() + "." + pet.getUuidAsString();
                    net.minecraft.text.Text message = switch (reason) {
                        case INCOMPATIBLE -> net.minecraft.text.Text.translatable(
                            "petsplus.emotion_cue.support.potion_incompatible",
                            pet.getDisplayName()
                        );
                        case TOO_FULL, INVALID, NONE -> net.minecraft.text.Text.translatable(
                            "petsplus.emotion_cue.support.potion_full",
                            pet.getDisplayName()
                        );
                    };
                    EmotionContextCues.sendCue(serverOwner, cueId, message, 160);
                }
                continue;
            }

            SupportPotionUtils.writeStoredState(component, outcome.result());
            currentState = outcome.result();

            stack.decrement(1);
            SupportPotionVacuumManager.getInstance().handleStackChanged(picked);
            if (stack.isEmpty()) {
                SupportPotionVacuumManager.getInstance().remove(picked);
                picked.discard();
            }

            ServerWorld serverWorld = world;
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                pet.getX(), pet.getY() + pet.getHeight() * 0.5, pet.getZ(),
                7, 0.25, 0.25, 0.25, 0.02);

            if (owner instanceof ServerPlayerEntity serverOwner) {
                net.minecraft.text.Text message = net.minecraft.text.Text.translatable(
                    "petsplus.emotion_cue.support.potion_stored",
                    pet.getDisplayName()
                );
                String cueId = "support.potion.stored." + pet.getUuidAsString();
                if (outcome.toppedUp()) {
                    message = net.minecraft.text.Text.translatable(
                        "petsplus.emotion_cue.support.potion_topped_up",
                        pet.getDisplayName()
                    );
                    cueId = "support.potion.topped." + pet.getUuidAsString();
                }
                EmotionContextCues.sendCue(serverOwner, cueId, message, 160);
            }

            serverWorld.playSound(null, pet.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_BREWING_STAND_BREW,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                0.4f,
                1.6f);

            return picked;
        }

        return null;
    }

    private static final class SpatialJobState {
        private int attempts;
        private boolean pending;
        private long lastRequestedTick;

        void markRequested(long tick) {
            attempts++;
            pending = true;
            lastRequestedTick = tick;
        }

        void markCompleted() {
            pending = false;
        }

        void markFailed() {
            pending = false;
        }
    }

    private static final class AdaptiveTickScaler {
        private static final long TARGET_TICK_NANOS = 50_000_000L;
        private static final double SMOOTHING = 0.1D;
        private static final double MIN_FACTOR = 0.5D;
        private static final double MAX_FACTOR = 2.0D;

        private long lastTickNanos;
        private double emaTickNanos = TARGET_TICK_NANOS;
        private boolean initialized;

        void recordTick() {
            long now = System.nanoTime();
            if (!initialized) {
                initialized = true;
                lastTickNanos = now;
                return;
            }

            long delta = now - lastTickNanos;
            lastTickNanos = now;
            if (delta <= 0L) {
                return;
            }

            emaTickNanos = (SMOOTHING * delta) + ((1.0D - SMOOTHING) * emaTickNanos);
        }

        long scaleInterval(long baseTicks) {
            if (baseTicks <= 0L) {
                return 1L;
            }
            if (baseTicks >= Long.MAX_VALUE / 2L) {
                return baseTicks;
            }

            double scaled = baseTicks * currentFactor();
            long rounded = Math.round(scaled);
            if (rounded <= 0L) {
                return 1L;
            }
            if (rounded >= Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return rounded;
        }

        private double currentFactor() {
            double factor = emaTickNanos / TARGET_TICK_NANOS;
            if (factor < MIN_FACTOR) {
                return MIN_FACTOR;
            }
            if (factor > MAX_FACTOR) {
                return MAX_FACTOR;
            }
            return factor;
        }

        double loadFactor() {
            return currentFactor();
        }

        int ownerBatchBudget(int pendingOwners) {
            if (pendingOwners <= 0) {
                return 0;
            }

            double factor = currentFactor();
            double baseBudget = Math.min(Math.max(4.0D, pendingOwners), 32.0D);
            double scaled = baseBudget / factor;
            int budget = (int) Math.round(scaled);
            if (budget < 1) {
                budget = 1;
            }
            if (budget > pendingOwners) {
                budget = pendingOwners;
            }
            return budget;
        }
    }
}
