package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.*;
import com.dota2assistant.domain.repository.SynergyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Synergy Scorer")
class SynergyScorerTest {
    
    @Mock
    private SynergyRepository synergyRepository;
    
    private SynergyScorer scorer;
    private Hero testHero;
    private List<Hero> allies;
    
    @BeforeEach
    void setUp() {
        scorer = new SynergyScorer(synergyRepository);
        testHero = createHero(1, "Test Hero");
        allies = List.of(
            createHero(2, "Ally 1"),
            createHero(3, "Ally 2")
        );
    }
    
    @Test
    @DisplayName("returns neutral score when no allies")
    void score_noAllies_returnsNeutral() {
        ScoreComponent result = scorer.score(testHero, List.of());
        
        assertThat(result.type()).isEqualTo(ScoreComponent.SYNERGY);
        assertThat(result.value()).isEqualTo(0.5);
        assertThat(result.description()).contains("No allies");
    }
    
    @Test
    @DisplayName("calculates high synergy correctly")
    void score_highSynergy_returnsHighScore() {
        when(synergyRepository.calculateAverageSynergy(eq(1), anyList()))
            .thenReturn(0.8);
        
        ScoreComponent result = scorer.score(testHero, allies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.SYNERGY);
        assertThat(result.value()).isEqualTo(0.8);
        assertThat(result.description()).contains("Strong synergy");
    }
    
    @Test
    @DisplayName("calculates neutral synergy correctly")
    void score_neutralSynergy_returnsNeutralScore() {
        when(synergyRepository.calculateAverageSynergy(eq(1), anyList()))
            .thenReturn(0.55);
        
        ScoreComponent result = scorer.score(testHero, allies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.SYNERGY);
        assertThat(result.value()).isEqualTo(0.55);
        assertThat(result.description()).contains("Neutral synergy");
    }
    
    @Test
    @DisplayName("calculates weak synergy correctly")
    void score_weakSynergy_returnsLowScore() {
        when(synergyRepository.calculateAverageSynergy(eq(1), anyList()))
            .thenReturn(0.35);
        
        ScoreComponent result = scorer.score(testHero, allies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.SYNERGY);
        assertThat(result.value()).isEqualTo(0.35);
        assertThat(result.description()).contains("Weak synergy");
    }
    
    @Test
    @DisplayName("passes correct ally IDs to repository")
    void score_passesCorrectAllyIds() {
        when(synergyRepository.calculateAverageSynergy(anyInt(), anyList()))
            .thenReturn(0.5);
        
        scorer.score(testHero, allies);
        
        verify(synergyRepository).calculateAverageSynergy(1, List.of(2, 3));
    }
    
    @Test
    @DisplayName("description mentions ally count")
    void score_descriptionMentionsAllyCount() {
        when(synergyRepository.calculateAverageSynergy(eq(1), anyList()))
            .thenReturn(0.75);
        
        ScoreComponent result = scorer.score(testHero, allies);
        
        assertThat(result.description()).contains("2");
    }
    
    private Hero createHero(int id, String name) {
        return new Hero(
            id, "npc_dota_hero_" + name.toLowerCase().replace(" ", "_"), name,
            Attribute.STRENGTH, AttackType.MELEE,
            List.of("Carry"), Map.of(1, 0.5),
            HeroAttributes.defaults(), "", "", List.of()
        );
    }
}

