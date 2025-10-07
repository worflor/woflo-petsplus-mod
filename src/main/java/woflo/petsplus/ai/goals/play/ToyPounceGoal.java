package woflo.petsplus.ai.goals.play;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Play behavior - pounce on toy-like blocks (wool, hay, slime).
 */
public class ToyPounceGoal extends AdaptiveGoal {
    private BlockPos toyBlock;
    private int pouncePhase = 0; // 0=approach, 1=crouch, 2=pounce, 3=success
    private int phaseTicks = 0;
    
    public ToyPounceGoal(MobEntity mob) {
        super(mob, GoalType.TOY_POUNCE, EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        toyBlock = findNearbyToy();
        return toyBlock != null && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return pouncePhase < 3 && toyBlock != null;
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
        
        switch (pouncePhase) {
            case 0: // Approach
                if (mob.getBlockPos().isWithinDistance(toyBlock, 3.0)) {
                    pouncePhase = 1;
                    phaseTicks = 0;
                } else {
                    mob.getNavigation().startMovingTo(toyBlock.getX(), toyBlock.getY(), toyBlock.getZ(), 1.0);
                }
                break;
                
            case 1: // Crouch and wiggle
                mob.setSneaking(true);
                mob.getLookControl().lookAt(toyBlock.getX(), toyBlock.getY(), toyBlock.getZ());
                
                // Wiggle
                if (phaseTicks % 4 < 2) {
                    mob.setYaw(mob.getYaw() + 3);
                } else {
                    mob.setYaw(mob.getYaw() - 3);
                }
                
                if (phaseTicks > 20) {
                    pouncePhase = 2;
                    phaseTicks = 0;
                }
                break;
                
            case 2: // Pounce!
                mob.setSneaking(false);
                if (phaseTicks == 1) {
                    // Jump toward toy
                    double dx = toyBlock.getX() - mob.getX();
                    double dz = toyBlock.getZ() - mob.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    mob.setVelocity(dx / distance * 0.5, 0.5, dz / distance * 0.5);
                    mob.velocityModified = true;
                }
                
                if (mob.isOnGround() && phaseTicks > 5) {
                    pouncePhase = 3;
                    spawnSuccessParticles();
                }
                break;
        }
    }
    
    private BlockPos findNearbyToy() {
        BlockPos pos = mob.getBlockPos();
        
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos check = pos.add(dx, dy, dz);
                    Block block = mob.getWorld().getBlockState(check).getBlock();
                    
                    if (block == Blocks.WHITE_WOOL || block == Blocks.HAY_BLOCK ||
                        block == Blocks.SLIME_BLOCK || block.getName().getString().contains("wool")) {
                        return check;
                    }
                }
            }
        }
        
        return null;
    }
    
    private void spawnSuccessParticles() {
        if (mob.getWorld() instanceof ServerWorld serverWorld) {
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
            .add(woflo.petsplus.state.PetComponent.Emotion.GLEE, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.020f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;
        
        // Very engaging for young/playful pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.2f;
        }
        
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.3f)) {
            engagement = 1.0f;
        }
        
        return engagement;
    }
}
