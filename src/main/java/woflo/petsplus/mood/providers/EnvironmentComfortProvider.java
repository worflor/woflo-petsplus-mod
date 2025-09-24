package woflo.petsplus.mood.providers;

import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.state.PetComponent;

/**
 * Lightweight comfort/safety provider:
 * - Near owner and indoors at night → calm (BLISSFUL, QUERECIA)
 * - Next to campfire/bed → calm
 * - In rain/thunder without cover → slight discomfort (FOREBODING)
 */
public class EnvironmentComfortProvider implements EmotionProvider {
    @Override public String id() { return "env_comfort"; }
    @Override public int periodHintTicks() { return 40; } // ~2s

    @Override
    public void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api) {
        // Owner proximity
        var owner = comp.getOwner();
        if (owner != null && owner.isAlive()) {
            double d2 = pet.squaredDistanceTo(owner);
            if (d2 < 36) { // within 6 blocks
                api.pushEmotion(pet, PetComponent.Emotion.QUERECIA, 0.05f);
                applySocialBuffer(comp, pet, time, api);
            }
        }

        // Night and cover check: cheap overhead block test
        boolean isNight = world.getTimeOfDay() % 24000L > 13000L; // simple heuristic
        if (isNight) {
            BlockPos pos = pet.getBlockPos();
            boolean covered = !world.isSkyVisible(pos.up());
            if (covered) {
                api.pushEmotion(pet, PetComponent.Emotion.BLISSFUL, 0.03f);
            } else {
                api.pushEmotion(pet, PetComponent.Emotion.FOREBODING, 0.02f);
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

        // Rain discomfort if in rain and sky visible
        if (world.isRaining() && world.isSkyVisible(pet.getBlockPos().up())) {
            api.pushEmotion(pet, PetComponent.Emotion.FOREBODING, 0.02f);
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
}
