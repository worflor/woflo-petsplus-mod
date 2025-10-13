package woflo.petsplus.mood.providers;

import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.api.mood.ReactiveEmotionProvider;
import woflo.petsplus.mood.EmotionStimulusBus;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.state.PetComponent;

/**
 * Lightweight comfort/safety provider:
 * - Near owner and indoors at night → calm (CONTENT, QUERECIA)
 * - Next to campfire/bed → calm
 * - In rain/thunder without cover → slight discomfort (FOREBODING)
 */
public class EnvironmentComfortProvider implements EmotionProvider, ReactiveEmotionProvider {
    private EmotionStimulusBus.DispatchListener dispatchListener;
    @Override public String id() { return "env_comfort"; }
    @Override public int periodHintTicks() { return 80; } // Increased from 40 to 80 ticks (~4s instead of ~2s)

    @Override
    public void register(EmotionStimulusBus bus) {
        if (dispatchListener != null) {
            return;
        }
        dispatchListener = (pet, component, time) -> {
            if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
                return;
            }
            contribute(world, pet, component, time, MoodService.getInstance());
        };
        bus.addDispatchListener(dispatchListener);
    }

    @Override
    public void unregister(EmotionStimulusBus bus) {
        if (dispatchListener != null) {
            bus.removeDispatchListener(dispatchListener);
            dispatchListener = null;
        }
    }

    @Override
    public void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api) {
        // Owner proximity with fatigue
        var owner = comp.getOwner();
        if (owner != null && owner.isAlive()) {
            double d2 = pet.squaredDistanceTo(owner);
            if (d2 < 36) { // within 6 blocks
                float baseAmount = 0.02f;
                float fatigueMultiplier = calculateEmotionFatigue(comp, "proximity_querecia", time, baseAmount, 160); // 8s fatigue window
                if (fatigueMultiplier > 0.1f) { // Only trigger if not heavily fatigued
                    api.pushEmotion(pet, PetComponent.Emotion.QUERECIA, baseAmount * fatigueMultiplier);
                }
                applySocialBuffer(comp, pet, time, api);
            }
        }

        // Night and cover check: cheap overhead block test with fatigue
        boolean isNight = world.getTimeOfDay() % 24000L > 13000L; // simple heuristic
        if (isNight) {
            BlockPos pos = pet.getBlockPos();
            boolean covered = !world.isSkyVisible(pos.up());
            if (covered) {
                float baseAmount = 0.015f;
                float fatigueMultiplier = calculateEmotionFatigue(comp, "night_covered_content", time, baseAmount, 240); // 12s fatigue window
                if (fatigueMultiplier > 0.2f) { // Only trigger if not heavily fatigued
                    api.pushEmotion(pet, PetComponent.Emotion.CONTENT, baseAmount * fatigueMultiplier);
                }
            } else {
                float baseAmount = 0.02f;
                float fatigueMultiplier = calculateEmotionFatigue(comp, "night_exposed_foreboding", time, baseAmount, 180); // 9s fatigue window
                if (fatigueMultiplier > 0.15f) { // Only trigger if not heavily fatigued
                    api.pushEmotion(pet, PetComponent.Emotion.FOREBODING, baseAmount * fatigueMultiplier);
                }
            }
        }

        // Nearby cozy blocks: campfire, bed
        BlockPos base = pet.getBlockPos();
        for (BlockPos off : new BlockPos[]{base, base.down(), base.up(), base.north(), base.south(), base.east(), base.west()}) {
            var state = world.getBlockState(off);
            if (state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)) {
                api.pushEmotion(pet, PetComponent.Emotion.SOBREMESA, 0.04f);
                break;
            }
            if (state.getBlock().asItem() == Items.RED_BED || state.getBlock().asItem() == Items.WHITE_BED) {
                api.pushEmotion(pet, PetComponent.Emotion.SOBREMESA, 0.03f);
                break;
            }
        }

        // Rain discomfort if in rain and sky visible with fatigue
        if (world.isRaining() && world.isSkyVisible(pet.getBlockPos().up())) {
            float baseAmount = 0.02f;
            float fatigueMultiplier = calculateEmotionFatigue(comp, "rain_foreboding", time, baseAmount, 200); // 10s fatigue window
            if (fatigueMultiplier > 0.15f) { // Only trigger if not heavily fatigued
                api.pushEmotion(pet, PetComponent.Emotion.FOREBODING, baseAmount * fatigueMultiplier);
            }
        }
    }

    private void applySocialBuffer(PetComponent comp, MobEntity pet, long now, MoodAPI api) {
        boolean stressed = comp.hasMoodAbove(PetComponent.Mood.AFRAID, 0.25f)
            || comp.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.3f)
            || comp.hasMoodAbove(PetComponent.Mood.ANGRY, 0.3f);
        if (!stressed) {
            return;
        }

        long lastTick = comp.getStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, Long.class, 0L);
        if (now - lastTick < 60) {
            return; // leave a short window so we do not over-dampen
        }

        float resilience = comp.computeBondResilience(now);
        float calmDrain = Math.min(0.1f, 0.03f + 0.05f * resilience);
        api.pushEmotion(pet, PetComponent.Emotion.ANGST, -calmDrain);
        api.pushEmotion(pet, PetComponent.Emotion.STARTLE, -calmDrain * 0.6f);
        api.pushEmotion(pet, PetComponent.Emotion.FRUSTRATION, -calmDrain * 0.4f);

        float reassurance = 0.02f + 0.03f * resilience;
        api.pushEmotion(pet, PetComponent.Emotion.RELIEF, reassurance);
        api.pushEmotion(pet, PetComponent.Emotion.UBUNTU, reassurance * 0.6f);

        comp.setStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, now);
    }

    /**
     * Calculate emotion fatigue multiplier based on recent trigger frequency.
     * Returns a value from 0.1 to 1.0 where lower values indicate more fatigue.
     * 
     * @param comp Pet component for state storage
     * @param triggerKey Unique key for this trigger type
     * @param currentTime Current game time
     * @param baseAmount The base emotion amount being triggered
     * @param fatigueWindow Time window in ticks for fatigue calculation
     * @return Multiplier from 0.1 to 1.0
     */
    private static float calculateEmotionFatigue(PetComponent comp, String triggerKey, long currentTime, float baseAmount, int fatigueWindow) {
        String lastTickKey = "fatigue_" + triggerKey + "_last_tick";
        String intensityKey = "fatigue_" + triggerKey + "_intensity";
        String countKey = "fatigue_" + triggerKey + "_count";

        long lastTick = comp.getStateData(lastTickKey, Long.class, 0L);
        float lastIntensity = comp.getStateData(intensityKey, Float.class, 0f);
        int triggerCount = comp.getStateData(countKey, Integer.class, 0);

        // If outside fatigue window, reset counters
        if (currentTime - lastTick > fatigueWindow) {
            triggerCount = 0;
            lastIntensity = 0f;
        }

        // Increment trigger count and update intensity
        triggerCount++;
        float newIntensity = Math.max(lastIntensity * 0.8f, baseAmount); // Intensity fades but is boosted by new triggers

        // Calculate fatigue based on trigger frequency and intensity changes
        float frequencyFatigue = Math.max(0.1f, 1.0f - (triggerCount - 1) * 0.15f); // Each repeat reduces by 15%
        
        // If intensity increased significantly (25%+), reduce fatigue
        float intensityBoost = 1.0f;
        if (newIntensity > lastIntensity * 1.25f) {
            intensityBoost = Math.min(1.5f, 1.0f + (newIntensity - lastIntensity) * 2.0f);
        }

        float finalMultiplier = Math.min(1.0f, frequencyFatigue * intensityBoost);

        // Store updated state
        comp.setStateData(lastTickKey, currentTime);
        comp.setStateData(intensityKey, newIntensity);
        comp.setStateData(countKey, triggerCount);

        return finalMultiplier;
    }

    /**
     * Phase A scaffold; accepting samplingInterval without using it.
     * No behavior change; not referenced by existing logic yet.
     *
     * @param samplingIntervalTicks hint for future sampling interval in ticks
     */
    public static void setSamplingIntervalHint(int samplingIntervalTicks) {
        // Phase A scaffold; accepting samplingInterval without using it
        // no-op
    }

    /**
     * Phase A scaffold; accepting samplingInterval without using it.
     * No behavior change; not referenced by existing logic yet.
     *
     * @param samplingIntervalTicks caller-provided interval hint
     * @return the same samplingIntervalTicks value unchanged
     */
    public static int getSamplingIntervalHintOrDefault(int samplingIntervalTicks) {
        // Phase A scaffold; accepting samplingInterval without using it
        // preserve behavior by returning the provided value unchanged
        return samplingIntervalTicks;
    }
}

