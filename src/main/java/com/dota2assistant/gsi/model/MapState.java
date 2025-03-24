package com.dota2assistant.gsi.model;

/**
 * Represents the map state in the GSI data.
 */
public class MapState {
    private String name;
    private long matchId;
    private int gameTime;
    private String gameState;
    private int clockTime;
    private boolean dayTime;
    private boolean nightStalkerNight;
    private int gameMode;
    private double radiantWinChance;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public long getMatchId() {
        return matchId;
    }
    
    public void setMatchId(long matchId) {
        this.matchId = matchId;
    }
    
    public int getGameTime() {
        return gameTime;
    }
    
    public void setGameTime(int gameTime) {
        this.gameTime = gameTime;
    }
    
    public String getGameState() {
        return gameState;
    }
    
    public void setGameState(String gameState) {
        this.gameState = gameState;
    }
    
    public int getClockTime() {
        return clockTime;
    }
    
    public void setClockTime(int clockTime) {
        this.clockTime = clockTime;
    }
    
    public boolean isDayTime() {
        return dayTime;
    }
    
    public void setDayTime(boolean dayTime) {
        this.dayTime = dayTime;
    }
    
    public boolean isNightStalkerNight() {
        return nightStalkerNight;
    }
    
    public void setNightStalkerNight(boolean nightStalkerNight) {
        this.nightStalkerNight = nightStalkerNight;
    }
    
    public int getGameMode() {
        return gameMode;
    }
    
    public void setGameMode(int gameMode) {
        this.gameMode = gameMode;
    }
    
    public double getRadiantWinChance() {
        return radiantWinChance;
    }
    
    public void setRadiantWinChance(double radiantWinChance) {
        this.radiantWinChance = radiantWinChance;
    }
    
    /**
     * Gets a formatted game time string.
     * 
     * @return Game time formatted as MM:SS
     */
    public String getFormattedGameTime() {
        int minutes = Math.abs(gameTime) / 60;
        int seconds = Math.abs(gameTime) % 60;
        String prefix = gameTime < 0 ? "-" : "";
        return prefix + String.format("%d:%02d", minutes, seconds);
    }
}