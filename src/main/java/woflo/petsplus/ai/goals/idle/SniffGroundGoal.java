package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Land-specific idle quirk - pet sniffs the ground investigating scents.
 */
public class SniffGroundGoal extends AdaptiveGoal {
    private BlockPos sniffTarget;
    private int sniffTicks = 0;
    private static final int SNIFF_DURATION = 40;
    
    public SniffGroundGoal(MobEntity mob) {
        super(mob, GoalType.SNIFF_GROUND, EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isOnGround() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return sniffTicks < SNIFF_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        sniffTicks = 0;
        // Pick a nearby spot to sniff
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 1.0 + mob.getRandom().nextDouble();
        sniffTarget = mob.getBlockPos().add(
            (int)(Math.cos(angle) * distance),
            0,
            (int)(Math.sin(angle) * distance)
        );
    }
    
    @Override
    protected void onStopGoal() {
        mob.setPitch(0);
    }
    
    @Override
    protected void onTickGoal() {
        sniffTicks++;
        
        // Look at ground
        Vec3d targetVec = Vec3d.ofBottomCenter(sniffTarget);
        mob.getLookControl().lookAt(targetVec.x, targetVec.y, targetVec.z);
        mob.setPitch(45); // Nose to ground
        
        // Sniff animation (head bob)
        if (sniffTicks % 10 < 5) {
            mob.setPitch(40);
        } else {
            mob.setPitch(50);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;
        
        // More engaging if curious
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.3f)) {
            engagement += 0.3f;
        }
        
        return engagement;
    }
}
