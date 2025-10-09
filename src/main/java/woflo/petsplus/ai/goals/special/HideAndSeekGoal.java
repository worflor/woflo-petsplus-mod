package woflo.petsplus.ai.goals.special;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Hide and seek - playfully hides and peeks at owner.
 * Creates mischievous, playful interaction.
 */
public class HideAndSeekGoal extends AdaptiveGoal {
    private BlockPos hidingSpot;
    private int seekPhase = 0; // 0 = find spot, 1 = hide, 2 = peek
    private int hideTicks = 0;
    private static final int MAX_HIDE_TICKS = 200; // 10 seconds
    
    public HideAndSeekGoal(MobEntity mob) {
        super(mob, GoalType.HIDE_AND_SEEK, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null || !ctx.ownerNearby()) return false;
        
        hidingSpot = findHidingSpot();
        return hidingSpot != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return hideTicks < MAX_HIDE_TICKS && hidingSpot != null;
    }
    
    @Override
    protected void onStartGoal() {
        seekPhase = 0;
        hideTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        hidingSpot = null;
    }
    
    @Override
    protected void onTickGoal() {
        hideTicks++;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null || hidingSpot == null) return;
        
        if (seekPhase == 0) {
            // Phase 0: Move to hiding spot
            mob.getNavigation().startMovingTo(
                hidingSpot.getX(), 
                hidingSpot.getY(), 
                hidingSpot.getZ(), 
                1.1
            );
            
            if (mob.getBlockPos().isWithinDistance(hidingSpot, 1.5)) {
                seekPhase = 1;
            }
        } else if (seekPhase == 1) {
            // Phase 1: Hide (stay still, face away from owner)
            mob.getNavigation().stop();
            
            // Look away from owner
            double dx = mob.getX() - owner.getX();
            double dz = mob.getZ() - owner.getZ();
            float awayYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            mob.setYaw(awayYaw);
            
            // Occasionally peek
            if (hideTicks % 40 == 0) {
                seekPhase = 2;
            }
        } else if (seekPhase == 2) {
            // Phase 2: Peek at owner
            mob.getLookControl().lookAt(owner);
            
            // Return to hiding after brief peek
            if (hideTicks % 40 == 10) {
                seekPhase = 1;
            }
        }
    }
    
    /**
     * Finds a suitable hiding spot near owner.
     */
    private BlockPos findHidingSpot() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) return null;
        
        BlockPos ownerPos = owner.getBlockPos();
        
        for (int attempts = 0; attempts < 10; attempts++) {
            int dx = mob.getRandom().nextInt(10) - 5;
            int dz = mob.getRandom().nextInt(10) - 5;
            int dy = mob.getRandom().nextInt(3) - 1;
            
            BlockPos candidate = ownerPos.add(dx, dy, dz);
            
            // Must be partially hidden but not too far
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 3.0 && distance < 8.0) {
                // Check if there's a block nearby for cover
                if (!mob.getEntityWorld().getBlockState(candidate).isAir() ||
                    !mob.getEntityWorld().getBlockState(candidate.north()).isAir() ||
                    !mob.getEntityWorld().getBlockState(candidate.south()).isAir() ||
                    !mob.getEntityWorld().getBlockState(candidate.east()).isAir() ||
                    !mob.getEntityWorld().getBlockState(candidate.west()).isAir()) {
                    return candidate;
                }
            }
        }
        
        return null;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.30f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.28f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.022f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.9f;
        
        // Peak engagement for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        // Very engaging for young pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement = 1.0f;
        }
        
        // Peek phase is most engaging
        if (seekPhase == 2) {
            engagement = 1.0f;
        }
        
        return engagement;
    }
}

