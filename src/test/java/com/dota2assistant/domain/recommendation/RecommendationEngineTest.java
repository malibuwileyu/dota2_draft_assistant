package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.draft.*;
import com.dota2assistant.domain.model.*;
import com.dota2assistant.domain.repository.SynergyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Recommendation Engine")
class RecommendationEngineTest {
    
    private SynergyRepository synergyRepo;
    private RecommendationEngine engine;
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        synergyRepo = mock(SynergyRepository.class);
        when(synergyRepo.getSynergyScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        when(synergyRepo.getCounterScore(anyInt(), anyInt())).thenReturn(Optional.of(0.5));
        when(synergyRepo.calculateAverageSynergy(anyInt(), anyList())).thenReturn(0.5);
        when(synergyRepo.calculateAverageCounter(anyInt(), anyList())).thenReturn(0.5);
        
        SynergyScorer synergyScorer = new SynergyScorer(synergyRepo);
        CounterScorer counterScorer = new CounterScorer(synergyRepo);
        RoleScorer roleScorer = new RoleScorer();
        
        engine = new RecommendationEngine(synergyScorer, counterScorer, roleScorer);
        testHeroes = createTestHeroes(20);
    }
    
    @Test
    @DisplayName("returns requested number of recommendations")
    void getRecommendations_returnsCorrectCount() {
        DraftState state = createEmptyDraft();
        
        List<Recommendation> recs = engine.getRecommendations(state, Team.RADIANT, 5);
        
        assertThat(recs).hasSize(5);
    }
    
    @Test
    @DisplayName("recommendations are sorted by score descending")
    void getRecommendations_sortedByScore() {
        DraftState state = createEmptyDraft();
        
        List<Recommendation> recs = engine.getRecommendations(state, Team.RADIANT, 10);
        
        for (int i = 0; i < recs.size() - 1; i++) {
            assertThat(recs.get(i).score()).isGreaterThanOrEqualTo(recs.get(i + 1).score());
        }
    }
    
    @Test
    @DisplayName("recommendations include score components")
    void getRecommendations_includeReasons() {
        DraftState state = createEmptyDraft();
        
        List<Recommendation> recs = engine.getRecommendations(state, Team.RADIANT, 1);
        
        assertThat(recs.getFirst().reasons()).hasSize(4);
        assertThat(recs.getFirst().reasons().stream().map(ScoreComponent::type))
            .containsExactlyInAnyOrder("synergy", "counter", "role", "meta");
    }
    
    @Test
    @DisplayName("high synergy hero scores higher")
    void getRecommendations_highSynergyScoresHigher() {
        // Hero 1 has high synergy with ally
        when(synergyRepo.calculateAverageSynergy(eq(1), anyList())).thenReturn(0.9);
        // Hero 2 has low synergy
        when(synergyRepo.calculateAverageSynergy(eq(2), anyList())).thenReturn(0.1);
        
        Hero ally = testHeroes.get(5);
        DraftState state = createDraftWithPicks(List.of(ally), List.of());
        
        List<Recommendation> recs = engine.getRecommendations(state, Team.RADIANT, 20);
        
        Recommendation hero1Rec = recs.stream().filter(r -> r.hero().id() == 1).findFirst().orElseThrow();
        Recommendation hero2Rec = recs.stream().filter(r -> r.hero().id() == 2).findFirst().orElseThrow();
        
        assertThat(hero1Rec.score()).isGreaterThan(hero2Rec.score());
    }
    
    @Test
    @DisplayName("strong counter hero scores higher")
    void getRecommendations_strongCounterScoresHigher() {
        // Hero 1 counters enemy
        when(synergyRepo.calculateAverageCounter(eq(1), anyList())).thenReturn(0.9);
        // Hero 2 is countered
        when(synergyRepo.calculateAverageCounter(eq(2), anyList())).thenReturn(0.1);
        
        Hero enemy = testHeroes.get(10);
        DraftState state = createDraftWithPicks(List.of(), List.of(enemy));
        
        List<Recommendation> recs = engine.getRecommendations(state, Team.RADIANT, 20);
        
        Recommendation hero1Rec = recs.stream().filter(r -> r.hero().id() == 1).findFirst().orElseThrow();
        Recommendation hero2Rec = recs.stream().filter(r -> r.hero().id() == 2).findFirst().orElseThrow();
        
        assertThat(hero1Rec.score()).isGreaterThan(hero2Rec.score());
    }
    
    private DraftState createEmptyDraft() {
        return DraftState.initial(DraftMode.CAPTAINS_MODE, false, testHeroes);
    }
    
    private DraftState createDraftWithPicks(List<Hero> radiant, List<Hero> dire) {
        List<Hero> available = new ArrayList<>(testHeroes);
        available.removeAll(radiant);
        available.removeAll(dire);
        
        return new DraftState(DraftMode.CAPTAINS_MODE, DraftPhase.PICK_1, Team.RADIANT, 7,
            radiant, dire, List.of(), List.of(), available, false, 30, 130, 130, List.of());
    }
    
    private List<Hero> createTestHeroes(int count) {
        List<Hero> heroes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            heroes.add(new Hero(i, "hero_" + i, "Hero " + i, Attribute.STRENGTH, AttackType.MELEE,
                List.of("Carry", "Nuker"), Map.of(1, 0.5), HeroAttributes.defaults(), "", "", List.of()));
        }
        return heroes;
    }
}

