package com.dota2assistant.infrastructure.auth;

import java.util.Optional;

/**
 * Interface for secure credential storage.
 * Implementations handle platform-specific secure storage mechanisms.
 */
public interface CredentialStore {
    
    /** Service identifier for this app's credentials */
    String SERVICE_NAME = "com.dota2assistant";
    
    /**
     * Stores a credential securely.
     * @param key The credential key (e.g., "steam_session")
     * @param value The credential value to store
     * @return true if stored successfully
     */
    boolean store(String key, String value);
    
    /**
     * Retrieves a stored credential.
     * @param key The credential key
     * @return The stored value, or empty if not found
     */
    Optional<String> retrieve(String key);
    
    /**
     * Deletes a stored credential.
     * @param key The credential key
     * @return true if deleted (or didn't exist)
     */
    boolean delete(String key);
    
    /**
     * Checks if this credential store is available on the current system.
     */
    boolean isAvailable();
    
    /**
     * Returns a human-readable name for this store type.
     */
    String getStoreName();
}

