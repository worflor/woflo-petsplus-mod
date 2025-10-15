package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.social.BedtimeCompanionGoal;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.HashMap;
import java.util.Map;

public class EnvironmentDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/environment");
    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private static final String ZOOMIES_UNTIL_KEY = "zoomies_until";

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        float modifier = 1.0f;
        Map<String, Object> trace = new HashMap<>();
        MobEntity mob = ctx.mob();
        PetComponent component = ctx.component();

        boolean mobWet = isEntityWet(mob);
        boolean mobExposed = isEntityExposedToRain(mob);
        boolean mobShelteredDry = !mobWet && !mobExposed;

        if (component != null) {
            Long zoomiesUntil = component.getStateData(ZOOMIES_UNTIL_KEY, Long.class);
            if (zoomiesUntil != null && ctx.worldTime() < zoomiesUntil) {
                if (goal.category() == GoalDefinition.Category.WANDER || goal.category() == GoalDefinition.Category.PLAY) {
                    if (mobShelteredDry) {
                        modifier *= 3.0f; // ZOOM!
                        trace.put("zoomies_boost", 3.0f);
                    }
                }
            } else if (zoomiesUntil != null) {
                component.clearStateData(ZOOMIES_UNTIL_KEY);
            }
        }

        PlayerEntity owner = ctx.getOwner();
        boolean ownerSleeping = owner != null && owner.isSleeping();
        boolean isNight = !ctx.isDaytime();

        if (ownerSleeping) {
            if (isGoal(goal, GoalIds.BEDTIME_COMPANION)) {
                float affinity = BedtimeCompanionGoal.calculateSnuggleAffinity(mob, ctx, owner);
                float social = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
                float bondWeight = 1.55f + affinity * 0.9f;
                float socialWeight = 0.85f + social * 0.6f;
                if (component != null && component.isDormant()) {
                    socialWeight *= 0.92f;
                }
                if (ctx.hasMoodInBlend(PetComponent.Mood.CALM, 0.24f) || ctx.hasMoodInBlend(PetComponent.Mood.BONDED, 0.2f)) {
                    bondWeight *= 1.12f;
                }
                if (ctx.hasMoodInBlend(PetComponent.Mood.RESTLESS, 0.22f) || ctx.hasEmotionAbove(PetComponent.Emotion.WORRIED, 0.22f)) {
                    bondWeight *= 0.68f;
                }
                if (affinity < 0.3f) {
                    bondWeight *= MathHelper.clamp(0.7f + affinity * 0.9f, 0.45f, 1.1f);
                }
                float boost = MathHelper.clamp(bondWeight * socialWeight, 0.35f, 3.85f);
                modifier *= boost;
                trace.put("owner_sleeping_companion", boost);
                trace.put("snuggle_affinity", affinity);
            } else {
                if (goal.category() == GoalDefinition.Category.PLAY) {
                    modifier *= 0.55f;
                    trace.put("owner_sleeping_play", 0.55f);
                } else if (goal.category() == GoalDefinition.Category.WANDER) {
                    modifier *= 0.7f;
                    trace.put("owner_sleeping_wander", 0.7f);
                }
            }
        }

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
            boolean ownerNearby = ctx.ownerNearby();
            boolean ownerWet = isEntityWet(owner);
            boolean ownerExposed = isEntityExposedToRain(owner);
            boolean ownerDrySheltered = owner != null && !ownerWet && !ownerExposed;
            boolean ownerRisky = ownerWet || ownerExposed;
            boolean ownerSubmerged = owner != null && owner.isSubmergedInWater();

            if (mobWet && component != null) {
                component.setStateData(LAST_WET_TICK_KEY, ctx.worldTime());
            }

            boolean isFeline = isFelineLike(component, mob);
            boolean fearful = component != null && (
                ctx.hasEmotionAbove(PetComponent.Emotion.WORRIED, 0.35f)
                    || ctx.hasEmotionAbove(PetComponent.Emotion.STARTLE, 0.32f)
                    || ctx.hasEmotionAbove(PetComponent.Emotion.FOREBODING, 0.35f)
            );

            float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
            float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

            boolean relaxDryFocus = ownerNearby && ownerRisky && !isFeline && !fearful;
            boolean reinforceDryFocus = ownerNearby && ownerDrySheltered;
            boolean shelterWatcher = ownerNearby && ownerRisky && mobShelteredDry && (isFeline || fearful);

            if (goal.category() == GoalDefinition.Category.WANDER) {
                float wanderMultiplier = relaxDryFocus
                    ? 0.82f + 0.15f * momentum
                    : 0.58f;
                if (reinforceDryFocus) {
                    wanderMultiplier *= 0.7f + 0.1f * (1.0f - socialCharge);
                }
                if (shelterWatcher) {
                    wanderMultiplier *= 0.55f;
                }
                if (mobWet && relaxDryFocus) {
                    wanderMultiplier *= 1.1f;
                }
                wanderMultiplier = MathHelper.clamp(wanderMultiplier, 0.2f, 1.25f);
                modifier *= wanderMultiplier;
                trace.put("rain_wander", wanderMultiplier);
            }

            if (goal.category() == GoalDefinition.Category.PLAY) {
                float playMultiplier = relaxDryFocus
                    ? 0.88f + 0.12f * momentum
                    : 0.68f;
                if (reinforceDryFocus) {
                    playMultiplier *= 0.65f;
                }
                if (shelterWatcher) {
                    playMultiplier *= 0.5f;
                }
                if (mobWet && relaxDryFocus) {
                    playMultiplier *= 1.08f;
                }
                playMultiplier = MathHelper.clamp(playMultiplier, 0.25f, 1.4f);
                modifier *= playMultiplier;
                trace.put("rain_play", playMultiplier);
            }

            if (isGoal(goal, GoalIds.LEAN_AGAINST_OWNER)
                || isGoal(goal, GoalIds.OWNER_ORBIT)
                || isGoal(goal, GoalIds.CROUCH_APPROACH_RESPONSE)
                || isGoal(goal, GoalIds.PARALLEL_PLAY)) {
                float closenessMultiplier = relaxDryFocus
                    ? 1.35f + 0.25f * (momentum * 0.5f)
                    : 1.45f;
                if (reinforceDryFocus) {
                    closenessMultiplier *= 1.2f + 0.2f * (1.0f - socialCharge);
                }
                if (ownerRisky && (isFeline || fearful)) {
                    closenessMultiplier *= 0.65f;
                }
                closenessMultiplier = MathHelper.clamp(closenessMultiplier, 0.45f, 2.2f);
                modifier *= closenessMultiplier;
                trace.put("rain_closeness", closenessMultiplier);
            }

            if (isGoal(goal, GoalIds.WATER_SPLASH) || isGoal(goal, GoalIds.BUBBLE_PLAY) || isGoal(goal, GoalIds.PUDDLE_PAW)) {
                float splashMultiplier = relaxDryFocus
                    ? 1.45f + 0.2f * momentum
                    : 1.25f;
                if (reinforceDryFocus || shelterWatcher) {
                    splashMultiplier *= 0.65f;
                }
                if (relaxDryFocus && ownerSubmerged) {
                    splashMultiplier *= 1.5f;
                }
                splashMultiplier = MathHelper.clamp(splashMultiplier, 0.3f, 1.8f);
                modifier *= splashMultiplier;
                trace.put("rain_splash", splashMultiplier);
            }

            if (isGoal(goal, GoalIds.SIT_SPHINX_POSE) || isGoal(goal, GoalIds.CIRCLE_SPOT) || isGoal(goal, GoalIds.HEARTH_SETTLE)) {
                float shelterMultiplier;
                if (reinforceDryFocus) {
                    shelterMultiplier = 1.55f + 0.25f * (1.0f - socialCharge);
                } else if (shelterWatcher) {
                    shelterMultiplier = 1.35f + 0.15f * (1.0f - socialCharge);
                } else if (relaxDryFocus) {
                    shelterMultiplier = 0.92f;
                } else {
                    shelterMultiplier = 1.38f;
                }
                shelterMultiplier = MathHelper.clamp(shelterMultiplier, 0.6f, 1.95f);
                modifier *= shelterMultiplier;
                trace.put("rain_shelter", shelterMultiplier);
            }

            if (shelterWatcher && isGoal(goal, GoalIds.PERK_EARS_SCAN)) {
                float watchMultiplier = 1.25f + 0.2f * (1.0f - socialCharge);
                watchMultiplier = MathHelper.clamp(watchMultiplier, 0.75f, 1.8f);
                modifier *= watchMultiplier;
                trace.put("rain_watch_owner", watchMultiplier);
            }
            
            if (isFeline && mobShelteredDry && isGoal(goal, GoalIds.PUDDLE_PAW) && isNearPuddle(mob)) {
                if (mob.getRandom().nextFloat() < 0.15f) { // 15% chance
                    modifier *= 2.0f; // High desire when it triggers
                    trace.put("curious_cat_puddle", 2.0f);
                }
            }

            trace.put("ownerWet", ownerWet);
            trace.put("ownerExposed", ownerExposed);
            trace.put("ownerSubmerged", ownerSubmerged);
            trace.put("petWet", mobWet);
            trace.put("petShelteredDry", mobShelteredDry);
            trace.put("relaxDryFocus", relaxDryFocus);
            trace.put("reinforceDryFocus", reinforceDryFocus);
            trace.put("shelterWatcher", shelterWatcher);
        }

        if (wasRecentlyWet(ctx) && mobShelteredDry) {
            if (isGoal(goal, GoalIds.HEARTH_SETTLE) || isGoal(goal, GoalIds.LEAN_AGAINST_OWNER)) {
                modifier *= 1.8f; // Strong desire for warmth
                trace.put("seeking_warmth", 1.8f);
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

    private boolean wasRecentlyWet(PetContext ctx) {
        if (ctx.component() == null) {
            return false;
        }
        Long lastWetTick = ctx.component().getStateData(LAST_WET_TICK_KEY, Long.class);
        if (lastWetTick == null) {
            return false;
        }
        // 2 minutes (2400 ticks) window
        long ticksSinceWet = Math.max(0L, ctx.worldTime() - lastWetTick);
        return ticksSinceWet < 2400L;
    }

    private static boolean isNearPuddle(MobEntity mob) {
        if (mob == null || mob.getEntityWorld() == null) {
            return false;
        }
        World world = mob.getEntityWorld();
        BlockPos mobPos = mob.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(mobPos.add(-3, -1, -3), mobPos.add(3, 0, 3))) {
            if (world.isWater(pos) && !world.isWater(pos.up())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGoal(GoalDefinition goal, Identifier id) {
        return goal != null && goal.id().equals(id);
    }

    private static boolean isEntityWet(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.isSubmergedInWater() || entity.isTouchingWater() || entity.isTouchingWaterOrRain();
    }

    private static boolean isEntityExposedToRain(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        var world = entity.getEntityWorld();
        if (world == null || (!world.isRaining() && !world.isThundering())) {
            return false;
        }
        return world.isSkyVisible(entity.getBlockPos());
    }

    private static boolean isFelineLike(@Nullable PetComponent component, @Nullable MobEntity mob) {
        if (component != null) {
            if (component.hasSpeciesTag(PetsplusEntityTypeTags.FELINE_LIKE)) {
                return true;
            }
            if (component.matchesSpeciesKeyword("cat", "feline", "lynx", "ocelot")) {
                return true;
            }
        }
        return mob != null && mob.getType().isIn(PetsplusEntityTypeTags.FELINE_LIKE);
    }
}
