package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses natural language processing techniques to analyze hero ability descriptions
 * and extract key features for AI decision making.
 */
@Component
public class NlpAbilityAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(NlpAbilityAnalyzer.class);
    
    // Regular expressions for detecting key features in ability descriptions
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("\\b(\\d+)\\s*(damage|dmg)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AOE_PATTERN = Pattern.compile("\\b(\\d+)\\s*(radius|area|aoe)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*seconds?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("\\bcooldown\\s*:\\s*(\\d+(?:\\.\\d+)?)\\b", Pattern.CASE_INSENSITIVE);
    
    // Control effect patterns
    private static final Pattern STUN_PATTERN = Pattern.compile("\\bstun", Pattern.CASE_INSENSITIVE);
    private static final Pattern SILENCE_PATTERN = Pattern.compile("\\bsilence", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_PATTERN = Pattern.compile("\\broot", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOW_PATTERN = Pattern.compile("\\bslow", Pattern.CASE_INSENSITIVE);
    
    // Damage type patterns
    private static final Pattern MAGICAL_PATTERN = Pattern.compile("\\bmag(ical|ic)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHYSICAL_PATTERN = Pattern.compile("\\bphysical\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PURE_PATTERN = Pattern.compile("\\bpure\\b", Pattern.CASE_INSENSITIVE);
    
    // Role patterns
    private static final Pattern INITIATOR_PATTERN = Pattern.compile("\\b(initiat|jump|blink|charge)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\b(escape|flee|run|move|speed)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_PATTERN = Pattern.compile("\\b(heal|restore|buff|increase|mana|shield|protect)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Analyzes ability descriptions to extract key features for ML processing
     * @param ability The ability to analyze
     * @return A map of extracted features
     */
    public Map<String, Object> extractAbilityFeatures(Ability ability) {
        Map<String, Object> features = new HashMap<>();
        
        if (ability == null || ability.getDescription() == null) {
            return features;
        }
        
        String description = ability.getDescription();
        
        // Basic ability type
        features.put("ability_type", ability.getType());
        features.put("behavior", ability.getBehavior());
        features.put("damage_type", ability.getDamageType());
        features.put("pierces_immunity", ability.isPiercesImmunity());
        
        // Extract numerical features
        extractNumericalFeatures(description, features);
        
        // Extract ability effects
        extractAbilityEffects(description, features);
        
        // Generate word embeddings for machine learning
        features.put("text_embedding", generateTextEmbedding(description));
        
        return features;
    }
    
    /**
     * Analyzes all abilities of a hero and creates a feature vector representation
     * @param hero The hero to analyze
     * @return A feature vector for machine learning models
     */
    public Map<String, Double> createHeroFeatureVector(Hero hero) {
        Map<String, Double> featureVector = new HashMap<>();
        
        // Base stats
        featureVector.put("stun_score", 0.0);
        featureVector.put("silence_score", 0.0);
        featureVector.put("root_score", 0.0);
        featureVector.put("slow_score", 0.0);
        featureVector.put("magical_damage", 0.0);
        featureVector.put("physical_damage", 0.0);
        featureVector.put("pure_damage", 0.0);
        featureVector.put("aoe_impact", 0.0);
        featureVector.put("control_duration", 0.0);
        featureVector.put("mobility_score", 0.0);
        featureVector.put("sustain_score", 0.0);
        featureVector.put("utility_score", 0.0);
        
        // Analyze each ability
        if (hero.getAbilities() != null) {
            for (Ability ability : hero.getAbilities()) {
                Map<String, Object> abilityFeatures = extractAbilityFeatures(ability);
                
                // Increment feature scores based on ability analysis
                updateFeatureVector(featureVector, abilityFeatures, ability);
            }
        }
        
        // Normalize scores to 0-1 range
        normalizeFeatureVector(featureVector);
        
        return featureVector;
    }
    
    /**
     * Extracts numerical values from ability descriptions
     */
    private void extractNumericalFeatures(String description, Map<String, Object> features) {
        // Extract damage values
        Matcher damageMatcher = DAMAGE_PATTERN.matcher(description);
        List<Integer> damageValues = new ArrayList<>();
        while (damageMatcher.find()) {
            try {
                damageValues.add(Integer.parseInt(damageMatcher.group(1)));
            } catch (NumberFormatException e) {
                logger.debug("Error parsing damage value: " + damageMatcher.group(1));
            }
        }
        features.put("extracted_damage_values", damageValues);
        
        // Extract AoE values
        Matcher aoeMatcher = AOE_PATTERN.matcher(description);
        if (aoeMatcher.find()) {
            try {
                features.put("extracted_aoe", Integer.parseInt(aoeMatcher.group(1)));
            } catch (NumberFormatException e) {
                logger.debug("Error parsing AoE value: " + aoeMatcher.group(1));
            }
        }
        
        // Extract duration values
        Matcher durationMatcher = DURATION_PATTERN.matcher(description);
        if (durationMatcher.find()) {
            try {
                features.put("extracted_duration", Double.parseDouble(durationMatcher.group(1)));
            } catch (NumberFormatException e) {
                logger.debug("Error parsing duration value: " + durationMatcher.group(1));
            }
        }
    }
    
    /**
     * Extracts effect information from ability descriptions
     */
    private void extractAbilityEffects(String description, Map<String, Object> features) {
        // Check for control effects
        features.put("has_stun", STUN_PATTERN.matcher(description).find());
        features.put("has_silence", SILENCE_PATTERN.matcher(description).find());
        features.put("has_root", ROOT_PATTERN.matcher(description).find());
        features.put("has_slow", SLOW_PATTERN.matcher(description).find());
        
        // Check for damage types
        features.put("mentions_magical", MAGICAL_PATTERN.matcher(description).find());
        features.put("mentions_physical", PHYSICAL_PATTERN.matcher(description).find());
        features.put("mentions_pure", PURE_PATTERN.matcher(description).find());
        
        // Check for role indicators
        features.put("initiator_score", matchesPatternIntensity(description, INITIATOR_PATTERN));
        features.put("escape_score", matchesPatternIntensity(description, ESCAPE_PATTERN));
        features.put("support_score", matchesPatternIntensity(description, SUPPORT_PATTERN));
        
        // Detect AoE abilities
        boolean isAoe = description.toLowerCase().contains("area") || 
                        description.toLowerCase().contains("radius") ||
                        description.toLowerCase().contains("around") ||
                        description.toLowerCase().contains("nearby");
        features.put("is_aoe", isAoe);
    }
    
    /**
     * Updates a hero's feature vector based on extracted ability features
     */
    private void updateFeatureVector(Map<String, Double> featureVector, Map<String, Object> abilityFeatures, Ability ability) {
        // Update control scores
        if ((Boolean) abilityFeatures.getOrDefault("has_stun", false)) {
            featureVector.put("stun_score", featureVector.get("stun_score") + 1.0);
        }
        
        if ((Boolean) abilityFeatures.getOrDefault("has_silence", false)) {
            featureVector.put("silence_score", featureVector.get("silence_score") + 1.0);
        }
        
        if ((Boolean) abilityFeatures.getOrDefault("has_root", false)) {
            featureVector.put("root_score", featureVector.get("root_score") + 1.0);
        }
        
        if ((Boolean) abilityFeatures.getOrDefault("has_slow", false)) {
            featureVector.put("slow_score", featureVector.get("slow_score") + 0.75);
        }
        
        // Update damage type scores
        if ("magical".equals(ability.getDamageType())) {
            featureVector.put("magical_damage", featureVector.get("magical_damage") + 1.0);
        } else if ("physical".equals(ability.getDamageType())) {
            featureVector.put("physical_damage", featureVector.get("physical_damage") + 1.0);
        } else if ("pure".equals(ability.getDamageType())) {
            featureVector.put("pure_damage", featureVector.get("pure_damage") + 1.5);  // Pure damage weighted higher
        }
        
        // Update AoE score
        if ((Boolean) abilityFeatures.getOrDefault("is_aoe", false)) {
            featureVector.put("aoe_impact", featureVector.get("aoe_impact") + 1.0);
        }
        
        // Update mobility score
        double initiatorScore = (double) abilityFeatures.getOrDefault("initiator_score", 0.0);
        double escapeScore = (double) abilityFeatures.getOrDefault("escape_score", 0.0);
        featureVector.put("mobility_score", featureVector.get("mobility_score") + initiatorScore + escapeScore);
        
        // Update support score
        double supportScore = (double) abilityFeatures.getOrDefault("support_score", 0.0);
        featureVector.put("sustain_score", featureVector.get("sustain_score") + supportScore);
        
        // Update utility based on behavior
        if ("no target".equals(ability.getBehavior()) || "passive".equals(ability.getBehavior())) {
            featureVector.put("utility_score", featureVector.get("utility_score") + 0.5);
        }
        
        // Extra points for ultimate abilities
        if ("ultimate".equals(ability.getType())) {
            // Ultimates have higher impact on scores
            if ((Boolean) abilityFeatures.getOrDefault("is_aoe", false)) {
                featureVector.put("aoe_impact", featureVector.get("aoe_impact") + 1.0);  // Double score for AoE ultimates
            }
            
            if ((Boolean) abilityFeatures.getOrDefault("has_stun", false) || 
                (Boolean) abilityFeatures.getOrDefault("has_silence", false) ||
                (Boolean) abilityFeatures.getOrDefault("has_root", false)) {
                featureVector.put("control_duration", featureVector.get("control_duration") + 1.0);
            }
        }
    }
    
    /**
     * Returns a score based on how strongly a text matches a pattern
     */
    private double matchesPatternIntensity(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int matches = 0;
        while (matcher.find()) {
            matches++;
        }
        
        if (matches == 0) return 0.0;
        if (matches == 1) return 0.5;
        return Math.min(1.0, matches * 0.25);  // Cap at 1.0
    }
    
    /**
     * Normalizes a feature vector to ensure all values are in a reasonable range
     */
    private void normalizeFeatureVector(Map<String, Double> featureVector) {
        // Normalize control scores
        double totalControlScore = featureVector.get("stun_score") + 
                                 featureVector.get("silence_score") + 
                                 featureVector.get("root_score") + 
                                 featureVector.get("slow_score");
                                 
        if (totalControlScore > 0) {
            featureVector.put("stun_score", featureVector.get("stun_score") / Math.max(3.0, totalControlScore));
            featureVector.put("silence_score", featureVector.get("silence_score") / Math.max(3.0, totalControlScore));
            featureVector.put("root_score", featureVector.get("root_score") / Math.max(3.0, totalControlScore));
            featureVector.put("slow_score", featureVector.get("slow_score") / Math.max(3.0, totalControlScore));
        }
        
        // Normalize damage scores
        double totalDamageScore = featureVector.get("magical_damage") + 
                               featureVector.get("physical_damage") + 
                               featureVector.get("pure_damage");
                               
        if (totalDamageScore > 0) {
            featureVector.put("magical_damage", featureVector.get("magical_damage") / Math.max(3.0, totalDamageScore));
            featureVector.put("physical_damage", featureVector.get("physical_damage") / Math.max(3.0, totalDamageScore));
            featureVector.put("pure_damage", featureVector.get("pure_damage") / Math.max(3.0, totalDamageScore));
        }
        
        // Cap scores at 1.0
        for (String key : featureVector.keySet()) {
            featureVector.put(key, Math.min(1.0, featureVector.get(key)));
        }
    }
    
    /**
     * Generates simple text embeddings for ability descriptions
     * 
     * Note: In a real implementation, this would use a proper NLP library
     * or ML model to generate semantic embeddings.
     */
    private double[] generateTextEmbedding(String text) {
        // Very simple embedding using word frequencies
        // In a real implementation, this would use pre-trained embeddings or language models
        
        double[] embedding = new double[10];  // Using a tiny embedding for demo
        
        // These are just placeholder calculations
        if (text.toLowerCase().contains("stun")) embedding[0] = 1.0;
        if (text.toLowerCase().contains("damage")) embedding[1] = 1.0;
        if (text.toLowerCase().contains("heal")) embedding[2] = 1.0;
        if (text.toLowerCase().contains("slow")) embedding[3] = 1.0;
        if (text.toLowerCase().contains("area")) embedding[4] = 1.0;
        if (text.toLowerCase().contains("silence")) embedding[5] = 1.0;
        if (text.toLowerCase().contains("buff")) embedding[6] = 1.0;
        if (text.toLowerCase().contains("increase")) embedding[7] = 1.0;
        if (text.toLowerCase().contains("duration")) embedding[8] = 1.0;
        if (text.toLowerCase().contains("cooldown")) embedding[9] = 1.0;
        
        return embedding;
    }
    
    /**
     * Computes the similarity between two heroes based on their ability profiles
     * 
     * @param hero1 First hero
     * @param hero2 Second hero
     * @return Similarity score between 0-1
     */
    public double computeHeroSimilarity(Hero hero1, Hero hero2) {
        Map<String, Double> vector1 = createHeroFeatureVector(hero1);
        Map<String, Double> vector2 = createHeroFeatureVector(hero2);
        
        // Compute cosine similarity between feature vectors
        return calculateCosineSimilarity(vector1, vector2);
    }
    
    /**
     * Calculate cosine similarity between two feature vectors
     */
    private double calculateCosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String key : vector1.keySet()) {
            double v1 = vector1.getOrDefault(key, 0.0);
            double v2 = vector2.getOrDefault(key, 0.0);
            
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        
        // Handle potential division by zero
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Identifies potential synergies between two heroes based on their abilities
     * 
     * @param hero1 First hero
     * @param hero2 Second hero
     * @return A list of potential synergies with descriptions
     */
    public List<AbilitySynergy> findPotentialSynergies(Hero hero1, Hero hero2) {
        List<AbilitySynergy> synergies = new ArrayList<>();
        
        if (hero1 == null || hero2 == null || 
            hero1.getAbilities() == null || hero2.getAbilities() == null) {
            return synergies;
        }
        
        // Check for specific ability combinations that have good synergy
        for (Ability ability1 : hero1.getAbilities()) {
            for (Ability ability2 : hero2.getAbilities()) {
                // Control + Damage synergy
                if (isControlAbility(ability1) && isDamageAbility(ability2)) {
                    synergies.add(new AbilitySynergy(
                        ability1,
                        ability2,
                        "Control + Damage",
                        String.format("%s's %s sets up %s's %s for effective damage",
                            hero1.getLocalizedName(), ability1.getName(),
                            hero2.getLocalizedName(), ability2.getName()),
                        0.8
                    ));
                }
                
                // AoE Abilities synergy
                if (isAoeAbility(ability1) && isAoeAbility(ability2)) {
                    synergies.add(new AbilitySynergy(
                        ability1,
                        ability2,
                        "Team Fight",
                        String.format("%s's %s and %s's %s provide strong team fight capability",
                            hero1.getLocalizedName(), ability1.getName(),
                            hero2.getLocalizedName(), ability2.getName()),
                        0.7
                    ));
                }
                
                // Check more specific synergies based on ability names or descriptions
                checkSpecificAbilitySynergies(ability1, ability2, hero1, hero2, synergies);
            }
        }
        
        return synergies;
    }
    
    private boolean isControlAbility(Ability ability) {
        if (ability == null || ability.getDescription() == null) {
            return false;
        }
        
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("stun") || desc.contains("root") || desc.contains("slow") || 
               desc.contains("hex") || desc.contains("silence") || desc.contains("disarm");
    }
    
    private boolean isDamageAbility(Ability ability) {
        if (ability == null || ability.getDescription() == null) {
            return false;
        }
        
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("damage") || desc.contains("burst") || desc.contains("nuke") || 
               "magical".equals(ability.getDamageType()) || "physical".equals(ability.getDamageType()) ||
               "pure".equals(ability.getDamageType());
    }
    
    private boolean isAoeAbility(Ability ability) {
        if (ability == null || ability.getDescription() == null) {
            return false;
        }
        
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("area") || desc.contains("radius") || desc.contains("aoe") ||
               desc.contains("nearby") || desc.contains("around") || desc.contains("surrounding");
    }
    
    private void checkSpecificAbilitySynergies(Ability ability1, Ability ability2, Hero hero1, Hero hero2, List<AbilitySynergy> synergies) {
        // This would contain very specific ability combinations that are known to work well together
        // Example: Faceless Void's Chronosphere + Skywrath Mage's Mystic Flare
        
        String name1 = ability1.getName().toLowerCase();
        String name2 = ability2.getName().toLowerCase();
        
        // Example of specific synergy checks
        if (name1.contains("chronosphere") && isDamageAbility(ability2)) {
            synergies.add(new AbilitySynergy(
                ability1, 
                ability2,
                "Chrono Combo",
                String.format("%s's Chronosphere perfectly sets up %s's %s for maximum damage",
                    hero1.getLocalizedName(), hero2.getLocalizedName(), ability2.getName()),
                0.9
            ));
        }
        
        if (name1.contains("black hole") && isDamageAbility(ability2)) {
            synergies.add(new AbilitySynergy(
                ability1, 
                ability2,
                "Black Hole Combo",
                String.format("%s's Black Hole perfectly sets up %s's %s for maximum damage",
                    hero1.getLocalizedName(), hero2.getLocalizedName(), ability2.getName()),
                0.9
            ));
        }
        
        if (name1.contains("reverse polarity") && isDamageAbility(ability2)) {
            synergies.add(new AbilitySynergy(
                ability1, 
                ability2,
                "RP Combo",
                String.format("%s's Reverse Polarity perfectly sets up %s's %s for maximum damage",
                    hero1.getLocalizedName(), hero2.getLocalizedName(), ability2.getName()),
                0.85
            ));
        }
    }
    
    /**
     * Represents a detected synergy between two abilities
     */
    public static class AbilitySynergy {
        private final Ability ability1;
        private final Ability ability2;
        private final String type;
        private final String description;
        private final double score;
        
        public AbilitySynergy(Ability ability1, Ability ability2, String type, String description, double score) {
            this.ability1 = ability1;
            this.ability2 = ability2;
            this.type = type;
            this.description = description;
            this.score = score;
        }
        
        public Ability getAbility1() {
            return ability1;
        }
        
        public Ability getAbility2() {
            return ability2;
        }
        
        public String getType() {
            return type;
        }
        
        public String getDescription() {
            return description;
        }
        
        public double getScore() {
            return score;
        }
    }
}