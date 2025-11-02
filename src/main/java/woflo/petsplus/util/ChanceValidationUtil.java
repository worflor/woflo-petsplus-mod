package woflo.petsplus.util;

import net.minecraft.util.math.MathHelper;

/**
 * Utility class for validating and clamping chance values to ensure they stay within valid ranges.
 * This provides consistent validation across the codebase for configuration-based chance values.
 */
public final class ChanceValidationUtil {
    
    private ChanceValidationUtil() {
        // Non-instantiable utility class
    }
    
    /**
     * Minimum valid chance value (0.0 = 0% chance)
     */
    public static final double MIN_CHANCE = 0.0;
    
    /**
     * Maximum valid chance value (1.0 = 100% chance)
     */
    public static final double MAX_CHANCE = 1.0;
    
    /**
     * Validates and clamps a chance value to the valid range [0.0, 1.0].
     * 
     * @param chance The chance value to validate
     * @return The clamped chance value within [0.0, 1.0]
     */
    public static double validateChance(double chance) {
        return MathHelper.clamp(chance, MIN_CHANCE, MAX_CHANCE);
    }
    
    /**
     * Validates and clamps a float chance value to the valid range [0.0, 1.0].
     * 
     * @param chance The chance value to validate
     * @return The clamped chance value within [0.0, 1.0]
     */
    public static float validateChance(float chance) {
        return MathHelper.clamp(chance, (float) MIN_CHANCE, (float) MAX_CHANCE);
    }
    
    /**
     * Converts a percentage value (0-100) to a chance value (0.0-1.0) and validates it.
     * 
     * @param percentage The percentage value (0-100)
     * @return The validated chance value within [0.0, 1.0]
     */
    public static double percentageToChance(double percentage) {
        return validateChance(percentage / 100.0);
    }
    
    /**
     * Converts a percentage value (0-100) to a chance value (0.0-1.0) and validates it.
     * 
     * @param percentage The percentage value (0-100)
     * @return The validated chance value within [0.0, 1.0]
     */
    public static float percentageToChance(float percentage) {
        return validateChance(percentage / 100.0f);
    }
    
    /**
     * Checks if a chance value is already within the valid range [0.0, 1.0].
     * 
     * @param chance The chance value to check
     * @return true if the value is within the valid range, false otherwise
     */
    public static boolean isValidChance(double chance) {
        return chance >= MIN_CHANCE && chance <= MAX_CHANCE;
    }
    
    /**
     * Checks if a chance value is already within the valid range [0.0, 1.0].
     * 
     * @param chance The chance value to check
     * @return true if the value is within the valid range, false otherwise
     */
    public static boolean isValidChance(float chance) {
        return chance >= MIN_CHANCE && chance <= MAX_CHANCE;
    }
    
    /**
     * Gets a validated chance value from configuration, with optional logging if the value was clamped.
     * 
     * @param chance The chance value from configuration
     * @param configKey The configuration key for logging purposes
     * @return The validated chance value within [0.0, 1.0]
     */
    public static double getValidatedChance(double chance, String configKey) {
        if (!isValidChance(chance)) {
            double originalChance = chance;
            double validatedChance = validateChance(chance);
            
            // Log out-of-range value
            System.err.println("[PetsPlus] Chance value for '" + configKey + "' was out of range [" + 
                             originalChance + "], clamped to [" + validatedChance + "]");
            
            return validatedChance;
        }
        return chance;
    }
    
    /**
     * Gets a validated chance value from configuration, with optional logging if the value was clamped.
     * 
     * @param chance The chance value from configuration
     * @param configKey The configuration key for logging purposes
     * @return The validated chance value within [0.0, 1.0]
     */
    public static float getValidatedChance(float chance, String configKey) {
        if (!isValidChance(chance)) {
            float originalChance = chance;
            float validatedChance = validateChance(chance);
            
            // Log out-of-range value
            System.err.println("[PetsPlus] Chance value for '" + configKey + "' was out of range [" + 
                             originalChance + "], clamped to [" + validatedChance + "]");
            
            return validatedChance;
        }
        return chance;
    }
    
    /**
     * Safely performs a chance check using Minecraft's random generator.
     * This ensures the chance value is validated before the random check.
     * 
     * @param chance The chance value (0.0-1.0)
     * @param random The random generator to use
     * @return true if the random check passes, false otherwise
     */
    public static boolean checkChance(double chance, java.util.Random random) {
        double validatedChance = validateChance(chance);
        return random.nextDouble() < validatedChance;
    }
    
    /**
     * Safely performs a chance check using Minecraft's random generator.
     * This ensures the chance value is validated before the random check.
     * 
     * @param chance The chance value (0.0-1.0)
     * @param random The random generator to use
     * @return true if the random check passes, false otherwise
     */
    public static boolean checkChance(float chance, java.util.Random random) {
        float validatedChance = validateChance(chance);
        return random.nextFloat() < validatedChance;
    }
    
    /**
     * Safely performs a chance check using net.minecraft.util.math.random.Random.
     * This ensures the chance value is validated before the random check.
     * 
     * @param chance The chance value (0.0-1.0)
     * @param random The random generator to use
     * @return true if the random check passes, false otherwise
     */
    public static boolean checkChance(double chance, net.minecraft.util.math.random.Random random) {
        double validatedChance = validateChance(chance);
        return random.nextFloat() < validatedChance;
    }
    
    /**
     * Safely performs a chance check using net.minecraft.util.math.random.Random.
     * This ensures the chance value is validated before the random check.
     * 
     * @param chance The chance value (0.0-1.0)
     * @param random The random generator to use
     * @return true if the random check passes, false otherwise
     */
    public static boolean checkChance(float chance, net.minecraft.util.math.random.Random random) {
        float validatedChance = validateChance(chance);
        return random.nextFloat() < validatedChance;
    }
}
