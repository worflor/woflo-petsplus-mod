package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.BiomeKeys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.stats.nature.PetNatureSelector;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class PetNatureSelectorTest {

    @Test
    void ceramicCanSelectWithArchaeologySignals() {
        // Desert with archaeology site and trial chamber - ceramic should be selectable
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.DESERT.getValue(),
            false,
            false,
            false,
            false,
            true,  // hasArchaeologySite
            true,  // nearTrialChamber
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        
        // Run multiple trials to check if ceramic can be selected
        boolean foundCeramic = false;
        for (int seed = 0; seed < 50; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            if (Identifier.of("petsplus", "ceramic").equals(selected)) {
                foundCeramic = true;
                break;
            }
        }
        
        assertTrue(foundCeramic, "ceramic should be selectable with archaeology signals in desert");
    }

    @Test
    void blossomCanSelectInCherryGroveConditions() {
        // Cherry grove with cherry bloom - blossom should be one of the possible selections
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.CHERRY_GROVE.getValue(),
            true,  // hasOpenSky - required for blossom
            false,
            false,
            false,
            false,
            false,
            true,  // hasCherryBloom - required for blossom
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, true, false, false, 1, 1);
        
        // Run multiple trials with different seeds to check if blossom appears
        boolean foundBlossom = false;
        for (int seed = 0; seed < 50; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            if (Identifier.of("petsplus", "blossom").equals(selected)) {
                foundBlossom = true;
                break;
            }
        }
        
        assertTrue(foundBlossom, "blossom should be selectable in cherry grove with cherry bloom conditions");
    }

    @Test
    void clockworkCanSelectWithActiveRedstone() {
        // Indoor/cozy environment with active redstone - clockwork should be selectable
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            true,  // hasCozyBlocks - required for clockwork
            false,
            false,
            false,
            false,
            false,
            true   // hasActiveRedstone - required for clockwork
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        
        // Run multiple trials to check if clockwork can be selected
        boolean foundClockwork = false;
        for (int seed = 0; seed < 50; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            if (Identifier.of("petsplus", "clockwork").equals(selected)) {
                foundClockwork = true;
                break;
            }
        }
        
        assertTrue(foundClockwork, "clockwork should be selectable with active redstone in cozy environment");
    }

    @Test
    void ceramicRequiresSupportiveContext() {
        // Archaeology site alone without proper biome/conditions shouldn't guarantee ceramic
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            false,
            false,
            false,
            true,  // hasArchaeologySite but missing other conditions
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, false, false, 1, 1);
        
        // Verify ceramic doesn't appear across multiple seeds (insufficient conditions)
        for (int seed = 0; seed < 20; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            assertFalse(
                Identifier.of("petsplus", "ceramic").equals(selected),
                "ceramic should not select without supportive context (seed " + seed + ")"
            );
        }
    }

    @Test
    void blossomDoesNotSelectDuringStorms() {
        // Cherry grove with bloom but stormy weather - blossom requires clear weather
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.CHERRY_GROVE.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            true,  // hasCherryBloom
            false
        );
        PetBreedEvent.BirthContext context = context(environment, false, false, true, true, 1, 1);  // raining + thundering
        
        // Verify blossom doesn't appear across multiple seeds (storms block it)
        for (int seed = 0; seed < 20; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            assertFalse(
                Identifier.of("petsplus", "blossom").equals(selected),
                "blossom should not select during storms (seed " + seed + ")"
            );
        }
    }

    @Test
    void clockworkRequiresActiveNetworks() {
        // Cozy blocks without active redstone - clockwork requires active redstone
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            false,
            true,  // hasCozyBlocks but no active redstone
            false,
            false,
            false,
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext context = context(environment, true, false, false, false, 1, 1);
        
        // Verify clockwork doesn't appear across multiple seeds (no active redstone)
        for (int seed = 0; seed < 20; seed++) {
            MobEntity mob = Mockito.mock(MobEntity.class);
            when(mob.getRandom()).thenReturn(Random.create(seed));
            
            Identifier selected = PetNatureSelector.selectNature(mob, context);
            assertFalse(
                Identifier.of("petsplus", "clockwork").equals(selected),
                "clockwork should not select without active redstone (seed " + seed + ")"
            );
        }
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

    @Test
    void lunarisSelectsUnderOpenNightSky() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext context = new PetBreedEvent.BirthContext(
            80L * 24000L,
            13000L,
            Identifier.of("minecraft", "overworld"),
            false,
            false,
            false,
            false,
            0,
            0,
            true,
            true,
            true,
            environment
        );
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create(12345));

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertEquals(AstrologyRegistry.LUNARIS_NATURE_ID, selected);
    }

    @Test
    void resolveSignUsesDayOfYear() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );
        PetBreedEvent.BirthContext ariesContext = new PetBreedEvent.BirthContext(
            82L * 24000L,
            13000L,
            Identifier.of("minecraft", "overworld"),
            false,
            false,
            false,
            false,
            0,
            0,
            true,
            true,
            true,
            environment
        );
        Identifier aries = AstrologyRegistry.resolveSign(ariesContext, 0);
        assertEquals(Identifier.of("petsplus", "lunaris/aries"), aries);

        PetBreedEvent.BirthContext aquariusContext = new PetBreedEvent.BirthContext(
            25L * 24000L,
            13000L,
            Identifier.of("minecraft", "overworld"),
            false,
            false,
            false,
            false,
            0,
            0,
            true,
            true,
            true,
            environment
        );
        Identifier aquarius = AstrologyRegistry.resolveSign(aquariusContext, 0);
        assertEquals(Identifier.of("petsplus", "lunaris/aquarius"), aquarius);
    }

    @Test
    void displayTitlesUseConfiguredEpithet() {
        String capricornTitle = AstrologyRegistry.getDisplayTitle(Identifier.of("petsplus", "lunaris/capricorn"));
        assertEquals("Lunaris Aegis", capricornTitle);

        String aquariusTitle = AstrologyRegistry.getDisplayTitle(Identifier.of("petsplus", "lunaris/aquarius"));
        assertEquals("Astra-Lunaris", aquariusTitle);
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
            hasActiveRedstone,
            false,
            false,
            0,
            0,
            0,
            0
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
