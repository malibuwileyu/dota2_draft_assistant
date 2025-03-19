package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Standalone model validation utility for Dota 2 Draft Assistant.
 * Can be executed directly from Maven for Windows compatibility.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.dota2assistant", 
               excludeFilters = @ComponentScan.Filter(
                   type = FilterType.ASSIGNABLE_TYPE, 
                   classes = {com.dota2assistant.Dota2DraftAssistant.class}))
public class ModelValidation implements CommandLineRunner {

    private final HeroRepository heroRepository;
    private final MlBasedAiDecisionEngine mlDecisionEngine;
    
    public ModelValidation(HeroRepository heroRepository,
                         MlBasedAiDecisionEngine mlDecisionEngine) {
        this.heroRepository = heroRepository;
        this.mlDecisionEngine = mlDecisionEngine;
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Dota 2 Draft Assistant Model Validation");
        SpringApplication.run(ModelValidation.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        String scenarioPath = null;
        
        // Parse command line arguments
        for (String arg : args) {
            if (arg.startsWith("--scenario=")) {
                scenarioPath = arg.substring("--scenario=".length());
            }
        }
        
        if (scenarioPath == null) {
            System.err.println("Error: No scenario specified. Use --scenario=<path>");
            System.exit(1);
        }
        
        // Validate the scenario
        validateScenario(scenarioPath);
    }
    
    /**
     * Validates a scenario file against the ML model
     */
    private void validateScenario(String scenarioPath) {
        try {
            System.out.println("Validating scenario: " + scenarioPath);
            File scenarioFile = new File(scenarioPath);
            
            if (!scenarioFile.exists()) {
                System.err.println("Error: Scenario file not found: " + scenarioPath);
                System.exit(1);
            }
            
            // Parse scenario JSON
            JSONParser parser = new JSONParser();
            JSONObject scenario = (JSONObject) parser.parse(new FileReader(scenarioFile));
            
            // Extract hero IDs
            List<Hero> allyHeroes = extractHeroes((JSONArray) scenario.get("allyHeroIds"));
            List<Hero> enemyHeroes = extractHeroes((JSONArray) scenario.get("enemyHeroIds"));
            List<Hero> bannedHeroes = extractHeroes((JSONArray) scenario.get("bannedHeroIds"));
            int expectedPickId = ((Long) scenario.get("expectedPickId")).intValue();
            
            // Get model recommendations
            System.out.println("Getting model recommendations...");
            List<Hero> recommendations = mlDecisionEngine.suggestPicks(allyHeroes, enemyHeroes, bannedHeroes, 5);
            
            // Create result JSON
            JSONObject result = new JSONObject();
            result.put("scenario", scenarioFile.getName().replace(".json", ""));
            result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            result.put("expected_hero_id", expectedPickId);
            
            // Add recommendations
            JSONArray recommendationIds = new JSONArray();
            for (Hero hero : recommendations) {
                recommendationIds.add(hero.getId());
            }
            result.put("ml_recommendations", recommendationIds);
            result.put("ml_top_pick", recommendations.isEmpty() ? -1 : recommendations.get(0).getId());
            
            // Add validation result
            boolean success = !recommendations.isEmpty() && recommendations.get(0).getId() == expectedPickId;
            result.put("validation_result", success ? "PASS" : "FAIL");
            
            // Get reasoning for recommended hero
            if (!recommendations.isEmpty()) {
                JSONObject reasoning = new JSONObject();
                mlDecisionEngine.getHeroRecommendationReasoning(recommendations.get(0), allyHeroes, enemyHeroes)
                    .forEach(reasoning::put);
                result.put("reasoning", reasoning);
            }
            
            // Save result to file
            String resultDir = "data/validation_results";
            Files.createDirectories(Paths.get(resultDir));
            
            String resultFile = resultDir + "/scenario_" + 
                scenarioFile.getName().replace(".json", "") + "_" + 
                System.currentTimeMillis() + ".json";
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile))) {
                writer.write(result.toJSONString());
            }
            
            System.out.println("Validation " + (success ? "PASSED" : "FAILED"));
            System.out.println("Results saved to: " + resultFile);
            System.exit(success ? 0 : 1);
            
        } catch (Exception e) {
            System.err.println("Error validating scenario: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Extract hero objects from hero IDs
     */
    private List<Hero> extractHeroes(JSONArray heroIds) {
        List<Hero> heroes = new ArrayList<>();
        
        for (Object id : heroIds) {
            int heroId = ((Long) id).intValue();
            Hero hero = heroRepository.getHeroById(heroId);
            if (hero != null) {
                heroes.add(hero);
            } else {
                System.err.println("Warning: Hero not found with ID: " + heroId);
            }
        }
        
        return heroes;
    }
}