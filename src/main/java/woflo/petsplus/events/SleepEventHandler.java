package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureFlavorHandler.Trigger;
import woflo.petsplus.stats.nature.NatureTabooHandler;
import woflo.petsplus.stats.nature.NatureTabooHandler.TabooTrigger;
import woflo.petsplus.state.StateManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles sleep events for Eepy Eeper mechanics.
 * Tracks when players sleep and triggers appropriate pet behaviors.
 */
public class SleepEventHandler {

    private static final Map<UUID, Boolean> playerSleepStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sleepStartTime = new ConcurrentHashMap<>();

    public static void initialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register(SleepEventHandler::onPlayerRespawn);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                onPlayerDisconnect(player);
            }
        });
    }

    /**
     * Handle player starting to sleep
     */
    public static void onSleepStart(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        if (Boolean.TRUE.equals(playerSleepStatus.put(playerId, true))) {
            return;
        }

        sleepStartTime.put(playerId, player.getEntityWorld().getTime());
    }

    /**
     * Handle player finishing sleep (successful sleep)
     */
    public static void onSleepEnd(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        playerSleepStatus.put(playerId, false);

        Long startTime = sleepStartTime.remove(playerId);
        if (startTime == null) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        long sleepDuration = world.getTime() - startTime;

        // A full sleep cycle is typically 100 ticks (5 seconds) minimum
        if (sleepDuration >= 100) {
            onSuccessfulSleep(player, world, sleepDuration, false);
        }
    }

    /**
     * Handle successful sleep completion
     */
    private static void onSuccessfulSleep(ServerPlayerEntity player,
                                          ServerWorld world,
                                          long sleepDuration,
                                          boolean viaAnchor) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sleep_duration_ticks", sleepDuration);
        payload.put("sleep_world_time", world.getTime());
        payload.put("via_anchor", viaAnchor);
        payload.put("sleep_event_id", UUID.randomUUID());
        StateManager.forWorld(world).fireAbilityTrigger(player, "owner_sleep_complete", payload);

        if (!viaAnchor) {
            List<HostileEntity> nearbyHostiles = world.getEntitiesByClass(HostileEntity.class,
                player.getBoundingBox().expand(24.0),
                entity -> entity.isAlive() && !entity.isRemoved());
            if (!nearbyHostiles.isEmpty()) {
                float threatScore = 0f;
                float closestDistance = Float.MAX_VALUE;
                for (HostileEntity hostile : nearbyHostiles) {
                    double distSq = hostile.squaredDistanceTo(player);
                    double dist = Math.sqrt(Math.max(distSq, 0.0));
                    closestDistance = (float) Math.min(closestDistance, dist);

                    float distanceWeight = (float) MathHelper.clamp((24.0 - dist) / 12.0, 0.0, 1.6);
                    float attackWeight = (float) MathHelper.clamp(
                        hostile.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) / 6.0,
                        0.15f,
                        1.4f);
                    float healthWeight = (float) MathHelper.clamp(hostile.getMaxHealth() / 30.0f, 0.1f, 1.3f);
                    float speedWeight = (float) MathHelper.clamp(
                        hostile.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) / 0.25f,
                        0.2f,
                        1.2f);

                    float pressure = 0.35f
                        + distanceWeight * 0.8f
                        + attackWeight * 0.5f
                        + healthWeight * 0.35f
                        + speedWeight * 0.25f;
                    threatScore += pressure;
                }

                float proximityBonus = closestDistance < Float.MAX_VALUE
                    ? MathHelper.clamp((8.0f - closestDistance) / 4.0f, 0.0f, 1.5f)
                    : 0.0f;
                float clusterBonus = MathHelper.clamp((nearbyHostiles.size() - 1) / 2.0f, 0.0f, 1.8f);

                float hostilesIntensity = 0.85f
                    + MathHelper.clamp(threatScore * 0.75f + proximityBonus + clusterBonus * 0.4f,
                    0.0f,
                    4.5f);
                NatureTabooHandler.triggerForOwner(player, 48, TabooTrigger.SLEEPING_DANGER, hostilesIntensity);
            }
        }

        // Push calming emotions to nearby owned pets
        world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
            player.getBoundingBox().expand(32),
            mob -> {
                woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(player);
            }
        ).forEach(pet -> {
            woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(pet);
            if (pc != null) {
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.20f);
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.QUERECIA, 0.12f);
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.20f);
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.RELIEF, 0.20f);
                pc.updateMood();
                if (pc.hasRole(PetRoleType.EEPY_EEPER)) {
                    EmotionContextCues.sendCue(player,
                        "role.eepy.dream." + pet.getUuidAsString(),
                        pet,
                        Text.translatable("petsplus.emotion_cue.role.eepy_dream", pet.getDisplayName()),
                        2400);
                }
            }
        });

        EmotionContextCues.sendCue(player,
            "sleep.rested",
            Text.translatable("petsplus.emotion_cue.sleep.rested"),
            2400);

        NatureTabooHandler.triggerForOwner(player, 64, TabooTrigger.OWNER_ABSENCE, 1.0f);
        NatureFlavorHandler.triggerForOwner(player, 32, Trigger.OWNER_SLEEP);
    }

    /**
     * Handle respawn anchor usage (should also trigger sleep benefits)
     */
    private static void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (alive) {
            // Check if respawn was from a respawn anchor in the Nether
            if (newPlayer.getEntityWorld().getRegistryKey().getValue().getPath().equals("the_nether")) {
                // Trigger sleep-like benefits for respawn anchor usage
                onSuccessfulSleep(newPlayer, newPlayer.getEntityWorld(), 100L, true);
                EmotionContextCues.sendCue(newPlayer,
                    "sleep.anchor",
                    Text.translatable("petsplus.emotion_cue.sleep.anchor"),
                    2400);
            }

            NatureFlavorHandler.triggerForOwner(newPlayer, 32, Trigger.OWNER_RESPAWN);
        }
    }

    /**
     * Manual trigger for sleep events (for testing or special cases)
     */
    public static void triggerSleepEvent(ServerPlayerEntity player) {
        onSuccessfulSleep(player, player.getEntityWorld(), 100L, false);
    }

    /**
     * Remove all cached state for a disconnecting player.
     */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        playerSleepStatus.remove(playerId);
        sleepStartTime.remove(playerId);
    }

    /**
     * Check if a player is currently sleeping
     */
    public static boolean isPlayerSleeping(PlayerEntity player) {
        return playerSleepStatus.getOrDefault(player.getUuid(), false);
    }

    /**
     * Get how long a player has been sleeping (in ticks)
     */
    public static long getSleepDuration(PlayerEntity player) {
        Long startTime = sleepStartTime.get(player.getUuid());
        if (startTime == null || !isPlayerSleeping(player)) {
            return 0;
        }
        return player.getEntityWorld().getTime() - startTime;
    }

    /**
     * Force trigger sleep completion for a player (admin/debug use)
     */
    public static void forceSleepCompletion(ServerPlayerEntity player) {
        onSuccessfulSleep(player, player.getEntityWorld(), 100L, false);
    }

}
