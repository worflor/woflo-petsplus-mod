package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import woflo.petsplus.config.PetsPlusConfig;

/**
 * Central manager for action-bar feedback cues. Provides lightweight, event-driven
 * gating so only the most relevant cue for the pet a player recently inspected is shown.
 */
public final class ActionBarCueManager {

    private static final int DEFAULT_TTL_TICKS = 40; // 2 seconds
    private static final int DEFAULT_REPEAT_COOLDOWN_TICKS = 200; // 10 seconds
    private static final double DEFAULT_DISTANCE_RADIUS_SQ = 12 * 12;
    private static final int FOCUS_MEMORY_TICKS = 20 * 180; // 3 minutes

    private static final Map<UUID, PlayerCueState> PLAYER_STATES = new HashMap<>();

    private ActionBarCueManager() {}

    private static int resolveRecentPetLimit() {
        return Math.max(1, PetsPlusConfig.getInstance().getActionBarRecentPetLimit());
    }

    /**
     * Queue a cue for the player using the standard gating rules.
     */
    public static void queueCue(ServerPlayerEntity player, ActionBarCue cue) {
        if (player == null || cue == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        long currentTick = server.getTicks();
        PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
        int recentPetLimit = resolveRecentPetLimit();
        state.pruneFocuses(currentTick, recentPetLimit);

        ActionBarCueSource source = cue.source();
        if (source == null) {
            source = state.deriveImplicitSource(currentTick, recentPetLimit);
            if (source == null) {
                // No current or recent focus; drop the cue quietly to avoid spamming.
                return;
            }
        }

        QueuedCue queued = new QueuedCue(
            cue.messageKey(),
            cue.args().clone(),
            source,
            cue.priority(),
            currentTick,
            cue.ttlTicks(),
            cue.repeatCooldownTicks(),
            cue.customDeduplicationKey()
        );

        state.cues.add(queued);
    }

    /**
     * Convenience helper to queue a basic cue with default metadata.
     */
    public static void queueCue(ServerPlayerEntity player, String messageKey, Object... args) {
        queueCue(player, ActionBarCue.of(messageKey, args));
    }

    /**
     * Notify the manager that the player is actively looking at a pet.
     */
    public static void onPlayerLookedAtPet(ServerPlayerEntity player, MobEntity pet) {
        if (player == null || pet == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        long currentTick = server.getTicks();
        PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
        int recentPetLimit = resolveRecentPetLimit();
        state.recordFocus(pet, currentTick, recentPetLimit);
    }

    /**
     * Notify the manager that the player has stopped looking at their current pet focus.
     */
    public static void onPlayerLookedAway(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PlayerCueState state = PLAYER_STATES.get(player.getUuid());
        if (state == null) {
            return;
        }

        long currentTick = server.getTicks();
        int recentPetLimit = resolveRecentPetLimit();
        state.noteLookAway(currentTick, recentPetLimit);
    }

    /**
     * Server tick hook to evaluate cue eligibility and send action bar messages.
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long currentTick = server.getTicks();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        int recentPetLimit = resolveRecentPetLimit();

        for (ServerPlayerEntity player : players) {
            PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
            state.removeExpired(currentTick);
            state.pruneFocuses(currentTick, recentPetLimit);

            QueuedCue selected = null;
            for (QueuedCue cue : state.cues) {
                if (!isEligible(player, state, cue, currentTick, recentPetLimit)) {
                    continue;
                }

                if (selected == null || cue.priority().compareTo(selected.priority()) > 0) {
                    selected = cue;
                    continue;
                }

                if (cue.priority() == selected.priority() && cue.createdTick() < selected.createdTick()) {
                    selected = cue;
                }
            }

            if (selected != null) {
                sendCue(player, state, selected, currentTick);
            }

            state.cleanupCooldowns(currentTick);
        }

        pruneOfflinePlayers(players);
    }

    /**
     * Clear all cached state (used during shutdown).
     */
    public static void shutdown() {
        PLAYER_STATES.clear();
    }

    private static void sendCue(ServerPlayerEntity player, PlayerCueState state, QueuedCue cue, long currentTick) {
        state.cues.remove(cue);

        String dedupeKey = cue.deduplicationKey();
        if (dedupeKey != null) {
            state.cooldowns.put(dedupeKey, currentTick + cue.repeatCooldownTicks());
        }

        Text message = Text.translatable(cue.messageKey(), cue.args()).formatted(Formatting.GRAY);
        player.sendMessage(message, true);
    }

    private static boolean isEligible(ServerPlayerEntity player, PlayerCueState state, QueuedCue cue, long currentTick, int recentPetLimit) {
        if (cue.isExpired(currentTick)) {
            return false;
        }

        String dedupeKey = cue.deduplicationKey();
        if (dedupeKey != null) {
            long nextEligibleTick = state.cooldowns.getOrDefault(dedupeKey, Long.MIN_VALUE);
            if (currentTick < nextEligibleTick) {
                return false;
            }
        }

        ActionBarCueSource source = cue.source();
        if (source == null) {
            return false;
        }

        if (source.petId() != null && !state.hasInterestIn(source.petId(), currentTick, recentPetLimit)) {
            return false;
        }

        Vec3d position = source.position();
        if (position != null) {
            double distanceSq = player.getPos().squaredDistanceTo(position);
            if (distanceSq > source.maxDistanceSq()) {
                return false;
            }
        }

        return true;
    }

    private static void pruneOfflinePlayers(List<ServerPlayerEntity> activePlayers) {
        if (PLAYER_STATES.isEmpty()) {
            return;
        }

        if (activePlayers.isEmpty()) {
            PLAYER_STATES.clear();
            return;
        }

        java.util.Set<UUID> activeIds = new java.util.HashSet<>();
        for (ServerPlayerEntity player : activePlayers) {
            activeIds.add(player.getUuid());
        }

        Iterator<Map.Entry<UUID, PlayerCueState>> iterator = PLAYER_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerCueState> entry = iterator.next();
            if (!activeIds.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private static final class PlayerCueState {
        private final List<QueuedCue> cues = new ArrayList<>();
        private final Map<String, Long> cooldowns = new HashMap<>();
        private final List<RecentPetFocus> recentPets = new ArrayList<>();

        void removeExpired(long currentTick) {
            cues.removeIf(cue -> cue.isExpired(currentTick));
        }

        void cleanupCooldowns(long currentTick) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
        }

        void recordFocus(MobEntity pet, long currentTick, int recentPetLimit) {
            UUID petId = pet.getUuid();
            Vec3d position = pet.getPos();
            recentPets.removeIf(entry -> Objects.equals(entry.petId(), petId));
            recentPets.add(0, new RecentPetFocus(petId, position, currentTick));
            pruneFocuses(currentTick, recentPetLimit);
        }

        void noteLookAway(long currentTick, int recentPetLimit) {
            if (recentPets.isEmpty()) {
                return;
            }

            RecentPetFocus head = recentPets.get(0);
            recentPets.set(0, head.withLastSeenTick(currentTick));
            pruneFocuses(currentTick, recentPetLimit);
        }

        void pruneFocuses(long currentTick, int recentPetLimit) {
            recentPets.removeIf(entry -> currentTick - entry.lastSeenTick() > FOCUS_MEMORY_TICKS);
            int maxEntries = Math.max(1, recentPetLimit);
            if (recentPets.size() > maxEntries) {
                recentPets.subList(maxEntries, recentPets.size()).clear();
            }
        }

        boolean hasInterestIn(UUID petId, long currentTick, int recentPetLimit) {
            int maxEntries = Math.max(1, recentPetLimit);
            int checked = 0;
            for (RecentPetFocus entry : recentPets) {
                if (checked++ >= maxEntries) {
                    break;
                }
                if (!Objects.equals(entry.petId(), petId)) {
                    continue;
                }
                if (currentTick - entry.lastSeenTick() <= FOCUS_MEMORY_TICKS) {
                    return true;
                }
                break;
            }
            return false;
        }

        ActionBarCueSource deriveImplicitSource(long currentTick, int recentPetLimit) {
            pruneFocuses(currentTick, recentPetLimit);
            if (recentPets.isEmpty()) {
                return null;
            }
            RecentPetFocus head = recentPets.get(0);
            return new ActionBarCueSource(head.petId(), head.position(), DEFAULT_DISTANCE_RADIUS_SQ);
        }
    }

    private record RecentPetFocus(UUID petId, Vec3d position, long lastSeenTick) {
        RecentPetFocus withLastSeenTick(long tick) {
            return new RecentPetFocus(petId, position, tick);
        }
    }

    private record QueuedCue(
        String messageKey,
        Object[] args,
        ActionBarCueSource source,
        ActionBarCuePriority priority,
        long createdTick,
        int ttlTicks,
        int repeatCooldownTicks,
        String customDeduplicationKey
    ) {
        boolean isExpired(long currentTick) {
            return currentTick - createdTick >= ttlTicks;
        }

        String deduplicationKey() {
            if (customDeduplicationKey != null) {
                return customDeduplicationKey;
            }
            if (source.petId() == null) {
                return null;
            }
            return source.petId() + "|" + messageKey;
        }
    }

    /**
     * Builder-style descriptor for a cue.
     */
    public static final class ActionBarCue {
        private final String messageKey;
        private final Object[] args;
        private ActionBarCuePriority priority = ActionBarCuePriority.NORMAL;
        private int ttlTicks = DEFAULT_TTL_TICKS;
        private int repeatCooldownTicks = DEFAULT_REPEAT_COOLDOWN_TICKS;
        private ActionBarCueSource source;
        private String customDeduplicationKey;

        private ActionBarCue(String messageKey, Object... args) {
            this.messageKey = messageKey;
            this.args = args == null ? new Object[0] : args;
        }

        public static ActionBarCue of(String messageKey, Object... args) {
            return new ActionBarCue(messageKey, args);
        }

        public ActionBarCue withPriority(ActionBarCuePriority priority) {
            if (priority != null) {
                this.priority = priority;
            }
            return this;
        }

        public ActionBarCue withTtlTicks(int ttlTicks) {
            if (ttlTicks > 0) {
                this.ttlTicks = ttlTicks;
            }
            return this;
        }

        public ActionBarCue withRepeatCooldownTicks(int ticks) {
            if (ticks >= 0) {
                this.repeatCooldownTicks = ticks;
            }
            return this;
        }

        public ActionBarCue withSource(ActionBarCueSource source) {
            this.source = source;
            return this;
        }

        public ActionBarCue withCustomDeduplicationKey(String key) {
            this.customDeduplicationKey = key;
            return this;
        }

        String messageKey() {
            return messageKey;
        }

        Object[] args() {
            return args;
        }

        ActionBarCuePriority priority() {
            return priority;
        }

        int ttlTicks() {
            return ttlTicks;
        }

        int repeatCooldownTicks() {
            return repeatCooldownTicks;
        }

        ActionBarCueSource source() {
            return source;
        }

        String customDeduplicationKey() {
            return customDeduplicationKey;
        }
    }

    /**
     * Metadata describing where a cue originated.
     */
    public record ActionBarCueSource(UUID petId, Vec3d position, double maxDistanceSq) {
        public static ActionBarCueSource forPet(MobEntity pet) {
            return new ActionBarCueSource(
                pet.getUuid(),
                pet.getPos(),
                DEFAULT_DISTANCE_RADIUS_SQ
            );
        }

        public ActionBarCueSource withRadius(double radius) {
            double value = radius > 0 ? radius * radius : DEFAULT_DISTANCE_RADIUS_SQ;
            return new ActionBarCueSource(petId, position, value);
        }

        public ActionBarCueSource withPosition(Vec3d newPosition) {
            return new ActionBarCueSource(petId, newPosition, maxDistanceSq);
        }
    }

    /**
     * Cue priority tiers.
     */
    public enum ActionBarCuePriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
}
