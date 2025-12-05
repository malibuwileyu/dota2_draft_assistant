package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * Captain's Mode draft engine implementing the official Dota 2 draft sequence.
 * Sequence: Ban1(7: ABBABBA) → Pick1(2: AB) → Ban2(3: AAB) → Pick2(6: BAABBA) → Ban3(4: ABBA) → Pick3(2: AB)
 */
public class CaptainsModeDraft implements DraftEngine {
    
    private record TurnInfo(Team team, DraftPhase phase, boolean isBan) {}
    
    private static final List<TurnInfo> SEQUENCE = List.of(
        // Ban Phase 1: ABBABBA (7 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        // Pick Phase 1: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_1, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_1, false),
        // Ban Phase 2: AAB (3 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_2, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_2, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_2, true),
        // Pick Phase 2: BAABBA (6 picks)
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        // Ban Phase 3: ABBA (4 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_3, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_3, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_3, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_3, true),
        // Pick Phase 3: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_3, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_3, false)
    );
    
    @Override
    public DraftState initDraft(List<Hero> availableHeroes, boolean timerEnabled) {
        if (availableHeroes == null || availableHeroes.isEmpty()) {
            throw new DraftValidationException("Cannot start draft with no heroes");
        }
        TurnInfo first = SEQUENCE.getFirst();
        return new DraftState(DraftMode.CAPTAINS_MODE, first.phase(), first.team(), 0,
            List.of(), List.of(), List.of(), List.of(), List.copyOf(availableHeroes),
            timerEnabled, 30, 130, 130, List.of());
    }
    
    @Override
    public DraftState pickHero(DraftState state, Hero hero) {
        validateNotComplete(state);
        validateHeroAvailable(state, hero);
        TurnInfo turn = SEQUENCE.get(state.turnIndex());
        if (turn.isBan()) throw new InvalidDraftPhaseException(state.phase(), "pick");
        return advanceToNextTurn(state.withPick(turn.team(), hero, turn.phase()));
    }
    
    @Override
    public DraftState banHero(DraftState state, Hero hero) {
        validateNotComplete(state);
        validateHeroAvailable(state, hero);
        TurnInfo turn = SEQUENCE.get(state.turnIndex());
        if (!turn.isBan()) throw new InvalidDraftPhaseException(state.phase(), "ban");
        return advanceToNextTurn(state.withBan(turn.team(), hero, turn.phase()));
    }
    
    @Override
    public DraftState undo(DraftState state) {
        if (state.history().isEmpty()) throw new DraftValidationException("No actions to undo");
        DraftState fresh = initDraft(reconstructAllHeroes(state), state.timerEnabled());
        for (int i = 0; i < state.history().size() - 1; i++) {
            DraftAction a = state.history().get(i);
            fresh = a.type() == ActionType.BAN ? banHero(fresh, a.hero()) : pickHero(fresh, a.hero());
        }
        return fresh;
    }
    
    @Override
    public DraftState redo(DraftState state) {
        throw new DraftValidationException("Redo not yet implemented");
    }
    
    @Override
    public boolean isComplete(DraftState state) { return state.phase() == DraftPhase.COMPLETED; }
    
    @Override
    public Team getCurrentTeam(DraftState state) {
        return isComplete(state) ? null : SEQUENCE.get(state.turnIndex()).team();
    }
    
    @Override
    public DraftPhase getCurrentPhase(DraftState state) { return state.phase(); }
    
    @Override
    public DraftMode getMode() { return DraftMode.CAPTAINS_MODE; }
    
    private DraftState advanceToNextTurn(DraftState state) {
        int next = state.turnIndex() + 1;
        if (next >= SEQUENCE.size()) return state.withTurn(next, DraftPhase.COMPLETED, null);
        TurnInfo t = SEQUENCE.get(next);
        return state.withTurn(next, t.phase(), t.team());
    }
    
    private void validateNotComplete(DraftState state) {
        if (state.phase() == DraftPhase.COMPLETED) throw new DraftValidationException("Draft is already complete");
    }
    
    private void validateHeroAvailable(DraftState state, Hero hero) {
        if (hero == null) throw new DraftValidationException("Hero cannot be null");
        if (!state.availableHeroes().contains(hero))
            throw new DraftValidationException("Hero not available: " + hero.localizedName());
    }
    
    private List<Hero> reconstructAllHeroes(DraftState state) {
        var all = new ArrayList<>(state.availableHeroes());
        all.addAll(state.radiantPicks());
        all.addAll(state.direPicks());
        all.addAll(state.radiantBans());
        all.addAll(state.direBans());
        return all;
    }
}
