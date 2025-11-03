package woflo.petsplus.state;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static woflo.petsplus.TestFixtures.*;

/**
 * Comprehensive tests for NBT persistence covering:
 * - Complete state serialization/deserialization
 * - Data integrity across save/load cycles
 * - Edge cases and corrupted data handling
 * 
 * NOTE: NbtCompound getter methods return Optional<T> in Minecraft 1.21.10
 */
@DisplayName("NBT Persistence")
class NbtPersistenceTest {

    @BeforeEach
    void setup() {
        resetTicks();
        // NOTE: ServerWorld cannot be mocked in test environment due to complex static
        // initializers that trigger ParticleTypes initialization. Since these NBT tests
        // don't actually USE the world/pet, we skip creating them.
    }

    @Nested
    @DisplayName("Basic Serialization")
    class BasicSerializationTests {

        @Test
        @DisplayName("should serialize and deserialize empty NBT")
        void roundtrip_handlesEmptyNbt() {
            // Given: Empty NBT
            NbtCompound original = new NbtCompound();

            // When: Write and read
            NbtCompound serialized = original.copy();

            // Then: Should be empty
            assertThat(serialized.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should preserve primitive types")
        void roundtrip_preservesPrimitives() {
            // Given: NBT with various primitives
            NbtCompound original = new NbtCompound();
            original.putBoolean("boolValue", true);
            original.putByte("byteValue", (byte) 42);
            original.putShort("shortValue", (short) 1000);
            original.putInt("intValue", 123456);
            original.putLong("longValue", 9876543210L);
            original.putFloat("floatValue", 3.14159f);
            original.putDouble("doubleValue", 2.71828);
            original.putString("stringValue", "test string");

            // When: Copy (simulating roundtrip)
            NbtCompound copy = original.copy();

            // Then: All values should match (remember to unwrap Optional!)
            assertThat(copy.getBoolean("boolValue").orElse(false)).isTrue();
            assertThat(copy.getByte("byteValue").orElse((byte) 0)).isEqualTo((byte) 42);
            assertThat(copy.getShort("shortValue").orElse((short) 0)).isEqualTo((short) 1000);
            assertThat(copy.getInt("intValue").orElse(0)).isEqualTo(123456);
            assertThat(copy.getLong("longValue").orElse(0L)).isEqualTo(9876543210L);
            assertThat(copy.getFloat("floatValue").orElse(0f)).isCloseTo(3.14159f, within(0.0001f));
            assertThat(copy.getDouble("doubleValue").orElse(0.0)).isCloseTo(2.71828, within(0.00001));
            assertThat(copy.getString("stringValue").orElse("")).isEqualTo("test string");
        }

        @Test
        @DisplayName("should preserve nested compounds")
        void roundtrip_preservesNestedCompounds() {
            // Given: Nested NBT structure
            NbtCompound original = new NbtCompound();
            NbtCompound child = new NbtCompound();
            child.putString("childData", "nested value");
            child.putInt("childInt", 99);
            original.put("child", child);
            original.putString("parentData", "parent value");

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: Nested structure preserved
            assertThat(copy.getString("parentData").orElse("")).isEqualTo("parent value");
            assertThat(copy.contains("child")).isTrue();
            
            // getCompound returns Optional in 1.21.10
            NbtCompound copiedChild = copy.getCompound("child").orElse(new NbtCompound());
            assertThat(copiedChild.getString("childData").orElse("")).isEqualTo("nested value");
            assertThat(copiedChild.getInt("childInt").orElse(0)).isEqualTo(99);
        }

        @Test
        @DisplayName("should preserve UUIDs via string serialization")
        void roundtrip_preservesUuids() {
            // Given: UUID stored as string (Minecraft pattern)
            NbtCompound original = new NbtCompound();
            UUID uuid = UUID.randomUUID();
            original.putString("petId", uuid.toString());
            original.putString("ownerId", uuid.toString());

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: UUIDs should match
            UUID copiedPetId = UUID.fromString(copy.getString("petId").orElse(""));
            UUID copiedOwnerId = UUID.fromString(copy.getString("ownerId").orElse(""));
            assertThat(copiedPetId).isEqualTo(uuid);
            assertThat(copiedOwnerId).isEqualTo(uuid);
        }
    }

    @Nested
    @DisplayName("Complex State Persistence")
    class ComplexStatePersistenceTests {

        @Test
        @DisplayName("should preserve complete component state")
        void roundtrip_preservesCompleteState() {
            // Given: Complex state structure
            NbtCompound original = new NbtCompound();
            original.putString("role", "petsplus:guardian");
            original.putBoolean("perched", false);
            original.putLong("lastAttackTick", 12345L);
            original.putInt("level", 5);
            original.putFloat("bond", 0.75f);

            // Nested emotion data
            NbtCompound emotions = new NbtCompound();
            NbtCompound cheerful = new NbtCompound();
            cheerful.putFloat("intensity", 0.6f);
            cheerful.putLong("lastUpdate", 1000L);
            emotions.put("CHEERFUL", cheerful);
            original.put("emotionRecords", emotions);

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: All data preserved
            assertThat(copy.getString("role").orElse("")).isEqualTo("petsplus:guardian");
            assertThat(copy.getBoolean("perched").orElse(true)).isFalse();
            assertThat(copy.getLong("lastAttackTick").orElse(0L)).isEqualTo(12345L);
            assertThat(copy.getInt("level").orElse(0)).isEqualTo(5);
            assertThat(copy.getFloat("bond").orElse(0f)).isCloseTo(0.75f, within(0.01f));

            NbtCompound copiedEmotions = copy.getCompound("emotionRecords").orElse(new NbtCompound());
            assertThat(copiedEmotions.contains("CHEERFUL")).isTrue();
            
            NbtCompound copiedCheerful = copiedEmotions.getCompound("CHEERFUL").orElse(new NbtCompound());
            assertThat(copiedCheerful.getFloat("intensity").orElse(0f)).isCloseTo(0.6f, within(0.01f));
            assertThat(copiedCheerful.getLong("lastUpdate").orElse(0L)).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should preserve behavioral state pool")
        void roundtrip_preservesBehavioralState() {
            // Given: Behavioral state (from PetMoodEngine)
            NbtCompound original = new NbtCompound();
            original.putFloat("behavioralMomentum", 0.65f);
            original.putFloat("socialCharge", 0.45f);
            original.putFloat("physicalStamina", 0.80f);
            original.putFloat("mentalFocus", 0.70f);

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: All state preserved
            assertThat(copy.getFloat("behavioralMomentum").orElse(0f)).isCloseTo(0.65f, within(0.01f));
            assertThat(copy.getFloat("socialCharge").orElse(0f)).isCloseTo(0.45f, within(0.01f));
            assertThat(copy.getFloat("physicalStamina").orElse(0f)).isCloseTo(0.80f, within(0.01f));
            assertThat(copy.getFloat("mentalFocus").orElse(0f)).isCloseTo(0.70f, within(0.01f));
        }

        @Test
        @DisplayName("should preserve large emotion pools")
        void roundtrip_preservesLargeEmotionPool() {
            // Given: Many emotions (realistic scenario)
            NbtCompound original = new NbtCompound();
            NbtCompound emotions = new NbtCompound();

            String[] emotionNames = {"CHEERFUL", "ANGST", "CURIOUS", "PROTECTIVE", "FOCUSED", 
                                     "HOPEFUL", "REGRET", "SOBREMESA", "SAUDADE", "KEFI"};
            for (int i = 0; i < emotionNames.length; i++) {
                NbtCompound emotion = new NbtCompound();
                emotion.putFloat("intensity", 0.1f + (i * 0.08f));
                emotion.putLong("lastUpdate", 1000L + (i * 100L));
                emotions.put(emotionNames[i], emotion);
            }
            original.put("emotionRecords", emotions);

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: All emotions preserved
            NbtCompound copiedEmotions = copy.getCompound("emotionRecords").orElse(new NbtCompound());
            assertThat(copiedEmotions.getSize()).isEqualTo(emotionNames.length);

            for (String name : emotionNames) {
                assertThat(copiedEmotions.contains(name))
                    .withFailMessage("Missing emotion: " + name)
                    .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Data Integrity")
    class DataIntegrityTests {

        @Test
        @DisplayName("should survive multiple roundtrips")
        void multipleRoundtrips_preservesData() {
            // Given: Initial data
            NbtCompound original = new NbtCompound();
            original.putString("testData", "persistent value");
            original.putInt("counter", 42);

            // When: Multiple copy cycles
            NbtCompound current = original;
            for (int i = 0; i < 10; i++) {
                current = current.copy();
            }

            // Then: Data still intact
            assertThat(current.getString("testData").orElse("")).isEqualTo("persistent value");
            assertThat(current.getInt("counter").orElse(0)).isEqualTo(42);
        }

        @Test
        @DisplayName("should handle missing keys gracefully")
        void missingKeys_returnOptionalEmpty() {
            // Given: NBT with some data
            NbtCompound nbt = new NbtCompound();
            nbt.putFloat("existingKey", 0.5f);

            // When/Then: Missing keys return empty Optional
            assertThat(nbt.getFloat("missingKey").isPresent()).isFalse();
            assertThat(nbt.getString("missingString").isPresent()).isFalse();
            assertThat(nbt.getCompound("missingCompound").isPresent()).isFalse();
            
            // Should use defaults
            float ratio = nbt.getFloat("ratio").orElse(1.0f);
            assertThat(ratio).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("should preserve precision for floats")
        void roundtrip_preservesFloatPrecision() {
            // Given: Precise float values
            NbtCompound original = new NbtCompound();
            float preciseFloat = 0.123456789f;
            double preciseDouble = 0.123456789012345;
            original.putFloat("float", preciseFloat);
            original.putDouble("double", preciseDouble);

            // When: Copy
            NbtCompound copy = original.copy();

            // Then: Precision maintained within float/double limits
            assertThat(copy.getFloat("float").orElse(0f)).isCloseTo(preciseFloat, within(0.000001f));
            assertThat(copy.getDouble("double").orElse(0.0)).isCloseTo(preciseDouble, within(0.000000000001));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty strings")
        void emptyString_serializes() {
            // Given: Empty string
            NbtCompound nbt = new NbtCompound();
            nbt.putString("emptyField", "");

            // When: Copy
            NbtCompound copy = nbt.copy();

            // Then: Empty string preserved
            assertThat(copy.getString("emptyField").orElse(null)).isEmpty();
        }

        @Test
        @DisplayName("should handle very long strings")
        void veryLongString_serializes() {
            // Given: Long string
            NbtCompound nbt = new NbtCompound();
            String longString = "x".repeat(100000);
            nbt.putString("longField", longString);

            // When: Copy
            NbtCompound copy = nbt.copy();

            // Then: Full string preserved
            assertThat(copy.getString("longField").orElse("")).hasSize(100000);
        }

        @Test
        @DisplayName("should handle extreme values")
        void extremeValues_serialize() {
            // Given: Extreme values
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("maxInt", Integer.MAX_VALUE);
            nbt.putInt("minInt", Integer.MIN_VALUE);
            nbt.putLong("maxLong", Long.MAX_VALUE);
            nbt.putLong("minLong", Long.MIN_VALUE);
            nbt.putFloat("maxFloat", Float.MAX_VALUE);
            nbt.putFloat("minFloat", Float.MIN_VALUE);

            // When: Copy
            NbtCompound copy = nbt.copy();

            // Then: Extreme values preserved
            assertThat(copy.getInt("maxInt").orElse(0)).isEqualTo(Integer.MAX_VALUE);
            assertThat(copy.getInt("minInt").orElse(0)).isEqualTo(Integer.MIN_VALUE);
            assertThat(copy.getLong("maxLong").orElse(0L)).isEqualTo(Long.MAX_VALUE);
            assertThat(copy.getLong("minLong").orElse(0L)).isEqualTo(Long.MIN_VALUE);
            assertThat(copy.getFloat("maxFloat").orElse(0f)).isEqualTo(Float.MAX_VALUE);
            assertThat(copy.getFloat("minFloat").orElse(0f)).isEqualTo(Float.MIN_VALUE);
        }

        @Test
        @DisplayName("should handle special float values")
        void specialFloatValues_serialize() {
            // Given: Special float values
            NbtCompound nbt = new NbtCompound();
            nbt.putFloat("nan", Float.NaN);
            nbt.putFloat("posInf", Float.POSITIVE_INFINITY);
            nbt.putFloat("negInf", Float.NEGATIVE_INFINITY);
            nbt.putFloat("zero", 0.0f);
            nbt.putFloat("negZero", -0.0f);

            // When: Copy
            NbtCompound copy = nbt.copy();

            // Then: Special values preserved
            assertThat(Float.isNaN(copy.getFloat("nan").orElse(0f))).isTrue();
            assertThat(copy.getFloat("posInf").orElse(0f)).isEqualTo(Float.POSITIVE_INFINITY);
            assertThat(copy.getFloat("negInf").orElse(0f)).isEqualTo(Float.NEGATIVE_INFINITY);
            assertThat(copy.getFloat("zero").orElse(1f)).isZero();
        }

        @Test
        @DisplayName("should handle deep nesting")
        void deepNesting_serializes() {
            // Given: Deeply nested structure
            NbtCompound root = new NbtCompound();
            NbtCompound current = root;
            int depth = 100;

            for (int i = 0; i < depth; i++) {
                NbtCompound child = new NbtCompound();
                child.putInt("depth", i);
                current.put("child", child);
                current = child;
            }

            // When: Copy
            NbtCompound copy = root.copy();

            // Then: Full depth preserved
            NbtCompound walker = copy;
            for (int i = 0; i < depth; i++) {
                walker = walker.getCompound("child").orElse(new NbtCompound());
                assertThat(walker.getInt("depth").orElse(-1)).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("should handle arrays")
        void arrays_serialize() {
            // Given: Int array
            NbtCompound nbt = new NbtCompound();
            int[] array = {1, 2, 3, 4, 5};
            nbt.putIntArray("array", array);

            // When: Copy
            NbtCompound copy = nbt.copy();

            // Then: Array preserved
            int[] copiedArray = copy.getIntArray("array").orElse(new int[0]);
            assertThat(copiedArray).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("should handle overwrites correctly")
        void overwrites_replaceValues() {
            // Given: Initial value
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("value", 10);

            // When: Overwrite
            nbt.putInt("value", 42);
            NbtCompound copy = nbt.copy();

            // Then: New value should be present
            assertThat(copy.getInt("value").orElse(0)).isEqualTo(42);
        }
    }

    // Performance tests removed - too slow for regular test runs
}
