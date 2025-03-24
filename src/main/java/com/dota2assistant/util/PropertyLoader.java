package com.dota2assistant.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for loading application properties.
 */
public class PropertyLoader {
    
    private static final String DEFAULT_PROPERTIES_FILE = "/application.properties";
    
    private final Properties properties = new Properties();
    
    /**
     * Creates a new PropertyLoader and loads properties from the default properties file.
     */
    public PropertyLoader() {
        loadProperties(DEFAULT_PROPERTIES_FILE);
        // Try to load override properties if they exist
        loadExternalFile("./application.properties.override");
        loadEnvironmentVariables();
    }
    
    /**
     * Creates a new PropertyLoader and loads properties from the specified file path.
     * 
     * @param propertiesFilePath The path to the properties file to load
     */
    public PropertyLoader(String propertiesFilePath) {
        loadProperties(propertiesFilePath);
        loadEnvironmentVariables();
    }
    
    /**
     * Loads properties from the specified file path in classpath.
     * 
     * @param propertiesFilePath The path to the properties file to load
     */
    private void loadProperties(String propertiesFilePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(propertiesFilePath)) {
            if (inputStream != null) {
                properties.load(inputStream);
                System.out.println("Loaded properties from " + propertiesFilePath);
            } else {
                System.err.println("Could not find properties file: " + propertiesFilePath);
            }
        } catch (IOException e) {
            System.err.println("Error loading properties from " + propertiesFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Loads properties from an external file on the file system.
     * 
     * @param filePath The file system path to the properties file
     */
    private void loadExternalFile(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (file.exists() && file.isFile()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                properties.load(fis);
                System.out.println("Loaded external properties from " + filePath);
            } catch (IOException e) {
                System.err.println("Error loading external properties from " + filePath + ": " + e.getMessage());
            }
        } else {
            System.out.println("External properties file not found: " + filePath);
        }
    }
    
    /**
     * Loads sensitive values from environment variables
     */
    private void loadEnvironmentVariables() {
        // API keys should always be loaded from environment variables for security
        
        // Load Groq API key
        String groqApiKey = System.getenv("GROQ_API_KEY");
        if (groqApiKey != null && !groqApiKey.isEmpty()) {
            properties.setProperty("groq.api.key", groqApiKey);
            System.out.println("Loaded Groq API key from environment variable");
        } else {
            System.out.println("No Groq API key found in environment variables");
        }
        
        // Load Steam API key
        String steamApiKey = System.getenv("STEAM_API_KEY");
        if (steamApiKey != null && !steamApiKey.isEmpty()) {
            properties.setProperty("steam.api.key", steamApiKey);
            System.out.println("Loaded Steam API key from environment variable");
        } else {
            // Make sure we have a valid key in the properties
            String currentKey = properties.getProperty("steam.api.key", "");
            if (currentKey.isEmpty() || currentKey.equals("${STEAM_API_KEY}")) {
                // Set a hardcoded key for development
                properties.setProperty("steam.api.key", "42A0C9F06F162BD5220B252E417B0D83");
                System.out.println("Setting hardcoded Steam API key for development");
            } else {
                System.out.println("Using Steam API key from properties file");
            }
        }
        
        // Load OpenDota API key
        String openDotaApiKey = System.getenv("OPENDOTA_API_KEY");
        if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
            properties.setProperty("opendota.api.key", openDotaApiKey);
            System.out.println("Loaded OpenDota API key from environment variable");
        } else {
            // Check if we have a valid key in the properties
            String currentKey = properties.getProperty("opendota.api.key", "");
            if (currentKey.isEmpty()) {
                // The key from application.properties.override should be loaded here if it exists
                System.out.println("No OpenDota API key found in environment variables or properties");
            } else {
                System.out.println("Using OpenDota API key from properties file");
            }
        }
    }
    
    /**
     * Gets a property value as a string.
     * 
     * @param key The property key
     * @return The property value, or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Gets a property value as a string, with a default value if not found.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found
     * @return The property value, or defaultValue if not found
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Gets a property value as an integer.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found or is not a valid integer
     * @return The property value as an integer, or defaultValue if not found or not a valid integer
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Property " + key + " is not a valid integer: " + value);
            return defaultValue;
        }
    }
    
    /**
     * Gets a property value as a double.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found or is not a valid double
     * @return The property value as a double, or defaultValue if not found or not a valid double
     */
    public double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("Property " + key + " is not a valid double: " + value);
            return defaultValue;
        }
    }
    
    /**
     * Gets a property value as a boolean.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found
     * @return The property value as a boolean, or defaultValue if not found
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Sets a property value.
     * 
     * @param key The property key
     * @param value The property value
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    /**
     * Checks if a property exists
     * 
     * @param key The property key
     * @return True if the property exists, false otherwise
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
}