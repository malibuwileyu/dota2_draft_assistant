package com.dota2assistant.gsi.model;

/**
 * Represents the player state in the GSI data.
 */
public class PlayerState {
    private String steamId;
    private String name;
    private int teamId; // 0 = Radiant, 1 = Dire
    private String team;
    
    public String getSteamId() {
        return steamId;
    }
    
    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getTeamId() {
        return teamId;
    }
    
    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }
    
    public String getTeam() {
        return team;
    }
    
    public void setTeam(String team) {
        this.team = team;
    }
    
    /**
     * Determines if the player is on the Radiant team.
     * 
     * @return true if the player is on Radiant, false if on Dire
     */
    public boolean isRadiant() {
        return teamId == 0 || "radiant".equalsIgnoreCase(team);
    }
}