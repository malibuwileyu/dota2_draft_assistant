package com.dota2assistant.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to detect if specific processes are running on the system.
 * Used primarily to check if Dota 2 is running before attempting GSI connection.
 */
public class ProcessDetector {
    private static final Logger logger = LoggerFactory.getLogger(ProcessDetector.class);
    
    // Process names to look for on different operating systems
    private static final String[] DOTA_PROCESS_NAMES = {
        "dota2.exe",      // Windows
        "dota2",         // Linux
        "Dota 2",        // macOS
        "dota"           // Generic fallback
    };
    
    /**
     * Checks if Dota 2 is currently running on the system.
     * 
     * @return true if Dota 2 is running, false otherwise
     */
    public static boolean isDota2Running() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        try {
            if (osName.contains("win")) {
                return isProcessRunningWindows();
            } else if (osName.contains("mac")) {
                return isProcessRunningMac();
            } else {
                // Assume Linux or similar Unix-like system
                return isProcessRunningLinux();
            }
        } catch (Exception e) {
            logger.error("Error checking if Dota 2 is running", e);
            // Default to true in case of error (safer to assume it's running than not)
            return true;
        }
    }
    
    /**
     * Checks if Dota 2 is running on Windows using the tasklist command.
     * 
     * @return true if Dota 2 is running, false otherwise
     */
    private static boolean isProcessRunningWindows() {
        try {
            Process process = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq dota2.exe", "/NH").start();
            process.waitFor(3, TimeUnit.SECONDS);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("dota2.exe")) {
                        logger.info("Dota 2 process found on Windows: {}", line);
                        return true;
                    }
                }
            }
            
            logger.info("Dota 2 process not found on Windows");
            return false;
        } catch (Exception e) {
            logger.error("Error checking for Dota 2 process on Windows", e);
            // Default to true in case of error (assume it's running)
            return true;
        }
    }
    
    /**
     * Checks if Dota 2 is running on macOS using the ps command.
     * 
     * @return true if Dota 2 is running, false otherwise
     */
    private static boolean isProcessRunningMac() {
        try {
            Process process = new ProcessBuilder("ps", "-ax").start();
            process.waitFor(3, TimeUnit.SECONDS);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    for (String processName : DOTA_PROCESS_NAMES) {
                        if (line.toLowerCase().contains(processName.toLowerCase())) {
                            logger.info("Dota 2 process found on macOS: {}", line);
                            return true;
                        }
                    }
                }
            }
            
            logger.info("Dota 2 process not found on macOS");
            return false;
        } catch (Exception e) {
            logger.error("Error checking for Dota 2 process on macOS", e);
            // Default to true in case of error (assume it's running)
            return true;
        }
    }
    
    /**
     * Checks if Dota 2 is running on Linux using the ps command.
     * 
     * @return true if Dota 2 is running, false otherwise
     */
    private static boolean isProcessRunningLinux() {
        try {
            Process process = new ProcessBuilder("ps", "-aux").start();
            process.waitFor(3, TimeUnit.SECONDS);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                List<String> grepExceptions = new ArrayList<>();
                grepExceptions.add("grep");
                
                while ((line = reader.readLine()) != null) {
                    boolean isException = false;
                    for (String exception : grepExceptions) {
                        if (line.contains(exception)) {
                            isException = true;
                            break;
                        }
                    }
                    
                    if (!isException) {
                        for (String processName : DOTA_PROCESS_NAMES) {
                            if (line.toLowerCase().contains(processName.toLowerCase())) {
                                logger.info("Dota 2 process found on Linux: {}", line);
                                return true;
                            }
                        }
                    }
                }
            }
            
            logger.info("Dota 2 process not found on Linux");
            return false;
        } catch (Exception e) {
            logger.error("Error checking for Dota 2 process on Linux", e);
            // Default to true in case of error (assume it's running)
            return true;
        }
    }
}