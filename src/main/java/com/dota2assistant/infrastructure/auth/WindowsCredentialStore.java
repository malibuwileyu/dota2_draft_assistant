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
 * Windows Credential Manager store.
 * Uses cmdkey.exe to interact with Windows Credential Manager.
 * 
 * Note: For production, consider using JNA with advapi32 for direct API access.
 * This implementation uses command-line for simplicity and no native dependencies.
 */
public class WindowsCredentialStore implements CredentialStore {
    
    private static final Logger log = LoggerFactory.getLogger(WindowsCredentialStore.class);
    
    @Override
    public boolean store(String key, String value) {
        try {
            // Delete existing first
            delete(key);
            
            // Base64 encode the value
            String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            String targetName = SERVICE_NAME + "/" + key;
            
            // Use PowerShell to add credential (more reliable than cmdkey for passwords)
            String script = String.format(
                "$cred = New-Object System.Management.Automation.PSCredential('%s', (ConvertTo-SecureString '%s' -AsPlainText -Force)); " +
                "cmdkey /generic:%s /user:%s /pass:%s",
                "dota2assistant", encoded, targetName, "dota2assistant", encoded
            );
            
            ProcessBuilder pb = new ProcessBuilder(
                "cmdkey", "/generic:" + targetName,
                "/user:dota2assistant",
                "/pass:" + encoded
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0) {
                log.debug("Stored credential '{}' in Windows Credential Manager", key);
                return true;
            } else {
                log.warn("Failed to store credential '{}' in Windows Credential Manager", key);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error storing credential in Windows Credential Manager: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Optional<String> retrieve(String key) {
        try {
            String targetName = SERVICE_NAME + "/" + key;
            
            // Use PowerShell to retrieve credential
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-Command",
                String.format(
                    "$cred = Get-StoredCredential -Target '%s'; if ($cred) { $cred.GetNetworkCredential().Password }",
                    targetName
                )
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
            
            // PowerShell Get-StoredCredential might not be available, fall back to cmdkey
            if (!completed || output.toString().contains("not recognized")) {
                return retrieveWithCmdkey(key);
            }
            
            if (completed && process.exitValue() == 0 && !output.toString().isBlank()) {
                String encoded = output.toString().trim();
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                log.debug("Retrieved credential '{}' from Windows Credential Manager", key);
                return Optional.of(decoded);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error retrieving credential from Windows Credential Manager: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    private Optional<String> retrieveWithCmdkey(String key) {
        // cmdkey /list doesn't show passwords, so we need alternate approach
        // For now, this is a limitation - use file fallback on Windows if needed
        log.debug("cmdkey retrieval not fully supported, credential '{}' not retrievable", key);
        return Optional.empty();
    }
    
    @Override
    public boolean delete(String key) {
        try {
            String targetName = SERVICE_NAME + "/" + key;
            
            ProcessBuilder pb = new ProcessBuilder(
                "cmdkey", "/delete:" + targetName
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
            
            log.debug("Deleted credential '{}' from Windows Credential Manager", key);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting credential from Windows Credential Manager: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("windows")) {
            return false;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("cmdkey", "/list");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getStoreName() {
        return "Windows Credential Manager";
    }
}

