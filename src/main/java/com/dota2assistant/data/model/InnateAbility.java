package com.dota2assistant.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a hero's innate ability - something inherent to the hero not tied to a specific skill
 */
public class InnateAbility {
    private int id;
    private String name;
    private String description;
    private String type;
    private String behavior;
    @JsonProperty("damage_type")
    private String damageType;
    private String[] affects;
    @JsonProperty("special_values")
    private Map<String, double[]> specialValues = new HashMap<>();
    private String notes;
    
    public InnateAbility() {
    }
    
    public InnateAbility(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = "innate";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public String getDamageType() {
        return damageType;
    }

    public void setDamageType(String damageType) {
        this.damageType = damageType;
    }

    public String[] getAffects() {
        return affects;
    }

    public void setAffects(String[] affects) {
        this.affects = affects;
    }

    public Map<String, double[]> getSpecialValues() {
        return specialValues;
    }

    public void setSpecialValues(Map<String, double[]> specialValues) {
        this.specialValues = specialValues;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return name;
    }
}