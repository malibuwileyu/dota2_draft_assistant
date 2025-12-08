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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Counter Scorer")
class CounterScorerTest {
    
    @Mock
    private SynergyRepository synergyRepository;
    
    private CounterScorer scorer;
    private Hero testHero;
    private List<Hero> enemies;
    
    @BeforeEach
    void setUp() {
        scorer = new CounterScorer(synergyRepository);
        testHero = createHero(1, "Test Hero");
        enemies = List.of(
            createHero(2, "Enemy 1"),
            createHero(3, "Enemy 2")
        );
    }
    
    @Test
    @DisplayName("returns neutral score when no enemies")
    void score_noEnemies_returnsNeutral() {
        ScoreComponent result = scorer.score(testHero, List.of());
        
        assertThat(result.type()).isEqualTo(ScoreComponent.COUNTER);
        assertThat(result.value()).isEqualTo(0.5);
        assertThat(result.description()).contains("No enemies");
    }
    
    @Test
    @DisplayName("calculates strong counter correctly")
    void score_strongCounter_returnsHighScore() {
        when(synergyRepository.calculateAverageCounter(eq(1), anyList()))
            .thenReturn(0.75);
        
        ScoreComponent result = scorer.score(testHero, enemies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.COUNTER);
        assertThat(result.value()).isEqualTo(0.75);
        assertThat(result.description()).contains("Strong counter");
    }
    
    @Test
    @DisplayName("calculates neutral matchup correctly")
    void score_neutralMatchup_returnsNeutralScore() {
        when(synergyRepository.calculateAverageCounter(eq(1), anyList()))
            .thenReturn(0.55);
        
        ScoreComponent result = scorer.score(testHero, enemies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.COUNTER);
        assertThat(result.value()).isEqualTo(0.55);
        assertThat(result.description()).contains("Neutral matchup");
    }
    
    @Test
    @DisplayName("calculates being countered correctly")
    void score_beingCountered_returnsLowScore() {
        when(synergyRepository.calculateAverageCounter(eq(1), anyList()))
            .thenReturn(0.35);
        
        ScoreComponent result = scorer.score(testHero, enemies);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.COUNTER);
        assertThat(result.value()).isEqualTo(0.35);
        assertThat(result.description()).contains("Countered by enemy");
    }
    
    @Test
    @DisplayName("passes correct enemy IDs to repository")
    void score_passesCorrectEnemyIds() {
        when(synergyRepository.calculateAverageCounter(anyInt(), anyList()))
            .thenReturn(0.5);
        
        scorer.score(testHero, enemies);
        
        verify(synergyRepository).calculateAverageCounter(1, List.of(2, 3));
    }
    
    @Test
    @DisplayName("description mentions enemy count for strong counter")
    void score_strongCounter_descriptionMentionsCount() {
        when(synergyRepository.calculateAverageCounter(eq(1), anyList()))
            .thenReturn(0.8);
        
        ScoreComponent result = scorer.score(testHero, enemies);
        
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

