package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.state.StateManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the hidden stargaze mechanic for "I love you and me" advancement.
 * Triggers when player crouches near sitting pet for 30s at night within 2 minutes of tribute.
 */
public final class StargazeMechanic implements PlayerTickListener {

    private static final StargazeMechanic INSTANCE = new StargazeMechanic();

    // Track players in stargaze window (2 minutes after tribute)
    private static final Map<UUID, Long> stargazeWindows = new ConcurrentHashMap<>();

    // Track active stargaze sessions
    private static final Map<UUID, StargazeSession> activeSessions = new ConcurrentHashMap<>();

    // Track scheduled wake ticks for active players
    private static final Map<UUID, Long> NEXT_WAKE_TICKS = new ConcurrentHashMap<>();

    private StargazeMechanic() {
    }

    public static StargazeMechanic getInstance() {
        return INSTANCE;
    }

    private static class StargazeSession {
        final UUID petUuid;
        final long startTime;
        boolean completed = false;

        StargazeSession(UUID petUuid, long startTime) {
            this.petUuid = petUuid;
            this.startTime = startTime;
        }
    }

    private static long resolveServerTick(ServerPlayerEntity player) {
        if (player == null) {
            return 0L;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            return server.getTicks();
        }

        return player.getEntityWorld().getTime();
    }

