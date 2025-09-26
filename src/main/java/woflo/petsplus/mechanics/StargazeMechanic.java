package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the hidden stargaze mechanic for "I love you and me" advancement.
 * Triggers when player crouches near sitting pet for 30s at night within 2 minutes of tribute.
 */
public class StargazeMechanic {

    // Track players in stargaze window (2 minutes after tribute)
    private static final Map<UUID, Long> stargazeWindows = new ConcurrentHashMap<>();

    // Track active stargaze sessions
    private static final Map<UUID, StargazeSession> activeSessions = new ConcurrentHashMap<>();

    private static class StargazeSession {
        final UUID petUuid;
        final Vec3d startPosition;
        final long startTime;
        boolean completed = false;

        StargazeSession(UUID petUuid, Vec3d startPosition, long startTime) {
            this.petUuid = petUuid;
            this.startPosition = startPosition;
            this.startTime = startTime;
        }

        boolean isValid(ServerPlayerEntity player, MobEntity pet, long currentTime) {
            if (completed) return false;

            // Check time limit (30 seconds = 600 ticks)
            long duration = currentTime - startTime;
            if (duration > 600) return false;

            // Check position hasn't moved too much (3 blocks)
            double range = PetsPlusConfig.getInstance().getSectionDouble("bond", "stargazeRange", 3.0);
            if (player.getPos().distanceTo(startPosition) > range) return false;

            // Check pet is still sitting and nearby
            if (pet == null || !pet.isAlive() || pet.getPos().distanceTo(startPosition) > range) return false;

            if (pet instanceof PetsplusTameable tameable) {
                return tameable.petsplus$isSitting();
            }

            return false;
        }
    }

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(StargazeMechanic::onWorldTick);
    }

    /**
     * Start a stargaze window after tribute payment
     */
    public static void startStargazeWindow(ServerPlayerEntity player) {
        long windowDuration = PetsPlusConfig.getInstance().getSectionInt("bond", "stargazeWindowTicks", 2400); // 2 minutes
        stargazeWindows.put(player.getUuid(), player.getWorld().getTime() + windowDuration);

        player.sendMessage(Text.of("§5✦ §dThe night sky seems to shimmer with possibility... §5✦"), false);
    }

    /**
     * Main tick handler
     */
    private static void onWorldTick(ServerWorld world) {
        // Clean up expired windows
        cleanupExpiredWindows(world.getTime());

        // Process active stargaze sessions
        for (ServerPlayerEntity player : world.getPlayers()) {
            processPlayerStargaze(player, world.getTime());
        }
    }

    /**
     * Process stargaze logic for a player
     */
    private static void processPlayerStargaze(ServerPlayerEntity player, long currentTime) {
        UUID playerId = player.getUuid();

        // Check if player is in stargaze window
        Long windowEnd = stargazeWindows.get(playerId);
        if (windowEnd == null || currentTime > windowEnd) {
            // Not in window or window expired
            if (activeSessions.containsKey(playerId)) {
                activeSessions.remove(playerId);
                player.sendMessage(Text.of("§7The moment has passed..."), true);
            }
            return;
        }

        // Check if it's night time
        if (!isNightTime((ServerWorld) player.getWorld())) {
            if (activeSessions.containsKey(playerId)) {
                activeSessions.remove(playerId);
                player.sendMessage(Text.of("§7Wait for nightfall..."), true);
            }
            return;
        }

        // Check if player is crouching
        if (!player.isSneaking()) {
            if (activeSessions.containsKey(playerId)) {
                activeSessions.remove(playerId);
                player.sendMessage(Text.of("§7You must stay close to your companion..."), true);
            }
            return;
        }

        // Find nearby sitting pet
        MobEntity sittingPet = findNearbySittingPet(player);
        if (sittingPet == null) {
            if (activeSessions.containsKey(playerId)) {
                activeSessions.remove(playerId);
                player.sendMessage(Text.of("§7Your pet must be sitting nearby..."), true);
            }
            return;
        }

        // Handle stargaze session
        StargazeSession session = activeSessions.get(playerId);
        if (session == null) {
            // Start new session
            session = new StargazeSession(sittingPet.getUuid(), player.getPos(), currentTime);
            activeSessions.put(playerId, session);
            player.sendMessage(Text.of("§dYou begin to share a quiet moment under the stars..."), true);
            return;
        }

        // Check if session is still valid
        if (!session.isValid(player, sittingPet, currentTime)) {
            activeSessions.remove(playerId);
            player.sendMessage(Text.of("§7The moment was broken..."), true);
            return;
        }

        // Check if session completed (30 seconds)
        long sessionDuration = currentTime - session.startTime;
        long requiredDuration = PetsPlusConfig.getInstance().getSectionInt("bond", "stargazeHoldTicks", 600); // 30s

        if (sessionDuration >= requiredDuration && !session.completed) {
            // Complete stargaze!
            session.completed = true;
            completeStargaze(player, sittingPet);
        } else {
            // Show progress
            if (sessionDuration % 100 == 0) { // Every 5 seconds
                int secondsRemaining = (int) ((requiredDuration - sessionDuration) / 20);
                player.sendMessage(Text.of("§d✦ " + secondsRemaining + "s remaining... ✦"), true);
            }
        }
    }

    /**
     * Complete the stargaze advancement
     */
    private static void completeStargaze(ServerPlayerEntity player, MobEntity pet) {
        String petName = pet.hasCustomName() ?
            pet.getCustomName().getString() :
            pet.getType().getName().getString();

        // Trigger advancement
        AdvancementManager.triggerStargazeTimeout(player);

        // Remove from tracking
        activeSessions.remove(player.getUuid());
        stargazeWindows.remove(player.getUuid());

        // Success feedback
        player.sendMessage(Text.of("§5✦ §d" + petName + " gazes at you with pure love... You know they've got your back. §5✦"), false);

        // Particle effects (heart particles)
        Vec3d petPos = pet.getPos();
        Vec3d playerPos = player.getPos();
        Vec3d midPoint = petPos.add(playerPos).multiply(0.5);

        ((ServerWorld) player.getWorld()).spawnParticles(
            net.minecraft.particle.ParticleTypes.HEART,
            midPoint.x, midPoint.y + 1, midPoint.z,
            10, 0.5, 0.5, 0.5, 0.1
        );

        // Sound effect
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sound.SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM,
            net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1.2f);
    }

    /**
     * Find a sitting pet near the player
     */
    private static MobEntity findNearbySittingPet(ServerPlayerEntity player) {
        double range = PetsPlusConfig.getInstance().getSectionDouble("bond", "stargazeRange", 3.0);

        return ((ServerWorld) player.getWorld()).getEntitiesByClass(
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
    private static void cleanupExpiredWindows(long currentTime) {
        stargazeWindows.entrySet().removeIf(entry -> currentTime > entry.getValue());
    }

    /**
     * Check if a player is currently in a stargaze window
     */
    public static boolean isInStargazeWindow(ServerPlayerEntity player) {
        Long windowEnd = stargazeWindows.get(player.getUuid());
        return windowEnd != null && player.getWorld().getTime() <= windowEnd;
    }

    /**
     * Get remaining time in stargaze window (in ticks)
     */
    public static long getStargazeWindowRemaining(ServerPlayerEntity player) {
        Long windowEnd = stargazeWindows.get(player.getUuid());
        if (windowEnd == null) return 0;
        return Math.max(0, windowEnd - player.getWorld().getTime());
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
}