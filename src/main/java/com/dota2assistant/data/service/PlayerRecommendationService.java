package com.dota2assistant.data.service;

import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.data.model.PlayerHeroStat;
import com.dota2assistant.data.model.PlayerMatch;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.UserMatchRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Service for generating player-specific hero recommendations
 * by combining global meta data with personal performance.
 */
@Service
public class PlayerRecommendationService {
    private static final Logger LOGGER = Logger.getLogger(PlayerRecommendationService.class.getName());
    
    private final UserMatchRepository userMatchRepository;
    private final HeroRepository heroRepository;
    private final DatabaseManager databaseManager;
    
    // Cache player performance data to reduce database load
    private final Map<Long, Map<Integer, PlayerHeroPerformance>> playerPerformanceCache;
    private final Map<Long, LocalDateTime> cacheTimestamps;
    
    // Constants for weighting and calculations
    private static final int STANDARD_MATCH_THRESHOLD = 500; // Matches needed for standard weighting
    private static final double STANDARD_GLOBAL_WEIGHT = 0.6; // Global meta weight for players with enough matches
    private static final double LIMITED_GLOBAL_WEIGHT = 0.8; // Global meta weight for players with limited history
    
    private static final int COMFORT_PICK_MIN_MATCHES = 5; // Minimum matches to be considered a comfort pick
    private static final double COMFORT_PICK_THRESHOLD = 0.55; // Min win rate for comfort heroes
    private static final int RECENT_MATCH_DAYS = 90; // Consider matches in last 90 days as recent
    
    // Cache expiration time
    private static final int CACHE_EXPIRATION_MINUTES = 30;
    
