package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.Petsplus;

/**
 * Central manager for action-bar feedback cues. Provides lightweight, event-driven
 * gating so only the most relevant cue for the pet a player recently inspected is shown.
 */
public final class ActionBarCueManager implements PlayerTickListener {

    private static final ActionBarCueManager INSTANCE = new ActionBarCueManager();

    private static final int DEFAULT_TTL_TICKS = 40; // 2 seconds
    private static final int DEFAULT_REPEAT_COOLDOWN_TICKS = 200; // 10 seconds
    private static final double DEFAULT_DISTANCE_RADIUS_SQ = 12 * 12;
    private static final int FOCUS_MEMORY_TICKS = 20 * 180; // 3 minutes

    private static final Map<UUID, PlayerCueState> PLAYER_STATES = new HashMap<>();
    private static final boolean DIAGNOSTIC_LOGGING = Boolean.getBoolean("petsplus.debug.cue_logging");

    private ActionBarCueManager() {}

    public static ActionBarCueManager getInstance() {
        return INSTANCE;
    }

    private static int resolveRecentPetLimit() {
        return Math.max(1, PetsPlusConfig.getInstance().getActionBarRecentPetLimit());
    }

    private static void requestImmediateRun(ServerPlayerEntity player, PlayerCueState state, long tick) {
        if (player == null || state == null) {
            return;
        }

        state.scheduleAt(tick);
        PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
    }

    /**
     * Queue a cue for the player using the standard gating rules.
     */
    public static void queueCue(ServerPlayerEntity player, ActionBarCue cue) {
        if (player == null || cue == null) {
            return;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
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
                if (DIAGNOSTIC_LOGGING && Petsplus.LOGGER != null) {
                    Petsplus.LOGGER.debug("[CUE] Dropped cue without focus: key={}, player={}", cue.messageKey(), player.getName().getString());
                }
                return;
            }
        }

        QueuedCue queued = new QueuedCue(
            cue.messageKey(),
            cue.args().clone(),
            cue.explicitText(),
            source,
            cue.priority(),
            currentTick,
            cue.ttlTicks(),
            cue.repeatCooldownTicks(),
            cue.customDeduplicationKey()
        );

