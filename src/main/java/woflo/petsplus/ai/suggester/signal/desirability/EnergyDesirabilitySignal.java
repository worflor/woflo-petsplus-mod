package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.HashMap;
import java.util.Map;

public class EnergyDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/energy");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        BehaviouralEnergyProfile profile = ctx.behaviouralEnergyProfile();
        if (profile == null) {
            profile = BehaviouralEnergyProfile.neutral();
        }

        float momentum = profile.momentum();
        float socialCharge = profile.socialCharge();
        float physicalStamina = profile.physicalStamina();
        float mentalFocus = profile.mentalFocus();
        float modifier = goal.getEnergyBias(profile);

        if (momentum > 0.7f) {
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 1.3f;
            }
            if (isGoal(goal, GoalIds.AERIAL_ACROBATICS) || isGoal(goal, GoalIds.PARKOUR_CHALLENGE)) {
                modifier *= 1.5f;
            }
            if (goal.category() == GoalDefinition.Category.IDLE_QUIRK) {
                modifier *= 0.5f;
            }
        } else if (momentum < 0.3f) {
            if (goal.category() == GoalDefinition.Category.IDLE_QUIRK) {
                modifier *= 1.3f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.6f;
            }
            if (isGoal(goal, GoalIds.PARKOUR_CHALLENGE) || isGoal(goal, GoalIds.AERIAL_ACROBATICS)) {
                modifier *= 0.5f;
            }
        }

        if (socialCharge > 0.7f) {
            if (goal.category() == GoalDefinition.Category.SOCIAL) {
                modifier *= 1.4f;
            }
            if (isGoal(goal, GoalIds.PARALLEL_PLAY) || isGoal(goal, GoalIds.SHOW_AND_DROP)) {
                modifier *= 1.5f;
            }
        } else if (socialCharge < 0.3f) {
            if (goal.category() == GoalDefinition.Category.SOCIAL) {
                modifier *= 0.6f;
            }
            if (isGoal(goal, GoalIds.SHOW_AND_DROP)) {
                modifier *= 0.4f;
            }
        }

        if (physicalStamina > 0.7f) {
            if (goal.category() == GoalDefinition.Category.WANDER || goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 1.2f;
            }
        } else if (physicalStamina < 0.3f) {
            if (goal.category() == GoalDefinition.Category.WANDER || goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.6f;
            }
            if (goal.category() == GoalDefinition.Category.IDLE_QUIRK) {
                modifier *= 1.2f;
            }
        }

        if (goal.category() == GoalDefinition.Category.SPECIAL) {
            if (mentalFocus > 0.7f) {
                float clarity = MathHelper.clamp((mentalFocus - 0.7f) / 0.3f, 0f, 1f);
                modifier *= 1.0f + clarity * 0.35f;
            } else if (mentalFocus < 0.4f) {
                float scatter = MathHelper.clamp((0.4f - mentalFocus) / 0.4f, 0f, 1f);
                modifier *= MathHelper.lerp(scatter, 1.0f, 0.6f);
            }
        }

        Map<String, Object> trace = new HashMap<>();
        trace.put("momentum", momentum);
        trace.put("socialCharge", socialCharge);
        trace.put("physicalStamina", physicalStamina);
        trace.put("mentalFocus", mentalFocus);
        trace.put("finalMultiplier", modifier);
        return new SignalResult(modifier, modifier, trace);
    }

    private static boolean isGoal(GoalDefinition goal, Identifier id) {
        return goal != null && goal.id().equals(id);
    }
}
