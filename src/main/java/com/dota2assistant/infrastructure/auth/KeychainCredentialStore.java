package com.dota2assistant.infrastructure.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * macOS Keychain credential store.
 * Uses the 'security' command-line tool to interact with Keychain.
 */
public class KeychainCredentialStore implements CredentialStore {
    
    private static final Logger log = LoggerFactory.getLogger(KeychainCredentialStore.class);
    private static final String ACCOUNT_NAME = "dota2assistant";
    
    @Override
    public boolean store(String key, String value) {
        try {
            // First delete any existing entry (security add-generic-password fails if exists)
            delete(key);
            
            // Base64 encode to handle special characters
            String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            
            ProcessBuilder pb = new ProcessBuilder(
                "security", "add-generic-password",
                "-a", ACCOUNT_NAME,
                "-s", SERVICE_NAME + "." + key,
                "-w", encoded,
                "-U" // Update if exists (though we deleted above)
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0) {
                log.debug("Stored credential '{}' in Keychain", key);
                return true;
            } else {
                log.warn("Failed to store credential '{}' in Keychain", key);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error storing credential in Keychain: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Optional<String> retrieve(String key) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "security", "find-generic-password",
                "-a", ACCOUNT_NAME,
                "-s", SERVICE_NAME + "." + key,
                "-w" // Output password only
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0) {
                String encoded = output.toString().trim();
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                log.debug("Retrieved credential '{}' from Keychain", key);
                return Optional.of(decoded);
            } else {
                log.debug("Credential '{}' not found in Keychain", key);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving credential from Keychain: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(String key) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "security", "delete-generic-password",
                "-a", ACCOUNT_NAME,
                "-s", SERVICE_NAME + "." + key
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
            
            // Exit code 44 means item not found, which is fine
            log.debug("Deleted credential '{}' from Keychain", key);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting credential from Keychain: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        // Check if we're on macOS and security command exists
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            return false;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("security", "help");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getStoreName() {
        return "macOS Keychain";
    }
}

