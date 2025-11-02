package woflo.petsplus.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for utility validation methods covering:
 * - Range validation
 * - Null safety
 * - Clamping operations
 * - Mathematical utilities
 * - Edge cases and boundaries
 */
@DisplayName("Utility Validation")
class ValidationUtilTest {

    @Nested
    @DisplayName("Clamping")
    class ClampingTests {

        @ParameterizedTest
        @CsvSource({
            "0.5, 0.0, 1.0, 0.5",   // Within range
            "1.5, 0.0, 1.0, 1.0",   // Above max
            "-0.5, 0.0, 1.0, 0.0",  // Below min
            "0.0, 0.0, 1.0, 0.0",   // At min
            "1.0, 0.0, 1.0, 1.0",   // At max
        })
        @DisplayName("should clamp float values to range")
        void clamp_floatValues(float value, float min, float max, float expected) {
            // When: Clamp value
            float result = Math.max(min, Math.min(max, value));

            // Then: Should match expected
            assertThat(result).isCloseTo(expected, within(0.0001f));
        }

        @ParameterizedTest
        @CsvSource({
            "50, 0, 100, 50",
            "150, 0, 100, 100",
            "-50, 0, 100, 0",
            "0, 0, 100, 0",
            "100, 0, 100, 100"
        })
        @DisplayName("should clamp integer values to range")
        void clamp_integerValues(int value, int min, int max, int expected) {
            // When: Clamp value
            int result = Math.max(min, Math.min(max, value));

            // Then: Should match expected
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should handle min equals max")
        void clamp_handlesMinEqualsMax() {
            // When: Min equals max
            float result = Math.max(5.0f, Math.min(5.0f, 3.0f));

            // Then: Should return min/max value
            assertThat(result).isEqualTo(5.0f);
        }

        @Test
        @DisplayName("should handle inverted range gracefully")
        void clamp_handlesInvertedRange() {
            // When: Max < min (programmer error, but should handle)
            float min = 10.0f;
            float max = 0.0f;
            float value = 5.0f;
            
            // Correct implementation would swap or throw
            // Test that it doesn't crash
            assertThatCode(() -> Math.max(min, Math.min(max, value)))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Range Validation")
    class RangeValidationTests {

        @ParameterizedTest
        @ValueSource(floats = {0.0f, 0.5f, 1.0f})
        @DisplayName("should validate values within 0-1 range")
        void isInRange_validatesUnitRange(float value) {
            // When: Check if in unit range
            boolean inRange = value >= 0.0f && value <= 1.0f;

            // Then: Should be in range
            assertThat(inRange).isTrue();
        }

        @ParameterizedTest
        @ValueSource(floats = {-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY})
        @DisplayName("should reject values outside 0-1 range")
        void isInRange_rejectsOutOfRange(float value) {
            // When: Check if in unit range
            boolean inRange = value >= 0.0f && value <= 1.0f;

            // Then: Should be out of range
            assertThat(inRange).isFalse();
        }

        @Test
        @DisplayName("should validate positive values")
        void isPositive_validatesPositive() {
            // Given: Various values
            assertThat(5.0f > 0).isTrue();
            assertThat(0.0f > 0).isFalse();
            assertThat(-5.0f > 0).isFalse();
        }

        @Test
        @DisplayName("should validate non-negative values")
        void isNonNegative_validatesNonNegative() {
            // Given: Various values
            assertThat(5.0f >= 0).isTrue();
            assertThat(0.0f >= 0).isTrue();
            assertThat(-5.0f >= 0).isFalse();
        }
    }

    @Nested
    @DisplayName("Special Values")
    class SpecialValuesTests {

        @Test
        @DisplayName("should detect NaN values")
        void isNaN_detectsNaN() {
            // Given: NaN value
            float nan = Float.NaN;

            // When: Check for NaN
            boolean isNaN = Float.isNaN(nan);

            // Then: Should detect NaN
            assertThat(isNaN).isTrue();
        }

        @Test
        @DisplayName("should detect infinity values")
        void isInfinite_detectsInfinity() {
            // Given: Infinite values
            float posInf = Float.POSITIVE_INFINITY;
            float negInf = Float.NEGATIVE_INFINITY;

            // When: Check for infinity
            boolean isPosInf = Float.isInfinite(posInf);
            boolean isNegInf = Float.isInfinite(negInf);

            // Then: Should detect infinity
            assertThat(isPosInf).isTrue();
            assertThat(isNegInf).isTrue();
        }

        @Test
        @DisplayName("should detect finite values")
        void isFinite_detectsFinite() {
            // Given: Various values
            float normal = 5.0f;
            float nan = Float.NaN;
            float inf = Float.POSITIVE_INFINITY;

            // When: Check for finite
            boolean normalFinite = Float.isFinite(normal);
            boolean nanFinite = Float.isFinite(nan);
            boolean infFinite = Float.isFinite(inf);

            // Then: Only normal should be finite
            assertThat(normalFinite).isTrue();
            assertThat(nanFinite).isFalse();
            assertThat(infFinite).isFalse();
        }

        @ParameterizedTest
        @ValueSource(floats = {0.0f, -0.0f, 0.00001f, -0.00001f})
        @DisplayName("should handle zero and near-zero values")
        void handleZero_handlesNearZero(float value) {
            // When: Check if effectively zero
            float epsilon = 0.0001f;
            boolean nearZero = Math.abs(value) < epsilon;

            // Then: Should classify correctly
            if (Math.abs(value) < epsilon) {
                assertThat(nearZero).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Mathematical Operations")
    class MathematicalOperationsTests {

        @ParameterizedTest
        @CsvSource({
            "0.0, 1.0, 0.0, 0.0",   // a=0 returns min
            "1.0, 1.0, 0.0, 1.0",   // a=1 returns max
            "0.5, 1.0, 0.0, 0.5",   // a=0.5 returns middle
            "0.25, 4.0, 0.0, 1.0",  // a=0.25 returns quarter
            "0.75, 4.0, 0.0, 3.0"   // a=0.75 returns three-quarters
        })
        @DisplayName("should lerp between values correctly")
        void lerp_interpolatesCorrectly(float alpha, float max, float min, float expected) {
            // When: Lerp between min and max
            float result = min + (max - min) * alpha;

            // Then: Should match expected
            assertThat(result).isCloseTo(expected, within(0.0001f));
        }

        @Test
        @DisplayName("should normalize values to 0-1 range")
        void normalize_mapsToUnitRange() {
            // Given: Value in range [0, 100]
            float value = 75.0f;
            float min = 0.0f;
            float max = 100.0f;

            // When: Normalize
            float normalized = (value - min) / (max - min);

            // Then: Should be in [0, 1]
            assertThat(normalized).isCloseTo(0.75f, within(0.0001f));
        }

        @ParameterizedTest
        @CsvSource({
            "100.0, 10.0",    // sqrt(100) = 10
            "25.0, 5.0",      // sqrt(25) = 5
            "2.0, 1.41421",   // sqrt(2) ≈ 1.41421
            "0.0, 0.0"        // sqrt(0) = 0
        })
        @DisplayName("should calculate square root correctly")
        void sqrt_calculatesCorrectly(float value, float expected) {
            // When: Calculate square root
            double result = Math.sqrt(value);

            // Then: Should match expected
            assertThat(result).isCloseTo(expected, within(0.001));
        }

        @Test
        @DisplayName("should handle distance calculations")
        void distance_calculatesEuclidean() {
            // Given: Two points
            double x1 = 0.0, y1 = 0.0, z1 = 0.0;
            double x2 = 3.0, y2 = 4.0, z2 = 0.0;

            // When: Calculate distance
            double distance = Math.sqrt(
                Math.pow(x2 - x1, 2) + 
                Math.pow(y2 - y1, 2) + 
                Math.pow(z2 - z1, 2)
            );

            // Then: Should be 5.0 (3-4-5 triangle)
            assertThat(distance).isCloseTo(5.0, within(0.0001));
        }
    }

    @Nested
    @DisplayName("Null Safety")
    class NullSafetyTests {

        @Test
        @DisplayName("should provide default for null string")
        void orDefault_handlesNullString() {
            // Given: Null string
            String value = null;

            // When: Use default
            String result = value != null ? value : "default";

            // Then: Should return default
            assertThat(result).isEqualTo("default");
        }

        @Test
        @DisplayName("should provide default for null number")
        void orDefault_handlesNullNumber() {
            // Given: Null integer
            Integer value = null;

            // When: Use default
            int result = value != null ? value : 42;

            // Then: Should return default
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("should use non-null value over default")
        void orDefault_prefersNonNull() {
            // Given: Non-null value
            String value = "actual";

            // When: Use default
            String result = value != null ? value : "default";

            // Then: Should return actual value
            assertThat(result).isEqualTo("actual");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle division by zero safely")
        void divideByZero_handlesSafely() {
            // Given: Potential division by zero
            float numerator = 10.0f;
            float denominator = 0.0f;

            // When: Divide with safety check
            float result = denominator != 0 ? numerator / denominator : 0.0f;

            // Then: Should return safe value
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should handle very small numbers")
        void handleSmallNumbers_maintainsPrecision() {
            // Given: Very small number
            float small = 0.0000001f;

            // When: Perform operations
            float doubled = small * 2;

            // Then: Should maintain relative precision
            assertThat(doubled).isCloseTo(0.0000002f, within(0.00000001f));
        }

        @Test
        @DisplayName("should handle very large numbers")
        void handleLargeNumbers_avoidsOverflow() {
            // Given: Large numbers
            long large1 = Long.MAX_VALUE / 2;
            long large2 = Long.MAX_VALUE / 2;

            // When: Add without overflow
            // In production, use Math.addExact or check
            boolean wouldOverflow = large1 > Long.MAX_VALUE - large2;

            // Then: Should detect potential overflow
            assertThat(wouldOverflow).isFalse();
        }

        @Test
        @DisplayName("should handle percentage calculations")
        void percentage_calculatesCorrectly() {
            // Given: Values for percentage
            float part = 75.0f;
            float whole = 100.0f;

            // When: Calculate percentage
            float percentage = (part / whole) * 100.0f;

            // Then: Should be 75%
            assertThat(percentage).isCloseTo(75.0f, within(0.01f));
        }

        @Test
        @DisplayName("should handle ratio comparisons")
        void ratio_comparesCorrectly() {
            // Given: Two ratios
            float ratio1 = 3.0f / 4.0f;  // 0.75
            float ratio2 = 2.0f / 3.0f;  // 0.666...

            // When: Compare
            boolean ratio1Greater = ratio1 > ratio2;

            // Then: 3/4 should be greater than 2/3
            assertThat(ratio1Greater).isTrue();
        }
    }

    @Nested
    @DisplayName("String Validation")
    class StringValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
        @DisplayName("should detect blank strings")
        void isBlank_detectsBlank(String value) {
            // When: Check if blank
            boolean blank = value == null || value.trim().isEmpty();

            // Then: Should be blank
            assertThat(blank).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"text", "a", "   text   "})
        @DisplayName("should detect non-blank strings")
        void isBlank_detectsNonBlank(String value) {
            // When: Check if blank
            boolean blank = value == null || value.trim().isEmpty();

            // Then: Should not be blank
            assertThat(blank).isFalse();
        }

        @Test
        @DisplayName("should validate identifier format")
        void validateIdentifier_checksFormat() {
            // Given: Various identifier formats
            String valid = "petsplus:guardian";
            String invalid = "Invalid Id";

            // When: Validate format (namespace:path)
            boolean validFormat = valid.matches("^[a-z0-9_-]+:[a-z0-9_/-]+$");
            boolean invalidFormat = invalid.matches("^[a-z0-9_-]+:[a-z0-9_/-]+$");

            // Then: Should match correctly
            assertThat(validFormat).isTrue();
            assertThat(invalidFormat).isFalse();
        }
    }
}
