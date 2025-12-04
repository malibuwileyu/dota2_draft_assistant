package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable representation of a draft's current state.
 * All modifications return new instances - the original is never mutated.
 */
public record DraftState(
    DraftMode mode,
    DraftPhase phase,
    Team currentTeam,
    int turnIndex,
    List<Hero> radiantPicks,
    List<Hero> direPicks,
    List<Hero> radiantBans,
    List<Hero> direBans,
    List<Hero> availableHeroes,
    boolean timerEnabled,
    int remainingTime,
    int radiantReserveTime,
    int direReserveTime,
    List<DraftAction> history
) {
    /**
     * Creates an initial draft state.
     */
    public static DraftState initial(DraftMode mode, boolean timerEnabled, List<Hero> heroes) {
        return new DraftState(
            mode,
            DraftPhase.BAN_1,
            Team.RADIANT,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.copyOf(heroes),
            timerEnabled,
            30,
            130,
            130,
            List.of()
        );
    }
    
    /**
     * Returns a new state with the hero added to the team's picks.
     */
    public DraftState withPick(Team team, Hero hero, DraftPhase phase) {
        var newRadiantPicks = team == Team.RADIANT ? append(radiantPicks, hero) : radiantPicks;
        var newDirePicks = team == Team.DIRE ? append(direPicks, hero) : direPicks;
        var newAvailable = remove(availableHeroes, hero);
        var action = DraftAction.pick(team, hero, turnIndex, phase);
        var newHistory = append(history, action);
        
        return new DraftState(
            mode, phase, currentTeam, turnIndex,
            newRadiantPicks, newDirePicks,
            radiantBans, direBans,
            newAvailable, timerEnabled, remainingTime,
            radiantReserveTime, direReserveTime, newHistory
        );
    }
    
    /**
     * Returns a new state with the hero added to the team's bans.
     */
    public DraftState withBan(Team team, Hero hero, DraftPhase phase) {
        var newRadiantBans = team == Team.RADIANT ? append(radiantBans, hero) : radiantBans;
        var newDireBans = team == Team.DIRE ? append(direBans, hero) : direBans;
        var newAvailable = remove(availableHeroes, hero);
        var action = DraftAction.ban(team, hero, turnIndex, phase);
        var newHistory = append(history, action);
        
        return new DraftState(
            mode, phase, currentTeam, turnIndex,
            radiantPicks, direPicks,
            newRadiantBans, newDireBans,
            newAvailable, timerEnabled, remainingTime,
            radiantReserveTime, direReserveTime, newHistory
        );
    }
    
    /**
     * Returns a new state with updated phase and team.
     */
    public DraftState withTurn(int newTurnIndex, DraftPhase newPhase, Team newTeam) {
        return new DraftState(
            mode, newPhase, newTeam, newTurnIndex,
            radiantPicks, direPicks,
            radiantBans, direBans,
            availableHeroes, timerEnabled, 30,
            radiantReserveTime, direReserveTime, history
        );
    }
    
    /**
     * Checks if the draft is complete.
     */
    public boolean isComplete() {
        return phase == DraftPhase.COMPLETED;
    }
    
    /**
     * Gets total picks for a team.
     */
    public int pickCount(Team team) {
        return team == Team.RADIANT ? radiantPicks.size() : direPicks.size();
    }
    
    /**
     * Gets total bans for a team.
     */
    public int banCount(Team team) {
        return team == Team.RADIANT ? radiantBans.size() : direBans.size();
    }
    
    private static <T> List<T> append(List<T> list, T item) {
        var result = new ArrayList<>(list);
        result.add(item);
        return List.copyOf(result);
    }
    
    private static <T> List<T> remove(List<T> list, T item) {
        return list.stream()
            .filter(i -> !i.equals(item))
            .toList();
    }
}

