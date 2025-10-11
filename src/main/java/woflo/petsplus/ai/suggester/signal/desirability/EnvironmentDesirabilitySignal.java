package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.HashMap;
import java.util.Map;

public class EnvironmentDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/environment");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        float modifier = 1.0f;
        Map<String, Object> trace = new HashMap<>();
        MobEntity mob = ctx.mob();

        boolean isNight = !ctx.isDaytime();

        if (isGoal(goal, GoalIds.STARGAZING)) {
            if (isNight) {
                modifier *= 3.0f;
            } else {
                return new SignalResult(0.0f, 0.0f, Map.of("reason", "daytime"));
            }
        }

        if (isNight) {
            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE) || isGoal(goal, GoalIds.FLOAT_IDLE)) {
                modifier *= 1.3f;
            }
            if (isGoal(goal, GoalIds.PERCH_HOP)) {
                modifier *= 1.4f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.7f;
            }
            if (isGoal(goal, GoalIds.OWNER_ORBIT) || isGoal(goal, GoalIds.LEAN_AGAINST_OWNER)) {
                modifier *= 1.2f;
            }
        }

        if (ctx.isDaytime()) {
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 1.2f;
            }
            if (goal.category() == GoalDefinition.Category.WANDER) {
                modifier *= 1.15f;
            }
        }

        boolean isRaining = mob != null && mob.getEntityWorld().isRaining();
        boolean isThundering = mob != null && mob.getEntityWorld().isThundering();

        if (isRaining) {
            if (goal.category() == GoalDefinition.Category.WANDER) {
                modifier *= 0.6f;
            }
            if (isGoal(goal, GoalIds.LEAN_AGAINST_OWNER)) {
                modifier *= 1.5f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.7f;
            }
            if (isGoal(goal, GoalIds.WATER_SPLASH) || isGoal(goal, GoalIds.BUBBLE_PLAY)) {
                modifier *= 1.3f;
            }
            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE) || isGoal(goal, GoalIds.CIRCLE_SPOT)) {
                modifier *= 1.4f;
            }
        }

        if (isThundering) {
            if (isGoal(goal, GoalIds.LEAN_AGAINST_OWNER) || isGoal(goal, GoalIds.OWNER_ORBIT)) {
                modifier *= 2.0f;
            }
            if (isGoal(goal, GoalIds.PERCH_ON_SHOULDER)) {
                modifier *= 2.2f;
            }
            if (goal.category() == GoalDefinition.Category.WANDER) {
                modifier *= 0.3f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.4f;
            }
        }

        if (mob != null) {
            try {
                var biome = mob.getEntityWorld().getBiome(ctx.currentPos());
                var biomeKey = biome.getKey();
                if (biomeKey.isPresent()) {
                    var key = biomeKey.get();
                    String value = key.getValue().toString();

                    if (value.contains("taiga") || value.contains("snow") || value.contains("ice") || value.contains("frozen")) {
                        if (ctx.isDaytime()) {
                            if (goal.category() == GoalDefinition.Category.WANDER) {
                                modifier *= 1.3f;
                            }
                            if (isGoal(goal, GoalIds.CASUAL_WANDER) || isGoal(goal, GoalIds.PURPOSEFUL_PATROL)) {
                                modifier *= 1.4f;
                            }
                        } else {
                            if (isGoal(goal, GoalIds.LEAN_AGAINST_OWNER) || isGoal(goal, GoalIds.CIRCLE_SPOT)) {
                                modifier *= 1.5f;
                            }
                        }
                    }

                    if (value.contains("ocean") || value.contains("river") || value.contains("beach")) {
                        if (isGoal(goal, GoalIds.WATER_CRUISE) || isGoal(goal, GoalIds.DIVE_PLAY) ||
                            isGoal(goal, GoalIds.WATER_SPLASH) || isGoal(goal, GoalIds.ORBIT_SWIM)) {
                            modifier *= 1.5f;
                        }
                    }

                    if (value.contains("desert") || value.contains("savanna") || value.contains("badlands")) {
                        if (ctx.isDaytime()) {
                            if (goal.category() == GoalDefinition.Category.PLAY) {
                                modifier *= 0.6f;
                            }
                            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE) || isGoal(goal, GoalIds.FLOAT_IDLE)) {
                                modifier *= 1.6f;
                            }
                        }
                    }

                    if (value.contains("forest") || value.contains("jungle") || value.contains("grove")) {
                        if (isGoal(goal, GoalIds.INVESTIGATE_BLOCK) || isGoal(goal, GoalIds.SNIFF_GROUND) ||
                            isGoal(goal, GoalIds.SCENT_TRAIL_FOLLOW)) {
                            modifier *= 1.4f;
                        }
                        if (isGoal(goal, GoalIds.PERCH_HOP)) {
                            modifier *= 1.3f;
                        }
                    }

                    if (value.contains("mountain") || value.contains("peak") || value.contains("hill")) {
                        if (isGoal(goal, GoalIds.PERCH_HOP) || isGoal(goal, GoalIds.AERIAL_PATROL)) {
                            modifier *= 1.5f;
                        }
                        if (isGoal(goal, GoalIds.PURPOSEFUL_PATROL)) {
                            modifier *= 1.3f;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        trace.put("finalMultiplier", modifier);
        return new SignalResult(modifier, modifier, trace);
    }

    private static boolean isGoal(GoalDefinition goal, Identifier id) {
        return goal != null && goal.id().equals(id);
    }
}
