package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import java.util.EnumSet;

/**
 * Actor goal that keeps pets under cover when recently soaked during rain.
 */
public class ShelterFromRainGoal extends AdaptiveGoal {
    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private static final long RECENT_WET_THRESHOLD = 2400L;

    private BlockPos holdPosition = BlockPos.ORIGIN;

    public ShelterFromRainGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SHELTER_FROM_RAIN), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        return petComponent != null && isShelterHoldActive();
    }

    @Override
    protected boolean shouldContinueGoal() {
        return petComponent != null && isShelterHoldActive();
    }

    @Override
    protected void onStartGoal() {
        holdPosition = mob.getBlockPos();
        mob.getNavigation().stop();
    }

    @Override
    protected void onStopGoal() {
        // no-op
    }

    @Override
    protected void onTickGoal() {
        mob.getNavigation().stop();
        PlayerEntity owner = petComponent.getOwner();
        if (owner != null) {
            mob.getLookControl().lookAt(owner, 10.0f, mob.getMaxLookPitchChange());
        }
        if (!mob.getBlockPos().equals(holdPosition)) {
            mob.getNavigation().startMovingTo(holdPosition.getX() + 0.5, holdPosition.getY(), holdPosition.getZ() + 0.5, 0.4);
        }
    }

    @Override
    protected float calculateEngagement() {
        return 0.2f;
    }

    private boolean isShelterHoldActive() {
        var world = mob.getEntityWorld();
        if (world == null || (!world.isRaining() && !world.isThundering())) {
            return false;
        }
        if (world.isSkyVisible(mob.getBlockPos())) {
            return false;
        }
        if (petComponent == null) {
            return false;
        }
        Long lastWetTick = petComponent.getStateData(LAST_WET_TICK_KEY, Long.class);
        if (lastWetTick == null) {
            return false;
        }
        long now = world.getTime();
        return now - lastWetTick <= RECENT_WET_THRESHOLD;
    }
}
