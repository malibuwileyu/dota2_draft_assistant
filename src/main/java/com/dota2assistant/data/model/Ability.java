package com.dota2assistant.data.model;

public class Ability {
    private int id;
    private String name;
    private String description;
    private String type; // active, passive, toggle, etc.
    private String behavior; // point target, no target, etc.
    private String damageType; // physical, magical, pure
    private String[] affects; // enemies, allies, self
    private double[] cooldown;
    private double[] manaCost;
    
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
        // Check affects or other properties to determine if ability pierces immunity
        if (affects != null) {
            for (String affect : affects) {
                if ("Magic Immune".equalsIgnoreCase(affect)) {
                    return true;
                }
            }
        }
        return false;
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
}