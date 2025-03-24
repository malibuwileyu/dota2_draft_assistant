package com.dota2assistant.gsi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dota2assistant.gsi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the game state received from GSI and notifies listeners of changes.
 */
@Component
public class GsiStateManager {
    private static final Logger logger = LoggerFactory.getLogger(GsiStateManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Current game state
    private GameState currentState;
    
    // Draft state
    private DraftState draftState;
    
    // Listeners for GSI updates
    private final List<GsiUpdateListener> updateListeners = new CopyOnWriteArrayList<>();
    
    // Listeners specifically for draft updates
    private final List<DraftUpdateListener> draftListeners = new CopyOnWriteArrayList<>();
    
    /**
     * Processes a GSI update from Dota 2.
     * 
     * @param jsonData The raw JSON data from the GSI update
     */
    public void processGsiUpdate(String jsonData) {
        try {
            // Parse the JSON data
            JsonNode rootNode = objectMapper.readTree(jsonData);
            
            // Check if this is a draft update
            if (rootNode.has("draft")) {
                processDraftUpdate(rootNode.get("draft"));
            }
            
            // Process the full game state
            GameState newState = createGameStateFromJson(rootNode);
            updateGameState(newState);
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing GSI JSON data", e);
        } catch (Exception e) {
            logger.error("Error processing GSI update", e);
        }
    }
    
    /**
     * Creates a GameState object from the GSI JSON data.
     * 
     * @param rootNode The root JSON node
     * @return A new GameState object
     */
    private GameState createGameStateFromJson(JsonNode rootNode) {
        GameState state = new GameState();
        
        // Extract provider info
        if (rootNode.has("provider")) {
            JsonNode provider = rootNode.get("provider");
            state.setTimestamp(provider.path("timestamp").asLong());
            state.setAppid(provider.path("appid").asInt());
            state.setName(provider.path("name").asText());
        }
        
        // Extract map info
        if (rootNode.has("map")) {
            JsonNode map = rootNode.get("map");
            MapState mapState = new MapState();
            mapState.setName(map.path("name").asText());
            mapState.setMatchId(map.path("matchid").asLong());
            mapState.setGameTime(map.path("game_time").asInt());
            mapState.setGameState(map.path("game_state").asText());
            mapState.setClockTime(map.path("clock_time").asInt());
            mapState.setDayTime(map.path("daytime").asBoolean());
            mapState.setNightStalkerNight(map.path("nightstalker_night").asBoolean());
            mapState.setGameMode(map.path("game_mode").asInt());
            mapState.setRadiantWinChance(map.path("radiant_win_chance").asDouble());
            state.setMapState(mapState);
        }
        
        // Extract player info
        if (rootNode.has("player")) {
            JsonNode player = rootNode.get("player");
            PlayerState playerState = new PlayerState();
            playerState.setSteamId(player.path("steamid").asText());
            playerState.setName(player.path("name").asText());
            playerState.setTeam(player.path("team_name").asText());
            playerState.setTeamId(player.path("team_slot").asInt());
            state.setPlayerState(playerState);
        }
        
        // Extract hero info if available
        if (rootNode.has("hero")) {
            JsonNode hero = rootNode.get("hero");
            HeroState heroState = new HeroState();
            heroState.setId(hero.path("id").asInt());
            heroState.setName(hero.path("name").asText());
            heroState.setLevel(hero.path("level").asInt());
            heroState.setAlive(hero.path("alive").asBoolean());
            heroState.setRespawnSeconds(hero.path("respawn_seconds").asInt());
            state.setHeroState(heroState);
        }
        
        return state;
    }
    
    /**
     * Processes a draft update from the GSI data.
     * 
     * @param draftNode The draft JSON node
     */
    private void processDraftUpdate(JsonNode draftNode) {
        try {
            // Parse the draft state
            DraftState newDraftState = new DraftState();
            
            // Get active team (0 for Radiant, 1 for Dire)
            newDraftState.setActiveTeam(draftNode.path("activeteam").asInt());
            
            // Get pick/ban state
            newDraftState.setActiveteamPickIndex(draftNode.path("activeteam_time_remaining").asInt());
            newDraftState.setPhase(draftNode.path("phase").asText());
            
            // Parse team picks and bans
            List<DraftPick> radiantPicks = new ArrayList<>();
            List<DraftPick> direPicks = new ArrayList<>();
            List<DraftPick> radiantBans = new ArrayList<>();
            List<DraftPick> direBans = new ArrayList<>();
            
            // Process team picks
            if (draftNode.has("team2") && draftNode.get("team2").has("picks")) {
                JsonNode picks = draftNode.get("team2").get("picks");
                for (JsonNode pickNode : picks) {
                    DraftPick pick = new DraftPick();
                    pick.setHeroId(pickNode.path("hero_id").asInt());
                    radiantPicks.add(pick);
                }
            }
            
            if (draftNode.has("team3") && draftNode.get("team3").has("picks")) {
                JsonNode picks = draftNode.get("team3").get("picks");
                for (JsonNode pickNode : picks) {
                    DraftPick pick = new DraftPick();
                    pick.setHeroId(pickNode.path("hero_id").asInt());
                    direPicks.add(pick);
                }
            }
            
            // Process team bans
            if (draftNode.has("team2") && draftNode.get("team2").has("bans")) {
                JsonNode bans = draftNode.get("team2").get("bans");
                for (JsonNode banNode : bans) {
                    DraftPick ban = new DraftPick();
                    ban.setHeroId(banNode.path("hero_id").asInt());
                    radiantBans.add(ban);
                }
            }
            
            if (draftNode.has("team3") && draftNode.get("team3").has("bans")) {
                JsonNode bans = draftNode.get("team3").get("bans");
                for (JsonNode banNode : bans) {
                    DraftPick ban = new DraftPick();
                    ban.setHeroId(banNode.path("hero_id").asInt());
                    direBans.add(ban);
                }
            }
            
            newDraftState.setRadiantPicks(radiantPicks);
            newDraftState.setDirePicks(direPicks);
            newDraftState.setRadiantBans(radiantBans);
            newDraftState.setDireBans(direBans);
            
            // Update the draft state and notify listeners
            updateDraftState(newDraftState);
            
        } catch (Exception e) {
            logger.error("Error processing draft update", e);
        }
    }
    
    /**
     * Updates the current game state and notifies listeners.
     * 
     * @param newState The new game state
     */
    private void updateGameState(GameState newState) {
        boolean isInitialState = (currentState == null);
        this.currentState = newState;
        
        // Notify listeners
        for (GsiUpdateListener listener : updateListeners) {
            if (isInitialState) {
                listener.onGameStateInitialized(newState);
            } else {
                listener.onGameStateUpdated(newState);
            }
        }
    }
    
    /**
     * Updates the draft state and notifies listeners.
     * 
     * @param newDraftState The new draft state
     */
    private void updateDraftState(DraftState newDraftState) {
        boolean isInitialDraft = (draftState == null);
        boolean hasChanged = !newDraftState.equals(draftState);
        this.draftState = newDraftState;
        
        // Only notify if this is the first draft update or something has changed
        if (isInitialDraft || hasChanged) {
            for (DraftUpdateListener listener : draftListeners) {
                if (isInitialDraft) {
                    listener.onDraftStarted(newDraftState);
                } else {
                    listener.onDraftUpdated(newDraftState);
                }
            }
        }
    }
    
    /**
     * Adds a listener for GSI updates.
     * 
     * @param listener The listener to add
     */
    public void addUpdateListener(GsiUpdateListener listener) {
        updateListeners.add(listener);
        
        // If there's already state, notify the new listener immediately
        if (currentState != null) {
            listener.onGameStateInitialized(currentState);
        }
    }
    
    /**
     * Removes a listener for GSI updates.
     * 
     * @param listener The listener to remove
     */
    public void removeUpdateListener(GsiUpdateListener listener) {
        updateListeners.remove(listener);
    }
    
    /**
     * Adds a listener for draft updates.
     * 
     * @param listener The listener to add
     */
    public void addDraftListener(DraftUpdateListener listener) {
        draftListeners.add(listener);
        
        // If there's already a draft in progress, notify the new listener immediately
        if (draftState != null) {
            listener.onDraftStarted(draftState);
        }
    }
    
    /**
     * Removes a listener for draft updates.
     * 
     * @param listener The listener to remove
     */
    public void removeDraftListener(DraftUpdateListener listener) {
        draftListeners.remove(listener);
    }
    
    /**
     * Gets the current game state.
     * 
     * @return The current game state, or null if no state has been received
     */
    public GameState getCurrentState() {
        return currentState;
    }
    
    /**
     * Gets the current draft state.
     * 
     * @return The current draft state, or null if no draft is in progress
     */
    public DraftState getDraftState() {
        return draftState;
    }
    
    /**
     * Interface for objects that want to be notified of GSI updates.
     */
    public interface GsiUpdateListener {
        /**
         * Called when the game state is first initialized.
         * 
         * @param state The initial game state
         */
        void onGameStateInitialized(GameState state);
        
        /**
         * Called when the game state is updated.
         * 
         * @param state The updated game state
         */
        void onGameStateUpdated(GameState state);
    }
    
    /**
     * Interface for objects that want to be notified of draft updates.
     */
    public interface DraftUpdateListener {
        /**
         * Called when a draft is started.
         * 
         * @param draftState The initial draft state
         */
        void onDraftStarted(DraftState draftState);
        
        /**
         * Called when the draft state is updated.
         * 
         * @param draftState The updated draft state
         */
        void onDraftUpdated(DraftState draftState);
    }
}