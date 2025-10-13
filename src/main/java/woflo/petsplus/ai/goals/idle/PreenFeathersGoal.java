package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.traits.SpeciesTraits;

import java.util.EnumSet;

/**
 * Flying-specific idle quirk - bird preens its feathers.
 */
public class PreenFeathersGoal extends AdaptiveGoal {
    private static final int BASE_PREEN_DURATION = 80;

    private int preenTicks = 0;
    private int preenSpot = 0; // 0=wing, 1=chest, 2=tail
    private int getPreenDuration() {
        PetContext ctx = getContext();
        float calmness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.CALM) : 0.0f;
        float multiplier = MathHelper.clamp(1.0f + calmness, 0.7f, 1.6f);
        return (int) (BASE_PREEN_DURATION * multiplier);
    }
    
    public PreenFeathersGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PREEN_FEATHERS), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating - require flierPercher OR groomingSpecies
        var profile = SpeciesTraits.getProfile(mob);
        if (!(profile.flierPercher() || profile.groomingSpecies())) {
            return false;
        }
        return mob.getNavigation().isIdle() && (mob.isOnGround() || mob.hasVehicle());
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return preenTicks < getPreenDuration();
    }
    
    @Override
    protected void onStartGoal() {
        preenTicks = 0;
        preenSpot = mob.getRandom().nextInt(3);
    }
    
    @Override
    protected void onStopGoal() {
        mob.setPitch(0);
        mob.setYaw(mob.bodyYaw);
    }
    
    @Override
    protected void onTickGoal() {
        preenTicks++;

        if (preenTicks == 1) {
            mob.playSound(net.minecraft.sound.SoundEvents.ENTITY_CHICKEN_AMBIENT, 0.5f, 1.5f);
        }

        // Preen different spots
        switch (preenSpot) {
            case 0: // Wing
                if (preenTicks % 20 < 10) {
                    mob.setYaw(mob.bodyYaw - 30);
                } else {
                    mob.setYaw(mob.bodyYaw + 30);
                }
                mob.setPitch(20);
                break;
            case 1: // Chest
                mob.setPitch(45);
                break;
            case 2: // Tail
                mob.setYaw(mob.bodyYaw + 180);
                mob.setPitch(30);
                break;
        }

        // Switch spots occasionally
        if (preenTicks % 20 == 0) {
            preenSpot = mob.getRandom().nextInt(3);
        }

        // Particle effect
        if (!mob.getEntityWorld().isClient()) {
            ((net.minecraft.server.world.ServerWorld) mob.getEntityWorld()).spawnParticles(new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, new net.minecraft.item.ItemStack(net.minecraft.item.Items.FEATHER)), mob.getParticleX(0.5D), mob.getRandomBodyY(), mob.getParticleZ(0.5D), 1, 0.1, 0.1, 0.1, 0.02);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f; // Satisfying grooming behavior

        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());

        return MathHelper.clamp(engagement, 0f, 1f);
    }
}
