package woflo.petsplus.roles.striker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.state.PlayerTickListener;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the Striker hunt loop so marks, executions, and momentum stacks
 * stay in sync with the player's action bar feedback.
 */
public final class StrikerHuntManager implements PlayerTickListener {

    private static final Identifier FINISHER_TAG = Identifier.of("petsplus", "finisher");
    private static final int DEFAULT_MARK_DURATION_TICKS = 120;
    private static final int MOMENTUM_WARNING_WINDOW_TICKS = 30;

    private static final StrikerHuntManager INSTANCE = new StrikerHuntManager();

    private final Map<UUID, HuntState> huntStates = new HashMap<>();
    private final Map<UUID, Long> schedules = new HashMap<>();

    private StrikerHuntManager() {
    }

    public static StrikerHuntManager getInstance() {
        return INSTANCE;
    }

    public void onTargetMarked(ServerPlayerEntity owner, LivingEntity target, double thresholdPct,
                               int strikerLevel, int previewStacks, double previewFill) {
        if (owner == null || target == null) {
            return;
        }

        HuntState state = huntStates.computeIfAbsent(owner.getUuid(), uuid -> new HuntState());
        ServerWorld world = world(owner);
        long now = world.getTime();

        state.markedTargetId = target.getUuid();
        state.markedTargetName = target.getDisplayName().getString();
        state.thresholdPct = MathHelper.clamp((float) thresholdPct, 0f, 1f);
        state.strikerLevel = Math.max(1, strikerLevel);
        state.markExpireTick = now + DEFAULT_MARK_DURATION_TICKS;
        state.markWarningSent = false;
        state.markActive = true;
        state.previewStacks = Math.max(0, previewStacks);
        state.previewFill = MathHelper.clamp((float) previewFill, 0f, 1f);
        state.pendingFinisher = true;

        int activeStacks = Math.max(Math.max(previewStacks, 0), StrikerExecution.getActiveMomentumStacks(owner));
        state.activeMomentumStacks = activeStacks;
        state.momentumWarningSent = false;

        schedule(owner, now + 5);

        UIFeedbackManager.sendStrikerHuntFocusMessage(owner, state.markedTargetName,
            state.thresholdPct, Math.max(0, state.activeMomentumStacks));
        FeedbackManager.emitRoleAbility(PetRoleType.STRIKER.id(), "mark_focus", owner, world);
    }

    public void onExecutionKill(ServerPlayerEntity owner, LivingEntity target,
                                StrikerExecution.ExecutionKillSummary summary,
                                boolean finisherConsumed) {
        if (owner == null || summary == null) {
            return;
        }

        HuntState state = huntStates.computeIfAbsent(owner.getUuid(), uuid -> new HuntState());
        ServerWorld world = world(owner);
        long now = world.getTime();

        state.markActive = false;
        state.markedTargetId = null;
        state.markedTargetName = target != null ? target.getDisplayName().getString() : state.markedTargetName;
        state.markWarningSent = false;
        state.pendingFinisher = false;

        int stacks = Math.max(1, summary.momentumStacks());
        state.activeMomentumStacks = stacks;
        state.momentumWarningSent = false;
        state.thresholdPct = MathHelper.clamp(summary.thresholdPct(), 0f, 1f);
        state.previewFill = (float) MathHelper.clamp(summary.momentumFill(), 0.0, 1.0);
        state.previewStacks = stacks;
        state.strikerLevel = Math.max(1, summary.strikerLevel());
        state.lastExecutionTick = now;

        int ticksRemaining = StrikerExecution.getMomentumTicksRemaining(owner);
        if (ticksRemaining <= 0) {
            int duration = StrikerExecution.getMomentumDurationTicks(owner);
            if (duration > 0) {
                ticksRemaining = Math.max(1, (int) Math.round(duration * state.previewFill));
            }
        }
        state.momentumExpiryTick = ticksRemaining > 0 ? now + ticksRemaining : now;

        UIFeedbackManager.sendStrikerExecutionMessage(owner, summary.thresholdPct(), stacks);
        FeedbackManager.emitRoleAbility(PetRoleType.STRIKER.id(), "execution", owner, world);

        if (finisherConsumed && state.markedTargetName != null) {
            UIFeedbackManager.sendStrikerFinisherConfirmMessage(owner, state.markedTargetName);
        }

        schedule(owner, now + 5);
    }

    public void onFinisherSpent(ServerPlayerEntity owner, @Nullable LivingEntity target) {
        if (owner == null) {
            return;
        }
        HuntState state = huntStates.computeIfAbsent(owner.getUuid(), uuid -> new HuntState());
        state.markActive = false;
        state.markedTargetId = null;
        state.markWarningSent = false;
        state.pendingFinisher = false;
        String name = target != null ? target.getDisplayName().getString() : state.markedTargetName;
        if (name != null) {
            UIFeedbackManager.sendStrikerMarkSpentMessage(owner, name);
        }
    }

