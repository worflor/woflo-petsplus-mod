package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.striker.StrikerExecution;
import woflo.petsplus.roles.striker.StrikerExecution.ExecutionResult;
import woflo.petsplus.roles.striker.StrikerExecution.ExecutionTuning;
import woflo.petsplus.ui.FeedbackManager;

/**
 * Applies the Striker execution damage bonus via the owner outgoing-damage trigger.
 */
public final class StrikerExecutionEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "striker_execution");
    private static final String DATA_APPLIED_FLAG = "striker_execution_applied";

    private final boolean emitFeedback;
    private final boolean publishPreview;
    private final boolean ownerCannotExecute;
    @Nullable
    private final ExecutionTuning overrides;

    public StrikerExecutionEffect(JsonObject json) {
        this.emitFeedback = RegistryJsonHelper.getBoolean(json, "emit_feedback", true);
        this.publishPreview = RegistryJsonHelper.getBoolean(json, "publish_preview", true);
        this.ownerCannotExecute = RegistryJsonHelper.getBoolean(json, "owner_cannot_execute", false);

        Double baseThreshold = readOptionalDouble(json, "override_threshold_pct");
        Double chainBonusPerStack = readOptionalDouble(json, "override_chain_bonus_per_stack_pct");
        Integer chainMaxStacks = readOptionalInt(json, "override_chain_max_stacks");
        Integer chainDurationTicks = readOptionalInt(json, "override_chain_duration_ticks");

        if (baseThreshold != null || chainBonusPerStack != null || chainMaxStacks != null || chainDurationTicks != null) {
            this.overrides = new ExecutionTuning(baseThreshold, chainBonusPerStack, chainMaxStacks, chainDurationTicks);
        } else {
            this.overrides = null;
        }
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        TriggerContext trigger = context.getTriggerContext();
        if (trigger == null) {
            return false;
        }
        if (Boolean.TRUE.equals(trigger.getData(DATA_APPLIED_FLAG, Boolean.class))) {
            return false;
        }

        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        Entity victimEntity = trigger.getVictim();
        if (!(victimEntity instanceof LivingEntity victim)) {
            return false;
        }

        double baseDamage = Math.max(0.0, trigger.getDamage());
        if (baseDamage <= 0.0) {
            return false;
        }

        ExecutionResult execution = StrikerExecution.evaluateExecution(serverOwner, victim, (float) baseDamage, true, overrides);
        publishPreview(trigger, execution);

        if (!execution.triggered()) {
            return false;
        }

        // If owner cannot execute, just build momentum but don't apply damage
        if (ownerCannotExecute) {
            trigger.withData(DATA_APPLIED_FLAG, Boolean.TRUE)
                   .withData("execution_threshold_pct", (double) execution.appliedThresholdPct())
                   .withData("execution_momentum_stacks", execution.momentumStacks())
                   .withData("execution_momentum_fill", (double) execution.momentumFill())
                   .withData("striker_level", execution.strikerLevel())
                   .withData("owner_momentum_only", Boolean.TRUE);
            return true;
        }

        DamageInterceptionResult result = context.getDamageResult();
        if (result == null) {
            result = new DamageInterceptionResult(baseDamage);
            trigger.withDamageContext(trigger.getIncomingDamageSource(), baseDamage, trigger.isLethalDamage(), result);
        }

        double adjustedDamage = Math.max(0.0, baseDamage + execution.bonusDamage());
        result.setRemainingDamageAmount(adjustedDamage);

        trigger.withData(DATA_APPLIED_FLAG, Boolean.TRUE)
               .withData("execution_threshold_pct", (double) execution.appliedThresholdPct())
               .withData("execution_momentum_stacks", execution.momentumStacks())
               .withData("execution_momentum_fill", (double) execution.momentumFill())
               .withData("striker_level", execution.strikerLevel())
               .withData("execution_triggered", Boolean.TRUE);

        if (emitFeedback) {
            ServerWorld world = context.getEntityWorld();
            if (world != null) {
                FeedbackManager.emitStrikerExecution(serverOwner, victim, world,
                    execution.momentumStacks(), execution.momentumFill());
            }
        }

        return true;
    }

    private void publishPreview(TriggerContext trigger, ExecutionResult execution) {
        if (!publishPreview) {
            return;
        }
        trigger.withData("striker_level", execution.strikerLevel())
               .withData("striker_preview_threshold_pct", (double) execution.appliedThresholdPct())
               .withData("striker_preview_momentum_stacks", execution.momentumStacks())
               .withData("striker_preview_momentum_fill", (double) execution.momentumFill())
               .withData("striker_preview_ready", execution.triggered());
    }

    @Nullable
    private static Double readOptionalDouble(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer readOptionalInt(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }
}


