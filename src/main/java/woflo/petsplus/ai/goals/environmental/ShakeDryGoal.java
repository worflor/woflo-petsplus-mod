package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.EnumSet;

public class ShakeDryGoal extends AdaptiveGoal {

    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private static final String ZOOMIES_UNTIL_KEY = "zoomies_until";
    private static final int SHAKE_DURATION = 40; // 2 seconds

    public ShakeDryGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SHAKE_DRY), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return false;
        }

        // Condition 1: Was recently wet
        Long lastWetTick = component.getStateData(LAST_WET_TICK_KEY, Long.class);
        if (lastWetTick == null || mob.getEntityWorld().getTime() - lastWetTick > 1200L) { // 1 minute window
            return false;
        }

        // Condition 2: Is now dry and sheltered
        if (isEntityWet(mob) || isEntityExposedToRain(mob)) {
            return false;
        }
        
        // Condition 3: Not a fish or water-based creature
        if (mob.getType().isIn(PetsplusEntityTypeTags.AQUATIC_LIKE)) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return getActiveTicks() < SHAKE_DURATION;
    }

    @Override
    protected void onStartGoal() {
        // Particles are handled per-tick
    }

    @Override
    protected void onStopGoal() {
        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return;
        }

        // Trigger "zoomies" for non-felines
        if (!isFelineLike(component, mob)) {
            long zoomiesDuration = 400L + mob.getRandom().nextInt(400); // 20-40 seconds
            component.setStateData(ZOOMIES_UNTIL_KEY, mob.getEntityWorld().getTime() + zoomiesDuration);
        }
        
        // Clear the recently wet tracker so this doesn't immediately re-trigger
        component.clearStateData(LAST_WET_TICK_KEY);
    }

    @Override
    protected void onTickGoal() {
        // Spawn water splash particles
        if (mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            float spread = 0.6f;
            serverWorld.spawnParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + mob.getHeight() * 0.5f, mob.getZ(), 10, mob.getWidth() * spread, mob.getHeight() * spread, mob.getWidth() * spread, 0.1);
        }
    }

    @Override
    protected float calculateEngagement() {
        return 0.5f; // Neutral engagement
    }
    
    @Override
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        return woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.PHYSICAL;
    }

    @Override
    protected float getActivityIntensity() {
        return 0.2f;
    }

    private static boolean isEntityWet(@Nullable Entity entity) {
        if (entity == null) return false;
        return entity.isSubmergedInWater() || entity.isTouchingWater() || entity.isTouchingWaterOrRain();
    }

    private static boolean isEntityExposedToRain(@Nullable Entity entity) {
        if (entity == null) return false;
        World world = entity.getEntityWorld();
        if (world == null || !world.isRaining()) return false;
        return world.isSkyVisible(entity.getBlockPos());
    }

    private static boolean isFelineLike(@Nullable PetComponent component, @Nullable MobEntity mob) {
        if (component != null) {
            if (component.hasSpeciesTag(PetsplusEntityTypeTags.FELINE_LIKE)) return true;
            if (component.matchesSpeciesKeyword("cat", "feline", "lynx", "ocelot")) return true;
        }
        return mob != null && mob.getType().isIn(PetsplusEntityTypeTags.FELINE_LIKE);
    }
}
