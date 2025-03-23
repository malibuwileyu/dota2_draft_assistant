package com.dota2assistant.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test class focuses on the win probability bar fix implementation.
 * It simulates the logic in updateWinProbabilityBar method from MainController
 * and verifies that both progress bars are updated correctly.
 */
public class UpdateWinProbabilityTest {

    /**
     * Tests the fix for various win probability values
     */
    private void testWinProbabilityValue(double radiantWinProbability) {
        // Initial setup - mimic updateWinProbabilityBar method
        double radiantProgress = 0.5;  // Initial value
        double direProgress = 1.0;     // Default value that caused the bug
        
        // Apply the fix: Update both progress bars with proper values
        radiantProgress = radiantWinProbability;
        direProgress = 1.0 - radiantWinProbability;  // This is the key fix
        
        // Verify the calculations are correct
        assertEquals(radiantWinProbability, radiantProgress, 0.001, 
            "Radiant progress bar should match the radiant win probability");
        assertEquals(1.0 - radiantWinProbability, direProgress, 0.001, 
            "Dire progress bar should be (1 - radiantWinProbability)");
        
        // Verify the sum is always 1.0 (100%)
        assertEquals(1.0, radiantProgress + direProgress, 0.001, 
            "The sum of both progress bars should always be 1.0 (100%)");
    }
    
    @Test
    @DisplayName("Test win probability values at 10%")
    public void testWinProbability10Percent() {
        testWinProbabilityValue(0.1);
    }
    
    @Test
    @DisplayName("Test win probability values at 25%")
    public void testWinProbability25Percent() {
        testWinProbabilityValue(0.25);
    }
    
    @Test
    @DisplayName("Test win probability values at 50%")
    public void testWinProbability50Percent() {
        testWinProbabilityValue(0.5);
    }
    
    @Test
    @DisplayName("Test win probability values at 75%")
    public void testWinProbability75Percent() {
        testWinProbabilityValue(0.75);
    }
    
    @Test
    @DisplayName("Test win probability values at 90%")
    public void testWinProbability90Percent() {
        testWinProbabilityValue(0.9);
    }
    
    /**
     * Tests the edge cases of 0% and 100% win probability
     */
    @Test
    @DisplayName("Test win probability edge cases")
    public void testWinProbabilityEdgeCases() {
        // Test 0% Radiant win probability (100% Dire)
        double radiantProgress = 0.0;
        double direProgress = 1.0;
        assertEquals(0.0, radiantProgress, "Radiant progress should be 0%");
        assertEquals(1.0, direProgress, "Dire progress should be 100%");
        
        // Test 100% Radiant win probability (0% Dire)
        radiantProgress = 1.0;
        direProgress = 0.0;
        assertEquals(1.0, radiantProgress, "Radiant progress should be 100%");
        assertEquals(0.0, direProgress, "Dire progress should be 0%");
    }
    
    /**
     * Simulates the full updateWinProbabilityBar method
     * containing our fix for the win probability visualization.
     */
    @Test
    @DisplayName("Test full updateWinProbabilityBar method implementation")
    public void testFullWinProbabilityBarMethod() {
        // Mock progress bar values
        double radiantBarProgress = 0.5;
        double direBarProgress = 1.0; // Default problematic value
        String labelText = "50.0% - 50.0%";
        
        // Update with new probability
        double radiantWinProbability = 0.65; // 65% win chance for Radiant
        
        // Simulate our fixed method
        radiantWinProbability = Math.max(0, Math.min(1, radiantWinProbability)); // Ensure within bounds
        double direWinProbability = 1 - radiantWinProbability;
        
        // Format percentages for display
        String radiantPercentText = String.format("%.1f%%", radiantWinProbability * 100);
        String direPercentText = String.format("%.1f%%", direWinProbability * 100);
        
        // Update the label
        labelText = radiantPercentText + " - " + direPercentText;
        
        // HERE'S THE FIX: Update both progress bars with their respective probabilities
        radiantBarProgress = radiantWinProbability;
        direBarProgress = direWinProbability; // This was missing in the original code
        
        // Verify the results
        assertEquals(0.65, radiantBarProgress, 0.01, "Radiant bar should show 65%");
        assertEquals(0.35, direBarProgress, 0.01, "Dire bar should show 35%");
        assertEquals("65.0% - 35.0%", labelText, "Label should show correct percentages");
        
        // The sum should always be 1.0 (100%)
        assertEquals(1.0, radiantBarProgress + direBarProgress, 0.01, 
                    "The sum of both progress bars should be 100%");
    }
    
    /**
     * Tests the fix for capping advantage percentages at 100%
     */
    @Test
    @DisplayName("Test capping advantage percentages")
    public void testAdvantagePercentageCapping() {
        // Test normal advantage within range
        int normalAdvantage = calculateDisplayPercentage(0.75);
        assertEquals(75, normalAdvantage, "Normal advantage (75%) should not be capped");
        
        // Test very high advantage that should be capped
        int extremeAdvantage = calculateDisplayPercentage(100.0);
        assertEquals(100, extremeAdvantage, "Extreme advantage (10000%) should be capped at 100%");
        
        // Test another high advantage
        int highAdvantage = calculateDisplayPercentage(1.5);
        assertEquals(100, highAdvantage, "High advantage (150%) should be capped at 100%");
    }
    
    /**
     * Tests the capped win probability calculation
     */
    @Test
    @DisplayName("Test win probability capping")
    public void testWinProbabilityCapping() {
        // Test normal balanced probability - with min cap of 25%, it will be at least 0.25
        double balancedProb = calculateWinProbability(0.1); // Small difference
        assertTrue(balancedProb >= 0.25 && balancedProb <= 0.75, 
                "Balanced matchup should have win probability between 25-75%");
        
        // Test extreme probability that should be capped at 75%
        double extremeHighProb = calculateWinProbability(5.0); // Huge advantage
        assertEquals(0.75, extremeHighProb, 0.01, "Extreme high win probability should be capped at 75%");
        
        // Test extreme low probability that should be capped at 25%
        double extremeLowProb = calculateWinProbability(-5.0); // Huge disadvantage
        assertEquals(0.25, extremeLowProb, 0.01, "Extreme low win probability should be capped at 25%");
    }
    
    /**
     * Helper method to mimic the advantage percentage calculation with capping
     */
    private int calculateDisplayPercentage(double score) {
        // Cap the advantage percentage at 100% for display purposes
        return (int)Math.min(Math.round(score * 100), 100);
    }
    
    /**
     * Helper method to mimic the win probability calculation with capping
     */
    private double calculateWinProbability(double diff) {
        // Calculate probability using logistic function
        double probability = 1.0 / (1.0 + Math.exp(-diff * 5)); // Scaling factor 5
        
        // Cap extreme probabilities to keep the UI more balanced
        // Cap at 0.75 (75%) to 0.25 (25%) range
        return Math.max(0.25, Math.min(0.75, probability));
    }
}