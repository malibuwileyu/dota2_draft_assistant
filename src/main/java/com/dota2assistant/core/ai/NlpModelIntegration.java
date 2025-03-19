package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for integrating NLP capabilities with the Dota 2 draft assistant.
 * This serves as an abstract layer that could integrate with various NLP services,
 * including local models or cloud-based APIs like Groq LPU.
 */
@Service
public class NlpModelIntegration {

    private static final Logger logger = LoggerFactory.getLogger(NlpModelIntegration.class);
    
    private final HeroRepository heroRepository;
    private final NlpAbilityAnalyzer abilityAnalyzer;
    
    // Cache for hero feature vectors
    private final Map<Integer, Map<String, Double>> heroFeatureVectorCache = new ConcurrentHashMap<>();
    
    // Cache for hero similarity scores (hero1_id + "_" + hero2_id -> score)
    private final Map<String, Double> heroSimilarityCache = new ConcurrentHashMap<>();
    
    // Cache for hero synergies
    private final Map<String, List<NlpAbilityAnalyzer.AbilitySynergy>> heroSynergiesCache = new ConcurrentHashMap<>();

    @Autowired
    public NlpModelIntegration(HeroRepository heroRepository, NlpAbilityAnalyzer abilityAnalyzer) {
        this.heroRepository = heroRepository;
        this.abilityAnalyzer = abilityAnalyzer;
    }
    
    @PostConstruct
    public void initialize() {
        // Pre-compute feature vectors for all heroes at startup to improve runtime performance
        logger.info("Initializing NLP model and precomputing hero feature vectors");
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        for (Hero hero : allHeroes) {
            try {
                Map<String, Double> featureVector = abilityAnalyzer.createHeroFeatureVector(hero);
                heroFeatureVectorCache.put(hero.getId(), featureVector);
                logger.debug("Precomputed feature vector for hero: {}", hero.getLocalizedName());
            } catch (Exception e) {
                logger.error("Error computing feature vector for hero {}: {}", 
                    hero.getLocalizedName(), e.getMessage());
            }
        }
        logger.info("Precomputed feature vectors for {} heroes", heroFeatureVectorCache.size());
    }

    /**
     * Analyze an ability using NLP techniques
     * 
     * @param ability The ability to analyze
     * @return A map of extracted features
     */
    public Map<String, Object> analyzeAbility(Ability ability) {
        return abilityAnalyzer.extractAbilityFeatures(ability);
    }

    /**
     * Get the feature vector for a hero
     * 
     * @param heroId The ID of the hero
     * @return The feature vector map
     */
    public Map<String, Double> getHeroFeatureVector(int heroId) {
        // Return cached vector if available
        if (heroFeatureVectorCache.containsKey(heroId)) {
            return heroFeatureVectorCache.get(heroId);
        }
        
        // Compute vector if not in cache
        Hero hero = heroRepository.getHeroById(heroId);
        if (hero != null) {
            Map<String, Double> vector = abilityAnalyzer.createHeroFeatureVector(hero);
            heroFeatureVectorCache.put(heroId, vector);
            return vector;
        }
        
        return Collections.emptyMap();
    }

    /**
     * Calculate similarity between two heroes
     * 
     * @param hero1Id First hero ID
     * @param hero2Id Second hero ID
     * @return Similarity score between 0-1
     */
    public double getHeroSimilarity(int hero1Id, int hero2Id) {
        // Create a consistent key for the cache
        String cacheKey = Math.min(hero1Id, hero2Id) + "_" + Math.max(hero1Id, hero2Id);
        
        // Return cached similarity if available
        if (heroSimilarityCache.containsKey(cacheKey)) {
            return heroSimilarityCache.get(cacheKey);
        }
        
        // Compute similarity
        Hero hero1 = heroRepository.getHeroById(hero1Id);
        Hero hero2 = heroRepository.getHeroById(hero2Id);
        
        if (hero1 == null || hero2 == null) {
            return 0.0;
        }
        
        double similarity = abilityAnalyzer.computeHeroSimilarity(hero1, hero2);
        heroSimilarityCache.put(cacheKey, similarity);
        
        return similarity;
    }

