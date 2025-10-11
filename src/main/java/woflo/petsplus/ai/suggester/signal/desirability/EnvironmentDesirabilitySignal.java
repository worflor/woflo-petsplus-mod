package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
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

        if (goal == GoalRegistry.STARGAZING) {
            if (isNight) {
                modifier *= 3.0f;
            } else {
                return new SignalResult(0.0f, 0.0f, Map.of("reason", "daytime"));
            }
        }

        if (isNight) {
            if (goal == GoalRegistry.SIT_SPHINX_POSE || goal == GoalRegistry.FLOAT_IDLE) {
                modifier *= 1.3f;
            }
            if (goal == GoalRegistry.PERCH_HOP) {
                modifier *= 1.4f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.7f;
            }
            if (goal == GoalRegistry.OWNER_ORBIT || goal == GoalRegistry.LEAN_AGAINST_OWNER) {
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
            if (goal == GoalRegistry.LEAN_AGAINST_OWNER) {
                modifier *= 1.5f;
            }
            if (goal.category() == GoalDefinition.Category.PLAY) {
                modifier *= 0.7f;
            }
            if (goal == GoalRegistry.WATER_SPLASH || goal == GoalRegistry.BUBBLE_PLAY) {
                modifier *= 1.3f;
            }
            if (goal == GoalRegistry.SIT_SPHINX_POSE || goal == GoalRegistry.CIRCLE_SPOT) {
                modifier *= 1.4f;
            }
        }

        if (isThundering) {
            if (goal == GoalRegistry.LEAN_AGAINST_OWNER || goal == GoalRegistry.OWNER_ORBIT) {
                modifier *= 2.0f;
            }
            if (goal == GoalRegistry.PERCH_ON_SHOULDER) {
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
                            if (goal == GoalRegistry.CASUAL_WANDER || goal == GoalRegistry.PURPOSEFUL_PATROL) {
                                modifier *= 1.4f;
                            }
                        } else {
                            if (goal == GoalRegistry.LEAN_AGAINST_OWNER || goal == GoalRegistry.CIRCLE_SPOT) {
                                modifier *= 1.5f;
                            }
                        }
                    }

                    if (value.contains("ocean") || value.contains("river") || value.contains("beach")) {
                        if (goal == GoalRegistry.WATER_CRUISE || goal == GoalRegistry.DIVE_PLAY ||
                            goal == GoalRegistry.WATER_SPLASH || goal == GoalRegistry.ORBIT_SWIM) {
                            modifier *= 1.5f;
                        }
                    }

                    if (value.contains("desert") || value.contains("savanna") || value.contains("badlands")) {
                        if (ctx.isDaytime()) {
                            if (goal.category() == GoalDefinition.Category.PLAY) {
                                modifier *= 0.6f;
                            }
                            if (goal == GoalRegistry.SIT_SPHINX_POSE || goal == GoalRegistry.FLOAT_IDLE) {
                                modifier *= 1.6f;
                            }
                        }
                    }

                    if (value.contains("forest") || value.contains("jungle") || value.contains("grove")) {
                        if (goal == GoalRegistry.INVESTIGATE_BLOCK || goal == GoalRegistry.SNIFF_GROUND ||
                            goal == GoalRegistry.SCENT_TRAIL_FOLLOW) {
                            modifier *= 1.4f;
                        }
                        if (goal == GoalRegistry.PERCH_HOP) {
                            modifier *= 1.3f;
                        }
                    }

                    if (value.contains("mountain") || value.contains("peak") || value.contains("hill")) {
                        if (goal == GoalRegistry.PERCH_HOP || goal == GoalRegistry.AERIAL_PATROL) {
                            modifier *= 1.5f;
                        }
                        if (goal == GoalRegistry.PURPOSEFUL_PATROL) {
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
}
