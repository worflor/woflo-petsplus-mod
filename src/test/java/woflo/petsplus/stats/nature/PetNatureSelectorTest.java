package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.BiomeKeys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.api.event.PetBreedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class PetNatureSelectorTest {

    @Test
    void selectsCeramicWhenArchaeologySignalsAreStrong() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.DESERT.getValue(),
            false,
            false,
            false,
            false,
            true,
            true,
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertEquals(Identifier.of("petsplus", "ceramic"), selected);
    }

    @Test
    void selectsBlossomForCherryBloomSnapshots() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.CHERRY_GROVE.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            true,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertEquals(Identifier.of("petsplus", "blossom"), selected);
    }

    @Test
    void selectsClockworkWhenRedstoneContraptionsAreActive() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            true,
            false,
            false,
            false,
            false,
            false,
            true
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertEquals(Identifier.of("petsplus", "clockwork"), selected);
    }

    @Test
    void ceramicRequiresSupportiveContext() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertNotEquals(Identifier.of("petsplus", "ceramic"), selected);
    }

    @Test
    void blossomDoesNotSelectDuringStorms() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.CHERRY_GROVE.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            true,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, true, true, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertNotEquals(Identifier.of("petsplus", "blossom"), selected);
    }

    @Test
    void clockworkRequiresActiveNetworks() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            true,
            false,
            false,
            false,
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, true, false, false, false, 1, 1);
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create());

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertNotEquals(Identifier.of("petsplus", "clockwork"), selected);
    }

    @Test
    void redstoneTrackerDemandsMultiplePoweredNodes() {
        PetNatureSelector.RedstoneNetworkTracker tracker = new PetNatureSelector.RedstoneNetworkTracker();
        tracker.record(true, false, true); // dust
        tracker.record(false, true, true); // lever

        assertFalse(tracker.isActive(), "single component setups should not count as active networks");

        tracker.record(true, false, true); // piston
        assertTrue(tracker.isActive(), "multi-node powered loops should register as active redstone");
    }

    private static PetBreedEvent.BirthContext.Environment environment(Identifier biomeId,
                                                                      boolean hasOpenSky,
                                                                      boolean hasCozyBlocks,
                                                                      boolean hasLushFoliage,
                                                                      boolean nearMajorStructure,
                                                                      boolean hasArchaeologySite,
                                                                      boolean nearTrialChamber,
                                                                      boolean hasCherryBloom,
                                                                      boolean hasActiveRedstone) {
        return new PetBreedEvent.BirthContext.Environment(
            BlockPos.ORIGIN,
            biomeId,
            0.8f,
            false,
            false,
            false,
            false,
            false,
            10,
            64,
            hasOpenSky,
            hasCozyBlocks,
            false,
            hasLushFoliage,
            false,
            false,
            false,
            false,
            nearMajorStructure,
            hasArchaeologySite,
            nearTrialChamber,
            hasCherryBloom,
            hasActiveRedstone
        );
    }

    private static PetBreedEvent.BirthContext context(PetBreedEvent.BirthContext.Environment environment,
                                                      boolean indoors,
                                                      boolean daytime,
                                                      boolean raining,
                                                      boolean thundering,
                                                      int nearbyPlayers,
                                                      int nearbyPets) {
        return new PetBreedEvent.BirthContext(
            0L,
            0L,
            Identifier.of("minecraft", "overworld"),
            indoors,
            daytime,
            raining,
            thundering,
            nearbyPlayers,
            nearbyPets,
            false,
            true,
            true,
            environment
        );
    }
}
