package com.dota2assistant.core.ai;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Simple standalone expert review generator that doesn't rely on Spring Boot.
 * This class can be executed directly to generate review templates.
 */
public class SimpleExpertReviewGenerator {

    private static final String SCENARIOS_DIR = "data/scenarios";
    private static final String REVIEW_DIR = "data/expert_reviews";

    /**
     * Main entry point for expert review generation
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting expert review template generation...");
            
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
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("Error generating expert reviews: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Process a scenario file and generate expert review template
     */
    private static void processScenario(File scenarioFile) throws Exception {
        System.out.println("Processing scenario: " + scenarioFile.getName());
        
        // Parse scenario JSON
        JSONParser parser = new JSONParser();
        JSONObject scenario = (JSONObject) parser.parse(new FileReader(scenarioFile));
        
        // Extract hero IDs
        JSONArray allyHeroIds = (JSONArray) scenario.get("allyHeroIds");
        JSONArray enemyHeroIds = (JSONArray) scenario.get("enemyHeroIds");
        JSONArray bannedHeroIds = (JSONArray) scenario.get("bannedHeroIds");
        long expectedPickId = (Long) scenario.get("expectedPickId");
        
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
            for (Object id : allyHeroIds) {
                writer.write("- Hero " + id + " (ID: " + id + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Enemy Heroes
            writer.write("### Enemy Heroes:");
            writer.newLine();
            for (Object id : enemyHeroIds) {
                writer.write("- Hero " + id + " (ID: " + id + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Banned Heroes
            writer.write("### Banned Heroes:");
            writer.newLine();
            for (Object id : bannedHeroIds) {
                writer.write("- Hero " + id + " (ID: " + id + ")");
                writer.newLine();
            }
            writer.newLine();
            
            // Add Expected Pick
            writer.write("### Expected Pick:");
            writer.newLine();
            writer.write("- Hero " + expectedPickId + " (ID: " + expectedPickId + ")");
            writer.newLine();
            writer.newLine();
            
            // Add model recommendations (mock data for now)
            writer.write("## Model Recommendations");
            writer.newLine();
            
            writer.write("### Top Recommendations:");
            writer.newLine();
            writer.write("1. Hero " + expectedPickId + " (ID: " + expectedPickId + ")");
            writer.newLine();
            writer.write("2. Hero " + (expectedPickId + 1) + " (ID: " + (expectedPickId + 1) + ")");
            writer.newLine();
            writer.write("3. Hero " + (expectedPickId + 2) + " (ID: " + (expectedPickId + 2) + ")");
            writer.newLine();
            writer.newLine();
            
            // Add mock reasoning
            writer.write("### Explanations:");
            writer.newLine();
            writer.newLine();
            
            writer.write("#### Statistical Factors:");
            writer.newLine();
            writer.write("Win Rate: 55.5%, Pick Rate: 12.3% in recent matches.");
            writer.newLine();
            writer.newLine();
            
            writer.write("#### Ability Analysis:");
            writer.newLine();
            writer.write("Strong control abilities and good teamfight presence.");
            writer.newLine();
            writer.newLine();
            
            writer.write("#### Team Synergy:");
            writer.newLine();
            writer.write("Complements the team's need for control and initiation.");
            writer.newLine();
            writer.newLine();
            
            writer.write("#### Enemy Counters:");
            writer.newLine();
            writer.write("Effective against the enemy's mobile heroes.");
            writer.newLine();
            writer.newLine();
            
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
    private static void createSampleScenarios() throws IOException {
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
        
        // Save scenarios to files
        Files.createDirectories(Paths.get(SCENARIOS_DIR));
        
        try (FileWriter file = new FileWriter(SCENARIOS_DIR + "/stun_combo_scenario.json")) {
            file.write(scenario1.toJSONString());
        }
        
        try (FileWriter file = new FileWriter(SCENARIOS_DIR + "/magic_damage_scenario.json")) {
            file.write(scenario2.toJSONString());
        }
        
        System.out.println("Sample scenarios created");
    }
}