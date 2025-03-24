package com.dota2assistant.core.draft;

import com.dota2assistant.core.analysis.HeroRecommendation;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.data.model.PlayerHeroRecommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for generating personalized draft recommendations 
 * that incorporate both global meta data and player-specific performance data.
 */
public interface DraftRecommendationService {
    
    /**
     * Gets recommended hero picks for the current draft state.
     * 
     * @param radiantPicks Current Radiant team picks
     * @param direPicks Current Dire team picks
     * @param bannedHeroes Currently banned heroes
     * @param currentTeam Team for which to recommend picks (RADIANT or DIRE)
     * @param playerPerformance Optional map of player performance data by hero ID
     * @param limit Maximum number of recommendations to return
     * @return List of recommended heroes with reasoning
     */
    List<PlayerHeroRecommendation> getRecommendedPicks(
        List<Hero> radiantPicks, 
        List<Hero> direPicks, 
        List<Hero> bannedHeroes,
        Team currentTeam, 
        Map<Integer, PlayerHeroPerformance> playerPerformance,
        int limit
    );
    
    /**
     * Gets recommended hero bans for the current draft state.
     * 
     * @param radiantPicks Current Radiant team picks
     * @param direPicks Current Dire team picks
     * @param bannedHeroes Currently banned heroes
     * @param currentTeam Team for which to recommend bans (RADIANT or DIRE)
     * @param playerPerformance Optional map of player performance data by hero ID
     * @param limit Maximum number of recommendations to return
     * @return List of recommended heroes with reasoning
     */
    List<PlayerHeroRecommendation> getRecommendedBans(
        List<Hero> radiantPicks, 
        List<Hero> direPicks, 
        List<Hero> bannedHeroes,
        Team currentTeam, 
        Map<Integer, PlayerHeroPerformance> playerPerformance,
        int limit
    );
    
    /**
     * Gets a score for each available hero based on the current draft state.
     * 
     * @param radiantPicks Current Radiant team picks
     * @param direPicks Current Dire team picks
     * @param bannedHeroes Currently banned heroes
     * @param currentTeam Team for which to calculate scores (RADIANT or DIRE)
     * @param playerPerformance Optional map of player performance data by hero ID
     * @return Map of hero ID to recommendation score (0.0-1.0)
     */
    Map<Integer, Double> getHeroScores(
        List<Hero> radiantPicks, 
        List<Hero> direPicks, 
        List<Hero> bannedHeroes,
        Team currentTeam, 
        Map<Integer, PlayerHeroPerformance> playerPerformance
    );
    
    /**
     * Gets a list of recommended heroes based on the current ally and enemy picks.
     * This is a simplified version used specifically for GSI integration.
     *
     * @param allyPicks Current ally team picks
     * @param enemyPicks Current enemy team picks 
     * @param unavailableHeroIds Set of hero IDs that are unavailable (picked or banned)
     * @param limit Maximum number of recommendations to return
     * @return List of recommended heroes
     */
    default List<Hero> getRecommendedHeroes(
        List<Hero> allyPicks,
        List<Hero> enemyPicks,
        Set<Integer> unavailableHeroIds,
        int limit
    ) {
        // Default implementation calls the more detailed method with default parameters
        // This can be overridden by implementations that want to provide a more optimized version
        List<Hero> bannedHeroes = new ArrayList<>();
        // Current team doesn't matter in this simplified version
        Team currentTeam = Team.RADIANT;
        
        // Call the more detailed implementation and extract just the heroes
        List<PlayerHeroRecommendation> recommendations = getRecommendedPicks(
            allyPicks,
            enemyPicks, 
            bannedHeroes,
            currentTeam,
            null, // No player performance data 
            limit
        );
        
        // Convert to simple list of heroes
        return recommendations.stream()
            .map(PlayerHeroRecommendation::getHero)
            .filter(hero -> !unavailableHeroIds.contains(hero.getId()))
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }
}