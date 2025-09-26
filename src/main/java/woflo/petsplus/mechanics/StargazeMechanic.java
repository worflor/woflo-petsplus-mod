package woflo.petsplus.mechanics;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
        long lastReminderTick = -1L;

        StargazeSession(UUID petUuid, Vec3d startPosition, long startTime) {
            this.petUuid = petUuid;
            this.startPosition = startPosition;
            this.startTime = startTime;
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
        stargazeWindows.put(player.getUuid(), player.getWorld().getTime() + windowDuration);

        player.sendMessage(Text.of("§5✦ §dThe night sky seems to shimmer with possibility... §5✦"), false);
    }

    /**
     * Invoked from mixins when a player toggles sneaking.
     */
    public static void handleSneakToggle(ServerPlayerEntity player, boolean sneaking) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }

        UUID playerId = player.getUuid();
        long now = player.getWorld().getTime();

        if (!sneaking) {
            StargazeSession session = activeSessions.remove(playerId);
            if (session != null && !session.completed) {
                player.sendMessage(Text.of("§7The moment was broken..."), true);
            }
            return;
        }

        if (!isWindowActive(player, now)) {
            return;
        }

        if (!isNightTime((ServerWorld) player.getWorld())) {
            player.sendMessage(Text.of("§7Wait for nightfall..."), true);
            return;
        }

        MobEntity sittingPet = findNearbySittingPet(player);
        if (sittingPet == null) {
            player.sendMessage(Text.of("§7Your pet must be sitting nearby..."), true);
            return;
        }

        StargazeSession session = activeSessions.get(playerId);
        if (session == null || !session.petUuid.equals(sittingPet.getUuid())) {
            session = new StargazeSession(sittingPet.getUuid(), player.getPos(), now);
            activeSessions.put(playerId, session);
            player.sendMessage(Text.of("§dYou begin to share a quiet moment under the stars..."), true);
        }
    }

    /**
     * Called when a tracked player sends a movement packet.
     */
    public static void handlePlayerMove(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }

        StargazeSession session = activeSessions.get(player.getUuid());
        if (session == null) {
            return;
        }

        tickSession(player, session, player.getWorld().getTime(), false);
    }

    /**
     * Called from a player tick mixin to advance any active stargaze session.
     */
    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }

        UUID playerId = player.getUuid();
        long now = player.getWorld().getTime();

        // Drop expired windows when players stay idle beyond the grace period.
        isWindowActive(player, now);

        StargazeSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }

        tickSession(player, session, now, true);
    }

    private static void tickSession(ServerPlayerEntity player, StargazeSession session, long now, boolean allowReminders) {
        if (!isWindowActive(player, now)) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7The moment has passed..."), true);
            return;
        }

        if (!player.isSneaking()) {
            activeSessions.remove(player.getUuid());
            if (!session.completed) {
                player.sendMessage(Text.of("§7You must stay close to your companion..."), true);
            }
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        if (!isNightTime(world)) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7Wait for nightfall..."), true);
            return;
        }

        MobEntity pet = getSessionPet(world, session.petUuid);
        if (pet == null) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7Your pet must be sitting nearby..."), true);
            return;
        }

        if (!pet.isAlive()) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7The moment was broken..."), true);
            return;
        }

        double range = PetsPlusConfig.getInstance().getSectionDouble("bond", "stargazeRange", 3.0);
        if (player.getPos().distanceTo(session.startPosition) > range
            || pet.getPos().distanceTo(session.startPosition) > range) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7You must stay close to your companion..."), true);
            return;
        }

        if (pet instanceof PetsplusTameable tameable && !tameable.petsplus$isSitting()) {
            activeSessions.remove(player.getUuid());
            player.sendMessage(Text.of("§7Your pet must be sitting nearby..."), true);
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
            if (session.lastReminderTick < 0 || now - session.lastReminderTick >= 100) {
                session.lastReminderTick = now;
                int secondsRemaining = (int) Math.max(0, (requiredDuration - duration) / 20);
                if (secondsRemaining > 0) {
                    player.sendMessage(Text.of("§d✦ " + secondsRemaining + "s remaining... ✦"), true);
                }
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
        stargazeWindows.remove(player.getUuid());
        activeSessions.remove(player.getUuid());
    }

    /**
     * Check if a player is currently in a stargaze window
     */
    public static boolean isInStargazeWindow(ServerPlayerEntity player) {
        return isWindowActive(player, player.getWorld().getTime());
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

    public static void handlePetSittingChange(MobEntity pet, boolean sitting) {
        if (pet == null || sitting) {
            return;
        }

        if (!(pet.getWorld() instanceof ServerWorld world)) {
            return;
        }

        UUID petId = pet.getUuid();
        activeSessions.entrySet().removeIf(entry -> {
            if (!entry.getValue().petUuid.equals(petId)) {
                return false;
            }

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Text.of("§7Your pet must be sitting nearby..."), true);
            }
            return true;
        });
    }
}