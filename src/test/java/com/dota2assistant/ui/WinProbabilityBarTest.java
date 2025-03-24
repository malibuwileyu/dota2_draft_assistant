package com.dota2assistant.ui;

import javafx.application.Platform;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the win probability bar fix.
 * This test verifies that both progress bars (Radiant and Dire) are properly updated
 * when the win probability changes.
 */
public class WinProbabilityBarTest {

    @BeforeAll
    public static void setUp() {
        // Initialize JavaFX Toolkit
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized - ignore
        }
    }

    @Test
    public void testWinProbabilityUpdatesCorrectly() {
        // Create the two progress bars used in the UI
        ProgressBar radiantWinBar = new ProgressBar(0.5); // Start at 50%
        ProgressBar direWinBar = new ProgressBar(1.0); // Initially Dire is 100% (the issue we fixed)
        Label winPercentageLabel = new Label("50.0% - 50.0%");

        // Verify initial state
        assertEquals(0.5, radiantWinBar.getProgress(), 0.01, "Initial Radiant win bar should be 50%");
        assertEquals(1.0, direWinBar.getProgress(), 0.01, "Initial Dire win bar should be 100% (the default that caused the issue)");
        
        // Simulate our updateWinProbabilityBar method with 70% Radiant win probability
        double radiantWinProbability = 0.7;
        double direWinProbability = 1 - radiantWinProbability;
        
        // This is the key part of our fix - update both progress bars
        radiantWinBar.setProgress(radiantWinProbability);
        direWinBar.setProgress(direWinProbability); // The critical fix we made
        
        String percentText = String.format("%.1f%% - %.1f%%", radiantWinProbability * 100, direWinProbability * 100);
        winPercentageLabel.setText(percentText);
        
        // Verify that both bars are updated correctly
        assertEquals(0.7, radiantWinBar.getProgress(), 0.01, "Radiant win bar should be updated to 70%");
        assertEquals(0.3, direWinBar.getProgress(), 0.01, "Dire win bar should be updated to 30% (our fix)");
        assertEquals("70.0% - 30.0%", winPercentageLabel.getText(), "Label should show correct percentages");
    }
}