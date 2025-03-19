package com.dota2assistant.core.ai;

import com.dota2assistant.core.analysis.AbilityClassifier;
import com.dota2assistant.core.analysis.AbilityInteractionAnalyzer;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI implementation that uses machine learning and ability analysis to make draft decisions.
 * This engine combines statistical analysis from match data with deep ability interaction analysis
 * to provide intelligent hero recommendations.
 */
@Service
public class MlBasedAiDecisionEngine implements AiDecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(MlBasedAiDecisionEngine.class);
    
    private final HeroRepository heroRepository;
    private final MatchRepository matchRepository;
    private final AbilityClassifier abilityClassifier;
    private final AbilityInteractionAnalyzer abilityInteractionAnalyzer;
    private final NlpModelIntegration nlpModel;
    private final MlTrainingService mlTrainingService;
    private final Random random = new Random();
    
    // AI difficulty level from 0.0 (random) to 1.0 (optimal)
    private double difficultyLevel = 0.9;
    
    // Weights for different factors in decision making
    private static final double WEIGHT_WIN_RATE = 0.15;
    private static final double WEIGHT_PICK_RATE = 0.10;
    private static final double WEIGHT_SYNERGY = 0.15;
    private static final double WEIGHT_COUNTER = 0.15;
    private static final double WEIGHT_ABILITY_SYNERGY = 0.20;
    private static final double WEIGHT_POSITION_DIVERSITY = 0.15;
    private static final double WEIGHT_META_POWER = 0.10;
    
    /**
     * Constructor for the MlBasedAiDecisionEngine
     * 
     * @param heroRepository The repository for hero data
     * @param matchRepository The repository for match data
     * @param abilityClassifier The classifier for hero abilities
     * @param abilityInteractionAnalyzer The analyzer for ability interactions
     * @param nlpModel NLP model integration
     * @param mlTrainingService ML training service
     */
    public MlBasedAiDecisionEngine(HeroRepository heroRepository, 
                                  MatchRepository matchRepository,
                                  AbilityClassifier abilityClassifier,
                                  AbilityInteractionAnalyzer abilityInteractionAnalyzer,
                                  NlpModelIntegration nlpModel,
                                  MlTrainingService mlTrainingService) {
        this.heroRepository = heroRepository;
        this.matchRepository = matchRepository;
        this.abilityClassifier = abilityClassifier;
        this.abilityInteractionAnalyzer = abilityInteractionAnalyzer;
        this.nlpModel = nlpModel;
        this.mlTrainingService = mlTrainingService;
        logger.info("Initialized ML-Based AI Decision Engine");
    }
    
    /**
     * Sets the difficulty level of the AI
     * 
     * @param difficultyLevel A value between 0.0 (random) and 1.0 (optimal)
     */
    @Override
    public void setDifficultyLevel(double difficultyLevel) {
        if (difficultyLevel < 0.0 || difficultyLevel > 1.0) {
            throw new IllegalArgumentException("Difficulty level must be between 0.0 and 1.0");
        }
        this.difficultyLevel = difficultyLevel;
    }

    @Override
    public Hero suggestPick(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        // If difficultyLevel is very low, make a random pick
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // Create a map of hero scores for picking
        Map<Hero, HeroScore> heroScores = new HashMap<>();
        
        for (Hero hero : available) {
            HeroScore score = calculateHeroScore(hero, direPicks, radiantPicks, true);
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score
        List<Hero> sortedHeroes = heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, HeroScore>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(3, sortedHeroes.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel * 0.8));
        
        Hero chosen = sortedHeroes.get(chosenIndex);
        logger.info("ML-based AI suggests picking {} (total score: {})", chosen.getLocalizedName(), 
                heroScores.get(chosen).getTotalScore());
                
        return chosen;
    }

    @Override
    public List<Hero> suggestPicks(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create a map of hero scores for picking
        Map<Hero, HeroScore> heroScores = new HashMap<>();
        
        for (Hero hero : available) {
            HeroScore score = calculateHeroScore(hero, direPicks, radiantPicks, true);
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score and return top N
        return heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, HeroScore>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public Hero suggestBan(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        // If difficultyLevel is very low, make a random ban
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // Create a map of hero scores for banning
        Map<Hero, HeroScore> heroScores = new HashMap<>();
        
        for (Hero hero : available) {
            HeroScore score = calculateHeroScore(hero, radiantPicks, direPicks, false);
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score
        List<Hero> sortedHeroes = heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, HeroScore>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(3, sortedHeroes.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel * 0.8));
        
        Hero chosen = sortedHeroes.get(chosenIndex);
        logger.info("ML-based AI suggests banning {} (total score: {})", chosen.getLocalizedName(), 
                heroScores.get(chosen).getTotalScore());
                
        return chosen;
    }

    @Override
    public List<Hero> suggestBans(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create a map of hero scores for banning
        Map<Hero, HeroScore> heroScores = new HashMap<>();
        
        for (Hero hero : available) {
            HeroScore score = calculateHeroScore(hero, radiantPicks, direPicks, false);
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score and return top N
        return heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, HeroScore>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, String> getHeroRecommendationReasoning(Hero hero, List<Hero> radiantPicks, List<Hero> direPicks) {
        Map<String, String> reasoning = new HashMap<>();
        
        // Calculate the score to get the reasoning
        HeroScore score = calculateHeroScore(hero, direPicks, radiantPicks, true);
        
        // Add win rate and pick rate reasoning
        reasoning.put("Statistics", String.format(
            "Win Rate: %.1f%%, Pick Rate: %.1f%% in recent matches.", 
            score.getWinRateScore() * 100, 
            score.getPickRateScore() * 100));
        
        // Add ability reasoning
        reasoning.put("Abilities", String.join(" ", score.getAbilityReasons()));
        
        // Add synergy reasoning
        reasoning.put("Team Synergy", String.join(" ", score.getSynergyReasons()));
        
        // Add counter reasoning
        reasoning.put("Enemy Counters", String.join(" ", score.getCounterReasons()));
        
        // Add position diversity reasoning
        if (!score.getPositionReasons().isEmpty()) {
            reasoning.put("Position Balance", String.join(" ", score.getPositionReasons()));
        }
        
        // Add meta power reasoning
        if (!score.getMetaReasons().isEmpty()) {
            reasoning.put("Meta Analysis", String.join(" ", score.getMetaReasons()));
        }
        
        // Add NLP-based explanation
        reasoning.put("ML Analysis", nlpModel.generateRecommendationExplanation(
            hero, direPicks, radiantPicks));
        
        return reasoning;
    }
    
    /**
     * Calculate a comprehensive score for a hero based on multiple factors
     * 
     * @param hero The hero to evaluate
     * @param allyHeroes Heroes on the same team
     * @param enemyHeroes Heroes on the enemy team
     * @param isPick True if calculating for a pick, false for a ban
     * @return A HeroScore object containing the total score and component scores
     */
    private HeroScore calculateHeroScore(Hero hero, List<Hero> allyHeroes, List<Hero> enemyHeroes, boolean isPick) {
        HeroScore heroScore = new HeroScore();
        
        // Get statistical data - now using ML training service
        Map<Integer, Double> winRates = mlTrainingService.getHeroWinRates().getOrDefault("all", Collections.emptyMap());
        
        // Factor 1: Win rate
        double winRate = winRates.getOrDefault(hero.getId(), 0.5);
        heroScore.setWinRateScore(winRate);
        
        // Factor 2: Pick rate (popularity) - still using match repo
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank("all");
        double pickRate = pickRates.getOrDefault(hero.getId(), 0.1);
        heroScore.setPickRateScore(pickRate);
        
        // Factor 3: Ability profile analysis using NLP model instead of just classifier
        Map<String, Double> heroFeatures = nlpModel.getHeroFeatureVector(hero.getId());
        
        // Calculate ability score based on team needs
        double abilityScore = 0.0;
        List<String> abilityReasons = new ArrayList<>();
        
        // Check for teamfight needs using NLP model
        boolean teamHasTeamfight = allyHeroes.stream()
            .anyMatch(ally -> nlpModel.getHeroFeatureVector(ally.getId())
                .getOrDefault("aoe_impact", 0.0) > 0.6);
            
        if (!teamHasTeamfight && heroFeatures.getOrDefault("aoe_impact", 0.0) > 0.6) {
            abilityScore += 0.3;
            abilityReasons.add(String.format(
                "%s provides needed teamfight capability.",
                hero.getLocalizedName()
            ));
        }
        
        // Check for control needs
        boolean teamHasControl = allyHeroes.stream()
            .anyMatch(ally -> nlpModel.getHeroFeatureVector(ally.getId())
                .getOrDefault("stun_score", 0.0) > 0.5 || 
                nlpModel.getHeroFeatureVector(ally.getId())
                .getOrDefault("root_score", 0.0) > 0.5);
            
        if (!teamHasControl && (heroFeatures.getOrDefault("stun_score", 0.0) > 0.5 || 
                             heroFeatures.getOrDefault("root_score", 0.0) > 0.5)) {
            abilityScore += 0.3;
            abilityReasons.add(String.format(
                "%s provides needed control abilities.",
                hero.getLocalizedName()
            ));
        }
        
        // Check damage type balance
        boolean teamHasMagical = allyHeroes.stream()
            .anyMatch(ally -> nlpModel.getHeroFeatureVector(ally.getId())
                .getOrDefault("magical_damage", 0.0) > 0.6);
                
        boolean teamHasPhysical = allyHeroes.stream()
            .anyMatch(ally -> nlpModel.getHeroFeatureVector(ally.getId())
                .getOrDefault("physical_damage", 0.0) > 0.6);
                
        if (!teamHasMagical && heroFeatures.getOrDefault("magical_damage", 0.0) > 0.6) {
            abilityScore += 0.2;
            abilityReasons.add(String.format(
                "%s provides needed magical damage.",
                hero.getLocalizedName()
            ));
        }
        
        if (!teamHasPhysical && heroFeatures.getOrDefault("physical_damage", 0.0) > 0.6) {
            abilityScore += 0.2;
            abilityReasons.add(String.format(
                "%s provides needed physical damage.",
                hero.getLocalizedName()
            ));
        }
        
        heroScore.setAbilityScore(abilityScore);
        heroScore.setAbilityReasons(abilityReasons);
        
        // Factor 4: Ability interactions with ally heroes - using ML-trained synergies
        double synergyScore = 0.0;
        List<String> synergyReasons = new ArrayList<>();
        
        for (Hero ally : allyHeroes) {
            // Get synergy score from ML training service
            String synergyKey = Math.min(hero.getId(), ally.getId()) + "_" + 
                              Math.max(hero.getId(), ally.getId());
            double synergy = mlTrainingService.getHeroSynergies().getOrDefault(synergyKey, 0.5);
            
            synergyScore += synergy / Math.max(1, allyHeroes.size());
            
            if (synergy > 0.7) {
                // Find the specific synergies using NLP model
                List<NlpAbilityAnalyzer.AbilitySynergy> abilitySynergies = 
                    nlpModel.findAbilitySynergies(hero.getId(), ally.getId());
                
                if (!abilitySynergies.isEmpty()) {
                    NlpAbilityAnalyzer.AbilitySynergy topSynergy = abilitySynergies.stream()
                        .max(Comparator.comparing(NlpAbilityAnalyzer.AbilitySynergy::getScore))
                        .orElse(null);
                        
                    if (topSynergy != null) {
                        synergyReasons.add(topSynergy.getDescription());
                    } else {
                        synergyReasons.add(String.format(
                            "Strong synergy between %s and %s",
                            hero.getLocalizedName(), ally.getLocalizedName()
                        ));
                    }
                }
            }
        }
        
        heroScore.setSynergyScore(synergyScore);
        heroScore.setSynergyReasons(synergyReasons);
        
        // Factor 5: Counters - using ML-trained counters
        double counterScore = 0.0;
        List<String> counterReasons = new ArrayList<>();
        
        for (Hero enemy : enemyHeroes) {
            if (isPick) {
                // When picking, evaluate if our hero counters enemies
                String counterKey = hero.getId() + "_" + enemy.getId();
                double counter = mlTrainingService.getHeroCounters().getOrDefault(counterKey, 0.5);
                
                counterScore += counter / Math.max(1, enemyHeroes.size());
                
                if (counter > 0.7) {
                    String reason = generateCounterReason(hero, enemy);
                    if (reason != null) {
                        counterReasons.add(reason);
                    } else {
                        counterReasons.add(String.format(
                            "Effective against %s", enemy.getLocalizedName()
                        ));
                    }
                }
            } else {
                // When banning, evaluate if enemy hero counters our team
                for (Hero ally : allyHeroes) {
                    String counterKey = enemy.getId() + "_" + ally.getId();
                    double counter = mlTrainingService.getHeroCounters().getOrDefault(counterKey, 0.5);
                    
                    counterScore += counter / (Math.max(1, allyHeroes.size()) * Math.max(1, enemyHeroes.size()));
                    
                    if (counter > 0.7) {
                        counterReasons.add(String.format(
                            "%s strongly counters our %s",
                            enemy.getLocalizedName(),
                            ally.getLocalizedName()
                        ));
                    }
                }
            }
        }
        
        heroScore.setCounterScore(counterScore);
        heroScore.setCounterReasons(counterReasons);
        
        // Factor 6: Position diversity - analyzing team position balance
        double positionDiversityScore = calculatePositionDiversityScore(hero, allyHeroes);
        List<String> positionReasons = generatePositionReasons(hero, allyHeroes);
        
        heroScore.setPositionDiversityScore(positionDiversityScore);
        heroScore.setPositionReasons(positionReasons);
        
        // Factor 7: Meta power score - evaluating hero's current meta strength for early picks
        double metaPowerScore = calculateMetaPowerScore(hero, allyHeroes);
        List<String> metaReasons = generateMetaReasons(hero);
        
        heroScore.setMetaPowerScore(metaPowerScore);
        heroScore.setMetaReasons(metaReasons);
        
        // Calculate total weighted score
        double totalScore = WEIGHT_WIN_RATE * winRate +
                           WEIGHT_PICK_RATE * pickRate +
                           WEIGHT_ABILITY_SYNERGY * abilityScore +
                           WEIGHT_SYNERGY * synergyScore +
                           WEIGHT_COUNTER * counterScore +
                           WEIGHT_POSITION_DIVERSITY * positionDiversityScore +
                           WEIGHT_META_POWER * metaPowerScore;
                           
        // Add some randomness based on difficulty
        totalScore += (1.0 - difficultyLevel) * random.nextDouble() * 0.1;
        
        heroScore.setTotalScore(totalScore);
        
        return heroScore;
    }
    
    /**
     * Generate a reason why hero1 counters hero2 using NLP analysis
     */
    private String generateCounterReason(Hero hero1, Hero hero2) {
        // Use NLP model to get feature vectors
        Map<String, Double> vector1 = nlpModel.getHeroFeatureVector(hero1.getId());
        Map<String, Double> vector2 = nlpModel.getHeroFeatureVector(hero2.getId());
        
        // Check if hero1 has control against mobility heroes
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
        
        return null;
    }
    
    /**
     * Calculate a score based on how well this hero complements the team's position distribution.
     * Higher score means better position diversity contribution.
     * 
     * @param hero The hero to evaluate
     * @param teamHeroes Current heroes on the team
     * @return A score between 0.0 and 1.0 representing position diversity contribution
     */
    private double calculatePositionDiversityScore(Hero hero, List<Hero> teamHeroes) {
        // If team is empty, any position is good
        if (teamHeroes.isEmpty()) {
            return 1.0;
        }
        
        // Get current position coverage in the team
        Map<Integer, Double> teamPositionCoverage = analyzeTeamPositions(teamHeroes);
        
        // Get the hero's position frequencies
        Map<Integer, Double> heroPositions = hero.getRoleFrequency();
        
        // With our fallback in place, this should never be null or empty,
        // but we'll keep the safety check just in case
        if (heroPositions == null || heroPositions.isEmpty()) {
            return 0.5;
        }
        
        // Apply a slight penalty if using default positions rather than real match data
        double dataQualityFactor = 1.0;
        if (hero.hasDefaultRoleFrequency()) {
            // 15% penalty for using default data instead of match-based data
            dataQualityFactor = 0.85;
        }
        
        double score = 0.0;
        double totalFrequency = 0.0;
        
        // For each possible position (1-5)
        for (int position = 1; position <= 5; position++) {
            // Get how often this hero plays this position
            double positionFrequency = heroPositions.getOrDefault(position, 0.0);
            // Skip positions this hero doesn't play
            if (positionFrequency <= 0.0) {
                continue;
            }
            
            // How much this position is already covered by the team
            double positionCoverage = teamPositionCoverage.getOrDefault(position, 0.0);
            
            // Calculate score contribution for this position:
            // - Higher if position not yet covered (1.0 - positionCoverage is high)
            // - Weighted by how often hero plays this position
            double positionScore = positionFrequency * (1.0 - positionCoverage);
            score += positionScore;
            totalFrequency += positionFrequency;
        }
        
        // Normalize score based on total frequency
        if (totalFrequency > 0) {
            score = score / totalFrequency;
        }
        
        // Apply data quality factor for heroes using default position data
        if (hero.hasDefaultRoleFrequency()) {
            // 15% penalty for using default data instead of match-based data
            score *= 0.85;
        }
        
        // Calculate flexibility score
        int positionsPlayed = (int) heroPositions.values().stream().filter(freq -> freq >= 0.2).count();
        double flexibilityScore = 0.0;
        
        // Draft phase awareness: early draft picks should favor flexible heroes
        // Simple heuristic: assess how early we are in the draft based on team size
        boolean earlyDraft = teamHeroes.size() <= 2; // First 2 picks
        boolean midDraft = teamHeroes.size() <= 3;   // First 3 picks
        
        // Boost score for flexible heroes more significantly in early draft
        if (positionsPlayed > 1) {
            if (earlyDraft) {
                flexibilityScore = 0.2 * (positionsPlayed - 1); // Stronger bonus for early picks
            } else if (midDraft) {
                flexibilityScore = 0.15 * (positionsPlayed - 1); // Medium bonus for mid-draft
            } else {
                flexibilityScore = 0.05 * (positionsPlayed - 1); // Smaller bonus for late picks
            }
            
            // Apply data quality factor to flexibility score too if using default data
            if (hero.hasDefaultRoleFrequency()) {
                flexibilityScore *= 0.85;
            }
        }
        
        score += flexibilityScore;
        
        // Cap at 1.0
        return Math.min(1.0, score);
    }
    
    /**
     * Analyze the team's position coverage, calculating how well each position is already covered.
     * 
     * @param teamHeroes Current heroes on the team
     * @return A map of position -> coverage score (0.0 = not covered, 1.0 = fully covered)
     */
    private Map<Integer, Double> analyzeTeamPositions(List<Hero> teamHeroes) {
        Map<Integer, Double> positionCoverage = new HashMap<>();
        
        // Initialize all positions
        for (int position = 1; position <= 5; position++) {
            positionCoverage.put(position, 0.0);
        }
        
        // If there are no heroes, return empty coverage
        if (teamHeroes.isEmpty()) {
            return positionCoverage;
        }
        
        // For each hero in the team
        for (Hero hero : teamHeroes) {
            Map<Integer, Double> heroPositions = hero.getRoleFrequency();
            
            // If no position data, assume equal probability across all positions
            if (heroPositions == null || heroPositions.isEmpty()) {
                for (int position = 1; position <= 5; position++) {
                    positionCoverage.put(position, 
                        positionCoverage.getOrDefault(position, 0.0) + (0.2 / teamHeroes.size()));
                }
                continue;
            }
            
            // For each position this hero plays
            for (Map.Entry<Integer, Double> entry : heroPositions.entrySet()) {
                int position = entry.getKey();
                double frequency = entry.getValue();
                
                // Increment the coverage for this position
                positionCoverage.put(position, 
                    positionCoverage.getOrDefault(position, 0.0) + (frequency / teamHeroes.size()));
            }
        }
        
        // Cap all values at 1.0
        for (int position = 1; position <= 5; position++) {
            positionCoverage.put(position, Math.min(1.0, positionCoverage.get(position)));
        }
        
        return positionCoverage;
    }
    
    /**
     * Generate reasoning text for position balancing
     * 
     * @param hero The hero to evaluate
     * @param teamHeroes Current heroes on the team
     * @return List of position balance reasoning strings
     */
    private List<String> generatePositionReasons(Hero hero, List<Hero> teamHeroes) {
        List<String> reasons = new ArrayList<>();
        
        // If no role frequency data is available, skip
        if (hero.getRoleFrequency() == null || hero.getRoleFrequency().isEmpty()) {
            return reasons;
        }
        
        Map<Integer, Double> teamPositions = analyzeTeamPositions(teamHeroes);
        
        // Find position with maximum frequency for this hero
        Map.Entry<Integer, Double> primaryPosition = hero.getRoleFrequency().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
            
        if (primaryPosition != null) {
            int position = primaryPosition.getKey();
            double frequency = primaryPosition.getValue();
            
            // If this position is not well covered in the team
            if (teamPositions.getOrDefault(position, 0.0) < 0.5 && frequency > 0.4) {
                if (hero.hasDefaultRoleFrequency()) {
                    reasons.add(String.format(
                        "%s likely fills the needed position %d role (based on hero archetype).", 
                        hero.getLocalizedName(), position));
                } else {
                    reasons.add(String.format(
                        "%s fills the needed position %d role (based on pro match data).", 
                        hero.getLocalizedName(), position));
                }
            }
        }
        
        // Check if hero is flexible
        int positionsPlayed = (int) hero.getRoleFrequency().entrySet().stream()
            .filter(entry -> entry.getValue() >= 0.2)
            .count();
            
        if (positionsPlayed > 1) {
            // Draft phase awareness: mention draft stage importance for flexible heroes
            boolean earlyDraft = teamHeroes.size() <= 2;
            
            if (earlyDraft) {
                if (hero.hasDefaultRoleFrequency()) {
                    reasons.add(String.format(
                        "%s appears to offer position flexibility (%d potential roles) - could be valuable for an early-draft pick.", 
                        hero.getLocalizedName(), positionsPlayed));
                } else {
                    reasons.add(String.format(
                        "%s offers strong position flexibility (%d roles) - ideal for an early-draft pick to keep options open.", 
                        hero.getLocalizedName(), positionsPlayed));
                }
            } else {
                if (hero.hasDefaultRoleFrequency()) {
                    reasons.add(String.format(
                        "%s appears to offer position flexibility (%d potential roles).", 
                        hero.getLocalizedName(), positionsPlayed));
                } else {
                    reasons.add(String.format(
                        "%s offers position flexibility (%d roles).", 
                        hero.getLocalizedName(), positionsPlayed));
                }
            }
        }
        
        return reasons;
    }
    
    /**
     * Calculate a meta power score for each hero based on current meta analysis.
     * This prioritizes strong meta heroes that can be picked early despite limited flexibility.
     * 
     * @param hero The hero to evaluate
     * @param teamHeroes Current heroes on the team
     * @return A score between 0.0 and 1.0 representing meta power
     */
    private double calculateMetaPowerScore(Hero hero, List<Hero> teamHeroes) {
        // Base score derived from win rate and pick rate
        double winRate = mlTrainingService.getHeroWinRates().getOrDefault("all", Collections.emptyMap())
                        .getOrDefault(hero.getId(), 0.5);
        double pickRate = matchRepository.getHeroPickRatesByRank("all")
                        .getOrDefault(hero.getId(), 0.1);
                        
        // Default values if win rate is missing (not in pro matches dataset)
        boolean usingDefaultMetaData = false;
        if (winRate == 0.5 && pickRate == 0.1) {
            usingDefaultMetaData = true;
            // Slight advantage for STR heroes in current meta (example heuristic)
            if ("str".equals(hero.getPrimaryAttribute())) {
                winRate = 0.52;
            }
        }
        
        // Calculate meta score as a combination of high win rate and pick rate
        double metaScore = (winRate * 0.7) + (pickRate * 0.3);
        
        // Reduce confidence for heroes with no match data
        if (usingDefaultMetaData) {
            metaScore = 0.5 + ((metaScore - 0.5) * 0.7); // Regress 30% toward 0.5
        }
        
        // Early draft assessment - more important for early picks
        boolean earlyDraft = teamHeroes.size() <= 2;
        
        // Heroes with exceptional win rates (>55%) get a strong bonus in early draft
        if (earlyDraft && winRate > 0.55) {
            metaScore += 0.2;
        }
        
        // Check if hero has strong counters in the meta
        // This is a simplified approach - in a real implementation,
        // we would use a machine learning model to determine which heroes
        // are currently strong counters to this hero in the meta
        boolean hasStrongMetaCounters = heroHasStrongMetaCounters(hero);
        if (hasStrongMetaCounters) {
            // Penalize heroes that have strong counters in the meta if picked early
            if (earlyDraft) {
                metaScore -= 0.15;
            } else {
                // Less penalty in late draft when we can see if counters are picked
                metaScore -= 0.05;
            }
        }
        
        // Cap at reasonable bounds
        return Math.max(0.0, Math.min(1.0, metaScore));
    }
    
    /**
     * Determine if a hero has strong counters that are currently popular in the meta
     */
    private boolean heroHasStrongMetaCounters(Hero hero) {
        // Get the hero's counter scores
        Map<String, Double> counters = mlTrainingService.getHeroCounters();
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank("all");
        
        // Count how many popular heroes strongly counter this hero
        int strongPopularCounters = 0;
        double counterThreshold = 0.65; // Counter score threshold
        double pickRateThreshold = 0.15; // Pick rate threshold for "popular"
        
        for (Map.Entry<Integer, Double> pickRateEntry : pickRates.entrySet()) {
            int potentialCounterId = pickRateEntry.getKey();
            double pickRate = pickRateEntry.getValue();
            
            // Skip if not popular enough
            if (pickRate < pickRateThreshold) continue;
            
            // Check counter relationship
            String counterKey = potentialCounterId + "_" + hero.getId();
            double counterScore = counters.getOrDefault(counterKey, 0.5);
            
            if (counterScore > counterThreshold) {
                strongPopularCounters++;
            }
        }
        
        // Consider having strong counters if there are 3+ popular heroes that counter this hero
        return strongPopularCounters >= 3;
    }
    
    /**
     * Generate reasoning text for meta power scoring
     * 
     * @param hero The hero to evaluate
     * @return List of meta power reasoning strings
     */
    private List<String> generateMetaReasons(Hero hero) {
        List<String> reasons = new ArrayList<>();
        
        double winRate = mlTrainingService.getHeroWinRates().getOrDefault("all", Collections.emptyMap())
                        .getOrDefault(hero.getId(), 0.5);
        double pickRate = matchRepository.getHeroPickRatesByRank("all")
                        .getOrDefault(hero.getId(), 0.1);
        
        // Check if we're using default data (hero not in pro matches)
        boolean usingDefaultMetaData = (winRate == 0.5 && pickRate == 0.1);
        
        if (usingDefaultMetaData) {
            // For heroes without pro data, provide more generalized reasoning
            if ("str".equals(hero.getPrimaryAttribute())) {
                reasons.add(String.format(
                    "%s may be valuable in the current meta, which favors strength heroes (though not seen in recent pro matches).", 
                    hero.getLocalizedName()));
            } else if ("agi".equals(hero.getPrimaryAttribute())) {
                reasons.add(String.format(
                    "%s could be an unconventional pick not seen in recent pro matches.", 
                    hero.getLocalizedName()));
            } else {
                reasons.add(String.format(
                    "%s hasn't appeared in recent pro matches but may offer situational value.", 
                    hero.getLocalizedName()));
            }
        } else {
            // Exceptional win rate
            if (winRate > 0.55) {
                reasons.add(String.format(
                    "%s is very strong in the current meta with a %.1f%% win rate.", 
                    hero.getLocalizedName(), winRate * 100));
            } else if (winRate > 0.53) {
                reasons.add(String.format(
                    "%s performs well in the current meta with a %.1f%% win rate.", 
                    hero.getLocalizedName(), winRate * 100));
            }
            
            // Popular pick
            if (pickRate > 0.2) {
                reasons.add(String.format(
                    "%s is a highly contested pick in the current meta (%.1f%% pick rate).", 
                    hero.getLocalizedName(), pickRate * 100));
            }
        }
        
        // Check for counter relationships
        boolean hasStrongMetaCounters = heroHasStrongMetaCounters(hero);
        if (hasStrongMetaCounters) {
            reasons.add(String.format(
                "Caution: %s has several popular counters in the current meta.", 
                hero.getLocalizedName()));
        } else {
            if (winRate > 0.52) {
                reasons.add(String.format(
                    "%s is a safe early pick with few strong counters in the current meta.", 
                    hero.getLocalizedName()));
            }
        }
        
        return reasons;
    }
    
    /**
     * Helper method to get all available heroes
     */
    private List<Hero> getAvailableHeroes(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Create a set of unavailable hero IDs
        Set<Integer> unavailableIds = new HashSet<>();
        
        for (Hero hero : radiantPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : direPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : bannedHeroes) {
            unavailableIds.add(hero.getId());
        }
        
        // Filter the heroes
        return allHeroes.stream()
                .filter(hero -> !unavailableIds.contains(hero.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Class to hold hero score components and reasoning
     */
    public static class HeroScore implements Comparable<HeroScore> {
        private double totalScore;
        private double winRateScore;
        private double pickRateScore;
        private double abilityScore;
        private double synergyScore;
        private double counterScore;
        private double positionDiversityScore;
        private double metaPowerScore;
        
        private List<String> abilityReasons = new ArrayList<>();
        private List<String> synergyReasons = new ArrayList<>();
        private List<String> counterReasons = new ArrayList<>();
        private List<String> positionReasons = new ArrayList<>();
        private List<String> metaReasons = new ArrayList<>();
        
        public double getTotalScore() {
            return totalScore;
        }
        
        public void setTotalScore(double totalScore) {
            this.totalScore = totalScore;
        }
        
        public double getWinRateScore() {
            return winRateScore;
        }
        
        public void setWinRateScore(double winRateScore) {
            this.winRateScore = winRateScore;
        }
        
        public double getPickRateScore() {
            return pickRateScore;
        }
        
        public void setPickRateScore(double pickRateScore) {
            this.pickRateScore = pickRateScore;
        }
        
        public double getAbilityScore() {
            return abilityScore;
        }
        
        public void setAbilityScore(double abilityScore) {
            this.abilityScore = abilityScore;
        }
        
        public double getSynergyScore() {
            return synergyScore;
        }
        
        public void setSynergyScore(double synergyScore) {
            this.synergyScore = synergyScore;
        }
        
        public double getCounterScore() {
            return counterScore;
        }
        
        public void setCounterScore(double counterScore) {
            this.counterScore = counterScore;
        }
        
        public List<String> getAbilityReasons() {
            return abilityReasons;
        }
        
        public void setAbilityReasons(List<String> abilityReasons) {
            this.abilityReasons = abilityReasons;
        }
        
        public List<String> getSynergyReasons() {
            return synergyReasons;
        }
        
        public void setSynergyReasons(List<String> synergyReasons) {
            this.synergyReasons = synergyReasons;
        }
        
        public List<String> getCounterReasons() {
            return counterReasons;
        }
        
        public void setCounterReasons(List<String> counterReasons) {
            this.counterReasons = counterReasons;
        }
        
        public double getPositionDiversityScore() {
            return positionDiversityScore;
        }
        
        public void setPositionDiversityScore(double positionDiversityScore) {
            this.positionDiversityScore = positionDiversityScore;
        }
        
        public List<String> getPositionReasons() {
            return positionReasons;
        }
        
        public void setPositionReasons(List<String> positionReasons) {
            this.positionReasons = positionReasons;
        }
        
        public double getMetaPowerScore() {
            return metaPowerScore;
        }
        
        public void setMetaPowerScore(double metaPowerScore) {
            this.metaPowerScore = metaPowerScore;
        }
        
        public List<String> getMetaReasons() {
            return metaReasons;
        }
        
        public void setMetaReasons(List<String> metaReasons) {
            this.metaReasons = metaReasons;
        }
        
        @Override
        public int compareTo(HeroScore other) {
            return Double.compare(this.totalScore, other.totalScore);
        }
    }
}