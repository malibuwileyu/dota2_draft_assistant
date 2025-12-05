package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;

import java.util.List;

/**
 * Core interface for draft simulation logic.
 * Implementations handle mode-specific rules (Captain's Mode, All Pick).
 * 
 * All methods return new immutable DraftState instances - no mutation.
 */
public interface DraftEngine {
    
    /**
     * Initialize a new draft with the given mode and available heroes.
     *
     * @param availableHeroes All heroes that can be picked/banned
     * @param timerEnabled Whether to enforce time limits
     * @return Initial draft state
     */
    DraftState initDraft(List<Hero> availableHeroes, boolean timerEnabled);
    
    /**
     * Pick a hero for the current team.
     *
     * @param state Current draft state
     * @param hero Hero to pick
     * @return New draft state with the pick applied
     * @throws InvalidDraftPhaseException if picking is not allowed in current phase
     * @throws DraftValidationException if hero is not available
     */
    DraftState pickHero(DraftState state, Hero hero);
    
    /**
     * Ban a hero from the draft pool.
     *
     * @param state Current draft state
     * @param hero Hero to ban
     * @return New draft state with the ban applied
     * @throws InvalidDraftPhaseException if banning is not allowed in current phase
     * @throws DraftValidationException if hero is not available
     */
    DraftState banHero(DraftState state, Hero hero);
    
    /**
     * Undo the last action and return to previous state.
     *
     * @param state Current draft state
     * @return Previous draft state
     * @throws DraftValidationException if no actions to undo
     */
    DraftState undo(DraftState state);
    
    /**
     * Redo a previously undone action.
     *
     * @param state Current draft state
     * @return Draft state with the action reapplied
     * @throws DraftValidationException if no actions to redo
     */
    DraftState redo(DraftState state);
    
    /**
     * Check if the draft is complete.
     */
    boolean isComplete(DraftState state);
    
    /**
     * Get the team whose turn it is.
     */
    Team getCurrentTeam(DraftState state);
    
    /**
     * Get the current phase of the draft.
     */
    DraftPhase getCurrentPhase(DraftState state);
    
    /**
     * Check if the current phase is a ban phase.
     */
    default boolean isBanPhase(DraftState state) {
        return getCurrentPhase(state).isBanPhase();
    }
    
    /**
     * Get the draft mode this engine handles.
     */
    DraftMode getMode();
}

