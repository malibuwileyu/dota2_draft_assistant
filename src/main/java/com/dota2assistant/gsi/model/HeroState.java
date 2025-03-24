package com.dota2assistant.gsi.model;

/**
 * Represents the player's hero state in the GSI data.
 */
public class HeroState {
    private int id;
    private String name;
    private int level;
    private boolean alive;
    private int respawnSeconds;
    
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
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    
    public int getRespawnSeconds() {
        return respawnSeconds;
    }
    
    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }
}