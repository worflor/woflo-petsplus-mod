package woflo.petsplus.ai.goals.play;


import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Play behavior - pounce on toy-like blocks (wool, hay, slime).
 */
public class ToyPounceGoal extends AdaptiveGoal {
    private static final int BASE_CROUCH_DURATION = 18;

    private static final double MIN_LAUNCH_DISTANCE_SQUARED = 1.0e-4;

    private BlockPos toyBlock;
    private int pouncePhase = 0; // 0=approach, 1=crouch, 2=pounce, 3=success
    private int phaseTicks = 0;

    private int getCrouchDuration() {
        PetContext ctx = getContext();
        float playfulness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.PLAYFUL) : 0.0f;
        float adjusted = MathHelper.clamp(BASE_CROUCH_DURATION * (1.2f - playfulness), 10f, 28f);
        return (int) adjusted;
    }
    
    public ToyPounceGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.TOY_POUNCE), EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        toyBlock = findNearbyToy();
        return toyBlock != null && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return pouncePhase < 3 && toyBlock != null && isToyBlockValid(toyBlock);
    }
    
    @Override
    protected void onStartGoal() {
        pouncePhase = 0;
        phaseTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.setSneaking(false);
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        phaseTicks++;

        if (toyBlock == null || !isToyBlockValid(toyBlock)) {
            requestStop();
            return;
        }
        
        switch (pouncePhase) {
            case 0: { // Approach
                Vec3d toyCenter = Vec3d.ofCenter(toyBlock);
                if (mob.squaredDistanceTo(toyCenter.x, toyCenter.y, toyCenter.z) <= 9.0) {
                    pouncePhase = 1;
                    phaseTicks = 0;
                } else {
                    mob.getNavigation().startMovingTo(toyCenter.x, toyCenter.y, toyCenter.z, 1.0);
                }
                break;
            }

            case 1: { // Crouch and wiggle
                mob.setSneaking(true);
                Vec3d toyFocus = Vec3d.ofCenter(toyBlock);
                mob.getLookControl().lookAt(toyFocus.x, toyFocus.y, toyFocus.z);

                // Wiggle
                if (phaseTicks % 4 < 2) {
                    mob.setYaw(mob.getYaw() + 3);
                } else {
                    mob.setYaw(mob.getYaw() - 3);
                }
                
                if (phaseTicks > getCrouchDuration()) {
                    pouncePhase = 2;
                    phaseTicks = 0;
                }
                break;
            }

            case 2: { // Pounce!
                mob.setSneaking(false);
                if (phaseTicks == 1) {
                    // Jump toward toy
                    Vec3d toyCenter = Vec3d.ofCenter(toyBlock);
                    double dx = toyCenter.x - mob.getX();
                    double dz = toyCenter.z - mob.getZ();
                    double horizontalSquared = dx * dx + dz * dz;

                    if (horizontalSquared < MIN_LAUNCH_DISTANCE_SQUARED) {
                        double randomAngle = mob.getRandom().nextDouble() * (Math.PI * 2.0);
                        double vx = Math.cos(randomAngle) * 0.5;
                        double vz = Math.sin(randomAngle) * 0.5;
                        mob.setVelocity(vx, 0.5, vz);
                    } else {
                        double distance = Math.sqrt(horizontalSquared);
                        mob.setVelocity(dx / distance * 0.5, 0.5, dz / distance * 0.5);
                    }
                    mob.velocityModified = true;
                }

                if (mob.isOnGround() && phaseTicks > 5) {
                    pouncePhase = 3;
                    spawnSuccessParticles();
                }
                break;
            }
        }
    }
    
    private BlockPos findNearbyToy() {
        BlockPos pos = mob.getBlockPos();
        
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos check = pos.add(dx, dy, dz);
                    BlockState state = mob.getEntityWorld().getBlockState(check);
                    
                    if (state.isIn(woflo.petsplus.tags.PetsplusBlockTags.TOY_BLOCKS)) {
                        return check;
                    }
                }
            }
        }
        
        return null;
    }

    private boolean isToyBlockValid(BlockPos check) {
        if (check == null) {
            return false;
        }

        if (!mob.getEntityWorld().isChunkLoaded(check)) {
            return false;
        }

        BlockState state = mob.getEntityWorld().getBlockState(check);
        return state.isIn(woflo.petsplus.tags.PetsplusBlockTags.TOY_BLOCKS);
    }
    
    private void spawnSuccessParticles() {
        if (mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.HEART,
                mob.getX(), mob.getY() + 0.5, mob.getZ(),
                3,
                0.3, 0.3, 0.3,
                0
            );
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.25f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.020f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.62f, 1.15f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.55f) / 0.35f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.12f);
        engagement *= momentumScale;

        // Very engaging for young/playful pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.2f;
        }
        
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.3f)) {
            engagement = 1.0f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

