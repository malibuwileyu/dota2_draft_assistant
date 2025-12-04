package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import java.time.Instant;

/**
 * Represents a single pick or ban action in a draft.
 * Immutable record for maintaining draft history.
 */
public record DraftAction(
    ActionType type,
    Team team,
    Hero hero,
    int turnIndex,
    DraftPhase phase,
    Instant timestamp
) {
    /**
     * Creates a pick action.
     */
    public static DraftAction pick(Team team, Hero hero, int turnIndex, DraftPhase phase) {
        return new DraftAction(ActionType.PICK, team, hero, turnIndex, phase, Instant.now());
    }
    
    /**
     * Creates a ban action.
     */
    public static DraftAction ban(Team team, Hero hero, int turnIndex, DraftPhase phase) {
        return new DraftAction(ActionType.BAN, team, hero, turnIndex, phase, Instant.now());
    }
}

