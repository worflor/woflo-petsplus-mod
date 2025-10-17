package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.ai.movement.SpeedModifier;
import woflo.petsplus.ai.movement.TargetDistanceModifier;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Influencer that tightens follow distance and adds a speed burst when the pet hesitates in combat.
 */
public class HesitateInCombatGoal extends AdaptiveGoal {
    private static final double HESITATION_DISTANCE_FACTOR = 0.6d;
    private static final double HESITATION_CLEAR_DISTANCE = 2.5d;
    private static final double HESITATION_SPEED_BOOST = 0.2d;

    private final PetComponent petComponent;
    private final PetComponent.MovementDirector movementDirector;

    public HesitateInCombatGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.HESITATE_IN_COMBAT), EnumSet.noneOf(Control.class));
        this.petComponent = PetComponent.get(mob);
        this.movementDirector = petComponent != null ? petComponent.getMovementDirector() : null;
    }

    @Override
    protected boolean canStartGoal() {
        if (petComponent == null || movementDirector == null) {
            return false;
        }
        long now = mob.getEntityWorld().getTime();
        return OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
    }

    @Override
    protected boolean shouldContinueGoal() {
        long now = mob.getEntityWorld().getTime();
        return OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
    }

    @Override
    protected void onStartGoal() {
        applyModifiers();
    }

    @Override
    protected void onStopGoal() {
        movementDirector.clearSource(goalId);
    }

    @Override
    protected void onTickGoal() {
        if (petComponent == null) {
            return;
        }
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) {
            return;
        }
        double distanceSq = mob.squaredDistanceTo(owner);
        if (distanceSq <= (HESITATION_CLEAR_DISTANCE * HESITATION_CLEAR_DISTANCE)) {
            OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
        }
    }

    @Override
    protected float calculateEngagement() {
        return 0.4f;
    }

    private void applyModifiers() {
        if (petComponent == null) {
            return;
        }
        double baseFollow = FollowTuning.resolveFollowDistance(petComponent);
        double desired = Math.max(HESITATION_CLEAR_DISTANCE, baseFollow * HESITATION_DISTANCE_FACTOR);
        double delta = desired - baseFollow;
        movementDirector.setTargetDistance(goalId,
            new TargetDistanceModifier(HESITATION_CLEAR_DISTANCE, delta, 1.0));
        movementDirector.setSpeedModifier(goalId, new SpeedModifier(1.0, HESITATION_SPEED_BOOST, 0.0, Double.POSITIVE_INFINITY));
    }
}
