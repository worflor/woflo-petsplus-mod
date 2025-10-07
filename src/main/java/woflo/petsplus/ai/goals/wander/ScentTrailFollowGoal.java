package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;
import java.util.List;

/**
 * Scent trail following - investigates paths where mobs recently walked.
 * Creates a sense of curiosity and investigation.
 */
public class ScentTrailFollowGoal extends AdaptiveGoal {
    private BlockPos trailTarget;
    private int investigationTicks = 0;
    private static final int MAX_INVESTIGATION = 100;
    
    public ScentTrailFollowGoal(MobEntity mob) {
        super(mob, GoalType.SCENT_TRAIL_FOLLOW, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isOnGround() && findScentTrail() != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return investigationTicks < MAX_INVESTIGATION && trailTarget != null;
    }
    
    @Override
    protected void onStartGoal() {
        investigationTicks = 0;
        trailTarget = findScentTrail();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(0);
    }
    
    @Override
    protected void onTickGoal() {
        investigationTicks++;
        
        if (trailTarget != null) {
            // Walk to scent location
            if (mob.getBlockPos().isWithinDistance(trailTarget, 1.5)) {
                // Reached target - sniff thoroughly
                mob.getNavigation().stop();
                mob.setPitch(45); // Nose to ground
                
                // After sniffing, find next trail point
                if (investigationTicks % 20 == 0) {
                    trailTarget = findScentTrail();
                }
            } else {
                mob.getNavigation().startMovingTo(trailTarget.getX(), trailTarget.getY(), trailTarget.getZ(), 0.9);
                mob.setPitch(30); // Nose tilted down while following
            }
        }
    }
    
    /**
     * Find a spot where another entity recently walked.
     */
    private BlockPos findScentTrail() {
        PetContext ctx = getContext();
        List<Entity> nearby = ctx.nearbyEntities();
        
        // Look for entities that aren't the owner
        for (Entity entity : nearby) {
            if (entity == ctx.owner() || entity == mob) {
                continue;
            }
            
            // Target their current position with some randomness
            BlockPos entityPos = entity.getBlockPos();
            int dx = mob.getRandom().nextInt(3) - 1;
            int dz = mob.getRandom().nextInt(3) - 1;
            return entityPos.add(dx, 0, dz);
        }
        
        // No entities found - pick a random nearby spot
        if (mob.getRandom().nextFloat() < 0.3f) {
            int dx = mob.getRandom().nextInt(8) - 4;
            int dz = mob.getRandom().nextInt(8) - 4;
            return mob.getBlockPos().add(dx, 0, dz);
        }
        
        return null;
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;
        
        // Very engaging if curious
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.3f)) {
            engagement = 0.9f;
        }
        
        return engagement;
    }
}