    private static void scheduleTick(ServerPlayerEntity player, long desiredTick) {
        if (player == null) {
            return;
        }

        long normalizedTick = Math.max(0L, desiredTick);
        UUID playerId = player.getUuid();
        NEXT_WAKE_TICKS.merge(playerId, normalizedTick, Math::min);

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null && normalizedTick <= server.getTicks()) {
            PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
        }
    }

    private static void cancelScheduledRuns(ServerPlayerEntity player) {
        if (player != null) {
            NEXT_WAKE_TICKS.remove(player.getUuid());
        }
    }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                handlePlayerDisconnect(player);
            }
        });
    }

    /**
     * Start a stargaze window after tribute payment
     */
    public static void startStargazeWindow(ServerPlayerEntity player) {
        long windowDuration = PetsPlusConfig.getInstance().getSectionInt("bond", "stargazeWindowTicks", 2400); // 2 minutes
        stargazeWindows.put(player.getUuid(), player.getEntityWorld().getTime() + windowDuration);

        player.sendMessage(Text.translatable("petsplus.stargaze.shimmer"), false);
    }

    /**
     * Invoked from mixins when a player toggles sneaking.
     */
    public static void handleSneakToggle(ServerPlayerEntity player, boolean sneaking) {
        if (player == null || player.getEntityWorld().isClient()) {
            return;
        }

        UUID playerId = player.getUuid();
        long now = player.getEntityWorld().getTime();

        if (!sneaking) {
            StargazeSession session = activeSessions.remove(playerId);
            if (session != null && !session.completed) {
                player.sendMessage(Text.translatable("petsplus.stargaze.interrupted"), false);
            }
            cancelScheduledRuns(player);
            return;
        }

        if (isCrouchChannelActive(player, now)) {
            return;
        }

        if (!isWindowActive(player, now)) {
            return;
        }

        if (!isNightTime((ServerWorld) player.getEntityWorld())) {
            player.sendMessage(Text.translatable("petsplus.stargaze.wait_for_night"), false);
            return;
        }

        MobEntity sittingPet = findNearbySittingPet(player);
        if (sittingPet == null) {
            player.sendMessage(Text.translatable("petsplus.stargaze.pet_not_sitting"), false);
            return;
        }

        StargazeSession session = activeSessions.get(playerId);
        if (session == null || !session.petUuid.equals(sittingPet.getUuid())) {
            session = new StargazeSession(sittingPet.getUuid(), now);
            activeSessions.put(playerId, session);
            player.sendMessage(Text.translatable("petsplus.stargaze.session_start"), false);
        }

        scheduleTick(player, resolveServerTick(player));
    }

    /**
     * Called when a tracked player sends a movement packet.
     */
    public static void handlePlayerMove(ServerPlayerEntity player) {
        if (player == null || player.getEntityWorld().isClient()) {
            return;
        }

        if (!activeSessions.containsKey(player.getUuid())) {
            return;
        }

        scheduleTick(player, resolveServerTick(player));
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }
        return NEXT_WAKE_TICKS.getOrDefault(player.getUuid(), Long.MAX_VALUE);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.getEntityWorld().isClient()) {
            return;
        }

        UUID playerId = player.getUuid();
        NEXT_WAKE_TICKS.remove(playerId);

        long now = player.getEntityWorld().getTime();
        // Drop expired windows when players stay idle beyond the grace period.
        isWindowActive(player, now);

        StargazeSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }

        tickSession(player, session, now, true);

        if (activeSessions.get(playerId) == session && !session.completed) {
            scheduleTick(player, currentTick + 1L);
        } else {
            cancelScheduledRuns(player);
        }
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        handlePlayerDisconnect(player);
    }

    private static void tickSession(ServerPlayerEntity player, StargazeSession session, long now, boolean allowReminders) {
        if (!isWindowActive(player, now)) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.window_expired"), false);
            cancelScheduledRuns(player);
            return;
        }

        if (!player.isSneaking()) {
            activeSessions.remove(player.getUuid());
            if (!session.completed) {
                player.sendMessage(Text.translatable("petsplus.stargaze.too_far"), false);
            }
            cancelScheduledRuns(player);
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        if (!isNightTime(world)) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.wait_for_night"), false);
            cancelScheduledRuns(player);
            return;
        }

        MobEntity pet = getSessionPet(world, session.petUuid);
        if (pet == null) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.pet_not_sitting"), false);
            cancelScheduledRuns(player);
            return;
        }

        if (!pet.isAlive()) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.interrupted"), false);
            cancelScheduledRuns(player);
            return;
        }

        double range = PetsPlusConfig.getInstance().getSectionDouble("bond", "stargazeRange", 3.0);
        double maxDistance = range + 0.5;
        if (player.getEntityPos().distanceTo(pet.getEntityPos()) > maxDistance) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.too_far"), false);
            cancelScheduledRuns(player);
            return;
        }

        if (pet instanceof PetsplusTameable tameable && !tameable.petsplus$isSitting()) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.translatable("petsplus.stargaze.pet_not_sitting"), false);
            cancelScheduledRuns(player);
            return;
        }

        long requiredDuration = PetsPlusConfig.getInstance().getSectionInt("bond", "stargazeHoldTicks", 600);
        long duration = now - session.startTime;

        if (duration >= requiredDuration && !session.completed) {
            session.completed = true;
            completeStargaze(player, pet);
            return;
        }

        if (allowReminders && duration >= 0) {
            emitAmbientParticles(world, player, pet);
        }
    }

    private static void emitAmbientParticles(ServerWorld world, ServerPlayerEntity player, MobEntity pet) {
        Vec3d playerPos = player.getEntityPos();
        Vec3d petPos = pet.getEntityPos();

        world.spawnParticles(
            net.minecraft.particle.ParticleTypes.ENCHANT,
            playerPos.x,
            playerPos.y + 1.2,
            playerPos.z,
            2,
            0.15,
            0.1,
            0.15,
            0.0
        );

        world.spawnParticles(
            net.minecraft.particle.ParticleTypes.ENCHANT,
            petPos.x,
            petPos.y + 0.8,
            petPos.z,
            2,
            0.15,
            0.1,
            0.15,
            0.0
        );
    }

    /**
     * Complete the stargaze advancement
     */
    private static void completeStargaze(ServerPlayerEntity player, MobEntity pet) {
        String petName = pet.hasCustomName() ?
            pet.getCustomName().getString() :
            pet.getType().getName().getString();

        // Trigger advancement
        woflo.petsplus.advancement.AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
            player,
            woflo.petsplus.advancement.criteria.PetInteractionCriterion.INTERACTION_STARGAZING,
            1
        );

        // Remove from tracking
        activeSessions.remove(player.getUuid());
        stargazeWindows.remove(player.getUuid());
        cancelScheduledRuns(player);

        // Success feedback
        player.sendMessage(Text.of("§5[Bond] §d" + petName + " gazes at you with pure love... You know they've got your back."), false);

        // Particle effects (heart particles)
        Vec3d petPos = pet.getEntityPos();
        Vec3d playerPos = player.getEntityPos();
        Vec3d midPoint = petPos.add(playerPos).multiply(0.5);

        ((ServerWorld) player.getEntityWorld()).spawnParticles(
            net.minecraft.particle.ParticleTypes.HEART,
            midPoint.x, midPoint.y + 1, midPoint.z,
            10, 0.5, 0.5, 0.5, 0.1
        );

        // Sound effect
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sound.SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM,
            net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1.2f);
    }

    /**
     * Find a sitting pet near the player
     */
    private static MobEntity findNearbySittingPet(ServerPlayerEntity player) {
        double range = PetsPlusConfig.getInstance().getSectionDouble("bond", "stargazeRange", 3.0);

        return ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(range),
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                if (petComp == null || !petComp.isOwnedBy(player) || !entity.isAlive()) {
                    return false;
                }

                if (entity instanceof PetsplusTameable tameable) {
                    return tameable.petsplus$isSitting();
                }

                return false;
            }
        ).stream().findFirst().orElse(null);
    }

    private static MobEntity getSessionPet(ServerWorld world, UUID petUuid) {
        net.minecraft.entity.Entity entity = world.getEntity(petUuid);
        if (entity instanceof MobEntity mob) {
            return mob;
        }
        return null;
    }

    private static boolean isWindowActive(ServerPlayerEntity player, long now) {
        Long windowEnd = stargazeWindows.get(player.getUuid());
        if (windowEnd == null) {
            return false;
        }
        if (now > windowEnd) {
            stargazeWindows.remove(player.getUuid());
            return false;
        }
        return true;
    }

    /**
     * Check if it's nighttime in the world
     */
    private static boolean isNightTime(ServerWorld world) {
        long timeOfDay = world.getTimeOfDay() % 24000;
        return timeOfDay >= 13000 && timeOfDay <= 23000; // Night is 13000-23000
    }

    /**
     * Clean up expired stargaze windows
     */
    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        stargazeWindows.remove(player.getUuid());
        activeSessions.remove(player.getUuid());
        cancelScheduledRuns(player);
    }

    /**
     * Check if a player is currently in a stargaze window
     */
    public static boolean isInStargazeWindow(ServerPlayerEntity player) {
        return isWindowActive(player, player.getEntityWorld().getTime());
    }

    /**
     * Get remaining time in stargaze window (in ticks)
     */
    public static long getStargazeWindowRemaining(ServerPlayerEntity player) {
        Long windowEnd = stargazeWindows.get(player.getUuid());
        if (windowEnd == null) return 0;
        return Math.max(0, windowEnd - player.getEntityWorld().getTime());
    }

    /**
     * Force trigger stargaze completion (for testing)
     */
    public static void forceTriggerStargaze(ServerPlayerEntity player) {
        MobEntity pet = findNearbySittingPet(player);
        if (pet != null) {
            completeStargaze(player, pet);
        }
    }

    private static boolean isCrouchChannelActive(ServerPlayerEntity player, long now) {
        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        StateManager stateManager = StateManager.forWorld(serverWorld);
        if (stateManager == null) {
            return false;
        }

        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(player.getUuid());
        if (swarm.isEmpty()) {
            return false;
        }

        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            PetComponent component = entry.component();
            if (component != null && component.isCrouchCuddleActiveWith(player, now)) {
                return true;
            }
        }

        return false;
    }

    public static void handlePetSittingChange(MobEntity pet, boolean sitting) {
        if (pet == null || sitting) {
            return;
        }

        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        UUID petId = pet.getUuid();
        java.util.Iterator<Map.Entry<UUID, StargazeSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, StargazeSession> entry = iterator.next();
            if (!entry.getValue().petUuid.equals(petId)) {
                continue;
            }

            iterator.remove();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Text.translatable("petsplus.stargaze.pet_not_sitting"), false);
                cancelScheduledRuns(player);
            }
        }
    }
}





