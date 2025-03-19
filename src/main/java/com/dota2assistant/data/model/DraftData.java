package com.dota2assistant.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Represents draft information from a professional Dota 2 match.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DraftData {
    private long matchId;
    private boolean radiantWin;
    private long startTime;
    private int duration;
    private List<DraftAction> radiantPicks;
    private List<DraftAction> direPicks;
    private List<DraftAction> radiantBans;
    private List<DraftAction> direBans;
    private List<DraftAction> draftSequence;
    
    /**
     * Represents a pick or ban action in the draft.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DraftAction {
        private int heroId;
        private int order;
        private String team;
        private String action;
        
        public int getHeroId() {
            return heroId;
        }
        
        public void setHeroId(int heroId) {
            this.heroId = heroId;
        }
        
        public int getOrder() {
            return order;
        }
        
        public void setOrder(int order) {
            this.order = order;
        }
        
        public String getTeam() {
            return team;
        }
        
        public void setTeam(String team) {
            this.team = team;
        }
        
        public String getAction() {
            return action;
        }
        
        public void setAction(String action) {
            this.action = action;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DraftAction that = (DraftAction) o;
            return heroId == that.heroId && 
                   order == that.order && 
                   Objects.equals(team, that.team) && 
                   Objects.equals(action, that.action);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(heroId, order, team, action);
        }
        
        @Override
        public String toString() {
            return "DraftAction{" +
                   "heroId=" + heroId +
                   ", order=" + order +
                   ", team='" + team + '\'' +
                   ", action='" + action + '\'' +
                   '}';
        }
    }
    
    public long getMatchId() {
        return matchId;
    }
    
    public void setMatchId(long matchId) {
        this.matchId = matchId;
    }
    
    public boolean isRadiantWin() {
        return radiantWin;
    }
    
    public void setRadiantWin(boolean radiantWin) {
        this.radiantWin = radiantWin;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public int getDuration() {
        return duration;
    }
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public List<DraftAction> getRadiantPicks() {
        return radiantPicks;
    }
    
    public void setRadiantPicks(List<DraftAction> radiantPicks) {
        this.radiantPicks = radiantPicks;
    }
    
    public List<DraftAction> getDirePicks() {
        return direPicks;
    }
    
    public void setDirePicks(List<DraftAction> direPicks) {
        this.direPicks = direPicks;
    }
    
    public List<DraftAction> getRadiantBans() {
        return radiantBans;
    }
    
    public void setRadiantBans(List<DraftAction> radiantBans) {
        this.radiantBans = radiantBans;
    }
    
    public List<DraftAction> getDireBans() {
        return direBans;
    }
    
    public void setDireBans(List<DraftAction> direBans) {
        this.direBans = direBans;
    }
    
    public List<DraftAction> getDraftSequence() {
        return draftSequence;
    }
    
    public void setDraftSequence(List<DraftAction> draftSequence) {
        this.draftSequence = draftSequence;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftData draftData = (DraftData) o;
        return matchId == draftData.matchId && 
               radiantWin == draftData.radiantWin && 
               startTime == draftData.startTime && 
               duration == draftData.duration;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(matchId, radiantWin, startTime, duration);
    }
    
    @Override
    public String toString() {
        return "DraftData{" +
               "matchId=" + matchId +
               ", radiantWin=" + radiantWin +
               ", startTime=" + startTime +
               ", duration=" + duration +
               ", radiantPicks=" + radiantPicks +
               ", direPicks=" + direPicks +
               ", radiantBans=" + radiantBans +
               ", direBans=" + direBans +
               '}';
    }
}