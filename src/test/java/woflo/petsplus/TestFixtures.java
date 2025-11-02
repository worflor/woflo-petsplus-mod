package woflo.petsplus;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;

/**
 * Reusable test fixtures and builders for creating test objects.
 * Leverages Java 21 features for cleaner, more maintainable test data.
 */
public final class TestFixtures {
    private TestFixtures() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final AtomicLong TICK_COUNTER = new AtomicLong(0);

    // =================================================================
    // WORLD MOCKING - Enhanced with World.class API knowledge
    // =================================================================

    /**
     * Creates a mock ServerWorld with configurable time.
     * Enhanced to mock additional World API methods based on decompiled World.class.
     */
    public static ServerWorld mockWorld(long initialTick) {
        ServerWorld world = mock(ServerWorld.class);
        
        // Time and day/night cycle
        when(world.getTime()).thenReturn(initialTick);
        when(world.getTimeOfDay()).thenReturn(initialTick % 24000L);
        when(world.isClient()).thenReturn(false);
        
        // World properties from World.class
        when(world.isDay()).thenAnswer(inv -> (initialTick % 24000L) < 12000L);
        when(world.isNight()).thenAnswer(inv -> (initialTick % 24000L) >= 12000L);
        
        // Difficulty
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        
        // Registry key (OVERWORLD by default)
        RegistryKey<World> overworldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld"));
        when(world.getRegistryKey()).thenReturn(overworldKey);
        
        // Random - return a real Random instance for tests that need it
        Random random = Random.create(initialTick);
        when(world.getRandom()).thenReturn(random);
        
        // Sea level and spawn point
        when(world.getSeaLevel()).thenReturn(64);
        when(world.getBottomY()).thenReturn(-64);
        // Note: getTopY() requires Heightmap.Type and BlockPos parameters, not mocking here
        
        // Debug world check
        when(world.isDebugWorld()).thenReturn(false);
        
        return world;
    }

    /**
     * Creates a mock ServerWorld that automatically increments time.
     * Enhanced with dynamic day/night cycle calculation.
     */
    public static ServerWorld mockDynamicWorld() {
        ServerWorld world = mock(ServerWorld.class);
        
        // Dynamic time
        when(world.getTime()).thenAnswer(invocation -> TICK_COUNTER.get());
        when(world.getTimeOfDay()).thenAnswer(invocation -> TICK_COUNTER.get() % 24000L);
        when(world.isClient()).thenReturn(false);
        
        // Dynamic day/night based on tick counter
        when(world.isDay()).thenAnswer(inv -> (TICK_COUNTER.get() % 24000L) < 12000L);
        when(world.isNight()).thenAnswer(inv -> (TICK_COUNTER.get() % 24000L) >= 12000L);
        
        // Difficulty
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        
        // Registry key
        RegistryKey<World> overworldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld"));
        when(world.getRegistryKey()).thenReturn(overworldKey);
        
        // Random with seed based on current tick
        when(world.getRandom()).thenAnswer(inv -> Random.create(TICK_COUNTER.get()));
        
        // Height limits
        when(world.getSeaLevel()).thenReturn(64);
        when(world.getBottomY()).thenReturn(-64);
        
        when(world.isDebugWorld()).thenReturn(false);
        
        return world;
    }

    /**
     * Creates a mock world configured for specific dimension (Overworld, Nether, End).
     */
    public static ServerWorld mockWorldForDimension(String dimensionId, long initialTick) {
        ServerWorld world = mockWorld(initialTick);
        
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla(dimensionId));
        when(world.getRegistryKey()).thenReturn(key);
        
        // Dimension-specific settings
        switch (dimensionId) {
            case "the_nether" -> {
                when(world.getSeaLevel()).thenReturn(32);
                when(world.getBottomY()).thenReturn(0);
            }
            case "the_end" -> {
                when(world.getSeaLevel()).thenReturn(0);
                when(world.getBottomY()).thenReturn(0);
            }
        }
        
