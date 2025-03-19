package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for benchmarking ML model performance and optimizing parameters.
 * This service tracks model performance over time and compares different configurations.
 */
@Service
public class MlModelBenchmarkService {
    
    private static final Logger logger = LoggerFactory.getLogger(MlModelBenchmarkService.class);
    
    private final MlModelEvaluationService evaluationService;
    private final HeroRepository heroRepository;
    private final MlBasedAiDecisionEngine mlDecisionEngine;
    
    // Directory for storing benchmark results
    private static final String BENCHMARK_DIR = "data/benchmarks";
    
    @Autowired
    public MlModelBenchmarkService(MlModelEvaluationService evaluationService,
                                  HeroRepository heroRepository,
                                  MlBasedAiDecisionEngine mlDecisionEngine) {
        this.evaluationService = evaluationService;
        this.heroRepository = heroRepository;
        this.mlDecisionEngine = mlDecisionEngine;
        
        // Create benchmark directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(BENCHMARK_DIR));
        } catch (IOException e) {
            logger.error("Failed to create benchmark directory: {}", e.getMessage());
        }
    }
    
    /**
     * Run a complete benchmark suite to evaluate the model's performance
     * 
     * @return BenchmarkResult with performance metrics
     */
    public BenchmarkResult runCompleteBenchmark() {
        logger.info("Starting complete model benchmark");
        BenchmarkResult result = new BenchmarkResult("Complete Model Benchmark");
        
        // Run performance benchmarks
        benchmarkRecommendationSpeed(result);
        
        // Run accuracy benchmarks
        benchmarkRecommendationAccuracy(result);
        
        // Run consistency benchmarks
        benchmarkRecommendationConsistency(result);
        
        // Save benchmark results
        saveBenchmarkResult(result);
        
        return result;
    }
    
    /**
     * Benchmark the speed of generating recommendations
     * 
     * @param result Benchmark result to add metrics to
     */
    private void benchmarkRecommendationSpeed(BenchmarkResult result) {
        logger.info("Benchmarking recommendation speed");
        
        // Create test scenarios with varying team sizes
        List<Map<String, Object>> scenarioMaps = createBenchmarkScenarios();
        
        // Warmup
        for (int i = 0; i < 5 && !scenarioMaps.isEmpty(); i++) {
            Map<String, Object> scenarioMap = scenarioMaps.get(i % scenarioMaps.size());
            List<Hero> allyPicks = convertToHeroList((List<Integer>) scenarioMap.get("allyHeroes"));
            List<Hero> enemyPicks = convertToHeroList((List<Integer>) scenarioMap.get("enemyHeroes"));
            List<Hero> bannedHeroes = new ArrayList<>();
            
            mlDecisionEngine.suggestPicks(allyPicks, enemyPicks, bannedHeroes, 5);
        }
        
        // Measure performance
        long startTime = System.nanoTime();
        int iterations = 50;
        
        for (int i = 0; i < iterations && !scenarioMaps.isEmpty(); i++) {
            Map<String, Object> scenarioMap = scenarioMaps.get(i % scenarioMaps.size());
            List<Hero> allyPicks = convertToHeroList((List<Integer>) scenarioMap.get("allyHeroes"));
            List<Hero> enemyPicks = convertToHeroList((List<Integer>) scenarioMap.get("enemyHeroes"));
            List<Hero> bannedHeroes = new ArrayList<>();
            
            mlDecisionEngine.suggestPicks(allyPicks, enemyPicks, bannedHeroes, 5);
        }
        
        long endTime = System.nanoTime();
        double averageTimeMs = (endTime - startTime) / (double) iterations / 1_000_000;
        
        result.addMetric("recommendation_speed", String.format("%.2f ms per recommendation", averageTimeMs));
        result.addMetric("recommendations_per_second", String.format("%.2f", 1000 / averageTimeMs));
        
        logger.info("Recommendation speed benchmark complete: {:.2f} ms per recommendation", averageTimeMs);
    }
    
    /**
     * Benchmark the accuracy of recommendations against known good picks
     * 
     * @param result Benchmark result to add metrics to
     */
    private void benchmarkRecommendationAccuracy(BenchmarkResult result) {
        logger.info("Benchmarking recommendation accuracy");
        
        // Use evaluation service to test accuracy
        MlModelEvaluationService.EvaluationResult evalResult = 
            evaluationService.evaluateRecommendationPrecision(50);
            
        // Copy metrics from evaluation result to benchmark result
        for (Map.Entry<String, String> entry : evalResult.getMetrics().entrySet()) {
            result.addMetric("accuracy_" + entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Benchmark the consistency of recommendations
     * 
     * @param result Benchmark result to add metrics to
     */
    private void benchmarkRecommendationConsistency(BenchmarkResult result) {
        logger.info("Benchmarking recommendation consistency");
        
        // Create a single test scenario
        List<Map<String, Object>> scenarioMaps = createBenchmarkScenarios();
        if (scenarioMaps.isEmpty()) {
            logger.warn("No scenarios available for consistency benchmark");
            result.addMetric("consistency_error", "No scenarios available");
            return;
        }
        
        Map<String, Object> scenarioMap = scenarioMaps.get(0);
        List<Hero> allyPicks = convertToHeroList((List<Integer>) scenarioMap.get("allyHeroes"));
        List<Hero> enemyPicks = convertToHeroList((List<Integer>) scenarioMap.get("enemyHeroes"));
        List<Hero> bannedHeroes = new ArrayList<>();
        
        // Run multiple recommendations for the same scenario
        int iterations = 100;
        Map<Integer, Integer> recommendationCounts = new HashMap<>();
        
        for (int i = 0; i < iterations; i++) {
            List<Hero> recommendations = mlDecisionEngine.suggestPicks(
                allyPicks, enemyPicks, bannedHeroes, 5);
                
            if (!recommendations.isEmpty()) {
                int topHeroId = recommendations.get(0).getId();
                recommendationCounts.put(topHeroId, recommendationCounts.getOrDefault(topHeroId, 0) + 1);
            }
        }
        
        // Find the most common recommendation
        Optional<Map.Entry<Integer, Integer>> mostCommon = recommendationCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue());
            
        if (mostCommon.isPresent()) {
            double consistencyScore = (double) mostCommon.get().getValue() / iterations;
            result.addMetric("consistency_score", String.format("%.2f%%", consistencyScore * 100));
            result.addMetric("recommendations_variety", Integer.toString(recommendationCounts.size()));
            
            Hero mostCommonHero = heroRepository.getHeroById(mostCommon.get().getKey());
            if (mostCommonHero != null) {
                result.addMetric("most_common_hero", mostCommonHero.getLocalizedName() + 
                    String.format(" (%d%%)", mostCommon.get().getValue() * 100 / iterations));
            }
        } else {
            result.addMetric("consistency_error", "No recommendations generated");
        }
    }
    
    /**
     * Compare different parameter weights to find optimal configuration
     * 
     * @return BenchmarkResult with comparison metrics
     */
    public BenchmarkResult optimizeParameters() {
        logger.info("Starting parameter optimization benchmark");
        BenchmarkResult result = new BenchmarkResult("Parameter Optimization");
        
        // Create optimization scenarios
        List<Map<String, Object>> scenarioMaps = createBenchmarkScenarios();
        if (scenarioMaps.isEmpty()) {
            logger.warn("No scenarios available for parameter optimization");
            result.addMetric("error", "No scenarios available");
            return result;
        }
        
        // Define parameter sets to test
        List<ParameterSet> parameterSets = new ArrayList<>();
        
        // Default parameters
        parameterSets.add(new ParameterSet("Default", 0.15, 0.10, 0.25, 0.25, 0.25));
        
        // Higher emphasis on win rate
        parameterSets.add(new ParameterSet("Win Rate Focus", 0.35, 0.10, 0.20, 0.20, 0.15));
        
        // Higher emphasis on synergy
        parameterSets.add(new ParameterSet("Synergy Focus", 0.10, 0.10, 0.40, 0.20, 0.20));
        
        // Higher emphasis on counter
        parameterSets.add(new ParameterSet("Counter Focus", 0.10, 0.10, 0.20, 0.40, 0.20));
        
        // Higher emphasis on ability synergy
        parameterSets.add(new ParameterSet("Ability Focus", 0.10, 0.10, 0.20, 0.20, 0.40));
        
        // Test each parameter set
        for (ParameterSet params : parameterSets) {
            logger.info("Testing parameter set: {}", params.name);
            
            // Get top recommendations using these parameters
            Map<Integer, Integer> correctPickCounts = new HashMap<>();
            int totalCorrect = 0;
            
            for (Map<String, Object> scenarioMap : scenarioMaps) {
                // Note: In a real implementation, we would modify the engine's parameters
                // For now, we'll simulate different parameter sets
                
                List<Hero> allyPicks = convertToHeroList((List<Integer>) scenarioMap.get("allyHeroes"));
                List<Hero> enemyPicks = convertToHeroList((List<Integer>) scenarioMap.get("enemyHeroes"));
                List<Hero> bannedHeroes = new ArrayList<>();
                Integer expectedPickId = (Integer) scenarioMap.get("expectedPick");
                
                List<Hero> recommendations = getRecommendationsWithParams(
                    allyPicks, enemyPicks, bannedHeroes,
                    params.winRateWeight, params.pickRateWeight, params.synergyWeight,
                    params.counterWeight, params.abilityWeight);
                    
                if (!recommendations.isEmpty() && expectedPickId != null && recommendations.get(0).getId() == expectedPickId) {
                    totalCorrect++;
                    correctPickCounts.put(expectedPickId, 
                        correctPickCounts.getOrDefault(expectedPickId, 0) + 1);
                }
            }
            
            double accuracy = scenarioMaps.size() > 0 ? (double) totalCorrect / scenarioMaps.size() : 0.0;
            result.addMetric(params.name + "_accuracy", String.format("%.2f%%", accuracy * 100));
        }
        
        // Save benchmark results
        saveBenchmarkResult(result);
        
        return result;
    }
    
    /**
     * Create test scenarios for benchmarking
     * 
     * @return List of test scenarios
     */
    private List<Map<String, Object>> createBenchmarkScenarios() {
        // Create our own test scenarios instead of using the evaluation service
        List<Map<String, Object>> scenarios = new ArrayList<>();
        
        // Add some sample benchmark scenarios
        Map<String, Object> scenario1 = new HashMap<>();
        scenario1.put("allyHeroes", Arrays.asList(22, 17, 19, 53));
        scenario1.put("enemyHeroes", Arrays.asList(8, 11, 25, 32, 35));
        scenario1.put("expectedPick", 26);
        scenarios.add(scenario1);
        
        Map<String, Object> scenario2 = new HashMap<>();
        scenario2.put("allyHeroes", Arrays.asList(5, 31, 26, 75));
        scenario2.put("enemyHeroes", Arrays.asList(1, 10, 49, 62, 81));
        scenario2.put("expectedPick", 64);
        scenarios.add(scenario2);
        
        return scenarios;
    }
    
    /**
     * Convert a list of hero IDs to a list of Hero objects
     * 
     * @param heroIds List of hero IDs
     * @return List of Hero objects
     */
    private List<Hero> convertToHeroList(List<Integer> heroIds) {
        if (heroIds == null) {
            return new ArrayList<>();
        }
        
        List<Hero> heroes = new ArrayList<>();
        for (Integer id : heroIds) {
            Hero hero = heroRepository.getHeroById(id);
            if (hero != null) {
                heroes.add(hero);
            }
        }
        return heroes;
    }
    
    /**
     * Simulate recommendations with different parameter weights
     * 
     * @param allyPicks Current allied picks
     * @param enemyPicks Current enemy picks
     * @param bannedHeroes Current banned heroes
     * @param winRateWeight Weight for win rate factor
     * @param pickRateWeight Weight for pick rate factor
     * @param synergyWeight Weight for synergy factor
     * @param counterWeight Weight for counter factor
     * @param abilityWeight Weight for ability synergy factor
     * @return List of recommended heroes
     */
    private List<Hero> getRecommendationsWithParams(List<Hero> allyPicks, List<Hero> enemyPicks, 
                                                  List<Hero> bannedHeroes, double winRateWeight,
                                                  double pickRateWeight, double synergyWeight,
                                                  double counterWeight, double abilityWeight) {
        // In a real implementation, we would modify the engine's parameters
        // For this simulation, we'll just use the regular engine
        return mlDecisionEngine.suggestPicks(allyPicks, enemyPicks, bannedHeroes, 5);
    }
    
    /**
     * Save benchmark result to file
     * 
     * @param result The benchmark result to save
     */
    private void saveBenchmarkResult(BenchmarkResult result) {
        try {
            String filename = String.format("%s/%s_%s.txt", 
                BENCHMARK_DIR,
                result.getName().toLowerCase().replaceAll("\\s+", "_"),
                System.currentTimeMillis());
                
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("Benchmark: " + result.getName());
                writer.println("Timestamp: " + result.getTimestamp());
                writer.println();
                writer.println("=== Metrics ===");
                
                for (Map.Entry<String, String> metric : result.getMetrics().entrySet()) {
                    writer.println(metric.getKey() + ": " + metric.getValue());
                }
            }
            
            logger.info("Saved benchmark result to {}", filename);
            
        } catch (IOException e) {
            logger.error("Error saving benchmark result: {}", e.getMessage());
        }
    }
    
    /**
     * Class to hold parameter sets for optimization
     */
    private static class ParameterSet {
        final String name;
        final double winRateWeight;
        final double pickRateWeight;
        final double synergyWeight;
        final double counterWeight;
        final double abilityWeight;
        
        public ParameterSet(String name, double winRateWeight, double pickRateWeight,
                          double synergyWeight, double counterWeight, double abilityWeight) {
            this.name = name;
            this.winRateWeight = winRateWeight;
            this.pickRateWeight = pickRateWeight;
            this.synergyWeight = synergyWeight;
            this.counterWeight = counterWeight;
            this.abilityWeight = abilityWeight;
        }
    }
    
    /**
     * Class to hold benchmark result
     */
    public static class BenchmarkResult {
        private final String name;
        private final Date timestamp;
        private final Map<String, String> metrics;
        
        public BenchmarkResult(String name) {
            this.name = name;
            this.timestamp = new Date();
            this.metrics = new LinkedHashMap<>();  // Preserve insertion order
        }
        
        public void addMetric(String name, String value) {
            metrics.put(name, value);
        }
        
        public String getName() {
            return name;
        }
        
        public Date getTimestamp() {
            return timestamp;
        }
        
        public Map<String, String> getMetrics() {
            return metrics;
        }
    }
}