        state.cues.add(queued);
        requestImmediateRun(player, state, currentTick);
    }

    /**
     * Convenience helper to queue a basic cue with default metadata.
     */
    public static void queueCue(ServerPlayerEntity player, String messageKey, Object... args) {
        queueCue(player, ActionBarCue.of(messageKey, args));
    }

    /**
     * Queue a text cue that strictly requires a recent pet focus; if no focus is
     * available, the cue is dropped (no broadcast fallback), avoiding chat spam.
     * Returns true if queued, false if no eligible focus/source.
     */
    public static boolean queueTextCueRequireFocus(ServerPlayerEntity player, Text text) {
        if (player == null || text == null) {
            return false;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return false;
        }

        long currentTick = server.getTicks();
        PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
        int recentPetLimit = resolveRecentPetLimit();
        state.pruneFocuses(currentTick, recentPetLimit);

        ActionBarCueSource source = state.deriveImplicitSource(currentTick, recentPetLimit);
        if (source == null) {
            // No recent pet focus; drop the cue per strict focus requirement
            return false;
        }

        QueuedCue queued = new QueuedCue(
            null,
            new Object[0],
            text,
            source,
            ActionBarCuePriority.NORMAL,
            currentTick,
            DEFAULT_TTL_TICKS,
            DEFAULT_REPEAT_COOLDOWN_TICKS,
            null
        );

        state.cues.add(queued);
        requestImmediateRun(player, state, currentTick);
        return true;
    }

    /**
     * Queue a keyed cue that strictly requires a recent pet focus; if no focus is
     * available, the cue is dropped (no broadcast fallback). Returns true if queued.
     */
    public static boolean queueCueRequireFocus(ServerPlayerEntity player, ActionBarCue cue) {
        if (player == null || cue == null) {
            return false;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return false;
        }

        long currentTick = server.getTicks();
        PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
        int recentPetLimit = resolveRecentPetLimit();
        state.pruneFocuses(currentTick, recentPetLimit);

        ActionBarCueSource source = cue.source();
        if (source == null) {
            source = state.deriveImplicitSource(currentTick, recentPetLimit);
        }
        if (source == null) {
            return false; // require focus, no broadcast fallback
        }

        QueuedCue queued = new QueuedCue(
            cue.messageKey(),
            cue.args().clone(),
            cue.explicitText(),
            source,
            cue.priority(),
            currentTick,
            cue.ttlTicks(),
            cue.repeatCooldownTicks(),
            cue.customDeduplicationKey()
        );
        state.cues.add(queued);
        requestImmediateRun(player, state, currentTick);
        return true;
    }

    /**
     * Notify the manager that the player is actively looking at a pet.
     */
    public static void onPlayerLookedAtPet(ServerPlayerEntity player, MobEntity pet) {
        if (player == null || pet == null) {
            return;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return;
        }

        long currentTick = server.getTicks();
        PlayerCueState state = PLAYER_STATES.computeIfAbsent(player.getUuid(), id -> new PlayerCueState());
        int recentPetLimit = resolveRecentPetLimit();
        state.recordFocus(pet, currentTick, recentPetLimit);
        requestImmediateRun(player, state, currentTick);
    }

    /**
     * Notify the manager that the player has stopped looking at their current pet focus.
     */
    public static void onPlayerLookedAway(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
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
        requestImmediateRun(player, state, currentTick);
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }

        PlayerCueState state = PLAYER_STATES.get(player.getUuid());
        if (state == null) {
            return Long.MAX_VALUE;
        }

        return state.nextRunTick();
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null) {
            return;
        }

        PlayerCueState state = PLAYER_STATES.get(player.getUuid());
        if (state == null) {
            return;
        }

        state.clearSchedule();

        int recentPetLimit = resolveRecentPetLimit();

        state.removeExpired(currentTick);
        state.pruneFocuses(currentTick, recentPetLimit);

        if (state.isDormant()) {
            PLAYER_STATES.remove(player.getUuid());
            return;
        }

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

        if (state.isDormant()) {
            PLAYER_STATES.remove(player.getUuid());
            return;
        }

        long nextTick = state.computeNextWakeTick(currentTick, recentPetLimit);
        if (nextTick == Long.MAX_VALUE) {
            nextTick = currentTick + 1;
        }
        state.scheduleAt(nextTick);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        onPlayerDisconnect(player);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player != null) {
            PlayerCueState state = PLAYER_STATES.remove(player.getUuid());
            if (state != null) {
                state.clearSchedule();
            }
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }

        PlayerCueState previous = PLAYER_STATES.remove(playerId);
        if (previous != null) {
            previous.clearSchedule();
        }

        PlayerCueState freshState = new PlayerCueState();
        ServerWorld world = player.getEntityWorld() instanceof ServerWorld serverWorld ? serverWorld : null;
        MinecraftServer server = world != null ? world.getServer() : null;
        long currentTick = server != null ? server.getTicks() : 0L;
        freshState.scheduleAt(currentTick);
        PLAYER_STATES.put(playerId, freshState);
        PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
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

        Text message;
        if (cue.explicitText() != null) {
            // Preserve formatting of explicit text; do not override to gray here
            message = cue.explicitText();
        } else {
            message = Text.translatable(cue.messageKey(), cue.args()).formatted(Formatting.GRAY);
        }
        ActionBarUtils.sendActionBar(player, message);
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

        if (source.petId() != null) {
            // Tie cue visibility to the inspection window (boss bar) lifecycle
            java.util.UUID activePet = PetInspectionManager.getActiveInspectedPetId(player);
            if (activePet == null || !activePet.equals(source.petId())) {
                if (DIAGNOSTIC_LOGGING && Petsplus.LOGGER != null) {
                    Petsplus.LOGGER.debug("[CUE] Not eligible (no active window/mismatch): player={}, petSource={}", player.getName().getString(), source.petId());
                }
                return false;
            }
            // Check proximity/recency if window changed pets
            if (!state.hasInterestIn(source.petId(), currentTick, recentPetLimit)) {
                if (DIAGNOSTIC_LOGGING && Petsplus.LOGGER != null) {
                    Petsplus.LOGGER.debug("[CUE] Not eligible (no recent interest): player={}, petSource={}", player.getName().getString(), source.petId());
                }
                return false;
            }
        }

        Vec3d position = source.position();
        if (position != null) {
            double distanceSq = player.getEntityPos().squaredDistanceTo(position);
            if (distanceSq > source.maxDistanceSq()) {
                return false;
            }
        }

        return true;
    }

    private static final class PlayerCueState {
        private final List<QueuedCue> cues = new ArrayList<>();
        private final Map<String, Long> cooldowns = new HashMap<>();
        private final List<RecentPetFocus> recentPets = new ArrayList<>();
        private long nextRunTick = Long.MAX_VALUE;

        long nextRunTick() {
            return nextRunTick;
        }

        void scheduleAt(long tick) {
            if (tick < 0) {
                tick = 0;
            }
            if (tick < nextRunTick) {
                nextRunTick = tick;
            }
        }

        void clearSchedule() {
            nextRunTick = Long.MAX_VALUE;
        }

        long computeNextWakeTick(long currentTick, int recentPetLimit) {
            long candidate = Long.MAX_VALUE;

            if (!cues.isEmpty()) {
                candidate = Math.min(candidate, currentTick + 1);
                for (QueuedCue cue : cues) {
                    long expiry = cue.createdTick() + cue.ttlTicks();
                    if (expiry > currentTick) {
                        candidate = Math.min(candidate, expiry);
                    }
                }
            }

            for (Long cooldownEnd : cooldowns.values()) {
                if (cooldownEnd > currentTick) {
                    candidate = Math.min(candidate, cooldownEnd);
                }
            }

            int limit = Math.min(recentPets.size(), Math.max(1, recentPetLimit));
            for (int i = 0; i < limit; i++) {
                RecentPetFocus focus = recentPets.get(i);
                long expiry = focus.lastSeenTick() + FOCUS_MEMORY_TICKS;
                if (expiry > currentTick) {
                    candidate = Math.min(candidate, expiry);
                }
            }

            return candidate;
        }

        boolean isDormant() {
            return cues.isEmpty() && cooldowns.isEmpty() && recentPets.isEmpty();
        }

        void removeExpired(long currentTick) {
            cues.removeIf(cue -> cue.isExpired(currentTick));
        }

        void cleanupCooldowns(long currentTick) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
        }

        void recordFocus(MobEntity pet, long currentTick, int recentPetLimit) {
            UUID petId = pet.getUuid();
            Vec3d position = pet.getEntityPos();
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
        Text explicitText,
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
            if (messageKey != null) {
                return source.petId() + "|" + messageKey;
            }
            // For explicit texts, fall back to text content as key
            String txt = explicitText == null ? null : explicitText.getString();
            return txt == null ? null : (source.petId() + "|txt:" + txt);
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
        private Text explicitText;

        private ActionBarCue(String messageKey, Object... args) {
            this.messageKey = messageKey;
            this.args = args == null ? new Object[0] : args;
        }

        public static ActionBarCue of(String messageKey, Object... args) {
            return new ActionBarCue(messageKey, args);
        }

        public static ActionBarCue ofText(Text text) {
            ActionBarCue cue = new ActionBarCue(null);
            cue.explicitText = text;
            return cue;
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

        Text explicitText() {
            return explicitText;
        }
    }

    /**
     * Metadata describing where a cue originated.
     */
    public record ActionBarCueSource(UUID petId, Vec3d position, double maxDistanceSq) {
        public static ActionBarCueSource forPet(MobEntity pet) {
            return new ActionBarCueSource(
                pet.getUuid(),
                pet.getEntityPos(),
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


