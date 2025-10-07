package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Flying-specific idle quirk - bird preens its feathers.
 */
public class PreenFeathersGoal extends AdaptiveGoal {
    private int preenTicks = 0;
    private int preenSpot = 0; // 0=wing, 1=chest, 2=tail
    private static final int PREEN_DURATION = 60;
    
    public PreenFeathersGoal(MobEntity mob) {
        super(mob, GoalType.PREEN_FEATHERS, EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle() && (mob.isOnGround() || mob.hasVehicle());
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return preenTicks < PREEN_DURATION;
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
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.6f; // Satisfying grooming behavior
    }
}
