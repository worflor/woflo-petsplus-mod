package woflo.petsplus.roles.scout;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Scout role mechanics: detection pings and loot attraction.
 *
 * Core Features:
 * - Baseline: +Move Speed scalar, occasional Glowing ping on nearby hostiles
 * - L3 Loot Wisp: Nearby drops and XP drift toward owner after combat
 * - Detection abilities and positioning advantages
 *
 * Design Philosophy:
 * - Information advantage and mobility archetype
 * - Reveals threats and improves resource collection
 * - Enhances exploration and situational awareness
 */
public class ScoutCore implements PlayerTickListener {

    public static void initialize() {
        // Register combat events for loot wisp and detection
        ServerLivingEntityEvents.AFTER_DEATH.register(ScoutCore::onEntityDeath);
    }

    /**
     * Handle entity death for Loot Wisp mechanics.
     */
    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        // Only process if death was caused by a player
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        // Maintain detection bookkeeping for loot-focused perks
        hasNearbyScoutWithLootWisp(player);
    }

    /**
     * World tick handler for detection pings and passive effects.
     */
    private static final Map<UUID, Long> nextDetectionTick = new ConcurrentHashMap<>();
    private static final long DETECTION_INTERVAL_TICKS = 60L;
    private static final long IDLE_RECHECK_TICKS = 120L;

    private static final ScoutCore INSTANCE = new ScoutCore();

    private ScoutCore() {}

    public static ScoutCore getInstance() {
        return INSTANCE;
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }
        return nextDetectionTick.getOrDefault(player.getUuid(), 0L);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        nextDetectionTick.put(playerId, currentTick + IDLE_RECHECK_TICKS);

        if (!(player.getWorld() instanceof ServerWorld)) {
            return;
        }

        if (hasActiveScoutDetection(player)) {
            nextDetectionTick.put(playerId, currentTick + DETECTION_INTERVAL_TICKS);
        }
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player != null) {
            nextDetectionTick.remove(player.getUuid());
        }
    }

    /**
     * Check if player has a nearby Scout pet with Loot Wisp ability (L3+).
     */
    private static boolean hasNearbyScoutWithLootWisp(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return false;
        }
        return ScoutBehaviors.collectScoutEntries(manager, player, 16.0D * 16.0D)
            .stream()
            .map(PetSwarmIndex.SwarmEntry::component)
            .anyMatch(component -> component != null && component.getLevel() >= 3);
    }

    /**
     * Check if a player has active Scout detection coverage.
     */
    public static boolean hasActiveScoutDetection(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return false;
        }
        return !ScoutBehaviors.collectScoutEntries(manager, player, 16.0D * 16.0D).isEmpty();
    }

    /**
     * Get the movement speed bonus from nearby Scout pets.
     */
    public static float getScoutSpeedBonus(ServerPlayerEntity player) {
        if (!ScoutBehaviors.hasNearbyScout(player)) {
            return 0.0f;
        }

        // Scout pets provide inherent speed bonus through their presence
        return 0.1f; // 10% speed bonus when Scout is nearby
    }
}
