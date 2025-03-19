package com.dota2assistant.core.draft;

import com.dota2assistant.data.model.Hero;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.List;

public interface DraftEngine {
    
    /**
     * Initializes a new draft session.
     * 
     * @param draftMode The draft mode to use (e.g., CAPTAINS_MODE, ALL_PICK)
     * @param timerEnabled Whether timers should be enabled
     */
    void initDraft(DraftMode draftMode, boolean timerEnabled);
    
    /**
     * Gets the current state of the draft.
     * 
     * @return The current draft state
     */
    DraftState getCurrentState();
    
    /**
     * Gets the list of heroes that are currently available for selection.
     * 
     * @return List of available heroes
     */
    List<Hero> getAvailableHeroes();
    
    /**
     * Selects a hero for the current draft phase.
     * 
     * @param hero The hero to select
     * @return true if the selection was successful, false otherwise
     */
    boolean selectHero(Hero hero);
    
    /**
     * Bans a hero from the draft.
     * 
     * @param hero The hero to ban
     * @return true if the ban was successful, false otherwise
     */
    boolean banHero(Hero hero);
    
    /**
     * Gets the list of heroes that have been picked by a team.
     * 
     * @param team The team (RADIANT or DIRE)
     * @return List of heroes picked by the team
     */
    List<Hero> getTeamPicks(Team team);
    
    /**
     * Gets the list of heroes that have been banned.
     * 
     * @return List of banned heroes
     */
    List<Hero> getBannedHeroes();
    
    /**
     * Checks if the draft is in progress.
     * 
     * @return true if the draft is in progress, false otherwise
     */
    boolean isDraftInProgress();
    
    /**
     * Checks if the draft is complete.
     * 
     * @return true if the draft is complete, false otherwise
     */
    boolean isDraftComplete();
    
    /**
     * Gets the remaining time for the current phase.
     * 
     * @return Remaining time in seconds
     */
    int getRemainingTime();
    
    /**
     * Resets the draft to its initial state.
     */
    void resetDraft();
    
    /**
     * Gets a property that indicates whether it is the player's turn.
     * 
     * @return ReadOnlyBooleanProperty that is true when it is the player's turn
     */
    ReadOnlyBooleanProperty playerTurnProperty();
    
    /**
     * Gets the current team whose turn it is.
     * 
     * @return The current team
     */
    Team getCurrentTeam();
    
    /**
     * Gets the current phase of the draft.
     * 
     * @return The current draft phase
     */
    DraftPhase getCurrentPhase();
    
    /**
     * Gets the current turn index in the draft sequence.
     * 
     * @return The current turn index (0-based)
     */
    int getCurrentTurnIndex();
}