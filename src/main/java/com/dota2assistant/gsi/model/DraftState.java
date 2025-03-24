package com.dota2assistant.gsi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the draft state in the GSI data.
 */
public class DraftState {
    private int activeTeam; // 0 = Radiant, 1 = Dire
    private int activeteamPickIndex;
    private String phase;  // pick, ban
    private List<DraftPick> radiantPicks = new ArrayList<>();
    private List<DraftPick> direPicks = new ArrayList<>();
    private List<DraftPick> radiantBans = new ArrayList<>();
    private List<DraftPick> direBans = new ArrayList<>();
    
    public int getActiveTeam() {
        return activeTeam;
    }
    
    public void setActiveTeam(int activeTeam) {
        this.activeTeam = activeTeam;
    }
    
    public int getActiveteamPickIndex() {
        return activeteamPickIndex;
    }
    
    public void setActiveteamPickIndex(int activeteamPickIndex) {
        this.activeteamPickIndex = activeteamPickIndex;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public void setPhase(String phase) {
        this.phase = phase;
    }
    
    public List<DraftPick> getRadiantPicks() {
        return radiantPicks;
    }
    
    public void setRadiantPicks(List<DraftPick> radiantPicks) {
        this.radiantPicks = radiantPicks != null ? radiantPicks : new ArrayList<>();
    }
    
    public List<DraftPick> getDirePicks() {
        return direPicks;
    }
    
    public void setDirePicks(List<DraftPick> direPicks) {
        this.direPicks = direPicks != null ? direPicks : new ArrayList<>();
    }
    
    public List<DraftPick> getRadiantBans() {
        return radiantBans;
    }
    
    public void setRadiantBans(List<DraftPick> radiantBans) {
        this.radiantBans = radiantBans != null ? radiantBans : new ArrayList<>();
    }
    
    public List<DraftPick> getDireBans() {
        return direBans;
    }
    
    public void setDireBans(List<DraftPick> direBans) {
        this.direBans = direBans != null ? direBans : new ArrayList<>();
    }
    
    /**
     * Gets all picked hero IDs from both teams.
     * 
     * @return List of hero IDs that have been picked
     */
    public List<Integer> getAllPickedHeroIds() {
        List<Integer> picks = new ArrayList<>();
        
        for (DraftPick pick : radiantPicks) {
            picks.add(pick.getHeroId());
        }
        
        for (DraftPick pick : direPicks) {
            picks.add(pick.getHeroId());
        }
        
        return picks;
    }
    
    /**
     * Gets all banned hero IDs from both teams.
     * 
     * @return List of hero IDs that have been banned
     */
    public List<Integer> getAllBannedHeroIds() {
        List<Integer> bans = new ArrayList<>();
        
        for (DraftPick ban : radiantBans) {
            bans.add(ban.getHeroId());
        }
        
        for (DraftPick ban : direBans) {
            bans.add(ban.getHeroId());
        }
        
        return bans;
    }
    
    /**
     * Gets the currently active team's picks.
     * 
     * @return The list of picks for the active team
     */
    public List<DraftPick> getActiveTeamPicks() {
        return activeTeam == 0 ? radiantPicks : direPicks;
    }
    
    /**
     * Gets the opposing team's picks.
     * 
     * @return The list of picks for the opposing team
     */
    public List<DraftPick> getOpposingTeamPicks() {
        return activeTeam == 0 ? direPicks : radiantPicks;
    }
    
    /**
     * Determines if the draft is complete.
     * 
     * @return true if both teams have completed their picks
     */
    public boolean isDraftComplete() {
        // In a standard Dota 2 draft, each team picks 5 heroes
        return radiantPicks.size() == 5 && direPicks.size() == 5;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftState that = (DraftState) o;
        return activeTeam == that.activeTeam &&
               activeteamPickIndex == that.activeteamPickIndex &&
               Objects.equals(phase, that.phase) &&
               Objects.equals(radiantPicks, that.radiantPicks) &&
               Objects.equals(direPicks, that.direPicks) &&
               Objects.equals(radiantBans, that.radiantBans) &&
               Objects.equals(direBans, that.direBans);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(activeTeam, activeteamPickIndex, phase, radiantPicks, direPicks, radiantBans, direBans);
    }
}