        return world;
    }

    /**
     * Creates a mock world at a specific time of day for testing day/night mechanics.
     * 
     * @param timeOfDay 0-11999 for day, 12000-23999 for night
     */
    public static ServerWorld mockWorldAtTime(long timeOfDay) {
        long normalizedTime = timeOfDay % 24000L;
        return mockWorld(normalizedTime);
    }

    /**
     * Advances the dynamic world time by the specified ticks.
     */
    public static void advanceTicks(long ticks) {
        TICK_COUNTER.addAndGet(ticks);
    }

    /**
     * Resets the dynamic world time to zero.
     */
    public static void resetTicks() {
        TICK_COUNTER.set(0);
    }

    /**
     * Sets the dynamic world to a specific tick count.
     */
    public static void setTick(long tick) {
        TICK_COUNTER.set(tick);
    }

    /**
     * Advances time to the next day cycle (adds remaining ticks to reach 0 of next day).
     */
    public static void advanceToNextDay() {
        long currentTime = TICK_COUNTER.get() % 24000L;
        long ticksToNextDay = 24000L - currentTime;
        TICK_COUNTER.addAndGet(ticksToNextDay);
    }

    /**
     * Advances time to next night (12000 ticks in day).
     */
    public static void advanceToNight() {
        long currentTime = TICK_COUNTER.get() % 24000L;
        if (currentTime < 12000L) {
            // Currently day, advance to night
            TICK_COUNTER.addAndGet(12000L - currentTime);
        }
        // Already night, do nothing
    }

    /**
     * Advances time to next day (back to 0).
     */
    public static void advanceToDay() {
        long currentTime = TICK_COUNTER.get() % 24000L;
        if (currentTime >= 12000L) {
            // Currently night, advance to day
            TICK_COUNTER.addAndGet(24000L - currentTime);
        }
        // Already day, do nothing
    }

    // =================================================================
    // ENTITY MOCKING - Enhanced with World API integration
    // =================================================================

    /**
     * Creates a mock MobEntity with minimal required setup.
     */
    public static MobEntity mockPet(ServerWorld world) {
        return mockPet(world, UUID.randomUUID());
    }

    /**
     * Creates a mock MobEntity with specified UUID.
     */
    public static MobEntity mockPet(ServerWorld world, UUID uuid) {
        MobEntity pet = mock(MobEntity.class);
        when(pet.getUuid()).thenReturn(uuid);
        when(pet.getUuidAsString()).thenReturn(uuid.toString());
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.isAlive()).thenReturn(true);
        when(pet.isRemoved()).thenReturn(false);
        when(pet.getWidth()).thenReturn(0.6f);
        when(pet.getHeight()).thenReturn(1.8f);
        lenient().when(pet.isBaby()).thenReturn(false);
        
        // Position mocking - default spawn point
        BlockPos defaultPos = new BlockPos(0, 64, 0);
        when(pet.getBlockPos()).thenReturn(defaultPos);
        when(pet.getX()).thenReturn(0.0);
        when(pet.getY()).thenReturn(64.0);
        when(pet.getZ()).thenReturn(0.0);
        
        return pet;
    }

    /**
     * Creates a mock MobEntity at a specific position.
     */
    public static MobEntity mockPetAtPosition(ServerWorld world, UUID uuid, BlockPos pos) {
        MobEntity pet = mockPet(world, uuid);
        when(pet.getBlockPos()).thenReturn(pos);
        when(pet.getX()).thenReturn((double) pos.getX());
        when(pet.getY()).thenReturn((double) pos.getY());
        when(pet.getZ()).thenReturn((double) pos.getZ());
        return pet;
    }

    /**
     * Creates a mock PlayerEntity.
     */
    public static PlayerEntity mockPlayer(ServerWorld world) {
        return mockPlayer(world, UUID.randomUUID());
    }

    /**
     * Creates a mock PlayerEntity with specified UUID.
     */
    public static PlayerEntity mockPlayer(ServerWorld world, UUID uuid) {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getUuid()).thenReturn(uuid);
        when(player.getUuidAsString()).thenReturn(uuid.toString());
        when(player.getEntityWorld()).thenReturn(world);
        when(player.isAlive()).thenReturn(true);
        when(player.isSpectator()).thenReturn(false);
        
        // Position mocking - default spawn point
        BlockPos defaultPos = new BlockPos(0, 64, 0);
        when(player.getBlockPos()).thenReturn(defaultPos);
        when(player.getX()).thenReturn(0.0);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(0.0);
        
        return player;
    }

    /**
     * Creates a mock PlayerEntity at a specific position.
     */
    public static PlayerEntity mockPlayerAtPosition(ServerWorld world, UUID uuid, BlockPos pos) {
        PlayerEntity player = mockPlayer(world, uuid);
        when(player.getBlockPos()).thenReturn(pos);
        when(player.getX()).thenReturn((double) pos.getX());
        when(player.getY()).thenReturn((double) pos.getY());
        when(player.getZ()).thenReturn((double) pos.getZ());
        return player;
    }

    // =================================================================
    // BUILDERS AND TEST DATA
    // =================================================================

    /**
     * Builder for creating EmotionDelta test data.
     */
    public static EmotionDeltaBuilder emotionDelta(PetComponent.Emotion emotion) {
        return new EmotionDeltaBuilder(emotion);
    }

    public static class EmotionDeltaBuilder {
        private final PetComponent.Emotion emotion;
        private float amount = 0.5f;

        private EmotionDeltaBuilder(PetComponent.Emotion emotion) {
            this.emotion = emotion;
        }

        public EmotionDeltaBuilder withAmount(float amount) {
            this.amount = amount;
            return this;
        }

        public EmotionDeltaBuilder weak() {
            this.amount = 0.15f;
            return this;
        }

        public EmotionDeltaBuilder moderate() {
            this.amount = 0.5f;
            return this;
        }

        public EmotionDeltaBuilder strong() {
            this.amount = 0.85f;
            return this;
        }

        public PetComponent.EmotionDelta build() {
            return new PetComponent.EmotionDelta(emotion, amount);
        }
    }

    /**
     * Creates a minimal valid NbtCompound for testing NBT operations.
     */
    public static NbtCompound minimalPetNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("role", "petsplus:guardian");
        nbt.putBoolean("perched", false);
        nbt.putLong("lastAttackTick", 0L);
        return nbt;
    }

    /**
     * Standard set of test emotions for comprehensive testing.
     */
    public static PetComponent.Emotion[] standardEmotions() {
        return new PetComponent.Emotion[]{
            PetComponent.Emotion.CHEERFUL,
            PetComponent.Emotion.ANGST,
            PetComponent.Emotion.CURIOUS,
            PetComponent.Emotion.SAUDADE,
            PetComponent.Emotion.PROTECTIVE,
            PetComponent.Emotion.FOCUSED
        };
    }

    /**
     * Negative emotions (should persist longer).
     */
    public static PetComponent.Emotion[] negativeEmotions() {
        return new PetComponent.Emotion[]{
            PetComponent.Emotion.ANGST,
            PetComponent.Emotion.FOREBODING,
            PetComponent.Emotion.FRUSTRATION,
            PetComponent.Emotion.REGRET,
            PetComponent.Emotion.WORRIED
        };
    }

    /**
     * Positive emotions (should decay faster).
     */
    public static PetComponent.Emotion[] positiveEmotions() {
        return new PetComponent.Emotion[]{
            PetComponent.Emotion.CHEERFUL,
            PetComponent.Emotion.KEFI,
            PetComponent.Emotion.HOPEFUL,
            PetComponent.Emotion.RELIEF,
            PetComponent.Emotion.CONTENT
        };
    }

    /**
     * Ultra-rare emotions for edge case testing.
     */
    public static PetComponent.Emotion[] ultraRareEmotions() {
        return new PetComponent.Emotion[]{
            PetComponent.Emotion.MALEVOLENCE,
            PetComponent.Emotion.ECHOED_RESONANCE,
            PetComponent.Emotion.ARCANE_OVERFLOW,
            PetComponent.Emotion.PACK_SPIRIT,
            PetComponent.Emotion.MINING_REVERIE
        };
    }

    /**
     * Standard test moods covering different categories.
     */
    public static PetComponent.Mood[] standardMoods() {
        return new PetComponent.Mood[]{
            PetComponent.Mood.HAPPY,
            PetComponent.Mood.CALM,
            PetComponent.Mood.PROTECTIVE,
            PetComponent.Mood.CURIOUS,
            PetComponent.Mood.SAUDADE
        };
    }

    /**
     * Ultra-rare moods for edge case testing.
     */
    public static PetComponent.Mood[] ultraRareMoods() {
        return new PetComponent.Mood[]{
            PetComponent.Mood.ECHOED_RESONANCE,
            PetComponent.Mood.ARCANE_OVERFLOW,
            PetComponent.Mood.PACK_SPIRIT,
            PetComponent.Mood.MALEVOLENT_ECLIPSE,
            PetComponent.Mood.MINING_REVERIE
        };
    }

    /**
     * Creates a deterministic UUID for reproducible tests.
     */
    public static UUID deterministicUuid(long seed) {
        return new UUID(seed, seed);
    }

    // =================================================================
    // POSITION AND WORLD INTERACTION HELPERS
    // =================================================================

    /**
     * Creates a BlockPos at spawn level (y=64).
     */
    public static BlockPos spawnPos(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    /**
     * Creates a BlockPos at sea level.
     */
    public static BlockPos seaLevelPos(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    /**
     * Creates a BlockPos underground (below sea level).
     */
    public static BlockPos undergroundPos(int x, int z) {
        return new BlockPos(x, 32, z);
    }

    /**
     * Creates a BlockPos high in the sky.
     */
    public static BlockPos skyPos(int x, int z) {
        return new BlockPos(x, 200, z);
    }

    /**
     * Calculates distance between two positions (Euclidean).
     */
    public static double distance(BlockPos pos1, BlockPos pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Checks if two positions are within render distance (typically 8-16 chunks).
     */
    public static boolean withinRenderDistance(BlockPos pos1, BlockPos pos2, int chunkRadius) {
        int dx = Math.abs(pos1.getX() - pos2.getX());
        int dz = Math.abs(pos1.getZ() - pos2.getZ());
        int maxDist = chunkRadius * 16; // Convert chunks to blocks
        return dx <= maxDist && dz <= maxDist;
    }

    // =================================================================
    // TIME AND TICK UTILITIES
    // =================================================================

    /**
     * Converts real-world time to Minecraft ticks (20 ticks = 1 second).
     */
    public static long secondsToTicks(double seconds) {
        return (long) (seconds * 20.0);
    }

    /**
     * Converts Minecraft ticks to real-world seconds.
     */
    public static double ticksToSeconds(long ticks) {
        return ticks / 20.0;
    }

    /**
     * Converts minutes to ticks.
     */
    public static long minutesToTicks(double minutes) {
        return (long) (minutes * 60.0 * 20.0);
    }

    /**
     * One full Minecraft day in ticks (20 minutes real-time).
     */
    public static long oneDay() {
        return 24000L;
    }

    /**
     * Length of Minecraft day phase in ticks (10 minutes real-time).
     */
    public static long dayLength() {
        return 12000L;
    }

    /**
     * Length of Minecraft night phase in ticks (10 minutes real-time).
     */
    public static long nightLength() {
        return 12000L;
    }

    /**
     * Checks if a given tick time is during day (based on World.isDay() logic).
     */
    public static boolean isTickDuringDay(long tick) {
        return (tick % 24000L) < 12000L;
    }

    /**
     * Checks if a given tick time is during night.
     */
    public static boolean isTickDuringNight(long tick) {
        return (tick % 24000L) >= 12000L;
    }
}
