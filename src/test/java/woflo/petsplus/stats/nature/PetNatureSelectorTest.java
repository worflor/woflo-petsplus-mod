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
import woflo.petsplus.stats.nature.astrology.AstrologySignDefinition;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            null,
            null,
            environment
        );
        MobEntity mob = Mockito.mock(MobEntity.class);
        when(mob.getRandom()).thenReturn(Random.create(12345));

        Identifier selected = PetNatureSelector.selectNature(mob, context);
        assertEquals(AstrologyRegistry.LUNARIS_NATURE_ID, selected);
    }

    @Test
    void lunarisAllowsBrightOpenSkyNights() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            10
        );
        PetBreedEvent.BirthContext context = new PetBreedEvent.BirthContext(
            25L * 24000L,
            17000L,
            Identifier.of("minecraft", "overworld"),
            false,
            false,
            false,
            false,
            1,
            1,
            false,
            true,
            true,
            null,
            null,
            environment
        );
        Identifier selected = PetNatureSelector.selectNature(Random.create(42), context);
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
            null,
            null,
            environment
        );
        Identifier aries = AstrologyRegistry.resolveSign(ariesContext, 5);
        assertEquals(Identifier.of("petsplus", "lunaris/aries"), aries);

        PetBreedEvent.BirthContext aquariusContext = new PetBreedEvent.BirthContext(
            25L * 24000L,
            17000L,
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
            null,
            null,
            environment
        );
        Identifier aquarius = AstrologyRegistry.resolveSign(aquariusContext, 7);
        assertEquals(Identifier.of("petsplus", "lunaris/aquarius"), aquarius);
    }

    @Test
    void lunarisPhaseNightSlotsIgnoreDaytimeContexts() {
        AstrologySignDefinition aries = AstrologyRegistry.get(Identifier.of("petsplus", "lunaris/aries"));
        assertNotNull(aries);

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

        AstrologySignDefinition.AstrologyContext context = new AstrologySignDefinition.AstrologyContext(
            Identifier.of("minecraft", "overworld"),
            5,
            82,
            6000L,
            false,
            true,
            false,
            false,
            true,
            0,
            0,
            environment
        );

        assertNull(AstrologySignDefinition.classifyNightPeriod(6000L));
        assertFalse(aries.matches(context));
    }

    @Test
    void lunarisPhaseNightSlotsCoverAllCombinations() {
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

        long[] times = {13000L, 17000L, 21000L};
        AstrologySignDefinition.NightPeriod[] periods = {
            AstrologySignDefinition.NightPeriod.EARLY_NIGHT,
            AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT,
            AstrologySignDefinition.NightPeriod.LATE_NIGHT
        };

        Set<String> covered = new HashSet<>();
        long baseDay = 200L;

        for (int phase = 0; phase < 8; phase++) {
            for (int i = 0; i < times.length; i++) {
                long timeOfDay = times[i];
                AstrologySignDefinition.NightPeriod expectedPeriod = periods[i];
                assertEquals(expectedPeriod, AstrologySignDefinition.classifyNightPeriod(timeOfDay));

                long worldTime = (baseDay + phase * times.length + i) * 24000L;
                PetBreedEvent.BirthContext context = new PetBreedEvent.BirthContext(
                    worldTime,
                    timeOfDay,
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
                    null,
                    null,
                    environment
                );

                Identifier signId = AstrologyRegistry.resolveSign(context, phase);
                assertNotNull(signId, "Expected Lunaris sign for moon phase " + phase + " at " + expectedPeriod);

                AstrologySignDefinition definition = AstrologyRegistry.get(signId);
                assertNotNull(definition, "Definition should exist for " + signId);

                Set<AstrologySignDefinition.NightPeriod> window = definition.phaseNightWindows().get(phase);
                assertNotNull(window, "Sign " + signId + " must declare moon phase " + phase);
                assertTrue(window.contains(expectedPeriod),
                    "Sign " + signId + " must cover " + expectedPeriod + " for moon phase " + phase);

                covered.add(phase + ":" + expectedPeriod.name());
            }
        }

        assertEquals(24, covered.size(), "All moon phase/night-period combinations should be mapped");
    }

    @Test
    void lunarisPhaseNightSlotsIncludeDuskAndDawnWindows() {
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

        long duskTime = 12500L;
        assertEquals(AstrologySignDefinition.NightPeriod.EARLY_NIGHT,
            AstrologySignDefinition.classifyNightPeriod(duskTime));

        PetBreedEvent.BirthContext duskContext = new PetBreedEvent.BirthContext(
            24000L,
            duskTime,
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
            null,
            null,
            environment
        );

        int duskPhase = 5;
        Identifier duskSignId = AstrologyRegistry.resolveSign(duskContext, duskPhase);
        assertNotNull(duskSignId, "Expected Lunaris sign to resolve during dusk window");

        AstrologySignDefinition duskDefinition = AstrologyRegistry.get(duskSignId);
        assertNotNull(duskDefinition);
        Set<AstrologySignDefinition.NightPeriod> duskWindow = duskDefinition.phaseNightWindows().get(duskPhase);
        assertNotNull(duskWindow, "Resolved sign must declare moon phase " + duskPhase);
        assertTrue(duskWindow.contains(AstrologySignDefinition.NightPeriod.EARLY_NIGHT),
            "Resolved sign must cover dusk as EARLY_NIGHT for phase " + duskPhase);

        long dawnTime = 23500L;
        assertEquals(AstrologySignDefinition.NightPeriod.LATE_NIGHT,
            AstrologySignDefinition.classifyNightPeriod(dawnTime));

        PetBreedEvent.BirthContext dawnContext = new PetBreedEvent.BirthContext(
            48000L,
            dawnTime,
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
            null,
            null,
            environment
        );

        int dawnPhase = 0;
        Identifier dawnSignId = AstrologyRegistry.resolveSign(dawnContext, dawnPhase);
        assertNotNull(dawnSignId, "Expected Lunaris sign to resolve during dawn window");

        AstrologySignDefinition dawnDefinition = AstrologyRegistry.get(dawnSignId);
        assertNotNull(dawnDefinition);
        Set<AstrologySignDefinition.NightPeriod> dawnWindow = dawnDefinition.phaseNightWindows().get(dawnPhase);
        assertNotNull(dawnWindow, "Resolved sign must declare moon phase " + dawnPhase);
        assertTrue(dawnWindow.contains(AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            "Resolved sign must cover dawn as LATE_NIGHT for phase " + dawnPhase);
    }

    @Test
    void lunarisThunderstormsRouteThroughNightSlots() {
        PetBreedEvent.BirthContext.Environment environment = environment(
            BiomeKeys.PLAINS.getValue(),
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            12
        );

        long middayTime = 6000L;
        PetBreedEvent.BirthContext clearDayContext = new PetBreedEvent.BirthContext(
            96L * 24000L,
            middayTime,
            Identifier.of("minecraft", "overworld"),
            false,
            true,
            false,
            false,
            0,
            0,
            false,
            true,
            true,
            null,
            null,
            environment
        );

        assertTrue(clearDayContext.isDaytime(), "Test precondition: context should represent daytime");
        int thunderPhase = 3;
        Identifier clearDaySign = AstrologyRegistry.resolveSign(clearDayContext, thunderPhase);
        assertNotNull(clearDaySign, "Registry should still return a fallback sign for unmatched contexts");

        AstrologySignDefinition clearDayDefinition = AstrologyRegistry.get(clearDaySign);
        assertNotNull(clearDayDefinition);

        int clearDayOfYear = (int) ((clearDayContext.getWorldTime() / 24000L) % 360L);
        AstrologySignDefinition.AstrologyContext clearDayAstrologyContext = new AstrologySignDefinition.AstrologyContext(
            clearDayContext.getDimensionId(),
            thunderPhase,
            clearDayOfYear,
            clearDayContext.getTimeOfDay(),
            clearDayContext.isIndoors(),
            clearDayContext.isDaytime(),
            clearDayContext.isRaining(),
            clearDayContext.isThundering(),
            clearDayContext.getEnvironment() != null && clearDayContext.getEnvironment().hasOpenSky(),
            clearDayContext.getNearbyPlayerCount(),
            clearDayContext.getNearbyPetCount(),
            clearDayContext.getEnvironment()
        );

        assertFalse(clearDayDefinition.matches(clearDayAstrologyContext),
            "Non-thunder daytime contexts should fail the Lunaris night window checks");

        PetBreedEvent.BirthContext thunderContext = new PetBreedEvent.BirthContext(
            96L * 24000L,
            middayTime,
            Identifier.of("minecraft", "overworld"),
            false,
            true,
            false,
            true,
            0,
            0,
            false,
            true,
            true,
            null,
            null,
            environment
        );

        assertTrue(thunderContext.isDaytime(), "Test precondition: thunder context should still be daytime");
        Identifier thunderSignId = AstrologyRegistry.resolveSign(thunderContext, thunderPhase);
        assertNotNull(thunderSignId, "Expected Lunaris sign to resolve during thunderstorms regardless of daylight");

        AstrologySignDefinition thunderDefinition = AstrologyRegistry.get(thunderSignId);
        assertNotNull(thunderDefinition);
        Set<AstrologySignDefinition.NightPeriod> thunderWindow = thunderDefinition.phaseNightWindows().get(thunderPhase);
        assertNotNull(thunderWindow, "Resolved sign must declare moon phase " + thunderPhase);
        assertTrue(thunderWindow.contains(AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            "Thunder contexts should map to the fallback LATE_NIGHT period for moon phase " + thunderPhase);
    }

    @Test
    void displayTitlesUseConfiguredEpithet() {
        String capricornTitle = AstrologyRegistry.getDisplayTitle(Identifier.of("petsplus", "lunaris/capricorn"));
        assertEquals("Lunaris Apex", capricornTitle);

        String aquariusTitle = AstrologyRegistry.getDisplayTitle(Identifier.of("petsplus", "lunaris/aquarius"));
        assertEquals("Cascade Lunaris", aquariusTitle);
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
        return environment(
            biomeId,
            hasOpenSky,
            hasCozyBlocks,
            hasLushFoliage,
            nearMajorStructure,
            hasArchaeologySite,
            nearTrialChamber,
            hasCherryBloom,
            hasActiveRedstone,
            10
        );
    }

    private static PetBreedEvent.BirthContext.Environment environment(Identifier biomeId,
                                                                      boolean hasOpenSky,
                                                                      boolean hasCozyBlocks,
                                                                      boolean hasLushFoliage,
                                                                      boolean nearMajorStructure,
                                                                      boolean hasArchaeologySite,
                                                                      boolean nearTrialChamber,
                                                                      boolean hasCherryBloom,
                                                                      boolean hasActiveRedstone,
                                                                      int skyLightLevel) {
        return new PetBreedEvent.BirthContext.Environment(
            BlockPos.ORIGIN,
            biomeId,
            0.8f,
            false,
            false,
            false,
            false,
            false,
            skyLightLevel,
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
            null,
            null,
            environment
        );
    }
}
