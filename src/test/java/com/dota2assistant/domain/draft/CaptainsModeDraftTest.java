package com.dota2assistant.domain.draft;

import com.dota2assistant.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Captain's Mode Draft Engine")
class CaptainsModeDraftTest {
    
    private CaptainsModeDraft engine;
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        engine = new CaptainsModeDraft();
        testHeroes = createTestHeroes(30);
    }
    
    @Test
    @DisplayName("initDraft sets correct initial state")
    void initDraft_setsCorrectInitialState() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThat(state.phase()).isEqualTo(DraftPhase.BAN_1);
        assertThat(state.currentTeam()).isEqualTo(Team.RADIANT);
        assertThat(state.turnIndex()).isEqualTo(0);
        assertThat(state.radiantPicks()).isEmpty();
        assertThat(state.direPicks()).isEmpty();
        assertThat(state.radiantBans()).isEmpty();
        assertThat(state.direBans()).isEmpty();
        assertThat(state.availableHeroes()).hasSize(30);
    }
    
    @Test
    @DisplayName("banHero removes hero from available pool")
    void banHero_removesFromAvailable() {
        DraftState state = engine.initDraft(testHeroes, false);
        Hero heroToBan = testHeroes.get(0);
        
        DraftState newState = engine.banHero(state, heroToBan);
        
        assertThat(newState.availableHeroes()).doesNotContain(heroToBan);
        assertThat(newState.radiantBans()).contains(heroToBan);
        assertThat(newState.turnIndex()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("pickHero during ban phase throws exception")
    void pickHero_duringBanPhase_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        Hero hero = testHeroes.get(0);
        
        assertThatThrownBy(() -> engine.pickHero(state, hero))
            .isInstanceOf(InvalidDraftPhaseException.class)
            .hasMessageContaining("pick");
    }
    
    @Test
    @DisplayName("banHero during pick phase throws exception")
    void banHero_duringPickPhase_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        // Complete ban phase 1 (7 bans)
        for (int i = 0; i < 7; i++) {
            state = engine.banHero(state, state.availableHeroes().getFirst());
        }
        assertThat(state.phase()).isEqualTo(DraftPhase.PICK_1);
        
        Hero hero = state.availableHeroes().getFirst();
        DraftState finalState = state;
        assertThatThrownBy(() -> engine.banHero(finalState, hero))
            .isInstanceOf(InvalidDraftPhaseException.class)
            .hasMessageContaining("ban");
    }
    
    @Test
    @DisplayName("picking unavailable hero throws exception")
    void pickHero_unavailableHero_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        // Ban a hero first
        Hero bannedHero = testHeroes.get(0);
        state = engine.banHero(state, bannedHero);
        
        // Try to ban same hero again
        DraftState finalState = state;
        assertThatThrownBy(() -> engine.banHero(finalState, bannedHero))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("not available");
    }
    
    @Test
    @DisplayName("complete draft has correct pick/ban counts")
    void fullDraft_hasCorrectCounts() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        // Execute all 24 actions
        while (!engine.isComplete(state)) {
            Hero hero = state.availableHeroes().getFirst();
            if (engine.isBanPhase(state)) {
                state = engine.banHero(state, hero);
            } else {
                state = engine.pickHero(state, hero);
            }
        }
        
        assertThat(state.phase()).isEqualTo(DraftPhase.COMPLETED);
        assertThat(state.radiantPicks()).hasSize(5);
        assertThat(state.direPicks()).hasSize(5);
        // Total bans = 7 + 3 + 4 = 14, split between teams
        int totalBans = state.radiantBans().size() + state.direBans().size();
        assertThat(totalBans).isEqualTo(14);
    }
    
    @Test
    @DisplayName("undo restores previous state")
    void undo_restoresPreviousState() {
        DraftState state = engine.initDraft(testHeroes, false);
        int originalAvailable = state.availableHeroes().size();
        Hero heroToBan = testHeroes.get(0);
        
        state = engine.banHero(state, heroToBan);
        assertThat(state.availableHeroes()).hasSize(originalAvailable - 1);
        
        state = engine.undo(state);
        assertThat(state.availableHeroes()).hasSize(originalAvailable);
        assertThat(state.availableHeroes()).contains(heroToBan);
        assertThat(state.turnIndex()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("undo with no history throws exception")
    void undo_noHistory_throwsException() {
        DraftState state = engine.initDraft(testHeroes, false);
        
        assertThatThrownBy(() -> engine.undo(state))
            .isInstanceOf(DraftValidationException.class)
            .hasMessageContaining("No actions to undo");
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