    /**
     * Find heroes with similar ability profiles to the given hero
     * 
     * @param heroId Hero to find similar heroes for
     * @param count Number of similar heroes to return
     * @return List of similar heroes with similarity scores
     */
    public List<HeroSimilarity> findSimilarHeroes(int heroId, int count) {
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        List<HeroSimilarity> similarities = new ArrayList<>();
        
        for (Hero hero : allHeroes) {
            if (hero.getId() == heroId) {
                continue;  // Skip the target hero
            }
            
            double similarity = getHeroSimilarity(heroId, hero.getId());
            similarities.add(new HeroSimilarity(hero, similarity));
        }
        
        // Sort and limit to requested count
        return similarities.stream()
            .sorted(Comparator.comparing(HeroSimilarity::getSimilarity).reversed())
            .limit(count)
            .collect(Collectors.toList());
    }

    /**
     * Find synergy between heroes based on their abilities
     * 
     * @param hero1Id First hero ID
     * @param hero2Id Second hero ID
     * @return List of ability synergies
     */
    public List<NlpAbilityAnalyzer.AbilitySynergy> findAbilitySynergies(int hero1Id, int hero2Id) {
        // Create a consistent key for the cache
        String cacheKey = Math.min(hero1Id, hero2Id) + "_" + Math.max(hero1Id, hero2Id);
        
        // Return cached synergies if available
        if (heroSynergiesCache.containsKey(cacheKey)) {
            return heroSynergiesCache.get(cacheKey);
        }
        
        // Compute synergies
        Hero hero1 = heroRepository.getHeroById(hero1Id);
        Hero hero2 = heroRepository.getHeroById(hero2Id);
        
        if (hero1 == null || hero2 == null) {
            return Collections.emptyList();
        }
        
        List<NlpAbilityAnalyzer.AbilitySynergy> synergies = abilityAnalyzer.findPotentialSynergies(hero1, hero2);
        heroSynergiesCache.put(cacheKey, synergies);
        
        return synergies;
    }
    
    /**
     * Generate a natural language explanation for a draft recommendation
     * 
     * @param recommendedHero The recommended hero
     * @param allyHeroes Current ally heroes
     * @param enemyHeroes Current enemy heroes
     * @return A multi-paragraph explanation for the recommendation
     */
    public String generateRecommendationExplanation(Hero recommendedHero, List<Hero> allyHeroes, List<Hero> enemyHeroes) {
        StringBuilder explanation = new StringBuilder();
        
        // This is a simplified implementation - a real NLP integration would use a more sophisticated
        // language model like Groq LPU to generate natural language explanations
        
        // Explain hero strengths
        explanation.append(String.format("**%s** is recommended because of their strong %s capabilities. ",
            recommendedHero.getLocalizedName(),
            getHeroStrengths(recommendedHero)));
            
        // Explain synergies with team
        List<String> synergyReasons = new ArrayList<>();
        for (Hero ally : allyHeroes) {
            List<NlpAbilityAnalyzer.AbilitySynergy> synergies = findAbilitySynergies(recommendedHero.getId(), ally.getId());
            if (!synergies.isEmpty()) {
                // Get the highest scoring synergy
                NlpAbilityAnalyzer.AbilitySynergy topSynergy = synergies.stream()
                    .max(Comparator.comparing(NlpAbilityAnalyzer.AbilitySynergy::getScore))
                    .orElse(null);
                    
                if (topSynergy != null) {
                    synergyReasons.add(topSynergy.getDescription());
                }
            }
        }
        
        if (!synergyReasons.isEmpty()) {
            explanation.append("\n\n**Team synergies:**\n");
            for (String reason : synergyReasons) {
                explanation.append("- ").append(reason).append("\n");
            }
        }
        
        // Explain counters against enemies
        if (!enemyHeroes.isEmpty()) {
            explanation.append("\n\n**Effective against:**\n");
            for (Hero enemy : enemyHeroes) {
                String counterReason = generateCounterReason(recommendedHero, enemy);
                if (counterReason != null) {
                    explanation.append("- ").append(counterReason).append("\n");
                }
            }
        }
        
        return explanation.toString();
    }
    
