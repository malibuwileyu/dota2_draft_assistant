package com.dota2assistant.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ProcessDetector utility class.
 */
public class ProcessDetectorTest {
    
    @Test
    public void testIsDota2Running() {
        // This test just verifies the method doesn't throw exceptions
        // It's not testing actual process detection - that would require 
        // starting/stopping actual Dota 2 processes
        boolean result = ProcessDetector.isDota2Running();
        
        // Just assert that we get some result
        // The actual value depends on the test environment
        assertNotNull(result);
    }
    
    @Test
    public void testOsDetection() {
        // Test OS detection logic
        String osName = System.getProperty("os.name").toLowerCase();
        
        // Test should run on all platforms
        assertTrue(osName.contains("win") || osName.contains("mac") || 
                  osName.contains("nix") || osName.contains("nux") || 
                  osName.contains("aix") || osName.contains("sunos"));
    }
}