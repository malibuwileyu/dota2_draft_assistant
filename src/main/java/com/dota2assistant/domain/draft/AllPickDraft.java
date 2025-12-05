package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;

import java.util.List;

/**
 * All Pick draft mode.
 * Both teams can pick simultaneously (in simulation, we alternate).
 * No formal ban phase - heroes become unavailable when picked.
 * Draft ends when both teams have 5 picks.
 */
public class AllPickDraft implements DraftEngine {
    
    private static final int PICKS_PER_TEAM = 5;
    
    @Override
    public DraftState initDraft(List<Hero> availableHeroes, boolean timerEnabled) {
        if (availableHeroes == null || availableHeroes.isEmpty()) {
            throw new DraftValidationException("Cannot start draft with no heroes");
        }
        
        return new DraftState(
            DraftMode.ALL_PICK,
            DraftPhase.PICK_1,  // All Pick uses PICK_1 throughout
            Team.RADIANT,       // Start with Radiant
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.copyOf(availableHeroes),
            timerEnabled,
            30,
            0,   // No reserve time in All Pick
            0,
            List.of()
        );
    }
    
    @Override
    public DraftState pickHero(DraftState state, Hero hero) {
        validateNotComplete(state);
        validateHeroAvailable(state, hero);
        
        Team team = state.currentTeam();
        DraftState newState = state.withPick(team, hero, DraftPhase.PICK_1);
        
        // Check if draft is complete
        if (newState.radiantPicks().size() >= PICKS_PER_TEAM 
                && newState.direPicks().size() >= PICKS_PER_TEAM) {
            return newState.withTurn(state.turnIndex() + 1, DraftPhase.COMPLETED, null);
        }
        
        // Alternate teams
        Team nextTeam = team == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        return newState.withTurn(state.turnIndex() + 1, DraftPhase.PICK_1, nextTeam);
    }
    
    @Override
    public DraftState banHero(DraftState state, Hero hero) {
        // All Pick doesn't have a formal ban phase
        throw new InvalidDraftPhaseException(state.phase(), "ban in All Pick mode");
    }
    
    @Override
    public DraftState undo(DraftState state) {
        if (state.history().isEmpty()) {
            throw new DraftValidationException("No actions to undo");
        }
        
        // Reconstruct state from scratch up to (history.size - 1) actions
        DraftState fresh = initDraft(reconstructAllHeroes(state), state.timerEnabled());
        List<DraftAction> actions = state.history();
        
        for (int i = 0; i < actions.size() - 1; i++) {
            DraftAction action = actions.get(i);
            fresh = pickHero(fresh, action.hero());
        }
        
        return fresh;
    }
    
    @Override
    public DraftState redo(DraftState state) {
        throw new DraftValidationException("Redo not yet implemented");
    }
    
    @Override
    public boolean isComplete(DraftState state) {
        return state.phase() == DraftPhase.COMPLETED;
    }
    
    @Override
    public Team getCurrentTeam(DraftState state) {
        if (isComplete(state)) {
            return null;
        }
        return state.currentTeam();
    }
    
    @Override
    public DraftPhase getCurrentPhase(DraftState state) {
        return state.phase();
    }
    
    @Override
    public DraftMode getMode() {
        return DraftMode.ALL_PICK;
    }
    
    private void validateNotComplete(DraftState state) {
        if (state.phase() == DraftPhase.COMPLETED) {
            throw new DraftValidationException("Draft is already complete");
        }
    }
    
    private void validateHeroAvailable(DraftState state, Hero hero) {
        if (hero == null) {
            throw new DraftValidationException("Hero cannot be null");
        }
        if (!state.availableHeroes().contains(hero)) {
            throw new DraftValidationException(
                "Hero not available: " + hero.localizedName()
            );
        }
    }
    
    private List<Hero> reconstructAllHeroes(DraftState state) {
        var allHeroes = new java.util.ArrayList<>(state.availableHeroes());
        allHeroes.addAll(state.radiantPicks());
        allHeroes.addAll(state.direPicks());
        return allHeroes;
    }
}