    /**
     * Get the key strengths of a hero based on their ability profile
     */
    private String getHeroStrengths(Hero hero) {
        Map<String, Double> featureVector = getHeroFeatureVector(hero.getId());
        
        List<String> strengths = new ArrayList<>();
        
        if (featureVector.getOrDefault("stun_score", 0.0) > 0.5 || 
            featureVector.getOrDefault("root_score", 0.0) > 0.5 ||
            featureVector.getOrDefault("silence_score", 0.0) > 0.5) {
            strengths.add("disable");
        }
        
        if (featureVector.getOrDefault("aoe_impact", 0.0) > 0.5) {
            strengths.add("teamfight");
        }
        
        if (featureVector.getOrDefault("magical_damage", 0.0) > 0.7) {
            strengths.add("magical damage");
        }
        
        if (featureVector.getOrDefault("physical_damage", 0.0) > 0.7) {
            strengths.add("physical damage");
        }
        
        if (featureVector.getOrDefault("mobility_score", 0.0) > 0.6) {
            strengths.add("mobility");
        }
        
        if (featureVector.getOrDefault("sustain_score", 0.0) > 0.6) {
            strengths.add("sustain");
        }
        
        if (strengths.isEmpty()) {
            strengths.add("versatility");
        }
        
        return String.join(" and ", strengths);
    }
    
    /**
     * Generate a reason why hero1 counters hero2
     */
    private String generateCounterReason(Hero hero1, Hero hero2) {
        // This would be more sophisticated in a real NLP implementation
        
        // For now, use basic heuristics
        Map<String, Double> vector1 = getHeroFeatureVector(hero1.getId());
        Map<String, Double> vector2 = getHeroFeatureVector(hero2.getId());
        
        // Check if hero1 has control abilities against mobility heroes
        if (vector1.getOrDefault("stun_score", 0.0) > 0.5 && vector2.getOrDefault("mobility_score", 0.0) > 0.7) {
            return String.format("%s's control abilities lock down %s's mobility", hero1.getLocalizedName(), hero2.getLocalizedName());
        }
        
        // Check if hero1 has silence against spell-reliant heroes
        if (vector1.getOrDefault("silence_score", 0.0) > 0.5 && vector2.getOrDefault("magical_damage", 0.0) > 0.7) {
            return String.format("%s's silence is effective against %s's spell-reliant kit", hero1.getLocalizedName(), hero2.getLocalizedName());
        }
        
        // Check if hero1 has mobility against low mobility heroes
        if (vector1.getOrDefault("mobility_score", 0.0) > 0.7 && vector2.getOrDefault("mobility_score", 0.0) < 0.3) {
            return String.format("%s's mobility allows outplaying %s's limited movement", hero1.getLocalizedName(), hero2.getLocalizedName());
        }
        
        // Check if physical vs magical damage is advantageous
        if (vector1.getOrDefault("physical_damage", 0.0) > 0.7 && vector2.getOrDefault("magical_damage", 0.0) > 0.7) {
            return String.format("%s's physical damage is effective against %s's typically lower armor", hero1.getLocalizedName(), hero2.getLocalizedName());
        }
        
        return null;
    }

    /**
     * Class to hold hero similarity information
     */
    public static class HeroSimilarity {
        private final Hero hero;
        private final double similarity;
        
        public HeroSimilarity(Hero hero, double similarity) {
            this.hero = hero;
            this.similarity = similarity;
        }
        
        public Hero getHero() {
            return hero;
        }
        
        public double getSimilarity() {
            return similarity;
        }
    }
}