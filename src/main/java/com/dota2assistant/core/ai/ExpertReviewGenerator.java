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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Expert review template generator for Dota 2 Draft Assistant.
 * Creates human-readable reviews from model recommendations.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.dota2assistant", 
               excludeFilters = @ComponentScan.Filter(
                   type = FilterType.ASSIGNABLE_TYPE, 
                   classes = {com.dota2assistant.Dota2DraftAssistant.class}))
public class ExpertReviewGenerator implements CommandLineRunner {

    private final HeroRepository heroRepository;
    private final MlBasedAiDecisionEngine mlDecisionEngine;
    
    private static final String SCENARIOS_DIR = "data/scenarios";
    private static final String REVIEW_DIR = "data/expert_reviews";
    
    public ExpertReviewGenerator(HeroRepository heroRepository,
                              MlBasedAiDecisionEngine mlDecisionEngine) {
        this.heroRepository = heroRepository;
        this.mlDecisionEngine = mlDecisionEngine;
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Dota 2 Expert Review Generation");
        SpringApplication.run(ExpertReviewGenerator.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("Generating expert review templates...");
        
        // Create directories if they don't exist
        Files.createDirectories(Paths.get(SCENARIOS_DIR));
        Files.createDirectories(Paths.get(REVIEW_DIR));
        
        // Find all scenario files
        File scenariosFolder = new File(SCENARIOS_DIR);
        File[] scenarioFiles = scenariosFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        
        if (scenarioFiles == null || scenarioFiles.length == 0) {
            // Create some sample scenarios if none exist
            createSampleScenarios();
            scenarioFiles = scenariosFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        }
        
        if (scenarioFiles == null || scenarioFiles.length == 0) {
            System.err.println("No scenario files found or created!");
            System.exit(1);
        }
        
        // Process each scenario
        for (File scenarioFile : scenarioFiles) {
            processScenario(scenarioFile);
        }
        
        System.out.println("Expert review templates generated successfully in: " + REVIEW_DIR);
    }
    
    /**
     * Process a scenario file and generate expert review
     */
    private void processScenario(File scenarioFile) throws Exception {
        System.out.println("Processing scenario: " + scenarioFile.getName());
        
        // Parse scenario JSON
        JSONParser parser = new JSONParser();
        JSONObject scenario = (JSONObject) parser.parse(new FileReader(scenarioFile));
        
        // Extract hero IDs
        List<Hero> allyHeroes = extractHeroes((JSONArray) scenario.get("allyHeroIds"));
        List<Hero> enemyHeroes = extractHeroes((JSONArray) scenario.get("enemyHeroIds"));
        List<Hero> bannedHeroes = extractHeroes((JSONArray) scenario.get("bannedHeroIds"));
        long expectedPickId = (Long) scenario.get("expectedPickId");
        
        // Get model recommendations
        List<Hero> recommendations = mlDecisionEngine.suggestPicks(allyHeroes, enemyHeroes, bannedHeroes, 5);
        
        // Generate review template
        String reviewFileName = scenarioFile.getName().replace(".json", "") + 
            "_review_" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".md";
        File reviewFile = new File(REVIEW_DIR, reviewFileName);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reviewFile))) {
            // Add header
            writer.write("# Expert Review: " + scenarioFile.getName().replace(".json", ""));
            writer.newLine();
            writer.write("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine();
            writer.newLine();
            
            // Add scenario description
            writer.write("## Scenario");
            writer.newLine();
            
            // Add Allied Heroes
            writer.write("### Allied Heroes:");
            writer.newLine();
            for (Hero hero : allyHeroes) {
                writer.write("- " + hero.getLocalizedName() + " (ID: " + hero.getId() + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Enemy Heroes
            writer.write("### Enemy Heroes:");
            writer.newLine();
            for (Hero hero : enemyHeroes) {
                writer.write("- " + hero.getLocalizedName() + " (ID: " + hero.getId() + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Banned Heroes
            writer.write("### Banned Heroes:");
            writer.newLine();
            for (Hero hero : bannedHeroes) {
                writer.write("- " + hero.getLocalizedName() + " (ID: " + hero.getId() + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Expected Pick
            Hero expectedHero = heroRepository.getHeroById((int) expectedPickId);
            writer.write("### Expected Pick:");
            writer.newLine();
            if (expectedHero != null) {
                writer.write("- " + expectedHero.getLocalizedName() + " (ID: " + expectedHero.getId() + ")");
            } else {
                writer.write("- Unknown hero (ID: " + expectedPickId + ")");
            }
            writer.newLine();
            writer.newLine();
            
            // Add model recommendations
            writer.write("## Model Recommendations");
            writer.newLine();
            
            writer.write("### Top Recommendations:");
            writer.newLine();
            int rank = 1;
            for (Hero hero : recommendations) {
                writer.write(rank + ". " + hero.getLocalizedName() + " (ID: " + hero.getId() + ")");
                writer.newLine();
                rank++;
            }
            writer.newLine();
            
            // Add reasoning for top recommendation
            if (!recommendations.isEmpty()) {
                writer.write("### Explanations:");
                writer.newLine();
                writer.newLine();
                
                Hero topHero = recommendations.get(0);
                Map<String, String> reasoning = mlDecisionEngine.getHeroRecommendationReasoning(topHero, allyHeroes, enemyHeroes);
                
                writer.write("#### Statistical Factors:");
                writer.newLine();
                writer.write(reasoning.getOrDefault("Statistics", "No statistics available"));
                writer.newLine();
                writer.newLine();
                
                writer.write("#### Ability Analysis:");
                writer.newLine();
                writer.write(reasoning.getOrDefault("Abilities", "No ability analysis available"));
                writer.newLine();
                writer.newLine();
                
                writer.write("#### Team Synergy:");
                writer.newLine();
                writer.write(reasoning.getOrDefault("Team Synergy", "No synergy analysis available"));
                writer.newLine();
                writer.newLine();
                
                writer.write("#### Enemy Counters:");
                writer.newLine();
                writer.write(reasoning.getOrDefault("Enemy Counters", "No counter analysis available"));
                writer.newLine();
                writer.newLine();
            }
            
            // Add expert review template
            writer.write("## Expert Review");
            writer.newLine();
            writer.newLine();
            writer.write("### Recommendation Quality (1-5):");
            writer.newLine();
            writer.write("___ ");
            writer.newLine();
            writer.newLine();
            writer.write("### Reasoning Quality (1-5):");
            writer.newLine();
            writer.write("___ ");
            writer.newLine();
            writer.newLine();
            writer.write("### Comments on Recommendations:");
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("### Comments on Reasoning:");
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("### Suggested Improvements:");
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("### Additional Notes:");
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("```");
            writer.newLine();
        }
        
        System.out.println("Generated review template: " + reviewFile.getPath());
    }
    
    /**
     * Create sample scenarios if none exist
     */
    private void createSampleScenarios() throws IOException {
        System.out.println("Creating sample scenarios...");
        
        // Sample 1: Stun combo scenario
        JSONObject scenario1 = new JSONObject();
        scenario1.put("allyHeroIds", new JSONArray() {{ 
            add(22L); add(17L); add(19L); add(53L); 
        }});
        scenario1.put("enemyHeroIds", new JSONArray() {{ 
            add(8L); add(11L); add(25L); add(32L); add(35L); 
        }});
        scenario1.put("bannedHeroIds", new JSONArray() {{ 
            add(1L); add(6L); add(33L); add(41L); add(86L); add(90L); add(114L); 
        }});
        scenario1.put("expectedPickId", 26L);
        
        // Sample 2: Magic damage scenario
        JSONObject scenario2 = new JSONObject();
        scenario2.put("allyHeroIds", new JSONArray() {{ 
            add(5L); add(31L); add(26L); add(75L); 
        }});
        scenario2.put("enemyHeroIds", new JSONArray() {{ 
            add(1L); add(10L); add(49L); add(62L); add(81L); 
        }});
        scenario2.put("bannedHeroIds", new JSONArray() {{ 
            add(8L); add(74L); add(86L); add(87L); add(92L); add(114L); 
        }});
        scenario2.put("expectedPickId", 64L);
        
        // Sample 3: Physical damage scenario
        JSONObject scenario3 = new JSONObject();
        scenario3.put("allyHeroIds", new JSONArray() {{ 
            add(6L); add(35L); add(44L); add(54L); 
        }});
        scenario3.put("enemyHeroIds", new JSONArray() {{ 
            add(36L); add(43L); add(67L); add(74L); add(79L); 
        }});
        scenario3.put("bannedHeroIds", new JSONArray() {{ 
            add(1L); add(8L); add(14L); add(38L); add(41L); add(82L); add(84L); 
        }});
        scenario3.put("expectedPickId", 28L);
        
        // Save scenarios to files
        try (FileWriter file = new FileWriter(SCENARIOS_DIR + "/stun_combo_scenario.json")) {
            file.write(scenario1.toJSONString());
        }
        
        try (FileWriter file = new FileWriter(SCENARIOS_DIR + "/magic_damage_scenario.json")) {
            file.write(scenario2.toJSONString());
        }
        
        try (FileWriter file = new FileWriter(SCENARIOS_DIR + "/physical_damage_scenario.json")) {
            file.write(scenario3.toJSONString());
        }
        
        System.out.println("Sample scenarios created");
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