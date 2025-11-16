package com.dota2assistant.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Utility class for storing and retrieving authentication session tokens.
 * Uses Java Preferences API for storing session tokens securely.
 */
public class SessionStorageUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionStorageUtil.class);
    private static final String SESSION_TOKEN_KEY = "session_token";
    private static final String APP_NAME = "Dota2DraftAssistant";
    private static final String PREFS_FOLDER = "dota2_draft_assistant";
    
    // Use Java Preferences API for cross-platform storage
    private static final Preferences prefs = Preferences.userRoot().node(PREFS_FOLDER);
    
    // Properties file as backup storage method
    private static final String PROPS_FILE = "session.properties";
    
    /**
     * Save a session token using multiple storage mechanisms.
     * 
     * @param token The session token to save
     * @return True if the token was successfully saved
     */
    public static boolean saveSessionToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        boolean success = true;
        
        // Store in Java Preferences API (primary storage)
        try {
            prefs.put(SESSION_TOKEN_KEY, token);
            prefs.sync();
            logger.debug("Session token saved to Preferences API");
        } catch (Exception e) {
            logger.warn("Failed to save session token to Preferences API", e);
            success = false;
        }
        
        // Also save to properties file as backup
        try {
            Properties props = new Properties();
            props.setProperty(SESSION_TOKEN_KEY, token);
            
            File configDir = new File(System.getProperty("user.home"), "." + APP_NAME);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            File propsFile = new File(configDir, PROPS_FILE);
            try (FileOutputStream out = new FileOutputStream(propsFile)) {
                props.store(out, "Dota 2 Draft Assistant Session");
            }
            logger.debug("Session token saved to properties file");
        } catch (IOException e) {
            logger.warn("Failed to save session token to properties file", e);
            // We consider this operation successful if at least one storage method worked
        }
        
        return success;
    }
    
    /**
     * Load a previously saved session token.
     * 
     * @return The session token, or null if not found
     */
    public static String loadSessionToken() {
        // Try to load from Java Preferences API first
        String token = prefs.get(SESSION_TOKEN_KEY, null);
        
        if (token != null && !token.isEmpty()) {
            logger.debug("Session token loaded from Preferences API");
            return token;
        }
        
        // If not found, try to load from properties file
        try {
            File configDir = new File(System.getProperty("user.home"), "." + APP_NAME);
            File propsFile = new File(configDir, PROPS_FILE);
            
            if (propsFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(propsFile)) {
                    props.load(in);
                }
                
                token = props.getProperty(SESSION_TOKEN_KEY);
                if (token != null && !token.isEmpty()) {
                    logger.debug("Session token loaded from properties file");
                    return token;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load session token from properties file", e);
        }
        
        return null;
    }
    
    /**
     * Clear any saved session token.
     */
    public static void clearSessionToken() {
        // Remove from Java Preferences API
        try {
            prefs.remove(SESSION_TOKEN_KEY);
            prefs.sync();
            logger.debug("Session token cleared from Preferences API");
        } catch (Exception e) {
            logger.warn("Failed to clear session token from Preferences API", e);
        }
        
        // Remove from properties file
        try {
            File configDir = new File(System.getProperty("user.home"), "." + APP_NAME);
            File propsFile = new File(configDir, PROPS_FILE);
            
            if (propsFile.exists() && propsFile.isFile()) {
                if (propsFile.delete()) {
                    logger.debug("Session token properties file deleted");
                } else {
                    logger.warn("Failed to delete session token properties file");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to delete session token properties file", e);
        }
    }
}