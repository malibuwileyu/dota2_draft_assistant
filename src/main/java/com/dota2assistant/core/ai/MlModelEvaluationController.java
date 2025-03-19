package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for model evaluation and metrics tracking.
 * Provides API endpoints for running evaluations, benchmarks, and retrieving metrics.
 */
@RestController
@RequestMapping("/api/ml/evaluation")
public class MlModelEvaluationController {

    private static final Logger logger = LoggerFactory.getLogger(MlModelEvaluationController.class);
    
    private final MlModelEvaluationService evaluationService;
    private final MlModelBenchmarkService benchmarkService;
    private final HeroRepository heroRepository;
    
    @Autowired
    public MlModelEvaluationController(MlModelEvaluationService evaluationService,
                                      MlModelBenchmarkService benchmarkService,
                                      HeroRepository heroRepository) {
        this.evaluationService = evaluationService;
        this.benchmarkService = benchmarkService;
        this.heroRepository = heroRepository;
    }
    
    /**
     * Trigger a comprehensive evaluation of the ML model
     * 
     * @return Evaluation report with metrics
     */
    @PostMapping("/run-evaluation")
    public ResponseEntity<Map<String, Object>> runEvaluation() {
        logger.info("Received request to run comprehensive evaluation");
        
        try {
            MlModelEvaluationService.EvaluationReport report = evaluationService.generateComprehensiveReport();
            
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", report.getTimestamp());
            response.put("name", report.getName());
            
            List<Map<String, Object>> results = new ArrayList<>();
            for (MlModelEvaluationService.EvaluationResult result : report.getResults()) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("name", result.getName());
                resultMap.put("timestamp", result.getTimestamp());
                resultMap.put("metrics", result.getMetrics());
                results.add(resultMap);
            }
            
            response.put("results", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error running evaluation: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to run evaluation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Run a benchmarking suite to measure model performance
     * 
     * @return Benchmark results with performance metrics
     */
    @PostMapping("/run-benchmark")
    public ResponseEntity<Map<String, Object>> runBenchmark() {
        logger.info("Received request to run performance benchmark");
        
        try {
            MlModelBenchmarkService.BenchmarkResult result = benchmarkService.runCompleteBenchmark();
            
            Map<String, Object> response = new HashMap<>();
            response.put("name", result.getName());
            response.put("timestamp", result.getTimestamp());
            response.put("metrics", result.getMetrics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error running benchmark: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to run benchmark: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Optimize model parameters to find the best configuration
     * 
     * @return Optimization results with comparative metrics
     */
    @PostMapping("/optimize-parameters")
    public ResponseEntity<Map<String, Object>> optimizeParameters() {
        logger.info("Received request to optimize model parameters");
        
        try {
            MlModelBenchmarkService.BenchmarkResult result = benchmarkService.optimizeParameters();
            
            Map<String, Object> response = new HashMap<>();
            response.put("name", result.getName());
            response.put("timestamp", result.getTimestamp());
            response.put("metrics", result.getMetrics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error optimizing parameters: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to optimize parameters: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Evaluate a specific test scenario to measure recommendation quality
     * 
     * @param request Test scenario request with team compositions
     * @return Evaluation results for the scenario
     */
    @PostMapping("/evaluate-scenario")
    public ResponseEntity<Map<String, Object>> evaluateScenario(@RequestBody TestScenarioRequest request) {
        logger.info("Received request to evaluate specific scenario");
        
        try {
            // Convert hero IDs to Hero objects
            List<Hero> allyPicks = request.getAllyHeroIds().stream()
                .map(heroRepository::getHeroById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            List<Hero> enemyPicks = request.getEnemyHeroIds().stream()
                .map(heroRepository::getHeroById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            List<Hero> bannedHeroes = request.getBannedHeroIds().stream()
                .map(heroRepository::getHeroById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            Hero expectedPick = heroRepository.getHeroById(request.getExpectedPickId());
            if (expectedPick == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid expected hero ID");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Create a test scenario
            MlModelEvaluationService.TestScenario scenario = new MlModelEvaluationService.TestScenario();
            scenario.allyPicks = allyPicks;
            scenario.enemyPicks = enemyPicks;
            scenario.bannedHeroes = bannedHeroes;
            scenario.expectedPickId = expectedPick.getId();
            
            // Run A/B testing with this scenario
            MlModelEvaluationService.EvaluationResult result = 
                evaluationService.performABTesting(Collections.singletonList(scenario));
            
            Map<String, Object> response = new HashMap<>();
            response.put("name", result.getName());
            response.put("timestamp", result.getTimestamp());
            response.put("metrics", result.getMetrics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error evaluating scenario: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to evaluate scenario: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Test a specific hero synergy to evaluate prediction accuracy
     * 
     * @param request Hero synergy test request with hero IDs
     * @return Evaluation results for the synergy
     */
    @PostMapping("/test-synergy")
    public ResponseEntity<Map<String, Object>> testSynergy(@RequestBody HeroSynergyRequest request) {
        logger.info("Received request to test hero synergy");
        
        try {
            Hero hero1 = heroRepository.getHeroById(request.getHero1Id());
            Hero hero2 = heroRepository.getHeroById(request.getHero2Id());
            
            if (hero1 == null || hero2 == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid hero IDs");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Run specific synergy evaluation
            MlModelEvaluationService.EvaluationResult result = evaluationService.evaluateSynergyDetection();
            
            // Extract relevant metrics for this hero pair
            String synergyKey = Math.min(hero1.getId(), hero2.getId()) + "_" + Math.max(hero1.getId(), hero2.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("hero1", hero1.getLocalizedName());
            response.put("hero2", hero2.getLocalizedName());
            response.put("metrics", result.getMetrics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error testing synergy: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to test synergy: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Request object for test scenario evaluation
     */
    public static class TestScenarioRequest {
        private List<Integer> allyHeroIds;
        private List<Integer> enemyHeroIds;
        private List<Integer> bannedHeroIds;
        private int expectedPickId;
        
        public List<Integer> getAllyHeroIds() {
            return allyHeroIds;
        }
        
        public void setAllyHeroIds(List<Integer> allyHeroIds) {
            this.allyHeroIds = allyHeroIds;
        }
        
        public List<Integer> getEnemyHeroIds() {
            return enemyHeroIds;
        }
        
        public void setEnemyHeroIds(List<Integer> enemyHeroIds) {
            this.enemyHeroIds = enemyHeroIds;
        }
        
        public List<Integer> getBannedHeroIds() {
            return bannedHeroIds;
        }
        
        public void setBannedHeroIds(List<Integer> bannedHeroIds) {
            this.bannedHeroIds = bannedHeroIds;
        }
        
        public int getExpectedPickId() {
            return expectedPickId;
        }
        
        public void setExpectedPickId(int expectedPickId) {
            this.expectedPickId = expectedPickId;
        }
    }
    
    /**
     * Request object for hero synergy testing
     */
    public static class HeroSynergyRequest {
        private int hero1Id;
        private int hero2Id;
        
        public int getHero1Id() {
            return hero1Id;
        }
        
        public void setHero1Id(int hero1Id) {
            this.hero1Id = hero1Id;
        }
        
        public int getHero2Id() {
            return hero2Id;
        }
        
        public void setHero2Id(int hero2Id) {
            this.hero2Id = hero2Id;
        }
    }
}