package woflo.petsplus.ai.goals;

import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for energy range gating to ensure proper balance and integration.
 */
class EnergyGatingTest {
    
    @Test
    void testEnergyBiasCalculation() {
        // Create a test goal with mid-range energy (0.3-0.7)
        GoalType testGoal = GoalType.CIRCLE_SPOT; // 0.3-0.7 range
        
        // Test center of range (0.5) - should be 1.0
        assertEquals(1.0f, testGoal.getEnergyBias(0.5f), 0.01f, 
            "Center of range should have full bias (1.0)");
        
        // Test at min edge (0.3) - should be 0.5
        assertEquals(0.5f, testGoal.getEnergyBias(0.3f), 0.01f, 
            "Min edge should have reduced bias (0.5)");
        
        // Test at max edge (0.7) - should be 0.5
        assertEquals(0.5f, testGoal.getEnergyBias(0.7f), 0.01f, 
            "Max edge should have reduced bias (0.5)");
        
        // Test outside range (0.1) - should be 0.1
        assertEquals(0.1f, testGoal.getEnergyBias(0.1f), 0.01f, 
            "Outside range should have minimal bias (0.1) for unpredictability");
        
        // Test outside range (0.9) - should be 0.1
        assertEquals(0.1f, testGoal.getEnergyBias(0.9f), 0.01f, 
            "Outside range should have minimal bias (0.1) for unpredictability");
    }
    
    @Test
    void testEnergyCompatibility() {
        // Test rest goal (0.0-0.4) - with SOFT gating, still compatible outside range (just rare)
        GoalType restGoal = GoalType.SIT_SPHINX_POSE;
        assertTrue(restGoal.isEnergyCompatible(0.2f), "Rest goal should be compatible at low energy");
        // With soft gating (0.1 bias), this is still technically compatible
        assertTrue(restGoal.isEnergyCompatible(0.9f), "Rest goal is technically compatible (soft gate allows 10%)");
        // But bias should be very low
        assertTrue(restGoal.getEnergyBias(0.9f) < 0.15f, "Rest goal should have very low bias at high energy");
        
        // Test active goal (0.6-1.0)
        GoalType activeGoal = GoalType.TAIL_CHASE;
        // With soft gating, still compatible outside range
        assertTrue(activeGoal.isEnergyCompatible(0.1f), "Active goal is technically compatible (soft gate allows 10%)");
        assertTrue(activeGoal.isEnergyCompatible(0.8f), "Active goal should be compatible at high energy");
        // But bias should be very low at wrong energy
        assertTrue(activeGoal.getEnergyBias(0.1f) < 0.15f, "Active goal should have very low bias at low energy");
        
        // Test flexible goal (0.0-1.0)
        GoalType flexibleGoal = GoalType.OWNER_ORBIT;
        assertTrue(flexibleGoal.isEnergyCompatible(0.1f), "Flexible goal should be compatible at low energy");
        assertTrue(flexibleGoal.isEnergyCompatible(0.9f), "Flexible goal should be compatible at high energy");
    }
    
    @Test
    void testRestGoalsHaveLowEnergyRanges() {
        // Verify rest behaviors require low energy
        assertTrue(GoalType.SIT_SPHINX_POSE.getEnergyBias(0.2f) > 0.5f, 
            "Sit should prefer low energy");
        assertTrue(GoalType.PREEN_FEATHERS.getEnergyBias(0.2f) > 0.5f, 
            "Preen should prefer low energy");
        assertTrue(GoalType.FLOAT_IDLE.getEnergyBias(0.2f) > 0.5f, 
            "Float idle should prefer low energy");
        assertTrue(GoalType.STARGAZING.getEnergyBias(0.2f) > 0.5f, 
            "Stargazing should prefer low energy");
    }
    
    @Test
    void testActiveGoalsHaveHighEnergyRanges() {
        // Verify active behaviors require high energy
        assertTrue(GoalType.TAIL_CHASE.getEnergyBias(0.8f) > 0.5f, 
            "Tail chase should prefer high energy");
        assertTrue(GoalType.POUNCE_PRACTICE.getEnergyBias(0.8f) > 0.5f, 
            "Pounce should prefer high energy");
        assertTrue(GoalType.PARKOUR_CHALLENGE.getEnergyBias(0.8f) > 0.5f, 
            "Parkour should prefer high energy");
        assertTrue(GoalType.BUBBLE_PLAY.getEnergyBias(0.8f) > 0.5f, 
            "Bubble play should prefer high energy");
    }
    
