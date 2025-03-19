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
     * Loads properties from the specified file path.
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
     * Loads sensitive values from environment variables
     */
    private void loadEnvironmentVariables() {
        // API keys should always be loaded from environment variables for security
        String groqApiKey = System.getenv("GROQ_API_KEY");
        if (groqApiKey != null && !groqApiKey.isEmpty()) {
            properties.setProperty("groq.api.key", groqApiKey);
            System.out.println("Loaded Groq API key from environment variable");
        } else {
            System.out.println("No Groq API key found in environment variables");
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