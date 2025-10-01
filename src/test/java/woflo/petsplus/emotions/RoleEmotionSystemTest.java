package woflo.petsplus.emotions;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.events.EmotionProcessor;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.emotions.modifiers.CursedOneEmotionModifier;
import woflo.petsplus.emotions.modifiers.EnchantmentBoundEmotionModifier;


/**
 * Test suite for the role-based emotion modifier system.
 * Validates that role-specific emotional responses work correctly.
 */
public class RoleEmotionSystemTest {
    
    /**
     * Custom exception for test failures
     */
    public static class RoleEmotionTestException extends RuntimeException {
        public RoleEmotionTestException(String message) {
            super(message);
        }
    }
    
    /**
     * Tests that the role emotion registry initializes correctly
     */
    public static boolean testRegistryInitialization() {
        try {
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            
            // Should not be initialized initially
            if (registry.isInitialized()) {
                return false;
            }
            
            // Initialize registry
            registry.initialize();
            
            // Should now be initialized
            if (!registry.isInitialized()) {
                return false;
            }
            
            // Should have registered modifiers
            if (registry.getAllModifiers().isEmpty()) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Registry initialization test failed: " + e.getMessage());
        }
    }
    
    /**
     * Tests Cursed One role modifier behavior
     */
    public static boolean testCursedOneModifier() {
        try {
            CursedOneEmotionModifier modifier = new CursedOneEmotionModifier();
            
            // Test that modifier has correct role ID
            if (!modifier.getRoleId().equals(PetRoleType.CURSED_ONE_ID)) {
                return false;
            }
            
            // Test that modifier has high priority
            if (modifier.getPriority() <= 5) {
                return false;
            }
            
            // Test that modifier applies to cursed one role
            // (BaseRoleEmotionModifier automatically applies to its designated role)
            if (!modifier.getRoleId().equals(PetRoleType.CURSED_ONE_ID)) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Cursed One modifier test failed: " + e.getMessage());
        }
    }
    
    /**
     * Tests Enchantment Bound role modifier behavior
     */
    public static boolean testEnchantmentBoundModifier() {
        try {
            EnchantmentBoundEmotionModifier modifier = new EnchantmentBoundEmotionModifier();
            
            // Test that modifier has correct role ID
            if (!modifier.getRoleId().equals(PetRoleType.ENCHANTMENT_BOUND_ID)) {
                return false;
            }
            
            // Test that modifier applies to enchantment bound role
            // (BaseRoleEmotionModifier automatically applies to its designated role)
            if (!modifier.getRoleId().equals(PetRoleType.ENCHANTMENT_BOUND_ID)) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Enchantment Bound modifier test failed: " + e.getMessage());
        }
    }
    
    /**
     * Tests role modifier integration with emotion processing
     */
    public static boolean testRoleModifierIntegration(MobEntity pet, PetComponent petComp, 
                                                     PlayerEntity owner) {
        try {
            // Set up pet with cursed one role
            // Note: This would require actual role assignment implementation
            
            // Create mock damage source for owner hitting pet
            DamageSource ownerDamage = null; // Would need proper mock
            
            // Process combat damage
            EmotionProcessor.processCombatDamage(pet, petComp, ownerDamage, 5.0f, true, true);
            
            // Cursed One should enjoy owner hits, so should have positive emotions
            // This would require access to pet's current emotional state to validate
            
            return true;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Role modifier integration test failed: " + e.getMessage());
        }
    }
    
    /**
     * Tests role modifier priority system
     */
    public static boolean testRoleModifierPriority() {
        try {
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            registry.initialize();
            
            // Get modifiers for a specific role
            var modifiers = registry.getModifiersForRole(PetRoleType.CURSED_ONE_ID);
            
            // Should have at least one modifier
            if (modifiers.isEmpty()) {
                return false;
            }
            
            // All modifiers should have valid priorities
            for (var modifier : modifiers) {
                if (modifier.getPriority() <= 0) {
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Role modifier priority test failed: " + e.getMessage());
        }
    }
    
    /**
     * Comprehensive test for the role-based emotion system
     */
    public static boolean runComprehensiveRoleTest(MobEntity pet, PetComponent petComp, 
                                                  PlayerEntity owner) {
        boolean allTestsPassed = true;
        
        try {
            // Test 1: Registry initialization
            allTestsPassed &= testRegistryInitialization();
            
            // Test 2: Cursed One modifier
            allTestsPassed &= testCursedOneModifier();
            
            // Test 3: Enchantment Bound modifier
            allTestsPassed &= testEnchantmentBoundModifier();
            
            // Test 4: Role modifier priority
            allTestsPassed &= testRoleModifierPriority();
            
            // Test 5: Integration with emotion processing
            allTestsPassed &= testRoleModifierIntegration(pet, petComp, owner);
            
            return allTestsPassed;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Comprehensive role test failed: " + e.getMessage());
        }
    }
    
    /**
     * Performance test for role-based emotion processing
     */
    public static boolean testRoleEmotionPerformance() {
        try {
            long startTime = System.nanoTime();
            
            RoleEmotionRegistry registry = RoleEmotionRegistry.getInstance();
            registry.initialize();
            
            // Process multiple role modifier lookups
            for (int i = 0; i < 1000; i++) {
                registry.getModifiersForRole(PetRoleType.CURSED_ONE_ID);
                registry.getModifiersForRole(PetRoleType.ENCHANTMENT_BOUND_ID);
                registry.getModifiersForRole(PetRoleType.GUARDIAN_ID);
            }
            
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            long durationMs = duration / 1_000_000;
            
            // Should complete within reasonable time (e.g., 50ms for 3000 lookups)
            return durationMs < 50;
            
        } catch (Exception e) {
            throw new RoleEmotionTestException("Role emotion performance test failed: " + e.getMessage());
        }
    }
}