    @Test
    void testNeutralGoalsWorkAtMidEnergy() {
        // Verify neutral behaviors work well at mid energy
        assertTrue(GoalType.STRETCH_AND_YAW.getEnergyBias(0.5f) > 0.8f, 
            "Stretch should work at mid energy");
        assertTrue(GoalType.CASUAL_WANDER.getEnergyBias(0.5f) > 0.8f, 
            "Casual wander should work at mid energy");
        assertTrue(GoalType.SNIFF_GROUND.getEnergyBias(0.5f) > 0.8f, 
            "Sniff ground should work at mid energy");
    }
    
    @Test
    void testSoftGatingPreventsTotalExclusion() {
        // Verify soft gating allows rare behaviors outside range
        GoalType restGoal = GoalType.SIT_SPHINX_POSE; // 0.0-0.4
        float highEnergyBias = restGoal.getEnergyBias(0.9f);
        
        assertTrue(highEnergyBias > 0.05f, 
            "Even at high energy, rest goals should have >5% chance (soft gate threshold)");
        assertTrue(highEnergyBias <= 0.15f, 
            "But bias should be low enough to be rare (<=15%)");
    }
    
    @Test
    void testLinearFalloffFromCenter() {
        GoalType testGoal = GoalType.CIRCLE_SPOT; // 0.3-0.7 range, center=0.5
        
        float centerBias = testGoal.getEnergyBias(0.5f);  // Center
        float midBias = testGoal.getEnergyBias(0.6f);     // Halfway to edge
        float edgeBias = testGoal.getEnergyBias(0.7f);    // Edge
        
        // Should have smooth linear falloff
        assertTrue(centerBias > midBias, "Center should have higher bias than midpoint");
        assertTrue(midBias > edgeBias, "Midpoint should have higher bias than edge");
        
        // Check approximate linearity
        float centerToMid = centerBias - midBias;
        float midToEdge = midBias - edgeBias;
        assertEquals(centerToMid, midToEdge, 0.1f, "Falloff should be approximately linear");
    }
    
    @Test
    void testAllGoalsHaveEnergyRanges() {
        // Verify every goal has a valid energy range
        for (GoalType goal : GoalType.values()) {
            assertNotNull(goal, "Goal should not be null");
            
            // Test that getEnergyBias doesn't throw exceptions
            assertDoesNotThrow(() -> goal.getEnergyBias(0.0f), 
                goal + " should handle min momentum");
            assertDoesNotThrow(() -> goal.getEnergyBias(0.5f), 
                goal + " should handle mid momentum");
            assertDoesNotThrow(() -> goal.getEnergyBias(1.0f), 
                goal + " should handle max momentum");
        }
    }
    
    @Test
    void testCategoryEnergyDistribution() {
        // Count goals in each energy category
        int restGoals = 0;      // 0.0-0.4
        int neutralGoals = 0;   // 0.3-0.7
        int activeGoals = 0;    // 0.6-1.0
        int flexibleGoals = 0;  // 0.0-1.0 or wide
        
        for (GoalType goal : GoalType.values()) {
            // Test at different energy levels
            float lowBias = goal.getEnergyBias(0.2f);
            float midBias = goal.getEnergyBias(0.5f);
            float highBias = goal.getEnergyBias(0.8f);
            
            if (lowBias > 0.5f && highBias <= 0.15f) {
                restGoals++;
            } else if (highBias > 0.5f && lowBias <= 0.15f) {
                activeGoals++;
            } else if (midBias > 0.5f) {
                neutralGoals++;
            } else {
                flexibleGoals++;
            }
        }
        
        // Verify we have good distribution
        assertTrue(restGoals > 0, "Should have some rest goals");
        assertTrue(activeGoals > 0, "Should have some active goals");
        assertTrue(neutralGoals > 0, "Should have some neutral goals");
        
        System.out.println("Energy distribution:");
        System.out.println("  Rest goals (0.0-0.4): " + restGoals);
        System.out.println("  Neutral goals (0.3-0.7): " + neutralGoals);
        System.out.println("  Active goals (0.6-1.0): " + activeGoals);
        System.out.println("  Flexible goals: " + flexibleGoals);
        System.out.println("  Total: " + GoalType.values().length);
    }
    
    @Test
    void testSocialGoalsHaveReasonableRanges() {
        // Social goals should be accessible across different energy levels
        // LEAN_AGAINST_OWNER should be low-energy (0.0-0.6)
        assertTrue(GoalType.LEAN_AGAINST_OWNER.getEnergyBias(0.3f) > 0.5f,
            "Lean against owner should work at low energy");
        
        // SHOW_OFF_TRICK should be high-energy (0.6-1.0)
        assertTrue(GoalType.SHOW_OFF_TRICK.getEnergyBias(0.8f) > 0.5f,
            "Show off trick should work at high energy");
        
        // PARALLEL_PLAY should be flexible (0.3-0.8)
        assertTrue(GoalType.PARALLEL_PLAY.getEnergyBias(0.5f) > 0.5f,
            "Parallel play should work at mid energy");
    }
}
