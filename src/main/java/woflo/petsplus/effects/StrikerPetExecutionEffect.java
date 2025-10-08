package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.striker.StrikerExecution;
import woflo.petsplus.roles.striker.StrikerExecution.ExecutionResult;
import woflo.petsplus.ui.FeedbackManager;

/**
 * Pet execution effect - allows striker pets to execute marked low-health targets.
 */
public final class StrikerPetExecutionEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "striker_pet_execution");
    private static final String DATA_APPLIED_FLAG = "striker_pet_execution_applied";

    private final boolean emitFeedback;
    private final boolean requireFinisherMark;

    public StrikerPetExecutionEffect(JsonObject json) {
        this.emitFeedback = RegistryJsonHelper.getBoolean(json, "emit_feedback", true);
        this.requireFinisherMark = RegistryJsonHelper.getBoolean(json, "require_finisher_mark", true);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        TriggerContext trigger = context.getTriggerContext();
        if (trigger == null || Boolean.TRUE.equals(trigger.getData(DATA_APPLIED_FLAG, Boolean.class))) {
            return false;
        }

        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();
        if (pet == null || !(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        Entity victimEntity = trigger.getVictim();
        if (!(victimEntity instanceof LivingEntity victim)) {
            return false;
        }

        // Check finisher mark requirement
        if (requireFinisherMark && !StrikerExecution.hasFinisherMark(victim)) {
            return false;
        }

        double baseDamage = Math.max(0.0, trigger.getDamage());
        if (baseDamage <= 0.0) {
            return false;
        }

        // Evaluate execution using existing system
        ExecutionResult execution = StrikerExecution.evaluateExecution(serverOwner, victim, (float) baseDamage, true, null);
        if (!execution.triggered()) {
            return false;
        }

        // Apply execution damage
        DamageInterceptionResult result = context.getDamageResult();
        if (result == null) {
            result = new DamageInterceptionResult(baseDamage);
            trigger.withDamageContext(trigger.getIncomingDamageSource(), baseDamage, trigger.isLethalDamage(), result);
        }

        double adjustedDamage = Math.max(0.0, baseDamage + execution.bonusDamage());
        result.setRemainingDamageAmount(adjustedDamage);

        // Mark execution data for pet_killed_entity trigger
        trigger.withData(DATA_APPLIED_FLAG, Boolean.TRUE)
               .withData("execution_threshold_pct", (double) execution.appliedThresholdPct())
               .withData("execution_momentum_stacks", execution.momentumStacks())
               .withData("execution_momentum_fill", (double) execution.momentumFill())
               .withData("striker_level", execution.strikerLevel())
               .withData("execution_triggered", Boolean.TRUE)
               .withData("pet_execution", Boolean.TRUE);

        // Remove finisher mark on execution
        if (requireFinisherMark) {
            TagTargetEffect.removeTag(victim, "petsplus:finisher");
            StrikerExecution.flagFinisherConsumption(serverOwner, victim);
        }

        if (emitFeedback) {
            ServerWorld world = context.getEntityWorld();
            if (world != null) {
                FeedbackManager.emitStrikerExecution(serverOwner, victim, world,
                    execution.momentumStacks(), execution.momentumFill());
            }
        }

        return true;
    }
}


