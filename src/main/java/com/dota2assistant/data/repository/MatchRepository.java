package com.dota2assistant.data.repository;

import com.dota2assistant.data.api.DotaApiClient;
import com.dota2assistant.data.db.DatabaseManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchRepository {

    private static final Logger logger = LoggerFactory.getLogger(MatchRepository.class);
    
    private final DatabaseManager dbManager;
    private final DotaApiClient apiClient;
    private final Map<Long, Map<String, Object>> matchCache;
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Cache for match statistics by rank
    private final Map<String, List<Map<String, Object>>> rankMatchCache = new ConcurrentHashMap<>();
    // Cache for pick rate data by rank
    private final Map<String, Map<Integer, Double>> pickRateCache = new ConcurrentHashMap<>();
    // Cache for win rate data by rank
    private final Map<String, Map<Integer, Double>> winRateCache = new ConcurrentHashMap<>();
    
    // Constants for rank labels
    public static final String RANK_CRUSADER = "crusader";
    public static final String RANK_ARCHON = "archon";
    public static final String RANK_LEGEND = "legend";
    public static final String RANK_ANCIENT = "ancient";
    public static final String RANK_DIVINE = "divine";
    public static final String RANK_IMMORTAL = "immortal";
    public static final String RANK_PRO = "professional";
    
    // MMR approximations for ranks
    private static final Map<String, Integer> RANK_MMR = new HashMap<>();
    static {
        RANK_MMR.put(RANK_CRUSADER, 2000);
        RANK_MMR.put(RANK_ARCHON, 2700);
        RANK_MMR.put(RANK_LEGEND, 3400);
        RANK_MMR.put(RANK_ANCIENT, 4100);
        RANK_MMR.put(RANK_DIVINE, 4800);
        RANK_MMR.put(RANK_IMMORTAL, 5500);
        RANK_MMR.put(RANK_PRO, 8000); // Professional level is approximate
    }
    
    public MatchRepository(DatabaseManager dbManager, DotaApiClient apiClient) {
        this.dbManager = dbManager;
        this.apiClient = apiClient;
        this.matchCache = new ConcurrentHashMap<>();
        
        try {
            initDatabase();
        } catch (SQLException e) {
            logger.error("Failed to initialize match repository database tables", e);
        }
    }
    
    private void initDatabase() throws SQLException {
        // Match table
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS matches (" +
                "match_id BIGINT PRIMARY KEY, " +
                "match_data TEXT NOT NULL, " + // JSON format
                "match_rank TEXT, " +
                "is_pro BOOLEAN DEFAULT FALSE, " +
                "match_timestamp BIGINT, " +
                "cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
        );
        
        // Hero stats by rank
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_stats_by_rank (" +
                "hero_id INTEGER, " +
                "rank TEXT, " +
                "pick_rate REAL, " +
                "win_rate REAL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (hero_id, rank)" +
                ")"
        );
        
        // Hero picks by match
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS match_hero_picks (" +
                "match_id BIGINT, " +
                "hero_id INTEGER, " +
                "team INTEGER, " + // 0 for Radiant, 1 for Dire
                "position INTEGER, " + // 1-5 positions
                "is_winner BOOLEAN, " +
                "PRIMARY KEY (match_id, hero_id), " +
                "FOREIGN KEY (match_id) REFERENCES matches(match_id)" +
                ")"
        );
        
        // Match index for faster rank-based queries
        dbManager.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_matches_rank ON matches(match_rank)"
        );
        
        // Match index for timestamp-based sorting
        dbManager.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_matches_timestamp ON matches(match_timestamp)"
        );
    }
    
    /**
     * Gets a match by its ID, either from cache, database, or API
     */
    public Map<String, Object> getMatch(long matchId) {
        // Check cache first
        if (matchCache.containsKey(matchId)) {
            return matchCache.get(matchId);
        }
        
        // Try to load from database
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT match_data FROM matches WHERE match_id = ?")) {
            
            stmt.setLong(1, matchId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String matchDataJson = rs.getString("match_data");
                Map<String, Object> matchData = mapper.readValue(matchDataJson, Map.class);
                
                // Add to cache
                matchCache.put(matchId, matchData);
                return matchData;
            }
        } catch (SQLException | IOException e) {
            logger.error("Failed to load match from database", e);
        }
        
        // If not in database, fetch from API
        try {
            Map<String, Object> matchData = apiClient.fetchMatch(matchId);
            if (matchData != null && !matchData.isEmpty()) {
                // Save to database
                saveMatch(matchId, matchData);
                
                // Add to cache
                matchCache.put(matchId, matchData);
                return matchData;
            }
        } catch (IOException e) {
            logger.error("Failed to fetch match from API", e);
        }
        
        // Return empty map if match not found
        return Collections.emptyMap();
    }
    
    /**
     * Saves a match to the database
     */
    private void saveMatch(long matchId, Map<String, Object> matchData) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO matches (match_id, match_data, match_rank, is_pro, match_timestamp) " +
                     "VALUES (?, ?, ?, ?, ?)")) {
            
            String matchDataJson = mapper.writeValueAsString(matchData);
            String rank = determineMatchRank(matchData);
            boolean isPro = isProMatch(matchData);
            long timestamp = extractMatchTimestamp(matchData);
            
            stmt.setLong(1, matchId);
            stmt.setString(2, matchDataJson);
            stmt.setString(3, rank);
            stmt.setBoolean(4, isPro);
            stmt.setLong(5, timestamp);
            stmt.executeUpdate();
            
            // Also save the hero picks for this match
            saveMatchHeroPicks(conn, matchId, matchData);
            
        } catch (SQLException | IOException e) {
            logger.error("Failed to save match to database", e);
        }
    }
    
    /**
     * Saves hero picks for a match
     */
    private void saveMatchHeroPicks(Connection conn, long matchId, Map<String, Object> matchData) throws SQLException {
        // Delete existing picks for this match
        try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM match_hero_picks WHERE match_id = ?")) {
            deleteStmt.setLong(1, matchId);
            deleteStmt.executeUpdate();
        }
        
        // Insert new picks
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO match_hero_picks (match_id, hero_id, team, position, is_winner) VALUES (?, ?, ?, ?, ?)")) {
            
            // Extract players data
            List<Map<String, Object>> players = (List<Map<String, Object>>) matchData.getOrDefault("players", Collections.emptyList());
            
            // Extract if radiant won
            boolean radiantWin = Boolean.TRUE.equals(matchData.getOrDefault("radiant_win", false));
            
            for (Map<String, Object> player : players) {
                Integer heroId = (Integer) player.get("hero_id");
                Integer team = (Integer) player.getOrDefault("team_number", player.getOrDefault("player_slot", 0)) <= 4 ? 0 : 1; // 0 for Radiant, 1 for Dire
                Integer position = getPlayerPosition(player);
                boolean isWinner = (team == 0 && radiantWin) || (team == 1 && !radiantWin);
                
                if (heroId != null) {
                    insertStmt.setLong(1, matchId);
                    insertStmt.setInt(2, heroId);
                    insertStmt.setInt(3, team);
                    insertStmt.setInt(4, position != null ? position : 0);
                    insertStmt.setBoolean(5, isWinner);
                    insertStmt.executeUpdate();
                }
            }
        }
    }
    
    /**
     * Gets player position (1-5) based on player data
     */
    private Integer getPlayerPosition(Map<String, Object> player) {
        // First try lane_role if available
        if (player.containsKey("lane_role")) {
            Integer laneRole = (Integer) player.get("lane_role");
            
            // Convert lane_role to position (1-5)
            if (laneRole != null) {
                switch (laneRole) {
                    case 1: return 1; // Safe lane carry
                    case 2: return 2; // Mid lane
                    case 3: return 3; // Off lane
                    case 4: return 4; // Soft support
                }
                return 5; // Hard support (default)
            }
        }
        
        // Next try gold_per_min as a heuristic
        if (player.containsKey("gold_per_min")) {
            // Could analyze relative GPM to determine farming priority and position
            // This is a simplified approach
            return null;
        }
        
        return null; // Unknown position
    }
    
    /**
     * Determines the rank bracket of a match based on average MMR
     */
    private String determineMatchRank(Map<String, Object> matchData) {
        // Check if it's a pro match first
        if (isProMatch(matchData)) {
            return RANK_PRO;
        }
        
        // Try to extract average_mmr or use individual player MMRs
        Integer avgMmr = null;
        
        if (matchData.containsKey("avg_mmr") || matchData.containsKey("average_mmr")) {
            avgMmr = (Integer) matchData.getOrDefault("avg_mmr", 
                    matchData.getOrDefault("average_mmr", null));
        }
        
        if (avgMmr == null) {
            // Try to calculate from players
            List<Map<String, Object>> players = (List<Map<String, Object>>) 
                    matchData.getOrDefault("players", Collections.emptyList());
            
            int mmrSum = 0;
            int mmrCount = 0;
            
            for (Map<String, Object> player : players) {
                if (player.containsKey("solo_competitive_rank") || player.containsKey("mmr_estimate")) {
                    Integer playerMmr = (Integer) player.getOrDefault("solo_competitive_rank",
                            ((Map<String, Object>) player.getOrDefault("mmr_estimate", Map.of())).getOrDefault("estimate", 0));
                    
                    if (playerMmr > 0) {
                        mmrSum += playerMmr;
                        mmrCount++;
                    }
                }
            }
            
            if (mmrCount > 0) {
                avgMmr = mmrSum / mmrCount;
            }
        }
        
        // Determine rank based on MMR
        if (avgMmr != null) {
            if (avgMmr >= RANK_MMR.get(RANK_IMMORTAL)) return RANK_IMMORTAL;
            if (avgMmr >= RANK_MMR.get(RANK_DIVINE)) return RANK_DIVINE;
            if (avgMmr >= RANK_MMR.get(RANK_ANCIENT)) return RANK_ANCIENT;
            if (avgMmr >= RANK_MMR.get(RANK_LEGEND)) return RANK_LEGEND;
            if (avgMmr >= RANK_MMR.get(RANK_ARCHON)) return RANK_ARCHON;
            if (avgMmr >= RANK_MMR.get(RANK_CRUSADER)) return RANK_CRUSADER;
        }
        
        // Default to legend if unknown
        return RANK_LEGEND;
    }
    
    /**
     * Checks if a match is a professional match
     */
    private boolean isProMatch(Map<String, Object> matchData) {
        // Check for league_id or other pro identifiers
        return matchData.containsKey("league_id") && 
               (Integer) matchData.getOrDefault("league_id", 0) > 0;
    }
    
    /**
     * Extracts the timestamp from match data
     */
    private long extractMatchTimestamp(Map<String, Object> matchData) {
        if (matchData.containsKey("start_time")) {
            return ((Number) matchData.get("start_time")).longValue();
        }
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * Gets a list of professional matches
     */
    public List<Map<String, Object>> getProMatches(int limit) {
        // First check database for recent pro matches
        List<Map<String, Object>> matches = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT match_id, match_data FROM matches WHERE is_pro = TRUE " +
                     "ORDER BY match_timestamp DESC LIMIT ?")) {
            
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                long matchId = rs.getLong("match_id");
                String matchDataJson = rs.getString("match_data");
                Map<String, Object> matchData = mapper.readValue(matchDataJson, Map.class);
                matchCache.put(matchId, matchData); // Add to cache
                matches.add(matchData);
            }
        } catch (SQLException | IOException e) {
            logger.error("Failed to load pro matches from database", e);
        }
        
        // If we don't have enough matches from the database, fetch from API
        if (matches.size() < limit) {
            try {
                List<Map<String, Object>> apiMatches = apiClient.fetchProMatches(limit);
                
                for (Map<String, Object> matchData : apiMatches) {
                    // Don't add duplicates
                    long matchId = ((Number) matchData.get("match_id")).longValue();
                    if (!matchCache.containsKey(matchId)) {
                        // Save to database
                        saveMatch(matchId, matchData);
                        
                        // Add to cache and result
                        matchCache.put(matchId, matchData);
                        matches.add(matchData);
                        
                        // Stop if we've reached the limit
                        if (matches.size() >= limit) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to fetch pro matches from API", e);
            }
        }
        
        return matches.subList(0, Math.min(matches.size(), limit));
    }
    
    /**
     * Gets matches by rank bracket
     */
    public List<Map<String, Object>> getMatchesByRank(String rank, int limit) {
        // Check cache first
        if (rankMatchCache.containsKey(rank)) {
            List<Map<String, Object>> cached = rankMatchCache.get(rank);
            if (cached.size() >= limit) {
                return cached.subList(0, limit);
            }
        }
        
        // Query from database
        List<Map<String, Object>> matches = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT match_id, match_data FROM matches WHERE match_rank = ? " +
                     "ORDER BY match_timestamp DESC LIMIT ?")) {
            
            stmt.setString(1, rank);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                long matchId = rs.getLong("match_id");
                String matchDataJson = rs.getString("match_data");
                Map<String, Object> matchData = mapper.readValue(matchDataJson, Map.class);
                matchCache.put(matchId, matchData); // Add to cache
                matches.add(matchData);
            }
        } catch (SQLException | IOException e) {
            logger.error("Failed to load matches by rank from database", e);
        }
        
        // If we don't have enough matches from the database, fetch from API
        if (matches.size() < limit && RANK_MMR.containsKey(rank)) {
            try {
                // For ranks other than pro, we need a different approach
                // This is a workaround since the API doesn't directly support rank-based filtering
                List<Map<String, Object>> apiMatches = apiClient.fetchMatchesByRank(rank, limit * 3); // Fetch more to filter
                
                for (Map<String, Object> matchData : apiMatches) {
                    // Check if this match falls within our rank bracket
                    String matchRank = determineMatchRank(matchData);
                    if (rank.equals(matchRank)) {
                        long matchId = ((Number) matchData.get("match_id")).longValue();
                        // Don't add duplicates
                        if (!matchCache.containsKey(matchId)) {
                            // Save to database
                            saveMatch(matchId, matchData);
                            
                            // Add to cache and result
                            matchCache.put(matchId, matchData);
                            matches.add(matchData);
                            
                            // Stop if we've reached the limit
                            if (matches.size() >= limit) {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to fetch matches by rank from API", e);
            }
        }
        
        // Update cache
        rankMatchCache.put(rank, new ArrayList<>(matches));
        
        return matches.subList(0, Math.min(matches.size(), limit));
    }
    
    /**
     * Gets hero pick rates by rank
     */
    public Map<Integer, Double> getHeroPickRatesByRank(String rank) {
        // Check cache first
        if (pickRateCache.containsKey(rank)) {
            return pickRateCache.get(rank);
        }
        
        Map<Integer, Double> pickRates = new HashMap<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT hero_id, pick_rate FROM hero_stats_by_rank WHERE rank = ?")) {
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId = rs.getInt("hero_id");
                double pickRate = rs.getDouble("pick_rate");
                pickRates.put(heroId, pickRate);
            }
        } catch (SQLException e) {
            logger.error("Failed to load pick rates from database", e);
        }
        
        // If database is empty, calculate from match data
        if (pickRates.isEmpty()) {
            pickRates = calculatePickRates(rank);
            saveHeroStats(rank, pickRates, null);
        }
        
        // Update cache
        pickRateCache.put(rank, pickRates);
        
        return pickRates;
    }
    
    /**
     * Gets hero win rates by rank
     */
    public Map<Integer, Double> getHeroWinRatesByRank(String rank) {
        // Check cache first
        if (winRateCache.containsKey(rank)) {
            return winRateCache.get(rank);
        }
        
        Map<Integer, Double> winRates = new HashMap<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT hero_id, win_rate FROM hero_stats_by_rank WHERE rank = ?")) {
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId = rs.getInt("hero_id");
                double winRate = rs.getDouble("win_rate");
                winRates.put(heroId, winRate);
            }
        } catch (SQLException e) {
            logger.error("Failed to load win rates from database", e);
        }
        
        // If database is empty, calculate from match data
        if (winRates.isEmpty()) {
            winRates = calculateWinRates(rank);
            saveHeroStats(rank, null, winRates);
        }
        
        // Update cache
        winRateCache.put(rank, winRates);
        
        return winRates;
    }
    
    /**
     * Calculates pick rates from match data
     */
    private Map<Integer, Double> calculatePickRates(String rank) {
        Map<Integer, Integer> pickCounts = new HashMap<>();
        int totalPicks = 0;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT m.match_id, hp.hero_id FROM matches m " +
                     "JOIN match_hero_picks hp ON m.match_id = hp.match_id " +
                     "WHERE m.match_rank = ? " +
                     "ORDER BY m.match_timestamp DESC LIMIT 1000")) {
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId = rs.getInt("hero_id");
                pickCounts.put(heroId, pickCounts.getOrDefault(heroId, 0) + 1);
                totalPicks++;
            }
        } catch (SQLException e) {
            logger.error("Failed to calculate pick rates", e);
        }
        
        // Convert counts to rates
        Map<Integer, Double> pickRates = new HashMap<>();
        if (totalPicks > 0) {
            for (Map.Entry<Integer, Integer> entry : pickCounts.entrySet()) {
                pickRates.put(entry.getKey(), (double) entry.getValue() / totalPicks);
            }
        }
        
        return pickRates;
    }
    
    /**
     * Calculates win rates from match data
     */
    private Map<Integer, Double> calculateWinRates(String rank) {
        Map<Integer, Integer> winCounts = new HashMap<>();
        Map<Integer, Integer> pickCounts = new HashMap<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT m.match_id, hp.hero_id, hp.is_winner FROM matches m " +
                     "JOIN match_hero_picks hp ON m.match_id = hp.match_id " +
                     "WHERE m.match_rank = ? " +
                     "ORDER BY m.match_timestamp DESC LIMIT 1000")) {
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId = rs.getInt("hero_id");
                boolean isWinner = rs.getBoolean("is_winner");
                
                pickCounts.put(heroId, pickCounts.getOrDefault(heroId, 0) + 1);
                if (isWinner) {
                    winCounts.put(heroId, winCounts.getOrDefault(heroId, 0) + 1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to calculate win rates", e);
        }
        
        // Convert counts to rates
        Map<Integer, Double> winRates = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : pickCounts.entrySet()) {
            int heroId = entry.getKey();
            int picks = entry.getValue();
            int wins = winCounts.getOrDefault(heroId, 0);
            
            if (picks > 0) {
                winRates.put(heroId, (double) wins / picks);
            }
        }
        
        return winRates;
    }
    
    /**
     * Saves hero stats to the database
     */
    private void saveHeroStats(String rank, Map<Integer, Double> pickRates, Map<Integer, Double> winRates) {
        try (Connection conn = dbManager.getConnection()) {
            // For each hero with stats, update or insert
            Set<Integer> heroIds = new HashSet<>();
            if (pickRates != null) heroIds.addAll(pickRates.keySet());
            if (winRates != null) heroIds.addAll(winRates.keySet());
            
            for (int heroId : heroIds) {
                Double pickRate = pickRates != null ? pickRates.get(heroId) : null;
                Double winRate = winRates != null ? winRates.get(heroId) : null;
                
                // Check if entry exists
                boolean exists = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT 1 FROM hero_stats_by_rank WHERE hero_id = ? AND rank = ?")) {
                    checkStmt.setInt(1, heroId);
                    checkStmt.setString(2, rank);
                    ResultSet rs = checkStmt.executeQuery();
                    exists = rs.next();
                }
                
                if (exists) {
                    // Update existing entry
                    StringBuilder sql = new StringBuilder("UPDATE hero_stats_by_rank SET updated_at = CURRENT_TIMESTAMP");
                    if (pickRate != null) sql.append(", pick_rate = ?");
                    if (winRate != null) sql.append(", win_rate = ?");
                    sql.append(" WHERE hero_id = ? AND rank = ?");
                    
                    try (PreparedStatement updateStmt = conn.prepareStatement(sql.toString())) {
                        int paramIndex = 1;
                        if (pickRate != null) updateStmt.setDouble(paramIndex++, pickRate);
                        if (winRate != null) updateStmt.setDouble(paramIndex++, winRate);
                        updateStmt.setInt(paramIndex++, heroId);
                        updateStmt.setString(paramIndex, rank);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert new entry
                    StringBuilder sql = new StringBuilder("INSERT INTO hero_stats_by_rank (hero_id, rank");
                    if (pickRate != null) sql.append(", pick_rate");
                    if (winRate != null) sql.append(", win_rate");
                    sql.append(") VALUES (?, ?");
                    if (pickRate != null) sql.append(", ?");
                    if (winRate != null) sql.append(", ?");
                    sql.append(")");
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(sql.toString())) {
                        int paramIndex = 1;
                        insertStmt.setInt(paramIndex++, heroId);
                        insertStmt.setString(paramIndex++, rank);
                        if (pickRate != null) insertStmt.setDouble(paramIndex++, pickRate);
                        if (winRate != null) insertStmt.setDouble(paramIndex++, winRate);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save hero stats", e);
        }
    }
    
    /**
     * Refreshes match data for a specific rank
     */
    public void refreshMatchData(String rank, int limit) {
        // Clear caches
        if (rankMatchCache.containsKey(rank)) {
            rankMatchCache.remove(rank);
        }
        if (pickRateCache.containsKey(rank)) {
            pickRateCache.remove(rank);
        }
        if (winRateCache.containsKey(rank)) {
            winRateCache.remove(rank);
        }
        
        try {
            // Fetch fresh data from API
            List<Map<String, Object>> matches;
            if (RANK_PRO.equals(rank)) {
                matches = apiClient.fetchProMatches(limit);
            } else {
                matches = apiClient.fetchMatchesByRank(rank, limit);
            }
            
            // Process and save the matches
            for (Map<String, Object> match : matches) {
                long matchId = ((Number) match.get("match_id")).longValue();
                saveMatch(matchId, match);
            }
            
            // Recalculate statistics
            Map<Integer, Double> pickRates = calculatePickRates(rank);
            Map<Integer, Double> winRates = calculateWinRates(rank);
            
            // Save to database
            saveHeroStats(rank, pickRates, winRates);
            
            // Update caches
            pickRateCache.put(rank, pickRates);
            winRateCache.put(rank, winRates);
            
        } catch (IOException e) {
            logger.error("Failed to refresh match data for rank " + rank, e);
        }
    }
    
    /**
     * Gets hero synergy scores by rank (heroes that work well together)
     */
    public Map<String, Double> getHeroSynergies(String rank) {
        Map<String, Double> synergies = new HashMap<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT h1.hero_id as hero_id1, h2.hero_id as hero_id2, " +
                     "SUM(CASE WHEN h1.is_winner = 1 THEN 1 ELSE 0 END) as wins, " +
                     "COUNT(*) as games " +
                     "FROM match_hero_picks h1 " +
                     "JOIN match_hero_picks h2 ON h1.match_id = h2.match_id " +
                     "JOIN matches m ON h1.match_id = m.match_id " +
                     "WHERE h1.team = h2.team AND h1.hero_id < h2.hero_id " +  // Ensure unique pairs
                     "AND m.match_rank = ? " +
                     "GROUP BY h1.hero_id, h2.hero_id " +
                     "HAVING games >= 5")) {  // Min threshold for statistical relevance
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId1 = rs.getInt("hero_id1");
                int heroId2 = rs.getInt("hero_id2");
                int wins = rs.getInt("wins");
                int games = rs.getInt("games");
                
                double winRate = (double) wins / games;
                String key = heroId1 + "_" + heroId2;
                synergies.put(key, winRate);
            }
        } catch (SQLException e) {
            logger.error("Failed to load hero synergies", e);
        }
        
        // If we don't have enough data, use mock data for now
        // This avoids unnecessary API calls during development/startup
        if (synergies.size() < 100) {
            // Generate mock synergy data
            Map<String, Double> mockSynergies = new HashMap<>();
            for (int i = 1; i <= 130; i++) {
                for (int j = i + 1; j <= 130; j++) {
                    String key = i + "_" + j;
                    mockSynergies.put(key, Math.random() * 0.5 + 0.25);
                }
            }
            synergies.putAll(mockSynergies);
            logger.info("Using mock synergy data for development");
        }
        
        return synergies;
    }
    
    /**
     * Gets hero counter scores by rank (heroes that are strong against others)
     */
    public Map<String, Double> getHeroCounters(String rank) {
        Map<String, Double> counters = new HashMap<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT h1.hero_id as hero_id1, h2.hero_id as hero_id2, " +
                     "SUM(CASE WHEN h1.is_winner = 1 THEN 1 ELSE 0 END) as wins, " +
                     "COUNT(*) as games " +
                     "FROM match_hero_picks h1 " +
                     "JOIN match_hero_picks h2 ON h1.match_id = h2.match_id " +
                     "JOIN matches m ON h1.match_id = m.match_id " +
                     "WHERE h1.team <> h2.team " +  // Different teams
                     "AND m.match_rank = ? " +
                     "GROUP BY h1.hero_id, h2.hero_id " +
                     "HAVING games >= 5")) {  // Min threshold for statistical relevance
            
            stmt.setString(1, rank);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int heroId1 = rs.getInt("hero_id1");
                int heroId2 = rs.getInt("hero_id2");
                int wins = rs.getInt("wins");
                int games = rs.getInt("games");
                
                double winRate = (double) wins / games;
                
                // Hero1 counters Hero2 if Hero1 has a high win rate against Hero2
                String key = heroId1 + "_" + heroId2;
                counters.put(key, winRate);
            }
        } catch (SQLException e) {
            logger.error("Failed to load hero counters", e);
        }
        
        // If we don't have enough data, use mock data for now
        // This avoids unnecessary API calls during development/startup
        if (counters.size() < 100) {
            // Generate mock counter data
            Map<String, Double> mockCounters = new HashMap<>();
            for (int i = 1; i <= 130; i++) {
                for (int j = 1; j <= 130; j++) {
                    if (i != j) {
                        String key = i + "_" + j;
                        mockCounters.put(key, Math.random() * 0.5 + 0.25);
                    }
                }
            }
            counters.putAll(mockCounters);
            logger.info("Using mock counter data for development");
        }
        
        return counters;
    }
}