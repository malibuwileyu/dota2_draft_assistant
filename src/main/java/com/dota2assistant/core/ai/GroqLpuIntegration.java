package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.core.draft.DraftState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Integration with Groq LPU for real-time inference during the draft phase.
 * This class provides an interface to the Groq Large Processing Units for 
 * high-performance AI-powered draft recommendations.
 * 
 * Uses Groq's LLM API to analyze draft context and provide intelligent,
 * context-aware hero recommendations with detailed explanations.
 */
@Service
public class GroqLpuIntegration {

    private static final Logger logger = LoggerFactory.getLogger(GroqLpuIntegration.class);
    
    private final HeroRepository heroRepository;
    private final RestTemplate restTemplate;
    
    @Value("${groq.api.enabled:false}")
    private boolean groqEnabled;
    
    @Value("${groq.api.url:https://api.groq.com}")
    private String groqApiUrl;
    
    @Value("${groq.api.model:llama3-70b-8192}")
    private String groqApiModel;
    
    @Value("${groq.api.key:}")
    private String groqApiKey;
    
    @Value("${groq.api.timeout:10000}")
    private int groqApiTimeout;
    
    @Autowired
    public GroqLpuIntegration(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
        this.restTemplate = new RestTemplate();
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing Groq LPU integration with model: {}", groqApiModel);
        logger.info("Groq API integration is {}", groqEnabled ? "enabled" : "disabled");
        if (groqEnabled) {
            logger.info("Testing Groq API connection...");
            try {
                testGroqConnection();
                logger.info("Groq API connection successful");
            } catch (Exception e) {
                logger.error("Error connecting to Groq API: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Test connection to Groq API
     */
    private void testGroqConnection() {
        String prompt = "Return only the text 'Groq API connection successful.' nothing else.";
        String response = callGroqApi(prompt);
        logger.debug("Groq API test response: {}", response);
    }
    
    /**
     * Get hero recommendations with Groq-powered explanations
     * 
     * @param draftState The current state of the draft
     * @param forTeam The team to recommend heroes for
     * @param count Number of recommendations to return
     * @return List of recommendations with explanations
     */
    public List<HeroRecommendation> getHeroRecommendations(DraftState draftState, String forTeam, int count) {
        logger.info("Generating Groq LPU hero recommendations for team: {}", forTeam);
        
        // Get available heroes (not picked or banned)
        List<Hero> availableHeroes = getAvailableHeroes(draftState);
        
        // Get current team compositions
        List<Hero> allyHeroes;
        List<Hero> enemyHeroes;
        
        if ("radiant".equalsIgnoreCase(forTeam)) {
            allyHeroes = draftState.getRadiantPicks();
            enemyHeroes = draftState.getDirePicks();
        } else {
            allyHeroes = draftState.getDirePicks();
            enemyHeroes = draftState.getRadiantPicks();
        }
        
        // Score each available hero
        Map<Hero, Double> heroScores = new HashMap<>();
        for (Hero hero : availableHeroes) {
            double score = scoreHeroForDraft(hero, allyHeroes, enemyHeroes);
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score and take the top ones
        List<Hero> topHeroes = heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Generate recommendations with explanations using Groq LPU
        List<HeroRecommendation> recommendations = new ArrayList<>();
        for (Hero hero : topHeroes) {
            String explanation;
            if (groqEnabled) {
                explanation = generateGroqRecommendationExplanation(hero, allyHeroes, enemyHeroes);
            } else {
                explanation = generateBasicRecommendationExplanation(hero, allyHeroes, enemyHeroes);
            }
            recommendations.add(new HeroRecommendation(hero, explanation, heroScores.get(hero)));
        }
        
        return recommendations;
    }
    
    /**
     * Score a hero for the current draft situation.
     * This provides a basic scoring mechanism - the real intelligence comes from Groq LLM.
     * 
     * @param hero The hero to score
     * @param allyHeroes Current allies
     * @param enemyHeroes Current enemies
     * @return A score representing how good the hero is for the draft
     */
    private double scoreHeroForDraft(Hero hero, List<Hero> allyHeroes, List<Hero> enemyHeroes) {
        // Base score for all heroes
        double score = 0.5;
        
        // Simple heuristic: heroes with multiple roles are more flexible
        if (hero.getRoles() != null && hero.getRoles().size() > 2) {
            score += 0.1;
        }
        
        // Simple heuristic: position flexibility is valuable in early draft
        if (hero.getRoleFrequency() != null && !hero.getRoleFrequency().isEmpty()) {
            int positionsPlayed = (int) hero.getRoleFrequency().values().stream()
                .filter(freq -> freq >= 0.2)
                .count();
            
            if (allyHeroes.size() <= 2 && positionsPlayed > 1) {
                score += 0.2; // Boost flexible heroes in early draft
            }
        }
        
        // Add small random variation to prevent identical scores
        score += Math.random() * 0.1;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get all heroes that are available for picking (not picked or banned)
     */
    private List<Hero> getAvailableHeroes(DraftState draftState) {
        Set<Integer> unavailableIds = new HashSet<>();
        
        // Add picked heroes
        for (Hero hero : draftState.getRadiantPicks()) {
            unavailableIds.add(hero.getId());
        }
        for (Hero hero : draftState.getDirePicks()) {
            unavailableIds.add(hero.getId());
        }
        
        // Add banned heroes
        for (Hero hero : draftState.getBannedHeroes()) {
            unavailableIds.add(hero.getId());
        }
        
        // Return all heroes except unavailable ones
        return heroRepository.getAllHeroes().stream()
            .filter(hero -> !unavailableIds.contains(hero.getId()))
            .collect(Collectors.toList());
    }
    
    /**
     * Generate a recommendation explanation using Groq API
     */
    private String generateGroqRecommendationExplanation(Hero hero, List<Hero> allyHeroes, List<Hero> enemyHeroes) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert Dota 2 AI assistant providing hero recommendations during the draft phase.\n\n");
        
        // Add hero information details including abilities
        prompt.append("Hero details:\n");
        prompt.append("Name: ").append(hero.getLocalizedName()).append("\n");
        prompt.append("Primary attribute: ").append(hero.getPrimaryAttribute()).append("\n");
        prompt.append("Roles: ").append(String.join(", ", hero.getRoles())).append("\n");
        
        // Add ability information for context
        prompt.append("Abilities:\n");
        for (Ability ability : hero.getAbilities()) {
            prompt.append("- ").append(ability.getName()).append(": ").append(ability.getDescription()).append("\n");
        }
        prompt.append("\n");
        
        // Add position data if available
        if (hero.getRoleFrequency() != null && !hero.getRoleFrequency().isEmpty()) {
            prompt.append("Position data: ");
            for (Map.Entry<Integer, Double> entry : hero.getRoleFrequency().entrySet()) {
                if (entry.getValue() > 0.2) { // Only include significant positions
                    prompt.append("Position ").append(entry.getKey()).append(" (").append(String.format("%.0f%%", entry.getValue() * 100)).append(") ");
                }
            }
            prompt.append("\n\n");
        }
        
        prompt.append("Please explain why picking ").append(hero.getLocalizedName()).append(" would be beneficial ");
        
        // Add ally heroes with details
        prompt.append("when my team already has: ");
        if (allyHeroes.isEmpty()) {
            prompt.append("no heroes picked yet");
        } else {
            List<String> allyDetails = new ArrayList<>();
            for (Hero ally : allyHeroes) {
                StringBuilder allyInfo = new StringBuilder();
                allyInfo.append(ally.getLocalizedName());
                
                // Add main roles/positions if available
                if (ally.getRoleFrequency() != null && !ally.getRoleFrequency().isEmpty()) {
                    int mainPosition = ally.getRoleFrequency().entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(0);
                    
                    if (mainPosition > 0) {
                        allyInfo.append(" (pos ").append(mainPosition).append(")");
                    }
                }
                allyDetails.add(allyInfo.toString());
            }
            prompt.append(String.join(", ", allyDetails));
        }
        
        // Add enemy heroes with details
        prompt.append(", and the enemy team has: ");
        if (enemyHeroes.isEmpty()) {
            prompt.append("no heroes picked yet");
        } else {
            List<String> enemyDetails = new ArrayList<>();
            for (Hero enemy : enemyHeroes) {
                StringBuilder enemyInfo = new StringBuilder();
                enemyInfo.append(enemy.getLocalizedName());
                
                // Add main roles/positions if available
                if (enemy.getRoleFrequency() != null && !enemy.getRoleFrequency().isEmpty()) {
                    int mainPosition = enemy.getRoleFrequency().entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(0);
                    
                    if (mainPosition > 0) {
                        enemyInfo.append(" (pos ").append(mainPosition).append(")");
                    }
                }
                enemyDetails.add(enemyInfo.toString());
            }
            prompt.append(String.join(", ", enemyDetails));
        }
        prompt.append(".\n\n");
        
        // Add phase information based on current team size
        boolean isEarlyDraft = allyHeroes.size() <= 2;
        boolean isMidDraft = allyHeroes.size() > 2 && allyHeroes.size() <= 3;
        boolean isLateDraft = allyHeroes.size() > 3;
        
        prompt.append("Current draft stage: ");
        if (isEarlyDraft) {
            prompt.append("Early draft (picks 1-2). Flexibility is highly valuable at this stage.\n\n");
        } else if (isMidDraft) {
            prompt.append("Mid draft (pick 3). Balance between flexibility and specific roles.\n\n");
        } else if (isLateDraft) {
            prompt.append("Late draft (picks 4-5). Specific role filling and counter-picking is important.\n\n");
        }
        
        // Add specific instructions
        prompt.append("Please provide a detailed explanation covering:\n");
        prompt.append("1. How this hero synergizes with my team\n");
        prompt.append("2. How effective this hero is against the enemy team\n");
        prompt.append("3. What this hero contributes to overall team composition (teamfight, initiation, control, damage type balance)\n");
        prompt.append("4. This hero's timing and power spikes relative to our draft\n");
        prompt.append("5. ");
        
        if (isEarlyDraft) {
            prompt.append("How this hero's position flexibility benefits early draft strategy\n\n");
        } else {
            prompt.append("Optimal position recommendation for this hero in our draft\n\n");
        }
        
        prompt.append("Format your answer in Markdown with sections for each point. Be concise but thorough.\n");
        
        try {
            return callGroqApi(prompt.toString());
        } catch (Exception e) {
            logger.error("Error calling Groq API: {}", e.getMessage());
            // Fall back to basic explanation if Groq API fails
            return generateBasicRecommendationExplanation(hero, allyHeroes, enemyHeroes);
        }
    }
    
    /**
     * Generate a basic recommendation explanation without LLM
     * Used as fallback when Groq API is disabled or fails
     */
    private String generateBasicRecommendationExplanation(Hero hero, List<Hero> allyHeroes, List<Hero> enemyHeroes) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("**").append(hero.getLocalizedName()).append("** could be a good pick for this draft.\n\n");
        
        // Basic role information
        if (hero.getRoles() != null && !hero.getRoles().isEmpty()) {
            explanation.append("**Roles:** ").append(String.join(", ", hero.getRoles())).append("\n\n");
        }
        
        // Position flexibility
        if (hero.getRoleFrequency() != null && !hero.getRoleFrequency().isEmpty()) {
            explanation.append("**Position Flexibility:** ");
            List<String> positions = new ArrayList<>();
            for (Map.Entry<Integer, Double> entry : hero.getRoleFrequency().entrySet()) {
                if (entry.getValue() > 0.2) {
                    positions.add(String.format("Pos %d (%.0f%%)", entry.getKey(), entry.getValue() * 100));
                }
            }
            if (!positions.isEmpty()) {
                explanation.append(String.join(", ", positions)).append("\n\n");
            }
        }
        
        // Team context
        if (!allyHeroes.isEmpty()) {
            explanation.append("**Current Team:** ");
            explanation.append(allyHeroes.stream()
                .map(Hero::getLocalizedName)
                .collect(Collectors.joining(", ")));
            explanation.append("\n\n");
        }
        
        if (!enemyHeroes.isEmpty()) {
            explanation.append("**Enemy Team:** ");
            explanation.append(enemyHeroes.stream()
                .map(Hero::getLocalizedName)
                .collect(Collectors.joining(", ")));
            explanation.append("\n\n");
        }
        
        explanation.append("*Note: Enable Groq API for detailed AI-powered analysis and recommendations.*");
        
        return explanation.toString();
    }
    
    /**
     * Call Groq API with the given prompt
     */
    private String callGroqApi(String prompt) {
        if (!groqEnabled) {
            logger.warn("Attempted to call Groq API but integration is disabled");
            return "Groq API is disabled. Enable it in application.properties.";
        }
        
        try {
            String apiUrl = groqApiUrl + "/v1/chat/completions";
            logger.info("Calling Groq API at URL: {}", apiUrl);
            
            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + groqApiKey);
            
            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", groqApiModel);
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are an expert Dota 2 strategist providing draft advice.");
            messages.add(systemMessage);
            
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);
            
            // Make request
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);
            
            // Extract response text
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    return (String) message.get("content");
                }
            }
            
            throw new RuntimeException("Invalid response format from Groq API");
            
        } catch (Exception e) {
            logger.error("Error calling Groq API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Groq API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Class to represent a hero recommendation with explanation
     */
    public static class HeroRecommendation {
        private final Hero hero;
        private final String explanation;
        private final double score;
        
        public HeroRecommendation(Hero hero, String explanation, double score) {
            this.hero = hero;
            this.explanation = explanation;
            this.score = score;
        }
        
        public Hero getHero() {
            return hero;
        }
        
        public String getExplanation() {
            return explanation;
        }
        
        public double getScore() {
            return score;
        }
    }
}