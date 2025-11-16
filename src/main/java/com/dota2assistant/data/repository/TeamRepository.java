package com.dota2assistant.data.repository;

import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerRoster;
import com.dota2assistant.data.model.PlayerRoster.Position;
import com.dota2assistant.data.model.PlayerTeam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for handling team data operations.
 */
public class TeamRepository {
    private static final Logger LOGGER = Logger.getLogger(TeamRepository.class.getName());
    private final DatabaseManager databaseManager;
    
    public TeamRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * Creates a new team.
     *
     * @param team The team to create
     * @return True if creation was successful
     */
    public boolean createTeam(PlayerTeam team) {
        String sql = "INSERT INTO teams (id, name, tag, logo_url, is_professional, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET " +
                    "name = EXCLUDED.name, " +
                    "tag = EXCLUDED.tag, " +
                    "logo_url = EXCLUDED.logo_url, " +
                    "is_professional = EXCLUDED.is_professional, " +
                    "last_updated = EXCLUDED.last_updated";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, team.getId());
            stmt.setString(2, team.getName());
            stmt.setString(3, team.getTag());
            stmt.setString(4, team.getLogoUrl());
            stmt.setBoolean(5, team.isProfessional());
            stmt.setTimestamp(6, java.sql.Timestamp.valueOf(team.getLastUpdated()));
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Update roster entries
                updateTeamRoster(team);
                return true;
            }
            
            return false;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error creating team", e);
            return false;
        }
    }
    
    /**
     * Updates the roster for a team.
     *
     * @param team The team with roster to update
     * @return True if update was successful
     */
    public boolean updateTeamRoster(PlayerTeam team) {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            // Set all existing roster entries to inactive
            String deactivateSql = 
                "UPDATE team_roster SET is_active = false, leave_date = ? " +
                "WHERE team_id = ? AND is_active = true";
            
            try (PreparedStatement deactivateStmt = conn.prepareStatement(deactivateSql)) {
                deactivateStmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                deactivateStmt.setLong(2, team.getId());
                deactivateStmt.executeUpdate();
            }
            
            // Insert or update roster entries
            String rosterSql = 
                "INSERT INTO team_roster (team_id, account_id, position, is_active, join_date) " +
                "VALUES (?, ?, ?, true, ?) " +
                "ON CONFLICT (team_id, account_id) DO UPDATE SET " +
                "position = EXCLUDED.position, " +
                "is_active = true, " +
                "join_date = CASE WHEN team_roster.is_active = false THEN EXCLUDED.join_date ELSE team_roster.join_date END, " +
                "leave_date = null";
            
            try (PreparedStatement rosterStmt = conn.prepareStatement(rosterSql)) {
                for (PlayerRoster player : team.getRoster()) {
                    rosterStmt.setLong(1, team.getId());
                    rosterStmt.setLong(2, player.getAccountId());
                    rosterStmt.setInt(3, player.getPosition().getValue());
                    rosterStmt.setTimestamp(4, java.sql.Timestamp.valueOf(player.getJoinDate()));
                    rosterStmt.addBatch();
                }
                rosterStmt.executeBatch();
            }
            
            conn.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating team roster", e);
            return false;
        }
    }
    
    /**
     * Gets a team by ID.
     *
     * @param teamId The team ID
     * @return Optional containing the team if found
     */
    public Optional<PlayerTeam> getTeamById(long teamId) {
        String sql = "SELECT t.id, t.name, t.tag, t.logo_url, t.is_professional, " +
                    "t.last_updated, t.total_matches, COALESCE(t.wins * 1.0 / NULLIF(t.total_matches, 0), 0) as win_rate " +
                    "FROM teams t " +
                    "WHERE t.id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, teamId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PlayerTeam team = mapResultSetToTeam(rs);
                    team.getRoster().addAll(getTeamRoster(teamId, conn));
                    return Optional.of(team);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting team by ID", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Gets all active professional teams.
     *
     * @return List of professional teams
     */
    public List<PlayerTeam> getProfessionalTeams() {
        String sql = "SELECT t.id, t.name, t.tag, t.logo_url, t.is_professional, " +
                    "t.last_updated, t.total_matches, COALESCE(t.wins * 1.0 / NULLIF(t.total_matches, 0), 0) as win_rate " +
                    "FROM teams t " +
                    "WHERE t.is_professional = true " +
                    "ORDER BY t.name";
        
        List<PlayerTeam> teams = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            Map<Long, PlayerTeam> teamMap = new HashMap<>();
            
            while (rs.next()) {
                PlayerTeam team = mapResultSetToTeam(rs);
                teamMap.put(team.getId(), team);
                teams.add(team);
            }
            
            // Batch load rosters for all teams
            loadTeamRosters(teamMap, conn);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting professional teams", e);
        }
        
        return teams;
    }
    
    /**
     * Gets user-created teams for a specific account.
     *
     * @param accountId The account ID
     * @return List of the user's teams
     */
    public List<PlayerTeam> getUserTeams(long accountId) {
        String sql = "SELECT t.id, t.name, t.tag, t.logo_url, t.is_professional, " +
                    "t.last_updated, t.total_matches, COALESCE(t.wins * 1.0 / NULLIF(t.total_matches, 0), 0) as win_rate " +
                    "FROM teams t " +
                    "JOIN user_teams ut ON t.id = ut.team_id " +
                    "WHERE ut.creator_account_id = ? " +
                    "ORDER BY ut.is_favorite DESC, t.name";
        
        List<PlayerTeam> teams = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Long, PlayerTeam> teamMap = new HashMap<>();
                
                while (rs.next()) {
                    PlayerTeam team = mapResultSetToTeam(rs);
                    teamMap.put(team.getId(), team);
                    teams.add(team);
                }
                
                // Batch load rosters for all teams
                loadTeamRosters(teamMap, conn);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting user teams", e);
        }
        
        return teams;
    }
    
    /**
     * Gets the team roster for a specific team.
     *
     * @param teamId The team ID
     * @param conn Existing database connection
     * @return List of roster entries
     */
    private List<PlayerRoster> getTeamRoster(long teamId, Connection conn) throws SQLException {
        String sql = "SELECT tr.team_id, tr.account_id, p.name, tr.position, " +
                    "tr.is_active, tr.join_date, tr.leave_date " +
                    "FROM team_roster tr " +
                    "JOIN players p ON tr.account_id = p.account_id " +
                    "WHERE tr.team_id = ? " +
                    "ORDER BY tr.position";
        
        List<PlayerRoster> roster = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, teamId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerRoster player = new PlayerRoster(
                        rs.getLong("team_id"),
                        rs.getLong("account_id"),
                        rs.getString("name"),
                        Position.fromValue(rs.getInt("position")),
                        rs.getBoolean("is_active"),
                        rs.getTimestamp("join_date").toLocalDateTime(),
                        rs.getTimestamp("leave_date") != null ? 
                            rs.getTimestamp("leave_date").toLocalDateTime() : null
                    );
                    roster.add(player);
                }
            }
        }
        
        return roster;
    }
    
    /**
     * Batch loads team rosters for multiple teams.
     *
     * @param teamMap Map of team ID to team object
     * @param conn Existing database connection
     */
    private void loadTeamRosters(Map<Long, PlayerTeam> teamMap, Connection conn) throws SQLException {
        if (teamMap.isEmpty()) {
            return;
        }
        
        String sql = "SELECT tr.team_id, tr.account_id, p.name, tr.position, " +
                    "tr.is_active, tr.join_date, tr.leave_date " +
                    "FROM team_roster tr " +
                    "JOIN players p ON tr.account_id = p.account_id " +
                    "WHERE tr.team_id IN (" + 
                    String.join(",", teamMap.keySet().stream().map(id -> "?").toList()) + ") " +
                    "ORDER BY tr.team_id, tr.position";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (Long teamId : teamMap.keySet()) {
                stmt.setLong(index++, teamId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long teamId = rs.getLong("team_id");
                    PlayerTeam team = teamMap.get(teamId);
                    
                    if (team != null) {
                        PlayerRoster player = new PlayerRoster(
                            teamId,
                            rs.getLong("account_id"),
                            rs.getString("name"),
                            Position.fromValue(rs.getInt("position")),
                            rs.getBoolean("is_active"),
                            rs.getTimestamp("join_date").toLocalDateTime(),
                            rs.getTimestamp("leave_date") != null ? 
                                rs.getTimestamp("leave_date").toLocalDateTime() : null
                        );
                        team.getRoster().add(player);
                    }
                }
            }
        }
    }
    
    /**
     * Gets hero statistics for a team.
     *
     * @param teamId The team ID
     * @return Map of hero ID to [matches played, wins]
     */
    public Map<Integer, int[]> getTeamHeroStats(long teamId) {
        String sql = "SELECT hero_id, matches_played, wins " +
                    "FROM team_heroes " +
                    "WHERE team_id = ? " +
                    "ORDER BY matches_played DESC";
        
        Map<Integer, int[]> heroStats = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, teamId);
            
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
            LOGGER.log(Level.SEVERE, "Error getting team hero stats", e);
        }
        
        return heroStats;
    }
    
    /**
     * Gets player hero statistics within a team.
     *
     * @param teamId The team ID
     * @param accountId The player account ID
     * @return Map of hero ID to [matches played, wins, signature level]
     */
    public Map<Integer, int[]> getPlayerTeamHeroStats(long teamId, long accountId) {
        String sql = "SELECT hero_id, matches_played, wins, signature_level " +
                    "FROM team_player_heroes " +
                    "WHERE team_id = ? AND account_id = ? " +
                    "ORDER BY matches_played DESC";
        
        Map<Integer, int[]> heroStats = new HashMap<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, teamId);
            stmt.setLong(2, accountId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int heroId = rs.getInt("hero_id");
                    int[] stats = new int[3];
                    stats[0] = rs.getInt("matches_played");
                    stats[1] = rs.getInt("wins");
                    stats[2] = rs.getInt("signature_level");
                    heroStats.put(heroId, stats);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting player team hero stats", e);
        }
        
        return heroStats;
    }
    
    /**
     * Maps a database result set row to a PlayerTeam object.
     *
     * @param rs The result set
     * @return The mapped team
     */
    private PlayerTeam mapResultSetToTeam(ResultSet rs) throws SQLException {
        return new PlayerTeam(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("tag"),
            rs.getString("logo_url"),
            rs.getBoolean("is_professional"),
            rs.getTimestamp("last_updated").toLocalDateTime(),
            new ArrayList<>(),
            rs.getInt("total_matches"),
            rs.getDouble("win_rate")
        );
    }
    
    /**
     * Searches for teams by name or tag.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return List of matching teams
     */
    public List<PlayerTeam> searchTeams(String query, int limit) {
        String sql = "SELECT t.id, t.name, t.tag, t.logo_url, t.is_professional, " +
                    "t.last_updated, t.total_matches, COALESCE(t.wins * 1.0 / NULLIF(t.total_matches, 0), 0) as win_rate " +
                    "FROM teams t " +
                    "WHERE t.name ILIKE ? OR t.tag ILIKE ? " +
                    "ORDER BY t.is_professional DESC, t.name " +
                    "LIMIT ?";
        
        List<PlayerTeam> teams = new ArrayList<>();
        String searchPattern = "%" + query + "%";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Long, PlayerTeam> teamMap = new HashMap<>();
                
                while (rs.next()) {
                    PlayerTeam team = mapResultSetToTeam(rs);
                    teamMap.put(team.getId(), team);
                    teams.add(team);
                }
                
                // Batch load rosters for all teams
                loadTeamRosters(teamMap, conn);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching teams", e);
        }
        
        return teams;
    }
}