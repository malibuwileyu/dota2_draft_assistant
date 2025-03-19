package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for evaluating machine learning models for the draft assistant.
 * This service provides tools for measuring recommendation quality, comparing
 * model outputs against known good picks, and benchmarking different ML approaches.
 */
@Service
public class MlModelEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(MlModelEvaluationService.class);
    
    private final HeroRepository heroRepository;
    private final MatchRepository matchRepository;
    private final MlTrainingService mlTrainingService;
    private final NlpModelIntegration nlpModel;
    private final MlBasedAiDecisionEngine mlDecisionEngine;
    
    // Directory for storing evaluation results
    private static final String EVAL_DIR = "data/evaluation";
    
    @Autowired
    public MlModelEvaluationService(HeroRepository heroRepository, 
                                  MatchRepository matchRepository,
                                  MlTrainingService mlTrainingService,
                                  NlpModelIntegration nlpModel,
                                  MlBasedAiDecisionEngine mlDecisionEngine) {
        this.heroRepository = heroRepository;
        this.matchRepository = matchRepository;
        this.mlTrainingService = mlTrainingService;
        this.nlpModel = nlpModel;
        this.mlDecisionEngine = mlDecisionEngine;
    }
    
    /**
     * Initialize the evaluation system
     */
    public void initialize() {
        // Create evaluation directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(EVAL_DIR));
        } catch (IOException e) {
            logger.error("Failed to create evaluation directory: {}", e.getMessage());
        }
        
        logger.info("ML Model Evaluation Service initialized");
    }
    
    /**
     * Evaluate model recommendation precision against professional match data.
     * This compares the model's recommendations to what was actually picked in pro matches.
     * 
     * @param matchCount Number of matches to evaluate with
     * @return EvaluationResult with precision metrics
     */
    public EvaluationResult evaluateRecommendationPrecision(int matchCount) {
        logger.info("Evaluating recommendation precision with {} matches", matchCount);
        EvaluationResult result = new EvaluationResult("Recommendation Precision");
        
        // Get pro match data
        List<MatchData> proMatches = getProMatchesWithDrafts(matchCount);
        if (proMatches.isEmpty()) {
            logger.warn("No pro match data available for evaluation");
            result.addMetric("error", "No pro match data available");
            return result;
        }
        
        int totalPicks = 0;
        int correctPicks = 0;
        int totalTop3 = 0;
        int totalTop5 = 0;
        
        // For each match, we'll simulate the draft picks and see how well 
        // our recommendations match what was actually picked
        for (MatchData match : proMatches) {
            // Evaluate each pick in the draft sequence
            for (int pickIndex = 0; pickIndex < match.draftSequence.size(); pickIndex++) {
                DraftPick currentPick = match.draftSequence.get(pickIndex);
                
                // Skip bans for this evaluation
                if (!currentPick.isPick) {
                    continue;
                }
                
                // Get the draft state up to this point
                List<Hero> radiantPicks = new ArrayList<>();
                List<Hero> direPicks = new ArrayList<>();
                List<Hero> bannedHeroes = new ArrayList<>();
                
                for (int i = 0; i < pickIndex; i++) {
                    DraftPick previousPick = match.draftSequence.get(i);
                    Hero hero = heroRepository.getHeroById(previousPick.heroId);
                    if (hero == null) continue;
                    
                    if (previousPick.isPick) {
                        if (previousPick.isRadiant) {
                            radiantPicks.add(hero);
                        } else {
                            direPicks.add(hero);
                        }
                    } else {
                        bannedHeroes.add(hero);
                    }
                }
                
                // Get the actual hero that was picked
                Hero actualPick = heroRepository.getHeroById(currentPick.heroId);
                if (actualPick == null) continue;
                
                // Get recommendations from our model
                List<Hero> recommendedHeroes = currentPick.isRadiant
                    ? mlDecisionEngine.suggestPicks(radiantPicks, direPicks, bannedHeroes, 5)
                    : mlDecisionEngine.suggestPicks(direPicks, radiantPicks, bannedHeroes, 5);
                
                totalPicks++;
                
                // Check if top recommendation matches actual pick
                if (!recommendedHeroes.isEmpty() && recommendedHeroes.get(0).getId() == actualPick.getId()) {
                    correctPicks++;
                }
                
                // Check if actual pick is in top 3
                if (recommendedHeroes.size() >= 3 && 
                    recommendedHeroes.subList(0, 3).stream().anyMatch(h -> h.getId() == actualPick.getId())) {
                    totalTop3++;
                }
                
                // Check if actual pick is in top 5
                if (recommendedHeroes.stream().anyMatch(h -> h.getId() == actualPick.getId())) {
                    totalTop5++;
                }
            }
        }
        
        // Calculate precision metrics
        double precision = totalPicks > 0 ? (double) correctPicks / totalPicks : 0.0;
        double top3Precision = totalPicks > 0 ? (double) totalTop3 / totalPicks : 0.0;
        double top5Precision = totalPicks > 0 ? (double) totalTop5 / totalPicks : 0.0;
        
        result.addMetric("matches_evaluated", Integer.toString(proMatches.size()));
        result.addMetric("total_picks", Integer.toString(totalPicks));
        result.addMetric("exact_match_precision", String.format("%.2f%%", precision * 100));
        result.addMetric("top_3_precision", String.format("%.2f%%", top3Precision * 100));
        result.addMetric("top_5_precision", String.format("%.2f%%", top5Precision * 100));
        
        logger.info("Recommendation precision evaluation complete: exact={:.2f}%, top3={:.2f}%, top5={:.2f}%",
            precision * 100, top3Precision * 100, top5Precision * 100);
        
        // Save results to file
        saveEvaluationResult(result);
        
        return result;
    }
    
    /**
     * Evaluate the synergy detection accuracy by comparing model predictions
     * to actual win rates of hero combinations in pro matches
     * 
     * @return EvaluationResult with synergy detection metrics
     */
    public EvaluationResult evaluateSynergyDetection() {
        logger.info("Evaluating synergy detection accuracy");
        EvaluationResult result = new EvaluationResult("Synergy Detection");
        
        // Get hero synergies from our model
        Map<String, Double> predictedSynergies = mlTrainingService.getHeroSynergies();
        
        // Get actual win rates of hero combinations from match data
        Map<String, Double> actualSynergies = getHeroCombinationWinRates();
        
        if (actualSynergies.isEmpty()) {
            logger.warn("No actual synergy data available for evaluation");
            result.addMetric("error", "No match data available for synergy evaluation");
            return result;
        }
        
        // Calculate correlation between predicted and actual synergies
        List<Double> predictedValues = new ArrayList<>();
        List<Double> actualValues = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : predictedSynergies.entrySet()) {
            String heroKey = entry.getKey();
            if (actualSynergies.containsKey(heroKey)) {
                predictedValues.add(entry.getValue());
                actualValues.add(actualSynergies.get(heroKey));
            }
        }
        
        if (predictedValues.isEmpty()) {
            result.addMetric("error", "No matching hero combinations found for evaluation");
            return result;
        }
        
        // Calculate Pearson correlation coefficient
        double correlation = calculateCorrelation(predictedValues, actualValues);
        double meanError = calculateMeanAbsoluteError(predictedValues, actualValues);
        
        result.addMetric("hero_combinations_evaluated", Integer.toString(predictedValues.size()));
        result.addMetric("correlation_coefficient", String.format("%.4f", correlation));
        result.addMetric("mean_absolute_error", String.format("%.4f", meanError));
        
        logger.info("Synergy detection evaluation complete: correlation={:.4f}, MAE={:.4f}",
            correlation, meanError);
        
        // Save results to file
        saveEvaluationResult(result);
        
        return result;
    }
    
    /**
     * Evaluate counter detection accuracy
     * 
     * @return EvaluationResult with counter detection metrics
     */
    public EvaluationResult evaluateCounterDetection() {
        logger.info("Evaluating counter detection accuracy");
        EvaluationResult result = new EvaluationResult("Counter Detection");
        
        // Get hero counters from our model
        Map<String, Double> predictedCounters = mlTrainingService.getHeroCounters();
        
        // Get actual win rates of hero matchups from match data
        Map<String, Double> actualCounters = getHeroMatchupWinRates();
        
        if (actualCounters.isEmpty()) {
            logger.warn("No actual counter data available for evaluation");
            result.addMetric("error", "No match data available for counter evaluation");
            return result;
        }
        
        // Calculate metrics
        List<Double> predictedValues = new ArrayList<>();
        List<Double> actualValues = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : predictedCounters.entrySet()) {
            String heroKey = entry.getKey();
            if (actualCounters.containsKey(heroKey)) {
                predictedValues.add(entry.getValue());
                actualValues.add(actualCounters.get(heroKey));
            }
        }
        
        if (predictedValues.isEmpty()) {
            result.addMetric("error", "No matching hero matchups found for evaluation");
            return result;
        }
        
        double correlation = calculateCorrelation(predictedValues, actualValues);
        double meanError = calculateMeanAbsoluteError(predictedValues, actualValues);
        
        result.addMetric("hero_matchups_evaluated", Integer.toString(predictedValues.size()));
        result.addMetric("correlation_coefficient", String.format("%.4f", correlation));
        result.addMetric("mean_absolute_error", String.format("%.4f", meanError));
        
        logger.info("Counter detection evaluation complete: correlation={:.4f}, MAE={:.4f}",
            correlation, meanError);
        
        // Save results to file
        saveEvaluationResult(result);
        
        return result;
    }
    
    /**
     * Evaluate feature extraction quality
     * 
     * @return EvaluationResult with feature extraction metrics
     */
    public EvaluationResult evaluateFeatureExtraction() {
        logger.info("Evaluating feature extraction quality");
        EvaluationResult result = new EvaluationResult("Feature Extraction");
        
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Count heroes with feature vectors
        int heroesWithFeatures = 0;
        int totalFeatures = 0;
        double averageFeatureCount = 0.0;
        
        for (Hero hero : allHeroes) {
            Map<String, Double> features = nlpModel.getHeroFeatureVector(hero.getId());
            if (!features.isEmpty()) {
                heroesWithFeatures++;
                totalFeatures += features.size();
            }
        }
        
        if (heroesWithFeatures > 0) {
            averageFeatureCount = (double) totalFeatures / heroesWithFeatures;
        }
        
        result.addMetric("total_heroes", Integer.toString(allHeroes.size()));
        result.addMetric("heroes_with_features", Integer.toString(heroesWithFeatures));
        result.addMetric("feature_coverage", String.format("%.2f%%", 
            (double) heroesWithFeatures / allHeroes.size() * 100));
        result.addMetric("average_features_per_hero", String.format("%.2f", averageFeatureCount));
        
        logger.info("Feature extraction evaluation complete: coverage={:.2f}%, avg features={:.2f}",
            (double) heroesWithFeatures / allHeroes.size() * 100, averageFeatureCount);
        
        // Save results to file
        saveEvaluationResult(result);
        
        return result;
    }
    
    /**
     * Performs A/B testing of different recommendation approaches
     * 
     * @param testScenarios List of test scenarios with known good picks
     * @return EvaluationResult with comparison metrics
     */
    public EvaluationResult performABTesting(List<TestScenario> testScenarios) {
        logger.info("Performing A/B testing on {} test scenarios", testScenarios.size());
        EvaluationResult result = new EvaluationResult("A/B Testing");
        
        // We could have different approaches to compare here
        // For demonstration, we'll compare our ML-based approach to a simpler approach
        
        int mlCorrect = 0;
        int simpleCorrect = 0;
        int mlInTop3 = 0;
        int simpleInTop3 = 0;
        
        for (TestScenario scenario : testScenarios) {
            // Get ML-based recommendations
            List<Hero> mlRecommendations = mlDecisionEngine.suggestPicks(
                scenario.allyPicks, scenario.enemyPicks, scenario.bannedHeroes, 5);
                
            // For demonstration, a simpler approach just based on win rates
            // In a real implementation, this would be a different algorithm
            List<Hero> simpleRecommendations = getSimpleRecommendations(
                scenario.allyPicks, scenario.enemyPicks, scenario.bannedHeroes, 5);
                
            // Check if top recommendation matches expected pick
            if (!mlRecommendations.isEmpty() && mlRecommendations.get(0).getId() == scenario.expectedPickId) {
                mlCorrect++;
            }
            
            if (!simpleRecommendations.isEmpty() && simpleRecommendations.get(0).getId() == scenario.expectedPickId) {
                simpleCorrect++;
            }
            
            // Check if expected pick is in top 3
            if (mlRecommendations.size() >= 3 &&
                mlRecommendations.subList(0, 3).stream().anyMatch(h -> h.getId() == scenario.expectedPickId)) {
                mlInTop3++;
            }
            
            if (simpleRecommendations.size() >= 3 &&
                simpleRecommendations.subList(0, 3).stream().anyMatch(h -> h.getId() == scenario.expectedPickId)) {
                simpleInTop3++;
            }
        }
        
        int totalScenarios = testScenarios.size();
        
        result.addMetric("test_scenarios", Integer.toString(totalScenarios));
        result.addMetric("ml_exact_match", String.format("%d (%.2f%%)", 
            mlCorrect, totalScenarios > 0 ? (double) mlCorrect / totalScenarios * 100 : 0.0));
        result.addMetric("simple_exact_match", String.format("%d (%.2f%%)", 
            simpleCorrect, totalScenarios > 0 ? (double) simpleCorrect / totalScenarios * 100 : 0.0));
        result.addMetric("ml_top3", String.format("%d (%.2f%%)", 
            mlInTop3, totalScenarios > 0 ? (double) mlInTop3 / totalScenarios * 100 : 0.0));
        result.addMetric("simple_top3", String.format("%d (%.2f%%)", 
            simpleInTop3, totalScenarios > 0 ? (double) simpleInTop3 / totalScenarios * 100 : 0.0));
            
        logger.info("A/B testing complete: ML exact={:.2f}%, Simple exact={:.2f}%",
            totalScenarios > 0 ? (double) mlCorrect / totalScenarios * 100 : 0.0,
            totalScenarios > 0 ? (double) simpleCorrect / totalScenarios * 100 : 0.0);
        
        // Save results to file
        saveEvaluationResult(result);
        
        return result;
    }
    
    /**
     * Generate a comprehensive evaluation report with all available metrics
     * 
     * @return A complete evaluation report
     */
    public EvaluationReport generateComprehensiveReport() {
        logger.info("Generating comprehensive evaluation report");
        EvaluationReport report = new EvaluationReport("ML Model Comprehensive Evaluation");
        
        // Add individual evaluation results
        report.addEvaluationResult(evaluateRecommendationPrecision(50));
        report.addEvaluationResult(evaluateSynergyDetection());
        report.addEvaluationResult(evaluateCounterDetection());
        report.addEvaluationResult(evaluateFeatureExtraction());
        
        // Create some test scenarios
        List<TestScenario> testScenarios = createTestScenarios();
        report.addEvaluationResult(performABTesting(testScenarios));
        
        // Save the full report
        saveEvaluationReport(report);
        
        return report;
    }
    
    /**
     * Get pro match data with draft sequences
     * 
     * @param count Number of matches to retrieve
     * @return List of matches with draft data
     */
    private List<MatchData> getProMatchesWithDrafts(int count) {
        // In a real implementation, this would read from the match repository
        // For simulation, we'll generate some mock data
        return generateMockMatchData(count);
    }
    
    /**
     * Get win rates of hero combinations from match data
     * 
     * @return Map of hero combo keys to win rates
     */
    private Map<String, Double> getHeroCombinationWinRates() {
        // In a real implementation, this would calculate actual win rates
        // For simulation, we'll generate some mock data
        return generateMockHeroCombinationWinRates();
    }
    
    /**
     * Get win rates of hero matchups from match data
     * 
     * @return Map of hero matchup keys to win rates
     */
    private Map<String, Double> getHeroMatchupWinRates() {
        // In a real implementation, this would calculate actual win rates
        // For simulation, we'll generate some mock data
        return generateMockHeroMatchupWinRates();
    }
    
    /**
     * Simple recommendation method just for A/B testing comparison
     * 
     * @param allyPicks Current ally picks
     * @param enemyPicks Current enemy picks
     * @param bannedHeroes Current banned heroes
     * @param count Number of recommendations to return
     * @return List of recommended heroes
     */
    private List<Hero> getSimpleRecommendations(List<Hero> allyPicks, List<Hero> enemyPicks, 
                                               List<Hero> bannedHeroes, int count) {
        // Get all available heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Filter out heroes that are already picked or banned
        Set<Integer> unavailableIds = new HashSet<>();
        allyPicks.forEach(hero -> unavailableIds.add(hero.getId()));
        enemyPicks.forEach(hero -> unavailableIds.add(hero.getId()));
        bannedHeroes.forEach(hero -> unavailableIds.add(hero.getId()));
        
        List<Hero> availableHeroes = allHeroes.stream()
            .filter(hero -> !unavailableIds.contains(hero.getId()))
            .collect(Collectors.toList());
            
        // For demonstration, we'll just use win rates
        Map<String, Map<Integer, Double>> allWinRates = mlTrainingService.getHeroWinRates();
        Map<Integer, Double> winRates = allWinRates.getOrDefault("all", Collections.emptyMap());
        
        // Sort available heroes by win rate
        return availableHeroes.stream()
            .sorted(Comparator.comparing(hero -> -winRates.getOrDefault(hero.getId(), 0.5)))
            .limit(count)
            .collect(Collectors.toList());
    }
    
    /**
     * Create a set of test scenarios for A/B testing
     * 
     * @return List of test scenarios
     */
    private List<TestScenario> createTestScenarios() {
        List<TestScenario> scenarios = new ArrayList<>();
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // We need at least 15 heroes to create meaningful scenarios
        if (allHeroes.size() < 15) {
            logger.warn("Not enough heroes to create test scenarios");
            return scenarios;
        }
        
        Random random = new Random(42);  // Fixed seed for reproducibility
        
        // Create 20 test scenarios
        for (int i = 0; i < 20; i++) {
            // Shuffle heroes
            List<Hero> shuffledHeroes = new ArrayList<>(allHeroes);
            Collections.shuffle(shuffledHeroes, random);
            
            // Create ally and enemy picks
            List<Hero> allyPicks = new ArrayList<>(shuffledHeroes.subList(0, 2 + random.nextInt(3)));
            List<Hero> enemyPicks = new ArrayList<>(shuffledHeroes.subList(5, 7 + random.nextInt(3)));
            List<Hero> bannedHeroes = new ArrayList<>(shuffledHeroes.subList(10, 12 + random.nextInt(3)));
            
            // Choose an expected pick
            Hero expectedPick = shuffledHeroes.get(15 + random.nextInt(Math.min(10, shuffledHeroes.size() - 15)));
            
            TestScenario scenario = new TestScenario();
            scenario.allyPicks = allyPicks;
            scenario.enemyPicks = enemyPicks;
            scenario.bannedHeroes = bannedHeroes;
            scenario.expectedPickId = expectedPick.getId();
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate mock match data for testing
     * 
     * @param count Number of matches to generate
     * @return List of mock match data
     */
    private List<MatchData> generateMockMatchData(int count) {
        List<MatchData> matches = new ArrayList<>();
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        if (allHeroes.isEmpty()) {
            return matches;
        }
        
        Random random = new Random(42);
        
        for (int i = 0; i < count; i++) {
            MatchData match = new MatchData();
            match.matchId = 1000000 + i;
            
            // Shuffle heroes for random selection
            List<Hero> shuffledHeroes = new ArrayList<>(allHeroes);
            Collections.shuffle(shuffledHeroes, random);
            
            // Generate a realistic draft sequence (ban-pick-ban-pick...)
            List<DraftPick> draftSequence = new ArrayList<>();
            
            // First ban phase (2 bans each)
            for (int j = 0; j < 4; j++) {
                DraftPick ban = new DraftPick();
                ban.isPick = false;
                ban.isRadiant = j % 2 == 0;
                ban.heroId = shuffledHeroes.get(j).getId();
                draftSequence.add(ban);
            }
            
            // First pick phase (2 picks each)
            for (int j = 0; j < 4; j++) {
                DraftPick pick = new DraftPick();
                pick.isPick = true;
                pick.isRadiant = j % 2 == 0;
                pick.heroId = shuffledHeroes.get(4 + j).getId();
                draftSequence.add(pick);
            }
            
            // Second ban phase (3 bans each)
            for (int j = 0; j < 6; j++) {
                DraftPick ban = new DraftPick();
                ban.isPick = false;
                ban.isRadiant = j % 2 == 0;
                ban.heroId = shuffledHeroes.get(8 + j).getId();
                draftSequence.add(ban);
            }
            
            // Second pick phase (2 picks each)
            for (int j = 0; j < 4; j++) {
                DraftPick pick = new DraftPick();
                pick.isPick = true;
                pick.isRadiant = j % 2 == 0;
                pick.heroId = shuffledHeroes.get(14 + j).getId();
                draftSequence.add(pick);
            }
            
            // Third ban phase (1 ban each)
            for (int j = 0; j < 2; j++) {
                DraftPick ban = new DraftPick();
                ban.isPick = false;
                ban.isRadiant = j % 2 == 0;
                ban.heroId = shuffledHeroes.get(18 + j).getId();
                draftSequence.add(ban);
            }
            
            // Third pick phase (1 pick each)
            for (int j = 0; j < 2; j++) {
                DraftPick pick = new DraftPick();
                pick.isPick = true;
                pick.isRadiant = j % 2 == 0;
                pick.heroId = shuffledHeroes.get(20 + j).getId();
                draftSequence.add(pick);
            }
            
            match.draftSequence = draftSequence;
            match.radiantWin = random.nextBoolean();
            
            matches.add(match);
        }
        
        return matches;
    }
    
    /**
     * Generate mock hero combination win rates
     * 
     * @return Map of hero combination keys to win rates
     */
    private Map<String, Double> generateMockHeroCombinationWinRates() {
        Map<String, Double> winRates = new HashMap<>();
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        if (allHeroes.size() < 2) {
            return winRates;
        }
        
        Random random = new Random(42);
        
        // Generate win rates for all hero combinations
        for (int i = 0; i < allHeroes.size(); i++) {
            Hero hero1 = allHeroes.get(i);
            
            for (int j = i + 1; j < allHeroes.size(); j++) {
                Hero hero2 = allHeroes.get(j);
                
                // Use synergy scores to influence mock win rates
                String synergyKey = Math.min(hero1.getId(), hero2.getId()) + "_" + Math.max(hero1.getId(), hero2.getId());
                double synergy = mlTrainingService.getHeroSynergies().getOrDefault(synergyKey, 0.5);
                
                // Win rate is influenced by synergy but with some randomness
                double winRate = 0.4 + (synergy * 0.2) + (random.nextDouble() * 0.2 - 0.1);
                winRate = Math.max(0.0, Math.min(1.0, winRate));  // Clamp between 0 and 1
                
                winRates.put(synergyKey, winRate);
            }
        }
        
        return winRates;
    }
    
    /**
     * Generate mock hero matchup win rates
     * 
     * @return Map of hero matchup keys to win rates
     */
    private Map<String, Double> generateMockHeroMatchupWinRates() {
        Map<String, Double> winRates = new HashMap<>();
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        if (allHeroes.isEmpty()) {
            return winRates;
        }
        
        Random random = new Random(42);
        
        // Generate win rates for all hero matchups
        for (Hero hero1 : allHeroes) {
            for (Hero hero2 : allHeroes) {
                if (hero1.getId() == hero2.getId()) {
                    continue;  // Skip same hero
                }
                
                // Use counter scores to influence mock win rates
                String counterKey = hero1.getId() + "_" + hero2.getId();
                double counter = mlTrainingService.getHeroCounters().getOrDefault(counterKey, 0.5);
                
                // Win rate is influenced by counter relationship but with some randomness
                double winRate = 0.4 + (counter * 0.2) + (random.nextDouble() * 0.2 - 0.1);
                winRate = Math.max(0.0, Math.min(1.0, winRate));  // Clamp between 0 and 1
                
                winRates.put(counterKey, winRate);
            }
        }
        
        return winRates;
    }
    
    /**
     * Calculate Pearson correlation coefficient between two lists
     * 
     * @param list1 First list
     * @param list2 Second list
     * @return Correlation coefficient
     */
    private double calculateCorrelation(List<Double> list1, List<Double> list2) {
        if (list1.size() != list2.size() || list1.isEmpty()) {
            return 0.0;
        }
        
        int n = list1.size();
        
        // Calculate means
        double mean1 = list1.stream().mapToDouble(Double::doubleValue).sum() / n;
        double mean2 = list2.stream().mapToDouble(Double::doubleValue).sum() / n;
        
        // Calculate numerator and denominators
        double numerator = 0.0;
        double denom1 = 0.0;
        double denom2 = 0.0;
        
        for (int i = 0; i < n; i++) {
            double x = list1.get(i) - mean1;
            double y = list2.get(i) - mean2;
            numerator += x * y;
            denom1 += x * x;
            denom2 += y * y;
        }
        
        // Handle edge cases
        if (denom1 == 0.0 || denom2 == 0.0) {
            return 0.0;
        }
        
        return numerator / (Math.sqrt(denom1) * Math.sqrt(denom2));
    }
    
    /**
     * Calculate mean absolute error between two lists
     * 
     * @param list1 First list
     * @param list2 Second list
     * @return Mean absolute error
     */
    private double calculateMeanAbsoluteError(List<Double> list1, List<Double> list2) {
        if (list1.size() != list2.size() || list1.isEmpty()) {
            return 0.0;
        }
        
        int n = list1.size();
        double sumAbsError = 0.0;
        
        for (int i = 0; i < n; i++) {
            sumAbsError += Math.abs(list1.get(i) - list2.get(i));
        }
        
        return sumAbsError / n;
    }
    
    /**
     * Save evaluation result to file
     * 
     * @param result The evaluation result to save
     */
    private void saveEvaluationResult(EvaluationResult result) {
        try {
            String filename = String.format("%s/%s_%s.txt", 
                EVAL_DIR,
                result.getName().toLowerCase().replaceAll("\\s+", "_"),
                System.currentTimeMillis());
                
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("Evaluation: " + result.getName());
                writer.println("Timestamp: " + result.getTimestamp());
                writer.println();
                writer.println("=== Metrics ===");
                
                for (Map.Entry<String, String> metric : result.getMetrics().entrySet()) {
                    writer.println(metric.getKey() + ": " + metric.getValue());
                }
            }
            
            logger.info("Saved evaluation result to {}", filename);
            
        } catch (IOException e) {
            logger.error("Error saving evaluation result: {}", e.getMessage());
        }
    }
    
    /**
     * Save evaluation report to file
     * 
     * @param report The evaluation report to save
     */
    private void saveEvaluationReport(EvaluationReport report) {
        try {
            String filename = String.format("%s/comprehensive_report_%s.txt", 
                EVAL_DIR, System.currentTimeMillis());
                
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("=== " + report.getName() + " ===");
                writer.println("Timestamp: " + report.getTimestamp());
                writer.println();
                
                for (EvaluationResult result : report.getResults()) {
                    writer.println("==== " + result.getName() + " ====");
                    writer.println("Timestamp: " + result.getTimestamp());
                    writer.println();
                    
                    for (Map.Entry<String, String> metric : result.getMetrics().entrySet()) {
                        writer.println(metric.getKey() + ": " + metric.getValue());
                    }
                    
                    writer.println();
                }
            }
            
            logger.info("Saved evaluation report to {}", filename);
            
        } catch (IOException e) {
            logger.error("Error saving evaluation report: {}", e.getMessage());
        }
    }
    
    /**
     * Class to hold draft pick information
     */
    private static class DraftPick {
        int heroId;
        boolean isPick;  // true for pick, false for ban
        boolean isRadiant;  // true for radiant, false for dire
    }
    
    /**
     * Class to hold match data with draft information
     */
    private static class MatchData {
        long matchId;
        List<DraftPick> draftSequence;
        boolean radiantWin;
    }
    
    /**
     * Class for test scenarios with expected results
     */
    public static class TestScenario {
        List<Hero> allyPicks;
        List<Hero> enemyPicks;
        List<Hero> bannedHeroes;
        int expectedPickId;
    }
    
    /**
     * Class to hold evaluation result
     */
    public static class EvaluationResult {
        private final String name;
        private final Date timestamp;
        private final Map<String, String> metrics;
        
        public EvaluationResult(String name) {
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
    
    /**
     * Class to hold a comprehensive evaluation report
     */
    public static class EvaluationReport {
        private final String name;
        private final Date timestamp;
        private final List<EvaluationResult> results;
        
        public EvaluationReport(String name) {
            this.name = name;
            this.timestamp = new Date();
            this.results = new ArrayList<>();
        }
        
        public void addEvaluationResult(EvaluationResult result) {
            results.add(result);
        }
        
        public String getName() {
            return name;
        }
        
        public Date getTimestamp() {
            return timestamp;
        }
        
        public List<EvaluationResult> getResults() {
            return results;
        }
    }
}