package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.EnumSet;

public class AgeDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/age");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        float modifier = 1.0f;
        PetContext.AgeCategory age = ctx.getAgeCategory();
        NearbyMobAgeProfile mobAgeProfile = ctx.nearbyMobAgeProfile();

        if (age == PetContext.AgeCategory.YOUNG) {
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier = 1.5f;
            }
            if (isGoal(goal, GoalIds.TAIL_CHASE)) {
                modifier = 2.0f;
            }
            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE)) {
                modifier = 0.3f;
            }
        } else if (age == PetContext.AgeCategory.MATURE) {
            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE)) {
                modifier = 1.5f;
            }
            if (isGoal(goal, GoalIds.TAIL_CHASE)) {
                modifier = 0.6f;
            }
        }

        if (goal.category() == GoalDefinition.Category.SOCIAL && mobAgeProfile != null && mobAgeProfile.hasBabies()) {
            if (age == PetContext.AgeCategory.YOUNG && mobAgeProfile.babyFriendlyCount() > 0) {
                modifier *= 1.2f;
            }
            if (age != PetContext.AgeCategory.YOUNG && mobAgeProfile.babyHostileCount() > 0) {
                modifier *= 0.65f;
            }
        }

        return new SignalResult(modifier, modifier, null);
    }

    @Override
    public EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.STATE_DATA, ContextSlice.CROWD);
    }

    private static boolean isGoal(GoalDefinition goal, Identifier id) {
        return goal != null && goal.id().equals(id);
    }
}
