package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("All Pick Draft Engine")
class AllPickDraftTest {
    
    private AllPickDraft engine;
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        engine = new AllPickDraft();
        testHeroes = createTestHeroes(20);
    }
    
    @Test
    @DisplayName("initDraft sets correct initial state for All Pick")
    void initDraft_setsCorrectInitialState() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThat(state.mode()).isEqualTo(DraftMode.ALL_PICK);
        assertThat(state.phase()).isEqualTo(DraftPhase.PICK_1);
        assertThat(state.currentTeam()).isEqualTo(Team.RADIANT);
        assertThat(state.turnIndex()).isEqualTo(0);
        assertThat(state.radiantPicks()).isEmpty();
        assertThat(state.direPicks()).isEmpty();
        assertThat(state.radiantBans()).isEmpty();
        assertThat(state.direBans()).isEmpty();
        assertThat(state.availableHeroes()).hasSize(20);
    }
    
    @Test
    @DisplayName("initDraft with empty heroes throws exception")
    void initDraft_emptyHeroes_throwsException() {
        assertThatThrownBy(() -> engine.initDraft(List.of(), false))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("no heroes");
    }
    
    @Test
    @DisplayName("initDraft with null heroes throws exception")
    void initDraft_nullHeroes_throwsException() {
        assertThatThrownBy(() -> engine.initDraft(null, false))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("no heroes");
    }
    
    @Test
    @DisplayName("pickHero removes hero from available pool")
    void pickHero_removesFromAvailable() {
        DraftState state = engine.initDraft(testHeroes, false);
        Hero heroToPick = testHeroes.get(0);
        
        DraftState newState = engine.pickHero(state, heroToPick);
        
        assertThat(newState.availableHeroes()).doesNotContain(heroToPick);
        assertThat(newState.radiantPicks()).contains(heroToPick);
        assertThat(newState.turnIndex()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("pickHero alternates between teams")
    void pickHero_alternatesTeams() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        // First pick - Radiant
        state = engine.pickHero(state, testHeroes.get(0));
        assertThat(state.radiantPicks()).hasSize(1);
        assertThat(state.direPicks()).isEmpty();
        assertThat(state.currentTeam()).isEqualTo(Team.DIRE);
        
        // Second pick - Dire
        state = engine.pickHero(state, testHeroes.get(1));
        assertThat(state.radiantPicks()).hasSize(1);
        assertThat(state.direPicks()).hasSize(1);
        assertThat(state.currentTeam()).isEqualTo(Team.RADIANT);
    }
    
    @Test
    @DisplayName("banHero throws exception in All Pick mode")
    void banHero_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        Hero hero = testHeroes.get(0);
        
        assertThatThrownBy(() -> engine.banHero(state, hero))
            .isInstanceOf(InvalidDraftPhaseException.class)
            .hasMessageContaining("ban in All Pick");
    }
    
    @Test
    @DisplayName("picking unavailable hero throws exception")
    void pickHero_unavailableHero_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        Hero pickedHero = testHeroes.get(0);
        state = engine.pickHero(state, pickedHero);
        
        // Try to pick same hero again
        DraftState finalState = state;
        assertThatThrownBy(() -> engine.pickHero(finalState, pickedHero))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("not available");
    }
    
    @Test
    @DisplayName("picking null hero throws exception")
    void pickHero_nullHero_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThatThrownBy(() -> engine.pickHero(state, null))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("null");
    }
    
    @Test
    @DisplayName("complete draft has 5 picks per team")
    void fullDraft_hasCorrectCounts() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        // Execute 10 picks (5 per team)
        for (int i = 0; i < 10; i++) {
            Hero hero = state.availableHeroes().getFirst();
            state = engine.pickHero(state, hero);
        }
        
        assertThat(engine.isComplete(state)).isTrue();
        assertThat(state.phase()).isEqualTo(DraftPhase.COMPLETED);
        assertThat(state.radiantPicks()).hasSize(5);
        assertThat(state.direPicks()).hasSize(5);
    }
    
    @Test
    @DisplayName("picking after draft complete throws exception")
    void pickHero_afterComplete_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        // Complete the draft
        for (int i = 0; i < 10; i++) {
            state = engine.pickHero(state, state.availableHeroes().getFirst());
        }
        
        DraftState finalState = state;
        assertThatThrownBy(() -> engine.pickHero(finalState, testHeroes.get(10)))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("complete");
    }
    
    @Test
    @DisplayName("undo restores previous state")
    void undo_restoresPreviousState() {
        DraftState state = engine.initDraft(testHeroes, false);
        int originalAvailable = state.availableHeroes().size();
        Hero pickedHero = testHeroes.get(0);
        
        state = engine.pickHero(state, pickedHero);
        assertThat(state.availableHeroes()).hasSize(originalAvailable - 1);
        assertThat(state.radiantPicks()).contains(pickedHero);
        
        state = engine.undo(state);
        assertThat(state.availableHeroes()).hasSize(originalAvailable);
        assertThat(state.radiantPicks()).isEmpty();
        assertThat(state.turnIndex()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("undo multiple picks restores correct state")
    void undo_multiplePicks_restoresCorrectState() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        Hero pick1 = testHeroes.get(0);
        Hero pick2 = testHeroes.get(1);
        Hero pick3 = testHeroes.get(2);
        
        state = engine.pickHero(state, pick1);
        state = engine.pickHero(state, pick2);
        state = engine.pickHero(state, pick3);
        
        assertThat(state.radiantPicks()).containsExactly(pick1, pick3);
        assertThat(state.direPicks()).containsExactly(pick2);
        
        state = engine.undo(state);
        assertThat(state.radiantPicks()).containsExactly(pick1);
        assertThat(state.direPicks()).containsExactly(pick2);
        assertThat(state.currentTeam()).isEqualTo(Team.RADIANT);
    }
    
    @Test
    @DisplayName("undo with no history throws exception")
    void undo_noHistory_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThatThrownBy(() -> engine.undo(state))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("No actions to undo");
    }
    
    @Test
    @DisplayName("redo throws not implemented exception")
    void redo_throwsNotImplemented() {
        DraftState state = engine.initDraft(testHeroes, false);
        state = engine.pickHero(state, testHeroes.get(0));
        state = engine.undo(state);
        
        DraftState finalState = state;
        assertThatThrownBy(() -> engine.redo(finalState))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("not yet implemented");
    }
    
    @Test
    @DisplayName("getCurrentTeam returns null when complete")
    void getCurrentTeam_whenComplete_returnsNull() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        for (int i = 0; i < 10; i++) {
            state = engine.pickHero(state, state.availableHeroes().getFirst());
        }
        
        assertThat(engine.getCurrentTeam(state)).isNull();
    }
    
    @Test
    @DisplayName("getMode returns ALL_PICK")
    void getMode_returnsAllPick() {
        assertThat(engine.getMode()).isEqualTo(DraftMode.ALL_PICK);
    }
    
    @Test
    @DisplayName("getCurrentPhase always returns PICK_1 during draft")
    void getCurrentPhase_duringDraft_returnsPick1() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThat(engine.getCurrentPhase(state)).isEqualTo(DraftPhase.PICK_1);
        
        state = engine.pickHero(state, testHeroes.get(0));
        assertThat(engine.getCurrentPhase(state)).isEqualTo(DraftPhase.PICK_1);
        
        state = engine.pickHero(state, testHeroes.get(1));
        assertThat(engine.getCurrentPhase(state)).isEqualTo(DraftPhase.PICK_1);
    }
    
    private List<Hero> createTestHeroes(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(i -> new Hero(
                i, "npc_dota_hero_test" + i, "Test Hero " + i,
                Attribute.STRENGTH, AttackType.MELEE,
                List.of("Carry"), java.util.Map.of(1, 0.5),
                HeroAttributes.defaults(), "", "", List.of()
            ))
            .toList();
    }
}

