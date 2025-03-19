package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MlModelEvaluationTest {

    @Mock
    private HeroRepository heroRepository;
    
    @Mock
    private MatchRepository matchRepository;
    
    @Mock
    private NlpModelIntegration nlpModel;
    
    @Mock
    private MlTrainingService mlTrainingService;
    
    private MlBasedAiDecisionEngine mlDecisionEngine;
    private MlModelEvaluationService evaluationService;
    private MlModelBenchmarkService benchmarkService;
    
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        // Setup test heroes
        testHeroes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Hero hero = new Hero();
            hero.setId(i);
            hero.setLocalizedName("Test Hero " + i);
            testHeroes.add(hero);
        }
        
        // Mock repository to return test heroes
        when(heroRepository.getAllHeroes()).thenReturn(testHeroes);
        for (Hero hero : testHeroes) {
            when(heroRepository.getHeroById(hero.getId())).thenReturn(hero);
        }
        
        // Mock ML training service
        Map<String, Map<Integer, Double>> winRates = new HashMap<>();
        Map<Integer, Double> allWinRates = new HashMap<>();
        for (Hero hero : testHeroes) {
            allWinRates.put(hero.getId(), 0.5 + (hero.getId() % 10) * 0.02); // 50-68% win rates
        }
        winRates.put("all", allWinRates);
        when(mlTrainingService.getHeroWinRates()).thenReturn(winRates);
        
        // Mock synergies
        Map<String, Double> synergies = new HashMap<>();
        for (int i = 0; i < testHeroes.size(); i++) {
            for (int j = i + 1; j < testHeroes.size(); j++) {
                int hero1Id = testHeroes.get(i).getId();
                int hero2Id = testHeroes.get(j).getId();
                String key = Math.min(hero1Id, hero2Id) + "_" + Math.max(hero1Id, hero2Id);
                synergies.put(key, 0.4 + Math.random() * 0.4); // 40-80% synergy
            }
        }
        when(mlTrainingService.getHeroSynergies()).thenReturn(synergies);
        
        // Mock counters
        Map<String, Double> counters = new HashMap<>();
        for (Hero hero1 : testHeroes) {
            for (Hero hero2 : testHeroes) {
                if (hero1.getId() != hero2.getId()) {
                    String key = hero1.getId() + "_" + hero2.getId();
                    counters.put(key, 0.4 + Math.random() * 0.4); // 40-80% counter
                }
            }
        }
        when(mlTrainingService.getHeroCounters()).thenReturn(counters);
        
        // Mock NLP model
        for (Hero hero : testHeroes) {
            Map<String, Double> features = new HashMap<>();
            features.put("stun_score", Math.random() * 0.8);
            features.put("magical_damage", Math.random() * 0.8);
            features.put("physical_damage", Math.random() * 0.8);
            features.put("mobility_score", Math.random() * 0.8);
            features.put("aoe_impact", Math.random() * 0.8);
            when(nlpModel.getHeroFeatureVector(hero.getId())).thenReturn(features);
        }
        
        // Initialize services under test
        mlDecisionEngine = new MlBasedAiDecisionEngine(
            heroRepository, matchRepository, null, null, nlpModel, mlTrainingService);
        
        evaluationService = new MlModelEvaluationService(
            heroRepository, matchRepository, mlTrainingService, nlpModel, mlDecisionEngine);
            
        benchmarkService = new MlModelBenchmarkService(
            evaluationService, heroRepository, mlDecisionEngine);
    }
    
    @Test
    void testRecommendationPrecision() {
        // Run evaluation
        MlModelEvaluationService.EvaluationResult result = evaluationService.evaluateRecommendationPrecision(10);
        
        // Verify the result contains expected metrics
        assertNotNull(result);
        assertTrue(result.getMetrics().containsKey("matches_evaluated"));
        assertTrue(result.getMetrics().containsKey("total_picks"));
        assertTrue(result.getMetrics().containsKey("exact_match_precision"));
        assertTrue(result.getMetrics().containsKey("top_3_precision"));
        assertTrue(result.getMetrics().containsKey("top_5_precision"));
    }
    
    @Test
    void testSynergyDetection() {
        // Run evaluation
        MlModelEvaluationService.EvaluationResult result = evaluationService.evaluateSynergyDetection();
        
        // Verify the result contains expected metrics
        assertNotNull(result);
        assertTrue(result.getMetrics().containsKey("hero_combinations_evaluated"));
        assertTrue(result.getMetrics().containsKey("correlation_coefficient"));
        assertTrue(result.getMetrics().containsKey("mean_absolute_error"));
    }
    
    @Test
    void testBenchmarkSpeed() {
        // Run benchmark
        MlModelBenchmarkService.BenchmarkResult result = new MlModelBenchmarkService.BenchmarkResult("Test");
        
        // Test with a small but valid scenario
        List<MlModelEvaluationService.TestScenario> scenarios = createTestScenarios(2);
        
        // This is just a mock test to check the structure - in real tests, 
        // you'd verify actual performance metrics
        assertDoesNotThrow(() -> {
            for (MlModelEvaluationService.TestScenario scenario : scenarios) {
                mlDecisionEngine.suggestPicks(scenario.allyPicks, scenario.enemyPicks, scenario.bannedHeroes, 5);
            }
        });
    }
    
    @Test
    void testParameterOptimization() {
        // Create a simple test scenario
        List<MlModelEvaluationService.TestScenario> scenarios = createTestScenarios(1);
        MlModelEvaluationService.TestScenario scenario = scenarios.get(0);
        
        // Test recommendations with different parameters
        List<Hero> recs1 = mlDecisionEngine.suggestPicks(
            scenario.allyPicks, scenario.enemyPicks, scenario.bannedHeroes, 5);
            
        // Basic verification
        assertNotNull(recs1);
        assertTrue(recs1.size() > 0);
    }
    
    /**
     * Helper method to create test scenarios
     */
    private List<MlModelEvaluationService.TestScenario> createTestScenarios(int count) {
        List<MlModelEvaluationService.TestScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            MlModelEvaluationService.TestScenario scenario = new MlModelEvaluationService.TestScenario();
            
            // Assign different heroes to each role
            scenario.allyPicks = new ArrayList<>(testHeroes.subList(0, 2));
            scenario.enemyPicks = new ArrayList<>(testHeroes.subList(2, 5));
            scenario.bannedHeroes = new ArrayList<>(testHeroes.subList(5, 7));
            scenario.expectedPickId = testHeroes.get(8).getId();
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
}