    public void onBloodlustTriggered(ServerPlayerEntity owner, int stacks, int durationTicks) {
        if (owner == null || stacks <= 0) {
            return;
        }

        HuntState state = huntStates.computeIfAbsent(owner.getUuid(), uuid -> new HuntState());
        ServerWorld world = world(owner);
        long now = world.getTime();
        state.activeMomentumStacks = Math.max(state.activeMomentumStacks, stacks);
        state.momentumWarningSent = false;
        state.momentumExpiryTick = now + Math.max(durationTicks, MOMENTUM_WARNING_WINDOW_TICKS);
        schedule(owner, now + Math.max(5, Math.min(20, durationTicks)));
    }

    public void onOwnerKill(ServerPlayerEntity owner) {
        if (owner == null) {
            return;
        }
        long now = world(owner).getTime();
        schedule(owner, now + 20);
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        return schedules.getOrDefault(player.getUuid(), Long.MAX_VALUE);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        HuntState state = huntStates.get(player.getUuid());
        if (state == null) {
            schedules.put(player.getUuid(), Long.MAX_VALUE);
            return;
        }

        ServerWorld world = world(player);
        long now = world.getTime();

        if (state.markActive) {
            boolean stillMarked = ensureMarkValid(world, state);
            if (!stillMarked) {
                if (state.markedTargetName != null) {
                    UIFeedbackManager.sendStrikerMarkLostMessage(player, state.markedTargetName);
                }
                state.clearMark();
            } else if (!state.markWarningSent && state.markExpireTick - now <= MOMENTUM_WARNING_WINDOW_TICKS) {
                UIFeedbackManager.sendStrikerMarkExpiryWarning(player, state.markedTargetName);
                state.markWarningSent = true;
            }
        }

        int stacks = StrikerExecution.getActiveMomentumStacks(player);
        int ticksRemaining = StrikerExecution.getMomentumTicksRemaining(player);
        if (stacks > 0 && ticksRemaining > 0) {
            state.activeMomentumStacks = stacks;
            if (state.momentumExpiryTick <= now) {
                state.momentumExpiryTick = now + ticksRemaining;
                state.momentumWarningSent = false;
            }
            if (!state.momentumWarningSent && state.momentumExpiryTick - now <= MOMENTUM_WARNING_WINDOW_TICKS) {
                UIFeedbackManager.sendStrikerMomentumWarning(player, stacks);
                state.momentumWarningSent = true;
            }
        } else {
            state.activeMomentumStacks = 0;
            state.momentumExpiryTick = 0;
            state.momentumWarningSent = false;
        }

        if (state.isIdle()) {
            huntStates.remove(player.getUuid());
            schedules.put(player.getUuid(), Long.MAX_VALUE);
            return;
        }

        long nextTick = Long.MAX_VALUE;
        if (state.markActive) {
            nextTick = Math.min(nextTick, now + 10);
        }
        if (state.activeMomentumStacks > 0) {
            nextTick = Math.min(nextTick, now + Math.max(5, Math.min(20, ticksRemaining)));
        }
        schedules.put(player.getUuid(), nextTick);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        huntStates.remove(player.getUuid());
        schedules.remove(player.getUuid());
    }

    private boolean ensureMarkValid(ServerWorld world, HuntState state) {
        if (!state.markActive || state.markedTargetId == null) {
            return false;
        }

        Entity entity = world.getEntity(state.markedTargetId);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }

        return TagTargetEffect.hasTag(living, FINISHER_TAG.toString());
    }

    private void schedule(ServerPlayerEntity owner, long tick) {
        schedules.put(owner.getUuid(), Math.max(tick, world(owner).getTime()));
    }

    private static ServerWorld world(ServerPlayerEntity player) {
        return (ServerWorld) player.getWorld();
    }

    private static final class HuntState {
        @Nullable UUID markedTargetId;
        @Nullable String markedTargetName;
        float thresholdPct;
        int strikerLevel;
        long markExpireTick;
        boolean markWarningSent;
        boolean markActive;
        boolean pendingFinisher;
        int previewStacks;
        float previewFill;
        int activeMomentumStacks;
        long momentumExpiryTick;
        boolean momentumWarningSent;
        long lastExecutionTick;

        boolean isIdle() {
            return !markActive && activeMomentumStacks <= 0 && !pendingFinisher;
        }

        void clearMark() {
            markActive = false;
            markedTargetId = null;
            markWarningSent = false;
            pendingFinisher = false;
        }
    }
}

