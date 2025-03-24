package com.dota2assistant.data.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Represents a user preference.
 */
public class UserPreference implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String value;
    private Timestamp updatedDate;
    
    // Default constructor
    public UserPreference() {
    }
    
    // Constructor with name and value
    public UserPreference(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public Timestamp getUpdatedDate() {
        return updatedDate;
    }
    
    public void setUpdatedDate(Timestamp updatedDate) {
        this.updatedDate = updatedDate;
    }
    
    /**
     * Get the boolean value of this preference.
     * 
     * @param defaultValue the default value to return if the preference's value cannot be parsed as a boolean
     * @return the boolean value of this preference
     */
    public boolean getBooleanValue(boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Get the integer value of this preference.
     * 
     * @param defaultValue the default value to return if the preference's value cannot be parsed as an integer
     * @return the integer value of this preference
     */
    public int getIntValue(int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get the double value of this preference.
     * 
     * @param defaultValue the default value to return if the preference's value cannot be parsed as a double
     * @return the double value of this preference
     */
    public double getDoubleValue(double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}