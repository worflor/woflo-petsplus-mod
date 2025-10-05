package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.roles.skyrider.SkyriderWinds;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.state.StateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Handles player movement and state tracking events.
 */
public final class PlayerStateTracker implements PlayerTickListener {

    private static final PlayerStateTracker INSTANCE = new PlayerStateTracker();
    private static final Map<ServerPlayerEntity, FallState> FALL_STATES = new WeakHashMap<>();

    private PlayerStateTracker() {
    }

    public static PlayerStateTracker getInstance() {
        return INSTANCE;
    }

    public static void register() {
        // Register for player tick events to track sprint changes
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerStateTracker::onPlayerRespawn);
        Petsplus.LOGGER.info("Player state tracker registered");
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }

        boolean shouldRequestImmediateRun = false;
        long nextTick;
        synchronized (FALL_STATES) {
            FallState state = FALL_STATES.computeIfAbsent(player, ignored -> new FallState());

            if (state.nextTick == Long.MAX_VALUE && isFalling(player)) {
                if (player.getServer() != null) {
                    state.nextTick = player.getServer().getTicks();
                    shouldRequestImmediateRun = true;
                }
            }
            nextTick = state.nextTick;
        }

        if (shouldRequestImmediateRun) {
            PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
        }

        return nextTick;
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.isRemoved()) {
            return;
        }

        FallState state;
        synchronized (FALL_STATES) {
            state = FALL_STATES.computeIfAbsent(player, ignored -> new FallState());
        }

        double currentFallDistance = player.fallDistance;
        boolean isFalling = isFalling(player);

        if (isFalling) {
            double threshold = SkyriderWinds.getWindlashMinFallBlocks();
            if (!state.triggered && currentFallDistance >= threshold && state.lastFallDistance < threshold) {
                state.triggered = true;
                trackFallStart(player, currentFallDistance);
            }
            state.lastFallDistance = currentFallDistance;
            state.nextTick = currentTick + 1L;
        } else {
            state.lastFallDistance = 0.0D;
            state.triggered = false;
            state.nextTick = Long.MAX_VALUE;
        }
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        synchronized (FALL_STATES) {
            FALL_STATES.remove(player);
        }
    }
    
    /**
     * Called when a player respawns.
     */
    private static void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        // Reset combat state on respawn
        OwnerCombatState.remove(oldPlayer);

        // Clear boss bars and UI state to prevent stale displays after death
        woflo.petsplus.ui.BossBarManager.removeBossBar(oldPlayer);
        woflo.petsplus.ui.PetInspectionManager.onPlayerDisconnect(oldPlayer);

        // Clear for new player as well to ensure clean slate
        if (newPlayer != oldPlayer) {
            woflo.petsplus.ui.BossBarManager.removeBossBar(newPlayer);
        }

        // Trigger any resurrection abilities if it was a death respawn
        Map<String, Object> payload = new HashMap<>();
        payload.put("death_respawn", !alive);
        payload.put("respawn_dimension", newPlayer.getWorld().getRegistryKey().getValue().toString());
        StateManager.forWorld(newPlayer.getWorld()).fireAbilityTrigger(newPlayer, "owner_respawn", payload);
    }
    
    /**
     * Track sprint state changes for owners.
     * This should be called from a movement tracking system.
     */
    public static void trackSprintChange(PlayerEntity player, boolean isNowSprinting, boolean wasSprinting) {
        if (isNowSprinting && !wasSprinting) {
            // Sprint started
            CombatEventHandler.triggerAbilitiesForOwner(player, "after_owner_sprint_start");
        }
    }
    
    /**
     * Track fall damage for Edge Step and other fall-related abilities.
     */
    public static float modifyFallDamage(PlayerEntity player, float fallDamage, double fallDistance) {
        return fallDamage;
    }
    
    /**
     * Track when players start falling for fall-related triggers.
     */
    public static void trackFallStart(PlayerEntity player, double fallDistance) {
        double threshold = SkyriderWinds.getWindlashMinFallBlocks();
        if (fallDistance >= threshold) {
            java.util.Map<String,Object> data = new java.util.HashMap<>();
            data.put("fall_distance", fallDistance);
            CombatEventHandler.triggerAbilitiesForOwner(player, "owner_begin_fall", data);

            if (player instanceof ServerPlayerEntity serverPlayer) {
                resumeMonitoring(serverPlayer);
                boolean mounted = serverPlayer.getVehicle() != null;
                Petsplus.LOGGER.debug(
                    "Owner {} began falling {} blocks ({})",
                    serverPlayer.getName().getString(),
                    String.format("%.2f", fallDistance),
                    mounted ? "mounted" : "on foot"
                );
            }
        }
    }

    private static final class FallState {
        double lastFallDistance;
        boolean triggered;
        long nextTick;

        private FallState() {
            this.nextTick = Long.MAX_VALUE;
        }
    }

    private static boolean isFalling(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        return !player.isOnGround() && player.getVelocity().y < 0 && player.fallDistance > 0.0F;
    }

    private static void resumeMonitoring(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        boolean shouldRequestImmediateRun = false;
        synchronized (FALL_STATES) {
            FallState state = FALL_STATES.computeIfAbsent(player, ignored -> new FallState());
            if (state.nextTick == Long.MAX_VALUE) {
                state.nextTick = player.getServer().getTicks();
                shouldRequestImmediateRun = true;
            }
        }

        if (shouldRequestImmediateRun) {
            PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
        }
    }
}