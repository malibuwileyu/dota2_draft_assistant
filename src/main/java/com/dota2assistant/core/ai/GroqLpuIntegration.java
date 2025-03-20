package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.core.draft.DraftState;
import com.dota2assistant.util.PropertyLoader;
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
 * high-performance AI recommendations.
 * 
 * Note: This is a simulation of what such an integration would look like.
 * In a real implementation, this would communicate with the Groq API.
 */
@Service
public class GroqLpuIntegration {

    private static final Logger logger = LoggerFactory.getLogger(GroqLpuIntegration.class);
    
    private final HeroRepository heroRepository;
    private final NlpModelIntegration nlpModel;
    private final RestTemplate restTemplate;
    
    @Value("${groq.api.enabled:false}")
    private boolean groqEnabled;
    
    @Value("${groq.api.url:https://api.groq.com}")
    private String groqApiUrl;
    
    @Value("${groq.api.model:llama3-medium-8b}")
    private String groqApiModel;
    
    @Value("${groq.api.key:dummy-key}")
    private String groqApiKey;
    
    @Value("${groq.api.timeout:10000}")
    private int groqApiTimeout;
    
    @Autowired
    public GroqLpuIntegration(HeroRepository heroRepository, NlpModelIntegration nlpModel) {
        this.heroRepository = heroRepository;
        this.nlpModel = nlpModel;
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
                explanation = nlpModel.generateRecommendationExplanation(hero, allyHeroes, enemyHeroes);
            }
            recommendations.add(new HeroRecommendation(hero, explanation, heroScores.get(hero)));
        }
        
        return recommendations;
    }
    
    /**
     * Score a hero for the current draft situation
     * 
     * @param hero The hero to score
     * @param allyHeroes Current allies
     * @param enemyHeroes Current enemies
     * @return A score representing how good the hero is for the draft
     */
    private double scoreHeroForDraft(Hero hero, List<Hero> allyHeroes, List<Hero> enemyHeroes) {
        // In a real implementation, this would use the Groq LPU for inference
        // This is a simplified version that combines various factors
        
        double score = 0.0;
        
        // Get hero feature vector
        Map<String, Double> heroFeatures = nlpModel.getHeroFeatureVector(hero.getId());
        
        // Factor 1: Team synergy (weight 0.4)
        double synergy = 0.0;
        for (Hero ally : allyHeroes) {
            double similarity = nlpModel.getHeroSimilarity(hero.getId(), ally.getId());
            List<NlpAbilityAnalyzer.AbilitySynergy> synergies = nlpModel.findAbilitySynergies(hero.getId(), ally.getId());
            
            // Add score based on top synergy
            if (!synergies.isEmpty()) {
                double topSynergyScore = synergies.stream()
                        .mapToDouble(NlpAbilityAnalyzer.AbilitySynergy::getScore)
                        .max()
                        .orElse(0.0);
                synergy += topSynergyScore;
            }
        }
        
        if (!allyHeroes.isEmpty()) {
            synergy /= allyHeroes.size();  // Normalize by team size
        }
        
        score += 0.4 * synergy;
        
        // Factor 2: Counter enemies (weight 0.4)
        double counter = 0.0;
        for (Hero enemy : enemyHeroes) {
            // Check if this hero's strengths counter enemy weaknesses
            Map<String, Double> enemyFeatures = nlpModel.getHeroFeatureVector(enemy.getId());
            
            // Example: If we have control and enemy has mobility
            if (heroFeatures.getOrDefault("stun_score", 0.0) > 0.5 && 
                enemyFeatures.getOrDefault("mobility_score", 0.0) > 0.7) {
                counter += 0.3;
            }
            
            // Example: If we have silence and enemy relies on spells
            if (heroFeatures.getOrDefault("silence_score", 0.0) > 0.5 && 
                enemyFeatures.getOrDefault("magical_damage", 0.0) > 0.7) {
                counter += 0.3;
            }
            
            // Example: If we have mobility and enemy lacks control
            if (heroFeatures.getOrDefault("mobility_score", 0.0) > 0.7 && 
                enemyFeatures.getOrDefault("stun_score", 0.0) < 0.3 &&
                enemyFeatures.getOrDefault("root_score", 0.0) < 0.3) {
                counter += 0.2;
            }
        }
        
        if (!enemyHeroes.isEmpty()) {
            counter /= enemyHeroes.size();  // Normalize by enemy team size
        }
        
        score += 0.4 * counter;
        
        // Factor 3: Team composition needs (weight 0.2)
        double compositionScore = assessTeamCompositionFit(hero, allyHeroes);
        score += 0.2 * compositionScore;
        
        return score;
    }
    
    /**
     * Assess how well a hero fits the team composition needs
     */
    private double assessTeamCompositionFit(Hero hero, List<Hero> allyHeroes) {
        double score = 0.5;  // Base score
        
        // Extract team capabilities
        boolean hasTeamfight = false;
        boolean hasControl = false;
        boolean hasPhysicalDamage = false;
        boolean hasMagicalDamage = false;
        boolean hasInitiation = false;
        
        for (Hero ally : allyHeroes) {
            Map<String, Double> features = nlpModel.getHeroFeatureVector(ally.getId());
            
            if (features.getOrDefault("aoe_impact", 0.0) > 0.6) {
                hasTeamfight = true;
            }
            
            if (features.getOrDefault("stun_score", 0.0) > 0.5 || 
                features.getOrDefault("root_score", 0.0) > 0.5) {
                hasControl = true;
            }
            
            if (features.getOrDefault("physical_damage", 0.0) > 0.7) {
                hasPhysicalDamage = true;
            }
            
            if (features.getOrDefault("magical_damage", 0.0) > 0.7) {
                hasMagicalDamage = true;
            }
            
            if (features.getOrDefault("initiator_score", 0.0) > 0.6) {
                hasInitiation = true;
            }
        }
        
        // Check what the hero brings to the team
        Map<String, Double> heroFeatures = nlpModel.getHeroFeatureVector(hero.getId());
        
        // Fill team needs
        if (!hasTeamfight && heroFeatures.getOrDefault("aoe_impact", 0.0) > 0.6) {
            score += 0.3;
        }
        
        if (!hasControl && (heroFeatures.getOrDefault("stun_score", 0.0) > 0.5 || 
                          heroFeatures.getOrDefault("root_score", 0.0) > 0.5)) {
            score += 0.3;
        }
        
        if (!hasPhysicalDamage && heroFeatures.getOrDefault("physical_damage", 0.0) > 0.7) {
            score += 0.2;
        }
        
        if (!hasMagicalDamage && heroFeatures.getOrDefault("magical_damage", 0.0) > 0.7) {
            score += 0.2;
        }
        
        if (!hasInitiation && heroFeatures.getOrDefault("initiator_score", 0.0) > 0.6) {
            score += 0.2;
        }
        
        return Math.min(1.0, score);  // Cap at 1.0
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
        } else {
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
            // Fall back to local NLP model if Groq API fails
            return nlpModel.generateRecommendationExplanation(hero, allyHeroes, enemyHeroes);
        }
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