package com.dota2assistant.core.draft;

import com.dota2assistant.core.ai.AiDecisionEngine;
import com.dota2assistant.core.analysis.AnalysisEngine;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.data.model.PlayerHeroRecommendation;
import com.dota2assistant.data.model.PlayerHeroRecommendation.RecommendationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the DraftRecommendationService that combines
 * global meta data with player-specific performance.
 */
@Service
public class DefaultDraftRecommendationService implements DraftRecommendationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDraftRecommendationService.class);
    
    private final AiDecisionEngine aiDecisionEngine;
    private final AnalysisEngine analysisEngine;
    
    // Weight factors for different recommendation components
    private static final double SYNERGY_WEIGHT = 0.30;
    private static final double COUNTER_WEIGHT = 0.30;
    private static final double META_WEIGHT = 0.20;
    private static final double PLAYER_WEIGHT_BASE = 0.20;
    
    // Weight adjustments based on player experience
    private static final double PLAYER_WEIGHT_MAX_BOOST = 0.20;
    private static final int EXPERIENCE_THRESHOLD = 20; // Matches needed for full weight
    
    // Thresholds for comfort picks
    private static final int COMFORT_PICK_MIN_MATCHES = 5;
    private static final double COMFORT_PICK_WIN_RATE = 0.55;
    
    public DefaultDraftRecommendationService(AiDecisionEngine aiDecisionEngine, 
                                           AnalysisEngine analysisEngine) {
        this.aiDecisionEngine = aiDecisionEngine;
        this.analysisEngine = analysisEngine;
    }
    
    @Override
    public List<PlayerHeroRecommendation> getRecommendedPicks(
            List<Hero> radiantPicks, 
            List<Hero> direPicks, 
            List<Hero> bannedHeroes,
            Team currentTeam, 
            Map<Integer, PlayerHeroPerformance> playerPerformance,
            int limit) {
        
        LOGGER.debug("Getting recommended picks for team {} with player performance data: {}", 
                   currentTeam, playerPerformance != null ? playerPerformance.size() : 0);
        
        // Get team-specific picks
        List<Hero> allyPicks = currentTeam == Team.RADIANT ? radiantPicks : direPicks;
        List<Hero> enemyPicks = currentTeam == Team.RADIANT ? direPicks : radiantPicks;
        
        // Get hero scores based on current draft state
        Map<Integer, Double> heroScores = getHeroScores(
            radiantPicks, direPicks, bannedHeroes, currentTeam, playerPerformance);
        
        // Get all available heroes (not picked or banned)
        List<Hero> availableHeroes = aiDecisionEngine.getAvailableHeroes();
        List<Hero> availableForPicks = availableHeroes.stream()
            .filter(h -> !radiantPicks.contains(h) && !direPicks.contains(h) && !bannedHeroes.contains(h))
            .collect(Collectors.toList());
        
        // Generate sorted recommendations
        List<PlayerHeroRecommendation> recommendations = generateSortedRecommendations(
            availableForPicks, 
            heroScores, 
            allyPicks, 
            enemyPicks, 
            playerPerformance, 
            true, // true for picks (show synergies)
            limit
        );
        
        return recommendations;
    }
    
    @Override
    public List<PlayerHeroRecommendation> getRecommendedBans(
            List<Hero> radiantPicks, 
            List<Hero> direPicks, 
            List<Hero> bannedHeroes,
            Team currentTeam, 
            Map<Integer, PlayerHeroPerformance> playerPerformance,
            int limit) {
        
        LOGGER.debug("Getting recommended bans for team {} with player performance data: {}", 
                   currentTeam, playerPerformance != null ? playerPerformance.size() : 0);
        
        // For bans, we consider the enemy team's perspective
        Team enemyTeam = currentTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Get team-specific picks
        List<Hero> allyPicks = currentTeam == Team.RADIANT ? radiantPicks : direPicks;
        List<Hero> enemyPicks = currentTeam == Team.RADIANT ? direPicks : radiantPicks;
        
        // Get hero scores based on current draft state, but from enemy perspective
        // This helps identify heroes that would be strong for the enemy
        Map<Integer, Double> heroScores = getHeroScores(
            radiantPicks, direPicks, bannedHeroes, enemyTeam, null);
        
        // Get all available heroes (not picked or banned)
        List<Hero> availableHeroes = aiDecisionEngine.getAvailableHeroes();
        List<Hero> availableForBans = availableHeroes.stream()
            .filter(h -> !radiantPicks.contains(h) && !direPicks.contains(h) && !bannedHeroes.contains(h))
            .collect(Collectors.toList());
        
        // Generate sorted ban recommendations
        List<PlayerHeroRecommendation> recommendations = generateSortedRecommendations(
            availableForBans, 
            heroScores, 
            enemyPicks,  // For bans, we focus on what synergizes well with enemy picks
            allyPicks,   // For bans, we focus on what counters our picks
            playerPerformance, 
            false,  // false for bans (show counters)
            limit
        );
        
        return recommendations;
    }
    
    @Override
    public Map<Integer, Double> getHeroScores(
            List<Hero> radiantPicks, 
            List<Hero> direPicks, 
            List<Hero> bannedHeroes,
            Team currentTeam, 
            Map<Integer, PlayerHeroPerformance> playerPerformance) {
        
        // Get team-specific picks
        List<Hero> allyPicks = currentTeam == Team.RADIANT ? radiantPicks : direPicks;
        List<Hero> enemyPicks = currentTeam == Team.RADIANT ? direPicks : radiantPicks;
        
        // Get all available heroes (not picked or banned)
        List<Hero> availableHeroes = aiDecisionEngine.getAvailableHeroes();
        List<Hero> availableForPicks = availableHeroes.stream()
            .filter(h -> !radiantPicks.contains(h) && !direPicks.contains(h) && !bannedHeroes.contains(h))
            .collect(Collectors.toList());
        
        Map<Integer, Double> scores = new HashMap<>();
        
        // Calculate scoring components for all available heroes
        for (Hero hero : availableForPicks) {
            // Calculate base scores from global data
            double synergyScore = calculateSynergyScore(hero, allyPicks);
            double counterScore = calculateCounterScore(hero, enemyPicks);
            double metaScore = calculateMetaScore(hero);
            
            // Player-specific performance adjustment
            double playerFactor = 0.0;
            double playerWeight = PLAYER_WEIGHT_BASE;
            
            if (playerPerformance != null && playerPerformance.containsKey(hero.getId())) {
                PlayerHeroPerformance perf = playerPerformance.get(hero.getId());
                
                // Calculate player factor based on win rate and matches played
                int matches = perf.getMatches();
                if (matches > 0) {
                    // Scale the player weight based on experience (more matches = more weight)
                    double experienceFactor = Math.min(1.0, (double) matches / EXPERIENCE_THRESHOLD);
                    playerWeight = PLAYER_WEIGHT_BASE + (PLAYER_WEIGHT_MAX_BOOST * experienceFactor);
                    
                    // Player factor combines win rate and impact
                    playerFactor = perf.calculatePerformanceScore() / 10.0; // Scale to 0-1 range
                    
                    // Comfort boost for heroes the player performs well with
                    if (perf.isComfortPick()) {
                        playerFactor *= 1.2; // 20% boost for comfort picks
                    }
                }
            }
            
            // Adjust other weights if player weight is increased
            double remainingWeight = 1.0 - playerWeight;
            double synergyWeightAdjusted = SYNERGY_WEIGHT / (SYNERGY_WEIGHT + COUNTER_WEIGHT + META_WEIGHT) * remainingWeight;
            double counterWeightAdjusted = COUNTER_WEIGHT / (SYNERGY_WEIGHT + COUNTER_WEIGHT + META_WEIGHT) * remainingWeight;
            double metaWeightAdjusted = META_WEIGHT / (SYNERGY_WEIGHT + COUNTER_WEIGHT + META_WEIGHT) * remainingWeight;
            
            // Combine all factors with weights
            double finalScore = (synergyScore * synergyWeightAdjusted) +
                              (counterScore * counterWeightAdjusted) +
                              (metaScore * metaWeightAdjusted) +
                              (playerFactor * playerWeight);
            
            // Store final score
            scores.put(hero.getId(), finalScore);
        }
        
        return scores;
    }
    
    /**
     * Generates sorted hero recommendations based on scores and performance.
     * 
     * @param availableHeroes List of heroes available for recommendation 
     * @param heroScores Map of hero ID to calculated score
     * @param allyTeam Current ally team heroes
     * @param enemyTeam Current enemy team heroes
     * @param playerPerformance Optional map of player performance data by hero ID
     * @param isPick Whether generating pick recommendations (vs. bans)
     * @param limit Maximum number of recommendations to return
     * @return List of hero recommendations sorted by score
     */
    private List<PlayerHeroRecommendation> generateSortedRecommendations(
            List<Hero> availableHeroes,
            Map<Integer, Double> heroScores,
            List<Hero> allyTeam,
            List<Hero> enemyTeam,
            Map<Integer, PlayerHeroPerformance> playerPerformance,
            boolean isPick,
            int limit) {
        
        // Create recommendations for each available hero
        List<PlayerHeroRecommendation> recommendations = new ArrayList<>();
        
        for (Hero hero : availableHeroes) {
            // Skip if we don't have a score for this hero
            if (!heroScores.containsKey(hero.getId())) {
                continue;
            }
            
            double score = heroScores.get(hero.getId());
            
            // Determine recommendation type and reason
            RecommendationType type = determineRecommendationType(
                hero, isPick, playerPerformance, allyTeam, enemyTeam);
            
            String reason = generateRecommendationReason(
                hero, type, isPick, playerPerformance, allyTeam, enemyTeam);
            
            // Create recommendation with default values
            double winRate = analysisEngine.getHeroWinRate(hero);
            boolean isComfortPick = false;
            int matchesPlayed = 0;
            double kdaRatio = 0.0;
            
            // Update with player-specific data if available
            if (playerPerformance != null && playerPerformance.containsKey(hero.getId())) {
                PlayerHeroPerformance perf = playerPerformance.get(hero.getId());
                isComfortPick = perf.isComfortPick();
                matchesPlayed = perf.getMatches();
                kdaRatio = perf.getKdaRatio();
                
                // For pick recommendations, player's personal win rate is more relevant
                if (isPick && matchesPlayed >= 5) {
                    winRate = perf.getWinRate();
                }
            }
            
            // Create final recommendation
            PlayerHeroRecommendation recommendation = new PlayerHeroRecommendation(
                hero, score * 10.0, isComfortPick, matchesPlayed, winRate, kdaRatio, reason, type);
            
            recommendations.add(recommendation);
        }
        
        // Sort by score (descending)
        recommendations.sort((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()));
        
        // Apply special sorting logic:
        // 1. Comfort picks should be prioritized regardless of score if player data is available
        // 2. Counter picks should be prioritized in ban recommendations
        if (playerPerformance != null && !playerPerformance.isEmpty()) {
            // Sort with comfort picks first, then by score
            recommendations.sort((a, b) -> {
                if (a.isComfortPick() && !b.isComfortPick()) {
                    return -1;
                } else if (!a.isComfortPick() && b.isComfortPick()) {
                    return 1;
                } else {
                    return Double.compare(b.getRecommendationScore(), a.getRecommendationScore());
                }
            });
        }
        
        // Limit the number of recommendations
        if (recommendations.size() > limit) {
            recommendations = recommendations.subList(0, limit);
        }
        
        return recommendations;
    }
    
    /**
     * Determines the recommendation type for a hero.
     */
    private RecommendationType determineRecommendationType(
            Hero hero, 
            boolean isPick, 
            Map<Integer, PlayerHeroPerformance> playerPerformance,
            List<Hero> allyTeam,
            List<Hero> enemyTeam) {
        
        // Check if this is a comfort pick for the player
        if (playerPerformance != null && 
            playerPerformance.containsKey(hero.getId()) && 
            playerPerformance.get(hero.getId()).isComfortPick()) {
            return RecommendationType.COMFORT;
        }
        
        // For picks, check if hero has strong synergy with ally team
        if (isPick && !allyTeam.isEmpty()) {
            double synergyScore = calculateSynergyScore(hero, allyTeam);
            if (synergyScore > 0.7) {
                return RecommendationType.SYNERGY;
            }
        }
        
        // For bans or if we have enemy picks, check if hero counters enemies
        if (!enemyTeam.isEmpty()) {
            double counterScore = calculateCounterScore(hero, enemyTeam);
            if (counterScore > 0.7) {
                return RecommendationType.COUNTER;
            }
        }
        
        // Check if hero is strong in current meta
        double metaScore = calculateMetaScore(hero);
        if (metaScore > 0.7) {
            return RecommendationType.META;
        }
        
        // Default to balanced pick
        return RecommendationType.BALANCED;
    }
    
    /**
     * Generates a recommendation reason based on the hero and recommendation type.
     */
    private String generateRecommendationReason(
            Hero hero, 
            RecommendationType type, 
            boolean isPick,
            Map<Integer, PlayerHeroPerformance> playerPerformance,
            List<Hero> allyTeam,
            List<Hero> enemyTeam) {
        
        switch (type) {
            case COMFORT:
                if (playerPerformance != null && playerPerformance.containsKey(hero.getId())) {
                    PlayerHeroPerformance perf = playerPerformance.get(hero.getId());
                    return String.format("Your comfort hero (%.1f%% win rate in %d matches)", 
                                      perf.getWinRate() * 100, perf.getMatches());
                }
                return "Your comfort pick";
                
            case SYNERGY:
                if (!allyTeam.isEmpty()) {
                    // Find strongest synergy
                    Hero bestSynergy = allyTeam.stream()
                        .max(Comparator.comparingDouble(ally -> 
                             analysisEngine.calculateHeroSynergy(hero, ally)))
                        .orElse(null);
                    
                    if (bestSynergy != null) {
                        return String.format("Strong synergy with %s", bestSynergy.getLocalizedName());
                    }
                }
                return "Good synergy with your team composition";
                
            case COUNTER:
                if (!enemyTeam.isEmpty()) {
                    // Find strongest counter
                    Hero bestCounter = enemyTeam.stream()
                        .max(Comparator.comparingDouble(enemy -> 
                             analysisEngine.calculateHeroCounter(hero, enemy)))
                        .orElse(null);
                    
                    if (bestCounter != null) {
                        return isPick ? 
                            String.format("Counters enemy %s", bestCounter.getLocalizedName()) :
                            String.format("Strong against your team's %s", bestCounter.getLocalizedName());
                    }
                }
                return isPick ? 
                    "Effective counter to enemy lineup" :
                    "Strong against your current draft";
                
            case META:
                double winRate = analysisEngine.getHeroWinRate(hero);
                int pickCount = analysisEngine.getHeroPickCount(hero);
                return String.format("Strong in current meta (%.1f%% win rate)", winRate * 100);
                
            case BALANCED:
            default:
                return "Balanced pick for current draft";
        }
    }
    
    private double calculateSynergyScore(Hero hero, List<Hero> allyTeam) {
        if (allyTeam.isEmpty()) {
            return 0.5; // Neutral score if no allies yet
        }
        
        // Calculate average synergy with existing team
        double totalSynergy = 0.0;
        for (Hero ally : allyTeam) {
            totalSynergy += analysisEngine.calculateHeroSynergy(hero, ally);
        }
        
        return totalSynergy / allyTeam.size();
    }
    
    private double calculateCounterScore(Hero hero, List<Hero> enemyTeam) {
        if (enemyTeam.isEmpty()) {
            return 0.5; // Neutral score if no enemies yet
        }
        
        // Calculate average counter score against enemy team
        double totalCounter = 0.0;
        for (Hero enemy : enemyTeam) {
            totalCounter += analysisEngine.calculateHeroCounter(hero, enemy);
        }
        
        return totalCounter / enemyTeam.size();
    }
    
    private double calculateMetaScore(Hero hero) {
        // Combine win rate and pick rate into a meta score
        double winRate = analysisEngine.getHeroWinRate(hero);
        double pickRate = analysisEngine.getHeroPickRate(hero);
        
        // Value high win rate over pick rate
        return (winRate * 0.7) + (pickRate * 0.3);
    }
}