    public PlayerRecommendationService(UserMatchRepository userMatchRepository, HeroRepository heroRepository, DatabaseManager databaseManager) {
        this.userMatchRepository = userMatchRepository;
        this.heroRepository = heroRepository;
        this.databaseManager = databaseManager;
        this.playerPerformanceCache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets weighted hero performance data for a player.
     * 
     * @param accountId The player's account ID
     * @return Map of hero ID to performance data
     */
    public Map<Integer, PlayerHeroPerformance> getPlayerHeroPerformance(long accountId) {
        // Check cache first
        if (hasValidCache(accountId)) {
            return new HashMap<>(playerPerformanceCache.get(accountId));
        }
        
        LOGGER.info("Calculating hero performance for player " + accountId);
        
        // First check if we have data in the player_hero_performance table
        Map<Integer, PlayerHeroPerformance> performance = getPerformanceFromDatabase(accountId);
        
        // If we don't have data in the database, calculate from matches
        if (performance.isEmpty()) {
            // Get player match history
            List<PlayerMatch> matches = userMatchRepository.getPlayerMatchHistory(accountId, 1000, 0);
            
            // Calculate total matches and determine weighting
            int totalMatches = matches.size();
            double globalWeight = totalMatches < STANDARD_MATCH_THRESHOLD ? 
                                LIMITED_GLOBAL_WEIGHT : STANDARD_GLOBAL_WEIGHT;
            double personalWeight = 1.0 - globalWeight;
            
            LOGGER.info("Player has " + totalMatches + " matches. Weighting: " + 
                      (globalWeight * 100) + "% global / " + (personalWeight * 100) + "% personal");
            
            // Calculate hero statistics
            Map<Integer, int[]> heroStats = getHeroStats(matches);
            performance = calculatePerformance(
                heroStats, 
                matches,
                globalWeight, 
                personalWeight
            );
        }
        
        // Cache the results
        playerPerformanceCache.put(accountId, performance);
        cacheTimestamps.put(accountId, LocalDateTime.now());
        
        return performance;
    }
    
    /**
     * Gets player hero performance from the database.
     * 
     * @param accountId The player's account ID
     * @return Map of hero ID to performance data
     */
    private Map<Integer, PlayerHeroPerformance> getPerformanceFromDatabase(long accountId) {
        Map<Integer, PlayerHeroPerformance> performance = new HashMap<>();
        
        // Calculate global weighting based on match count
        String countSql = "SELECT COUNT(*) FROM match_players WHERE account_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            
            countStmt.setLong(1, accountId);
            
            int totalMatches = 0;
            try (ResultSet rs = countStmt.executeQuery()) {
                if (rs.next()) {
                    totalMatches = rs.getInt(1);
                }
            }
            
            double globalWeight = totalMatches < STANDARD_MATCH_THRESHOLD ? 
                                LIMITED_GLOBAL_WEIGHT : STANDARD_GLOBAL_WEIGHT;
            double personalWeight = 1.0 - globalWeight;
            
            // Get performance data from database
            String sql = "SELECT php.account_id, php.hero_id, h.name, h.localized_name, " +
                       "php.matches_count, php.wins_count, php.total_kills, php.total_deaths, " +
                       "php.total_assists, php.last_played, php.performance_score, " +
                       "php.comfort_score, php.pick_rate, php.is_comfort_pick " +
                       "FROM player_hero_performance php " +
                       "JOIN heroes h ON php.hero_id = h.id " +
                       "WHERE php.account_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, accountId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int heroId = rs.getInt("hero_id");
                        String heroName = rs.getString("name");
                        String localizedName = rs.getString("localized_name");
                        
                        // Create a Hero object
                        Hero hero = heroRepository.getHeroById(heroId);
                        if (hero == null) {
                            hero = new Hero(heroId, heroName, localizedName);
                        }
                        
                        // Create performance object
                        int matchCount = rs.getInt("matches_count");
                        double winRate = matchCount > 0 ? rs.getInt("wins_count") / (double) matchCount : 0;
                        
                        // Calculate KDA
                        int kills = rs.getInt("total_kills");
                        int deaths = rs.getInt("total_deaths");
                        int assists = rs.getInt("total_assists");
                        double kdaRatio = deaths > 0 ? (kills + assists) / (double) deaths : kills + assists;
                        
                        java.sql.Timestamp lastPlayedTime = rs.getTimestamp("last_played");
                        LocalDateTime lastPlayed = lastPlayedTime != null ? 
                            lastPlayedTime.toLocalDateTime() : null;
                        
                        double impactScore = rs.getDouble("performance_score");
                        double confidenceScore = Math.min(1.0, Math.log(matchCount + 1) / Math.log(21));
                        double pickRate = rs.getDouble("pick_rate");
                        boolean isComfortPick = rs.getBoolean("is_comfort_pick");
                        
                        PlayerHeroPerformance heroPerformance = new PlayerHeroPerformance(
                            hero, matchCount, winRate, kdaRatio, impactScore, confidenceScore,
                            pickRate, isComfortPick, globalWeight, personalWeight, lastPlayed
                        );
                        
                        performance.put(heroId, heroPerformance);
                    }
                }
            }
            
            // Add any missing heroes with default values
            for (Hero hero : heroRepository.getAllHeroes()) {
                int heroId = hero.getId();
                if (!performance.containsKey(heroId)) {
                    performance.put(heroId, new PlayerHeroPerformance(hero));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting player hero performance from database", e);
        }
        
        return performance;
    }
    
    /**
     * Determines if there is valid cached data for a player.
     * 
     * @param accountId The player's account ID
     * @return True if valid cache exists
     */
    private boolean hasValidCache(long accountId) {
        if (!playerPerformanceCache.containsKey(accountId) || !cacheTimestamps.containsKey(accountId)) {
            return false;
        }
        
        LocalDateTime timestamp = cacheTimestamps.get(accountId);
        return ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now()) < CACHE_EXPIRATION_MINUTES;
    }
    
    /**
     * Calculates hero statistics from match history.
     * 
     * @param matches The player's match history
     * @return Map of hero ID to stats array [matches, wins, kills, deaths, assists]
     */
    private Map<Integer, int[]> getHeroStats(List<PlayerMatch> matches) {
        Map<Integer, int[]> stats = new HashMap<>();
        
        for (PlayerMatch match : matches) {
            int heroId = match.getHero().getId();
            
            stats.putIfAbsent(heroId, new int[5]); // [matches, wins, kills, deaths, assists]
            int[] heroStats = stats.get(heroId);
            
            heroStats[0]++; // Matches
            if (match.isWon()) {
                heroStats[1]++; // Wins
            }
            heroStats[2] += match.getKills(); // Kills
            heroStats[3] += match.getDeaths(); // Deaths
            heroStats[4] += match.getAssists(); // Assists
        }
        
        return stats;
    }
    
    /**
     * Calculates hero performance metrics.
     * 
     * @param heroStats Map of hero statistics
     * @param matches Full match history
     * @param globalWeight Weight to give global meta data
     * @param personalWeight Weight to give personal performance data
     * @return Map of hero ID to performance metrics
     */
    private Map<Integer, PlayerHeroPerformance> calculatePerformance(
            Map<Integer, int[]> heroStats, 
            List<PlayerMatch> matches,
            double globalWeight, 
            double personalWeight) {
        
        Map<Integer, PlayerHeroPerformance> performance = new HashMap<>();
        int totalMatches = matches.size();
        
        // First pass: create basic performance objects for each hero
        for (Map.Entry<Integer, int[]> entry : heroStats.entrySet()) {
            int heroId = entry.getKey();
            int[] stats = entry.getValue();
            
            // Find the hero object
            Hero hero = heroRepository.getHeroById(heroId);
            if (hero == null) {
                continue;
            }
            int matchCount = stats[0];
            double winRate = matchCount > 0 ? (double) stats[1] / matchCount : 0;
            double kdaRatio = stats[3] > 0 ? 
                           (double) (stats[2] + stats[4]) / stats[3] : 
                           stats[2] + stats[4];
            
            // Find the last time this hero was played
            LocalDateTime lastPlayed = findLastPlayedDate(matches, heroId);
            
            // Calculate pick rate
            double pickRate = (double) matchCount / totalMatches;
            
            // Calculate confidence based on match count
            double confidence = calculateConfidence(matchCount);
            
            // Determine if this is a comfort pick
            boolean isComfortPick = matchCount >= COMFORT_PICK_MIN_MATCHES && 
                                  winRate >= COMFORT_PICK_THRESHOLD;
            
            // Calculate impact score based on win rate and KDA
            double impactScore = calculateImpactScore(winRate, kdaRatio);
            
            PlayerHeroPerformance heroPerformance = new PlayerHeroPerformance(
                hero,
                matchCount,
                winRate,
                kdaRatio,
                impactScore,
                confidence,
                pickRate,
                isComfortPick,
                globalWeight,
                personalWeight,
                lastPlayed
            );
            
            performance.put(heroId, heroPerformance);
        }
        
        // Add any missing heroes with default values
        for (Hero hero : heroRepository.getAllHeroes()) {
            int heroId = hero.getId();
            if (!performance.containsKey(heroId)) {
                performance.put(heroId, new PlayerHeroPerformance(hero));
            }
        }
        
        return performance;
    }
    
    /**
     * Finds when a hero was last played.
     * 
     * @param matches The player's match history
     * @param heroId The hero ID to check
     * @return The last played date, or null if never played
     */
    private LocalDateTime findLastPlayedDate(List<PlayerMatch> matches, int heroId) {
        return matches.stream()
            .filter(m -> m.getHero().getId() == heroId)
            .map(PlayerMatch::getDate)
            .max(LocalDateTime::compareTo)
            .orElse(null);
    }
    
    /**
     * Calculates confidence score based on match count.
     * 
     * @param matchCount Number of matches with the hero
     * @return Confidence score between 0.0 and 1.0
     */
    private double calculateConfidence(int matchCount) {
        // Logarithmic confidence scale that approaches 1.0
        if (matchCount == 0) {
            return 0.0;
        }
        
        return Math.min(1.0, Math.log(matchCount + 1) / Math.log(21)); // Log base 20 + 1
    }
    
    /**
     * Calculates impact score based on win rate and KDA.
     * 
     * @param winRate Win rate with the hero
     * @param kdaRatio KDA ratio with the hero
     * @return Impact score between 0.0 and 1.0
     */
    private double calculateImpactScore(double winRate, double kdaRatio) {
        // Weight win rate more heavily than KDA
        return (winRate * 0.7) + (Math.min(kdaRatio, 10.0) / 10.0 * 0.3);
    }
    
    /**
     * Gets recommended heroes for the player based on their performance.
     * 
     * @param accountId The player's account ID
     * @param limit Maximum number of recommendations to return
     * @param considerComfort Whether to prioritize comfort picks
     * @return List of recommended heroes with performance metrics
     */
    public List<PlayerHeroPerformance> getRecommendedHeroes(
            long accountId, int limit, boolean considerComfort) {
        
        Map<Integer, PlayerHeroPerformance> performance = getPlayerHeroPerformance(accountId);
        
        // Sort by performance score, optionally prioritizing comfort picks
        Comparator<PlayerHeroPerformance> comparator;
        
        if (considerComfort) {
            comparator = Comparator
                .comparing(PlayerHeroPerformance::isComfortPick).reversed()
                .thenComparingDouble(PlayerHeroPerformance::calculatePerformanceScore).reversed();
        } else {
            comparator = Comparator
                .comparingDouble(PlayerHeroPerformance::calculatePerformanceScore).reversed();
        }
        
        return performance.values().stream()
            .sorted(comparator)
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the player's comfort heroes (frequently played with good win rate).
     * 
     * @param accountId The player's account ID
     * @param limit Maximum number of comfort heroes to return
     * @return List of comfort heroes with performance metrics
     */
    public List<PlayerHeroPerformance> getComfortHeroes(long accountId, int limit) {
        Map<Integer, PlayerHeroPerformance> performance = getPlayerHeroPerformance(accountId);
        
        return performance.values().stream()
            .filter(PlayerHeroPerformance::isComfortPick)
            .sorted(Comparator
                .comparingDouble(PlayerHeroPerformance::getWinRate).reversed()
                .thenComparingInt(PlayerHeroPerformance::getMatches).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Clears the cache for a specific player.
     * 
     * @param accountId The player's account ID
     */
    public void clearCache(long accountId) {
        playerPerformanceCache.remove(accountId);
        cacheTimestamps.remove(accountId);
    }
    
    /**
     * Clears all cached data.
     */
    public void clearAllCaches() {
        playerPerformanceCache.clear();
        cacheTimestamps.clear();
    }
}