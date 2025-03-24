package com.dota2assistant.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class Ability {
    private int id;
    private String name;
    private String description;
    private String type; // active, passive, toggle, etc.
    private String behavior; // point target, no target, etc.
    @JsonProperty("damage_type")
    private String damageType; // physical, magical, pure
    private String[] affects; // enemies, allies, self
    private double[] cooldown;
    @JsonProperty("mana_cost")
    private double[] manaCost;
    @JsonProperty("pierces_immunity")
    private Boolean piercesImmunity;
    @JsonProperty("special_values")
    private Map<String, Object> specialValues = new HashMap<>();
    private String notes;
    
    public Ability() {
    }
    
    public Ability(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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
    
    public boolean isPiercesImmunity() {
        if (piercesImmunity != null) {
            return piercesImmunity;
        }
        
        // Fallback to checking affects as before
        if (affects != null) {
            for (String affect : affects) {
                if ("Magic Immune".equalsIgnoreCase(affect)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public Boolean getPiercesImmunity() {
        return piercesImmunity;
    }
    
    public void setPiercesImmunity(Boolean piercesImmunity) {
        this.piercesImmunity = piercesImmunity;
    }

    public double[] getCooldown() {
        return cooldown;
    }

    public void setCooldown(double[] cooldown) {
        this.cooldown = cooldown;
    }

    public double[] getManaCost() {
        return manaCost;
    }

    public void setManaCost(double[] manaCost) {
        this.manaCost = manaCost;
    }
    
    public Map<String, Object> getSpecialValues() {
        return specialValues;
    }

    public void setSpecialValues(Map<String, Object> specialValues) {
        this.specialValues = specialValues;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}