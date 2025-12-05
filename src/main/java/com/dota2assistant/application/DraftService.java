package com.dota2assistant.application;

import com.dota2assistant.domain.draft.*;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import com.dota2assistant.domain.repository.HeroRepository;
import org.springframework.stereotype.Service;

/**
 * Application service that orchestrates draft operations.
 */
@Service
public class DraftService {
    
    private final HeroRepository heroRepository;
    private final CaptainsModeDraft captainsMode;
    private final AllPickDraft allPick;
    
    private DraftState currentState;
    
    public DraftService(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
        this.captainsMode = new CaptainsModeDraft();
        this.allPick = new AllPickDraft();
    }
    
    /**
     * Start a new draft.
     */
    public DraftState initDraft(DraftMode mode, boolean timerEnabled) {
        var heroes = heroRepository.findAll();
        var engine = getEngine(mode);
        currentState = engine.initDraft(heroes, timerEnabled);
        return currentState;
    }
    
    /**
     * Pick a hero by ID.
     */
    public DraftState pickHero(int heroId) {
        var hero = heroRepository.findById(heroId)
            .orElseThrow(() -> new DraftValidationException("Hero not found: " + heroId));
        var engine = getEngine(currentState.mode());
        currentState = engine.pickHero(currentState, hero);
        return currentState;
    }
    
    /**
     * Ban a hero by ID.
     */
    public DraftState banHero(int heroId) {
        var hero = heroRepository.findById(heroId)
            .orElseThrow(() -> new DraftValidationException("Hero not found: " + heroId));
        var engine = getEngine(currentState.mode());
        currentState = engine.banHero(currentState, hero);
        return currentState;
    }
    
    /**
     * Undo the last action.
     */
    public DraftState undo() {
        var engine = getEngine(currentState.mode());
        currentState = engine.undo(currentState);
        return currentState;
    }
    
    /**
     * Get current draft state.
     */
    public DraftState getCurrentState() {
        return currentState;
    }
    
    /**
     * Check if draft is complete.
     */
    public boolean isComplete() {
        return currentState != null && currentState.isComplete();
    }
    
    /**
     * Get current team's turn.
     */
    public Team getCurrentTeam() {
        if (currentState == null) return null;
        return getEngine(currentState.mode()).getCurrentTeam(currentState);
    }
    
    /**
     * Check if current phase is ban phase.
     */
    public boolean isBanPhase() {
        if (currentState == null) return false;
        return getEngine(currentState.mode()).isBanPhase(currentState);
    }
    
    private DraftEngine getEngine(DraftMode mode) {
        return mode == DraftMode.CAPTAINS_MODE ? captainsMode : allPick;
    }
}

