package com.dota2assistant.gsi.model;

/**
 * Represents the overall game state from GSI.
 */
public class GameState {
    private long timestamp;
    private int appid;
    private String name;
    private MapState mapState;
    private PlayerState playerState;
    private HeroState heroState;
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getAppid() {
        return appid;
    }
    
    public void setAppid(int appid) {
        this.appid = appid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public MapState getMapState() {
        return mapState;
    }
    
    public void setMapState(MapState mapState) {
        this.mapState = mapState;
    }
    
    public PlayerState getPlayerState() {
        return playerState;
    }
    
    public void setPlayerState(PlayerState playerState) {
        this.playerState = playerState;
    }
    
    public HeroState getHeroState() {
        return heroState;
    }
    
    public void setHeroState(HeroState heroState) {
        this.heroState = heroState;
    }
    
    /**
     * Determines if this game state represents a match in draft mode.
     * 
     * @return true if the game is in draft phase, false otherwise
     */
    public boolean isInDraftPhase() {
        return mapState != null && "DOTA_GAMERULES_STATE_HERO_SELECTION".equals(mapState.getGameState());
    }
    
    /**
     * Determines if this game state represents an active match.
     * 
     * @return true if the game is active, false otherwise
     */
    public boolean isInActiveMatch() {
        if (mapState == null) {
            return false;
        }
        
        String gameState = mapState.getGameState();
        return "DOTA_GAMERULES_STATE_GAME_IN_PROGRESS".equals(gameState) ||
               "DOTA_GAMERULES_STATE_PRE_GAME".equals(gameState);
    }
}