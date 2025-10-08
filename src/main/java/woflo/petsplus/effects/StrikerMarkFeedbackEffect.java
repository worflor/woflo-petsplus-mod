package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.striker.StrikerHuntManager;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Sends UI and particle feedback when a Striker finisher mark is applied.
 */
public class StrikerMarkFeedbackEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "striker_mark_feedback");

    private final boolean sendMessage;
    private final boolean highlightTarget;
    private final int particleCount;
    private final double particleSpread;

    public StrikerMarkFeedbackEffect(JsonObject json) {
        this.sendMessage = RegistryJsonHelper.getBoolean(json, "send_message", true);
        this.highlightTarget = RegistryJsonHelper.getBoolean(json, "highlight_target", true);
        this.particleCount = Math.max(0, RegistryJsonHelper.getInt(json, "particle_count", 8));
        this.particleSpread = Math.max(0.01, RegistryJsonHelper.getDouble(json, "particle_spread", 0.35));
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        Entity rawTarget = context.getTriggerContext() != null
            ? context.getTriggerContext().getVictim()
            : context.getTarget();
        if (!(rawTarget instanceof LivingEntity target)) {
            return false;
        }

        if (context.getTriggerContext() != null) {
            double threshold = context.getTriggerContext().getStrikerPreviewThresholdPct();
            int stacks = context.getTriggerContext().getStrikerPreviewMomentumStacks();
            double fill = context.getTriggerContext().getStrikerPreviewMomentumFill();
            int level = context.getTriggerContext().getStrikerLevel();
            StrikerHuntManager.getInstance().onTargetMarked(serverOwner, target, threshold, level, stacks, fill);
        }

        if (highlightTarget && context.getEntityWorld() instanceof ServerWorld world) {
            spawnMarkParticles(world, target);
        }

        boolean hasPreview = context.getTriggerContext() != null
            && context.getTriggerContext().getStrikerPreviewThresholdPct() > 0.0;

        // Removed finisher mark spam - has visual particle feedback already

        return true;
    }

    private void spawnMarkParticles(ServerWorld world, LivingEntity target) {
        if (particleCount <= 0) {
            return;
        }

        Vec3d pos = target.getEntityPos();
        double y = target.getBodyY(0.5);
        for (int i = 0; i < particleCount; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * particleSpread;
            double offsetY = (world.random.nextDouble() - 0.5) * (particleSpread * 0.6);
            double offsetZ = (world.random.nextDouble() - 0.5) * particleSpread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x + offsetX, y + offsetY, pos.z + offsetZ,
                1, 0.0, 0.0, 0.0, 0.01);
        }
    }
}



