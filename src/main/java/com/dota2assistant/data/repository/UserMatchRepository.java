package com.dota2assistant.data.repository;

import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroStat;
import com.dota2assistant.data.model.PlayerMatch;
import com.dota2assistant.util.GameModeUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Repository for user match history and related data.
 */
@Repository
public class UserMatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserMatchRepository.class);
    private final DatabaseManager databaseManager;
    private final HeroRepository heroRepository;
    
    public UserMatchRepository(DatabaseManager databaseManager, HeroRepository heroRepository) {
        this.databaseManager = databaseManager;
        this.heroRepository = heroRepository;
    }
    
    /**
     * Gets player matches with pagination and filtering.
     *
     * @param accountId The player's account ID
     * @param limit Maximum number of matches to retrieve
     * @param offset Start position for pagination
     * @param includeHidden Whether to include hidden matches
     * @return List of player matches
     */
    public List<PlayerMatch> getPlayerMatches(int accountId, int limit, int offset, boolean includeHidden) {
        String sql = "SELECT m.id as match_id, mp.hero_id, " +
                    "mp.won, m.duration, m.start_time, " +
                    "mp.kills, mp.deaths, mp.assists, m.game_mode, " +
                    "mp.team = 0 as is_radiant, m.radiant_win, " +
                    "COALESCE(umd.is_favorite, FALSE) as is_favorite, " +
                    "COALESCE(umd.is_hidden, FALSE) as is_hidden, " +
                    "COALESCE(umd.notes, '') as notes " +
                    "FROM match_players mp " +
                    "JOIN matches m ON mp.match_id = m.id " +
                    "LEFT JOIN user_match_details umd ON m.id = umd.match_id AND umd.account_id = ? " +
                    "WHERE mp.account_id = ? " +
                    (includeHidden ? "" : "AND COALESCE(umd.is_hidden, FALSE) = FALSE ") +
                    "ORDER BY m.start_time DESC " +
                    "LIMIT ? OFFSET ?";
        
        List<PlayerMatch> matchHistory = new ArrayList<>();
        Map<Integer, Hero> heroCache = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, accountId);
            stmt.setInt(2, accountId);
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int heroId = rs.getInt("hero_id");
                    Hero hero = heroCache.computeIfAbsent(heroId, 
                                                         id -> {
                                                             Hero h = heroRepository.getHeroById(id);
                                                             if (h == null) {
                                                                 throw new RuntimeException("Hero not found: " + id);
                                                             }
                                                             return h;
                                                         });
                    
                    PlayerMatch match = new PlayerMatch(
                        rs.getLong("match_id"),
                        hero,
                        rs.getBoolean("won"),
                        rs.getInt("duration"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("assists"),
                        rs.getString("game_mode")
                    );
                    
                    match.setRadiantSide(rs.getBoolean("is_radiant"));
                    match.setRadiantWin(rs.getBoolean("radiant_win"));
                    match.setFavorite(rs.getBoolean("is_favorite"));
                    match.setHidden(rs.getBoolean("is_hidden"));
                    match.setNotes(rs.getString("notes"));
                    
                    matchHistory.add(match);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving player matches", e);
        }
        
        return matchHistory;
    }
    
    /**
     * Gets the count of player matches.
     *
     * @param accountId The player's account ID
     * @param includeHidden Whether to include hidden matches
     * @return Number of matches
     */
    public int getPlayerMatchCount(int accountId, boolean includeHidden) {
        String sql = "SELECT COUNT(*) " +
                    "FROM match_players mp " +
                    "LEFT JOIN user_match_details umd ON mp.match_id = umd.match_id AND umd.account_id = ? " +
                    "WHERE mp.account_id = ? " +
                    (includeHidden ? "" : "AND COALESCE(umd.is_hidden, FALSE) = FALSE");
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, accountId);
            stmt.setInt(2, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error counting player matches", e);
        }
        
        return 0;
    }
    
    /**
     * Saves a list of player matches.
     *
     * @param accountId The player's account ID
     * @param matches List of player matches to save
     * @return Number of matches saved
     */
    public int savePlayerMatches(int accountId, List<PlayerMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        
        int savedCount = 0;
        
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            // First save match data
            String matchSql = "INSERT INTO matches (id, duration, start_time, game_mode, radiant_win) " +
                              "VALUES (?, ?, ?, ?, ?) " +
                              "ON CONFLICT (id) DO UPDATE SET " +
                              "duration = EXCLUDED.duration, " +
                              "start_time = EXCLUDED.start_time, " +
                              "game_mode = EXCLUDED.game_mode, " +
                              "radiant_win = EXCLUDED.radiant_win";
            
            // First check if player match exists before inserting
            String checkSql = "SELECT id FROM match_players WHERE match_id = ? AND account_id = ?";
            
            // Insert or update player match data
            String insertPlayerSql = "INSERT INTO match_players (match_id, account_id, hero_id, is_radiant, kills, deaths, assists) " +
                                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            String updatePlayerSql = "UPDATE match_players SET " +
                                    "hero_id = ?, " +
                                    "is_radiant = ?, " +
                                    "kills = ?, " +
                                    "deaths = ?, " +
                                    "assists = ? " +
                                    "WHERE match_id = ? AND account_id = ?";
            
            try (PreparedStatement matchStmt = conn.prepareStatement(matchSql);
                 PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement insertPlayerStmt = conn.prepareStatement(insertPlayerSql);
                 PreparedStatement updatePlayerStmt = conn.prepareStatement(updatePlayerSql)) {
                
                for (PlayerMatch match : matches) {
                    // Save match data
                    matchStmt.setLong(1, match.getMatchId());
                    matchStmt.setInt(2, match.getDuration());
                    matchStmt.setTimestamp(3, java.sql.Timestamp.valueOf(match.getDate()));
                    // Convert string game mode to integer using our utility class
                    int gameModeInt = GameModeUtil.getGameModeId(match.getGameMode());
                    
                    // Log the mapping for debugging
                    if (match.getGameMode() != null && !match.getGameMode().isEmpty()) {
                        LOGGER.info("Mapped game mode '{}' to ID: {}", match.getGameMode(), gameModeInt);
                    }
                    matchStmt.setInt(4, gameModeInt);
                    matchStmt.setBoolean(5, match.isRadiantWin());
                    matchStmt.executeUpdate();
                    
                    // Check if player match exists
                    checkStmt.setLong(1, match.getMatchId());
                    checkStmt.setInt(2, accountId);
                    boolean exists = false;
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next();
                    }
                    
                    if (exists) {
                        // Update existing record
                        updatePlayerStmt.setInt(1, match.getHero().getId());
                        updatePlayerStmt.setBoolean(2, match.isRadiantSide());
                        updatePlayerStmt.setInt(3, match.getKills());
                        updatePlayerStmt.setInt(4, match.getDeaths());
                        updatePlayerStmt.setInt(5, match.getAssists());
                        updatePlayerStmt.setLong(6, match.getMatchId());
                        updatePlayerStmt.setInt(7, accountId);
                        updatePlayerStmt.executeUpdate();
                    } else {
                        // Insert new record
                        insertPlayerStmt.setLong(1, match.getMatchId());
                        insertPlayerStmt.setInt(2, accountId);
                        insertPlayerStmt.setInt(3, match.getHero().getId());
                        insertPlayerStmt.setBoolean(4, match.isRadiantSide());
                        insertPlayerStmt.setInt(5, match.getKills());
                        insertPlayerStmt.setInt(6, match.getDeaths());
                        insertPlayerStmt.setInt(7, match.getAssists());
                        insertPlayerStmt.executeUpdate();
                    }
                    
                    savedCount++;
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.error("Error saving player matches", e);
                return 0;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            LOGGER.error("Error getting database connection", e);
            return 0;
        }
        
        return savedCount;
    }
    
    /**
     * Marks a match history sync as complete.
     *
     * @param accountId Player account ID
     * @param matchCount Total matches synchronized
     * @param lastMatchId Most recent match ID
     * @param fullSyncComplete Whether a full sync has been completed
     * @param syncFrequency How often to sync (DAILY, WEEKLY, etc.)
     * @return True if update was successful
     */
    public boolean completeMatchHistorySync(int accountId, int matchCount, 
                                          Long lastMatchId, boolean fullSyncComplete, String syncFrequency) {
        String sql = "INSERT INTO player_match_history_sync " +
                    "(account_id, last_sync_timestamp, matches_count, last_match_id, " +
                    "full_sync_completed, sync_in_progress, sync_frequency) " +
                    "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (account_id) DO UPDATE SET " +
                    "last_sync_timestamp = CURRENT_TIMESTAMP, " +
                    "matches_count = EXCLUDED.matches_count, " +
                    "last_match_id = EXCLUDED.last_match_id, " +
                    "full_sync_completed = EXCLUDED.full_sync_completed, " +
                    "sync_in_progress = EXCLUDED.sync_in_progress, " +
                    "sync_frequency = EXCLUDED.sync_frequency";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, accountId);
            stmt.setInt(2, matchCount);
            
            if (lastMatchId != null) {
                try {
                    // PostgreSQL column is INTEGER (max ~2 billion), not BIGINT
                    int lastMatchIdInt = lastMatchId.intValue();
                    stmt.setInt(3, lastMatchIdInt);
                } catch (Exception e) {
                    // If value is too large to fit in an integer, set most recent possible value
                    LOGGER.warn("Match ID {} too large for INTEGER column, setting to MAX_INT", lastMatchId);
                    stmt.setInt(3, Integer.MAX_VALUE);
                }
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(4, fullSyncComplete ? 1 : 0);  // Use 1/0 instead of boolean
            stmt.setInt(5, 0);  // Setting sync_in_progress to false (0)
            stmt.setString(6, syncFrequency);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LOGGER.error("Error completing match history sync", e);
            return false;
        }
    }
    
    /**
     * Gets player hero statistics with sorting options.
     *
     * @param accountId The player's account ID
     * @param limit Maximum number of heroes to retrieve
     * @param sortBy Field to sort by (games, winRate, kdaRatio)
     * @return List of player hero statistics
     */
    public List<PlayerHeroStat> getPlayerHeroStats(int accountId, int limit, String sortBy) {
        String orderBy;
        
        // Determine sort order
        if ("winRate".equals(sortBy)) {
            orderBy = "wins / CAST(matches_played AS FLOAT) DESC";
        } else if ("kdaRatio".equals(sortBy)) {
            orderBy = "(kills + assists) / NULLIF(deaths, 0) DESC";
        } else {
            // Default to sort by games played
            orderBy = "matches_played DESC";
        }
        
        String sql = "SELECT mp.hero_id, COUNT(*) as matches_played, " +
                    "SUM(CASE WHEN mp.won THEN 1 ELSE 0 END) as wins, " +
                    "SUM(mp.kills) as kills, SUM(mp.deaths) as deaths, SUM(mp.assists) as assists " +
                    "FROM match_players mp " +
                    "WHERE mp.account_id = ? " +
                    "GROUP BY mp.hero_id " +
                    "ORDER BY " + orderBy + " " +
                    "LIMIT ?";
        
        List<PlayerHeroStat> heroStats = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, accountId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int heroId = rs.getInt("hero_id");
                    Hero hero = heroRepository.getHeroById(heroId);
                    
                    if (hero == null) {
                        continue;
                    }
                    
                    int matchesPlayed = rs.getInt("matches_played");
                    int wins = rs.getInt("wins");
                    double winRate = matchesPlayed > 0 ? (double) wins / matchesPlayed : 0;
                    
                    int kills = rs.getInt("kills");
                    int deaths = rs.getInt("deaths");
                    int assists = rs.getInt("assists");
                    
                    double kdaRatio = deaths > 0 ? (double) (kills + assists) / deaths : kills + assists;
                    
                    PlayerHeroStat stat = new PlayerHeroStat();
                    stat.setHero(hero);
                    stat.setMatchesPlayed(matchesPlayed);
                    stat.setWins(wins);
                    stat.setWinRate(winRate);
                    stat.setKills(kills);
                    stat.setDeaths(deaths);
                    stat.setAssists(assists);
                    stat.setKdaRatio(kdaRatio);
                    
                    heroStats.add(stat);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving player hero statistics with sorting", e);
        }
        
        return heroStats;
    }
    
    /**
     * Adds a notification for a user.
     *
     * @param accountId Player account ID
     * @param type Notification type
     * @param priority Notification priority
     * @param message Notification message
     * @return True if the notification was added successfully
     */
    public boolean addNotification(int accountId, String type, String priority, String message) {
        try {
            // Check if the notifications table exists, create it if it doesn't
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'user_notifications')")) {
                
                boolean tableExists = false;
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        tableExists = rs.getBoolean(1);
                    }
                }
                
                if (!tableExists) {
                    // Create the notifications table
                    try (PreparedStatement createStmt = conn.prepareStatement(
                        "CREATE TABLE user_notifications (" +
                        "id SERIAL PRIMARY KEY, " +
                        "account_id BIGINT NOT NULL, " +
                        "notification_type TEXT NOT NULL, " +
                        "priority TEXT DEFAULT 'NORMAL', " +
                        "message TEXT NOT NULL, " +
                        "created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "read_date TIMESTAMP, " +
                        "is_read BOOLEAN DEFAULT FALSE)")) { // Remove FK constraint for flexibility
                        
                        createStmt.executeUpdate();
                        LOGGER.info("Created user_notifications table without foreign key");
                    }
                }
            }
            
            // Make sure player exists in players table
            try (Connection conn = databaseManager.getConnection()) {
                // Check if player exists
                try (PreparedStatement checkStmt = conn.prepareStatement(
                     "SELECT 1 FROM players WHERE account_id = ?")) {
                    
                    checkStmt.setInt(1, accountId);
                    boolean playerExists = false;
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        playerExists = rs.next();
                    }
                    
                    // If player doesn't exist, create a dummy record
                    if (!playerExists) {
                        try (PreparedStatement insertStmt = conn.prepareStatement(
                             "INSERT INTO players (account_id, username, personaname, created_date) " +
                             "VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                            
                            insertStmt.setInt(1, accountId);
                            insertStmt.setString(2, "User_" + accountId);
                            insertStmt.setString(3, "User " + accountId);
                            insertStmt.executeUpdate();
                            LOGGER.info("Created placeholder player record for account ID: {}", accountId);
                        }
                    }
                }
            }
            
            // Now insert the notification
            String sql = "INSERT INTO user_notifications " +
                        "(account_id, notification_type, priority, message, created_date, is_read) " +
                        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, false)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, accountId);
                stmt.setString(2, type);
                
                if (priority != null) {
                    stmt.setString(3, priority);
                } else {
                    stmt.setString(3, "NORMAL");
                }
                
                stmt.setString(4, message);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
            
        } catch (SQLException e) {
            LOGGER.error("Error adding notification", e);
            return false;
        }
    }
    
    /**
     * Sets a match as favorite or unfavorite for a user.
     *
     * @param accountId Player account ID
     * @param matchId Match ID
     * @param isFavorite Whether the match is a favorite
     * @return True if the update was successful
     */
    public boolean setMatchFavorite(int accountId, long matchId, boolean isFavorite) {
        return updateUserMatchDetails(accountId, matchId, isFavorite, null, null);
    }
    
    /**
     * Sets a match as hidden or visible for a user.
     *
     * @param accountId Player account ID
     * @param matchId Match ID
     * @param isHidden Whether the match should be hidden
     * @return True if the update was successful
     */
    public boolean setMatchHidden(int accountId, long matchId, boolean isHidden) {
        return updateUserMatchDetails(accountId, matchId, null, isHidden, null);
    }
    
    /**
     * Updates the notes for a user's match.
     *
     * @param accountId Player account ID
     * @param matchId Match ID
     * @param notes The notes to save
     * @return True if the update was successful
     */
    public boolean updateMatchNotes(int accountId, long matchId, String notes) {
        return updateUserMatchDetails(accountId, matchId, null, null, notes);
    }
    
    /**
     * Gets player match history.
     *
     * @param accountId The player's account ID
     * @param limit Maximum number of matches to retrieve
     * @param offset Start position for pagination
     * @return List of player matches
     */
    public List<PlayerMatch> getPlayerMatchHistory(long accountId, int limit, int offset) {
        String sql = "SELECT m.id as match_id, mp.hero_id, " +
                    "mp.won, m.duration, m.start_time, " +
                    "mp.kills, mp.deaths, mp.assists, m.game_mode, " +
                    "mp.team = 0 as is_radiant, m.radiant_win, " +
                    "COALESCE(umd.is_favorite, FALSE) as is_favorite, " +
                    "COALESCE(umd.is_hidden, FALSE) as is_hidden, " +
                    "COALESCE(umd.notes, '') as notes " +
                    "FROM match_players mp " +
                    "JOIN matches m ON mp.match_id = m.id " +
                    "LEFT JOIN user_match_details umd ON m.id = umd.match_id AND umd.account_id = ? " +
                    "WHERE mp.account_id = ? " +
                    "ORDER BY m.start_time DESC " +
                    "LIMIT ? OFFSET ?";
        
        List<PlayerMatch> matchHistory = new ArrayList<>();
        Map<Integer, Hero> heroCache = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int heroId = rs.getInt("hero_id");
                    Hero hero = heroCache.computeIfAbsent(heroId, 
                                                         id -> {
                                                             Hero h = heroRepository.getHeroById(id);
                                                             if (h == null) {
                                                                 throw new RuntimeException("Hero not found: " + id);
                                                             }
                                                             return h;
                                                         });
                    
                    PlayerMatch match = new PlayerMatch(
                        rs.getLong("match_id"),
                        hero,
                        rs.getBoolean("won"),
                        rs.getInt("duration"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("assists"),
                        rs.getString("game_mode")
                    );
                    
                    match.setRadiantSide(rs.getBoolean("is_radiant"));
                    match.setRadiantWin(rs.getBoolean("radiant_win"));
                    match.setFavorite(rs.getBoolean("is_favorite"));
                    match.setHidden(rs.getBoolean("is_hidden"));
                    match.setNotes(rs.getString("notes"));
                    
                    matchHistory.add(match);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving player match history", e);
        }
        
        return matchHistory;
    }
    
    /**
     * Updates user match details like favorites, hidden status, and notes.
     *
     * @param accountId Player account ID
     * @param matchId Match ID
     * @param isFavorite Whether match is marked as favorite (can be null to not change)
     * @param isHidden Whether match is hidden from normal view (can be null to not change)
     * @param notes Optional notes about the match (can be null to not change)
     * @return True if update was successful
     */
    public boolean updateUserMatchDetails(long accountId, long matchId, 
                                         Boolean isFavorite, Boolean isHidden, String notes) {
        try (Connection conn = databaseManager.getConnection()) {
            // First, check if the record exists
            boolean recordExists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT 1 FROM user_match_details WHERE account_id = ? AND match_id = ?")) {
                checkStmt.setLong(1, accountId);
                checkStmt.setLong(2, matchId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    recordExists = rs.next();
                }
            }
            
            String sql;
            if (recordExists) {
                // Build UPDATE statement dynamically based on which fields are being updated
                StringBuilder updateSql = new StringBuilder(
                        "UPDATE user_match_details SET updated_date = CURRENT_TIMESTAMP");
                
                if (isFavorite != null) {
                    updateSql.append(", is_favorite = ?");
                }
                if (isHidden != null) {
                    updateSql.append(", is_hidden = ?");
                }
                if (notes != null) {
                    updateSql.append(", notes = ?");
                }
                
                updateSql.append(" WHERE account_id = ? AND match_id = ?");
                sql = updateSql.toString();
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    int paramIndex = 1;
                    
                    if (isFavorite != null) {
                        stmt.setBoolean(paramIndex++, isFavorite);
                    }
                    if (isHidden != null) {
                        stmt.setBoolean(paramIndex++, isHidden);
                    }
                    if (notes != null) {
                        stmt.setString(paramIndex++, notes);
                    }
                    
                    stmt.setLong(paramIndex++, accountId);
                    stmt.setLong(paramIndex, matchId);
                    
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
            } else {
                // Insert new record
                sql = "INSERT INTO user_match_details " +
                      "(account_id, match_id, is_favorite, is_hidden, notes) " +
                      "VALUES (?, ?, ?, ?, ?)";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, accountId);
                    stmt.setLong(2, matchId);
                    stmt.setBoolean(3, isFavorite != null ? isFavorite : false);
                    stmt.setBoolean(4, isHidden != null ? isHidden : false);
                    stmt.setString(5, notes != null ? notes : "");
                    
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error updating user match details", e);
            return false;
        }
    }
    
    /**
     * Updates user match details (int version for accountId).
     *
     * @param accountId Player account ID (as int)
     * @param matchId Match ID
     * @param isFavorite Whether match is marked as favorite (can be null to not change)
     * @param isHidden Whether match is hidden from normal view (can be null to not change)
     * @param notes Optional notes about the match (can be null to not change)
     * @return True if update was successful
     */
    public boolean updateUserMatchDetails(int accountId, long matchId, 
                                         Boolean isFavorite, Boolean isHidden, String notes) {
        return updateUserMatchDetails((long)accountId, matchId, isFavorite, isHidden, notes);
    }
    
    /**
     * Updates player match history sync status.
     *
     * @param accountId Player account ID
     * @param matchCount Total matches synchronized
     * @param lastMatchId Most recent match ID
     * @param fullSyncComplete Whether a full sync has been completed
     * @return True if update was successful
     */
    public boolean updateMatchHistorySyncStatus(long accountId, int matchCount, 
                                              Long lastMatchId, boolean fullSyncComplete) {
        return updateMatchHistorySyncStatus(accountId, matchCount, lastMatchId, fullSyncComplete, "DAILY");
    }
                                              
    public boolean updateMatchHistorySyncStatus(long accountId, int matchCount, 
                                              Long lastMatchId, boolean fullSyncComplete, String syncFrequency) {
        String sql = "INSERT INTO player_match_history_sync " +
                    "(account_id, last_sync_timestamp, matches_count, last_match_id, full_sync_completed, sync_frequency, sync_in_progress) " +
                    "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (account_id) DO UPDATE SET " +
                    "last_sync_timestamp = CURRENT_TIMESTAMP, " +
                    "matches_count = EXCLUDED.matches_count, " +
                    "last_match_id = EXCLUDED.last_match_id, " +
                    "full_sync_completed = EXCLUDED.full_sync_completed, " +
                    "sync_frequency = EXCLUDED.sync_frequency, " +
                    "sync_in_progress = EXCLUDED.sync_in_progress";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Convert long to int - account_id is INTEGER in PostgreSQL, not BIGINT
            int accountIdInt = (int)accountId; 
            stmt.setInt(1, accountIdInt);
            stmt.setInt(2, matchCount);
            
            if (lastMatchId != null) {
                try {
                    // PostgreSQL column is INTEGER (max ~2 billion), not BIGINT
                    int lastMatchIdInt = lastMatchId.intValue();
                    stmt.setInt(3, lastMatchIdInt);
                } catch (Exception e) {
                    // If value is too large to fit in an integer, set most recent possible value
                    LOGGER.warn("Match ID {} too large for INTEGER column, setting to MAX_INT", lastMatchId);
                    stmt.setInt(3, Integer.MAX_VALUE);
                }
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(4, fullSyncComplete ? 1 : 0);  // Use 1/0 instead of boolean
            stmt.setString(5, syncFrequency);
            stmt.setInt(6, 0);  // Use 0 for false since the column is INTEGER
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LOGGER.error("Error updating match history sync status", e);
            return false;
        }
    }
    
    /**
     * Gets player hero statistics.
     *
     * @param accountId The player's account ID
     * @param limit Maximum number of heroes to retrieve
     * @return Map of hero ID to [matches played, wins]
     */
    public Map<Integer, int[]> getPlayerHeroStats(long accountId, int limit) {
        String sql = "SELECT hero_id, COUNT(*) as matches_played, " +
                    "SUM(CASE WHEN won THEN 1 ELSE 0 END) as wins " +
                    "FROM match_players " +
                    "WHERE account_id = ? " +
                    "GROUP BY hero_id " +
                    "ORDER BY matches_played DESC " +
                    "LIMIT ?";
        
        Map<Integer, int[]> heroStats = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int heroId = rs.getInt("hero_id");
                    int[] stats = new int[2];
                    stats[0] = rs.getInt("matches_played");
                    stats[1] = rs.getInt("wins");
                    heroStats.put(heroId, stats);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving player hero statistics", e);
        }
        
        return heroStats;
    }
    
    /**
     * Gets the sync status for a player's match history.
     *
     * @param accountId Player account ID
     * @return Optional containing sync status info as [matchCount, lastSyncTimestamp in millis, fullSyncCompleted]
     */
    public Map<String, Object> getMatchHistorySyncStatus(long accountId) {
        String sql = "SELECT matches_count, last_sync_timestamp, full_sync_completed, sync_in_progress, " +
                    "last_match_id, next_sync_date, sync_frequency " +
                    "FROM player_match_history_sync " +
                    "WHERE account_id = ?";
        
        Map<String, Object> status = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.put("accountId", accountId);
                    status.put("matchesCount", rs.getInt("matches_count"));
                    
                    java.sql.Timestamp lastSyncTimestamp = rs.getTimestamp("last_sync_timestamp");
                    status.put("lastSyncTimestamp", lastSyncTimestamp != null ? lastSyncTimestamp.getTime() : null);
                    
                    status.put("fullSyncCompleted", rs.getInt("full_sync_completed") == 1);
                    status.put("syncInProgress", rs.getInt("sync_in_progress") == 1);
                    
                    Long lastMatchId = rs.getLong("last_match_id");
                    status.put("lastMatchId", rs.wasNull() ? null : lastMatchId);
                    
                    java.sql.Timestamp nextSyncDate = rs.getTimestamp("next_sync_date");
                    status.put("nextSyncDate", nextSyncDate != null ? nextSyncDate.getTime() : null);
                    
                    status.put("syncFrequency", rs.getString("sync_frequency"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving match history sync status", e);
        }
        
        return status;
    }
    
    /**
     * Gets match history sync status as array (legacy method).
     *
     * @param accountId Player account ID
     * @return Optional containing sync status info as [matchCount, lastSyncTimestamp in millis, fullSyncCompleted]
     */
    public Optional<Object[]> getMatchHistorySyncStatusAsArray(long accountId) {
        String sql = "SELECT matches_count, last_sync_timestamp, full_sync_completed, sync_in_progress " +
                    "FROM player_match_history_sync " +
                    "WHERE account_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Object[] status = new Object[4];
                    status[0] = rs.getInt("matches_count");
                    status[1] = rs.getTimestamp("last_sync_timestamp").getTime();
                    status[2] = rs.getInt("full_sync_completed") == 1;
                    status[3] = rs.getInt("sync_in_progress") == 1;
                    return Optional.of(status);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving match history sync status", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Marks sync as in progress for a player.
     *
     * @param accountId Player account ID
     * @return True if update was successful
     */
    public boolean startMatchHistorySync(long accountId) {
        String sql = "INSERT INTO player_match_history_sync " +
                    "(account_id, sync_in_progress, next_sync_date) " +
                    "VALUES (?, ?, NULL) " +
                    "ON CONFLICT (account_id) DO UPDATE SET " +
                    "sync_in_progress = ?, " +
                    "next_sync_date = NULL";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Convert long to int - account_id is INTEGER in PostgreSQL, not BIGINT
            int accountIdInt = (int)accountId;
            stmt.setInt(1, accountIdInt);
            stmt.setInt(2, 1);  // Use 1 for true since the column is INTEGER
            stmt.setInt(3, 1);  // Use 1 for true since the column is INTEGER
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LOGGER.error("Error starting match history sync", e);
            return false;
        }
    }
    
    /**
     * Gets the count of favorite matches for a player.
     *
     * @param accountId The player's account ID
     * @return Number of favorite matches
     */
    public int getFavoriteMatchesCount(long accountId) {
        String sql = "SELECT COUNT(*) " +
                    "FROM user_match_details " +
                    "WHERE account_id = ? AND is_favorite = true";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting favorite matches count", e);
        }
        
        return 0;
    }
}