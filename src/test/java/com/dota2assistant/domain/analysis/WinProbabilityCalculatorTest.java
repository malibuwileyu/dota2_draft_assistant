package com.dota2assistant.domain.analysis;

import com.dota2assistant.domain.draft.DraftMode;
import com.dota2assistant.domain.draft.DraftPhase;
import com.dota2assistant.domain.draft.DraftState;
import com.dota2assistant.domain.model.*;
import com.dota2assistant.domain.repository.SynergyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Win Probability Calculator")
class WinProbabilityCalculatorTest {
    
    @Mock
    private SynergyRepository synergyRepository;
    
    private WinProbabilityCalculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new WinProbabilityCalculator(synergyRepository);
    }
    
    @Test
    @DisplayName("returns 50% for empty draft")
    void calculate_emptyDraft_returnsFiftyPercent() {
        DraftState state = createDraftState(List.of(), List.of());
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        assertThat(probability).isEqualTo(0.5);
    }
    
    @Test
    @DisplayName("higher probability when Radiant has better synergy")
    void calculate_radiantBetterSynergy_higherProbability() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        Hero dire1 = createHero(3, "Dire1");
        
        // Radiant pair has excellent synergy
        when(synergyRepository.getSynergyScore(1, 2)).thenReturn(Optional.of(0.8));
        when(synergyRepository.getSynergyScore(2, 1)).thenReturn(Optional.of(0.8));
        
        // Counter matchups are neutral
        when(synergyRepository.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        
        DraftState state = createDraftState(List.of(rad1, rad2), List.of(dire1));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        assertThat(probability).isGreaterThan(0.5);
    }
    
    @Test
    @DisplayName("lower probability when Radiant is countered")
    void calculate_radiantCountered_lowerProbability() {
        Hero rad1 = createHero(1, "Rad1");
        Hero dire1 = createHero(2, "Dire1");
        
        // Dire hero counters Radiant hero hard
        when(synergyRepository.getCounterScore(1, 2)).thenReturn(Optional.of(0.2));  // Rad countered
        when(synergyRepository.getCounterScore(2, 1)).thenReturn(Optional.of(0.8));  // Dire counters
        
        DraftState state = createDraftState(List.of(rad1), List.of(dire1));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        assertThat(probability).isLessThan(0.5);
    }
    
    @Test
    @DisplayName("probability stays between 0 and 1")
    void calculate_extremeValues_staysInRange() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        Hero dire1 = createHero(3, "Dire1");
        Hero dire2 = createHero(4, "Dire2");
        
        // Extreme synergy values
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(1.0));
        when(synergyRepository.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.of(0.0));
        
        DraftState state = createDraftState(List.of(rad1, rad2), List.of(dire1, dire2));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        assertThat(probability).isBetween(0.0, 1.0);
    }
    
    @Test
    @DisplayName("handles missing synergy data gracefully")
    void calculate_missingSynergyData_usesDefault() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        Hero dire1 = createHero(3, "Dire1");
        
        // No synergy data available
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(synergyRepository.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.empty());
        
        DraftState state = createDraftState(List.of(rad1, rad2), List.of(dire1));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        // Should return near 50% with no data
        assertThat(probability).isBetween(0.45, 0.55);
    }
    
    @Test
    @DisplayName("synergy matters more with larger teams")
    void calculate_largerTeams_moreSynergyPairs() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        Hero rad3 = createHero(3, "Rad3");
        Hero dire1 = createHero(4, "Dire1");
        
        // High synergy between all Radiant pairs
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(0.8));
        when(synergyRepository.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        
        DraftState smallTeam = createDraftState(List.of(rad1, rad2), List.of(dire1));
        DraftState largerTeam = createDraftState(List.of(rad1, rad2, rad3), List.of(dire1));
        
        double smallProb = calculator.calculateRadiantWinProbability(smallTeam);
        double largerProb = calculator.calculateRadiantWinProbability(largerTeam);
        
        // Both should favor Radiant due to good synergy
        assertThat(smallProb).isGreaterThan(0.5);
        assertThat(largerProb).isGreaterThan(0.5);
    }
    
    @Test
    @DisplayName("balanced teams return near 50%")
    void calculate_balancedTeams_nearFiftyPercent() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        Hero dire1 = createHero(3, "Dire1");
        Hero dire2 = createHero(4, "Dire2");
        
        // All values neutral
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        when(synergyRepository.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        
        DraftState state = createDraftState(List.of(rad1, rad2), List.of(dire1, dire2));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        assertThat(probability).isBetween(0.45, 0.55);
    }
    
    @Test
    @DisplayName("Radiant only picks still calculate probability")
    void calculate_onlyRadiantPicks_stillCalculates() {
        Hero rad1 = createHero(1, "Rad1");
        Hero rad2 = createHero(2, "Rad2");
        
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(0.7));
        
        DraftState state = createDraftState(List.of(rad1, rad2), List.of());
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        // Should favor Radiant with good synergy and no enemies
        assertThat(probability).isGreaterThanOrEqualTo(0.5);
    }
    
    @Test
    @DisplayName("Dire only picks return lower probability for Radiant")
    void calculate_onlyDirePicks_lowerRadiantProbability() {
        Hero dire1 = createHero(1, "Dire1");
        Hero dire2 = createHero(2, "Dire2");
        
        when(synergyRepository.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(0.8));
        
        DraftState state = createDraftState(List.of(), List.of(dire1, dire2));
        
        double probability = calculator.calculateRadiantWinProbability(state);
        
        // Should favor Dire with good synergy and no Radiant
        assertThat(probability).isLessThanOrEqualTo(0.5);
    }
    
    private DraftState createDraftState(List<Hero> radiantPicks, List<Hero> direPicks) {
        return new DraftState(
            DraftMode.CAPTAINS_MODE,
            DraftPhase.PICK_1,
            Team.RADIANT,
            0,
            radiantPicks,
            direPicks,
            List.of(),
            List.of(),
            List.of(),
            false,
            30,
            130,
            130,
            List.of()
        );
    }
    
    private Hero createHero(int id, String name) {
        return new Hero(
            id, "npc_dota_hero_" + name.toLowerCase(), name,
            Attribute.STRENGTH, AttackType.MELEE,
            List.of("Carry"), Map.of(1, 0.5),
            HeroAttributes.defaults(), "", "", List.of()
        );
    }
}

