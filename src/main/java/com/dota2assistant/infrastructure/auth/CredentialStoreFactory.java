package com.dota2assistant.infrastructure.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Factory for creating the appropriate credential store for the current platform.
 * Attempts native secure storage first, falls back to encrypted file.
 */
@Component
public class CredentialStoreFactory {
    
    private static final Logger log = LoggerFactory.getLogger(CredentialStoreFactory.class);
    
    private final Path appDataDir;
    
    public CredentialStoreFactory(@Value("${database.path:${user.home}/.dota2assistant/data.db}") String dbPath) {
        // Extract directory from database path
        this.appDataDir = Paths.get(dbPath).getParent();
    }
    
    /**
     * Creates the best available credential store for this platform.
     */
    public CredentialStore create() {
        // Try macOS Keychain first
        KeychainCredentialStore keychain = new KeychainCredentialStore();
        if (keychain.isAvailable()) {
            log.info("Using {} for credential storage", keychain.getStoreName());
            return keychain;
        }
        
        // Try Windows Credential Manager
        WindowsCredentialStore winCred = new WindowsCredentialStore();
        if (winCred.isAvailable()) {
            log.info("Using {} for credential storage", winCred.getStoreName());
            return winCred;
        }
        
        // Fall back to encrypted file
        FileCredentialStore fileStore = new FileCredentialStore(appDataDir);
        if (fileStore.isAvailable()) {
            log.info("Using {} for credential storage (fallback)", fileStore.getStoreName());
            return fileStore;
        }
        
        // This shouldn't happen, but return file store anyway
        log.warn("No credential store available, using file store without verification");
        return fileStore;
    }
}

